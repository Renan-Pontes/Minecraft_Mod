package com.renanpontes.isekaimahou.feature.light_shadow.effect;

import com.renanpontes.isekaimahou.feature.light_shadow.LsFx;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/** Graça da Ressurreição: totem temporário (o salvamento acontece em LsEvents, no LivingDeathEvent). */
public class ResurrectionGraceEffect extends MobEffect {
    public ResurrectionGraceEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xFFE9A0);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 10 == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity e, int amplifier) {
        if (e.level() instanceof ServerLevel sl) {
            // Auréola dourada girando sobre a cabeça
            double y = e.getY() + e.getBbHeight() + 0.35;
            double base = e.tickCount * 0.15;
            for (int i = 0; i < 6; i++) {
                double a = base + i * Math.PI / 3;
                sl.sendParticles(LsFx.dust(LsFx.GOLD, 0.7F), e.getX() + Math.cos(a) * 0.35, y, e.getZ() + Math.sin(a) * 0.35, 1, 0, 0, 0, 0);
            }
        }
        return true;
    }
}
