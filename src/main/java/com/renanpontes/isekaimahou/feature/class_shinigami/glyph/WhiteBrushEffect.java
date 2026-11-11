package com.renanpontes.isekaimahou.feature.class_shinigami.glyph;

import com.renanpontes.isekaimahou.feature.class_shinigami.Archetype;

/** Pincel Branco (homenagem Shirafude Ichimonji): a tinta apaga o nome — Silenciado (sem magias), Fraqueza e perde os efeitos bons; depois fica imune ao pincel por 8 s. */
public class WhiteBrushEffect extends ShikaiEffect {
    public WhiteBrushEffect() {
        super("shikai_white_brush", 12, Archetype.BRUSH, 2.0F, 80);
    }
}
