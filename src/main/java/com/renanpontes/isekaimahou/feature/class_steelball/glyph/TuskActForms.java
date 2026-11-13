package com.renanpontes.isekaimahou.feature.class_steelball.glyph;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_steelball.SbFx;
import com.renanpontes.isekaimahou.feature.class_steelball.SbFxNet;
import com.renanpontes.isekaimahou.feature.class_steelball.SbSpin;
import com.renanpontes.isekaimahou.feature.class_steelball.SbTechniques;
import com.renanpontes.isekaimahou.feature.class_steelball.entity.SpiralHoleEntity;
import com.renanpontes.isekaimahou.feature.class_steelball.entity.TuskNailEntity;
import com.renanpontes.isekaimahou.glyph.CoreGlyphs;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellCost;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Os ATOS da unha (homenagem Tusk), glifos de origem do Steel Ball liberados pela lição E pelas Partes do Cadáver
 * Santo ({@link SbTechniques}): Ato 1 → Ato 2 → Ato 3 → (Ato 4 = Rotação Infinita, a forma G que já existia).
 */
public final class TuskActForms {
    public static final int ACT1_COLOR = 0xE58AE8;
    public static final int ACT3_COLOR = 0xB45CFF;

    /**
     * Tusk Ato 1 — Unhas-Bala: as dez unhas disparadas uma a uma (5 + 1 por Dividir, até 10), alternando as mãos;
     * cada unha voa em saca-rolhas e faz o alvo RODOPIAR. Cada unha é uma cópia dos efeitos (60% do poder).
     */
    public static class Act1Form extends FormGlyph {
        public static final float POWER = 0.6F;

        public Act1Form() {
            super("tusk_act1", 7);
            origin(MagicPath.STEELBALL);
            element(Element.EARTH);
            tier(2);
        }

        public static int nails(SpellStats s) {
            return Math.min(10, 5 + Math.max(0, s.split));
        }

        @Override public float copies(SpellStats stats, int circle) { return nails(stats); }
        @Override public boolean stageable() { return true; }
        @Override public int baseCooldown() { return 20; }
        @Override public float hintRange() { return 44F; }

        @Override
        public void cast(SpellContext ctx) {
            LivingEntity caster = ctx.caster();
            if (caster == null) return;
            ServerLevel level = ctx.level();
            SpellStats s = ctx.formStats();
            SpellContext nailCtx = ctx.copy(ctx.powerMult() * POWER);
            int n = nails(s);
            float speed = 2.2F * s.speedMult();
            boolean staged = ctx.hasOrigin();
            Vec3 fixedFrom = ctx.eyePos();
            Vec3 fixedLook = ctx.look();
            if (!staged) SbTechniques.actionbar(caster instanceof net.minecraft.world.entity.player.Player p ? p : null,
                    Component.translatable("callout.isekai_mahou.steelball.tusk_act1"), ACT1_COLOR);
            for (int i = 0; i < n; i++) {
                final int idx = i;
                Runnable shot = () -> {
                    if (!caster.isAlive() || caster.level() != level) return;
                    Vec3 look = staged ? fixedLook : aim(caster);
                    Vec3 side = new Vec3(-look.z, 0, look.x);
                    side = side.lengthSqr() < 1.0E-4 ? Vec3.ZERO : side.normalize().scale(idx % 2 == 0 ? 0.3 : -0.3);
                    Vec3 from = staged ? fixedFrom : caster.getEyePosition().add(look.scale(0.5)).add(side).subtract(0, 0.3, 0);
                    Vec3 d = s.spread ? CoreGlyphs.rotate(look, (ctx.random().nextFloat() - 0.5F) * 24F, (ctx.random().nextFloat() - 0.5F) * 8F)
                            : look;
                    level.addFreshEntity(TuskNailEntity.act1(nailCtx, from, d, speed, idx * 1.3F));
                    SbFxNet.at(level, SbFxNet.NAIL_FIRE, from, d, 1F, ACT1_COLOR, caster.getId());
                    if (!staged) caster.swing(idx % 2 == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, true);
                };
                if (i == 0) shot.run();
                else SpellScheduler.schedule(2 * i, shot);
            }
            SbFx.sound(level, caster.position(), SoundEvents.AMETHYST_CLUSTER_BREAK, 0.5F, 1.6F);
        }
    }

    /**
     * Tusk Ato 2 — Rotação Dourada: a unha dourada; onde acerta deixa um Buraco Espiral que PERSEGUE o inimigo por
     * 6 s (×Estender) e o morde a cada 0,75 s (60% do poder). Custo por cópia: o acerto + as mordidas (moderadas).
     */
    public static class Act2Form extends FormGlyph {
        public static final int HOLE_TICKS = 120;

        public Act2Form() {
            super("tusk_act2", 14);
            origin(MagicPath.STEELBALL);
            element(Element.EARTH);
            tier(3);
            minCircle(2);
        }

