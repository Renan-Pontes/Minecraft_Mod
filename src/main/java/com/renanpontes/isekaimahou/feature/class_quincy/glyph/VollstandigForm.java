package com.renanpontes.isekaimahou.feature.class_quincy.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_quincy.QuincyVollstandig;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellCost;
import com.renanpontes.isekaimahou.glyph.SpellStats;

/**
 * Vollständig — a forma G (ultimate) do Quincy. Asas de luz e auréola por 12 s (Estender aumenta): plana, resiste,
 * engole projéteis e a cada segundo a auréola dispara uma saraivada nos inimigos (até 4 + Dividir, raio 18 + Expandir),
 * cada flecha com os efeitos. Custo: pulsos por segundo × duração, de forma moderada (como os Domínios).
 */
public class VollstandigForm extends FormGlyph {
    public static final int DURATION = 240;

    public VollstandigForm() {
        super("quincy_vollstandig", 80);
        origin(MagicPath.QUINCY);
        element(Element.LIGHT);
        tier(5);
        minCircle(6);
    }

    @Override
    public boolean isUltimate() {
        return true;
    }

    @Override
    public float copies(SpellStats stats, int circle) {
        return SpellCost.pulseCopies(stats.duration(DURATION), 20) * Math.min(2F, 1F + 0.25F * Math.max(0, stats.split));
    }

    @Override
    public int baseCastTicks() {
        return 20;
    }

    @Override
    public int baseCooldown() {
        return 600;
    }

    @Override
    public int hintDuration() {
        return DURATION;
    }

    @Override
    public float hintRadius() {
        return 18F;
    }

    @Override
    public float hintRange() {
        return 18F;
    }

    @Override
    public void cast(SpellContext ctx) {
        SpellStats s = ctx.formStats();
        QuincyVollstandig.start(ctx, s.duration(DURATION), s.radius(18F), 4 + Math.max(0, s.split));
    }
}
