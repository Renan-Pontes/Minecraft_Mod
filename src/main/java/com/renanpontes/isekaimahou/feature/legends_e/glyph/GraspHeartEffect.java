package com.renanpontes.isekaimahou.feature.legends_e.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.legends_e.entity.GraspHeartEntity;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Agarrar o Coração (Sombra, tier 4, Círculo 5): uma mão espectral surge diante do alvo, arranca o coração dele (que
 * flutua batendo cada vez mais rápido) e o esmaga. Criatura comum com até 60 de vida (×poder) morre na hora; as
 * outras levam dano sombrio, sangram e ficam atordoadas. Chefes e jogadores resistem: perdem 15% da vida máxima e ficam
 * Atordoados por 2 s.
 */
public class GraspHeartEffect extends EffectGlyph {
    public GraspHeartEffect() {
        super("grasp_heart", 30);
        element(Element.SHADOW);
        tier(4);
        minCircle(5);
    }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        if (target instanceof LivingEntity le && le.isAlive()) GraspHeartEntity.grasp(ctx, le, stats);
    }
}
