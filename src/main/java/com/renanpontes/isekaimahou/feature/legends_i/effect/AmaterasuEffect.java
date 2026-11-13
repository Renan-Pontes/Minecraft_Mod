package com.renanpontes.isekaimahou.feature.legends_i.effect;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.Progression;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Amaterasu (homenagem a Naruto — Itachi/Sasuke): chamas NEGRAS que não se apagam. A cada 0,5 s causam
 * {@code (0,6 + 0,25 × nível)} × escala de dano, mesmo debaixo d'água ou na chuva (a água só chia). O crédito do dano vai
 * para quem lançou (guardado em {@link #SOURCES}). Não põe fogo em blocos.
 */
public class AmaterasuEffect extends LiMobEffect {
    /** Alvo → quem acendeu as chamas (crédito do dano e do abate). */
    private static final Map<UUID, UUID> SOURCES = new HashMap<>();

    public AmaterasuEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    public static void credit(LivingEntity target, @Nullable Entity source) {
        if (source != null) SOURCES.put(target.getUUID(), source.getUUID());
    }

    public static void clear() {
        SOURCES.clear();
    }

    public static void forget(UUID id) {
        SOURCES.remove(id);
    }

    /** Dano por pulso (a cada 10 ticks) para um nível do efeito. */
    public static float pulseDamage(int amplifier) {
        return (0.6F + 0.25F * Math.max(0, amplifier)) * Progression.DAMAGE_SCALE;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 10 == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity e, int amplifier) {
        if (!(e.level() instanceof ServerLevel sl)) return true;
        UUID src = SOURCES.get(e.getUUID());
        Entity owner = src == null ? null : sl.getEntity(src);
        DamageSource ds = owner != null ? sl.damageSources().indirectMagic(owner, owner) : sl.damageSources().magic();
        // Invulnerabilidade curta não "come" o pulso: a chama negra queima por baixo de outros golpes
        int inv = e.invulnerableTime;
        e.invulnerableTime = 0;
        e.hurt(ds, pulseDamage(amplifier));
        if (e.isAlive()) e.invulnerableTime = Math.max(e.invulnerableTime, Math.min(inv, 10));
        double h = e.getBbHeight();
        ElementFx.sendFar(sl, ParticleTypes.SQUID_INK, e.getX(), e.getY() + h * 0.5, e.getZ(), 4, e.getBbWidth() * 0.4, h * 0.35, e.getBbWidth() * 0.4, 0.01);
        ElementFx.sendFar(sl, ElementFx.glow(0x2A0838, 1.6F), e.getX(), e.getY() + h * 0.4, e.getZ(), 5, e.getBbWidth() * 0.45, h * 0.4, e.getBbWidth() * 0.45, 0.04);
        if (e.isInWaterOrRain() && e.tickCount % 20 == 0) {
            sl.sendParticles(ParticleTypes.CLOUD, e.getX(), e.getY() + h * 0.7, e.getZ(), 3, 0.2, 0.2, 0.2, 0.02);
            sl.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 0.4F, 0.6F);
        }
        return true;
    }
}
