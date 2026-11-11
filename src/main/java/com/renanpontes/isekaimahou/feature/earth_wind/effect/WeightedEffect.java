package com.renanpontes.isekaimahou.feature.earth_wind.effect;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Peso: os atributos cuidam de lentidao/pulo/gravidade. Aqui so puxamos para baixo quem voa sem gravidade
 * (morcegos, phantoms, invocacoes voadoras), que os atributos nao afetam.
 */
public class WeightedEffect extends EwMobEffect {
    public WeightedEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(LivingEntity e, int amplifier) {
        if (!(e.level() instanceof ServerLevel level) || e instanceof Player || e.onGround()) return true;
        if (e.isNoGravity()) {
            Vec3 dm = e.getDeltaMovement();
            e.setDeltaMovement(dm.x * 0.8, Math.min(dm.y, -0.25 - 0.1 * amplifier), dm.z * 0.8);
            e.hurtMarked = true;
        }
        if (e.tickCount % 4 == 0) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.GRAVEL.defaultBlockState()),
                    e.getX(), e.getY() + e.getBbHeight(), e.getZ(), 2, e.getBbWidth() * 0.4, 0.1, e.getBbWidth() * 0.4, 0);
        }
        return true;
    }
}
