package com.renanpontes.isekaimahou.feature.class_smith.forge;

import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.class_smith.SmithKeys;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_smith.SmithFx;
import com.renanpontes.isekaimahou.feature.class_smith.SmithProgress;
import com.renanpontes.isekaimahou.feature.class_smith.item.ForgedBladeItem;
import com.renanpontes.isekaimahou.feature.class_smith.item.SmithHammerItem;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Regras da Forja Arcana (servidor), usadas pelos glifos de forja e pela Bigorna Arcana: achar a lâmina que o jogador
 * segura (mão principal; ou a outra mão quando a principal tem um Martelo/qualquer coisa), converter armas vanilla
 * (espada, machado, maça) numa Lâmina Forjada, mexer nas partes respeitando a capacidade da qualidade, e a festa
 * (fagulhas, som de bigorna, texto na barra de ação, contadores das Provas).
 */
public final class ForgeService {
    /** Qualidade máxima de quem não é Ferreiro (multiclasse forja, mas sem obra-prima). */
    public static final int OUTSIDER_MAX_QUALITY = 2;

    /** Onde está a lâmina que será forjada. */
    public record Target(InteractionHand hand, ItemStack stack, boolean converted) {}

    /**
     * A lâmina segurada. Ordem: Lâmina Forjada na mão principal → na outra mão → arma vanilla conversível na mão
     * principal (vira Lâmina Forjada na hora, mantendo encantamentos e desgaste proporcional). Null se não há nada.
     */
    @Nullable
    public static Target target(ServerPlayer sp, boolean allowConvert) {
        ItemStack main = sp.getMainHandItem();
        if (ForgedBladeItem.is(main)) return new Target(InteractionHand.MAIN_HAND, main, false);
        ItemStack off = sp.getOffhandItem();
        if (ForgedBladeItem.is(off)) return new Target(InteractionHand.OFF_HAND, off, false);
        if (!allowConvert) return null;
        ForgedBlade base = convertible(main);
        if (base == null) return null;
        ItemStack blade = ForgedBladeItem.create(base.withSmith(sp.getGameProfile().getName()));
        // mantém encantamentos, nome e o desgaste proporcional da arma original
        blade.set(net.minecraft.core.component.DataComponents.ENCHANTMENTS,
                main.getOrDefault(net.minecraft.core.component.DataComponents.ENCHANTMENTS,
                        net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY));
        if (main.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)) {
            blade.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, main.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME));
        }
        if (main.getMaxDamage() > 0) {
            float wear = main.getDamageValue() / (float) main.getMaxDamage();
            blade.setDamageValue(Math.min(blade.getMaxDamage() - 1, Math.round(wear * blade.getMaxDamage())));
        }
        sp.setItemInHand(InteractionHand.MAIN_HAND, blade);
        sp.displayClientMessage(Component.translatable("message.isekai_mahou.class_smith.converted", main.getHoverName(),
                blade.getHoverName()).withStyle(ChatFormatting.GOLD), false);
        return new Target(InteractionHand.MAIN_HAND, blade, true);
    }

    /** Arma vanilla que a forja sabe "desmontar" numa Lâmina Forjada equivalente (null se não for). */
    @Nullable
    public static ForgedBlade convertible(ItemStack stack) {
        if (stack.isEmpty()) return null;
        Item it = stack.getItem();
        ForgedBlade d = ForgedBlade.DEFAULT;
        if (it == Items.IRON_SWORD) return d.withPart(ForgedBlade.Part.LENGTH, 1);
        if (it == Items.GOLDEN_SWORD) return d.withMaterial(BladeMaterial.GOLD).withPart(ForgedBlade.Part.LENGTH, 1);
        if (it == Items.DIAMOND_SWORD) return d.withMaterial(BladeMaterial.DIAMOND).withPart(ForgedBlade.Part.LENGTH, 1);
        if (it == Items.NETHERITE_SWORD) return d.withMaterial(BladeMaterial.NETHERITE).withPart(ForgedBlade.Part.LENGTH, 1);
        if (it == Items.IRON_AXE) return d.withFamily(BladeFamily.AXE);
        if (it == Items.GOLDEN_AXE) return d.withFamily(BladeFamily.AXE).withMaterial(BladeMaterial.GOLD);
        if (it == Items.DIAMOND_AXE) return d.withFamily(BladeFamily.AXE).withMaterial(BladeMaterial.DIAMOND);
        if (it == Items.NETHERITE_AXE) return d.withFamily(BladeFamily.AXE).withMaterial(BladeMaterial.NETHERITE);
        if (it == Items.MACE) return d.withFamily(BladeFamily.HAMMER).withMaterial(BladeMaterial.DIAMOND);
        if (it == Items.IRON_HOE) return d.withFamily(BladeFamily.SCYTHE);
        if (it == Items.DIAMOND_HOE) return d.withFamily(BladeFamily.SCYTHE).withMaterial(BladeMaterial.DIAMOND);
        if (it == Items.NETHERITE_HOE) return d.withFamily(BladeFamily.SCYTHE).withMaterial(BladeMaterial.NETHERITE);
        return null;
    }

    /** Maior qualidade que este jogador consegue temperar (Ferreiro: 1 + rank das Provas; outros: 2). */
    public static int maxQuality(ServerPlayer sp) {
        MagicData d = MagicApi.get(sp);
        if (d.path() != MagicPath.SMITH) return OUTSIDER_MAX_QUALITY;
        return Math.min(ForgedBlade.MAX_QUALITY, 1 + SmithProgress.rank(d));
    }

    // =====================================================================================
    //  Operações
    // =====================================================================================

    /** Resultado de uma tentativa (a mensagem já vem pronta). */
    public record Result(boolean ok, MutableComponent message) {
        static Result fail(String key, Object... args) {
            return new Result(false, Component.translatable(SmithKeys.MESSAGE + key, args).withStyle(ChatFormatting.RED));
        }
    }

    /** Troca a lâmina segurada por {@code after} se couber na capacidade; festa + contadores. */
    public static Result commit(ServerPlayer sp, Target t, ForgedBlade before, ForgedBlade after, MutableComponent what) {
        if (after.equals(before)) return Result.fail("no_change");
        if (!after.fits()) return Result.fail("capacity", after.points(), after.capacity(), ForgedBladeItem.stars(after.quality()));
        ItemStack stack = sp.getItemInHand(t.hand());
        if (!ForgedBladeItem.is(stack)) return Result.fail("no_blade");
        ForgedBladeItem.set(stack, after);
        SmithProgress.onReshaped(sp, before, after);
        celebrate(sp, t.hand(), after);
        MutableComponent msg = Component.translatable("message.isekai_mahou.class_smith.reshaped", what, after.typeName(),
                after.points(), after.capacity()).withStyle(ChatFormatting.GOLD);
        sp.displayClientMessage(msg, true);
        return new Result(true, msg);
    }

    /** Uma parte sobe/desce {@code steps} degraus. */
    public static Result stepPart(ServerPlayer sp, Target t, ForgedBlade.Part part, int steps) {
        ForgedBlade before = ForgedBladeItem.get(t.stack());
        int value = before.part(part) + steps;
        if (value < 0 || value > part.max) {
            int clamped = Math.max(0, Math.min(part.max, value));
            if (clamped == before.part(part)) return Result.fail(steps > 0 ? "part_max" : "part_min", part.displayName());
            value = clamped;
        }
        ForgedBlade after = before.withPart(part, value);
        return commit(sp, t, before, after, Component.translatable("message.isekai_mahou.class_smith.part_change",
                part.displayName(), before.part(part), value));
    }

    public static Result setFamily(ServerPlayer sp, Target t, BladeFamily family) {
        ForgedBlade before = ForgedBladeItem.get(t.stack());
        return commit(sp, t, before, before.withFamily(family), Component.translatable(
                "message.isekai_mahou.class_smith.family_change", family.familyName()));
    }

    public static Result setGrip(ServerPlayer sp, Target t, ForgedBlade.Grip grip) {
        ForgedBlade before = ForgedBladeItem.get(t.stack());
        return commit(sp, t, before, before.withGrip(grip), grip.displayName());
    }

    public static Result setFinish(ServerPlayer sp, Target t, ForgedBlade.Finish finish) {
        ForgedBlade before = ForgedBladeItem.get(t.stack());
        if (finish == ForgedBlade.Finish.RUNIC && before.runeElement() == null) return Result.fail("runic_needs_rune");
        return commit(sp, t, before, before.withFinish(finish), finish.displayName());
    }

    /** Grava (ou apaga, com null) a runa. Gravar exige 1 essência do elemento (paga aqui; criativo não paga). */
    public static Result setRune(ServerPlayer sp, Target t, @Nullable Element element) {
        ForgedBlade before = ForgedBladeItem.get(t.stack());
        if (element == null) {
            return commit(sp, t, before, before.withRune(null), Component.translatable("message.isekai_mahou.class_smith.rune_removed"));
        }
        if (before.runeElement() == element) return Result.fail("no_change");
        ForgedBlade after = before.withRune(element);
        if (!after.fits()) return Result.fail("capacity", after.points(), after.capacity(), ForgedBladeItem.stars(after.quality()));
        Item essence = essenceItem(element);
        if (essence == null) return Result.fail("rune_no_essence_kind", element.displayName());
        if (!sp.isCreative() && !take(sp, List.of(new ItemStack(essence, 1)))) {
            return Result.fail("rune_needs_essence", essence.getDescription());
        }
        Result r = commit(sp, t, before, after, Component.translatable("message.isekai_mahou.class_smith.rune_engraved", element.displayName()));
        if (r.ok()) SmithProgress.onRune(sp);
        return r;
    }

    /** Essência que grava a runa do elemento (fusões usam a do primeiro pai). */
    @Nullable
    public static Item essenceItem(Element e) {
        Element base = e.isFusion() ? e.parentA : e;
        var item = CoreRegistries.ESSENCES.get(base);
        return item == null ? null : item.get();
    }

    /** Troca o material (paga o custo do material novo). */
    public static Result setMaterial(ServerPlayer sp, Target t, BladeMaterial material) {
        ForgedBlade before = ForgedBladeItem.get(t.stack());
        if (before.material() == material) return Result.fail("no_change");
        if (!sp.isCreative() && !take(sp, material.cost())) return Result.fail("missing_material", costText(material.cost()));
        Result r = commit(sp, t, before, before.withMaterial(material),
                Component.translatable("message.isekai_mahou.class_smith.material_change", material.displayName()));
        if (!r.ok() && !sp.isCreative()) material.cost().forEach(s -> give(sp, s.copy()));
        return r;
    }

    /** Tempera: +1 de qualidade até o máximo do jogador (custa Cristais de Mana = qualidade nova). */
    public static Result temper(ServerPlayer sp, Target t) {
        ForgedBlade before = ForgedBladeItem.get(t.stack());
        int max = maxQuality(sp);
        if (before.quality() >= max) return Result.fail("quality_cap", max);
        int q = before.quality() + 1;
        List<ItemStack> cost = List.of(new ItemStack(CoreRegistries.MANA_CRYSTAL.get(), q));
        if (!sp.isCreative() && !take(sp, cost)) return Result.fail("missing_material", costText(cost));
        ForgedBlade after = before.withQuality(q).withSmith(sp.getGameProfile().getName());
        return commit(sp, t, before, after, Component.translatable("message.isekai_mahou.class_smith.tempered",
                ForgedBladeItem.stars(q)));
    }

    /** Forja uma lâmina nova (adaga do material) direto no inventário. */
    public static Result forgeNew(ServerPlayer sp, BladeMaterial material) {
        if (!sp.isCreative() && !take(sp, material.cost())) return Result.fail("missing_material", costText(material.cost()));
        ForgedBlade blade = ForgedBlade.DEFAULT.withMaterial(material).withSmith(sp.getGameProfile().getName());
        ItemStack stack = ForgedBladeItem.create(blade);
        if (sp.getMainHandItem().isEmpty()) sp.setItemInHand(InteractionHand.MAIN_HAND, stack);
        else give(sp, stack);
        SmithProgress.onForged(sp, blade);
        celebrate(sp, InteractionHand.MAIN_HAND, blade);
        MutableComponent msg = Component.translatable("message.isekai_mahou.class_smith.forged_new", stack.getHoverName())
                .withStyle(ChatFormatting.GOLD);
        sp.displayClientMessage(msg, true);
        return new Result(true, msg);
    }

    // =====================================================================================
    //  Inventário
    // =====================================================================================

    /** Tira os itens do inventário (tudo ou nada). */
    public static boolean take(ServerPlayer sp, List<ItemStack> cost) {
        for (ItemStack c : cost) if (count(sp, c.getItem()) < c.getCount()) return false;
        for (ItemStack c : cost) {
            int left = c.getCount();
            var inv = sp.getInventory();
            for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
                ItemStack s = inv.getItem(i);
                if (s.is(c.getItem()) && !ForgedBladeItem.is(s)) {
                    int n = Math.min(left, s.getCount());
                    s.shrink(n);
                    left -= n;
                }
            }
        }
        return true;
    }

    public static int count(ServerPlayer sp, Item item) {
        int n = 0;
        var inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }

    private static void give(ServerPlayer sp, ItemStack stack) {
        if (!sp.getInventory().add(stack)) sp.drop(stack, false);
    }

    /** "3× Lingote de Ferro, 1× Graveto". */
    public static MutableComponent costText(List<ItemStack> cost) {
        MutableComponent out = Component.empty();
        for (int i = 0; i < cost.size(); i++) {
            if (i > 0) out.append(", ");
            out.append(cost.get(i).getCount() + "× ").append(cost.get(i).getHoverName());
        }
        return out;
    }

    /** Fagulhas e som de martelada na mão que segura a lâmina. */
    public static void celebrate(ServerPlayer sp, InteractionHand hand, ForgedBlade blade) {
        ServerLevel sl = sp.serverLevel();
        Vec3 look = sp.getLookAngle();
        Vec3 side = new Vec3(-look.z, 0, look.x).normalize().scale(hand == InteractionHand.MAIN_HAND ? 0.35 : -0.35);
        Vec3 at = sp.getEyePosition().add(look.scale(0.6)).add(side).add(0, -0.45, 0);
        SmithFx.forgeSparks(sl, at, blade.material() == BladeMaterial.STARMETAL ? SmithFx.STAR : SmithFx.MOLTEN, 22);
        Element rune = blade.runeElement();
        if (rune != null) sl.sendParticles(SmithFx.dust(rune.color, 1.0F), at.x, at.y, at.z, 10, 0.2, 0.25, 0.2, 0);
        SmithFx.sound(sl, at, SoundEvents.ANVIL_USE, 0.55F, 1.1F + sl.random.nextFloat() * 0.2F);
        SmithFx.sound(sl, at, SoundEvents.FIRE_EXTINGUISH, 0.25F, 1.6F);
        sp.swing(hand, true);
    }

    /** Segura um Martelo do Ferreiro em alguma mão (a forja por glifo fica mais forte). */
    public static boolean holdsHammer(ServerPlayer sp) {
        return sp.getMainHandItem().getItem() instanceof SmithHammerItem || sp.getOffhandItem().getItem() instanceof SmithHammerItem;
    }

    private ForgeService() {}
}
