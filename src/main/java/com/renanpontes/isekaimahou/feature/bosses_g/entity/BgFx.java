package com.renanpontes.isekaimahou.feature.bosses_g.entity;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.core.MahouConfig;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Partículas reaproveitadas pelos chefes do módulo (anéis, linhas, esferas e pó colorido). Orçamento fixo por chamada. */
public final class BgFx {

    public static DustParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F), size);
    }

    /** Anel horizontal. */
    public static void ring(ServerLevel level, Vec3 c, double radius, int points, ParticleOptions p, double speed) {
        points = Math.min(points, 64);
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            level.sendParticles(p, c.x + Math.cos(a) * radius, c.y, c.z + Math.sin(a) * radius, 1, 0.02, 0.02, 0.02, speed);
        }
    }

    /** Linha de partículas entre dois pontos (no máx. 60). */
    public static void line(ServerLevel level, Vec3 from, Vec3 to, ParticleOptions p, double density) {
        Vec3 d = to.subtract(from);
        int steps = Math.min(60, Math.max(2, (int) (d.length() * density)));
        for (int i = 0; i <= steps; i++) {
            Vec3 at = from.add(d.scale(i / (double) steps));
            level.sendParticles(p, at.x, at.y, at.z, 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    /** Casca esférica (explosões, bolhas). */
    public static void sphere(ServerLevel level, Vec3 c, double radius, int points, ParticleOptions p) {
        points = Math.min(points, 80);
        double golden = Math.PI * (3 - Math.sqrt(5));
        for (int i = 0; i < points; i++) {
            double y = 1 - (i / (double) (points - 1)) * 2;
            double r = Math.sqrt(1 - y * y);
            double t = golden * i;
            level.sendParticles(p, c.x + Math.cos(t) * r * radius, c.y + y * radius, c.z + Math.sin(t) * r * radius, 1, 0, 0, 0, 0);
        }
    }

    /** Opção "reduzir partículas" do cliente (só chamar no cliente). */
    public static boolean reduced() {
        try {
            return MahouConfig.REDUCED_PARTICLES.get();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private BgFx() {}
}
