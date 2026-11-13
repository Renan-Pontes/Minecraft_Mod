package com.renanpontes.isekaimahou.feature.class_hollow.glyph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.renanpontes.isekaimahou.feature.class_hollow.HollowFx;
import com.renanpontes.isekaimahou.feature.class_hollow.HollowNetwork;
import com.renanpontes.isekaimahou.feature.class_hollow.HollowSounds;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * O disparo dos Ceros do Hollow (Cero, Lança do Murciélago, Gran Rey Cero): feixe em linha reta que para nos blocos (a
 * menos que {@code ghost}), atravessa TODOS os inimigos no caminho (dano próprio + efeitos da magia, se houver) e
 * explode no fim (metade do dano em quem o feixe não pegou). Não quebra blocos. O feixe, o clarão e o tremor são
 * desenhados no cliente (pacote {@link HollowNetwork.Fx}).
 */
public final class HollowBeam {
    /** Resultado: onde o feixe terminou e quem o feixe atravessou. */
    public record Shot(Vec3 end, int hits, List<LivingEntity> struck) {}

    public static Shot fire(SpellContext ctx, LivingEntity caster, Vec3 from, Vec3 dir, double range, float width, float damage,
                            float blastRadius, boolean ghost, int rgb, boolean grand) {
        return fire(ctx, caster, from, dir, range, width, damage, blastRadius, ghost, rgb, grand, HollowNetwork.CERO_BEAM);
    }

    /** Igual, escolhendo o desenho do feixe ({@link HollowNetwork#CERO_BEAM} ou {@link HollowNetwork#OSCURAS_BEAM}). */
    public static Shot fire(SpellContext ctx, LivingEntity caster, Vec3 from, Vec3 dir, double range, float width, float damage,
                            float blastRadius, boolean ghost, int rgb, boolean grand, int beamFx) {
        ServerLevel level = ctx.level();
        Vec3 d = dir.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : dir.normalize();
        Vec3 to = from.add(d.scale(range));
        if (!ghost) {
            HitResult r = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
            if (r.getType() == HitResult.Type.BLOCK) to = r.getLocation();
        }
        Vec3 end = to;
        List<LivingEntity> along = new ArrayList<>();
        // uma consulta só, na caixa do feixe (não roda por tick)
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, end).inflate(width + 1), LivingEntity::isAlive)) {
            if (e == caster || ctx.isAlly(e) || ctx.ignores(e)) continue;
            if (e.getBoundingBox().inflate(width * 0.5).clip(from, end).isPresent()) along.add(e);
        }
        along.sort(Comparator.comparingDouble(e -> e.distanceToSqr(from)));
        Set<UUID> struck = new HashSet<>();
        for (LivingEntity e : along) {
            struck.add(e.getUUID());
            Vec3 at = e.position().add(0, e.getBbHeight() / 2, 0);
            ctx.impact(at, d, e);
            ctx.damage(e, damage, ctx.formStats(), caster);
            if (e.isAlive() && !ctx.spell().effects().isEmpty()) ctx.resolveOnEntity(e, caster);
            Vec3 push = d.scale(grand ? 1.4 : 0.7);
            e.setDeltaMovement(e.getDeltaMovement().add(push.x, 0.25, push.z));
            e.hurtMarked = true;
        }
        for (LivingEntity e : ctx.enemiesAround(end, blastRadius)) {
            if (struck.contains(e.getUUID())) continue;
            ctx.damage(e, damage * 0.5F, ctx.formStats(), caster);
            Vec3 push = e.position().subtract(end);
            push = push.lengthSqr() < 1.0E-4 ? Vec3.ZERO : push.normalize().scale(grand ? 1.2 : 0.6);
            e.setDeltaMovement(e.getDeltaMovement().add(push.x, 0.3, push.z));
            e.hurtMarked = true;
        }
        // o feixe, o clarão e a explosão são do cliente; o servidor manda pouco
        HollowNetwork.fxNear(level, beamFx, caster.getId(), from, end, width, rgb, grand ? 160 : 96);
        HollowNetwork.fxNear(level, HollowNetwork.SHOCKWAVE, caster.getId(), end, Vec3.ZERO, blastRadius, rgb, grand ? 160 : 96);
        HollowNetwork.fxNear(level, HollowNetwork.SHAKE, caster.getId(), end, Vec3.ZERO, grand ? 1.4F : 0.35F + width * 0.15F, rgb,
                grand ? 64 : 24);
        level.sendParticles(grand ? ParticleTypes.EXPLOSION_EMITTER : ParticleTypes.EXPLOSION, end.x, end.y, end.z, 1, 0, 0, 0, 0);
        level.sendParticles(HollowFx.dust(rgb, 1.6F), end.x, end.y, end.z, grand ? 40 : 14, blastRadius * 0.3, blastRadius * 0.3,
                blastRadius * 0.3, 0.1);
        level.sendParticles(ParticleTypes.FLASH, from.x, from.y, from.z, 1, 0, 0, 0, 0);
        HollowFx.sound(level, from, HollowSounds.CERO_FIRE.get(), grand ? 2.5F : 1.4F, grand ? 0.7F : 1.0F);
        HollowFx.sound(level, from, SoundEvents.WARDEN_SONIC_BOOM, grand ? 1.6F : 0.9F, grand ? 0.45F : width > 1.5F ? 0.6F : 0.85F);
        HollowFx.sound(level, end, SoundEvents.GENERIC_EXPLODE.value(), grand ? 2.0F : 0.8F, grand ? 0.6F : 0.95F);
        ctx.end(end, d);
        return new Shot(end, along.size(), along);
    }

    private HollowBeam() {}
}