        @Override
        public float copies(SpellStats stats, int circle) {
            return stats.count() * (1F + SpellCost.pulseCopies(stats.duration(HOLE_TICKS), SpiralHoleEntity.BITE) * SpiralHoleEntity.BITE_POWER);
        }

        @Override public boolean stageable() { return true; }
        @Override public int baseCooldown() { return 50; }
        @Override public float hintRange() { return 60F; }
        @Override public int hintDuration() { return HOLE_TICKS; }

        @Override
        public void cast(SpellContext ctx) {
            LivingEntity caster = ctx.caster();
            if (caster == null) return;
            ServerLevel level = ctx.level();
            SpellStats s = ctx.formStats();
            Vec3 look = ctx.hasOrigin() ? ctx.look() : aim(caster);
            Vec3 from = ctx.hasOrigin() ? ctx.eyePos() : caster.getEyePosition().add(look.scale(0.6)).subtract(0, 0.25, 0);
            int hole = s.duration(HOLE_TICKS);
            int n = Math.max(1, s.count());
            for (int i = 0; i < n; i++) {
                float yaw = n == 1 ? 0 : (i - (n - 1) / 2F) * 8F;
                level.addFreshEntity(TuskNailEntity.act2(ctx, from, CoreGlyphs.rotate(look, yaw, 0), 3.0F * s.speedMult(), hole));
            }
            SbFxNet.at(level, SbFxNet.NAIL_FIRE, from, look, 2F, SbFx.GOLD, caster.getId());
            SbFxNet.at(level, SbFxNet.GOLDEN_RECT, from.add(look.scale(0.9)), look, 1.2F, SbFx.GOLD, caster.getId());
            SbFx.sound(level, from, SoundEvents.TRIDENT_RIPTIDE_1.value(), 0.9F, 1.6F);
            SbFx.sound(level, from, SoundEvents.BEACON_POWER_SELECT, 0.6F, 2.0F);
            if (!ctx.hasOrigin()) {
                caster.swing(InteractionHand.MAIN_HAND, true);
                if (caster instanceof ServerPlayer sp) {
                    SbTechniques.callout(sp, Component.translatable("callout.isekai_mahou.steelball.tusk_act2"),
                            Component.translatable("callout.isekai_mahou.steelball.tusk_act2.sub"), SbFx.GOLD);
                }
            }
        }
    }

    /**
     * Tusk Ato 3 — Buraco de Minhoca: você atira a unha em si mesmo, mergulha no buraco e atravessa o espaço até o
     * ponto mirado (até 24 blocos ×Acelerar) — ou para as COSTAS do inimigo mirado. Dentro do buraco, nada acerta você
     * (escudo de 2 s). Ao sair, o buraco gira e aplica os efeitos nos inimigos a até 3,5 blocos (×Expandir).
     */
    public static class Act3Form extends FormGlyph {
        public static final double RANGE = 24.0;
        public static final float RADIUS = 3.5F;
        public static final int TRAVEL = 8;
        public static final int SHIELD = 40;

        public Act3Form() {
            super("tusk_act3", 18);
            origin(MagicPath.STEELBALL);
            element(Element.EARTH);
            tier(4);
            minCircle(4);
        }

        @Override public float copies(SpellStats stats, int circle) { return 1F; }
        @Override public int baseCooldown() { return 80; }
        @Override public float hintRange() { return (float) RANGE; }
        @Override public float hintRadius() { return RADIUS; }

