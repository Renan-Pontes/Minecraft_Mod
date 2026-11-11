package com.renanpontes.isekaimahou.core.effect;

import com.renanpontes.isekaimahou.core.status.StatusEngine;

import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Petrificação: acúmulos 1–2 = Enrijecendo (só lentidão, pelos atributos); 3 (amplificador 2) = Petrificado: virou
 * pedra — preso no lugar, +50% de armadura, sem recuo.
 */
public class PetrifiedEffect extends MagicEffect {
    public PetrifiedEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return amplifier >= 2;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (!entity.level().isClientSide) StatusEngine.holdInPlace(entity);
        return true;
    }
}
