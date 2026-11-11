package com.renanpontes.isekaimahou.feature.creatures_e;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.core.MahouConfig;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Efeitos visuais/sonoros das criaturas das dimensoes (servidor manda as particulas; cliente so desenha). */
public final class CeFx {

    /** Po colorido com cor RGB. */
    public static DustParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F), size);
    }

    public static ParticleOptions block(BlockState state) {
        return new BlockParticleOption(ParticleTypes.BLOCK, state);
    }

    public static void burst(ServerLevel level, Vec3 pos, ParticleOptions p, int count, double spread, double speed) {
        level.sendParticles(p, pos.x, pos.y, pos.z, count, spread, spread, spread, speed);
    }

    /** Anel horizontal de particulas; {@code up} empurra as particulas para cima (ondas de choque). */
    public static void ring(ServerLevel level, Vec3 c, double radius, ParticleOptions p, int points, double up) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            level.sendParticles(p, c.x + Math.cos(a) * radius, c.y, c.z + Math.sin(a) * radius, 0, 0, up, 0, 1.0);
        }
    }

    /** Anel que se expande para fora (velocidade radial). */
    public static void shockwave(ServerLevel level, Vec3 c, ParticleOptions p, int points, double speed) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            level.sendParticles(p, c.x, c.y, c.z, 0, Math.cos(a), 0.05, Math.sin(a), speed);
        }
    }

    /** Linha de particulas entre dois pontos. */
    public static void line(ServerLevel level, Vec3 from, Vec3 to, ParticleOptions p, double perBlock) {
        Vec3 d = to.subtract(from);
        int steps = Math.max(2, (int) (d.length() * perBlock));
        for (int i = 0; i <= steps; i++) {
            Vec3 q = from.add(d.scale(i / (double) steps));
            level.sendParticles(p, q.x, q.y, q.z, 1, 0.02, 0.02, 0.02, 0);
        }
    }

    /** Espiral vertical (redemoinhos, colunas de luz). */
    public static void spiral(ServerLevel level, Vec3 base, double radius, double height, ParticleOptions p, int points, double phase) {
        for (int i = 0; i < points; i++) {
            double t = i / (double) points;
            double a = phase + t * Math.PI * 4;
            level.sendParticles(p, base.x + Math.cos(a) * radius, base.y + t * height, base.z + Math.sin(a) * radius, 1, 0, 0, 0, 0);
        }
    }

    /** Linha de particulas so no cliente (feixes desenhados a cada tick a partir de dados sincronizados). */
    public static void clientLine(Level level, Vec3 from, Vec3 to, ParticleOptions p, double perBlock, double jitter) {
        Vec3 d = to.subtract(from);
        int steps = Math.max(2, (int) (d.length() * perBlock));
        for (int i = 0; i <= steps; i++) {
            Vec3 q = from.add(d.scale(i / (double) steps));
            level.addParticle(p, q.x + (level.random.nextDouble() - 0.5) * jitter, q.y + (level.random.nextDouble() - 0.5) * jitter,
                    q.z + (level.random.nextDouble() - 0.5) * jitter, 0, 0, 0);
        }
    }

    /** Config de cliente "particulas reduzidas" (falso se a config ainda nao carregou, ex.: servidor). */
    public static boolean reducedParticles() {
        try {
            return MahouConfig.REDUCED_PARTICLES.get();
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static void sound(Level level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.HOSTILE, volume, pitch);
    }

    private CeFx() {}
}
