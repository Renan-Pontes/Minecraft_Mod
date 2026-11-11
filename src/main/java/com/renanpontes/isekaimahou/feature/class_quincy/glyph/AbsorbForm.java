package com.renanpontes.isekaimahou.feature.class_quincy.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_quincy.QuincyAbsorption;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

/**
 * Absorção: por 3 s (Estender aumenta) um campo de Reishi em volta do conjurador engole projéteis e magias inimigas e
 * puxa a energia de quem estiver dentro; ao acabar, DEVOLVE tudo como uma rajada de flechas espirituais (1 por carga,
 * máx. 8) que aplicam os efeitos da magia. Raio base 4 (Expandir). Custo calculado como 3 cópias (a média de cargas).
 */
public class AbsorbForm extends FormGlyph {
    public AbsorbForm() {
        super("quincy_absorb", 7);
        origin(MagicPath.QUINCY);
        element(Element.LIGHT);
        tier(2);
    }

    @Override
    public float copies(SpellStats stats, int circle) {
        return 3F;
    }

    @Override
    public int baseCooldown() {
        return 80;
    }

    @Override
    public int hintDuration() {
        return 60;
    }

    @Override
    public float hintRadius() {
        return 4F;
    }

    @Override
    public float hintRange() {
        return 16F;
    }

    @Override
    public void cast(SpellContext ctx) {
        SpellStats s = ctx.formStats();
        QuincyAbsorption.startField(ctx, s.duration(60), s.radius(4F));
    }
}
