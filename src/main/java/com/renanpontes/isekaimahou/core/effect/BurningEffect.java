package com.renanpontes.isekaimahou.core.effect;

import com.renanpontes.isekaimahou.core.status.StatusEngine;

import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/** Em Chamas: dano mágico contínuo a cada segundo (0,5 + 0,5·acúmulo, × poder de quem aplicou). */
public class BurningEffect extends MagicEffect {
    public BurningEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 20 == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (!entity.level().isClientSide) StatusEngine.tickBurning(entity, amplifier + 1);
        return true;
    }
}
