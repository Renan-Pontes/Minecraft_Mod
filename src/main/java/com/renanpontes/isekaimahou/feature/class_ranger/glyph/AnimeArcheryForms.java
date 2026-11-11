package com.renanpontes.isekaimahou.feature.class_ranger.glyph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_ranger.RangerFx;
import com.renanpontes.isekaimahou.feature.class_ranger.RangerGlyphIds;
import com.renanpontes.isekaimahou.feature.class_ranger.entity.BulletLineEntity;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellStats;
import com.renanpontes.isekaimahou.glyph.weapon.WeaponArrows;
import com.renanpontes.isekaimahou.glyph.weapon.WeaponFx;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Formas do pacote de técnicas de anime do Ranger:
 * <ul>
 *   <li><b>Disparo Triplo Élfico</b> ({@code ranger_triple_shot}, rank II — homenagem ao arqueiro élfico que atira
 *   correndo, saltando e escorregando): 3 rajadas seguidas de flechas mágicas, cada uma mirando SOZINHA num inimigo
 *   diferente à sua frente; em movimento (correndo, no ar ou montado) cada rajada tem +25% de poder, e no ar você cai
 *   devagar para continuar atirando.</li>
 *   <li><b>Tiro de Linha de Bala</b> ({@code ranger_bullet_line}, rank IV — homenagem à atiradora de elite): mira com a
 *   linha vermelha (o alvo vê!) e o tiro sai instantâneo, sem errar, mais forte quanto mais longe.</li>
 * </ul>
 */
public final class AnimeArcheryForms {
    /** Quem o auto-mirar escolhe: monstros, quem está mirando você e jogadores fora do grupo. */
    static boolean huntable(SpellContext ctx, LivingEntity caster, LivingEntity e) {
        if (!e.isAlive() || e == caster || ctx.isAlly(e) || e.isSpectator()) return false;
        return e instanceof Enemy || (e instanceof Mob m && m.getTarget() == caster) || e instanceof Player;
    }

