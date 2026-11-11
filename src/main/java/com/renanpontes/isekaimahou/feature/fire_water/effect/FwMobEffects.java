package com.renanpontes.isekaimahou.feature.fire_water.effect;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.fire_water.FwRegistries;
import com.renanpontes.isekaimahou.feature.fire_water.FwUtil;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Efeitos de status do módulo Fogo/Água. A lógica que precisa de eventos (afogar, refletir, renascer)
 * fica em {@code FwEvents}; aqui ficam o dano periódico, o movimento e as partículas.
 */
public final class FwMobEffects {

    /** Brasa Persistente: a cada segundo causa dano de brasa e reacende o alvo. Água/Encharcado apaga. */
    public static class LingeringEmber extends MobEffect {
        public LingeringEmber() {
            super(MobEffectCategory.HARMFUL, 0xFF7A1F, ParticleTypes.SMALL_FLAME);
        }

        @Override
        public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
            return duration % 20 == 0;
        }

        @Override
        public boolean applyEffectTick(LivingEntity e, int amplifier) {
            if (!(e.level() instanceof ServerLevel sl)) return true;
            if (e.isInWaterRainOrBubble() || e.hasEffect(CoreRegistries.SOAKED)) {
                sl.sendParticles(ParticleTypes.LARGE_SMOKE, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(),
                        10, 0.3, 0.3, 0.3, 0.02);
                FwUtil.sound(sl, e.position(), SoundEvents.FIRE_EXTINGUISH, 0.6F, 1.2F);
                return false; // as brasas apagaram
            }
            if (e.getRemainingFireTicks() < 30) e.igniteForSeconds(2F);
            e.hurt(FwUtil.source(e, FwRegistries.EMBER_BURN), 1.0F + amplifier);
            FwUtil.around(sl, e, ParticleTypes.FLAME, 6, 0.02);
            sl.sendParticles(ParticleTypes.LAVA, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 1, 0.2, 0.2, 0.2, 0);
            return true;
        }
    }

    /** Chama Azul: fogo de alma mágico. Não apaga na água e queima até criaturas imunes a fogo. */
    public static class AzureFlame extends MobEffect {
        public AzureFlame() {
            super(MobEffectCategory.HARMFUL, 0x3FC8FF, ParticleTypes.SOUL_FIRE_FLAME);
        }

        @Override
        public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
            return duration % 10 == 0;
        }

        @Override
        public boolean applyEffectTick(LivingEntity e, int amplifier) {
            if (!(e.level() instanceof ServerLevel sl)) return true;
            e.invulnerableTime = 0;
            e.hurt(FwUtil.source(e, FwRegistries.AZURE_BURN), 1.5F + 0.75F * amplifier);
            FwUtil.around(sl, e, ParticleTypes.SOUL_FIRE_FLAME, 8, 0.03);
            if (sl.random.nextInt(3) == 0) FwUtil.around(sl, e, ParticleTypes.SOUL, 1, 0.02);
            return true;
        }
    }

    /** Bolha: partículas de bolha em volta e o alvo não desliza para os lados enquanto flutua. */
    public static class BubblePrison extends MobEffect {
        public BubblePrison() {
            super(MobEffectCategory.HARMFUL, 0x9BE7FF, ParticleTypes.BUBBLE_POP);
        }

        @Override
        public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
            return duration % 2 == 0;
        }

        @Override
        public boolean applyEffectTick(LivingEntity e, int amplifier) {
            if (!(e.level() instanceof ServerLevel sl)) return true;
            double r = Math.max(e.getBbWidth(), e.getBbHeight()) * 0.5 + 0.35;
            Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
            FwUtil.sphere(sl, c, FwUtil.dust(0xA8ECFF, 0.9F), r, 7);
            if (sl.random.nextInt(4) == 0) FwUtil.sphere(sl, c, ParticleTypes.BUBBLE_POP, r, 1);
            if (!(e instanceof Player)) {
                Vec3 dm = e.getDeltaMovement();
                e.setDeltaMovement(dm.x * 0.3, dm.y, dm.z * 0.3);
            }
            return true;
        }
    }

    /** Correnteza: empurra o alvo continuamente numa direção guardada nos dados da entidade. */
    public static class Undertow extends MobEffect {
        private static final String KEY = IsekaiMahou.MODID + "_undertow";

        public Undertow() {
            super(MobEffectCategory.HARMFUL, 0x2F7FD8, ParticleTypes.FISHING);
        }

        /** Guarda a direção (horizontal) e a força da corrente no alvo. */
        public static void setFlow(LivingEntity e, Vec3 dir, float strength) {
            CompoundTag t = new CompoundTag();
            t.putDouble("x", dir.x);
            t.putDouble("z", dir.z);
            t.putFloat("s", strength);
            e.getPersistentData().put(KEY, t);
        }

        /** Apaga a corrente guardada (fim do efeito), para não ficar lixo nos dados salvos da entidade. */
        public static void clearFlow(LivingEntity e) {
            e.getPersistentData().remove(KEY);
        }

        @Override
        public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
            return true;
        }

        @Override
        public boolean applyEffectTick(LivingEntity e, int amplifier) {
            if (!(e.level() instanceof ServerLevel sl)) return true;
            CompoundTag t = e.getPersistentData().getCompound(KEY);
            if (t.isEmpty()) return true;
            double s = t.getFloat("s");
            double accel = 0.11 * s * (e.isInWater() ? 1.4 : 1.0);
            Vec3 n = e.getDeltaMovement().add(t.getDouble("x") * accel, 0, t.getDouble("z") * accel);
            double h = Math.sqrt(n.x * n.x + n.z * n.z), max = 0.55 * s;
            if (h > max) n = new Vec3(n.x / h * max, n.y, n.z / h * max);
            e.setDeltaMovement(n);
            e.hasImpulse = true;
            // Jogadores controlam o próprio movimento: o servidor precisa reenviar a velocidade
            if (e instanceof Player && e.tickCount % 2 == 0) e.hurtMarked = true;
            e.resetFallDistance();
            if (e.tickCount % 2 == 0) {
                sl.sendParticles(ParticleTypes.SPLASH, e.getX(), e.getY() + 0.1, e.getZ(), 5, 0.35, 0.05, 0.35, 0.1);
                sl.sendParticles(ParticleTypes.FISHING, e.getX(), e.getY() + 0.15, e.getZ(), 2, 0.3, 0.02, 0.3, 0.02);
            }
            return true;
        }
    }

    /** Afogando: o ar é drenado pelo evento de respiração (FwEvents); aqui só as bolhas escapando. */
    public static class WaterChoke extends MobEffect {
        public WaterChoke() {
            super(MobEffectCategory.HARMFUL, 0x1E5AA8, ParticleTypes.BUBBLE_POP);
        }

        @Override
        public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
            return duration % 4 == 0;
        }

        @Override
        public boolean applyEffectTick(LivingEntity e, int amplifier) {
            if (!(e.level() instanceof ServerLevel sl)) return true;
            sl.sendParticles(ParticleTypes.BUBBLE_POP, e.getX(), e.getEyeY(), e.getZ(), 3, 0.2, 0.15, 0.2, 0.01);
            sl.sendParticles(ParticleTypes.FALLING_WATER, e.getX(), e.getEyeY() - 0.1, e.getZ(), 1, 0.15, 0.05, 0.15, 0);
            return true;
        }
    }

    /** Espelho d'Água: brilho de água em volta; o reflexo é feito no evento de impacto de projétil. */
    public static class WaterMirror extends MobEffect {
        public WaterMirror() {
            super(MobEffectCategory.BENEFICIAL, 0x7FE0FF, ParticleTypes.DRIPPING_WATER);
        }

        @Override
        public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
            return duration % 4 == 0;
        }

        @Override
        public boolean applyEffectTick(LivingEntity e, int amplifier) {
            if (!(e.level() instanceof ServerLevel sl)) return true;
            double a = e.tickCount * 0.3;
            double r = e.getBbWidth() * 0.5 + 0.45;
            for (int i = 0; i < 3; i++) {
                double ang = a + i * Math.PI * 2 / 3;
                sl.sendParticles(FwUtil.dust(0xBFF4FF, 0.8F), e.getX() + Math.cos(ang) * r,
                        e.getY() + e.getBbHeight() * (0.3 + 0.2 * i), e.getZ() + Math.sin(ang) * r, 1, 0, 0, 0, 0);
            }
            return true;
        }
    }

    /** Bênção da Fênix: brasas douradas subindo; o renascimento é feito no evento de morte. */
    public static class PhoenixBlessing extends MobEffect {
        public PhoenixBlessing() {
            super(MobEffectCategory.BENEFICIAL, 0xFF9A2E, ParticleTypes.SMALL_FLAME);
        }

        @Override
        public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
            return duration % 8 == 0;
        }

        @Override
        public boolean applyEffectTick(LivingEntity e, int amplifier) {
            if (!(e.level() instanceof ServerLevel sl)) return true;
            sl.sendParticles(FwUtil.dust(0xFFC23D, 1.0F), e.getX(), e.getY() + 0.2, e.getZ(), 2, 0.35, 0.1, 0.35, 0);
            sl.sendParticles(ParticleTypes.FLAME, e.getX(), e.getY() + e.getBbHeight() + 0.1, e.getZ(), 1, 0.2, 0.05, 0.2, 0.01);
            return true;
        }
    }

    private FwMobEffects() {}
}
