package com.renanpontes.isekaimahou.feature.light_shadow.effect;

import com.renanpontes.isekaimahou.feature.light_shadow.LsFx;
import com.renanpontes.isekaimahou.feature.light_shadow.ai.LsAi;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.Vec3;

/**
 * Amedrontado (Medo/Pesadelo): mobs largam o alvo e fogem da origem do medo (um objetivo temporário de fuga
 * entra na IA; mobs sem navegação por terra, como fantasmas e slimes, são empurrados para longe).
 */
public class FearEffect extends MobEffect {
    public FearEffect() {
        super(MobEffectCategory.HARMFUL, 0x3B1D5A);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 10 == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity e, int amplifier) {
        if (!(e.level() instanceof ServerLevel sl)) return true;
        if (e instanceof Mob mob) {
            LsAi.clearTarget(mob);
            if (mob instanceof PathfinderMob pm) {
                LsAi.ensureFlee(pm);
            } else {
                Vec3 away = e.position().subtract(LsAi.fearSource(e));
                if (away.lengthSqr() > 1.0E-4) {
                    away = away.normalize().scale(0.35);
                    e.push(away.x, mob.isNoGravity() ? away.y * 0.5 : 0.1, away.z);
                    e.hurtMarked = true;
                }
            }
        }
        sl.sendParticles(LsFx.dust(LsFx.DEEP_VIOLET, 0.9F), e.getX(), e.getEyeY() + 0.25, e.getZ(), 3, 0.3, 0.1, 0.3, 0);
        if (sl.random.nextInt(3) == 0) {
            sl.sendParticles(ParticleTypes.SCULK_SOUL, e.getX(), e.getEyeY() + 0.2, e.getZ(), 1, 0.15, 0.1, 0.15, 0.01);
        }
        return true;
    }
}
