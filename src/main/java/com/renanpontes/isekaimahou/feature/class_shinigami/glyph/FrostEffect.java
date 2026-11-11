package com.renanpontes.isekaimahou.feature.class_shinigami.glyph;

import com.renanpontes.isekaimahou.feature.class_shinigami.Archetype;

/** Gelo (homenagem Sode no Shirayuki): a dança do gelo — Gelado acumulado; no quinto acúmulo o alvo congela. */
public class FrostEffect extends ShikaiEffect {
    public FrostEffect() {
        super("shikai_frost", 10, Archetype.FROST, 4.0F, 100);
    }
}
