package com.renanpontes.isekaimahou.feature.arcane.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.arcane.ArcaneUtil;
import com.renanpontes.isekaimahou.feature.arcane.RecallMarks;
import com.renanpontes.isekaimahou.feature.arcane.Telekinesis;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Efeitos arcanos de movimento: Puxar, Lampejo, Trocar, Marca e Retorno, Levitar, Queda Lenta, Telecinese. */
public final class MotionEffects {

    // =====================================================================================
    //  Puxar (C1) — Inverter: empurrar
    // =====================================================================================
    public static class PullEffect extends EffectGlyph {
        public PullEffect() {
            super("pull", 8);
            element(Element.ARCANE);
            tier(1);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity caster = ctx.caster();
            if (target == caster) return;
            ServerLevel level = ctx.level();
            Vec3 anchor = caster != null ? caster.position() : target.position().subtract(ctx.look().scale(3));
            Vec3 delta = anchor.subtract(target.position());
            double dist = delta.length();
            if (dist < 1.0E-3) return;
            Vec3 dir = delta.scale(1.0 / dist);
            double resist = target instanceof LivingEntity le
                    ? Mth.clamp(le.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0, 1) * 0.6 : 0;
            if (ArcaneUtil.isBoss(target)) resist = Math.max(resist, 0.75);

            Vec3 vel;
            if (stats.inverted) {
                double s = 1.2 * stats.power() * (1 - resist);
                vel = new Vec3(-dir.x * s, 0.25 + 0.1 * s, -dir.z * s);
            } else {
                // Força proporcional à distância: o alvo cai perto de você em vez de passar direto
                double s = Math.min(2.6, Math.max(0.35, (dist - 1.3) * 0.16)) * (0.85 + 0.15 * stats.power()) * (1 - resist);
                double lift = dir.y < -0.5 ? 0 : 0.3;
                vel = new Vec3(dir.x * s, dir.y * s + lift, dir.z * s);
            }
            target.setDeltaMovement(target.getDeltaMovement().scale(0.2).add(vel));
            target.hurtMarked = true;
            if (target instanceof Mob m) m.getNavigation().stop();

            ParticleOptions dust = ArcaneUtil.dust(ctx, 1.0F);
            Vec3 c = ArcaneUtil.center(target);
            if (!stats.inverted && caster != null) ArcaneUtil.line(level, c, ArcaneUtil.center(caster), dust, 0.7);
            level.sendParticles(stats.inverted ? Vfx.cloud() : Vfx.reversePortal(), c.x, c.y, c.z, 10,
                    0.3, 0.3, 0.3, 0.05);
            ArcaneUtil.sound(level, c, stats.inverted ? SoundEvents.PLAYER_ATTACK_KNOCKBACK : SoundEvents.FISHING_BOBBER_RETRIEVE,
                    0.9F, stats.inverted ? 0.8F : 0.7F);
        }
    }

    // =====================================================================================
    //  Lampejo (C2) — Inverter: recua
    // =====================================================================================
    public static class BlinkEffect extends EffectGlyph {
        public BlinkEffect() {
            super("blink", 12);
            element(Element.ARCANE);
            tier(2);
            minCircle(2);
        }

        @Override
        public boolean wantsBlocks() {
            return true;
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity c = ctx.caster();
            if (c == null || !ArcaneUtil.firstThisTick(ctx, "blink")) return;
            double max = 8.0 * stats.power();
            if (target == c) {
                Vec3 dir = c.getLookAngle();
                blinkAlong(ctx, c, stats.inverted ? dir.scale(-1) : dir, max);
                return;
            }
            Vec3 away = target.position().subtract(c.position()).multiply(1, 0, 1);
            if (away.lengthSqr() < 1.0E-4) away = c.getLookAngle().multiply(1, 0, 1);
            if (away.lengthSqr() < 1.0E-4) return;
            away = away.normalize();
            if (stats.inverted) {
                blinkAlong(ctx, c, away.scale(-1), max);
                return;
            }
            // Aparece atrás do alvo, já olhando para ele
            Vec3 behind = target.position().add(away.scale(target.getBbWidth() * 0.5 + c.getBbWidth() * 0.5 + 0.8));
            if (behind.distanceTo(c.position()) <= max + 2 && ArcaneUtil.fits(c, behind)) {
                float[] rot = ArcaneUtil.lookRotation(behind.add(0, c.getEyeHeight(), 0), ArcaneUtil.center(target));
                Vec3 from = c.position();
                if (ArcaneUtil.teleport(c, behind, rot[0], rot[1])) fx(ctx, c, from, behind);
            } else {
                blinkAlong(ctx, c, behind.subtract(c.position()), Math.min(max, behind.distanceTo(c.position())));
            }
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            LivingEntity c = ctx.caster();
            if (c == null || !ArcaneUtil.firstThisTick(ctx, "blink")) return;
            double max = 8.0 * stats.power();
            Vec3 point = Vec3.atBottomCenterOf(hit.getBlockPos().relative(hit.getDirection()));
            Vec3 to = point.subtract(c.position());
            if (stats.inverted) {
                blinkAlong(ctx, c, to.multiply(-1, 0, -1), max);
                return;
            }
            double d = to.length();
            if (d <= max && ArcaneUtil.fits(c, point)) {
                Vec3 from = c.position();
                if (ArcaneUtil.teleport(c, point)) fx(ctx, c, from, point);
            } else {
                blinkAlong(ctx, c, to, Math.min(max, d));
            }
        }

