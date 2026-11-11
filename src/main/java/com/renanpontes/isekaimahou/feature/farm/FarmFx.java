package com.renanpontes.isekaimahou.feature.farm;

import org.joml.Vector3f;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** Efeitos visuais/sonoros do módulo (servidor). Tudo com quantidades pequenas: construções grandes não podem pesar. */
public final class FarmFx {
    /** Azul-ciano da Construção Arcana. */
    public static final int BUILD_RGB = 0x7FD4FF;
    /** Dourado das plantas de casa. */
    public static final int BLUEPRINT_RGB = 0xF2C46B;

    public static DustParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F), size);
    }

    /** Brilho rápido num bloco recém-colocado. */
    public static void placed(ServerLevel level, BlockPos pos, int rgb) {
        level.sendParticles(dust(rgb, 0.9F), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.32, 0.32, 0.32, 0);
    }

    /** Linha de partículas de {@code from} até {@code to} (no máx. 24 pontos). */
    public static void beam(ServerLevel level, Vec3 from, Vec3 to, ParticleOptions p, double step) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 0.01) return;
        int n = (int) Math.min(24, Math.ceil(len / step));
        for (int i = 0; i <= n; i++) {
            Vec3 at = from.add(d.scale(i / (double) n));
            level.sendParticles(p, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
    }

    /** Anel horizontal. */
    public static void ring(ServerLevel level, Vec3 c, double r, ParticleOptions p, int points) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            level.sendParticles(p, c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r, 1, 0, 0.02, 0, 0);
        }
    }

    /** Explosão de "obra pronta". */
    public static void celebrate(ServerLevel level, Vec3 c, int rgb) {
        level.sendParticles(ParticleTypes.FIREWORK, c.x, c.y, c.z, 30, 0.6, 0.6, 0.6, 0.12);
        level.sendParticles(dust(rgb, 1.4F), c.x, c.y, c.z, 24, 1.2, 0.8, 1.2, 0);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, c.x, c.y, c.z, 12, 1.5, 0.8, 1.5, 0);
    }

    public static void sound(ServerLevel level, Vec3 at, SoundEvent s, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, s, SoundSource.BLOCKS, volume, pitch);
    }

    private FarmFx() {}
}
