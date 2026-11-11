package com.renanpontes.isekaimahou.core.effect;

import com.renanpontes.isekaimahou.core.status.StatusEngine;

import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/** Gelado: lentidão por atributos; mantém a geada parcial do vanilla (vinheta de frio no jogador) sem congelar de vez. */
public class ChilledEffect extends MagicEffect {
    public ChilledEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (!entity.level().isClientSide) StatusEngine.tickChilled(entity, amplifier + 1);
        return true;
    }
}
