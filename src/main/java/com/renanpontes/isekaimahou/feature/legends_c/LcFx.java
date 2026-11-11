package com.renanpontes.isekaimahou.feature.legends_c;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

/** Efeitos de servidor do módulo: manda os desenhos ao cliente, partículas visíveis de longe e sons. */
public final class LcFx {
    /** Cores das técnicas. */
    public static final int RED = 0xE0182A;
    public static final int RED_DEEP = 0x8B0A1A;
    public static final int BLUE = 0x2F6BFF;
    public static final int BLUE_LIGHT = 0x9FD8FF;
    public static final int SLASH_CORE = 0xFFF0F0;
    public static final int FIRE = 0xFF7A1A;
    public static final int VOID = 0x8A6BFF;

    /** Distância até onde os jogadores recebem os desenhos. */
    public static final double RANGE = 128.0;

    public static void send(ServerLevel level, byte kind, Vec3 a, Vec3 b, int color, int life, float size, int extra) {
        PacketDistributor.sendToPlayersNear(level, null, a.x, a.y, a.z, RANGE, new LcNet.Fx(kind, a, b, color, life, size, extra));
    }

    public static void slash(ServerLevel level, Vec3 from, Vec3 to, float height, int color, int life) {
        send(level, LcNet.SLASH, from, to, color, life, height, 0);
    }

    /** Grade de cortes desenhada sobre um alvo (meia largura/altura do corpo, giro da grade). */
    public static void grid(ServerLevel level, Vec3 center, float halfW, float halfH, float spin, int cuts, int color) {
        send(level, LcNet.GRID, center, new Vec3(halfW, halfH, spin), color, 16, 1F, cuts);
    }

    public static void wave(ServerLevel level, Vec3 center, Vec3 axis, float radius, int color, int life) {
        send(level, LcNet.WAVE, center, axis, color, life, radius, 0);
    }

    public static void suck(ServerLevel level, Vec3 from, Vec3 to, BlockState state, int life) {
        send(level, LcNet.SUCK, from, to, 0xFFFFFF, life, 1F, Block.getId(state));
    }

    public static void flash(ServerLevel level, Vec3 center, float radius, int color, int life) {
        send(level, LcNet.FLASH, center, Vec3.ZERO, color, life, radius, 0);
    }

    public static void ripple(ServerLevel level, Vec3 point, Vec3 normal, float radius, int color) {
        send(level, LcNet.RIPPLE, point, normal, color, 14, radius, 0);
    }

    public static ParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F), size);
    }

    /** Partículas forçadas (aparecem até 512 blocos, para efeitos grandes vistos de longe). */
    public static void far(ServerLevel level, ParticleOptions p, double x, double y, double z, int count,
                           double dx, double dy, double dz, double speed) {
        for (ServerPlayer sp : level.players()) {
            if (sp.distanceToSqr(x, y, z) > RANGE * RANGE) continue;
            level.sendParticles(sp, p, true, x, y, z, count, dx, dy, dz, speed);
        }
    }

    public static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    private LcFx() {}
}
