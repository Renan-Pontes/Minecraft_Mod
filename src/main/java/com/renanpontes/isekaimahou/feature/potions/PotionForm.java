package com.renanpontes.isekaimahou.feature.potions;

/**
 * Formas do frasco: beber (normal), arremessável (respingo em área nos aliados) e prolongada (menos efeito na hora,
 * fluxo 3× mais longo — boa para segurar uma luta longa).
 */
public enum PotionForm {
    DRINK("", 1.0F, 1, 20),
    SPLASH("splash_", 1.0F, 1, 0),
    LONG("long_", 0.4F, 3, 32);

    public final String prefix;
    /** Multiplicador da cura/restauração instantânea. */
    public final float instantMult;
    /** Multiplicador da duração do fluxo. */
    public final int flowMult;
    /** Tempo para beber (ticks); 0 = arremessa na hora. */
    public final int useTicks;

    PotionForm(String prefix, float instantMult, int flowMult, int useTicks) {
        this.prefix = prefix;
        this.instantMult = instantMult;
        this.flowMult = flowMult;
        this.useTicks = useTicks;
    }

    public String itemId(PotionKind kind, PotionTier tier) {
        return prefix + kind.id + "_potion_" + tier.id;
    }
}
