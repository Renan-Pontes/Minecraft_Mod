package com.renanpontes.isekaimahou.feature.legends_c.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.legends_c.infinity.Infinity;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Infinito (Mugen) — efeito lendário ARCANO (C5), benéfico: em você (Si Mesmo) ou num aliado. Por 10 s (Estender:
 * +5 s cada, de 8 a 20 s; drena 2 de mana por segundo) tudo que se aproxima desacelera e para a 1,5 bloco; projéteis
 * ficam suspensos; golpes são anulados — menos Acerto Garantido (fura tudo) e chefes (25%). Ver {@link Infinity}.
 */
public class InfinityEffect extends EffectGlyph {
    public static final int BASE_TICKS = 200;

    public InfinityEffect() {
        super("infinity", 55);
        element(Element.ARCANE);
        legendary();
        minCircle(5);
    }

    @Override
    public boolean isBeneficial() {
        return true;
    }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        if (!(target instanceof LivingEntity le) || !le.isAlive()) return;
        Infinity.grant(le, stats.duration(BASE_TICKS));
    }
}
