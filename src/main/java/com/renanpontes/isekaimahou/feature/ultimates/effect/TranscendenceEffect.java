package com.renanpontes.isekaimahou.feature.ultimates.effect;

import java.util.Set;

import com.renanpontes.isekaimahou.feature.ultimates.Transcendence;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.EffectCure;

/**
 * Transcendência: o efeito é só o indicador/cronômetro (ícone no HUD). A lógica — voo, atributos, pulsos, fim com
 * Exaustão Mágica — está em {@link Transcendence}. Leite não tira. Se a transformação não existe mais no servidor
 * (relogou, servidor reiniciou), o efeito se remove sozinho.
 */
public class TranscendenceEffect extends MobEffect {
    public TranscendenceEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xFFE9A8);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 10 == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        // false = o efeito sai (só no servidor; no cliente nada muda)
        return entity.level().isClientSide || Transcendence.isActive(entity);
    }

    @Override
    public void fillEffectCures(Set<EffectCure> cures, MobEffectInstance effectInstance) {}
}
