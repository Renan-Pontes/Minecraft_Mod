package com.renanpontes.isekaimahou.feature.light_shadow;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Efeitos visuais e sonoros (lado servidor) próprios de Luz e Sombra. */
public final class LsFx {
    public static final int GOLD = 0xFFE27A;
    public static final int WHITE_GOLD = 0xFFF6D0;
    public static final int VIOLET = 0x8A4FC8;
    public static final int DEEP_VIOLET = 0x3B1D5A;
    public static final int BLOOD = 0x9B1B30;
    public static final int CHAIN_GREY = 0x55556A;

    public static Vector3f vec(int rgb) {
        return new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F);
    }

    public static ParticleOptions dust(int rgb, float size) {
        // v1.3: brilho fx aditivo (mesmo tamanho do pó do vanilla; respeita o preset gráfico no cliente)
        return com.renanpontes.isekaimahou.core.ElementFx.glow(rgb, size);
    }

    public static DustColorTransitionOptions fade(int from, int to, float size) {
        return new DustColorTransitionOptions(vec(from), vec(to), size);
    }

    public static Vec3 mid(Entity e) {
        return e.position().add(0, e.getBbHeight() * 0.5, 0);
    }

    /** Linha de partículas entre dois pontos ({@code density} pontos por bloco). */
    public static void line(ServerLevel level, Vec3 from, Vec3 to, ParticleOptions p, double density) {
        Vec3 d = to.subtract(from);
        int steps = Math.max(2, (int) (d.length() * density));
        for (int i = 0; i <= steps; i++) {
            Vec3 q = from.add(d.scale(i / (double) steps));
            level.sendParticles(p, q.x, q.y, q.z, 1, 0, 0, 0, 0);
        }
    }

    /** Raio de luz descendo do céu sobre o alvo (Raio Sagrado, Exorcizar, Luz Solar). */
    public static void skyRay(ServerLevel level, Vec3 base, double height, float width, int rgb) {
        int steps = (int) (height * 3);
        for (int i = 0; i <= steps; i++) {
            double y = base.y + height * i / steps;
            level.sendParticles(dust(rgb, 1.6F * width), base.x, y, base.z, 2, 0.12 * width, 0.05, 0.12 * width, 0);
            if (i % 3 == 0) level.sendParticles(ParticleTypes.END_ROD, base.x, y, base.z, 1, 0.08 * width, 0.05, 0.08 * width, 0.01);
        }
        level.sendParticles(ParticleTypes.FLASH, base.x, base.y + 0.2, base.z, 1, 0, 0, 0, 0);
        ring(level, base.add(0, 0.05, 0), 0.9 * width, dust(rgb, 1.0F), 14);
        // v1.1: coluna de luz de verdade + onda no chão
        ElementFx.pillar(level, base, rgb, 0.45F * Math.max(0.5F, width), (float) height, 16);
        ElementFx.shockwave(level, base.add(0, 0.08, 0), rgb, 1.4F * Math.max(0.6F, width), 10, null);
    }

    /** Anel horizontal. */
    public static void ring(ServerLevel level, Vec3 c, double radius, ParticleOptions p, int points) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            level.sendParticles(p, c.x + Math.cos(a) * radius, c.y, c.z + Math.sin(a) * radius, 1, 0, 0, 0, 0);
        }
    }

    /** Espiral subindo em volta de uma entidade. */
    public static void spiral(ServerLevel level, Entity e, ParticleOptions p, int points, double radius) {
        double h = e.getBbHeight() + 0.3;
        for (int i = 0; i < points; i++) {
            double t = i / (double) points;
            double a = t * Math.PI * 4 + level.getGameTime() * 0.3;
            level.sendParticles(p, e.getX() + Math.cos(a) * radius, e.getY() + h * t, e.getZ() + Math.sin(a) * radius, 1, 0, 0, 0, 0);
        }
    }

    /** Nuvem de fumaça sombria (sumir/aparecer). */
    public static void shadowPuff(ServerLevel level, Vec3 pos, double h) {
        level.sendParticles(Vfx.largeSmoke(), pos.x, pos.y + h * 0.5, pos.z, 18, 0.3, h * 0.35, 0.3, 0.02);
        level.sendParticles(Vfx.ink(), pos.x, pos.y + h * 0.5, pos.z, 12, 0.3, h * 0.3, 0.3, 0.05);
        level.sendParticles(dust(VIOLET, 1.4F), pos.x, pos.y + h * 0.5, pos.z, 16, 0.35, h * 0.35, 0.35, 0);
        level.sendParticles(ElementFx.style(Element.SHADOW, 1.4F), pos.x, pos.y + h * 0.4, pos.z, 10, 0.35, h * 0.3, 0.35, 0.03);
    }

    /** Explosão de luz sagrada. */
    public static void holyBurst(ServerLevel level, Vec3 pos, int count, double spread) {
        level.sendParticles(dust(GOLD, 1.5F), pos.x, pos.y, pos.z, count, spread, spread, spread, 0);
        level.sendParticles(Vfx.endRod(), pos.x, pos.y, pos.z, Math.max(1, count / 2), spread, spread, spread, 0.06);
        level.sendParticles(ElementFx.style(Element.LIGHT, 1.2F), pos.x, pos.y, pos.z, Math.max(1, count / 3), spread, spread, spread, 0.08);
        if (count >= 16) ElementFx.flash(level, pos, GOLD, (float) Math.min(3.0, 0.8 + spread));
    }

    public static void sound(ServerLevel level, Vec3 pos, SoundEvent s, float vol, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, s, SoundSource.PLAYERS, vol, pitch);
    }

    private LsFx() {}
}
