package com.renanpontes.isekaimahou.feature.light_shadow.effect;

import com.renanpontes.isekaimahou.feature.light_shadow.LsFx;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/** Vínculo de Dor: marca visual (o espalhamento do dano fica em LsEvents, no LivingDamageEvent.Post). */
public class PainLinkEffect extends MobEffect {
    public PainLinkEffect() {
        super(MobEffectCategory.HARMFUL, 0x9B1B30);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 8 == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity e, int amplifier) {
        if (e.level() instanceof ServerLevel sl) {
            // Fios de sangue girando em volta do alvo
            double a = e.tickCount * 0.4;
            double r = e.getBbWidth() * 0.6 + 0.3;
            for (int i = 0; i < 3; i++) {
                double ang = a + i * Math.PI * 2 / 3;
                sl.sendParticles(LsFx.dust(LsFx.BLOOD, 0.9F), e.getX() + Math.cos(ang) * r, e.getY() + e.getBbHeight() * 0.6,
                        e.getZ() + Math.sin(ang) * r, 1, 0, 0.05, 0, 0);
            }
            if (sl.random.nextInt(4) == 0) {
                sl.sendParticles(ParticleTypes.DAMAGE_INDICATOR, e.getX(), e.getY() + e.getBbHeight(), e.getZ(), 1, 0.2, 0.1, 0.2, 0.05);
            }
        }
        return true;
    }
}
