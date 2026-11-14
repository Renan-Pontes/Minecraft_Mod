package com.renanpontes.isekaimahou.feature.cinematic.client;

import com.renanpontes.isekaimahou.glyph.fx.FxOptions;
import com.renanpontes.isekaimahou.glyph.fx.SpellFxParticles;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * v1.1 — Partículas das cutscenes coreografadas, SÓ no cliente de quem assiste (nenhum pacote a mais).
 * Usa as partículas da paleta dos elementos ({@link SpellFxParticles}). Contagens modestas (≤ ~6 por tick no
 * "carregar", ≤ ~40 na explosão) e metade com "Menos partículas".
 */
final class SceneFx {
    private static final RandomSource RNG = RandomSource.create();
    private static final int NO_FOLLOW = -1;

    /** A cada tick da cena (antes da explosão = "carregando"; depois = aura). */
    static void tick(CutsceneDirector.Scene s) {
        Choreo c = s.choreo;
        if (c == null || s.skipping) return;
        Entity f = s.focus;
        if (f == null) return;
        ClientLevel lv = s.level;
        float p = s.age / (float) s.total;
        boolean charging = p < c.burstAt;
        float k = CinematicSettings.particleFactor();
        Vec3 pos = f.position();
        double x = pos.x, y = pos.y, z = pos.z;
        float h = Math.max(0.6F, f.getBbHeight());
        double w = Math.max(0.5, f.getBbWidth());
        int rgb = s.rgb;
        int age = s.age;

        switch (c.look) {
            case DEFAULT, ARCANE -> {
                if (charging) spiralIn(lv, c.look == Choreo.Look.ARCANE ? SpellFxParticles.RUNE : SpellFxParticles.GLOW,
                        rgb, x, y, z, h, w, age, n(3, k));
                else aura(lv, SpellFxParticles.MOTE, rgb, x, y, z, h, w, n(2, k), 0.04);
            }
            case HOLY -> {
                // penas (folhas brancas caindo devagar) e brilhos subindo
                for (int i = 0; i < n(2, k); i++) {
                    double a = RNG.nextDouble() * Mth.TWO_PI, r = 0.6 + RNG.nextDouble() * 1.8;
                    add(lv, SpellFxParticles.LEAF, 0xFFFFFF, 1.6F, 40 + RNG.nextInt(20), NO_FOLLOW, 0,
                            x + Math.cos(a) * r, y + h + 2.5 + RNG.nextDouble() * 2.5, z + Math.sin(a) * r, 0, -0.02, 0);
                }
                if (!charging) aura(lv, SpellFxParticles.MOTE, CinematicDraw.mix(rgb, 0xFFF4C0, 0.5F), x, y, z, h, w, n(2, k), 0.06);
                // olhos acesos em dourado no close
                if (charging) eyes(lv, f, 0xFFF0A0);
            }
            case DARK -> {
                // tentáculos de sombra subindo do chão em volta e os olhos brilhando
                for (int i = 0; i < n(3, k); i++) {
                    double a = age * 0.35 + i * (Mth.TWO_PI / 3) + RNG.nextDouble() * 0.4;
                    double r = (charging ? 1.6 - p / c.burstAt : 1.2) * (0.8 + w);
                    add(lv, SpellFxParticles.WISP, rgb, 1.3F, 24, NO_FOLLOW, 0,
                            x + Math.cos(a) * r, y + 0.1 + RNG.nextDouble() * 0.4, z + Math.sin(a) * r,
                            -Math.cos(a) * 0.03, 0.07 + RNG.nextDouble() * 0.05, -Math.sin(a) * 0.03);
                }
                eyes(lv, f, CinematicDraw.mix(rgb, 0xFF2040, 0.5F));
            }
            case TIME -> {
                // partículas que "voltam" para o ponto (movimento ao contrário)
                for (int i = 0; i < n(3, k); i++) {
                    double a = RNG.nextDouble() * Mth.TWO_PI, r = 1.5 + RNG.nextDouble() * 1.5;
                    add(lv, SpellFxParticles.MOTE, rgb, 1.2F, 18, NO_FOLLOW, SpellFxParticles.MOTION_INWARD,
                            x, y + h * 0.55, z, Math.cos(a) * r, (RNG.nextDouble() - 0.5) * 2.0, Math.sin(a) * r);
                }
            }
            case SPEED -> {
                // riscos prateados passando ao lado (rajadas de vento) e rastro
                for (int i = 0; i < n(3, k); i++) {
                    double a = RNG.nextDouble() * Mth.TWO_PI, r = 0.8 + RNG.nextDouble() * 1.6;
                    double vx = -Math.sin(a) * 0.9, vz = Math.cos(a) * 0.9;
                    add(lv, SpellFxParticles.WIND, 0xE8F0FF, 1.4F, 8, NO_FOLLOW, 0,
                            x + Math.cos(a) * r, y + RNG.nextDouble() * h, z + Math.sin(a) * r, vx, 0, vz);
                }
                if (!charging) aura(lv, SpellFxParticles.SPARK, 0xDDE6F0, x, y, z, h, w, n(2, k), 0.25);
            }
            case FIRE, BLOOD -> {
                int col = c.look == Choreo.Look.BLOOD ? CinematicDraw.mix(rgb, 0x8A0010, 0.3F) : rgb;
                int count = charging ? n(3, k) : n(5, k);
                for (int i = 0; i < count; i++) {
                    double a = RNG.nextDouble() * Mth.TWO_PI, r = (0.3 + RNG.nextDouble() * 0.7) * (w + 0.4);
                    add(lv, SpellFxParticles.EMBER, col, charging ? 1.2F : 1.7F, 0, NO_FOLLOW, 0,
                            x + Math.cos(a) * r, y + RNG.nextDouble() * h * 0.8, z + Math.sin(a) * r,
                            Math.cos(a) * 0.02, 0.08 + RNG.nextDouble() * 0.08, Math.sin(a) * 0.02);
                }
                if (c.look == Choreo.Look.BLOOD && !charging && RNG.nextInt(2) == 0) {
                    add(lv, SpellFxParticles.DROPLET, 0x9A0010, 1.3F, 0, NO_FOLLOW, 0,
                            x + (RNG.nextDouble() - 0.5) * 1.5, y + h, z + (RNG.nextDouble() - 0.5) * 1.5, 0, 0.05, 0);
                }
                if (charging && c.look == Choreo.Look.BLOOD) eyes(lv, f, 0xFF1020);
            }
            case ICE -> {
                if (charging) spiralIn(lv, SpellFxParticles.FROST, rgb, x, y, z, h, w, age, n(3, k));
                else aura(lv, SpellFxParticles.FROST, rgb, x, y, z, h, w, n(3, k), 0.05);
            }
            case STORM -> aura(lv, SpellFxParticles.SPARK, rgb, x, y, z, h, w, charging ? n(2, k) : n(4, k), 0.3);
            case BEAST -> {
                // poeira no chão e o ar tremendo
                if (age % 2 == 0) for (int i = 0; i < n(2, k); i++) {
                    double a = RNG.nextDouble() * Mth.TWO_PI, r = 1.0 + RNG.nextDouble() * 1.5;
                    add(lv, SpellFxParticles.SMOKE, 0x6A5A48, 1.4F, 0, NO_FOLLOW, 0,
                            x + Math.cos(a) * r, y + 0.1, z + Math.sin(a) * r, Math.cos(a) * 0.05, 0.02, Math.sin(a) * 0.05);
                }
                if (!charging) aura(lv, SpellFxParticles.EMBER, rgb, x, y, z, h, w, n(2, k), 0.06);
                if (charging) eyes(lv, f, CinematicDraw.mix(rgb, 0xFFD040, 0.5F));
            }
            case SPIRIT -> {
                if (charging) spiralIn(lv, SpellFxParticles.GLOW, rgb, x, y, z, h, w, age, n(3, k));
                else {
                    // chamas de reiatsu subindo
                    for (int i = 0; i < n(4, k); i++) {
                        double a = RNG.nextDouble() * Mth.TWO_PI, r = RNG.nextDouble() * (w + 0.6);
                        add(lv, SpellFxParticles.GLOW, rgb, 2.0F, 14, NO_FOLLOW, 0,
                                x + Math.cos(a) * r, y + RNG.nextDouble() * 0.6, z + Math.sin(a) * r, 0, 0.18 + RNG.nextDouble() * 0.12, 0);
                    }
                }
            }
            case WATER -> {
                if (charging) spiralIn(lv, SpellFxParticles.DROPLET, rgb, x, y, z, h, w, age, n(2, k));
                else aura(lv, SpellFxParticles.GLOW, rgb, x, y, z, h, w, n(2, k), 0.05);
            }
            case EARTH -> {
                if (age % 2 == 0) for (int i = 0; i < n(2, k); i++) {
                    double a = RNG.nextDouble() * Mth.TWO_PI, r = 1.0 + RNG.nextDouble() * 1.4;
                    add(lv, SpellFxParticles.STONE, rgb, 1.6F, 0, NO_FOLLOW, 0,
                            x + Math.cos(a) * r, y + 0.1, z + Math.sin(a) * r, 0, 0.25 + RNG.nextDouble() * 0.15, 0);
                }
            }
            case WIND -> spiralIn(lv, SpellFxParticles.WIND, rgb, x, y, z, h, w, age, n(3, k));
            case NATURE -> spiralIn(lv, SpellFxParticles.LEAF, rgb, x, y, z, h, w, age, n(2, k));
            case BITES_THE_DUST -> {
                for (int i = 0; i < n(3, k); i++) {
                    double a = RNG.nextDouble() * Mth.TWO_PI, r = 2.0 + RNG.nextDouble() * 2.0;
                    add(lv, SpellFxParticles.MOTE, 0xFF8FCF, 1.3F, 20, NO_FOLLOW, SpellFxParticles.MOTION_INWARD,
                            x, y + h * 0.5, z, Math.cos(a) * r, (RNG.nextDouble() - 0.3) * 2.0, Math.sin(a) * r);
                }
            }
            default -> { }
        }
    }

