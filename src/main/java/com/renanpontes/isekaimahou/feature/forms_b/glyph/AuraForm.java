package com.renanpontes.isekaimahou.feature.forms_b.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.forms_b.util.AuraManager;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

/**
 * Aura: por 200×duração ticks aplica os efeitos a cada segundo em volta de você (raio 3×área),
 * gastando 2 de mana por segundo (acaba se o mana acabar). Uma aura por vez: a nova substitui.
 */
public class AuraForm extends FormGlyph {
    public AuraForm() {
        super("aura", 15);
        element(Element.ARCANE);
        tier(3);
        minCircle(3);
    }

    @Override
    public int baseCastTicks() { return 15; }

    @Override
    public int baseCooldown() { return 200; }

    @Override
    public void cast(SpellContext ctx) {
        if (ctx.caster() == null) return;
        SpellStats s = ctx.formStats();
        AuraManager.start(ctx, s.radius(3.0F), s.duration(200));
    }
}
