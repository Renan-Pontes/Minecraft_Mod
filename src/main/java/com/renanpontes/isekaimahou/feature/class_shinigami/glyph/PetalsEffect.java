package com.renanpontes.isekaimahou.feature.class_shinigami.glyph;

import com.renanpontes.isekaimahou.feature.class_shinigami.Archetype;

/** Pétalas de Lâmina (homenagem Senbonzakura): mil pétalas cortantes abrem feridas — Sangrando acumulado (dobra se o alvo se mexe). */
public class PetalsEffect extends ShikaiEffect {
    public PetalsEffect() {
        super("shikai_petals", 10, Archetype.PETALS, 2.5F, 100);
    }
}
