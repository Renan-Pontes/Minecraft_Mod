package com.renanpontes.isekaimahou.feature.origins_b.effect;

import com.renanpontes.isekaimahou.feature.origins_b.ObFx;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Contrato de Morte: a marca visível (selo carmesim girando sobre a cabeça). Quem marcou fica gravado nos
 * dados persistentes do alvo; a recompensa é paga no LivingDeathEvent (ver ObEvents).
 */
public class DeathContractEffect extends MobEffect {
    public DeathContractEffect() {
        super(MobEffectCategory.HARMFUL, 0x9E1B22);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 3 == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level() instanceof ServerLevel level) ObFx.contractSigil(level, entity);
        return true;
    }
}
