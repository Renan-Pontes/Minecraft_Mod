package com.renanpontes.isekaimahou.feature.class_shinigami.glyph;

import com.renanpontes.isekaimahou.feature.class_shinigami.Archetype;

/** Peso (homenagem Wabisuke): cada acerto DOBRA o peso do alvo — lentidão crescente, cansaço, depois preso ao chão e exposto (×32). */
public class WeightEffect extends ShikaiEffect {
    public WeightEffect() {
        super("shikai_weight", 10, Archetype.WEIGHT, 3.0F, 100);
    }
}
