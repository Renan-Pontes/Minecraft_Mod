package com.renanpontes.isekaimahou.feature.class_steelball;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.boss.BossKit;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * Aplica o efeito Girado ({@code steelball_spun}): a rotação entra no corpo do alvo — ele gira sem controle, anda mais
 * devagar e bate mais fraco. Acertos seguidos acumulam (nível I → III). Chefes giram só metade do tempo e não rodopiam.
 */
public final class SbSpin {
    public static final int MAX_LEVEL = 2;

    public static void apply(LivingEntity target, @Nullable LivingEntity source, int amplifier, int ticks) {
        if (target == null || !target.isAlive()) return;
        MobEffectInstance old = target.getEffect(SbRegistry.SPUN);
        int amp = Math.max(0, amplifier);
        if (old != null) amp = Math.min(MAX_LEVEL, Math.max(amp, old.getAmplifier() + 1));
        int dur = BossKit.isBossLike(target) ? ticks / 2 : ticks;
        if (old != null) dur = Math.max(dur, old.getDuration());
        target.addEffect(new MobEffectInstance(SbRegistry.SPUN, Math.max(10, dur), amp, false, true, true), source);
    }

    public static int level(LivingEntity target) {
        MobEffectInstance e = target.getEffect(SbRegistry.SPUN);
        return e == null ? -1 : e.getAmplifier();
    }

    private SbSpin() {}
}
