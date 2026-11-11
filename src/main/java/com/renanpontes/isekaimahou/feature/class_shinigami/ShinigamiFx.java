package com.renanpontes.isekaimahou.feature.class_shinigami;

import org.joml.Vector3f;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Visual do Reiatsu (servidor): pó vermelho/azul, arcos de corte, pilares de liberação, kanji de tinta, pétalas, gelo e
 * chamas por arquétipo. Tudo com quantidade limitada (as partículas só vão para quem está perto).
 */
public final class ShinigamiFx {
    public static final DustParticleOptions REIATSU = dust(Shinigami.RGB, 1.1F);
    public static final DustParticleOptions REIATSU_SMALL = dust(Shinigami.RGB, 0.7F);
    public static final DustParticleOptions SPIRIT = dust(Shinigami.REIATSU_RGB, 1.0F);
    public static final DustParticleOptions WHITE = dust(0xF4F6FF, 0.9F);
    public static final DustParticleOptions BLACK = dust(0x14121A, 1.2F);
    public static final DustParticleOptions INK = dust(Shinigami.INK_RGB, 1.6F);
    public static final DustParticleOptions KIDO_RED = dust(0xFF3A22, 1.3F);
    public static final DustParticleOptions KIDO_GOLD = dust(0xFFE27A, 1.0F);

    public static DustParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 0xFF) / 255F, ((rgb >> 8) & 0xFF) / 255F, (rgb & 0xFF) / 255F), size);
    }

    public static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    /** Linha de partículas entre dois pontos. */
    public static void line(ServerLevel level, Vec3 from, Vec3 to, ParticleOptions p, double step) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 1.0E-3) return;
        int n = Math.min(64, (int) Math.ceil(len / step));
        for (int i = 0; i <= n; i++) {
            Vec3 at = from.add(d.scale(i / (double) n));
            level.sendParticles(p, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
    }

    /** Anel horizontal (gira com {@code phase}). */
    public static void ring(ServerLevel level, Vec3 c, double radius, int points, float phase, ParticleOptions p) {
        for (int i = 0; i < points; i++) {
            double a = phase + i * Mth.TWO_PI / points;
            level.sendParticles(p, c.x + Math.cos(a) * radius, c.y, c.z + Math.sin(a) * radius, 1, 0, 0, 0, 0);
        }
    }

    /** Arco de corte horizontal na frente de {@code from} (o rastro da lâmina). */
    public static void slashArc(ServerLevel level, Vec3 center, Vec3 dir, double radius, double halfAngleDeg, float tilt, ParticleOptions p) {
        Vec3 flat = new Vec3(dir.x, 0, dir.z);
        if (flat.lengthSqr() < 1.0E-4) flat = new Vec3(0, 0, 1);
        flat = flat.normalize();
        double base = Math.atan2(flat.z, flat.x);
        double half = Math.toRadians(halfAngleDeg);
        int n = Math.max(8, (int) (radius * halfAngleDeg / 9));
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n;
            double a = base - half + 2 * half * t;
            double y = (t - 0.5) * tilt;
            Vec3 at = center.add(Math.cos(a) * radius, y, Math.sin(a) * radius);
            level.sendParticles(p, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            if (i % 3 == 0) level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
    }

    /** Pilar de Reiatsu subindo (liberação, rank, Bankai). */
    public static void pillar(ServerLevel level, Vec3 at, double height, int count, ParticleOptions p) {
        for (int i = 0; i < count; i++) {
            double t = level.random.nextDouble();
            double a = level.random.nextDouble() * Mth.TWO_PI;
            double r = 0.3 + 0.5 * level.random.nextDouble();
            level.sendParticles(p, at.x + Math.cos(a) * r, at.y + t * height, at.z + Math.sin(a) * r, 1, 0, 0.05, 0, 0.02);
        }
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + height * 0.5, at.z, Math.max(2, count / 8), 0.3, height * 0.3, 0.3, 0.04);
    }

    /** Pó da assinatura de um arquétipo em volta de um ponto. */
    public static void signature(ServerLevel level, Archetype a, Vec3 at, int count, double spread) {
        switch (a) {
            case WEIGHT -> {
                level.sendParticles(dust(0x6A7286, 1.4F), at.x, at.y, at.z, count, spread, spread * 0.4, spread, 0.0);
                level.sendParticles(ParticleTypes.FALLING_OBSIDIAN_TEAR, at.x, at.y + 0.6, at.z, Math.max(1, count / 4), spread, 0.2, spread, 0.0);
            }
            case PETALS -> {
                level.sendParticles(ParticleTypes.CHERRY_LEAVES, at.x, at.y, at.z, count, spread, spread, spread, 0.02);
                level.sendParticles(dust(0xFF8FC0, 0.8F), at.x, at.y, at.z, Math.max(1, count / 2), spread, spread, spread, 0.0);
            }
            case FROST -> {
                level.sendParticles(ParticleTypes.SNOWFLAKE, at.x, at.y, at.z, count, spread, spread, spread, 0.02);
                level.sendParticles(dust(0xA8ECFF, 1.0F), at.x, at.y, at.z, Math.max(1, count / 2), spread, spread, spread, 0.0);
            }
            case FLAME -> {
                level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, count, spread, spread, spread, 0.02);
                level.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, Math.max(1, count / 6), spread * 0.5, 0.1, spread * 0.5, 0.0);
            }
            case BRUSH -> {
                level.sendParticles(INK, at.x, at.y, at.z, count, spread, spread, spread, 0.0);
                level.sendParticles(WHITE, at.x, at.y, at.z, Math.max(1, count / 3), spread, spread, spread, 0.0);
            }
        }
    }

    /** Efeito de Shunpo (um "rasgo" de imagem residual entre dois pontos). */
    public static void afterimage(ServerLevel level, Entity who, Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        int n = Math.min(24, (int) Math.ceil(len / 0.5));
        for (int i = 0; i <= n; i++) {
            Vec3 at = from.add(d.scale(i / (double) Math.max(1, n)));
            level.sendParticles(BLACK, at.x, at.y + 0.2 + level.random.nextDouble() * who.getBbHeight(), at.z, 1, 0.08, 0.08, 0.08, 0);
        }
        level.sendParticles(ParticleTypes.CLOUD, from.x, from.y + 0.1, from.z, 6, 0.3, 0.05, 0.3, 0.02);
        level.sendParticles(WHITE, to.x, to.y + who.getBbHeight() * 0.5, to.z, 8, 0.3, 0.5, 0.3, 0.02);
    }

    /** Explosão de Reiatsu (impacto). */
    public static void burst(ServerLevel level, Vec3 at, int count, double spread, ParticleOptions p) {
        level.sendParticles(p, at.x, at.y, at.z, count, spread, spread, spread, 0.02);
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, Math.max(1, count / 3), spread * 0.6, spread * 0.6, spread * 0.6, 0.2);
    }

    private ShinigamiFx() {}
}
