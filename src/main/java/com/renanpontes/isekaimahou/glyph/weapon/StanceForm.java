package com.renanpontes.isekaimahou.glyph.weapon;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;

/**
 * Postura: enquanto o jogador SEGURA a tecla do slot, os efeitos se aplicam nele a cada 1 s (Pele de Pedra,
 * Curar, Guarda, Velocidade...), andando a 60% e firme contra empurrões. Cada segundo mantido custa 30% do custo
 * da magia. Soltar a tecla (ou ficar sem recurso) desfaz a Postura. Conjurada sem segurar (gatilho, mob, outra
 * fonte), dura 5 s.
 */
public class StanceForm extends FormGlyph {
    public StanceForm() {
        super(WeaponGlyphs.STANCE, 6);
        element(Element.ARCANE);
        tier(2);
        minCircle(1);
    }

    @Override
    public int baseCooldown() { return 30; }

    @Override
    public void cast(SpellContext ctx) {
        if (ctx.caster() == null || !ctx.caster().isAlive()) return;
        WeaponStances.begin(ctx);
    }
}
