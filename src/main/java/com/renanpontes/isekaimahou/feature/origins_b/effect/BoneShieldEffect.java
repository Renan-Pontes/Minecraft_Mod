package com.renanpontes.isekaimahou.feature.origins_b.effect;

import com.renanpontes.isekaimahou.feature.origins_b.ObFx;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Escudo de Ossos: lascas de osso orbitam o corpo e viram absorção (como o efeito vanilla, mas maior).
 * Enquanto houver escudo, parte do dano corpo a corpo volta para quem bateu (ver ObEvents). Quando a absorção
 * acaba, o escudo se estilhaça e o efeito termina.
 */
public class BoneShieldEffect extends MobEffect {
    /** Absorção por nível (amplificador 0 = 6 de vida = 3 corações). */
    public static final double ABSORPTION_PER_LEVEL = 6.0;

    public BoneShieldEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xE8E0C8);
    }

    @Override
    public void onEffectStarted(LivingEntity entity, int amplifier) {
        super.onEffectStarted(entity, amplifier);
        entity.setAbsorptionAmount(Math.max(entity.getAbsorptionAmount(), (float) (ABSORPTION_PER_LEVEL * (1 + amplifier))));
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide) return true;
        ServerLevel level = (ServerLevel) entity.level();
        if (entity.getAbsorptionAmount() <= 0.0F) {
            // Estilhaçou
            ObFx.boneBurst(level, entity.position().add(0, entity.getBbHeight() * 0.5, 0), 20, 0.4);
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.BONE_BLOCK_BREAK, SoundSource.PLAYERS, 1.0F, 0.8F);
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.SKELETON_HURT, SoundSource.PLAYERS, 0.6F, 0.6F);
            return false;
        }
        if (entity.tickCount % 4 == 0) ObFx.boneOrbit(level, entity);
        return true;
    }
}
