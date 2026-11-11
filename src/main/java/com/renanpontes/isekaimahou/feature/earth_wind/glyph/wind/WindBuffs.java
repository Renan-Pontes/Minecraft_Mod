package com.renanpontes.isekaimahou.feature.earth_wind.glyph.wind;

import com.renanpontes.isekaimahou.feature.earth_wind.EwRegistries;

import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/** Glifos de Vento que concedem um efeito de status (beneficos: afetam voce e aliados; Inverter vira contra inimigos). */
public final class WindBuffs {

    /** Pulo Duplo: 30 s com 1 pulo extra no ar (+1 por Amplificar, ate 3). */
    public static class DoubleJump extends WindBuffEffect {
        public DoubleJump() {
            super("double_jump", 12, 2, () -> EwRegistries.DOUBLE_JUMP, 600, 2, () -> SoundEvents.BREEZE_JUMP);
        }
    }

    /** Planar: 30 s de queda lenta controlada pelo olhar (agachar cai normal). */
    public static class Glide extends WindBuffEffect {
        public Glide() {
            super("glide", 10, 2, () -> EwRegistries.WIND_GLIDE, 600, 2, () -> SoundEvents.ELYTRA_FLYING);
        }
    }

    /** Velocidade: 30 s correndo mais rapido e subindo degraus de um bloco. Inverter: vento contrario (Lentidao II). */
    public static class Swiftness extends WindBuffEffect {
        public Swiftness() {
            super("swiftness", 14, 3, () -> EwRegistries.WIND_SWIFTNESS, 600, 2, () -> SoundEvents.BREEZE_IDLE_AIR);
        }

        @Override
        protected void onInverted(SpellContext ctx, LivingEntity le, SpellStats stats) {
            ctx.status(le, MobEffects.MOVEMENT_SLOWDOWN, 160, 1, stats);
        }
    }

    /** Desviar Projeteis: 15 s de barreira de vento que rebate flechas e projeteis contra quem atirou. */
    public static class Deflect extends WindBuffEffect {
        public Deflect() {
            super("deflect", 20, 4, () -> EwRegistries.WIND_BARRIER, 300, 0, () -> SoundEvents.BREEZE_DEFLECT);
            minCircle(3);
        }
    }

    /** Passos Silenciosos: 45 s em que mobs longe nao te percebem e as vibracoes nao sao ouvidas. */
    public static class SilentSteps extends WindBuffEffect {
        public SilentSteps() {
            super("silent_steps", 18, 4, () -> EwRegistries.SILENT_STEPS, 900, 0, () -> SoundEvents.WOOL_PLACE);
            minCircle(3);
            hideParticles();
        }

        /** Inverter: o vento "denuncia" o inimigo (Brilho) em vez de esconder. */
        @Override
        protected void onInverted(SpellContext ctx, LivingEntity le, SpellStats stats) {
            le.addEffect(new MobEffectInstance(MobEffects.GLOWING, stats.duration(200), 0, false, false), ctx.caster());
        }
    }

    private WindBuffs() {}
}
