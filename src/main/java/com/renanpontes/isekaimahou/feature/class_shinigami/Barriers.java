package com.renanpontes.isekaimahou.feature.class_shinigami;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Barreiras do Bakudō nº 81 (Muro do Vazio): uma parede vertical de Reiatsu dourado. Enquanto dura, desfaz os
 * projéteis inimigos que a tocam, empurra de volta os inimigos que tentam atravessar e, uma vez por segundo, aplica os
 * efeitos da magia em quem encosta nela. No máximo {@link #MAX_PER_CASTER} por conjurador e {@link #MAX_TOTAL} no total.
 */
public final class Barriers {
    public static final int MAX_PER_CASTER = 3;
    public static final int MAX_TOTAL = 48;

    private static final class Wall {
        final SpellContext ctx;
        final ServerLevel level;
        final UUID owner;
        final Vec3 center;
        final Vec3 normal;
        final Vec3 side;
        final double half;
        final double height;
        final long end;
        int age;

        Wall(SpellContext ctx, ServerLevel level, UUID owner, Vec3 center, Vec3 normal, double half, double height, long end) {
            this.ctx = ctx;
            this.level = level;
            this.owner = owner;
            this.center = center;
            this.normal = normal;
            this.side = new Vec3(-normal.z, 0, normal.x);
            this.half = half;
            this.height = height;
            this.end = end;
        }

        AABB box() {
            Vec3 a = center.add(side.scale(half)), b = center.subtract(side.scale(half));
            return new AABB(Math.min(a.x, b.x), center.y, Math.min(a.z, b.z), Math.max(a.x, b.x), center.y + height, Math.max(a.z, b.z))
                    .inflate(1.2, 0.2, 1.2);
        }

        /** Distância (com sinal) de um ponto ao plano e se ele está dentro da largura/altura. */
        double signed(Vec3 p) {
            return p.subtract(center).dot(normal);
        }

        boolean within(Vec3 p, double margin) {
            Vec3 d = p.subtract(center);
            return Math.abs(d.dot(side)) <= half + margin && d.y >= -margin && d.y <= height + margin;
        }
    }

    private static final List<Wall> WALLS = new ArrayList<>();

    /** Ergue uma parede (centro no chão, normal horizontal = para onde ela "olha"). */
    public static void raise(SpellContext ctx, Vec3 center, Vec3 normal, double half, double height, int duration) {
        LivingEntity caster = ctx.caster();
        UUID owner = caster != null ? caster.getUUID() : ctx.casterId();
        Vec3 n = new Vec3(normal.x, 0, normal.z);
        n = n.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : n.normalize();
        int mine = 0;
        Wall oldest = null;
        for (Wall w : WALLS) {
            if (w.owner.equals(owner)) {
                mine++;
                if (oldest == null || w.end < oldest.end) oldest = w;
            }
        }
        if (mine >= MAX_PER_CASTER && oldest != null) WALLS.remove(oldest);
        if (WALLS.size() >= MAX_TOTAL) WALLS.remove(0);
        Wall w = new Wall(ctx, ctx.level(), owner, center, n, half, height, ctx.level().getGameTime() + duration);
        WALLS.add(w);
        ShinigamiFx.sound(ctx.level(), center, SoundEvents.BEACON_ACTIVATE, 0.9F, 1.5F);
        ShinigamiFx.sound(ctx.level(), center, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 0.6F);
        outline(w, true);
    }

    static void tick(MinecraftServer server) {
        if (WALLS.isEmpty()) return;
        Iterator<Wall> it = WALLS.iterator();
        while (it.hasNext()) {
            Wall w = it.next();
            long now = w.level.getGameTime();
            if (now >= w.end || server.getLevel(w.level.dimension()) != w.level) {
                shatter(w);
                it.remove();
                continue;
            }
            w.age++;
            if (w.age % 4 == 0) outline(w, false);
            AABB box = w.box();
            // projéteis inimigos se desfazem ao tocar a parede
            for (Projectile p : w.level.getEntitiesOfClass(Projectile.class, box, Entity::isAlive)) {
                Entity owner = p.getOwner();
                if (owner != null && (owner.getUUID().equals(w.owner) || w.ctx.isAlly(owner))) continue;
                Vec3 pos = p.position();
                if (Math.abs(w.signed(pos)) > 0.9 || !w.within(pos, 0.3)) continue;
                w.level.sendParticles(ShinigamiFx.KIDO_GOLD, pos.x, pos.y, pos.z, 8, 0.2, 0.2, 0.2, 0.05);
                w.level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y, pos.z, 4, 0.2, 0.2, 0.2, 0.1);
                ShinigamiFx.sound(w.level, pos, SoundEvents.SHIELD_BLOCK, 0.7F, 1.4F);
                p.discard();
            }
            // inimigos não atravessam; uma vez por segundo, os efeitos em quem encosta
            boolean pulse = w.age % 20 == 0;
            for (LivingEntity e : w.ctx.enemiesAround(w.center.add(0, w.height / 2, 0), w.half + w.height)) {
                Vec3 pos = e.position().add(0, e.getBbHeight() * 0.5, 0);
                double s = w.signed(pos);
                double reach = 0.6 + e.getBbWidth() / 2;
                if (Math.abs(s) > reach || !w.within(pos, e.getBbWidth() / 2)) continue;
                Vec3 push = w.normal.scale(s >= 0 ? 0.45 : -0.45);
                e.setDeltaMovement(e.getDeltaMovement().multiply(0.2, 1, 0.2).add(push.x, 0.08, push.z));
                e.hurtMarked = true;
                if (pulse) w.ctx.resolveOnEntity(e, null);
            }
        }
    }

    /** Contorno (bordas + uma grade leve) de Reiatsu dourado. */
    private static void outline(Wall w, boolean full) {
        ServerLevel l = w.level;
        int cols = Math.max(2, (int) Math.ceil(w.half * 2 / 0.6));
        int rows = Math.max(2, (int) Math.ceil(w.height / 0.6));
        for (int i = 0; i <= cols; i++) {
            Vec3 bottom = w.center.add(w.side.scale(-w.half + 2 * w.half * i / cols));
            l.sendParticles(ShinigamiFx.KIDO_GOLD, bottom.x, bottom.y + 0.05, bottom.z, 1, 0, 0, 0, 0);
            l.sendParticles(ShinigamiFx.KIDO_GOLD, bottom.x, bottom.y + w.height, bottom.z, 1, 0, 0, 0, 0);
        }
        for (int j = 0; j <= rows; j++) {
            double y = w.height * j / rows;
            Vec3 a = w.center.add(w.side.scale(w.half)).add(0, y, 0), b = w.center.add(w.side.scale(-w.half)).add(0, y, 0);
            l.sendParticles(ShinigamiFx.KIDO_GOLD, a.x, a.y, a.z, 1, 0, 0, 0, 0);
            l.sendParticles(ShinigamiFx.KIDO_GOLD, b.x, b.y, b.z, 1, 0, 0, 0, 0);
        }
        int fill = full ? cols * rows : Math.max(4, cols * rows / 6);
        for (int k = 0; k < fill; k++) {
            double u = (l.random.nextDouble() * 2 - 1) * w.half;
            double v = l.random.nextDouble() * w.height;
            Vec3 p = w.center.add(w.side.scale(u)).add(0, v, 0);
            l.sendParticles(full ? ShinigamiFx.KIDO_GOLD : ShinigamiFx.WHITE, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }

    private static void shatter(Wall w) {
        Vec3 c = w.center.add(0, w.height / 2, 0);
        w.level.sendParticles(ShinigamiFx.KIDO_GOLD, c.x, c.y, c.z, 30, w.half * 0.5, w.height * 0.4, 0.2, 0.05);
        ShinigamiFx.sound(w.level, c, SoundEvents.GLASS_BREAK, 0.7F, 1.3F);
    }

    static void forgetOwner(UUID id) {
        WALLS.removeIf(w -> w.owner.equals(id));
    }

    static void clear() {
        WALLS.clear();
    }

    private Barriers() {}
}
