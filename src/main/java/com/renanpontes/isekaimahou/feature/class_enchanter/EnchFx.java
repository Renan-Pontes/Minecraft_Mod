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
        // desenhado no cliente (EnchFxClient), respeitando o preset gráfico
        EnchFxNet.at(level, EnchFxNet.RING, c, Vec3.ZERO, (float) r, rgb, -1);
    }

    /**
     * Círculo de inscrição no chão: dois anéis, um hexagrama entre eles e runas (encantamento) subindo. {@code turn} gira
     * o desenho (passe a idade para ele girar).
     */
    public static void sigil(ServerLevel level, Vec3 c, double r, int rgb, int accent, float turn) {
        EnchFxNet.at(level, EnchFxNet.SIGIL, c, Vec3.ZERO, (float) r, rgb, -1, accent & 0xFFFFFF);
    }

    /** Espiral que sobe em volta da entidade (aplicação de um encantamento). */
    public static void spiral(ServerLevel level, LivingEntity e, int rgb, int accent) {
        EnchFxNet.at(level, EnchFxNet.SPIRAL, e.position(), Vec3.ZERO, 1F, rgb, e.getId(), accent & 0xFFFFFF);
    }

    /** Feixe de pontos entre dois lugares (Elo de Encantamento). */
    public static void beam(ServerLevel level, Vec3 from, Vec3 to, int rgb, float size) {
        EnchFxNet.at(level, EnchFxNet.BEAM, from, to, size, rgb, -1);
    }

    /** "Estilhaço" hexagonal da Anti-Magia: a magia bate e se desfaz. */
    public static void shatter(ServerLevel level, Vec3 pos, double radius) {
        EnchFxNet.at(level, EnchFxNet.SHATTER, pos, Vec3.ZERO, (float) radius, ANTI, -1);
    }

    /** Partículas que só um jogador vê (os "sentidos" da Restrição Celestial). */
    public static void private_(ServerPlayer viewer, ParticleOptions p, Vec3 pos, int count, double spread) {
        viewer.serverLevel().sendParticles(viewer, p, true, pos.x, pos.y, pos.z, count, spread, spread, spread, 0);
    }

    private EnchFx() {}
}
