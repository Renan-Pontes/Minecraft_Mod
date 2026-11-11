package com.renanpontes.isekaimahou.feature.class_quincy.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_quincy.QuincyFinalForms;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellCost;
import com.renanpontes.isekaimahou.glyph.SpellStats;

/**
 * Quincy: Letzt Stil (homenagem à forma final) — forma G / ultimate. Por 30 s (Estender aumenta) o Quincy libera TODO o
 * Reishi: técnicas Quincy ×2, Força II, Velocidade II, Resistência I, Regeneração I, plana com asas enormes de luz
 * dourada, engole todo projétil a 8 blocos e a cada 0,75 s um leque de 6 flechas teleguiadas gigantes cai sobre os
 * inimigos a 24 blocos (+Expandir), cada uma com os efeitos. O PREÇO: ao acabar, vida em 50%, Fraqueza II e 10 minutos
 * SEM PODER QUINCY. Custo: pulsos × duração (moderado). Rank V + treino com a Luva Sanrei.
 */
public class LetztStilForm extends FormGlyph {
    public static final int DURATION = 600;

    public LetztStilForm() {
        super("quincy_letzt_stil", 120);
        origin(MagicPath.QUINCY);
        element(Element.LIGHT);
        tier(5);
        minCircle(8);
    }

    @Override
    public boolean isUltimate() {
        return true;
    }

    @Override
    public boolean requiresEffect() {
        return false;
    }

    @Override
    public float copies(SpellStats stats, int circle) {
        return SpellCost.pulseCopies(stats.duration(DURATION), 15);
    }

    @Override
    public int baseCastTicks() {
        return 40;
    }

    @Override
    public int baseCooldown() {
        return 2400;
    }

    @Override
    public int hintDuration() {
        return DURATION;
    }

    @Override
    public float hintRadius() {
        return 24F;
    }

    @Override
    public float hintRange() {
        return 24F;
    }

    @Override
    public void cast(SpellContext ctx) {
        SpellStats s = ctx.formStats();
        QuincyFinalForms.startLetzt(ctx, s.duration(DURATION), s.radius(24F));
    }
}
