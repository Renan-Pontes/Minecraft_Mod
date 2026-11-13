package com.renanpontes.isekaimahou.feature.earth_wind.glyph.wind;

import java.util.function.Supplier;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.earth_wind.EwFx;
import com.renanpontes.isekaimahou.feature.earth_wind.EwUtil;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Base dos glifos de Vento que so concedem um efeito de status benefico (Pulo Duplo, Planar, Velocidade,
 * Desviar Projeteis, Passos Silenciosos). Amplificar sobe o nivel do efeito (ate {@code maxAmp}).
 * Com Inverter, atinge inimigos com a versao "contraria" ({@link #onInverted}) em vez de dar o bonus.
 */
public class WindBuffEffect extends EffectGlyph {
    private final Supplier<Holder<MobEffect>> effect;
    private final int baseTicks;
    private final int maxAmp;
    private final Supplier<SoundEvent> sound;
    /** Sem particulas do efeito no corpo (Passos Silenciosos nao pode "brilhar"). */
    private boolean hideParticles;

    public WindBuffEffect(String id, int cost, int tier, Supplier<Holder<MobEffect>> effect, int baseTicks, int maxAmp,
            Supplier<SoundEvent> sound) {
        super(id, cost);
        element(Element.WIND);
        tier(tier);
        this.effect = effect;
        this.baseTicks = baseTicks;
        this.maxAmp = maxAmp;
        this.sound = sound;
    }

    /** O efeito nao solta particulas no corpo de quem o recebe. */
    protected WindBuffEffect hideParticles() {
        this.hideParticles = true;
        return this;
    }

    @Override
    public boolean isBeneficial() {
        return true;
    }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        if (!(target instanceof LivingEntity le)) return;
        if (stats.inverted && !ctx.isAlly(le)) {
            // Inverter: o vento sopra CONTRA o alvo (nunca da o bonus a um inimigo)
            onInverted(ctx, le, stats);
            ServerLevel level = ctx.level();
            level.sendParticles(ParticleTypes.SMALL_GUST, le.getX(), le.getY() + le.getBbHeight() * 0.5, le.getZ(), 3, 0.3, 0.3, 0.3, 0);
            level.sendParticles(Vfx.cloud(), le.getX(), le.getY() + le.getBbHeight() + 0.2, le.getZ(), 8, 0.3, 0.1, 0.3, 0.02);
            EwFx.sound(level, le.position(), SoundEvents.BREEZE_DEFLECT, 0.8F, 0.6F);
            return;
        }
        int amp = Math.max(0, Math.min(maxAmp, stats.amplify));
        if (hideParticles) {
            le.addEffect(new MobEffectInstance(effect.get(), stats.duration(baseTicks), amp, false, false, true), ctx.caster());
        } else {
            ctx.status(le, effect.get(), baseTicks, amp, stats);
        }
        ServerLevel level = ctx.level();
        EwFx.windSpiral(level, le.position(), le.getBbWidth() * 0.9 + 0.3, le.getBbHeight() + 0.3, 24, level.random.nextDouble() * 6);
        level.sendParticles(ParticleTypes.SMALL_GUST, le.getX(), le.getY() + le.getBbHeight() * 0.5, le.getZ(), 2, 0.3, 0.3, 0.3, 0);
        EwFx.sound(level, le.position(), sound.get(), 0.9F, 1.2F);
    }

    /** Versao invertida (num inimigo): por padrao, vento contrario que deixa lento e derruba quem esta no ar. */
    protected void onInverted(SpellContext ctx, LivingEntity le, SpellStats stats) {
        ctx.status(le, MobEffects.MOVEMENT_SLOWDOWN, 80, 0, stats);
        if (!le.onGround()) {
            Vec3 dm = le.getDeltaMovement();
            EwUtil.setVelocity(le, new Vec3(dm.x * 0.3, Math.min(dm.y, -0.9), dm.z * 0.3));
        }
    }
}