    /** Explosão de poder (batida BURST). */
    static void burst(CutsceneDirector.Scene s, float scale) {
        Choreo c = s.choreo;
        Entity f = s.focus;
        if (c == null || f == null) return;
        ClientLevel lv = s.level;
        float k = CinematicSettings.particleFactor() * scale;
        Vec3 pos = f.position();
        double x = pos.x, y = pos.y, z = pos.z;
        float h = Math.max(0.6F, f.getBbHeight());
        float size = Math.max(1F, h / 1.8F);
        int rgb = s.rgb;
        int remaining = Math.max(1, s.total - s.age);
        int tier = SpellFxParticles.flags(3, SpellFxParticles.MODE_NORMAL, 0);

        // comum: clarão no peito + onda de choque no chão
        add(lv, SpellFxParticles.FLASH, CinematicDraw.mix(rgb, 0xFFFFFF, 0.4F), 2.2F * size, 6, NO_FOLLOW, 0, x, y + h * 0.55, z, 0, 0, 0);
        add(lv, SpellFxParticles.SHOCKWAVE, rgb, 5.5F * size, 14, NO_FOLLOW, tier, x, y + 0.1, z, 0, 0, 0);

        DeferredHolder<ParticleType<?>, ParticleType<FxOptions>> spray = SpellFxParticles.GLOW;
        int sprayRgb = rgb;
        double speed = 0.35;
        switch (c.look) {
            case HOLY -> {
                // pilar de luz vindo do céu + auréola girando sobre a cabeça
                add(lv, SpellFxParticles.PILLAR, CinematicDraw.mix(rgb, 0xFFF0B0, 0.5F), 1.1F * size, remaining + 10,
                        Math.round(48 * 16F), 0, x, y, z, 0, 0, 0);
                add(lv, SpellFxParticles.CIRCLE, 0xFFE27A, 0.38F * size, remaining + 20, NO_FOLLOW,
                        SpellFxParticles.flags(2, SpellFxParticles.MODE_NORMAL, 3), x, y + h + 0.32, z, 0, 1, 0);
                spray = SpellFxParticles.MOTE;
                sprayRgb = 0xFFF4C0;
            }
            case DARK -> {
                spray = SpellFxParticles.WISP;
                speed = 0.3;
            }
            case TIME -> {
                // anel de relógio vertical atrás do personagem, girando ao contrário
                Vec3 fwd = Vec3.directionFromRotation(0F, s.faceYaw);
                add(lv, SpellFxParticles.CIRCLE, rgb, 1.5F * size, remaining + 10, NO_FOLLOW,
                        SpellFxParticles.flags(4, SpellFxParticles.MODE_NORMAL, 0) | SpellFxParticles.FLAG_REVERSE,
                        x - fwd.x * 0.7, y + h * 0.6, z - fwd.z * 0.7, fwd.x, 0, fwd.z);
                spray = SpellFxParticles.MOTE;
            }
            case SPEED -> {
                spray = SpellFxParticles.WIND;
                sprayRgb = 0xE8F0FF;
                speed = 0.7;
            }
            case FIRE -> spray = SpellFxParticles.EMBER;
            case BLOOD -> {
                spray = SpellFxParticles.EMBER;
                sprayRgb = CinematicDraw.mix(rgb, 0x8A0010, 0.3F);
            }
            case ICE -> spray = SpellFxParticles.FROST;
            case STORM -> spray = SpellFxParticles.SPARK;
            case BEAST -> {
                spray = SpellFxParticles.STONE;
                sprayRgb = 0x7A6A55;
                add(lv, SpellFxParticles.SHOCKWAVE, 0xB09070, 8F * size, 18, NO_FOLLOW, tier, x, y + 0.15, z, 0, 0, 0);
            }
            case SPIRIT -> {
                // pilar de reiatsu na cor de quem se transforma
                add(lv, SpellFxParticles.PILLAR, rgb, 1.3F * size, remaining + 10, Math.round(26 * 16F), 0, x, y, z, 0, 0, 0);
                spray = SpellFxParticles.GLOW;
            }
            case WATER -> spray = SpellFxParticles.DROPLET;
            case EARTH -> {
                spray = SpellFxParticles.STONE;
                add(lv, SpellFxParticles.SHOCKWAVE, CinematicDraw.darken(rgb, 0.3F), 8F * size, 18, NO_FOLLOW, tier, x, y + 0.15, z, 0, 0, 0);
            }
            case WIND -> {
                spray = SpellFxParticles.WIND;
                speed = 0.6;
            }
            case NATURE -> spray = SpellFxParticles.LEAF;
            case ARCANE -> spray = SpellFxParticles.RUNE;
            case BITES_THE_DUST -> {
                spray = SpellFxParticles.SMOKE;
                sprayRgb = 0xFFB0D8;
                speed = 0.25;
            }
            default -> { }
        }
        int count = Math.max(6, Math.round(28 * k));
        for (int i = 0; i < count; i++) {
            double a = RNG.nextDouble() * Mth.TWO_PI;
            double up = (RNG.nextDouble() - 0.25) * 0.6;
            double v = speed * (0.6 + RNG.nextDouble() * 0.8);
            add(lv, spray, sprayRgb, 1.5F, 0, NO_FOLLOW, 0, x, y + h * (0.3 + RNG.nextDouble() * 0.5), z,
                    Math.cos(a) * v, up * v + 0.05, Math.sin(a) * v);
        }
    }

