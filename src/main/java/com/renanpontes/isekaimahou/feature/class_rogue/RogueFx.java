package com.renanpontes.isekaimahou.feature.class_rogue;

import org.joml.Vector3f;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Efeitos visuais e sonoros do Ladino (só servidor; partículas vanilla, orçamento curto por chamada). */
public final class RogueFx {
    /** Roxo do Ladino. */
    public static final int PURPLE = 0x8E5BD6;
    public static final int SHADOW = 0x3A2458;
    public static final int GOLD = 0xFFD86B;
    public static final int BLOOD = 0xC0182E;
    public static final int MANA = 0x6FB8FF;
    public static final int CARD = 0xF4F0FF;

    public static ParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 0xFF) / 255F, ((rgb >> 8) & 0xFF) / 255F, (rgb & 0xFF) / 255F), size);
    }

    public static Vec3 center(Entity e) {
        return e.position().add(0, e.getBbHeight() * 0.55, 0);
    }

    public static void sound(ServerLevel level, Vec3 at, SoundEvent s, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, volume, pitch);
    }

    /** Linha de partículas de {@code a} até {@code b} (no máx. 40 pontos). */
    public static void line(ServerLevel level, Vec3 a, Vec3 b, ParticleOptions p, double step) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 1.0E-3) return;
        int n = Math.min(40, Math.max(2, (int) (len / step)));
        for (int i = 0; i <= n; i++) {
            Vec3 q = a.add(d.scale(i / (double) n));
            level.sendParticles(p, q.x, q.y, q.z, 1, 0, 0, 0, 0);
        }
    }

    /** Fumaça de sumir/aparecer (passo de sombra). */
    public static void vanish(ServerLevel level, Vec3 at) {
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 1.0, at.z, 12, 0.3, 0.5, 0.3, 0.02);
        level.sendParticles(dust(SHADOW, 1.6F), at.x, at.y + 1.0, at.z, 16, 0.35, 0.6, 0.35, 0);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 1.0, at.z, 10, 0.25, 0.5, 0.25, 0.04);
    }

    /** Corte em arco (golpe pelas costas). */
    public static void slash(ServerLevel level, LivingEntity target, int rgb) {
        Vec3 c = center(target);
        float yaw = target.getYRot() * Mth.DEG_TO_RAD;
        for (int i = 0; i < 9; i++) {
            double t = -1 + i / 4.0;
            double x = c.x + Math.cos(yaw) * t * 0.6;
            double z = c.z + Math.sin(yaw) * t * 0.6;
            level.sendParticles(dust(rgb, 1.1F), x, c.y + 0.35 - Math.abs(t) * 0.3, z, 1, 0, 0, 0, 0);
        }
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 1, 0, 0, 0, 0);
    }

    /** Estouro de crítico (pequeno). */
    public static void crit(ServerLevel level, Entity target, boolean vital) {
        Vec3 c = center(target);
        level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, vital ? 22 : 12, 0.3, 0.35, 0.3, 0.3);
        if (vital) level.sendParticles(dust(BLOOD, 1.3F), c.x, c.y + 0.2, c.z, 10, 0.25, 0.25, 0.25, 0);
        else level.sendParticles(ParticleTypes.ENCHANTED_HIT, c.x, c.y, c.z, 6, 0.25, 0.3, 0.25, 0.2);
    }

    /** Cartão de visita do Ladrão Fantasma: um losango de luz branca girando. */
    public static void callingCard(ServerLevel level, Vec3 at) {
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8;
            double r = (i % 2 == 0) ? 0.9 : 0.5;
            level.sendParticles(dust(CARD, 1.3F), at.x + Math.cos(a) * r, at.y + 1.2 + Math.sin(a) * r, at.z, 1, 0, 0, 0, 0);
        }
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1.2, at.z, 14, 0.4, 0.5, 0.4, 0.05);
        level.sendParticles(ParticleTypes.FIREWORK, at.x, at.y + 1.2, at.z, 10, 0.2, 0.2, 0.2, 0.08);
    }

    /** Mensagem na barra de ação. */
    public static void actionbar(LivingEntity e, Component msg, int rgb) {
        if (e instanceof ServerPlayer sp) sp.displayClientMessage(msg.copy().withColor(rgb), true);
    }

    private RogueFx() {}
}
