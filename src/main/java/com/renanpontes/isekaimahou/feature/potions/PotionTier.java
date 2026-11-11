package com.renanpontes.isekaimahou.feature.potions;

import net.minecraft.world.item.Rarity;

/**
 * Tiers das poções do mod (escala inflada da v1.0: vida base 40, mana ≈3×).
 * Vida: cura {@code healFlat + healPct × vida máxima}; recurso: {@code manaPct × máximo} — por fração, porque o máximo de
 * Mana/Vigor/Foco vai de ~300 (nível 1) a ~6000 (nível 200) e um valor fixo ficaria inútil no fim do jogo.
 * Fluxo (regen): {@code flowAmp + 1} de vida/s (Fluxo Vital) ou {@code flowAmp + 1}% do recurso/s (Fluxo Arcano).
 */
public enum PotionTier {
    LESSER("lesser", 6F, 0.10F, 0.15F, 0, 4, 8, Rarity.COMMON, 0xF06A7A, 0x6FB6FF),
    COMMON("common", 10F, 0.15F, 0.22F, 0, 6, 10, Rarity.COMMON, 0xE8384F, 0x4A8CFF),
    GREATER("greater", 16F, 0.20F, 0.30F, 1, 6, 12, Rarity.UNCOMMON, 0xD41E3C, 0x3C63F0),
    SUPERIOR("superior", 24F, 0.28F, 0.42F, 2, 6, 15, Rarity.RARE, 0xB5122F, 0x5A3CF0),
    SUPREME("supreme", 32F, 0.38F, 0.58F, 3, 8, 18, Rarity.EPIC, 0xFF2E63, 0x8A3CFF),
    /** Elixir Lendário: cura/restaura TUDO, fluxo forte e purifica. Recarga longa. */
    LEGENDARY("legendary", 0F, 1.0F, 1.0F, 4, 10, 45, Rarity.EPIC, 0xFF4A3D, 0xB05CFF);

    public final String id;
    public final float healFlat;
    public final float healPct;
    public final float manaPct;
    public final int flowAmp;
    public final int flowSeconds;
    public final int cooldownSeconds;
    public final Rarity rarity;
    public final int healthColor;
    public final int manaColor;

    PotionTier(String id, float healFlat, float healPct, float manaPct, int flowAmp, int flowSeconds, int cooldownSeconds,
               Rarity rarity, int healthColor, int manaColor) {
        this.id = id;
        this.healFlat = healFlat;
        this.healPct = healPct;
        this.manaPct = manaPct;
        this.flowAmp = flowAmp;
        this.flowSeconds = flowSeconds;
        this.cooldownSeconds = cooldownSeconds;
        this.rarity = rarity;
        this.healthColor = healthColor;
        this.manaColor = manaColor;
    }

    public boolean legendary() {
        return this == LEGENDARY;
    }

    public int color(PotionKind kind) {
        return kind == PotionKind.HEALTH ? healthColor : manaColor;
    }

    /** Raio do respingo da versão arremessável. */
    public double splashRadius() {
        return this == LEGENDARY || this == SUPREME ? 5.0 : 4.0;
    }
}