    /** Raio (batida BOLT): linha quebrada de faíscas do céu até o foco + clarão no chão. */
    static void bolt(CutsceneDirector.Scene s, float scale) {
        Entity f = s.focus;
        if (f == null) return;
        ClientLevel lv = s.level;
        Vec3 pos = f.position();
        double ox = (RNG.nextDouble() - 0.5) * 3.0 * scale, oz = (RNG.nextDouble() - 0.5) * 3.0 * scale;
        double x = pos.x + ox, z = pos.z + oz;
        double top = pos.y + 18, bottom = pos.y;
        int segments = 7;
        double px = x + (RNG.nextDouble() - 0.5) * 3, py = top, pz = z + (RNG.nextDouble() - 0.5) * 3;
        int col = CinematicDraw.mix(s.rgb, 0xFFFFFF, 0.5F);
        for (int i = 1; i <= segments; i++) {
            double t = i / (double) segments;
            double nx = i == segments ? x : x + (RNG.nextDouble() - 0.5) * 2.2;
            double nz = i == segments ? z : z + (RNG.nextDouble() - 0.5) * 2.2;
            double ny = Mth.lerp(t, top, bottom);
            for (int j = 0; j < 3; j++) {
                double u = j / 3.0;
                add(lv, SpellFxParticles.SPARK, col, 1.8F, 5, NO_FOLLOW, 0,
                        Mth.lerp(u, px, nx), Mth.lerp(u, py, ny), Mth.lerp(u, pz, nz), 0, -0.2, 0);
            }
            px = nx;
            py = ny;
            pz = nz;
        }
        add(lv, SpellFxParticles.FLASH, col, 2.5F * scale, 5, NO_FOLLOW, 0, x, bottom + 0.5, z, 0, 0, 0);
    }

