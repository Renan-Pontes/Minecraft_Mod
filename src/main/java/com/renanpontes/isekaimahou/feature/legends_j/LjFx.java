package com.renanpontes.isekaimahou.feature.legends_j;

import com.renanpontes.isekaimahou.core.ElementFx;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Visuais do legends_j montados com partículas do núcleo ({@link ElementFx#glow}: o cliente já respeita o Preset
 * Gráfico). Tudo com teto de pontos para não pesar com muitas magias ao mesmo tempo.
 */
public final class LjFx {
    /** Cinza-aço das correntes (Kurapika). */
    public static final int CHAIN = 0xB8C0CC;
    /** Vermelho do Raio Negro. */
    public static final int FLASH_RED = 0xFF1A2E;
    /** "Preto" do Raio Negro (um roxo bem escuro, para aparecer no escuro). */
    public static final int FLASH_BLACK = 0x2A0018;

    /** Corrente de {@code from} até {@code to}: elos alternando grande/pequeno (máx. 48 elos). */
    public static void chain(ServerLevel l, Vec3 from, Vec3 to, int rgb) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 0.1) return;
        int n = (int) Math.min(48, Math.ceil(len / 0.3));
        for (int i = 0; i <= n; i++) {
            Vec3 p = from.add(d.scale(i / (double) n));
            ElementFx.sendFar(l, ElementFx.glow(rgb, i % 2 == 0 ? 0.55F : 0.3F), p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }

    /** Corrente enrolada em volta de alguém (espiral subindo). */
    public static void coil(ServerLevel l, Vec3 feet, double radius, double height, int rgb) {
        int n = 28;
        for (int i = 0; i < n; i++) {
            double t = i / (double) n;
            double a = t * Math.PI * 6;
            ElementFx.sendFar(l, ElementFx.glow(rgb, i % 2 == 0 ? 0.5F : 0.3F),
                    feet.x + Math.cos(a) * radius, feet.y + t * height, feet.z + Math.sin(a) * radius, 1, 0, 0, 0, 0);
        }
    }

    /** Faíscas em zigue-zague (raio negro e vermelho / faíscas da alquimia). */
    public static void sparks(ServerLevel l, Vec3 c, int rgbA, int rgbB, double size, int bolts) {
        RandomSource r = l.random;
        for (int b = 0; b < Math.min(10, bolts); b++) {
            Vec3 p = c;
            Vec3 dir = new Vec3(r.nextGaussian(), r.nextGaussian() * 0.6, r.nextGaussian()).normalize();
            int segs = 5;
            for (int s = 0; s < segs; s++) {
                Vec3 jitter = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).scale(size * 0.12);
                Vec3 next = p.add(dir.scale(size / segs)).add(jitter);
                Vec3 mid = p.add(next).scale(0.5);
                int rgb = (b + s) % 2 == 0 ? rgbA : rgbB;
                ElementFx.sendFar(l, ElementFx.glow(rgb, 0.45F), p.x, p.y, p.z, 1, 0, 0, 0, 0);
                ElementFx.sendFar(l, ElementFx.glow(rgb, 0.35F), mid.x, mid.y, mid.z, 1, 0, 0, 0, 0);
                p = next;
            }
        }
    }

    /** "Poof" de troca de lugar (Boogie Woogie): nuvenzinha + anel. */
    public static void poof(ServerLevel l, Vec3 feet, double height, int rgb) {
        l.sendParticles(ParticleTypes.CLOUD, feet.x, feet.y + height * 0.5, feet.z, 8, 0.25, height * 0.3, 0.25, 0.02);
        ElementFx.shockwave(l, feet.add(0, 0.1, 0), rgb, 1.4F, 8, null);
    }

    private LjFx() {}
}
