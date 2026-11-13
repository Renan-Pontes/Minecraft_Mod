package com.renanpontes.isekaimahou.feature.legends_b;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Visual e som das lendas (servidor): partículas enviadas também para quem está longe (as lendas acontecem a
 * 40+ blocos), sons e os efeitos desenhados no cliente ({@link LegendsBNet.LegendFx}).
 */
public final class LbFx {
    /** Até onde os jogadores recebem partículas e efeitos destas magias. */
    private static final double FAR = 128.0;
    private static final double NEAR = 32.0;

    public static Vector3f vec(int rgb) {
        return new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F);
    }

    public static ParticleOptions dust(int rgb, float size) {
        // v1.3: brilho fx aditivo (mesmo tamanho do pó do vanilla; respeita o preset gráfico no cliente)
        return com.renanpontes.isekaimahou.core.ElementFx.glow(rgb, size);
    }

    /** Pó que muda de cor enquanto some (desintegração: cor da magia → preto). */
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

    /** Partículas para todos até {@link #FAR} blocos (perto: caminho normal; longe: como "longa distância"). */
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

    /** Partícula com velocidade (count = 0: dx/dy/dz viram a direção). */
    public static void moving(ServerLevel level, ParticleOptions p, Vec3 pos, Vec3 velocity, double speed) {
        send(level, p, pos.x, pos.y, pos.z, 0, velocity.x, velocity.y, velocity.z, speed);
    }

    /** Linha de pó entre dois pontos. */
    public static void line(ServerLevel level, Vec3 from, Vec3 to, ParticleOptions p, double perBlock) {
        Vec3 d = to.subtract(from);
        int steps = Math.max(2, (int) (d.length() * perBlock));
        for (int i = 0; i <= steps; i++) {
            Vec3 q = from.add(d.scale(i / (double) steps));
            send(level, p, q.x, q.y, q.z, 1, 0.02, 0.02, 0.02, 0);
        }
    }

    public static void sound(ServerLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume,
                pitch * (0.94F + level.random.nextFloat() * 0.12F));
    }

    public static void sound(ServerLevel level, Vec3 pos, Holder<SoundEvent> sound, float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume,
                pitch * (0.94F + level.random.nextFloat() * 0.12F));
    }

    // =====================================================================================
    //  Efeitos desenhados no cliente
    // =====================================================================================

    private static void fx(ServerLevel level, Vec3 near, LegendsBNet.LegendFx payload) {
        PacketDistributor.sendToPlayersNear(level, null, near.x, near.y, near.z, FAR, payload);
    }

    /** Feixe de luz estilo farol (gira, rola a textura, afina e some em {@code life} ticks). */
    public static void beam(ServerLevel level, Vec3 from, Vec3 to, int rgb, int life, float radius) {
        Vec3 mid = from.add(to).scale(0.5);
        fx(level, mid, new LegendsBNet.LegendFx(LegendsBNet.BEAM, from, to.subtract(from), rgb, life, radius));
    }

    /** Risco de corte (meia-lua fina que "corre" de uma ponta à outra e some). */
    public static void slash(ServerLevel level, Vec3 center, Vec3 dir, float length, int rgb, int life, float width) {
        Vec3 d = dir.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : dir.normalize();
        fx(level, center, new LegendsBNet.LegendFx(LegendsBNet.SLASH, center, d.scale(length), rgb, life, width));
    }

    /** Onda de choque em anel que se expande até {@code radius} (plano perpendicular a {@code axis}). */
    public static void ring(ServerLevel level, Vec3 center, Vec3 axis, float radius, int rgb, int life) {
        Vec3 a = axis.lengthSqr() < 1.0E-6 ? new Vec3(0, 1, 0) : axis.normalize();
        fx(level, center, new LegendsBNet.LegendFx(LegendsBNet.RING, center, a, rgb, life, radius));
    }

    /** Desintegração: o corpo "esfarela" em pó escuro que sobe, com fagulhas do vazio. */
    public static void disintegrate(ServerLevel level, LivingEntity e, int rgb, int count) {
        Vec3 c = LbUtil.center(e);
        double w = e.getBbWidth() * 0.45, h = e.getBbHeight() * 0.45;
        send(level, fade(rgb, 0x110018, 1.1F), c.x, c.y, c.z, count, w, h, w, 0.02);
        send(level, fade(0x000000, rgb, 0.7F), c.x, c.y, c.z, count / 2, w, h, w, 0.01);
        send(level, Vfx.reversePortal(), c.x, c.y, c.z, count / 2, w, h, w, 0.04);
        send(level, Vfx.ash(), c.x, c.y + h, c.z, count / 2, w, 0.2, w, 0.02);
    }

    private LbFx() {}
}
