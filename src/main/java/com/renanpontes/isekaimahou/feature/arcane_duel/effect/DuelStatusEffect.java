package com.renanpontes.isekaimahou.feature.arcane_duel.effect;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.feature.arcane_duel.DuelFx;
import com.renanpontes.isekaimahou.feature.arcane_duel.DuelRegistry;
import com.renanpontes.isekaimahou.feature.arcane_duel.entity.DuelFxEntity;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * Efeitos do arcane_duel. A regra (imunidades, vida mínima) fica em {@code DuelEvents}; aqui só a aura de partículas
 * que acompanha o efeito (a casca/égide desenhada vem da entidade de visual).
 */
public class DuelStatusEffect extends MobEffect {
    public enum Aura { HEXPROOF, INDESTRUCTIBLE }

    private final Aura aura;
    private final DustParticleOptions dust;

    public DuelStatusEffect(MobEffectCategory category, int color, Aura aura) {
        super(category, color);
        this.aura = aura;
        this.dust = new DustParticleOptions(new Vector3f(((color >> 16) & 255) / 255F, ((color >> 8) & 255) / 255F,
                (color & 255) / 255F), 0.8F);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 5 == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity e, int amplifier) {
        if (!(e.level() instanceof ServerLevel level)) return true;
        // A casca desenhada (entidade de visual, não salva) volta depois de sair e entrar no mundo, de trocar de
        // dimensão ou de o efeito ser roubado: a cada 1 s, garante que existe uma presa a quem tem o efeito
        if ((e.tickCount / 5) % 4 == 0) ensureShell(level, e);
        double w = e.getBbWidth() * 0.5 + 0.55;
        double h = e.getBbHeight();
        double a0 = e.tickCount * 0.21;
        if (aura == Aura.HEXPROOF) {
            // Seis pontos de um hexágono subindo em espiral: a casca de runas que "não deixa nada grudar"
            for (int i = 0; i < 6; i++) {
                double a = a0 + i * Math.PI / 3;
                double y = e.getY() + h * (0.15 + 0.7 * ((e.tickCount / 5 + i) % 6) / 6.0);
                level.sendParticles(dust, e.getX() + Math.cos(a) * w, y, e.getZ() + Math.sin(a) * w, 1, 0, 0, 0, 0);
            }
        } else {
            // Faíscas douradas caindo em volta (a égide)
            for (int i = 0; i < 3; i++) {
                double a = a0 * 1.7 + i * Math.PI * 2 / 3;
                level.sendParticles(dust, e.getX() + Math.cos(a) * w, e.getY() + h + 0.2, e.getZ() + Math.sin(a) * w, 1, 0, 0, 0, 0);
            }
            level.sendParticles(Vfx.waxOff(), e.getX(), e.getY() + h * 0.5, e.getZ(), 1, w * 0.6, h * 0.4, w * 0.6, 0.02);
        }
        return true;
    }

    private void ensureShell(ServerLevel level, LivingEntity e) {
        Holder<MobEffect> holder = aura == Aura.HEXPROOF ? DuelRegistry.HEXPROOF : DuelRegistry.INDESTRUCTIBLE;
        MobEffectInstance inst = e.getEffect(holder);
        if (inst == null || e.isSpectator()) return;
        int left = inst.isInfiniteDuration() ? 200 : inst.getDuration();
        if (left < 10) return;
        if (aura == Aura.HEXPROOF) {
            DuelFx.attach(level, DuelFxEntity.HEX_WARD, e, e.getBbWidth() * 0.5F + 0.75F, left, DuelFx.HEX, e.getBbHeight());
        } else {
            DuelFx.attach(level, DuelFxEntity.AEGIS, e, e.getBbWidth() * 0.5F + 0.9F, left, DuelFx.GOLD, e.getBbHeight());
        }
    }
}
