package com.renanpontes.isekaimahou.glyph.weapon;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.glyph.TriggerGlyph;

/**
 * Aparar (gatilho reativo, vai no começo da receita): o slot sai SOZINHO, mirando o atacante, quando o jogador
 * bloqueia um golpe com o escudo ou é atingido corpo a corpo logo depois de começar a atacar (0,3 s) — esse
 * "contra-golpe no tempo certo" ainda reduz 25% do dano. Apertar a tecla do slot não conjura. Ver {@link WeaponParry}.
 */
public class ParryTrigger extends TriggerGlyph {
    public ParryTrigger() {
        super(WeaponGlyphs.PARRY, 4, true);
        element(Element.ARCANE);
        tier(2);
        minCircle(1);
    }
}
