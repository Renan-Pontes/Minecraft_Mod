package com.renanpontes.isekaimahou.feature.bosses_b.entity;

import org.joml.Vector3f;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Partículas e sons dos chefes (servidor: sendParticles; cliente: addParticle). */
public final class BbFx {
    public static final int SHADOW = 0x8A4FC8;
    public static final int SHADOW_DEEP = 0x2A0F45;
    public static final int LIGHT = 0xFFE27A;
    public static final int LIGHT_WHITE = 0xFFF8E0;
    public static final int TWILIGHT = 0xC06AA0;

    public static DustParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(vec(rgb), size);
    }

    private static Vector3f vec(int rgb) {
        return new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F);
    }

    public static void sound(ServerLevel level, Vec3 at, SoundEvent s, float vol, float pitch) {
        level.playSound(null, at.x, at.y, at.z, s, SoundSource.HOSTILE, vol, pitch);
    }

    /** Anel horizontal. */
    public static void ring(ServerLevel level, ParticleOptions p, Vec3 c, double radius, int points) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            level.sendParticles(p, c.x + Math.cos(a) * radius, c.y, c.z + Math.sin(a) * radius, 1, 0.02, 0.02, 0.02, 0);
        }
    }

    /** Linha de partículas entre dois pontos (passo em blocos). */
    public static void line(ServerLevel level, ParticleOptions p, Vec3 from, Vec3 to, double step) {
        Vec3 d = to.subtract(from);
        int n = Math.max(2, (int) (d.length() / step));
        for (int i = 0; i <= n; i++) {
            Vec3 q = from.add(d.scale(i / (double) n));
            level.sendParticles(p, q.x, q.y, q.z, 1, 0.01, 0.01, 0.01, 0);
        }
    }

    /** Coluna vertical (pilares de luz, gêiseres de sombra). */
    public static void column(ServerLevel level, ParticleOptions p, Vec3 base, double height, int count, double spread) {
        for (int i = 0; i < count; i++) {
            double y = base.y + height * i / Math.max(1, count - 1);
            level.sendParticles(p, base.x, y, base.z, 1, spread, 0.05, spread, 0.0);
        }
    }

    /** Pontos aleatórios numa esfera (cliente). */
    public static void clientSphere(Level level, ParticleOptions p, Vec3 c, double r, int points, RandomSource rnd) {
        for (int i = 0; i < points; i++) {
            double u = rnd.nextDouble() * 2 - 1;
            double th = rnd.nextDouble() * Math.PI * 2;
            double s = Math.sqrt(1 - u * u);
            level.addParticle(p, c.x + r * s * Math.cos(th), c.y + r * u, c.z + r * s * Math.sin(th), 0, 0, 0);
        }
    }

    private BbFx() {}
}
