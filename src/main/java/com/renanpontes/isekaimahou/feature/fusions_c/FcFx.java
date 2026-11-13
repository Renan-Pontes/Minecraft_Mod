package com.renanpontes.isekaimahou.feature.fusions_c;

import org.joml.Vector3f;

import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** Partículas e sons das Misturas C (servidor). Cores de cada fusão e formas de partícula. */
public final class FcFx {
    public static final int MAGMA = 0xFF4500;
    public static final int MAGMA_HOT = 0xFFB040;
    public static final int MAGMA_DARK = 0x3A1208;
    public static final int STEAM = 0xE8F0F4;
    public static final int STEAM_GREY = 0x9FB0BA;
    public static final int VOLT = 0x7FDBFF;
    public static final int VOLT_WHITE = 0xE8FBFF;
    public static final int FROST = 0xA8E8FF;
    public static final int FROST_DEEP = 0x4C9CD8;
    public static final int NATURE = 0x4FBF4A;
    public static final int BARK = 0x5A3E22;
    public static final int POLLEN = 0xFFE066;
    public static final int POLLEN_PINK = 0xFFB0D8;
    public static final int SAND = 0xE3C77A;
    public static final int SAND_DARK = 0xA88A4A;
    public static final int SUN = 0xFFB300;
    public static final int SUN_WHITE = 0xFFF4C8;
    public static final int PURITY = 0xE8F8FF;
    public static final int HOLY = 0xFFE9A8;

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

    /** Anel horizontal parado. */
    public static void ring(ServerLevel level, Vec3 c, ParticleOptions p, double radius, int points, double speed) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            level.sendParticles(p, c.x + Math.cos(a) * radius, c.y, c.z + Math.sin(a) * radius, 1, 0.02, 0.02, 0.02, speed);
        }
    }

    /** Anel que se espalha para fora (velocidade radial). */
    public static void shockwave(ServerLevel level, Vec3 c, ParticleOptions p, int points, double speed) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            level.sendParticles(p, c.x, c.y, c.z, 0, Math.cos(a), 0.03, Math.sin(a), speed);
        }
    }

    /** Anel que se fecha para o centro (implosão). */
    public static void implode(ServerLevel level, Vec3 c, ParticleOptions p, double radius, int points, double speed) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            level.sendParticles(p, c.x + Math.cos(a) * radius, c.y, c.z + Math.sin(a) * radius, 0, -Math.cos(a), 0, -Math.sin(a), speed);
        }
    }

    /** Linha de partículas entre dois pontos. */
    public static void line(ServerLevel level, Vec3 from, Vec3 to, ParticleOptions p, double perBlock) {
        Vec3 d = to.subtract(from);
        int steps = Math.max(2, Math.min(160, (int) (d.length() * perBlock)));
        for (int i = 0; i <= steps; i++) {
            Vec3 q = from.add(d.scale(i / (double) steps));
            level.sendParticles(p, q.x, q.y, q.z, 1, 0.02, 0.02, 0.02, 0);
        }
    }


    /** Pontos aleatórios na casca de uma esfera. */
    public static void sphere(ServerLevel level, Vec3 c, ParticleOptions p, double radius, int points, double outSpeed) {
        var r = level.random;
        for (int i = 0; i < points; i++) {
            double u = r.nextDouble() * 2 - 1, th = r.nextDouble() * Math.PI * 2, s = Math.sqrt(1 - u * u);
            double x = s * Math.cos(th), z = s * Math.sin(th);
            if (outSpeed > 0) level.sendParticles(p, c.x + x * radius, c.y + u * radius, c.z + z * radius, 0, x, u, z, outSpeed);
            else level.sendParticles(p, c.x + x * radius, c.y + u * radius, c.z + z * radius, 1, 0, 0, 0, 0);
        }
    }


    public static void sound(ServerLevel level, Vec3 pos, SoundEvent s, float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, s, SoundSource.PLAYERS, volume, pitch * (0.92F + level.random.nextFloat() * 0.16F));
    }

    private FcFx() {}
}
