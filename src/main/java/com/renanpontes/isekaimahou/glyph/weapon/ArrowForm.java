package com.renanpontes.isekaimahou.glyph.weapon;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;

/**
 * Flecha: com arco/besta na mão e flecha no inventário (ou no criativo) dispara uma FLECHA REAL do vanilla
 * (gasta 1 flecha e 1 de durabilidade da arma) que, ao acertar entidade ou bloco, aplica os efeitos. Sem arco
 * (ou sem flechas), conjura uma flecha mágica mais fraca que não pode ser recolhida.
 * Valem Dividir (leque), Espalhar, Acelerar, Perfurar, Ricochete, Teleguiado e Expandir área.
 * Pode ser estágio 2+: sai do ponto do estágio anterior como flecha conjurada.
 */
public class ArrowForm extends FormGlyph {
    public ArrowForm() {
        super(WeaponGlyphs.ARROW, 5);
        element(Element.ARCANE);
        tier(1);
    }

    @Override
    public boolean stageable() { return true; }

    @Override
    public int baseCooldown() { return 12; }

    @Override
    public void cast(SpellContext ctx) {
        WeaponArrows.fire(ctx);
    }
}
