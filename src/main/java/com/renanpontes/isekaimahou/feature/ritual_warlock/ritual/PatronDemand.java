package com.renanpontes.isekaimahou.feature.ritual_warlock.ritual;

import java.util.List;

import com.renanpontes.isekaimahou.feature.ritual_warlock.WarlockContent;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;

/**
 * Tributo exigido por um Patrono.
 * <ul>
 *   <li>ITEM: entregar {@code amount} unidades do item {@code target} no altar.</li>
 *   <li>KILL: matar {@code amount} mobs do tipo {@code target} em {@code minutes} minutos (delta do contador kill.ns.mob).</li>
 *   <li>SOULS: colher {@code amount} almas com a Adaga Ritual em {@code minutes} minutos.</li>
 *   <li>HEARTS: ceder {@code amount} corações máximos por {@code minutes} minutos (pago ao aceitar).</li>
 *   <li>LEVELS: sacrificar {@code amount} níveis de experiência (pago ao aceitar).</li>
 * </ul>
 */
public record PatronDemand(Type type, String target, int amount, int minutes) {
    public static final String SOULS_COUNTER = "warlock.souls";

    public enum Type { ITEM, KILL, SOULS, HEARTS, LEVELS }

    public boolean isSacrifice() {
        return type == Type.HEARTS || type == Type.LEVELS;
    }

    /** Tributos acompanhados por contador (progresso automático). */
    public boolean isTracked() {
        return type == Type.KILL || type == Type.SOULS;
    }

    public String counterKey() {
        if (type == Type.SOULS) return SOULS_COUNTER;
        if (type == Type.KILL) {
            ResourceLocation id = ResourceLocation.tryParse(target);
            return id == null ? "kill.unknown" : "kill." + id.getNamespace() + "." + id.getPath();
        }
        return "";
    }

    public Item item() {
        ResourceLocation id = ResourceLocation.tryParse(target);
        return id == null ? Items.AIR : BuiltInRegistries.ITEM.get(id);
    }

    public EntityType<?> entity() {
        ResourceLocation id = ResourceLocation.tryParse(target);
        return id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
    }

    /** Nome curto do alvo (item ou mob). */
    public Component targetName() {
        return switch (type) {
            case ITEM -> item().getDescription();
            case KILL -> {
                EntityType<?> e = entity();
                yield e == null ? Component.literal(target) : e.getDescription();
            }
            case SOULS -> Component.translatable("warlock.isekai_mahou.souls");
            case HEARTS -> Component.translatable("warlock.isekai_mahou.hearts");
            case LEVELS -> Component.translatable("warlock.isekai_mahou.levels");
        };
    }

    /** Frase completa do tributo, ex.: "Entregue 4× Vara de Blaze". */
    public Component describe() {
        return switch (type) {
            case ITEM -> Component.translatable("warlock.isekai_mahou.demand.item", amount, targetName());
            case KILL -> Component.translatable("warlock.isekai_mahou.demand.kill", amount, targetName(), minutes);
            case SOULS -> Component.translatable("warlock.isekai_mahou.demand.souls", amount, minutes);
            case HEARTS -> Component.translatable("warlock.isekai_mahou.demand.hearts", amount, minutes);
            case LEVELS -> Component.translatable("warlock.isekai_mahou.demand.levels", amount);
        };
    }

    /** Ícone mostrado no diálogo / HUD. */
    public ItemStack icon() {
        return switch (type) {
            case ITEM -> new ItemStack(item(), Math.min(amount, item().getDefaultMaxStackSize()));
            case KILL -> {
                EntityType<?> e = entity();
                SpawnEggItem egg = e == null ? null : SpawnEggItem.byId(e);
                yield egg != null ? new ItemStack(egg) : new ItemStack(Items.SKELETON_SKULL);
            }
            case SOULS -> new ItemStack(WarlockContent.RITUAL_DAGGER.get());
            // A quantidade do ícone diz quantos corações a tela de diálogo desenha.
            case HEARTS -> new ItemStack(Items.FERMENTED_SPIDER_EYE, Math.max(1, amount));
            case LEVELS -> new ItemStack(Items.EXPERIENCE_BOTTLE);
        };
    }

    /** Quantos itens do tributo o jogador tem no inventário. */
    public int countInInventory(Player player) {
        if (type != Type.ITEM) return 0;
        Item it = item();
        int n = 0;
        for (ItemStack s : player.getInventory().items) if (s.is(it)) n += s.getCount();
        for (ItemStack s : player.getInventory().offhand) if (s.is(it)) n += s.getCount();
        return n;
    }

    /**
     * Remove {@code amount} itens do inventário (chame só depois de conferir com {@link #countInInventory}).
     * Primeiro leva as cópias "comuns"; encantadas, renomeadas ou gastas (ex.: um tridente favorito) só por último.
     */
    public void takeFromInventory(Player player) {
        Item it = item();
        int left = amount;
        List<List<ItemStack>> lists = List.of(player.getInventory().items, player.getInventory().offhand);
        for (int pass = 0; pass < 2 && left > 0; pass++) {
            for (List<ItemStack> list : lists) {
                for (ItemStack s : list) {
                    if (left <= 0) break;
                    if (!s.is(it) || (pass == 0 && isPrecious(s))) continue;
                    int take = Math.min(left, s.getCount());
                    s.shrink(take);
                    left -= take;
                }
            }
        }
        player.getInventory().setChanged();
    }

    private static boolean isPrecious(ItemStack s) {
        return s.isEnchanted() || s.has(DataComponents.CUSTOM_NAME) || s.isDamaged();
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("type", type.name());
        t.putString("target", target);
        t.putInt("amount", amount);
        t.putInt("minutes", minutes);
        return t;
    }

    public static PatronDemand load(CompoundTag t) {
        if (t == null || !t.contains("type")) return null;
        Type type;
        try {
            type = Type.valueOf(t.getString("type"));
        } catch (IllegalArgumentException e) {
            return null;
        }
        return new PatronDemand(type, t.getString("target"), Math.max(1, t.getInt("amount")), t.getInt("minutes"));
    }
}
