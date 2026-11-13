package com.renanpontes.isekaimahou.feature.legends_e;

import org.joml.Vector3f;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Visual e som das Magias de Nível Supremo (servidor): partículas que chegam também a quem está longe (essas magias
 * acontecem a 40+ blocos), sons com leve variação e os efeitos desenhados no cliente ({@link LeNet.Fx}).
 */
public final class LeFx {
    private static final double FAR = 160.0;
    private static final double NEAR = 32.0;

    public static Vector3f vec(int rgb) {
        return new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F);
    }

    public static ParticleOptions dust(int rgb, float size) {
        // v1.3: brilho fx aditivo (mesmo tamanho do pó do vanilla; respeita o preset gráfico no cliente)
        return com.renanpontes.isekaimahou.core.ElementFx.glow(rgb, size);
    }

    public static DustColorTransitionOptions fade(int from, int to, float size) {
        return new DustColorTransitionOptions(vec(from), vec(to), Math.max(0.05F, Math.min(4.0F, size)));
    }

    public static int lighten(int rgb, float f) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        return ((int) (r + (255 - r) * f) << 16) | ((int) (g + (255 - g) * f) << 8) | (int) (b + (255 - b) * f);
    }

    public static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    /** Partículas para todos até {@link #FAR} blocos (longe: como "longa distância", para não sumirem). */
    public static void send(ServerLevel level, ParticleOptions p, double x, double y, double z, int count,
                            double dx, double dy, double dz, double speed) {
        for (ServerPlayer sp : level.players()) {
            double d2 = sp.distanceToSqr(x, y, z);
            if (d2 > FAR * FAR) continue;
            level.sendParticles(sp, p, d2 > NEAR * NEAR, x, y, z, count, dx, dy, dz, speed);
        }
    }

    public static void send(ServerLevel level, ParticleOptions p, Vec3 pos, int count, double spread, double speed) {
        send(level, p, pos.x, pos.y, pos.z, count, spread, spread, spread, speed);
    }

    /** Partícula com velocidade própria (count = 0: dx/dy/dz viram a direção). */
    public static void moving(ServerLevel level, ParticleOptions p, Vec3 pos, Vec3 velocity, double speed) {
        send(level, p, pos.x, pos.y, pos.z, 0, velocity.x, velocity.y, velocity.z, speed);
    }

    public static void sound(ServerLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume,
                pitch * (0.95F + level.random.nextFloat() * 0.1F));
    }

    public static void sound(ServerLevel level, Vec3 pos, Holder<SoundEvent> sound, float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume,
                pitch * (0.95F + level.random.nextFloat() * 0.1F));
    }

    // =====================================================================================
    //  Efeitos desenhados no cliente
    // =====================================================================================

    private static void fx(ServerLevel level, Vec3 near, double range, LeNet.Fx payload) {
        PacketDistributor.sendToPlayersNear(level, null, near.x, near.y, near.z, range, payload);
    }

    /** Lança de luz que cai de {@code from} até {@code to} em {@code life} ticks (o impacto acontece no fim). */
    public static void lance(ServerLevel level, Vec3 from, Vec3 to, int rgb, int life, float width) {
        fx(level, to, FAR, new LeNet.Fx(LeNet.LANCE, from, to.subtract(from), rgb, life, width));
    }

    /** Meteoro em brasa que cai de {@code from} até {@code to} em {@code life} ticks. */
    public static void meteor(ServerLevel level, Vec3 from, Vec3 to, int rgb, int life, float radius) {
        fx(level, to, FAR, new LeNet.Fx(LeNet.METEOR, from, to.subtract(from), rgb, life, radius));
    }

    /** Onda de choque em anel que se expande até {@code radius}. */
    public static void ring(ServerLevel level, Vec3 center, Vec3 axis, float radius, int rgb, int life) {
        Vec3 a = axis.lengthSqr() < 1.0E-6 ? new Vec3(0, 1, 0) : axis.normalize();
        fx(level, center, FAR, new LeNet.Fx(LeNet.RING, center, a, rgb, life, radius));
    }

    /** Clarão na tela de quem está a até {@code range} blocos. {@code strength} 0..1. */
    public static void flash(ServerLevel level, Vec3 center, double range, int rgb, int life, float strength) {
        fx(level, center, range, new LeNet.Fx(LeNet.FLASH, center, Vec3.ZERO, rgb, life, strength));
    }

    private LeFx() {}
}