    /** Inimigos num cone à frente (ângulo em graus), do mais alinhado à mira ao menos alinhado. */
    static List<LivingEntity> cone(SpellContext ctx, LivingEntity caster, Vec3 eye, Vec3 look, double range, double degrees) {
        double cos = Math.cos(Math.toRadians(degrees));
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : ctx.enemiesAround(eye, range)) {
            if (!huntable(ctx, caster, e)) continue;
            Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
            double len = to.length();
            if (len < 0.5 || to.scale(1.0 / len).dot(look) < cos) continue;
            if (!caster.hasLineOfSight(e)) continue;
            out.add(e);
        }
        out.sort(Comparator.comparingDouble(e -> -e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye).normalize().dot(look)));
        return out;
    }

    // =====================================================================================
    //  Disparo Triplo Élfico
    // =====================================================================================
    public static class TripleShot extends FormGlyph {
        public static final int VOLLEYS = 3;
        public static final int GAP = 4;

        public TripleShot() {
            super(RangerGlyphIds.TRIPLE, 9);
            origin(MagicPath.RANGER);
            tier(2);
        }

        @Override
        public int baseCooldown() { return 30; }

        @Override
        public float copies(SpellStats stats, int circle) {
            return VOLLEYS * stats.count();
        }

        @Override
        public float hintRange() { return 32F; }

        @Override
        public void cast(SpellContext ctx) {
            LivingEntity caster = ctx.caster();
            if (caster == null) return;
            ServerLevel level = ctx.level();
            List<LivingEntity> targets = cone(ctx, caster, caster.getEyePosition(), ctx.look(), 32, 40);
            for (int v = 0; v < VOLLEYS; v++) {
                final int volley = v;
                Runnable shot = () -> volley(ctx, level, caster, targets, volley);
                if (v == 0) shot.run();
                else SpellScheduler.schedule(v * GAP, shot);
            }
        }

        private static void volley(SpellContext ctx, ServerLevel level, LivingEntity caster, List<LivingEntity> targets, int v) {
            if (!caster.isAlive() || caster.level() != level) return;
            Vec3 eye = caster.getEyePosition();
            Vec3 look = caster.getLookAngle();
            LivingEntity t = null;
            if (!targets.isEmpty()) {
                // cada rajada num alvo diferente (se tiver menos alvos, recomeça)
                for (int k = 0; k < targets.size() && t == null; k++) {
                    LivingEntity cand = targets.get((v + k) % targets.size());
                    if (cand.isAlive() && cand.level() == level) t = cand;
                }
            }
            Vec3 dir;
            if (t != null) {
                Vec3 aimAt = t.position().add(0, t.getBbHeight() * 0.6, 0).add(t.getDeltaMovement().scale(3));
                dir = aimAt.subtract(eye).normalize();
            } else {
                dir = look;
            }
            boolean moving = caster.isSprinting() || !caster.onGround() || caster.isPassenger();
            SpellContext shot = ctx.withOrigin(eye.add(look.scale(0.4)), dir);
            if (moving) shot.setPowerMult(ctx.powerMult() * 1.25F);
            WeaponArrows.fire(shot);
            if (!caster.onGround() && !caster.isPassenger()) {
                caster.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 16, 0, false, false, true));
            }
            caster.swing(InteractionHand.MAIN_HAND, true);
            Vec3 hand = eye.add(look.scale(0.8)).subtract(0, 0.2, 0);
            level.sendParticles(WeaponFx.dust(0x8FE07A, 1.0F), hand.x, hand.y, hand.z, 6, 0.15, 0.15, 0.15, 0);
            level.sendParticles(ParticleTypes.CHERRY_LEAVES, hand.x, hand.y, hand.z, 3, 0.3, 0.2, 0.3, 0.02);
            RangerFx.sound(level, hand, SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F, 1.3F + v * 0.25F);
            if (moving && v == 0) RangerFx.sound(level, hand, SoundEvents.PHANTOM_FLAP, 0.7F, 1.6F);
        }
    }

    // =====================================================================================
    //  Tiro de Linha de Bala
    // =====================================================================================
    public static class BulletLine extends FormGlyph {
        public static final double RANGE = 96;
        public static final int AIM = 16;

        public BulletLine() {
            super(RangerGlyphIds.BULLET_LINE, 20);
            origin(MagicPath.RANGER);
            tier(4);
            minCircle(5);
        }

        @Override
        public int baseCastTicks() { return 6; }

        @Override
        public int baseCooldown() { return 50; }

        @Override
        public float hintRange() { return (float) RANGE; }

        @Override
        public int hintDuration() { return AIM; }

        @Override
        public void cast(SpellContext ctx) {
            LivingEntity caster = ctx.caster();
            if (caster == null) return;
            ServerLevel level = ctx.level();
            SpellStats s = ctx.formStats();
            Vec3 eye = caster.getEyePosition();
            Vec3 look = ctx.look();
            BlockHitResult bh = level.clip(new ClipContext(eye, eye.add(look.scale(RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
            Vec3 point = bh.getType() == HitResult.Type.BLOCK ? bh.getLocation() : eye.add(look.scale(RANGE));
            List<LivingEntity> targets = new ArrayList<>();
            LivingEntity first = caster instanceof Mob mob && mob.getTarget() != null ? mob.getTarget() : crosshair(ctx, caster, eye, point);
            if (first != null) targets.add(first);
            int n = 1 + Math.max(0, s.split);
            for (LivingEntity e : cone(ctx, caster, eye, look, 64, 15)) {
                if (targets.size() >= n) break;
                if (!targets.contains(e)) targets.add(e);
            }
            int aim = Math.max(6, AIM - 3 * Math.max(0, s.speed));
            if (targets.isEmpty()) {
                level.addFreshEntity(BulletLineEntity.aim(ctx, caster, null, point, aim));
            } else {
                for (int i = 0; i < targets.size(); i++) level.addFreshEntity(BulletLineEntity.aim(ctx, caster, targets.get(i), point, aim + i * 3));
            }
            RangerFx.sound(level, eye, SoundEvents.SPYGLASS_USE, 1.0F, 0.8F);
            RangerFx.sound(level, eye, SoundEvents.LEVER_CLICK, 0.8F, 1.6F);
        }

        /** O inimigo exatamente na mira (até o bloco que a mira toca). */
        @Nullable
        private static LivingEntity crosshair(SpellContext ctx, LivingEntity caster, Vec3 eye, Vec3 end) {
            LivingEntity best = null;
            double bestD = Double.MAX_VALUE;
            for (LivingEntity e : ctx.enemiesAround(eye.lerp(end, 0.5), eye.distanceTo(end) / 2 + 2)) {
                if (!e.isAlive() || ctx.isAlly(e)) continue;
                var clip = e.getBoundingBox().inflate(0.6).clip(eye, end);
                if (clip.isEmpty()) continue;
                double d = eye.distanceToSqr(clip.get());
                if (d < bestD) {
                    bestD = d;
                    best = e;
                }
            }
            return best;
        }
    }

    private AnimeArcheryForms() {}
}