        @Override
        public void cast(SpellContext ctx) {
            LivingEntity caster = ctx.caster();
            if (caster == null) return;
            ServerLevel level = ctx.level();
            SpellStats s = ctx.formStats();
            double range = RANGE * Math.max(0.5F, s.speedMult());
            Vec3 dest = destination(ctx, caster, range);
            Vec3 entry = caster.position();
            // o tiro em si mesmo
            Vec3 chest = caster.position().add(0, caster.getBbHeight() * 0.6, 0);
            SbFxNet.at(level, SbFxNet.NAIL_FIRE, chest, new Vec3(0, -1, 0), 3F, ACT3_COLOR, caster.getId());
            SbFxNet.at(level, SbFxNet.WORMHOLE, entry, dest == null ? entry : dest, 1F, ACT3_COLOR, caster.getId());
            SbFx.sound(level, chest, SoundEvents.ARROW_HIT_PLAYER, 0.8F, 0.7F);
            SbFx.sound(level, chest, SoundEvents.PORTAL_TRIGGER, 0.35F, 2.0F);
            SpiralHoleEntity.portal(level, entry);
            caster.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, TRAVEL + 2, 0, false, false));
            if (caster instanceof ServerPlayer sp) {
                SbTechniques.shield(sp, SHIELD);
                SbTechniques.callout(sp, Component.translatable("callout.isekai_mahou.steelball.tusk_act3"),
                        Component.translatable("callout.isekai_mahou.steelball.tusk_act3.sub"), ACT3_COLOR);
            }
            if (dest == null) {
                // nenhum lugar livre: o buraco se abre e fecha no lugar (a explosão sai aqui mesmo)
                SpellScheduler.schedule(TRAVEL, () -> emerge(ctx, caster, entry, s));
                return;
            }
            SpiralHoleEntity.portal(level, dest);
            SpellScheduler.schedule(TRAVEL, () -> {
                if (!caster.isAlive() || caster.level() != level) return;
                Vec3 look = caster.getLookAngle();
                caster.stopRiding();
                if (caster instanceof ServerPlayer sp) {
                    sp.teleportTo(level, dest.x, dest.y, dest.z, sp.getYRot(), sp.getXRot());
                } else {
                    caster.teleportTo(dest.x, dest.y, dest.z);
                }
                caster.resetFallDistance();
                caster.setDeltaMovement(look.scale(0.1));
                caster.hurtMarked = true;
                emerge(ctx, caster, dest, s);
            });
        }

        /** Saída do buraco: espiral roxa, os efeitos nos inimigos em volta e todos ficam Girados. */
        private static void emerge(SpellContext ctx, LivingEntity caster, Vec3 at, SpellStats s) {
            ServerLevel level = ctx.level();
            if (caster.level() != level) return;
            float r = s.radius(RADIUS);
            // a explosão em espiral da saída é desenhada no cliente (WORMHOLE enviado no início); aqui só o anel de alcance
            SbFx.spinRing(level, at.add(0, 0.1, 0), ACT3_COLOR, r, 16);
            boolean any = false;
            for (LivingEntity e : ctx.enemiesAround(at, r)) {
                e.invulnerableTime = 0;
                ctx.resolveOnEntity(e, null);
                if (e.isAlive()) SbSpin.apply(e, caster, 1, 60);
                any = true;
            }
            if (!any) ctx.end(at, caster.getLookAngle());
        }

        /** Ponto de saída: atrás do inimigo mirado; senão, o ponto do chão/parede mirado (um pouco antes). */
        @Nullable
        private static Vec3 destination(SpellContext ctx, LivingEntity caster, double range) {
            ServerLevel level = ctx.level();
            Vec3 eye = caster.getEyePosition();
            Vec3 look = caster.getLookAngle();
            Vec3 far = eye.add(look.scale(range));
            BlockHitResult block = level.clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
            Vec3 end = block.getType() == HitResult.Type.MISS ? far : block.getLocation();
            EntityHitResult ehr = ProjectileUtil.getEntityHitResult(level, caster, eye, end, new AABB(eye, end).inflate(1.0),
                    e -> e instanceof LivingEntity && e.isAlive() && !ctx.isAlly(e) && !e.isSpectator(), 0.3F);
            if (caster instanceof Mob mob && mob.getTarget() != null) ehr = new EntityHitResult(mob.getTarget());
            if (ehr != null && ehr.getEntity() instanceof LivingEntity t) {
                Vec3 back = t.getLookAngle().multiply(1, 0, 1);
                if (back.lengthSqr() < 1.0E-3) back = t.position().subtract(caster.position()).multiply(1, 0, 1).scale(-1);
                back = back.lengthSqr() < 1.0E-3 ? new Vec3(1, 0, 0) : back.normalize();
                Vec3 behind = t.position().subtract(back.scale(t.getBbWidth() + 1.3));
                Vec3 ok = free(caster, behind);
                if (ok != null) return ok;
            }
            Vec3 back = end.subtract(look.scale(0.8));
            for (int i = 0; i < 6; i++) {
                Vec3 ok = free(caster, back.subtract(look.scale(i * 1.5)));
                if (ok != null && ok.distanceToSqr(caster.position()) > 4) return ok;
            }
            return null;
        }

        /** Posição livre para o corpo (tenta o ponto, 1 bloco acima e "pousa" até 3 blocos abaixo). */
        @Nullable
        private static Vec3 free(Entity e, Vec3 p) {
            for (double dy : new double[] {0, 1, -1, -2, -3}) {
                Vec3 q = p.add(0, dy, 0);
                if (e.level().noCollision(e, e.getDimensions(e.getPose()).makeBoundingBox(q))) {
                    // pousa no chão se der (até 3 blocos)
                    Vec3 ground = q;
                    for (int k = 0; k < 3; k++) {
                        Vec3 down = ground.add(0, -1, 0);
                        if (!e.level().noCollision(e, e.getDimensions(e.getPose()).makeBoundingBox(down))) break;
                        ground = down;
                    }
                    return ground;
                }
            }
            return null;
        }
    }

    /** Mira do conjurador (mobs miram no alvo). */
    static Vec3 aim(LivingEntity caster) {
        if (caster instanceof Mob mob && mob.getTarget() != null) {
            return mob.getTarget().getEyePosition().subtract(caster.getEyePosition()).normalize();
        }
        return caster.getLookAngle();
    }

    private TuskActForms() {}
}
