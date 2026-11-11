package com.renanpontes.isekaimahou.feature.class_knight;

import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.feature.class_knight.item.LegendarySwordItem;
import com.renanpontes.isekaimahou.feature.class_knight.item.RunicBladeItem;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;

/**
 * Regras das lâminas: o que conta como espada, quantos espaços de runa cada uma tem, quanto a runa custa e rende.
 * <ul>
 *   <li>Qualquer espada (vanilla ou de outro mod): 1 espaço, golpe rúnico a 85% do poder e 28% do custo da magia.</li>
 *   <li>Lâmina Rúnica: grau I 2 espaços · II 3 · III 4 · IV 5; 100% do poder; custo 22% → 16%.</li>
 *   <li>Espadas Lendárias: 4 espaços, 110% do poder e 15% do custo.</li>
 *   <li>Juramento IV: +1 espaço em qualquer lâmina.</li>
 * </ul>
 */
public final class KnightSwords {
    public static boolean isSword(ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() instanceof SwordItem || stack.is(ItemTags.SWORDS));
    }

    public static boolean holdsSword(@Nullable LivingEntity e) {
        return e != null && isSword(e.getMainHandItem());
    }

    /** Espaços de runa da arma, sem o bônus do Juramento. 0 = não é lâmina. */
    public static int baseSlots(ItemStack stack) {
        if (stack.getItem() instanceof RunicBladeItem rb) return rb.tierNumber() + 1;
        if (stack.getItem() instanceof LegendarySwordItem) return 4;
        return isSword(stack) ? 1 : 0;
    }

    public static int runeSlots(ItemStack stack, @Nullable MagicData data) {
        int base = baseSlots(stack);
        if (base > 0 && data != null && data.counter(KnightCounters.OATH) >= 4) base++;
        return Math.min(RuneData.MAX, base);
    }

    public static RuneData runes(ItemStack stack) {
        if (stack.isEmpty()) return RuneData.EMPTY;
        RuneData d = stack.get(KnightRegistries.RUNES.get());
        return d == null ? RuneData.EMPTY : d;
    }

    public static void setRunes(ItemStack stack, RuneData data) {
        if (data == null || data.isEmpty()) stack.remove(KnightRegistries.RUNES.get());
        else stack.set(KnightRegistries.RUNES.get(), data);
    }

    /** Fração do custo da magia paga a cada golpe rúnico. */
    public static float procCost(ItemStack stack) {
        if (stack.getItem() instanceof RunicBladeItem rb) return 0.24F - 0.02F * rb.tierNumber();
        if (stack.getItem() instanceof LegendarySwordItem) return 0.15F;
        return 0.28F;
    }

    /** Poder do golpe rúnico (multiplicador da magia gravada). */
    public static float procPower(ItemStack stack) {
        if (stack.getItem() instanceof RunicBladeItem) return 1.0F;
        if (stack.getItem() instanceof LegendarySwordItem) return 1.1F;
        return 0.85F;
    }

    @Nullable
    public static LegendSword legendOf(ItemStack stack) {
        return stack.getItem() instanceof LegendarySwordItem ls ? ls.legend() : null;
    }

    @Nullable
    public static LegendSword legendIn(@Nullable LivingEntity e) {
        return e == null ? null : legendOf(e.getMainHandItem());
    }

    /** Linhas da dica com as runas gravadas (compartilhado pelas Lâminas Rúnicas e Lendárias). */
    public static void runeTooltip(ItemStack stack, int slots, List<Component> tooltip) {
        RuneData d = runes(stack);
        tooltip.add(Component.translatable("tooltip.isekai_mahou.class_knight.rune_slots", d.size(), slots)
                .withStyle(ChatFormatting.AQUA));
        for (int i = 0; i < d.size(); i++) {
            SpellRecipe r = d.runes().get(i);
            Component name = r.name().isBlank() ? Component.translatable("tooltip.isekai_mahou.class_knight.rune_unnamed")
                    : Component.literal(r.name());
            int rgb = r.color() >= 0 ? r.color() : 0x8FD8FF;
            tooltip.add(Component.translatable("tooltip.isekai_mahou.class_knight.rune_line", name).withColor(rgb));
        }
        if (d.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.isekai_mahou.class_knight.rune_empty").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.isekai_mahou.class_knight.rune_clear").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private KnightSwords() {}
}
