package com.renanpontes.isekaimahou.feature.class_rogue;

/** Chaves de tradução montadas por partes (graus, ranks, requisitos, frases por tier). */
public final class RogueKeys {
    /** {@code prefix + suffix} (ex.: {@code "class_rogue.isekai_mahou.rank." + 3}). */
    public static String k(String prefix, Object suffix) {
        return prefix + suffix;
    }

    private RogueKeys() {}
}
