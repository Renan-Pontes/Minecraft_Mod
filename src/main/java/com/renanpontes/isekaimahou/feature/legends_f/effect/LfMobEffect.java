package com.renanpontes.isekaimahou.feature.legends_f.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Efeitos de status das técnicas do legends_f. A maioria só carrega modificadores de atributo (Motosserra, Modo Sábio,
 * Contrato, Monarca); as regras ficam nos ouvintes de eventos de cada técnica. O Sono Encantado também age a cada
 * tick: a criatura para, larga o alvo e não pula.
 */
public class LfMobEffect extends MobEffect {
    private final boolean sleep;

    public LfMobEffect(MobEffectCategory category, int color, boolean sleep) {
        super(category, color);
        this.sleep = sleep;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return sleep;
    }

    @Override
    public boolean applyEffectTick(LivingEntity e, int amplifier) {
        if (!sleep || e.level().isClientSide) return true;
        if (e instanceof Mob m) {
            m.getNavigation().stop();
            if (m.getTarget() != null) m.setTarget(null);
            m.setAggressive(false);
            m.setJumping(false);
        }
        Vec3 v = e.getDeltaMovement();
        e.setDeltaMovement(0, Math.min(0, v.y), 0);
        if (e instanceof net.minecraft.world.entity.player.Player && e.tickCount % 5 == 0) e.hurtMarked = true;
        return true;
    }
}