    // ------------------------------------------------------------------ peças

    /** Olhos acesos (dois brilhos minúsculos na frente da cabeça, renovados a cada tick). */
    private static void eyes(ClientLevel lv, Entity f, int rgb) {
        Vec3 eye = f.getEyePosition();
        float yaw = f.getYHeadRot() * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);   // frente
        double rx = -fz, rz = fx;                       // lado
        double fwd = 0.27 * Math.max(1F, f.getBbHeight() / 1.8F);
        for (int side = -1; side <= 1; side += 2) {
            add(lv, SpellFxParticles.GLOW, rgb, 0.4F, 3, NO_FOLLOW, 0,
                    eye.x + fx * fwd + rx * 0.11 * side, eye.y + 0.03, eye.z + fz * fwd + rz * 0.11 * side, 0, 0, 0);
        }
    }

    /** Partículas nascendo longe e vindo em espiral até o foco ("juntando energia"). */
    private static void spiralIn(ClientLevel lv, DeferredHolder<ParticleType<?>, ParticleType<FxOptions>> type, int rgb,
                                 double x, double y, double z, float h, double w, int age, int count) {
        for (int i = 0; i < count; i++) {
            double a = age * 0.45 + i * (Mth.TWO_PI / Math.max(1, count));
            double r = 2.2 + w + RNG.nextDouble() * 0.8;
            double dy = (RNG.nextDouble() - 0.5) * h;
            add(lv, type, rgb, 1.2F, 16, NO_FOLLOW, SpellFxParticles.MOTION_INWARD,
                    x, y + h * 0.55, z, Math.cos(a) * r, dy, Math.sin(a) * r);
        }
    }

    /** Aura solta em volta do corpo, subindo devagar. */
    private static void aura(ClientLevel lv, DeferredHolder<ParticleType<?>, ParticleType<FxOptions>> type, int rgb,
                             double x, double y, double z, float h, double w, int count, double speed) {
        for (int i = 0; i < count; i++) {
            double a = RNG.nextDouble() * Mth.TWO_PI, r = (0.4 + RNG.nextDouble() * 0.5) * (w + 0.5);
            add(lv, type, rgb, 1.3F, 0, NO_FOLLOW, 0,
                    x + Math.cos(a) * r, y + RNG.nextDouble() * h, z + Math.sin(a) * r,
                    Math.cos(a) * speed * 0.3, speed, Math.sin(a) * speed * 0.3);
        }
    }

    private static int n(int base, float k) {
        int v = (int) (base * k);
        if (v < base * k && RNG.nextFloat() < base * k - v) v++;
        return v;
    }

    private static void add(ClientLevel lv, DeferredHolder<ParticleType<?>, ParticleType<FxOptions>> type, int rgb,
                            float scale, int life, int data, int flags, double x, double y, double z,
                            double vx, double vy, double vz) {
        lv.addParticle(new FxOptions(type.get(), rgb & 0xFFFFFF, scale, life, data, flags), x, y, z, vx, vy, vz);
    }

    private SceneFx() {}
}
