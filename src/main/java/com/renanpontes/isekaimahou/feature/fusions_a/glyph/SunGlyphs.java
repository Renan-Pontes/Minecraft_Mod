package com.renanpontes.isekaimahou.feature.fusions_a.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.fusions_a.FaRegistries;
import com.renanpontes.isekaimahou.feature.fusions_a.FaUtil;
import com.renanpontes.isekaimahou.feature.fusions_a.entity.FusionZoneEntity;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Glifos do Sol (Fogo + Luz): Raio Solar, Chama Sagrada, Coroa Solar, Amanhecer. */
public final class SunGlyphs {

    public static void register() {
        GlyphRegistry.register(new SolarRay());
        GlyphRegistry.register(new HolyFlame());
        GlyphRegistry.register(new SolarCrown());
        GlyphRegistry.register(new Dawn());
    }

    static boolean sunny(ServerLevel sl, Vec3 at) {
        BlockPos p = BlockPos.containing(at);
        return sl.isDay() && !sl.isRainingAt(p) && sl.canSeeSky(p);
    }

    /** Raio Solar: um feixe de sol desce do céu no alvo. Mais forte de dia, a céu aberto, e contra mortos-vivos. */
    public static class SolarRay extends EffectGlyph {
        public SolarRay() {
            super("solar_ray", 12);
            element(Element.SUN);
            tier(1);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ServerLevel sl = ctx.level();
            int n = 0;
            for (LivingEntity t : FaUtil.offensiveTargets(ctx, target, 5.0)) {
                if (n++ >= 4) break;
                Vec3 base = t.position();
                Vec3 sky = base.add(0, 14, 0);
                FaUtil.line(sl, sky, base, FaUtil.dustFade(0xFFF7C2, 0xFFB300, 1.6F), 3);
                FaUtil.line(sl, sky, base, ParticleTypes.END_ROD, 1.2);
                float dmg = 7.0F;
                if (sunny(sl, base)) dmg *= 1.5F;
                if (FaUtil.isUndead(t)) dmg *= 1.25F;
                FaUtil.hurt(ctx, t, dmg, stats, null);
                t.igniteForSeconds(stats.duration(60) / 20F);
                sl.sendParticles(ParticleTypes.FLASH, base.x, base.y + 1, base.z, 1, 0, 0, 0, 0);
                sl.sendParticles(ParticleTypes.FLAME, base.x, base.y + 0.2, base.z, 16, 0.4, 0.1, 0.4, 0.05);
                FaUtil.ring(sl, base.add(0, 0.1, 0), FaUtil.dust(0xFFC83A, 1.2F), 1.0, 16, 0);
                FaUtil.sound(sl, base, SoundEvents.BEACON_ACTIVATE, 0.9F, 1.7F);
                FaUtil.sound(sl, base, SoundEvents.BLAZE_SHOOT, 0.7F, 1.2F);
            }
        }
    }

