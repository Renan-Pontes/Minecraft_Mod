package com.renanpontes.isekaimahou.feature.origins_a.rune;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.origins_a.OaRegistries;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Encantamento Temporário: aplica encantamentos de verdade no item da mão e guarda, num componente do item,
 * o nível que cada encantamento tinha antes e quando acaba. Ao expirar (checado no inventário, em baús abertos
 * e em itens soltos) os níveis antigos voltam e o componente some. Bigorna/rebolo recusam o item enquanto durar,
 * para o encantamento não virar permanente.
 */
public final class TempEnchant {

    /** Um encantamento mexido: nível antes e nível aplicado. */
    public record Entry(ResourceLocation id, int before, int applied) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("id").forGetter(Entry::id),
                Codec.INT.fieldOf("before").forGetter(Entry::before),
                Codec.INT.fieldOf("applied").forGetter(Entry::applied)).apply(i, Entry::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, Entry::id,
                ByteBufCodecs.VAR_INT, Entry::before,
                ByteBufCodecs.VAR_INT, Entry::applied,
                Entry::new);
    }

    /** Componente "isekai_mahou:temp_enchantment". {@code expires} = tempo de jogo (ticks) do fim. */
    public record Data(List<Entry> entries, long expires) {
        public static final Codec<Data> CODEC = RecordCodecBuilder.create(i -> i.group(
                Entry.CODEC.listOf().fieldOf("entries").forGetter(Data::entries),
                Codec.LONG.fieldOf("expires").forGetter(Data::expires)).apply(i, Data::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
                Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), Data::entries,
                ByteBufCodecs.VAR_LONG, Data::expires,
                Data::new);
    }

    // =====================================================================================
    //  Aplicar
    // =====================================================================================
    /** Resultado de {@link #apply}. */
    public enum Result { OK, NOT_ENCHANTABLE, ALREADY_STRONGER }

    /**
     * Encanta o item conforme o tipo (arma, arco, besta, tridente, maça, ferramenta, vara de pesca) e o elemento da magia.
     */
    public static Result apply(Player player, ItemStack stack, Element element, int bonus, int durationTicks) {
        if (stack.isEmpty() || stack.is(Items.BOOK) || stack.is(Items.ENCHANTED_BOOK) || stack.getMaxStackSize() > 1) {
            return Result.NOT_ENCHANTABLE;
        }
        Map<ResourceKey<Enchantment>, Integer> plan = plan(stack, element, bonus);
        if (plan.isEmpty()) return Result.NOT_ENCHANTABLE;
        RegistryAccess access = player.level().registryAccess();
        long now = player.level().getGameTime();
        // Renovar: desfaz o anterior antes, para não acumular
        if (stack.has(OaRegistries.TEMP_ENCHANT.get())) revert(stack, access);

        List<Entry> entries = new ArrayList<>();
        for (Map.Entry<ResourceKey<Enchantment>, Integer> p : plan.entrySet()) {
            Holder<Enchantment> h = access.registryOrThrow(Registries.ENCHANTMENT).getHolder(p.getKey()).orElse(null);
            if (h == null) continue;
            int before = storedLevel(stack, h);
            int lvl = Math.max(before, p.getValue());
            // Não sobe o que já está igual/maior nem cria combinação proibida (Afiação+Julgamento, Riptide+Lealdade...)
            if (lvl == before || !compatible(stack, h)) continue;
            EnchantmentHelper.updateEnchantments(stack, m -> m.set(h, lvl));
            entries.add(new Entry(p.getKey().location(), before, lvl));
        }
        if (entries.isEmpty()) return Result.ALREADY_STRONGER;
        stack.set(OaRegistries.TEMP_ENCHANT.get(), new Data(List.copyOf(entries), now + durationTicks));
        return Result.OK;
    }

    /** O encantamento novo não pode ser exclusivo com nenhum que o item já tem (o próprio pode subir de nível). */
    private static boolean compatible(ItemStack stack, Holder<Enchantment> h) {
        for (Holder<Enchantment> cur : stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).keySet()) {
            if (!cur.equals(h) && !Enchantment.areCompatible(cur, h)) return false;
        }
        return true;
    }

    /** Tipo de item encantável (pelas tags de encantamento do vanilla, que mods também usam). */
    private enum Kind { BOW, CROSSBOW, TRIDENT, MACE, MELEE, TOOL, FISHING }

    private static Kind kind(ItemStack stack) {
        if (stack.is(ItemTags.BOW_ENCHANTABLE) || stack.getItem() instanceof BowItem) return Kind.BOW;
        if (stack.is(ItemTags.CROSSBOW_ENCHANTABLE) || stack.getItem() instanceof CrossbowItem) return Kind.CROSSBOW;
        if (stack.is(ItemTags.TRIDENT_ENCHANTABLE) || stack.getItem() instanceof TridentItem) return Kind.TRIDENT;
        if (stack.is(ItemTags.MACE_ENCHANTABLE) || stack.getItem() instanceof MaceItem) return Kind.MACE;
        if (stack.is(ItemTags.SHARP_WEAPON_ENCHANTABLE) || stack.is(ItemTags.WEAPON_ENCHANTABLE)
                || stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem) return Kind.MELEE;
        if (stack.is(ItemTags.MINING_ENCHANTABLE) || stack.getItem() instanceof DiggerItem) return Kind.TOOL;
        if (stack.is(ItemTags.FISHING_ENCHANTABLE) || stack.getItem() instanceof FishingRodItem) return Kind.FISHING;
        return null;
    }

    /**
     * Encantamentos por tipo de item: um principal (força) + um por elemento-base da magia
     * (fusões contam os dois elementos-pai). Armadura, escudo etc. não recebem nada.
     */
    private static Map<ResourceKey<Enchantment>, Integer> plan(ItemStack stack, Element element, int bonus) {
        Map<ResourceKey<Enchantment>, Integer> m = new LinkedHashMap<>();
        Kind kind = kind(stack);
        if (kind == null) return m;
        List<Element> bases = element.isFusion() ? List.of(element.parentA, element.parentB) : List.of(element);
        boolean holy = bases.contains(Element.LIGHT);
        switch (kind) {
            case BOW -> m.put(Enchantments.POWER, Math.min(5, 3 + bonus));
            case CROSSBOW -> m.put(Enchantments.QUICK_CHARGE, Math.min(3, 2 + bonus));
            case TRIDENT -> m.put(Enchantments.IMPALING, Math.min(5, 3 + bonus));
            case MACE -> m.put(holy ? Enchantments.SMITE : Enchantments.DENSITY, Math.min(5, 3 + bonus));
            case MELEE -> m.put(holy ? Enchantments.SMITE : Enchantments.SHARPNESS, Math.min(5, 3 + bonus));
            case TOOL -> m.put(Enchantments.EFFICIENCY, Math.min(5, 3 + bonus));
            case FISHING -> m.put(Enchantments.LURE, Math.min(3, 2 + bonus));
        }
        for (Element el : bases) secondary(m, kind, el, bonus);
        return m;
    }

    private static void secondary(Map<ResourceKey<Enchantment>, Integer> m, Kind kind, Element el, int bonus) {
        switch (kind) {
            case BOW -> {
                switch (el) {
                    case FIRE -> m.put(Enchantments.FLAME, 1);
                    case WATER, WIND -> m.put(Enchantments.PUNCH, 2);
                    case LIGHT -> m.put(Enchantments.INFINITY, 1);
                    case EARTH -> m.put(Enchantments.UNBREAKING, 3);
                    default -> m.put(Enchantments.POWER, Math.min(5, 4 + bonus));
                }
            }
            case CROSSBOW -> {
                switch (el) {
                    case FIRE, WIND -> m.put(Enchantments.MULTISHOT, 1);
                    case EARTH -> m.put(Enchantments.UNBREAKING, 3);
                    default -> m.put(Enchantments.PIERCING, Math.min(4, 3 + bonus));
                }
            }
            case TRIDENT -> {
                switch (el) {
                    case WATER -> m.put(Enchantments.RIPTIDE, 3);
                    case WIND, LIGHT -> m.put(Enchantments.CHANNELING, 1);
                    default -> m.put(Enchantments.LOYALTY, 3);
                }
            }
            case MACE -> {
                switch (el) {
                    case FIRE -> m.put(Enchantments.FIRE_ASPECT, 2);
                    case WIND -> m.put(Enchantments.WIND_BURST, Math.min(3, 1 + bonus));
                    case LIGHT -> m.put(Enchantments.FIRE_ASPECT, 1);
                    default -> m.put(Enchantments.UNBREAKING, 3);
                }
            }
            case MELEE -> {
                switch (el) {
                    case FIRE -> m.put(Enchantments.FIRE_ASPECT, 2);
                    case WATER -> m.put(Enchantments.KNOCKBACK, 2);
                    case EARTH -> m.put(Enchantments.UNBREAKING, 3);
                    case WIND -> m.put(Enchantments.SWEEPING_EDGE, 3);
                    case LIGHT -> m.put(Enchantments.FIRE_ASPECT, 1);
                    case SHADOW -> m.put(Enchantments.LOOTING, 3);
                    default -> m.put(Enchantments.SHARPNESS, Math.min(5, 4 + bonus));
                }
            }
            case TOOL -> {
                switch (el) {
                    case EARTH, LIGHT -> m.put(Enchantments.FORTUNE, Math.min(3, 2 + bonus));
                    case SHADOW, WIND -> m.put(Enchantments.SILK_TOUCH, 1);
                    default -> m.put(Enchantments.UNBREAKING, 3);
                }
            }
            case FISHING -> {
                switch (el) {
                    case WATER, LIGHT -> m.put(Enchantments.LUCK_OF_THE_SEA, 3);
                    default -> m.put(Enchantments.UNBREAKING, 3);
                }
            }
        }
    }

    // =====================================================================================
    //  Remover
    // =====================================================================================
    /** Volta os níveis antigos (só onde o nível ainda é o que aplicamos) e tira o componente. */
    public static void revert(ItemStack stack, RegistryAccess access) {
        Data data = stack.get(OaRegistries.TEMP_ENCHANT.get());
        if (data == null) return;
        for (Entry e : data.entries()) {
            Holder<Enchantment> h = access.registryOrThrow(Registries.ENCHANTMENT)
                    .getHolder(ResourceKey.create(Registries.ENCHANTMENT, e.id())).orElse(null);
            if (h == null) continue;
            if (storedLevel(stack, h) == e.applied()) {
                EnchantmentHelper.updateEnchantments(stack, m -> m.set(h, e.before()));
            }
        }
        stack.remove(OaRegistries.TEMP_ENCHANT.get());
    }

    /** Nível gravado no item (sem bônus de outros sistemas). */
    private static int storedLevel(ItemStack stack, Holder<Enchantment> h) {
        return stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(h);
    }

    public static boolean expired(ItemStack stack, long now) {
        Data d = stack.get(OaRegistries.TEMP_ENCHANT.get());
        return d != null && now >= d.expires();
    }

    /** Desfaz os vencidos num container; devolve quantos. */
    public static int sweep(Container c, RegistryAccess access, long now) {
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (expired(s, now)) {
                revert(s, access);
                n++;
            }
        }
        return n;
    }

    /** Checagem periódica do inventário do jogador. */
    public static void sweepPlayer(ServerPlayer sp) {
        long now = sp.level().getGameTime();
        if (sweep(sp.getInventory(), sp.level().registryAccess(), now) > 0) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.origins_a.enchant_faded")
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
            sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), net.minecraft.sounds.SoundEvents.ENCHANTMENT_TABLE_USE,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.5F, 0.6F);
        }
    }

    private TempEnchant() {}
}