        private static void blinkAlong(SpellContext ctx, LivingEntity c, Vec3 dir, double max) {
            Vec3 spot = ArcaneUtil.findSafeSpot(c, dir, max);
            if (spot == null) {
                ArcaneUtil.actionbar(c, Component.translatable("message.isekai_mahou.arcane_blink_blocked"));
                ctx.level().sendParticles(Vfx.smoke(), c.getX(), c.getY() + 1, c.getZ(), 8, 0.3, 0.4, 0.3, 0.01);
                return;
            }
            Vec3 from = c.position();
            if (ArcaneUtil.teleport(c, spot)) fx(ctx, c, from, spot);
        }

        private static void fx(SpellContext ctx, LivingEntity c, Vec3 from, Vec3 to) {
            ServerLevel level = ctx.level();
            ParticleOptions dust = ArcaneUtil.dust(ctx, 1.1F);
            ArcaneUtil.teleportFx(level, from, c.getBbHeight(), dust);
            ArcaneUtil.teleportFx(level, to, c.getBbHeight(), dust);
            ArcaneUtil.line(level, from.add(0, c.getBbHeight() * 0.5, 0), to.add(0, c.getBbHeight() * 0.5, 0),
                    ArcaneUtil.dust(ctx, 0.6F), 0.5);
            ArcaneUtil.sound(level, from, SoundEvents.ENDERMAN_TELEPORT, 0.6F, 1.5F);
            ArcaneUtil.sound(level, to, SoundEvents.ENDERMAN_TELEPORT, 0.8F, 1.7F);
        }
    }

    // =====================================================================================
    //  Trocar (C3)
    // =====================================================================================
    public static class SwapEffect extends EffectGlyph {
        public SwapEffect() {
            super("swap", 15);
            element(Element.ARCANE);
            tier(3);
            minCircle(3);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity c = ctx.caster();
            if (c == null || target == c || target.level() != c.level()) return;
            ServerLevel level = ctx.level();
            if (ArcaneUtil.isBoss(target) || (target instanceof Player p && (p.isSpectator() || p.isCreative() && !ctx.isAlly(p)))) {
                ArcaneUtil.resisted(level, target, c);
                return;
            }
            if (c.distanceToSqr(target) > 64 * 64) return;
            Vec3 a = c.position();
            Vec3 b = target.position();
            if (!ArcaneUtil.fits(c, b) || !ArcaneUtil.fits(target, a)) {
                ArcaneUtil.actionbar(c, Component.translatable("message.isekai_mahou.arcane_swap_blocked"));
                return;
            }
            if (!ArcaneUtil.teleport(c, b)) return;
            if (!ArcaneUtil.teleport(target, a)) {
                ArcaneUtil.teleport(c, a); // o alvo não pôde ir: desfaz
                return;
            }
            ParticleOptions dust = ArcaneUtil.dust(ctx, 1.1F);
            ArcaneUtil.teleportFx(level, a, target.getBbHeight(), dust);
            ArcaneUtil.teleportFx(level, b, c.getBbHeight(), dust);
            ArcaneUtil.line(level, a.add(0, 1, 0), b.add(0, 1, 0), ArcaneUtil.dust(ctx, 0.6F), 0.5);
            ArcaneUtil.sound(level, a, SoundEvents.CHORUS_FRUIT_TELEPORT, 0.8F, 1.2F);
            ArcaneUtil.sound(level, b, SoundEvents.CHORUS_FRUIT_TELEPORT, 0.8F, 1.2F);
        }
    }

    // =====================================================================================
    //  Marca e Retorno (C3): 1º uso marca, 2º uso volta
    // =====================================================================================
    public static class RecallEffect extends EffectGlyph {
        public RecallEffect() {
            super("recall", 18);
            element(Element.ARCANE);
            tier(3);
            minCircle(3);
        }

        @Override
        public boolean wantsBlocks() {
            return true;
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity c = ctx.caster();
            if (c == null || !ArcaneUtil.firstThisTick(ctx, "recall")) return;
            recall(ctx, c, target == c ? c.position() : target.position(), stats);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            LivingEntity c = ctx.caster();
            if (c == null || !ArcaneUtil.firstThisTick(ctx, "recall")) return;
            recall(ctx, c, Vec3.atBottomCenterOf(hit.getBlockPos().relative(hit.getDirection())), stats);
        }

