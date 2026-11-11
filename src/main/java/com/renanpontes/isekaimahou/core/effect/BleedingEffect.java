package com.renanpontes.isekaimahou.core.effect;

import com.renanpontes.isekaimahou.core.status.StatusEngine;

import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/** Sangrando: 0,4 de dano por acúmulo a cada segundo, o dobro se o alvo se moveu no último segundo. */
public class BleedingEffect extends MagicEffect {
    public BleedingEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 20 == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (!entity.level().isClientSide) StatusEngine.tickBleeding(entity, amplifier + 1);
        return true;
    }
}
