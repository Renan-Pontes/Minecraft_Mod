package com.renanpontes.isekaimahou.feature.class_rogue.glyph;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_rogue.RogueCombat;
import com.renanpontes.isekaimahou.feature.class_rogue.RogueFx;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Passo Sombrio (forma de origem do Ladino, Sombra): você some e reaparece NAS COSTAS do inimigo na mira (até 16
 * blocos; Acelerar +4 cada), fica invisível por um instante e os efeitos acertam o alvo já pelas costas (Apunhalar
 * ×2,5!). Sem alvo na mira: um salto de sombra de até 8 blocos para a frente (sem efeitos). Ignora o Dividir (1 cópia).
 */
public class ShadowStepForm extends FormGlyph {
    public static final float RANGE = 16.0F;

    public ShadowStepForm() {
        super("rogue_shadow_step", 8);
        origin(MagicPath.ROGUE);
        element(Element.SHADOW);
        tier(1);
    }

    @Override
    public int baseCooldown() {
        return 30;
    }

    @Override
    public float copies(SpellStats stats, int circle) {
        return 1F;
    }

    @Override
    public float hintRange() {
        return RANGE;
    }

    @Override
    public int hintDuration() {
        return 15;
    }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null || !caster.isAlive()) return;
        ServerLevel level = ctx.level();
        SpellStats s = ctx.formStats();
        float range = RANGE + 4F * Math.max(0, s.speed);
        LivingEntity target = aimTarget(ctx, caster, range);
        if (target != null && RogueCombat.teleportBehind(level, caster, target, range + 2)) {
            caster.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, s.duration(15), 0, false, false));
            RogueCombat.startAmbush(caster, 40);
            ctx.resolveOnEntity(target, null);
            return;
        }
        // sem alvo: salto curto de sombra para a frente, parando antes de parede
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getLookAngle().multiply(1, 0, 1);
        if (look.lengthSqr() < 1.0E-4) return;
        look = look.normalize();
        HitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(8)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        double dist = Math.max(0, hit.getLocation().distanceTo(eye) - 0.8);
        Vec3 dest = caster.position().add(look.scale(dist));
        for (int up = 0; up <= 1; up++) {
            Vec3 d = dest.add(0, up, 0);
            if (level.noCollision(caster, caster.getBoundingBox().move(d.subtract(caster.position()))) && dist > 0.5) {
                Vec3 old = caster.position();
                RogueFx.vanish(level, old);
                caster.teleportTo(d.x, d.y, d.z);
                if (caster instanceof ServerPlayer sp) sp.connection.teleport(d.x, d.y, d.z, sp.getYRot(), sp.getXRot());
                caster.resetFallDistance();
                RogueFx.vanish(level, d);
                RogueFx.sound(level, d, net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT, 0.5F, 1.9F);
                caster.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, s.duration(15), 0, false, false));
                RogueCombat.startAmbush(caster, 30);
                ctx.end(d, look);
                return;
            }
        }
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE, caster.getX(), caster.getY() + 1, caster.getZ(), 8,
                0.3, 0.4, 0.3, 0.01);
    }

    /** Inimigo mais perto da linha da mira (cone de ~18°), com linha de visão, até {@code range}. */
    @Nullable
    public static LivingEntity aimTarget(SpellContext ctx, LivingEntity caster, double range) {
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getLookAngle();
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity e : ctx.enemiesAround(eye, range)) {
            if (e == caster || ctx.ignores(e)) continue;
            Vec3 to = RogueFx.center(e).subtract(eye);
            double d = to.length();
            if (d < 1.0E-3) continue;
            double cos = to.scale(1 / d).dot(look);
            if (cos < 0.95 || !caster.hasLineOfSight(e)) continue;
            double score = (1 - cos) * 40 + d * 0.05;
            if (score < bestScore) {
                bestScore = score;
                best = e;
            }
        }
        return best;
    }
}
