package com.renanpontes.isekaimahou.core.effect;

import com.renanpontes.isekaimahou.core.status.StatusEngine;

import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/** Congelado: preso no gelo (atributos zerados + parado no lugar), com o efeito visual de congelamento do jogo. */
public class FrozenEffect extends MagicEffect {
    public FrozenEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        entity.setTicksFrozen(Math.max(entity.getTicksFrozen(), entity.getTicksRequiredToFreeze() + 2));
        if (entity.level().isClientSide) return true;
        if (entity.isOnFire()) entity.clearFire();
        StatusEngine.holdInPlace(entity);
        return true;
    }
}
