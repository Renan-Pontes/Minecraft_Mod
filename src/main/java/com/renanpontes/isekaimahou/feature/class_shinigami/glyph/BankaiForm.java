package com.renanpontes.isekaimahou.feature.class_shinigami.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_shinigami.BankaiState;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellCost;
import com.renanpontes.isekaimahou.glyph.SpellStats;

/**
 * Bankai — a forma G (ultimate) do Shinigami: a liberação final da zanpakutō por 20 s (Estender aumenta). Transformação
 * (Força II, Resistência I, Velocidade II, Salto), espada ×1,6 com a assinatura reforçada e, a cada segundo, um pulso
 * num raio de 10 (+Expandir) aplica os efeitos da magia e o campo do arquétipo em todos os inimigos. Funciona sem
 * efeitos (só o campo). Custo: pulsos por segundo × duração, de forma moderada (como os Domínios).
 */
public class BankaiForm extends FormGlyph {
    public static final int DURATION = 400;

    public BankaiForm() {
        super("zanpakuto_bankai", 90);
        origin(MagicPath.SHINIGAMI);
        element(Element.SHADOW);
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
        return SpellCost.pulseCopies(stats.duration(DURATION), 20);
    }

    @Override
    public int baseCastTicks() {
        return 30;
    }

    @Override
    public int baseCooldown() {
        return 900;
    }

    @Override
    public int hintDuration() {
        return DURATION;
    }

    @Override
    public float hintRadius() {
        return 10F;
    }

    @Override
    public float hintRange() {
        return 10F;
    }

    @Override
    public void cast(SpellContext ctx) {
        SpellStats s = ctx.formStats();
        BankaiState.start(ctx, s.duration(DURATION), s.radius(10F));
    }
}
