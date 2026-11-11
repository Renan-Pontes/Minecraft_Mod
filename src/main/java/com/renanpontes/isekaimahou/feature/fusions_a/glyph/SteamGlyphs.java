package com.renanpontes.isekaimahou.feature.fusions_a.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.fusions_a.FaEvents;
import com.renanpontes.isekaimahou.feature.fusions_a.FaUtil;
import com.renanpontes.isekaimahou.feature.fusions_a.entity.FusionZoneEntity;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Glifos de Vapor (Fogo + Água): Escaldar, Propulsão a Vapor, Névoa Quente, Gêiser. */
public final class SteamGlyphs {

    public static void register() {
        GlyphRegistry.register(new Scald());
        GlyphRegistry.register(new SteamPropulsion());
        GlyphRegistry.register(new HotMist());
        GlyphRegistry.register(new Geyser());
    }

    /** Nuvem de vapor escaldante num ponto. */
    static void steamPuff(ServerLevel sl, Vec3 c, int amount, double spread) {
        sl.sendParticles(ParticleTypes.CLOUD, c.x, c.y, c.z, amount, spread, spread * 0.6, spread, 0.04);
        sl.sendParticles(ParticleTypes.POOF, c.x, c.y, c.z, amount / 2, spread, spread * 0.6, spread, 0.02);
        sl.sendParticles(FaUtil.dustFade(0xFFFFFF, 0xD8E6EE, 1.8F), c.x, c.y, c.z, amount / 2, spread, spread * 0.6, spread, 0);
    }

