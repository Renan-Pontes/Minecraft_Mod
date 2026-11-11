package com.renanpontes.isekaimahou.feature.bosses_e.entity;

import com.renanpontes.isekaimahou.core.MahouConfig;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/** Partículas e sons do Dragão Primordial (servidor → todos por perto). Contagens moderadas: o chefe é grande e longo. */
public final class DragonFx {
    public static final int FIRE = 0xFF6A1F;
    public static final int EMBER = 0xFFB347;
    public static final int ICE = 0xA8E8FF;
    public static final int WATER = 0x3FA9FF;
    public static final int EARTH = 0xA0743C;
    public static final int AMBER = 0xFFC04A;
    public static final int STORM = 0x8FF0FF;
    public static final int BOLT = 0xFFF27A;
    public static final int LIGHT = 0xFFE6A0;
    public static final int SHADOW = 0x8A4FC8;
    public static final int TWILIGHT = 0xC06AA0;

    public static ParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F),
                Math.min(4F, size));
    }

    /** Anel horizontal de partículas. */
    public static void ring(ServerLevel sl, ParticleOptions p, Vec3 c, double r, int points) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            sl.sendParticles(p, c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
    }

    /** Linha de partículas de {@code a} a {@code b}, uma a cada {@code step} blocos. */
    public static void line(ServerLevel sl, ParticleOptions p, Vec3 a, Vec3 b, double step) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        int n = Math.max(1, (int) (len / step));
        for (int i = 0; i <= n; i++) {
            Vec3 q = a.add(d.scale(i / (double) n));
            sl.sendParticles(p, q.x, q.y, q.z, 1, 0, 0, 0, 0);
        }
    }

    /** Coluna vertical de altura {@code h}. */
    public static void column(ServerLevel sl, ParticleOptions p, Vec3 base, int count, double h, double spread) {
        for (int i = 0; i < count; i++) {
            double y = h * i / Math.max(1, count - 1);
            sl.sendParticles(p, base.x, base.y + y, base.z, 1, spread, 0.1, spread, 0.01);
        }
    }

    /** Raio quebrado (zigue-zague) entre dois pontos. */
    public static void bolt(ServerLevel sl, ParticleOptions p, Vec3 a, Vec3 b, double jitter, net.minecraft.util.RandomSource r) {
        Vec3 prev = a;
        int seg = 6;
        for (int i = 1; i <= seg; i++) {
            Vec3 q = a.add(b.subtract(a).scale(i / (double) seg));
            if (i < seg) q = q.add((r.nextDouble() - 0.5) * jitter, (r.nextDouble() - 0.5) * jitter, (r.nextDouble() - 0.5) * jitter);
            line(sl, p, prev, q, 0.4);
            prev = q;
        }
    }

    public static void sound(ServerLevel sl, Vec3 at, SoundEvent s, float vol, float pitch) {
        sl.playSound(null, at.x, at.y, at.z, s, SoundSource.HOSTILE, vol, pitch);
    }

    /** Config de cliente "partículas reduzidas" (falso se não carregada). */
    public static boolean reduced() {
        try {
            return MahouConfig.REDUCED_PARTICLES.get();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private DragonFx() {}
}
