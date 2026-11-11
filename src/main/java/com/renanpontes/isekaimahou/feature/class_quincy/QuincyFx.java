package com.renanpontes.isekaimahou.feature.class_quincy;

import org.joml.Vector3f;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Visual do Reishi (servidor): pó azul-gelo, estrela Quincy no chão, fluxos de energia (absorção/Auswählen) e
 * explosões de luz. Tudo com quantidade limitada (as partículas vão para quem está perto).
 */
public final class QuincyFx {
    public static final DustParticleOptions REISHI = dust(Quincy.REISHI_RGB, 1.1F);
    public static final DustParticleOptions REISHI_SMALL = dust(Quincy.REISHI_RGB, 0.7F);
    public static final DustParticleOptions WHITE = dust(0xF2FAFF, 0.9F);
    public static final DustParticleOptions DEEP = dust(Quincy.DEEP_RGB, 1.2F);

    public static DustParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 0xFF) / 255F, ((rgb >> 8) & 0xFF) / 255F, (rgb & 0xFF) / 255F), size);
    }

    public static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    /** Explosão de luz (impacto). */
    public static void burst(ServerLevel level, Vec3 at, int count, double spread) {
        level.sendParticles(REISHI, at.x, at.y, at.z, count, spread, spread, spread, 0.02);
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, Math.max(1, count / 4), spread * 0.6, spread * 0.6, spread * 0.6, 0.05);
    }

    /** Linha de pó entre dois pontos (fluxo de energia). */
    public static void line(ServerLevel level, Vec3 from, Vec3 to, ParticleOptions p, double step) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 1.0E-3) return;
        int n = Math.min(48, (int) Math.ceil(len / step));
        for (int i = 0; i <= n; i++) {
            Vec3 at = from.add(d.scale(i / (double) n));
            level.sendParticles(p, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
    }

    /** Partículas que viajam de {@code from} até {@code to} (a energia sendo puxada). */
    public static void stream(ServerLevel level, Vec3 from, Vec3 to, int count) {
        Vec3 d = to.subtract(from);
        for (int i = 0; i < count; i++) {
            double t = level.random.nextDouble();
            Vec3 at = from.add(d.scale(t)).add((level.random.nextDouble() - 0.5) * 0.3, (level.random.nextDouble() - 0.5) * 0.3,
                    (level.random.nextDouble() - 0.5) * 0.3);
            Vec3 v = d.normalize().scale(0.25);
            level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 0, v.x, v.y, v.z, 1.0);
            level.sendParticles(REISHI_SMALL, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
    }

    /** Anel horizontal (gira com {@code phase}). */
    public static void ring(ServerLevel level, Vec3 c, double radius, int points, float phase, ParticleOptions p) {
        for (int i = 0; i < points; i++) {
            double a = phase + i * Mth.TWO_PI / points;
            level.sendParticles(p, c.x + Math.cos(a) * radius, c.y, c.z + Math.sin(a) * radius, 1, 0, 0, 0, 0);
        }
    }

    /** A estrela Quincy (cinco pontas dentro de um círculo) desenhada no chão. */
    public static void pentacle(ServerLevel level, Vec3 c, double radius, float phase) {
        ring(level, c, radius, Math.max(16, (int) (radius * 10)), phase, REISHI);
        Vec3[] pts = new Vec3[5];
        for (int k = 0; k < 5; k++) {
            double a = phase - Math.PI / 2 + k * Mth.TWO_PI / 5;
            pts[k] = new Vec3(c.x + Math.cos(a) * radius, c.y, c.z + Math.sin(a) * radius);
        }
        int[] order = {0, 2, 4, 1, 3, 0};
        for (int i = 0; i < 5; i++) line(level, pts[order[i]], pts[order[i + 1]], WHITE, 0.35);
    }

    /** O arco espiritual se formando na frente do atirador (lampejo ao disparar). */
    public static void bowFlash(ServerLevel level, Entity shooter) {
        Vec3 look = shooter.getLookAngle();
        Vec3 side = new Vec3(-look.z, 0, look.x).normalize();
        if (side.lengthSqr() < 1.0E-4) side = new Vec3(1, 0, 0);
        Vec3 up = side.cross(look).normalize();
        Vec3 c = shooter.getEyePosition().add(look.scale(0.9)).add(0, -0.15, 0);
        for (int i = 0; i <= 10; i++) {
            double t = -1 + 2 * i / 10.0;
            Vec3 at = c.add(up.scale(t * 0.75)).add(look.scale(-(1 - t * t) * 0.3));
            level.sendParticles(REISHI_SMALL, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
    }

    /** Coluna de luz (rank, Auswählen, NPC se desfazendo). */
    public static void pillar(ServerLevel level, Vec3 base, double height, int density) {
        for (int i = 0; i < density; i++) {
            double y = level.random.nextDouble() * height;
            double a = level.random.nextDouble() * Mth.TWO_PI;
            double r = 0.15 + level.random.nextDouble() * 0.45;
            level.sendParticles(i % 3 == 0 ? ParticleTypes.END_ROD : REISHI, base.x + Math.cos(a) * r, base.y + y, base.z + Math.sin(a) * r,
                    1, 0, 0.05, 0, 0.02);
        }
    }

    private QuincyFx() {}
}
