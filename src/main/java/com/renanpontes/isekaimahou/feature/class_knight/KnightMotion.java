package com.renanpontes.isekaimahou.feature.class_knight;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Investida Rúnica: o conjurador dispara em linha reta (velocidade forçada a cada tick) e aplica os efeitos em todos
 * que atravessa (uma vez cada), terminando com um golpe no chão. Bater de frente numa parede encerra a investida.
 * Uma investida por entidade.
 */
public final class KnightMotion {
    private static final class Dash {
        final LivingEntity e;
        final SpellContext ctx;
        final Vec3 dir;
        final double speed;
        final double radius;
        int ticks;
        final Set<UUID> hit = new HashSet<>();
        boolean first = true;

        Dash(LivingEntity e, SpellContext ctx, Vec3 dir, double speed, double radius, int ticks) {
            this.e = e;
            this.ctx = ctx;
            this.dir = dir;
            this.speed = speed;
            this.radius = radius;
            this.ticks = ticks;
        }
    }

    private static final List<Dash> DASHES = new ArrayList<>();

    public static void dash(LivingEntity e, SpellContext ctx, Vec3 dir, double speed, double radius, int ticks) {
        DASHES.removeIf(d -> d.e == e);
        DASHES.add(new Dash(e, ctx, dir.normalize(), speed, radius, ticks));
        if (e.level() instanceof ServerLevel level) {
            KnightFx.runeCircle(level, e.position(), 1.2, ctx.color() & 0xFFFFFF, 20, 0F);
            KnightFx.sound(level, e.position(), SoundEvents.TRIDENT_RIPTIDE_1.value(), 1.0F, 1.2F);
        }
    }

    public static boolean dashing(LivingEntity e) {
        for (Dash d : DASHES) if (d.e == e) return true;
        return false;
    }

    public static void tick(MinecraftServer server) {
        if (DASHES.isEmpty()) return;
        List<Dash> ended = new ArrayList<>();
        Iterator<Dash> it = DASHES.iterator();
        while (it.hasNext()) {
            Dash d = it.next();
            LivingEntity e = d.e;
            if (e.isRemoved() || !e.isAlive() || !(e.level() instanceof ServerLevel level)) {
                it.remove();
                continue;
            }
            if (!d.first && e.horizontalCollision || d.ticks-- <= 0) {
                it.remove();
                ended.add(d);
                continue;
            }
            d.first = false;
            double vy = e.onGround() ? 0.02 : Math.min(0.0, e.getDeltaMovement().y * 0.5);
            e.setDeltaMovement(d.dir.x * d.speed, vy, d.dir.z * d.speed);
            e.hurtMarked = true;
            e.fallDistance = 0F;
            int rgb = d.ctx.color() & 0xFFFFFF;
            Vec3 c = KnightFx.center(e);
            level.sendParticles(KnightFx.dust(rgb, 1.2F), c.x, c.y, c.z, 6, 0.3, 0.5, 0.3, 0);
            level.sendParticles(ParticleTypes.CLOUD, e.getX(), e.getY() + 0.1, e.getZ(), 2, 0.2, 0.0, 0.2, 0.01);
            for (LivingEntity o : level.getEntitiesOfClass(LivingEntity.class, e.getBoundingBox().inflate(d.radius),
                    o -> o != e && o.isAlive() && !o.isSpectator())) {
                if (!d.hit.add(o.getUUID()) || d.ctx.isAlly(o)) continue;
                Vec3 oc = KnightFx.center(o);
                d.ctx.impact(oc, d.dir, o);
                o.invulnerableTime = 0;
                d.ctx.resolveOnEntity(o, e);
                // empurra para o lado da investida
                Vec3 side = KnightFx.side(d.dir).scale(Math.signum(oc.subtract(c).dot(KnightFx.side(d.dir))) * 0.6);
                o.push(side.x, 0.25, side.z);
                o.hurtMarked = true;
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, oc.x, oc.y, oc.z, 1, 0, 0, 0, 0);
                KnightFx.sound(level, oc, SoundEvents.PLAYER_ATTACK_STRONG, 1.0F, 0.9F);
            }
        }
        for (Dash d : ended) finish(d);
    }

    private static void finish(Dash d) {
        LivingEntity e = d.e;
        if (!(e.level() instanceof ServerLevel level)) return;
        e.setDeltaMovement(e.getDeltaMovement().scale(0.2));
        e.hurtMarked = true;
        Vec3 p = e.position();
        KnightFx.runeCircle(level, p, 2.0, d.ctx.color() & 0xFFFFFF, 28, 0.5F);
        level.sendParticles(ParticleTypes.EXPLOSION, p.x, p.y + 0.3, p.z, 1, 0, 0, 0, 0);
        KnightFx.sound(level, p, SoundEvents.GENERIC_EXPLODE.value(), 0.5F, 1.4F);
        d.ctx.end(p.add(0, e.getBbHeight() * 0.5, 0), d.dir);
    }

    public static void clear() {
        DASHES.clear();
    }

    private KnightMotion() {}
}
