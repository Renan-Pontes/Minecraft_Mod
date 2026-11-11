package com.renanpontes.isekaimahou.feature.class_quincy.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_quincy.QuincyFinalForms;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;

/**
 * O Todo-Poderoso (homenagem a "The Almighty", Letra A) — forma G / ultimate. Por 20 s (Estender aumenta) você VÊ O
 * FUTURO: 45% de chance de esquivar de cada golpe (passo para o lado com imagem residual); quem vai atacar você brilha e
 * a linha do golpe/trajetória do projétil aparece antes; e a cada 5 s o primeiro golpe pesado (≥ 20% da vida, ou letal)
 * é REESCRITO — anulado e devolvido a quem atacou, com os efeitos da magia. Funciona sem efeitos.
 * Rank V + 8 Auswählen + 6 chefes.
 */
public class AlmightyForm extends FormGlyph {
    public static final int DURATION = 400;

    public AlmightyForm() {
        super("quincy_almighty", 100);
        origin(MagicPath.QUINCY);
        element(Element.LIGHT);
        tier(5);
        minCircle(9);
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
    public int baseCastTicks() {
        return 30;
    }

    @Override
    public int baseCooldown() {
        return 1800;
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
    public void cast(SpellContext ctx) {
        QuincyFinalForms.startAlmighty(ctx, ctx.formStats().duration(DURATION));
    }
}
