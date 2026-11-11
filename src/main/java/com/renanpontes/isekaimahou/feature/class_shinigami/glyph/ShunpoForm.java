package com.renanpontes.isekaimahou.feature.class_shinigami.glyph;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_shinigami.ShinigamiFx;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Shunpo — o passo instantâneo: o Shinigami some e reaparece até 9 blocos à frente (+3 por Expandir), parando antes de
 * paredes. Se estiver mirando um inimigo no alcance, reaparece ÀS COSTAS dele e aplica os efeitos nele (golpe pelas
 * costas). Sem efeitos, é só o deslocamento. Deixa uma imagem residual e zera a queda. 1 cópia.
 */
public class ShunpoForm extends FormGlyph {
    public ShunpoForm() {
        super("shinigami_shunpo", 5);
        origin(MagicPath.SHINIGAMI);
        element(Element.WIND);
        tier(1);
    }

    @Override
    public float copies(SpellStats stats, int circle) {
        return 1F;
    }

    @Override
    public boolean requiresEffect() {
        return false;
    }

    @Override
    public int baseCooldown() {
        return 24;
    }

    @Override
    public float hintRange() {
        return 9F;
    }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null || caster.isPassenger()) return;
        ServerLevel level = ctx.level();
        SpellStats s = ctx.formStats();
        double max = 9 + 3 * Math.max(0, s.aoe);
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getLookAngle();
        Vec3 start = caster.position();
        LivingEntity target = aimed(ctx, caster, eye, look, max);
        Vec3 end;
        if (target != null) {
            Vec3 dir = target.position().subtract(start);
            Vec3 flat = new Vec3(dir.x, 0, dir.z);
            flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(look.x, 0, look.z).normalize() : flat.normalize();
            Vec3 behind = target.position().add(flat.scale(target.getBbWidth() / 2 + 1.1));
            end = safe(level, caster, behind) ? behind : target.position().subtract(flat.scale(target.getBbWidth() / 2 + 1.1));
            if (!safe(level, caster, end)) end = dash(level, caster, start, flat, max);
        } else {
            Vec3 dir = new Vec3(look.x, Math.max(-0.3, Math.min(0.3, look.y)), look.z);
            end = dash(level, caster, start, dir.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : dir.normalize(), max);
        }
        ShinigamiFx.afterimage(level, caster, start, end);
        com.renanpontes.isekaimahou.feature.class_shinigami.ShinigamiFxNet.burst(caster,
                com.renanpontes.isekaimahou.feature.class_shinigami.ShinigamiFxNet.SHUNPO, start, end, caster.getBbHeight(),
                0x101018, null);
        ShinigamiFx.sound(level, start, SoundEvents.ENDER_DRAGON_FLAP, 0.4F, 2.0F);
        if (caster instanceof ServerPlayer sp) {
            float yaw = caster.getYRot(), pitch = caster.getXRot();
            if (target != null) {
                Vec3 face = target.position().subtract(end);
                yaw = (float) (Math.atan2(-face.x, face.z) * (180 / Math.PI));
            }
            sp.teleportTo(level, end.x, end.y, end.z, yaw, pitch);
        } else {
            caster.moveTo(end.x, end.y, end.z, caster.getYRot(), caster.getXRot());
        }
        caster.fallDistance = 0;
        caster.setDeltaMovement(Vec3.ZERO);
        caster.hurtMarked = true;
        ShinigamiFx.sound(level, end, SoundEvents.PLAYER_ATTACK_NODAMAGE, 0.8F, 1.8F);
        if (target != null) {
            ctx.impact(target.position().add(0, target.getBbHeight() / 2, 0), look, target);
            ctx.resolveOnEntity(target, caster);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY() + target.getBbHeight() / 2,
                    target.getZ(), 1, 0, 0, 0, 0);
        }
        ctx.end(end.add(0, caster.getBbHeight() * 0.6, 0), look);
    }

    /** Inimigo na mira (raio de entidades até {@code max}). */
    @Nullable
    private static LivingEntity aimed(SpellContext ctx, LivingEntity caster, Vec3 eye, Vec3 look, double max) {
        Vec3 to = eye.add(look.scale(max + 2));
        AABB box = caster.getBoundingBox().expandTowards(look.scale(max + 2)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(caster.level(), caster, eye, to, box,
                e -> e instanceof LivingEntity le && le.isAlive() && e != caster && !ctx.isAlly(e) && !e.isSpectator(), 0.3F);
        if (hit == null) return null;
        Entity e = hit.getEntity();
        // precisa de linha de visão
        var clip = caster.level().clip(new net.minecraft.world.level.ClipContext(eye, e.getEyePosition(),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, caster));
        if (clip.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) return null;
        return (LivingEntity) e;
    }

    private static boolean safe(ServerLevel level, LivingEntity caster, Vec3 at) {
        AABB box = caster.getBoundingBox().move(at.subtract(caster.position()));
        return level.noCollision(caster, box);
    }

    private static Vec3 dash(ServerLevel level, LivingEntity caster, Vec3 start, Vec3 dir, double max) {
        double dist = 0;
        for (double d = 0.5; d <= max; d += 0.5) {
            if (!level.noCollision(caster, caster.getBoundingBox().move(dir.scale(d)))) break;
            dist = d;
        }
        return start.add(dir.scale(dist));
    }
}
