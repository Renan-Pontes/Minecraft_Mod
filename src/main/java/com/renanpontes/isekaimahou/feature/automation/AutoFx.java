package com.renanpontes.isekaimahou.feature.automation;

import org.joml.Vector3f;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Efeitos visuais/sonoros do servidor usados pelos blocos e glifos da Automação. */
public final class AutoFx {
    /** Violeta arcano das máquinas. */
    public static final int MANA_RGB = 0x9B6BFF;

    public static ParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F), size);
    }

    /** Linha de partículas (mana fluindo) de {@code from} até {@code to}. */
    public static void line(ServerLevel level, Vec3 from, Vec3 to, ParticleOptions particle, double step) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        int n = Math.max(2, (int) Math.min(64, len / step));
        for (int i = 0; i <= n; i++) {
            Vec3 p = from.add(d.scale(i / (double) n));
            level.sendParticles(particle, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
        }
    }

    /** Anel horizontal de partículas. */
    public static void ring(ServerLevel level, Vec3 center, double radius, ParticleOptions particle, int points) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            level.sendParticles(particle, center.x + Math.cos(a) * radius, center.y, center.z + Math.sin(a) * radius, 1, 0.02, 0.02, 0.02, 0);
        }
    }

    public static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.BLOCKS, volume, pitch);
    }

    /** Mensagem na barra de ação (só para jogadores de verdade). */
    public static void actionbar(Entity who, Component msg) {
        if (who instanceof ServerPlayer sp && !sp.isFakePlayer()) sp.displayClientMessage(msg, true);
    }

    private AutoFx() {}
}
