package com.renanpontes.isekaimahou.feature.class_hollow;

import org.joml.Vector3f;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Ajudantes de efeitos do LADO DO SERVIDOR (poucas partículas — o servidor não sabe o preset gráfico do cliente; o
 * espetáculo de verdade vai pelo pacote {@link HollowNetwork.Fx} e é desenhado/multiplicado no cliente).
 */
public final class HollowFx {
    public static final DustParticleOptions REIATSU = dust(Hollow.REIATSU_RGB, 1.4F);
    public static final DustParticleOptions BLACK = dust(Hollow.DARK_RGB, 1.6F);
    public static final DustParticleOptions BONE = dust(Hollow.BONE_RGB, 1.1F);

    public static DustParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F), size);
    }

    public static void sound(ServerLevel level, Vec3 at, SoundEvent s, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, volume, pitch);
    }

    public static void sound(ServerLevel level, Vec3 at, Holder<SoundEvent> s, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, volume, pitch);
    }

    /** Anel horizontal de partículas (poucas: o cliente desenha a onda de verdade). */
    public static void ring(ServerLevel level, Vec3 c, double r, int n, ParticleOptions p) {
        for (int i = 0; i < n; i++) {
            double a = i * Mth.TWO_PI / n;
            level.sendParticles(p, c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
    }

    /** Linha de partículas de a até b. */
    public static void line(ServerLevel level, Vec3 a, Vec3 b, ParticleOptions p, double step) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 1.0E-3) return;
        int n = Math.min(80, (int) (len / step));
        for (int i = 0; i <= n; i++) {
            Vec3 at = a.add(d.scale(i / (double) Math.max(1, n)));
            level.sendParticles(p, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
    }

    private HollowFx() {}
}
