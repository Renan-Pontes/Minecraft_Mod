package com.renanpontes.isekaimahou.feature.glyphs_classes_b;

import org.joml.Vector3f;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Efeitos de servidor (partículas e sons) dos glifos do módulo — poucas partículas, sempre num ponto só. */
public final class GcbFx {
    public static final int ENCHANTER = 0x5FE3C0;
    public static final int SHINIGAMI = 0xD9483B;
    public static final int REISHI = 0x8FD0FF;
    public static final int GUN = 0xFFD86B;
    public static final int RANGER = 0x6BCB5A;
    public static final int GAMBLER = 0xE04A8C;
    public static final int GOLD = 0xFFD34A;

    public static DustParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F),
                Mth.clamp(size, 0.2F, 4.0F));
    }

    public static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    public static Vec3 center(Entity e) {
        return e.position().add(0, e.getBbHeight() * 0.5, 0);
    }

    /** Anel horizontal de partículas. */
    public static void ring(ServerLevel level, Vec3 c, double radius, int points, ParticleOptions p) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            level.sendParticles(p, c.x + Math.cos(a) * radius, c.y, c.z + Math.sin(a) * radius, 1, 0, 0, 0, 0);
        }
    }

    /** Linha de partículas (rastro). No máximo 24 pontos. */
    public static void line(ServerLevel level, Vec3 from, Vec3 to, ParticleOptions p) {
        Vec3 d = to.subtract(from);
        int n = Mth.clamp((int) (d.length() * 2), 2, 24);
        for (int i = 0; i <= n; i++) {
            Vec3 q = from.add(d.scale(i / (double) n));
            level.sendParticles(p, q.x, q.y, q.z, 1, 0, 0, 0, 0);
        }
    }

    /** Espiral dourada (proporção áurea) em volta de um ponto: o raio cresce ×φ a cada quarto de volta. */
    public static void goldenSpiral(ServerLevel level, Vec3 c, float scale, int rgb) {
        DustParticleOptions p = dust(rgb, 0.9F);
        double phi = 1.6180339887;
        for (int i = 0; i < 14; i++) {
            double theta = i * (Math.PI / 4);
            double r = 0.06 * scale * Math.pow(phi, theta / (Math.PI / 2));
            if (r > 2.2) break;
            level.sendParticles(p, c.x + Math.cos(theta) * r, c.y + i * 0.03, c.z + Math.sin(theta) * r, 1, 0, 0, 0, 0);
        }
    }

    private GcbFx() {}
}
