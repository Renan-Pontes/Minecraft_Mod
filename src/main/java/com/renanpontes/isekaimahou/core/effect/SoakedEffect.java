package com.renanpontes.isekaimahou.core.effect;

import com.renanpontes.isekaimahou.core.status.StatusEngine;

import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/** Molhado (id "soaked"): apaga o fogo e não deixa ficar Em Chamas. As gotas são desenhadas pelo cliente. */
public class SoakedEffect extends MagicEffect {
    public SoakedEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 5 == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide) return true;
        if (entity.isOnFire()) entity.clearFire();
        StatusEngine.extinguishBurning(entity);
        return true;
    }
}
