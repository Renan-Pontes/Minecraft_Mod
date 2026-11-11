package com.renanpontes.isekaimahou.feature.legends_d.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Tempo Parado: os atributos (velocidade, pulo, gravidade, alcance) vêm zerados pelos modificadores do registro;
 * aqui só seguramos o corpo no lugar (sem impulso guardado). Quem prende/solta é o {@code TimeStop} do servidor —
 * e ele mesmo tira o efeito de quem não está mais numa parada, então nunca fica preso.
 */
public class TimeStoppedEffect extends MobEffect {
    public TimeStoppedEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        Vec3 v = entity.getDeltaMovement();
        if (v.lengthSqr() > 1.0E-6) entity.setDeltaMovement(Vec3.ZERO);
        entity.fallDistance = 0F;
        if (entity.isUsingItem()) entity.stopUsingItem();
        return true;
    }
}
