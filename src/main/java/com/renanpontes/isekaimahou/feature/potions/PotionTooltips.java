package com.renanpontes.isekaimahou.feature.potions;

import java.util.List;
import java.util.function.Supplier;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Linhas de dica dos frascos (números já na escala da v1.0). */
public final class PotionTooltips {
    private PotionTooltips() {}

    private static final String POTENCY_PREFIX = "potion.potency.";

    /**
     * Nome do recurso do jogador local ("Mana"/"Vigor"/"Foco", colorido). O cliente preenche (PotionsClient);
     * no servidor fica o texto genérico.
     */
    public static Supplier<Component> localResourceName = () -> null;

    public static void add(PotionKind kind, PotionForm form, PotionTier tier, List<Component> out) {
        if (kind == PotionKind.HEALTH) {
            int pct = Math.round(tier.healPct * form.instantMult * 100);
            if (tier.healFlat <= 0) {
                out.add(Component.translatable("tooltip.isekai_mahou.potions.heal_pct", pct).withStyle(ChatFormatting.RED));
            } else {
                out.add(Component.translatable("tooltip.isekai_mahou.potions.heal",
                        fmt(tier.healFlat * form.instantMult), pct).withStyle(ChatFormatting.RED));
            }
        } else {
            Component res = localResourceName.get();
            if (res == null) res = Component.translatable("tooltip.isekai_mahou.potions.resource_any");
            int pct = Math.round(tier.manaPct * form.instantMult * 100);
            out.add(Component.translatable("tooltip.isekai_mahou.potions.restore", pct, res).withStyle(ChatFormatting.BLUE));
        }
        int secs = tier.flowSeconds * form.flowMult;
        MutableComponent effect = Component.translatable(kind == PotionKind.HEALTH
                ? "effect.isekai_mahou.vital_flow" : "effect.isekai_mahou.arcane_flow");
        // "II", "III"... = chaves de potência do próprio vanilla
        if (tier.flowAmp > 0) effect.append(" ").append(Component.translatable(POTENCY_PREFIX + tier.flowAmp));
        out.add(Component.translatable(kind == PotionKind.HEALTH
                        ? "tooltip.isekai_mahou.potions.flow_health" : "tooltip.isekai_mahou.potions.flow_mana",
                effect, tier.flowAmp + 1, secs).withStyle(ChatFormatting.LIGHT_PURPLE));
        if (tier.legendary()) {
            out.add(Component.translatable(kind == PotionKind.HEALTH
                    ? "tooltip.isekai_mahou.potions.cleanse_health" : "tooltip.isekai_mahou.potions.cleanse_mana")
                    .withStyle(ChatFormatting.GOLD));
        }
        if (form == PotionForm.SPLASH) {
            out.add(Component.translatable(kind == PotionKind.HEALTH
                            ? "tooltip.isekai_mahou.potions.splash_health" : "tooltip.isekai_mahou.potions.splash_mana",
                    fmt((float) tier.splashRadius())).withStyle(ChatFormatting.AQUA));
        } else if (form == PotionForm.LONG) {
            out.add(Component.translatable("tooltip.isekai_mahou.potions.long").withStyle(ChatFormatting.AQUA));
        }
        out.add(Component.translatable("tooltip.isekai_mahou.potions.cooldown", tier.cooldownSeconds)
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    private static String fmt(float v) {
        return v == Math.floor(v) ? Integer.toString((int) v) : String.format(java.util.Locale.ROOT, "%.1f", v);
    }
}