    /** Escaldar: jato de água fervente — dano, enfraquece e apaga o fogo do alvo. */
    public static class Scald extends EffectGlyph {
        public Scald() {
            super("scald", 9);
            element(Element.STEAM);
            tier(1);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ServerLevel sl = ctx.level();
            for (LivingEntity t : FaUtil.offensiveTargets(ctx, target, 3.5)) {
                FaUtil.hurt(ctx, t, 5.0F, stats, null);
                t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, stats.duration(80), 0), ctx.caster());
                Vec3 c = t.position().add(0, t.getBbHeight() * 0.6, 0);
                steamPuff(sl, c, 14, 0.35);
                sl.sendParticles(ParticleTypes.SPLASH, c.x, c.y, c.z, 16, 0.3, 0.3, 0.3, 0.1);
                FaUtil.sound(sl, c, SoundEvents.FIRE_EXTINGUISH, 0.9F, 0.9F);
                FaUtil.sound(sl, c, SoundEvents.GENERIC_SPLASH, 0.6F, 1.3F);
            }
        }
    }

    /** Propulsão a Vapor: lança você na direção do olhar (sem dano de queda) ou arremessa o alvo para longe. */
    public static class SteamPropulsion extends EffectGlyph {
        public SteamPropulsion() {
            super("steam_propulsion", 8);
            element(Element.STEAM);
            tier(1);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ServerLevel sl = ctx.level();
            float p = Math.min(stats.power(), 2.2F);
            Vec3 v;
            if (FaUtil.isSelf(ctx, target)) {
                Vec3 look = ctx.look();
                Vec3 cur = target.getDeltaMovement();
                v = target.onGround()
                        ? new Vec3(look.x, Math.max(0.4, look.y + 0.4), look.z).normalize().scale(1.35 * p)
                        : new Vec3(look.x * 0.7 * p, 0.85 * p, look.z * 0.7 * p);
                if (stats.inverted) v = new Vec3(-v.x, v.y, -v.z);
                FaUtil.setMotion(target, new Vec3(cur.x * 0.3 + v.x, v.y, cur.z * 0.3 + v.z));
                if (target instanceof LivingEntity le) FaEvents.cushion(le, 140);
            } else {
                Vec3 dir = FaUtil.awayFromCaster(ctx, target);
                if (stats.inverted) dir = dir.scale(-1);
                double k = FaUtil.kbFactor(target);
                v = dir.scale(1.2 * p * k).add(0, 0.5 * p * k, 0);
                FaUtil.addMotion(target, v);
                if (target instanceof LivingEntity le) FaUtil.hurt(ctx, le, 2.0F, stats, null);
            }
            // Jato de vapor saindo para trás
            Vec3 base = target.position().add(0, 0.3, 0);
            Vec3 back = v.lengthSqr() > 1.0E-4 ? v.normalize().scale(-1) : new Vec3(0, -1, 0);
            for (int i = 0; i < 16; i++) {
                double j = 0.3;
                sl.sendParticles(i % 3 == 0 ? ParticleTypes.POOF : ParticleTypes.CLOUD, base.x, base.y, base.z, 0,
                        back.x + (sl.random.nextDouble() - 0.5) * j, back.y + (sl.random.nextDouble() - 0.5) * j,
                        back.z + (sl.random.nextDouble() - 0.5) * j, 0.3 + sl.random.nextDouble() * 0.2);
            }
            FaUtil.sound(sl, base, SoundEvents.FIRE_EXTINGUISH, 1.0F, 0.6F);
            FaUtil.sound(sl, base, SoundEvents.BREEZE_JUMP, 0.8F, 0.9F);
        }
    }

    /** Névoa Quente: nuvem de vapor que cega e cozinha quem estiver dentro. */
    public static class HotMist extends EffectGlyph {
        public HotMist() {
            super("hot_mist", 14);
            element(Element.STEAM);
            tier(2);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            mist(ctx, stats, target.position());
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            mist(ctx, stats, Vec3.atBottomCenterOf(hit.getBlockPos().relative(hit.getDirection())));
        }

        private void mist(SpellContext ctx, SpellStats stats, Vec3 at) {
            if (!FaUtil.once(ctx, "hot_mist")) return;
            ServerLevel sl = ctx.level();
            Vec3 c = FaUtil.ground(sl, at, 4);
            float r = stats.radius(4.0F);
            FusionZoneEntity.spawn(ctx, stats, this, FusionZoneEntity.Kind.HOT_MIST, c, r, stats.duration(160), null);
            steamPuff(sl, c.add(0, 1, 0), 40, r * 0.4);
            FaUtil.sound(sl, c, SoundEvents.FIRE_EXTINGUISH, 1.2F, 0.5F);
            FaUtil.sound(sl, c, SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 0.6F, 1.2F);
        }
    }

    /** Gêiser: depois de um aviso, uma coluna de água fervente lança para o alto quem estiver em cima. */
    public static class Geyser extends EffectGlyph {
        private static final int WARN = 12;

        public Geyser() {
            super("geyser", 16);
            element(Element.STEAM);
            tier(3);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            start(ctx, stats, target.position(), FaUtil.isSelf(ctx, target));
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            start(ctx, stats, Vec3.atBottomCenterOf(hit.getBlockPos().relative(hit.getDirection())), false);
        }

        private void start(SpellContext ctx, SpellStats stats, Vec3 at, boolean self) {
            ServerLevel sl = ctx.level();
            Vec3 c = FaUtil.ground(sl, at, 6);
            if (!FaUtil.once(ctx, "geyser@" + (int) Math.floor(c.x) + "," + (int) Math.floor(c.z))) return;
            if (self) {
                erupt(ctx, stats, c); // em si mesmo: sem aviso, para "surfar" no gêiser
                return;
            }
            for (int t = 0; t < WARN; t += 3) {
                final int tick = t;
                SpellScheduler.schedule(Math.max(1, t), () -> {
                    sl.sendParticles(ParticleTypes.BUBBLE_POP, c.x, c.y + 0.1, c.z, 6 + tick, 0.5, 0.05, 0.5, 0.02);
                    sl.sendParticles(ParticleTypes.CLOUD, c.x, c.y + 0.1, c.z, 2, 0.3, 0.05, 0.3, 0.01);
                    FaUtil.ring(sl, c.add(0, 0.05, 0), FaUtil.dust(0xBFE6FF, 1.0F), 1.2, 14, 0);
                    if (tick % 6 == 0) FaUtil.sound(sl, c, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, 1.0F, 0.7F);
                });
            }
            SpellScheduler.schedule(WARN, () -> erupt(ctx, stats, c));
        }

        private void erupt(SpellContext ctx, SpellStats stats, Vec3 c) {
            ServerLevel sl = ctx.level();
            float p = Math.min(stats.power(), 2.5F);
            FaUtil.column(sl, c, ParticleTypes.CLOUD, 7.0, 4, 0.35, 0.03);
            FaUtil.column(sl, c, ParticleTypes.SPLASH, 6.0, 6, 0.4, 0.2);
            FaUtil.column(sl, c, ParticleTypes.BUBBLE_COLUMN_UP, 5.0, 4, 0.3, 0.1);
            sl.sendParticles(ParticleTypes.FALLING_WATER, c.x, c.y + 6, c.z, 30, 1.2, 0.3, 1.2, 0);
            FaUtil.sound(sl, c, SoundEvents.GENERIC_SPLASH, 1.4F, 0.8F);
            FaUtil.sound(sl, c, SoundEvents.FIRE_EXTINGUISH, 1.2F, 0.5F);
            FaUtil.sound(sl, c, SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, 1.2F, 1.0F);
            AABB box = new AABB(c.x - 1.6, c.y - 0.5, c.z - 1.6, c.x + 1.6, c.y + 3.5, c.z + 1.6);
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !e.isSpectator())) {
                boolean friend = e == ctx.caster() || ctx.isAlly(e);
                double k = FaUtil.kbFactor(e);
                Vec3 m = e.getDeltaMovement();
                if (friend) {
                    FaUtil.setMotion(e, new Vec3(m.x, 1.25 * p, m.z));
                    FaEvents.cushion(e, 160);
                } else {
                    FaUtil.hurt(ctx, e, 4.0F, stats, null);
                    FaUtil.setMotion(e, new Vec3(m.x * 0.3, Math.max(m.y, 1.1 * p * k), m.z * 0.3));
                }
            }
        }
    }

    private SteamGlyphs() {}
}
