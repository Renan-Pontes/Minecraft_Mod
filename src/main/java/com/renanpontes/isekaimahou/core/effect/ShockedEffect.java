package com.renanpontes.isekaimahou.core.effect;

import com.renanpontes.isekaimahou.core.status.StatusEngine;

import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/** Eletrizado: a cada segundo, chance de micro-atordoar, drena mana (jogador) e arqueia para alvos Molhados por perto. */
public class ShockedEffect extends MagicEffect {
    public ShockedEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 20 == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (!entity.level().isClientSide) StatusEngine.tickShocked(entity, amplifier + 1);
        return true;
    }
}