        private static void recall(SpellContext ctx, LivingEntity c, Vec3 markPos, SpellStats stats) {
            ServerLevel level = ctx.level();
            RecallMarks.Mark mark = RecallMarks.get(c);
            ParticleOptions dust = ArcaneUtil.dust(ctx, 1.2F);
            // Inverter: refaz a marca aqui (sem voltar), para trocar de ponto sem gastar a viagem
            if (mark == null || stats.inverted) {
                RecallMarks.set(c, new RecallMarks.Mark(level.dimension(), markPos));
                level.sendParticles(Vfx.endRod(), markPos.x, markPos.y + 1, markPos.z, 20, 0.05, 0.8, 0.05, 0.02);
                ArcaneUtil.ring(level, markPos.add(0, 0.1, 0), 0.8, dust, 16);
                ArcaneUtil.sound(level, markPos, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 0.8F);
                ArcaneUtil.actionbar(c, Component.translatable("message.isekai_mahou.arcane_recall_set",
                        Mth.floor(markPos.x), Mth.floor(markPos.y), Mth.floor(markPos.z)));
                return;
            }
            if (!mark.dimension().equals(level.dimension())) {
                ArcaneUtil.actionbar(c, Component.translatable("message.isekai_mahou.arcane_recall_dimension"));
                return;
            }
            Vec3 dest = null;
            for (double dy = 0; dy <= 3.0; dy += 0.5) {
                Vec3 cand = mark.pos().add(0, dy, 0);
                if (ArcaneUtil.fits(c, cand)) {
                    dest = cand;
                    break;
                }
            }
            if (dest == null) {
                ArcaneUtil.actionbar(c, Component.translatable("message.isekai_mahou.arcane_recall_blocked"));
                return;
            }
            Vec3 from = c.position();
            if (!ArcaneUtil.teleport(c, dest)) return;
            RecallMarks.clear(c);
            ArcaneUtil.teleportFx(level, from, c.getBbHeight(), dust);
            ArcaneUtil.teleportFx(level, dest, c.getBbHeight(), dust);
            level.sendParticles(Vfx.endRod(), dest.x, dest.y + 1, dest.z, 25, 0.3, 0.8, 0.3, 0.05);
            ArcaneUtil.sound(level, from, SoundEvents.ENDERMAN_TELEPORT, 0.7F, 0.9F);
            ArcaneUtil.sound(level, dest, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 1.0F);
            ArcaneUtil.actionbar(c, Component.translatable("message.isekai_mahou.arcane_recall_back"));
        }
    }

