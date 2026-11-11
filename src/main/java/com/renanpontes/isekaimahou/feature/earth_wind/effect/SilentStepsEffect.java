package com.renanpontes.isekaimahou.feature.earth_wind.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * Passos Silenciosos: a cada segundo, mobs que te perseguem de longe (sem terem sido atacados por voce)
 * perdem o alvo. A deteccao reduzida e o silencio das vibracoes ficam nos eventos do modulo.
 */
public class SilentStepsEffect extends EwMobEffect {
    /** Distancia (blocos) a partir da qual os mobs nao te percebem. */
    public static final double NOTICE_RANGE = 4.0;

    public SilentStepsEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 20 == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity e, int amplifier) {
        if (!(e.level() instanceof ServerLevel level)) return true;
        double keep = (NOTICE_RANGE + 2) * (NOTICE_RANGE + 2);
        for (Mob m : level.getEntitiesOfClass(Mob.class, e.getBoundingBox().inflate(32), m -> m.getTarget() == e)) {
            if (m.distanceToSqr(e) > keep && m.getLastHurtByMob() != e) m.setTarget(null);
        }
        return true;
    }
}
