package com.renanpontes.isekaimahou.feature.class_enchanter;

import org.joml.Vector3f;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Visual e som do Encantador (tudo no servidor, com orçamento de partículas). */
public final class EnchFx {
    /** Cor da classe (turquesa) e as de destaque. */
    public static final int TEAL = 0x5FE3C0;
    public static final int WHITE = 0xE8FFFF;
    public static final int GOLD = 0xFFD27A;
    public static final int ANTI = 0xBFE8FF;
    public static final int STEEL = 0x9AA3AE;
    public static final int RED = 0xC8303C;

    public static ParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 0xFF) / 255F, ((rgb >> 8) & 0xFF) / 255F, (rgb & 0xFF) / 255F),
                Mth.clamp(size, 0.2F, 4.0F));
    }

    public static Vec3 center(Entity e) {
        return e.position().add(0, e.getBbHeight() * 0.5, 0);
    }

    public static void sound(ServerLevel level, Vec3 pos, SoundEvent s, float vol, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, s, SoundSource.PLAYERS, vol, pitch);
    }

    public static void sound(ServerLevel level, Vec3 pos, Holder<SoundEvent> s, float vol, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, s, SoundSource.PLAYERS, vol, pitch);
    }

    /** Anel horizontal. */
    public static void ring(ServerLevel level, Vec3 c, double r, int rgb, int points, float size) {
        ParticleOptions p = dust(rgb, size);
        int n = Mth.clamp(points, 6, 96);
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            level.sendParticles(p, c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
    }

    /**
     * Círculo de inscrição no chão: dois anéis, um hexagrama entre eles e runas (encantamento) subindo. {@code turn} gira
     * o desenho (passe a idade para ele girar).
     */
    public static void sigil(ServerLevel level, Vec3 c, double r, int rgb, int accent, float turn) {
        ring(level, c, r, rgb, (int) (r * 12), 0.9F);
        ring(level, c, r * 0.82, accent, (int) (r * 9), 0.6F);
        ParticleOptions p = dust(accent, 0.7F);
        for (int tri = 0; tri < 2; tri++) {
            for (int k = 0; k < 3; k++) {
                double a0 = turn + tri * Math.PI / 3 + k * Math.PI * 2 / 3;
                double a1 = a0 + Math.PI * 2 / 3;
                Vec3 p0 = new Vec3(c.x + Math.cos(a0) * r * 0.82, c.y, c.z + Math.sin(a0) * r * 0.82);
                Vec3 p1 = new Vec3(c.x + Math.cos(a1) * r * 0.82, c.y, c.z + Math.sin(a1) * r * 0.82);
                int steps = Math.max(4, (int) (p0.distanceTo(p1) * 2.5));
                for (int s = 0; s <= steps; s++) {
                    Vec3 q = p0.lerp(p1, s / (double) steps);
                    level.sendParticles(p, q.x, q.y, q.z, 1, 0, 0, 0, 0);
                }
            }
        }
        level.sendParticles(ParticleTypes.ENCHANT, c.x, c.y + 0.6, c.z, (int) Math.min(40, r * 4), r * 0.4, 0.3, r * 0.4, 0.6);
    }

    /** Espiral que sobe em volta da entidade (aplicação de um encantamento). */
    public static void spiral(ServerLevel level, LivingEntity e, int rgb, int accent) {
        double h = Math.max(1.0, e.getBbHeight());
        double r = Math.max(0.55, e.getBbWidth() * 0.8);
        ParticleOptions a = dust(rgb, 0.9F), b = dust(accent, 0.7F);
        for (int i = 0; i < 24; i++) {
            double ang = i * 0.55;
            double y = e.getY() + h * i / 24.0;
            level.sendParticles(i % 3 == 0 ? b : a, e.getX() + Math.cos(ang) * r, y, e.getZ() + Math.sin(ang) * r, 1, 0, 0, 0, 0);
        }
        level.sendParticles(ParticleTypes.ENCHANT, e.getX(), e.getY() + h * 0.6, e.getZ(), 14, r * 0.6, h * 0.4, r * 0.6, 0.9);
    }

    /** Feixe de pontos entre dois lugares (Elo de Encantamento). */
    public static void beam(ServerLevel level, Vec3 from, Vec3 to, int rgb, float size) {
        double d = from.distanceTo(to);
        if (d < 0.1) return;
        int steps = Mth.clamp((int) (d * 3), 4, 90);
        ParticleOptions p = dust(rgb, size);
        for (int i = 0; i <= steps; i++) {
            Vec3 q = from.lerp(to, i / (double) steps);
            double wob = Math.sin(i * 0.7) * 0.06;
            level.sendParticles(p, q.x + wob, q.y + wob, q.z, 1, 0, 0, 0, 0);
        }
    }

    /** "Estilhaço" hexagonal da Anti-Magia: a magia bate e se desfaz. */
    public static void shatter(ServerLevel level, Vec3 pos, double radius) {
        ParticleOptions p = dust(ANTI, 0.8F);
        for (int i = 0; i < 6; i++) {
            double a0 = Math.PI / 3 * i, a1 = Math.PI / 3 * (i + 1);
            for (int s = 0; s <= 3; s++) {
                double t = s / 3.0;
                double a = a0 + (a1 - a0) * t;
                double rr = radius * (0.88 + 0.12 * Math.cos((a - a0) * 3));
                level.sendParticles(p, pos.x + Math.cos(a) * rr, pos.y + Math.sin(a) * rr * 0.9, pos.z + Math.sin(a) * rr * 0.3, 1, 0, 0, 0, 0);
            }
        }
        level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 6, radius * 0.4, radius * 0.4, radius * 0.4, 0.06);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y, pos.z, 4, radius * 0.3, radius * 0.3, radius * 0.3, 0.2);
    }

    /** Partículas que só um jogador vê (os "sentidos" da Restrição Celestial). */
    public static void private_(ServerPlayer viewer, ParticleOptions p, Vec3 pos, int count, double spread) {
        viewer.serverLevel().sendParticles(viewer, p, true, pos.x, pos.y, pos.z, count, spread, spread, spread, 0);
    }

    private EnchFx() {}
}