    // =====================================================================================
    //  Levitar (C2) — Inverter: derruba
    // =====================================================================================
    public static class LevitateEffect extends EffectGlyph {
        public LevitateEffect() {
            super("levitate", 10);
            element(Element.ARCANE);
            tier(2);
            minCircle(2);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le)) return;
            ServerLevel level = ctx.level();
            if (stats.inverted) {
                le.removeEffect(MobEffects.LEVITATION);
                le.removeEffect(MobEffects.SLOW_FALLING);
                Vec3 v = le.getDeltaMovement();
                le.setDeltaMovement(v.x * 0.3, -1.2 * Math.min(2.0F, stats.power()), v.z * 0.3);
                le.hurtMarked = true;
                ctx.status(le, MobEffects.MOVEMENT_SLOWDOWN, 40, 1, stats);
                level.sendParticles(ArcaneUtil.dust(ctx, 1.2F), le.getX(), le.getY() + le.getBbHeight(), le.getZ(), 14,
                        0.3, 0.6, 0.3, 0);
                ArcaneUtil.sound(level, le.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0F, 0.6F);
                return;
            }
            int ticks = ArcaneUtil.isBoss(le) ? 40 : 100;
            ctx.status(le, MobEffects.LEVITATION, ticks, Mth.clamp(stats.amplify, 0, 3), stats);
            ArcaneUtil.ring(level, le.position().add(0, 0.1, 0), le.getBbWidth() * 0.5 + 0.4, ArcaneUtil.dust(ctx, 1.0F), 14);
            level.sendParticles(Vfx.endRod(), le.getX(), le.getY() + 0.2, le.getZ(), 8, 0.3, 0.1, 0.3, 0.05);
            ArcaneUtil.sound(level, le.position(), SoundEvents.SHULKER_BULLET_HIT, 0.8F, 1.3F);
        }
    }

    // =====================================================================================
    //  Queda Lenta (C1, benéfico) — Inverter: inimigos perdem o voo
    // =====================================================================================
    public static class SlowFallEffect extends EffectGlyph {
        public SlowFallEffect() {
            super("slow_fall", 6);
            element(Element.ARCANE);
            tier(1);
        }

        @Override
        public boolean isBeneficial() {
            return true;
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le)) return;
            ServerLevel level = ctx.level();
            if (stats.inverted) {
                le.removeEffect(MobEffects.SLOW_FALLING);
                le.removeEffect(MobEffects.LEVITATION);
                if (le instanceof Player p && p.isFallFlying()) p.stopFallFlying();
                Vec3 v = le.getDeltaMovement();
                le.setDeltaMovement(v.x, Math.min(v.y, -0.6), v.z);
                le.hurtMarked = true;
                level.sendParticles(Vfx.cloud(), le.getX(), le.getY() + le.getBbHeight(), le.getZ(), 8, 0.3, 0.2, 0.3, 0.02);
                ArcaneUtil.sound(level, le.position(), SoundEvents.PHANTOM_FLAP, 0.8F, 0.6F);
                return;
            }
            ctx.status(le, MobEffects.SLOW_FALLING, 200, 0, stats);
            le.resetFallDistance();
            level.sendParticles(ArcaneUtil.dust(0xF4F0FF, 1.0F), le.getX(), le.getY() + le.getBbHeight(), le.getZ(), 12,
                    le.getBbWidth() * 0.6, 0.3, le.getBbWidth() * 0.6, 0);
            level.sendParticles(Vfx.cloud(), le.getX(), le.getY() + 0.1, le.getZ(), 5, 0.3, 0.05, 0.3, 0.01);
            ArcaneUtil.sound(level, le.position(), SoundEvents.PHANTOM_FLAP, 0.5F, 1.6F);
        }
    }

    // =====================================================================================
    //  Telecinese (C3) — Inverter: arremessa
    // =====================================================================================
    public static class TelekinesisEffect extends EffectGlyph {
        public TelekinesisEffect() {
            super("telekinesis", 16);
            element(Element.ARCANE);
            tier(3);
            minCircle(3);
        }

        @Override
        public boolean wantsBlocks() {
            return true;
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity c = ctx.caster();
            if (c == null) return;
            ArcaneUtil.markRan(ctx, "telekinesis");
            int ticks = stats.duration(80);
            if (target == c) {
                // Si Mesmo: agarra o que está na mira (mob + itens em volta do ponto)
                ServerLevel level = ctx.level();
                Vec3 eye = c.getEyePosition();
                Vec3 end = eye.add(c.getLookAngle().scale(24));
                BlockHitResult bh = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c));
                Vec3 point = bh.getType() == HitResult.Type.MISS ? end : bh.getLocation();
                EntityHitResult eh = ProjectileUtil.getEntityHitResult(level, c, eye, point, new AABB(eye, point).inflate(1.0),
                        e -> e.isPickable() && !e.isSpectator() && e != c);
                if (eh != null) {
                    point = eh.getLocation();
                    grab(ctx, c, eh.getEntity(), stats, ticks);
                }
                grabLoose(ctx, c, point, stats.radius(3), stats, ticks);
                return;
            }
            grab(ctx, c, target, stats, ticks);
            grabLoose(ctx, c, target.position(), 2.0, stats, ticks);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            LivingEntity c = ctx.caster();
            if (c == null || ArcaneUtil.ranThisTick(ctx, "telekinesis")) return;
            grabLoose(ctx, c, hit.getLocation(), stats.radius(3), stats, stats.duration(80));
        }

        private static void grab(SpellContext ctx, LivingEntity c, Entity e, SpellStats stats, int ticks) {
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return;
            if (ArcaneUtil.isBoss(e)) {
                ArcaneUtil.resisted(ctx.level(), e, c);
                return;
            }
            if (stats.inverted) Telekinesis.fling(c, e, stats.power());
            else Telekinesis.hold(c, e, ticks, ctx.color());
            Vec3 p = ArcaneUtil.center(e);
            ctx.level().sendParticles(ArcaneUtil.dust(ctx, 1.2F), p.x, p.y, p.z, 12, e.getBbWidth() * 0.5, e.getBbHeight() * 0.4,
                    e.getBbWidth() * 0.5, 0);
        }

        /** Itens e orbes de XP soltos perto do ponto. */
        private static void grabLoose(SpellContext ctx, LivingEntity c, Vec3 point, double radius, SpellStats stats, int ticks) {
            for (Entity e : ctx.level().getEntities((Entity) null, new AABB(point, point).inflate(radius),
                    e -> (e instanceof ItemEntity || e instanceof ExperienceOrb) && e.isAlive() && e.distanceToSqr(point) <= radius * radius)) {
                if (stats.inverted) Telekinesis.fling(c, e, stats.power());
                else Telekinesis.hold(c, e, ticks, ctx.color());
            }
        }
    }

    private MotionEffects() {}
}
