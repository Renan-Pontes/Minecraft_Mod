package com.renanpontes.isekaimahou.feature.fusions_d;

import org.joml.Vector3f;

import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** Visual de servidor das Misturas D (partículas e sons), com cores próprias de cada mistura. */
public final class FdFx {
    public static final int CRYSTAL = 0xE07AF0;
    public static final int CRYSTAL_LIGHT = 0xFFD6FF;
    public static final int ASTRAL = 0x9DB4FF;
    public static final int STAR_WHITE = 0xF2F6FF;
    public static final int HELLFIRE = 0xC0182E;
    public static final int HELL_BLACK = 0x14030A;
    public static final int SOUL = 0x4FD8FF;
    public static final int ABYSS = 0x2A4F9A;
    public static final int ABYSS_DEEP = 0x081530;
    public static final int GRAVITY = 0x6B5BA8;
    public static final int VOID = 0x5A2A8A;
    public static final int VOID_EDGE = 0xD9A8FF;
    public static final int TWILIGHT_GOLD = 0xFFD27A;
    public static final int TWILIGHT_SHADOW = 0x4A1E6E;

    public static ParticleOptions dust(int rgb, float size) {
        // v1.3: brilho fx aditivo (mesmo tamanho do pó do vanilla; respeita o preset gráfico no cliente)
        return com.renanpontes.isekaimahou.core.ElementFx.glow(rgb, size);
    }

    public static DustColorTransitionOptions fade(int from, int to, float size) {
        return new DustColorTransitionOptions(vec(from), vec(to), Math.min(4F, size));
    }

    private static Vector3f vec(int rgb) {
        return new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F);
    }

    /** Teto de partículas por chamada (orçamento por magia). */
    private static int n(int count) {
        return Math.max(1, Math.min(count, 160));
    }

    public static void burst(ServerLevel l, Vec3 p, ParticleOptions o, int count, double spread, double speed) {
        l.sendParticles(o, p.x, p.y, p.z, n(count), spread, spread, spread, speed);
    }

    public static void ring(ServerLevel l, Vec3 c, ParticleOptions p, double radius, int points, double speed) {
        for (int i = 0; i < n(points); i++) {
            double a = Math.PI * 2 * i / points;
            l.sendParticles(p, c.x + Math.cos(a) * radius, c.y, c.z + Math.sin(a) * radius, 1, 0.02, 0.02, 0.02, speed);
        }
    }

    /** Anel que se espalha para fora. */
    public static void shockwave(ServerLevel l, Vec3 c, ParticleOptions p, int points, double speed) {
        for (int i = 0; i < n(points); i++) {
            double a = Math.PI * 2 * i / points;
            l.sendParticles(p, c.x, c.y, c.z, 0, Math.cos(a), 0.03, Math.sin(a), speed);
        }
    }

    /** Anel que se fecha para o centro. */
    public static void implode(ServerLevel l, Vec3 c, ParticleOptions p, double radius, int points, double speed) {
        for (int i = 0; i < n(points); i++) {
            double a = Math.PI * 2 * i / points;
            l.sendParticles(p, c.x + Math.cos(a) * radius, c.y, c.z + Math.sin(a) * radius, 0, -Math.cos(a), 0, -Math.sin(a), speed);
        }
    }

    public static void line(ServerLevel l, Vec3 from, Vec3 to, ParticleOptions p, double perBlock) {
        Vec3 d = to.subtract(from);
        int steps = Math.min(120, Math.max(2, (int) (d.length() * perBlock)));
        for (int i = 0; i <= steps; i++) {
            Vec3 q = from.add(d.scale(i / (double) steps));
            l.sendParticles(p, q.x, q.y, q.z, 1, 0.02, 0.02, 0.02, 0);
        }
    }

    public static void column(ServerLevel l, Vec3 base, ParticleOptions p, double height, int points, double spread) {
        for (int i = 0; i < n(points); i++) {
            l.sendParticles(p, base.x, base.y + height * i / points, base.z, 1, spread, 0.1, spread, 0.01);
        }
    }

    public static void sphere(ServerLevel l, Vec3 c, ParticleOptions p, double radius, int points) {
        var r = l.random;
        for (int i = 0; i < n(points); i++) {
            double u = r.nextDouble() * 2 - 1, th = r.nextDouble() * Math.PI * 2, s = Math.sqrt(1 - u * u);
            l.sendParticles(p, c.x + s * Math.cos(th) * radius, c.y + u * radius, c.z + s * Math.sin(th) * radius, 1, 0, 0, 0, 0);
        }
    }

    public static void sound(ServerLevel l, Vec3 pos, SoundEvent s, float volume, float pitch) {
        l.playSound(null, pos.x, pos.y, pos.z, s, SoundSource.PLAYERS, volume, pitch * (0.94F + l.random.nextFloat() * 0.12F));
    }

    private FdFx() {}
}
