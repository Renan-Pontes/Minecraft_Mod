package com.renanpontes.isekaimahou.feature.legends_k;

import com.renanpontes.isekaimahou.core.ElementFx;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Visuais do legends_k montados com as partículas do núcleo ({@link ElementFx#glow}: o cliente já respeita o Preset
 * Gráfico). Tudo com teto de pontos (no máximo ~40 por chamada) para não pesar.
 */
public final class LkFx {
    /** Azul do Kamui / da dimensão de bolso. */
    public static final int KAMUI = 0x3A5BFF;
    /** Azul-claro do D4C (entre dimensões). */
    public static final int D4C = 0x8FD0FF;
    /** Ouro do Ponto Zero (Réquiem). */
    public static final int REQUIEM = 0xFFD24A;
    /** Laranja-dourado do Shun Shun Rikka (Orihime). */
    public static final int RIKKA = 0xFFB347;
    /** Vermelho escuro da Pressão Espiritual. */
    public static final int REIATSU = 0xC0302A;
    /** Ciano dos quadros da Projeção (Naoya). */
    public static final int FRAME = 0x9FF6FF;
    /** Branco-lilás dos vetores (Accelerator). */
    public static final int VECTOR = 0xE8E0FF;
    /** Preto-arroxeado das sombras (Dez Sombras, Possessão). */
    public static final int SHADOW = 0x3A1A5A;
    /** Ouro-velho da roda de Mahoraga. */
    public static final int WHEEL = 0xD8B860;
    /** Azul-água da Calmaria. */
    public static final int CALM = 0x5FB8FF;
    /** Rosa-lilás do Retorno pela Morte. */
    public static final int RBD = 0xB070FF;
    /** Rosa das flores (Frieren). */
    public static final int FLOWER = 0xFFB0D8;
    /** Roxo-ouro do Aschente. */
    public static final int ASCHENTE = 0xE0A0FF;
    /** Cor do vapor do Titã. */
    public static final int STEAM = 0xE8EEF2;

    /** Ponto brilhante. */
    public static void dot(ServerLevel l, Vec3 p, int rgb, float size) {
        ElementFx.sendFar(l, ElementFx.glow(rgb, size), p.x, p.y, p.z, 1, 0, 0, 0, 0);
    }

    /** Linha reta de pontos (máx. 40). */
    public static void line(ServerLevel l, Vec3 a, Vec3 b, int rgb, float size) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 0.05) return;
        int n = (int) Math.min(40, Math.ceil(len / 0.35));
        for (int i = 0; i <= n; i++) dot(l, a.add(d.scale(i / (double) n)), rgb, size);
    }

    /** Anel horizontal (máx. 40 pontos). */
    public static void ring(ServerLevel l, Vec3 c, double radius, int rgb, float size, int points) {
        int n = Math.min(40, Math.max(6, points));
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            dot(l, c.add(Math.cos(a) * radius, 0, Math.sin(a) * radius), rgb, size);
        }
    }

    /** Espiral subindo/encolhendo (redemoinho do Kamui). */
    public static void swirl(ServerLevel l, Vec3 c, double radius, int rgb, int points) {
        int n = Math.min(36, points);
        for (int i = 0; i < n; i++) {
            double t = i / (double) n;
            double a = t * Math.PI * 5;
            double r = radius * (1 - t * 0.85);
            dot(l, c.add(Math.cos(a) * r, (t - 0.5) * 0.6, Math.sin(a) * r), rgb, 0.35F);
        }
    }

    /**
     * Triângulo do Santen Kesshun virado para {@code facing} (horizontal), centrado em {@code c}. Três vértices brilhantes
     * e as arestas.
     */
    public static void triangle(ServerLevel l, Vec3 c, Vec3 facing, double size, int rgb) {
        Vec3 f = new Vec3(facing.x, 0, facing.z);
        if (f.lengthSqr() < 1e-4) f = new Vec3(0, 0, 1);
        f = f.normalize();
        Vec3 right = new Vec3(-f.z, 0, f.x);
        Vec3 top = c.add(0, size, 0);
        Vec3 bl = c.add(right.scale(-size * 0.9)).add(0, -size * 0.5, 0);
        Vec3 br = c.add(right.scale(size * 0.9)).add(0, -size * 0.5, 0);
        line(l, top, bl, rgb, 0.3F);
        line(l, bl, br, rgb, 0.3F);
        line(l, br, top, rgb, 0.3F);
        dot(l, top, 0xFFFFFF, 0.6F);
        dot(l, bl, 0xFFFFFF, 0.6F);
        dot(l, br, 0xFFFFFF, 0.6F);
    }

    /**
     * Painel de "quadro" da Projeção: um retângulo em volta da criatura no plano que encara {@code viewer} (o "fotograma"
     * em que ela ficou presa).
     */
    public static void framePanel(ServerLevel l, Entity e, Vec3 viewer) {
        Vec3 c = LkUtil.center(e);
        Vec3 f = new Vec3(viewer.x - c.x, 0, viewer.z - c.z);
        if (f.lengthSqr() < 1e-4) f = new Vec3(0, 0, 1);
        f = f.normalize();
        Vec3 right = new Vec3(-f.z, 0, f.x);
        double w = e.getBbWidth() * 0.5 + 0.45;
        double h = e.getBbHeight() * 0.5 + 0.3;
        Vec3 tl = c.add(right.scale(-w)).add(0, h, 0);
        Vec3 tr = c.add(right.scale(w)).add(0, h, 0);
        Vec3 bl = c.add(right.scale(-w)).add(0, -h, 0);
        Vec3 br = c.add(right.scale(w)).add(0, -h, 0);
        line(l, tl, tr, FRAME, 0.28F);
        line(l, tr, br, FRAME, 0.28F);
        line(l, br, bl, FRAME, 0.28F);
        line(l, bl, tl, FRAME, 0.28F);
        // um "brilho de vidro" na diagonal
        line(l, tl.add(right.scale(w * 0.4)), bl.add(right.scale(w * 0.9)).add(0, h * 0.6, 0), 0xFFFFFF, 0.18F);
    }

    /** A roda de Mahoraga (8 raios) sobre a cabeça; {@code turn} gira o desenho. */
    public static void wheel(ServerLevel l, Entity e, int turn) {
        Vec3 c = e.position().add(0, e.getBbHeight() + 0.55, 0);
        double r = 0.55;
        double off = turn * Math.PI / 8;
        ring(l, c, r, WHEEL, 0.25F, 16);
        for (int i = 0; i < 8; i++) {
            double a = off + Math.PI * 2 * i / 8;
            Vec3 tip = c.add(Math.cos(a) * (r + 0.18), 0, Math.sin(a) * (r + 0.18));
            line(l, c, tip, WHEEL, 0.2F);
        }
    }

    private LkFx() {}
}
