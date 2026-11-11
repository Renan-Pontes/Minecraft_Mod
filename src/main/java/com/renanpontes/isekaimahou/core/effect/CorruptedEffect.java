package com.renanpontes.isekaimahou.core.effect;

import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/** Corrompido: dano mágico contínuo (mais rápido com amplificador maior). */
public class CorruptedEffect extends MagicEffect {
    public CorruptedEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        int interval = Math.max(8, 40 >> amplifier);
        return duration % interval == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        entity.hurt(entity.damageSources().magic(), 1.0F + amplifier * 0.5F);
        return true;
    }
}