    /** Chama Sagrada: fogo dourado que não apaga na água e castiga mortos-vivos em dobro. */
    public static class HolyFlame extends EffectGlyph {
        public HolyFlame() {
            super("holy_flame", 14);
            element(Element.SUN);
            tier(2);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ServerLevel sl = ctx.level();
            for (LivingEntity t : FaUtil.offensiveTargets(ctx, target, 3.5)) {
                boolean undead = FaUtil.isUndead(t);
                FaUtil.hurt(ctx, t, undead ? 8.0F : 4.0F, stats, null);
                t.addEffect(new MobEffectInstance(FaRegistries.HOLY_FLAME, stats.duration(100), FaUtil.amp(ctx, stats)), ctx.caster());
                t.igniteForSeconds(2);
                Vec3 c = t.position().add(0, t.getBbHeight() * 0.5, 0);
                FaUtil.spiral(sl, t, FaUtil.dustFade(0xFFF3B0, 0xFFB300, 1.2F), 2, 26);
                sl.sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 14, 0.3, 0.4, 0.3, 0.03);
                sl.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 6, 0.3, 0.4, 0.3, 0.05);
                FaUtil.sound(sl, c, SoundEvents.FIRECHARGE_USE, 0.9F, 1.2F);
                FaUtil.sound(sl, c, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 0.8F);
            }
        }
    }

    /** Coroa Solar: uma coroa de sol sobre a cabeça queima os inimigos que se aproximam. */
    public static class SolarCrown extends EffectGlyph {
        public SolarCrown() {
            super("solar_crown", 20);
            element(Element.SUN);
            tier(3);
        }

        @Override
        public boolean isBeneficial() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le)) return;
            ServerLevel sl = ctx.level();
            if (stats.inverted && !ctx.isAlly(le)) {
                // Invertido: insolação no inimigo
                le.igniteForSeconds(stats.duration(80) / 20F);
                le.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, stats.duration(120), 0), ctx.caster());
                FaUtil.hurt(ctx, le, 3.0F, stats, null);
                FaUtil.around(sl, le, ParticleTypes.FLAME, 12, 0.03);
                return;
            }
            int ticks = stats.duration(400);
            le.addEffect(new MobEffectInstance(FaRegistries.SOLAR_CROWN, ticks, 0), ctx.caster());
            FusionZoneEntity.spawn(ctx, stats, this, FusionZoneEntity.Kind.SOLAR_CROWN, le.position(), stats.radius(4.0F), ticks + 5, le);
            Vec3 head = le.position().add(0, le.getBbHeight() + 0.3, 0);
            FaUtil.ring(sl, head, FaUtil.dust(0xFFC83A, 1.2F), 0.45, 14, 0);
            FaUtil.shockwave(sl, le.position().add(0, 0.2, 0), FaUtil.dustFade(0xFFF3B0, 0xFF9A1F, 1.3F), 32, 0.3);
            sl.sendParticles(ParticleTypes.END_ROD, head.x, head.y, head.z, 10, 0.3, 0.2, 0.3, 0.05);
            FaUtil.sound(sl, head, SoundEvents.BEACON_POWER_SELECT, 1.0F, 1.2F);
            FaUtil.sound(sl, head, SoundEvents.FIRECHARGE_USE, 0.6F, 1.5F);
        }
    }

    /** Amanhecer: um clarão de sol nascente — cega os inimigos em volta e incinera mortos-vivos. */
    public static class Dawn extends EffectGlyph {
        public Dawn() {
            super("dawn", 28);
            element(Element.SUN);
            tier(4);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            flash(ctx, stats, target.position().add(0, target.getBbHeight() * 0.5, 0));
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            // Errou o alvo e acertou o chão/parede: o clarão nasce ali mesmo
            flash(ctx, stats, Vec3.atCenterOf(hit.getBlockPos().relative(hit.getDirection())));
        }

        private void flash(SpellContext ctx, SpellStats stats, Vec3 c) {
            if (!FaUtil.once(ctx, "dawn")) return;
            ServerLevel sl = ctx.level();
            float r = stats.radius(8.0F);
            for (LivingEntity e : ctx.enemiesAround(c, r)) {
                e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, stats.duration(60), 0), ctx.caster());
                e.addEffect(new MobEffectInstance(MobEffects.GLOWING, stats.duration(100), 0), ctx.caster());
                if (FaUtil.isUndead(e)) {
                    FaUtil.hurt(ctx, e, 10.0F, stats, null);
                    e.igniteForSeconds(6);
                    FaUtil.around(sl, e, ParticleTypes.FLAME, 16, 0.05);
                } else {
                    FaUtil.hurt(ctx, e, 3.0F, stats, null);
                }
            }
            // Clarão + anéis de luz que se expandem
            sl.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 3, 0.5, 0.5, 0.5, 0);
            sl.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 60, 0.3, 0.3, 0.3, 0.45);
            for (int i = 0; i < 4; i++) {
                final double rr = r * (i + 1) / 4.0;
                SpellScheduler.schedule(1 + i * 2, () -> {
                    FaUtil.ring(sl, c, FaUtil.dustFade(0xFFFFFF, 0xFFC83A, 1.6F), rr, (int) (rr * 10), 0);
                    FaUtil.ring(sl, c.add(0, 0.6, 0), ParticleTypes.END_ROD, rr * 0.8, (int) (rr * 4), 0.01);
                });
            }
            FaUtil.sound(sl, c, SoundEvents.BEACON_ACTIVATE, 1.4F, 1.4F);
            FaUtil.sound(sl, c, SoundEvents.TOTEM_USE, 0.5F, 1.6F);
        }
    }

    private SunGlyphs() {}
}
