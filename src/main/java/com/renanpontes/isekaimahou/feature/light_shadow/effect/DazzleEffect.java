package com.renanpontes.isekaimahou.feature.light_shadow.effect;

import com.renanpontes.isekaimahou.feature.light_shadow.LsFx;
import com.renanpontes.isekaimahou.feature.light_shadow.ai.LsAi;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/** Ofuscado (Cegar/Escuridão): a Cegueira do vanilla não afeta mobs, então aqui eles perdem o alvo e ficam perdidos. */
public class DazzleEffect extends MobEffect {
    public DazzleEffect() {
        super(MobEffectCategory.HARMFUL, 0xFFF6C0);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 5 == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity e, int amplifier) {
        if (!(e.level() instanceof ServerLevel sl)) return true;
        if (e instanceof Mob mob) {
            LsAi.clearTarget(mob);
            // Vira a cabeça para os lados, desnorteado
            mob.setYHeadRot(mob.getYHeadRot() + (sl.random.nextFloat() - 0.5F) * 120F);
        }
        sl.sendParticles(LsFx.dust(LsFx.WHITE_GOLD, 0.8F), e.getX(), e.getEyeY() + 0.15, e.getZ(), 3, 0.25, 0.1, 0.25, 0);
        if (sl.random.nextInt(3) == 0) {
            sl.sendParticles(ParticleTypes.END_ROD, e.getX(), e.getEyeY() + 0.3, e.getZ(), 1, 0.2, 0.05, 0.2, 0.005);
        }
        return true;
    }
}
