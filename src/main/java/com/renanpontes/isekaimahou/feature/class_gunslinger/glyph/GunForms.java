package com.renanpontes.isekaimahou.feature.class_gunslinger.glyph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_gunslinger.GsFx;
import com.renanpontes.isekaimahou.feature.class_gunslinger.GsFxNet;
import com.renanpontes.isekaimahou.feature.class_gunslinger.GsNetwork;
import com.renanpontes.isekaimahou.feature.class_gunslinger.GunCombat;
import com.renanpontes.isekaimahou.feature.class_gunslinger.entity.ManaBulletEntity;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Formas de ARMA do Pistoleiro (glifos de origem): a magia sai pelas balas. Todas valem em qualquer elemento, podem
 * ser estágio 2+ (saem do ponto do estágio anterior) e contam as cópias reais para o custo por cópia da v1.0
 * ({@link #copies}): balas lado a lado (Dividir), tiros da rajada, leque do "Leque do Cão" e saltos do ricochete.
 * Com uma arma de mana carregada na mão, gastam um cartucho e saem mais fortes (ver {@link GunCombat#formPower}).
 */
public final class GunForms {

    /** Bala: um tiro rápido e certeiro (Dividir = balas lado a lado). A forma básica do Pistoleiro. */
    public static class BulletForm extends FormGlyph {
        public BulletForm() {
            super("gun_bullet", 4);
            origin(MagicPath.GUNSLINGER);
            tier(1);
        }

        @Override public boolean stageable() { return true; }
        @Override public int baseCooldown() { return 8; }
        @Override public float hintRange() { return 96F; }

        @Override
        public float copies(SpellStats s, int circle) {
            return s.count() * GunShots.fanBullets(s);
        }

        @Override
        public void cast(SpellContext ctx) {
            GunShots.fire(ctx, new GunShots.Volley(1, 0, 1, 4.2F, 0.3F, ManaBulletEntity.STYLE_NORMAL, 0, 0, 0F, 1.0F));
        }
    }

    /** Rajada de Balas: três tiros seguidos (+1 por Dividir), um a cada 3 ticks, seguindo a sua mira. */
    public static class BurstForm extends FormGlyph {
        public BurstForm() {
            super("gun_burst", 8);
            origin(MagicPath.GUNSLINGER);
            tier(2);
        }

        @Override public boolean stageable() { return true; }
        @Override public int baseCooldown() { return 20; }
        @Override public float hintRange() { return 90F; }

        private static int shots(SpellStats s) {
            return 3 + Math.max(0, s.split);
        }

        @Override
        public float copies(SpellStats s, int circle) {
            return shots(s) * GunShots.fanBullets(s);
        }

        @Override
        public void cast(SpellContext ctx) {
            // a rajada usa o Dividir como tiros a mais (não balas lado a lado): 1 bala por tiro
            GunShots.fire(ctx, new GunShots.Volley(shots(ctx.formStats()), 3, 1, 4.0F, 1.5F, ManaBulletEntity.STYLE_NORMAL,
                    0, 0, 0F, 1.0F, 1));
        }
    }

    /** Tiro Carregado: segura a mira e solta uma bala pesada (×1,8, atravessa 1 alvo e empurra). */
    public static class ChargedShotForm extends FormGlyph {
        public ChargedShotForm() {
            super("gun_charged_shot", 10);
            origin(MagicPath.GUNSLINGER);
            tier(2);
        }

        @Override public boolean stageable() { return true; }
        @Override public int baseCastTicks() { return 20; }
        @Override public int baseCooldown() { return 30; }
        @Override public float hintRange() { return 120F; }

        @Override
        public float copies(SpellStats s, int circle) {
            return s.count() * GunShots.fanBullets(s);
        }

        @Override
        public void cast(SpellContext ctx) {
            LivingEntity caster = ctx.caster();
            if (caster != null) {
                ServerLevel level = ctx.level();
                Vec3 at = ctx.hasOrigin() ? ctx.eyePos() : GsFx.muzzle(caster, caster.getLookAngle(), 1);
                GsFxNet.at(level, GsFxNet.CHARGED_SHOT, at, caster.getLookAngle(), 1F, ctx.color() & 0xFFFFFF, caster.getId());
                GsFx.sound(level, at, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 1.0F, 0.6F);
                GsFx.sound(level, at, SoundEvents.BLAZE_SHOOT, 0.6F, 0.7F);
                if (caster instanceof ServerPlayer sp && !ctx.hasOrigin()) GsNetwork.anim(sp, GsNetwork.CHARGE, 0, ctx.color() & 0xFFFFFF);
            }
            GunShots.fire(ctx, new GunShots.Volley(1, 0, 1, 5.0F, 0F, ManaBulletEntity.STYLE_HEAVY, 0, 1, 0.8F, 1.8F));
        }
    }

    /** Tiro Ricochete: a bala salta de inimigo em inimigo (2 saltos + Ricochete), aplicando a magia em cada um. */
    public static class RicochetForm extends FormGlyph {
        public RicochetForm() {
            super("gun_ricochet", 8);
            origin(MagicPath.GUNSLINGER);
            tier(3);
        }

        @Override public boolean stageable() { return true; }
        @Override public int baseCooldown() { return 24; }
        @Override public float hintRange() { return 72F; }

        private static int bounces(SpellStats s) {
            return 2 + Math.max(0, s.ricochet);
        }

        @Override
        public float copies(SpellStats s, int circle) {
            return s.count() * (1 + bounces(s)) * GunShots.fanBullets(s);
        }

        @Override
        public void cast(SpellContext ctx) {
            GunShots.fire(ctx, new GunShots.Volley(1, 0, 1, 3.4F, 0.3F, ManaBulletEntity.STYLE_RICOCHET,
                    bounces(ctx.formStats()), 0, 0F, 1.0F));
        }
    }

    /**
     * Bala Perfurante: tiro INSTANTÂNEO em linha reta (até 64 blocos, ×Acelerar) que atravessa até 5 inimigos
     * (+ Perfurar) e para no primeiro bloco sólido. Cabeça conta. Leque do Cão = mais linhas em leque.
     */
    public static class PiercingForm extends FormGlyph {
        public static final double RANGE = 64.0;

        public PiercingForm() {
            super("gun_piercing", 10);
            origin(MagicPath.GUNSLINGER);
            tier(3);
        }

        @Override public boolean stageable() { return true; }
        @Override public int baseCastTicks() { return 6; }
        @Override public int baseCooldown() { return 30; }
        @Override public float hintRange() { return (float) RANGE; }

        /** Uma linha atinge vários alvos de uma vez (como uma área): conta 1 por linha. */
        @Override
        public float copies(SpellStats s, int circle) {
            return GunShots.fanBullets(s);
        }

        @Override
        public void cast(SpellContext ctx) {
            LivingEntity caster = ctx.caster();
            if (caster == null) return;
            float power = GunCombat.formPower(ctx);
            SpellContext use = power > 1.001F ? ctx.copy(ctx.powerMult() * power) : ctx;
            int lines = GunShots.fanBullets(ctx.formStats());
            for (int i = 0; i < lines; i++) {
                float yaw = lines == 1 ? 0 : (i - (lines - 1) / 2.0F) * GunShots.FAN_STEP;
                beam(use, caster, yaw, i == 0);
            }
            if (caster instanceof ServerPlayer sp && !ctx.hasOrigin()) GsNetwork.anim(sp, GsNetwork.SHOOT, GunCombat.heldKind(caster).ordinal(),
                    ctx.color() & 0xFFFFFF);
        }

        private static void beam(SpellContext ctx, LivingEntity caster, float yaw, boolean first) {
            ServerLevel level = ctx.level();
            Vec3 look = GunCombat.rotate(ctx.look(), yaw, 0);
            Vec3 from = ctx.hasOrigin() ? ctx.eyePos() : GsFx.muzzle(caster, look, 1);
            double range = RANGE * Math.max(0.5F, ctx.formStats().speedMult());
            Vec3 eye = ctx.hasOrigin() ? from : caster.getEyePosition();
            Vec3 to = eye.add(look.scale(range));
            BlockHitResult block = level.clip(new ClipContext(eye, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
            Vec3 end = block.getType() == HitResult.Type.MISS ? to : block.getLocation();
            int max = 5 + Math.max(0, ctx.formStats().pierce);
            // todos os alvos ao longo da linha, do mais perto ao mais longe
            List<Entity> along = new ArrayList<>(level.getEntities(caster, new AABB(eye, end).inflate(1.0),
                    e -> e instanceof LivingEntity && e.isPickable() && !e.isSpectator() && !ctx.ignores(e) && !ctx.isAlly(e)));
            along.sort(Comparator.comparingDouble(e -> e.distanceToSqr(eye)));
            int hits = 0;
            int rgb = ctx.color() & 0xFFFFFF;
            for (Entity e : along) {
                if (hits >= max) break;
                var clip = e.getBoundingBox().inflate(0.3).clip(eye, end);
                if (clip.isEmpty()) continue;
                Vec3 at = clip.get();
                boolean head = e instanceof LivingEntity le && le.getBbHeight() > 0.6F && at.y >= le.getEyeY() - 0.3;
                float mult = head ? GunCombat.headshotMultiplier(caster) : 1.0F;
                SpellContext shot = mult > 1.001F ? ctx.copy(ctx.powerMult() * mult) : ctx;
                ctx.impact(at, look, e);
                e.invulnerableTime = 0;
                shot.resolveOnEntity(e, null);
                GsFx.impact(level, at, rgb, true);
                if (caster instanceof ServerPlayer sp) GunCombat.onBulletHit(sp, e, head);
                hits++;
            }
            GsFx.tracer(level, from, end, rgb, 0.9F);
            if (first) GsFx.shot(level, from, look, rgb, GunCombat.heldKind(caster));
            GsFx.sound(level, from, SoundEvents.TRIDENT_THUNDER.value(), 0.25F, 2.0F);
            if (block.getType() == HitResult.Type.BLOCK) {
                ctx.impact(end, look, null);
                ctx.resolveOnBlock(block, null);
                GsFx.impact(level, end, rgb, false);
            } else if (hits == 0) {
                ctx.end(end, look);
            }
        }
    }

    private GunForms() {}
}
