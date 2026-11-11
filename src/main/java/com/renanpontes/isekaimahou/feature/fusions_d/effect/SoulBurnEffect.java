package com.renanpontes.isekaimahou.feature.fusions_d.effect;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Queimar Alma (indicador): chama azul-negra que solta almas. O dano é feito pelo {@link SoulBurn} (que guarda a
 * conjuração para dar o crédito e o poder certos); este efeito dá o ícone, a cor e as partículas.
 */
public class SoulBurnEffect extends MobEffect {
    public SoulBurnEffect() {
        super(MobEffectCategory.HARMFUL, 0x3FC8E8, ParticleTypes.SOUL_FIRE_FLAME);
    }
}
