package com.renanpontes.isekaimahou.feature.legends_c.infinity;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Infinito (Mugen). O efeito só guarda a duração (ícone no inventário, salva com o jogador) e chama a lógica do
 * {@link Infinity} a cada tick no servidor: frear quem se aproxima, segurar projéteis, drenar mana.
 */
public class MugenEffect extends MobEffect {
    public MugenEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (!entity.level().isClientSide) Infinity.tick(entity);
        return true;
    }
}
