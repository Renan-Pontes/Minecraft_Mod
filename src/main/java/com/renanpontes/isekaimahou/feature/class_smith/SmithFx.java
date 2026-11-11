package com.renanpontes.isekaimahou.feature.class_smith;

import org.joml.Vector3f;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Partículas e sons do Ferreiro (servidor): fagulhas de forja, linhas, anéis no chão e poeira do terreno. */
public final class SmithFx {
    public static final int EMBER = 0xFF8A2A;
    public static final int MOLTEN = 0xFFB040;
    public static final int STEEL = 0xD8DEE8;
    public static final int STAR = 0x8FA8FF;

    /** Fagulha (pó colorido pequeno). */
    public static ParticleOptions spark(int rgb) {
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F), 0.7F);
    }

    public static ParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F), size);
    }

    public static void sound(ServerLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    public static void sound(ServerLevel level, Vec3 pos, net.minecraft.core.Holder<SoundEvent> sound, float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    /** Fagulhas de martelada (chuva curta de pontos quentes + 1 ou 2 gotas de lava). */
    public static void forgeSparks(ServerLevel level, Vec3 at, int rgb, int count) {
        level.sendParticles(spark(rgb), at.x, at.y, at.z, count, 0.25, 0.2, 0.25, 0.12);
        level.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, Math.max(1, count / 10), 0.1, 0.1, 0.1, 0);
        level.sendParticles(ParticleTypes.SMALL_FLAME, at.x, at.y, at.z, Math.max(2, count / 4), 0.15, 0.15, 0.15, 0.02);
    }

    /** Linha de partículas entre dois pontos (rastro de lâmina, estocada). */
    public static void line(ServerLevel level, Vec3 from, Vec3 to, ParticleOptions p, double step) {
        Vec3 d = to.subtract(from);
        int n = Math.max(2, Math.min(64, (int) (d.length() / step)));
        for (int i = 0; i <= n; i++) {
            Vec3 q = from.add(d.scale(i / (double) n));
            level.sendParticles(p, q.x, q.y, q.z, 1, 0.02, 0.02, 0.02, 0);
        }
    }

    /** Anel de partículas no plano horizontal. */
    public static void ring(ServerLevel level, Vec3 center, double radius, ParticleOptions p, int points) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            level.sendParticles(p, center.x + Math.cos(a) * radius, center.y, center.z + Math.sin(a) * radius, 1, 0, 0.02, 0, 0);
        }
    }

    /** Terra que pula do chão no ponto (usa o bloco de baixo; nada se for ar). */
    public static void groundBurst(ServerLevel level, Vec3 at, int count) {
        BlockPos below = BlockPos.containing(at.x, at.y - 0.2, at.z);
        BlockState st = level.getBlockState(below);
        if (st.isAir()) return;
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), at.x, at.y + 0.1, at.z, count, 0.35, 0.05, 0.35, 0.15);
    }

    /** Centro do corpo de uma entidade. */
    public static Vec3 center(Entity e) {
        return e.position().add(0, e.getBbHeight() * 0.5, 0);
    }

    /** Chão logo abaixo de um ponto (até {@code depth} blocos), ou o próprio ponto. */
    public static Vec3 ground(ServerLevel level, Vec3 at, int depth) {
        BlockPos p = BlockPos.containing(at);
        for (int i = 0; i <= depth; i++) {
            BlockPos q = p.below(i);
            if (!level.getBlockState(q).getCollisionShape(level, q).isEmpty()) {
                return new Vec3(at.x, q.getY() + level.getBlockState(q).getCollisionShape(level, q).max(net.minecraft.core.Direction.Axis.Y), at.z);
            }
        }
        return at;
    }

    private SmithFx() {}
}
