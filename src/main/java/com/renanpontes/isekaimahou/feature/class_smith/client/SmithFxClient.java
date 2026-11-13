package com.renanpontes.isekaimahou.feature.class_smith.client;

import static com.renanpontes.isekaimahou.feature.class_smith.client.FxKit.*;

import com.renanpontes.isekaimahou.feature.class_smith.SmithFx;
import com.renanpontes.isekaimahou.feature.class_smith.SmithFxNet;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * SÓ CLIENTE. Coreografia de anime do Ferreiro: fagulhas de forja que pulam e caem, a martelada que abre um anel de
 * choque de bigorna, a onda de erupções do Terremoto, a cratera do Martelo Estelar, o Mundo de Lâminas (cântico com o
 * anel de fogo correndo até a borda, abertura com clarão e coluna, borda de chamas), o escudo de 7 pétalas e o
 * Fantasma Quebrado das lâminas projetadas.
 */
public final class SmithFxClient {
    private static final int EMBER = SmithFx.EMBER;
    private static final int MOLTEN = SmithFx.MOLTEN;
    private static final int STEEL = SmithFx.STEEL;
    private static final int WHITE = 0xFFFFFF;

    public static void init() {
        SmithFxNet.clientBurst = SmithFxClient::onBurst;
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> FxKit.tick());
    }

    private static void onBurst(SmithFxNet.Burst p) {
        if (level() == null || !near(p.pos())) return;
        try {
            switch (p.kind()) {
                case SmithFxNet.SPARKS -> sparks(p.pos(), p.rgb(), (int) Math.max(4, p.size()));
                case SmithFxNet.GROUND_BURST -> debris(p.pos(), n((int) Math.max(4, p.size())), 0.6, 0.35);
                case SmithFxNet.HAMMER_SLAM -> hammerSlam(p);
                case SmithFxNet.QUAKE -> quake(p);
                case SmithFxNet.STAR_IMPACT -> starImpact(p);
                case SmithFxNet.WORLD_ARIA -> worldAria(p);
                case SmithFxNet.WORLD_OPEN -> worldOpen(p);
                case SmithFxNet.WORLD_BORDER -> worldBorder(p);
                case SmithFxNet.WORLD_CLOSE -> worldClose(p);
                case SmithFxNet.RHO_AIAS_UP -> rhoAiasUp(p);
                case SmithFxNet.RHO_AIAS_BLOCK -> rhoAiasBlock(p);
                case SmithFxNet.RHO_AIAS_BREAK -> rhoAiasBreak(p);
                case SmithFxNet.BLADE_HIT -> bladeHit(p);
                case SmithFxNet.BROKEN_PHANTASM -> brokenPhantasm(p);
                case SmithFxNet.CALADBOLG -> caladbolg(p);
                case SmithFxNet.EPHEMERAL -> ephemeral(p);
                case SmithFxNet.EPHEMERAL_BLAST -> ephemeralBlast(p);
                case SmithFxNet.BLADE_CAUGHT -> caught(p);
                case SmithFxNet.LEARN -> learn(p);
                default -> {}
            }
        } catch (RuntimeException ignored) {
            // efeito quebrado nunca derruba o cliente
        }
    }

    /** Fagulhas de forja: brasas que pulam em arco e caem, gota de lava e um brilho quente. */
    static void sparks(Vec3 at, int rgb, int count) {
        add(glow(light(rgb, 0.4F), 0.5F), at, Vec3.ZERO);
        for (int i = 0; i < n(count); i++) {
            double a = RND.nextDouble() * Math.PI * 2;
            double s = 0.08 + RND.nextDouble() * 0.18;
            add(i % 3 == 0 ? spark(light(rgb, 0.3F), 0.22F) : ember(rgb, 0.25F), at, new Vec3(Math.cos(a) * s, 0.12 + RND.nextDouble() * 0.2, Math.sin(a) * s));
        }
        if (hi()) add(ParticleTypes.LAVA, at, Vec3.ZERO);
        if (count >= 20) add(smoke(0x5A4A40, 0.8F), at, new Vec3(0, 0.04, 0));
    }

    private static void hammerSlam(SmithFxNet.Burst p) {
        Vec3 at = p.pos();
        // antecipação: brilho quente no ponto; impacto: anel de choque laranja + anel de bigorna (aço) + brasas
        add(glow(MOLTEN, 1.2F), at.add(0, 0.3, 0), Vec3.ZERO);
        later(1, () -> {
            flash(at.add(0, 0.3, 0), MOLTEN, 2.0F);
            shock(at.add(0, 0.08, 0), EMBER, 4.5F, 12, null);
            if (hi()) shock(at.add(0, 0.1, 0), STEEL, 3.0F, 8, null);
            sparks(at.add(0, 0.2, 0), MOLTEN, 30);
            groundImpact(at, 3.0, 0x4A3A30);
            shake(at, 14F, 0.9F, 8);
        });
    }

    private static void quake(SmithFxNet.Burst p) {
        Vec3 at = p.pos();
        // erupção: terra, brasas para cima e fenda incandescente
        debris(at, n(10), 0.5, 0.5);
        cone(at.add(0, 0.1, 0), new Vec3(0, 1, 0), 0.4, n(8), 0.35, ember(EMBER, 0.35F));
        add(glow(MOLTEN, 0.7F), at.add(0, 0.15, 0), Vec3.ZERO);
        if (hi()) add(ParticleTypes.LAVA, at, Vec3.ZERO);
        if (hi()) add(smoke(0x4A3A30, 1.2F), at.add(0, 0.3, 0), new Vec3(0, 0.05, 0));
    }

    private static void starImpact(SmithFxNet.Burst p) {
        Vec3 c = p.pos();
        float r = Math.max(2F, p.size());
        boolean last = p.data() == 1;
        int rgb = p.rgb();
        nova(c.add(0, 0.5, 0), rgb, r * 0.9F, true);
        shock(c.add(0, 0.12, 0), MOLTEN, r * 2.6F, 14, null);
        circle(c.add(0, 0.06, 0), rgb, r, 30, 4, 0, false);
        for (int i = 0; i < n(24); i++) {
            double a = RND.nextDouble() * Math.PI * 2;
            double s = 0.2 + RND.nextDouble() * 0.3;
            add(ember(i % 2 == 0 ? rgb : MOLTEN, 0.4F), c.add(0, 0.3, 0), new Vec3(Math.cos(a) * s, 0.3 + RND.nextDouble() * 0.4, Math.sin(a) * s));
        }
        later(4, () -> ring(c.add(0, 0.2, 0), null, r + 0.6, n(20), smoke(0x3A3A3A, 2.2F), 0.08));
        if (last) later(2, () -> pillar(c, rgb, 1.2F, 10F, 20));
        shake(c, 28F, last ? 1.8F : 1.1F, 14);
    }

    private static void worldAria(SmithFxNet.Burst p) {
        Vec3 c = p.pos();
        float r = Math.max(3F, p.size());
        int ticks = Math.max(10, p.data());
        int rgb = p.rgb();
        // o anel de fogo corre do centro até a borda durante o cântico; runas da forja no chão
        circle(c.add(0, 0.05, 0), rgb, r, ticks + 10, 4, 0, false);
        repeat(0, ticks / 3, 3, i -> {
            double rr = r * (i * 3 + 1) / (double) ticks;
            ring(c.add(0, 0.15, 0), null, rr, n((int) Math.max(12, rr * 5)), ParticleTypes.FLAME, 0.0);
            if (hi()) ring(c.add(0, 0.2, 0), null, rr, n(8), ember(MOLTEN, 0.3F), 0.0);
            if (i % 4 == 0) for (int k = 0; k < n(4); k++) {
                double a = RND.nextDouble() * Math.PI * 2;
                add(rune(rgb, 0.35F), c.x + Math.cos(a) * rr * 0.8, c.y + 0.2, c.z + Math.sin(a) * rr * 0.8, 0, 0.05, 0);
            }
        });
    }

    private static void worldOpen(SmithFxNet.Burst p) {
        Vec3 c = p.pos();
        float r = Math.max(3F, p.size());
        int rgb = p.rgb();
        flash(c.add(0, 1, 0), rgb, r);
        later(1, () -> flash(c.add(0, 1, 0), WHITE, r * 0.5F));
        shock(c.add(0, 0.1, 0), MOLTEN, r * 2.2F, 16, null);
        pillar(c, rgb, 1.5F, 14F, 30);
        // colunas de fogo em volta da borda e um céu de engrenagens (brasas altas)
        int cols = n(10);
        for (int i = 0; i < cols; i++) {
            double a = Math.PI * 2 * i / cols;
            Vec3 q = c.add(Math.cos(a) * r, 0, Math.sin(a) * r);
            cone(q, new Vec3(0, 1, 0), 0.15, n(6), 0.4, ParticleTypes.FLAME);
        }
        if (hi()) for (int i = 0; i < n(30); i++) {
            double a = RND.nextDouble() * Math.PI * 2, d = Math.sqrt(RND.nextDouble()) * r;
            add(ember(MOLTEN, 0.3F), c.x + Math.cos(a) * d, c.y + 3 + RND.nextDouble() * 4, c.z + Math.sin(a) * d, 0, -0.02, 0);
        }
        shake(c, r + 14, 1.0F, 14);
        sound(c, SoundEvents.BLAZE_SHOOT, 1.0F, 0.5F);
    }

    private static void worldBorder(SmithFxNet.Burst p) {
        Vec3 c = p.pos();
        float r = Math.max(3F, p.size());
        int pts = n((int) Math.max(16, r * 3));
        double off = RND.nextDouble() * Math.PI * 2;
        for (int i = 0; i < pts; i++) {
            double a = off + Math.PI * 2 * i / pts;
            double x = c.x + Math.cos(a) * r, z = c.z + Math.sin(a) * r;
            add(ParticleTypes.FLAME, x, c.y + 0.15, z, 0, 0.03, 0);
            if (i % 2 == 0) add(ember(p.rgb(), 0.3F), x, c.y + 0.6 + RND.nextDouble(), z, 0, 0.04, 0);
        }
    }

    private static void worldClose(SmithFxNet.Burst p) {
        Vec3 c = p.pos();
        float r = Math.max(3F, p.size());
        ring(c.add(0, 0.2, 0), null, r, n((int) Math.max(16, r * 3)), smoke(0x3A3A3A, 2.0F), -0.05);
        implode(c.add(0, 1, 0), null, r * 0.8, 4, 8, ember(MOLTEN, 0.3F));
    }

    private static void rhoAiasUp(SmithFxNet.Burst p) {
        Vec3 c = p.pos();
        int rgb = p.rgb();
        Vec3 f = p.vec().lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : p.vec().normalize();
        // as 7 pétalas se abrem uma a uma num disco de frente
        Vec3[] b = basis(f);
        for (int i = 0; i < 7; i++) {
            final int k = i;
            later(i, () -> {
                double a = Math.PI * 2 * k / 7;
                Vec3 tip = c.add(b[0].scale(Math.cos(a) * 1.6)).add(b[1].scale(Math.sin(a) * 1.6));
                Vec3 l = c.add(b[0].scale(Math.cos(a - 0.35) * 0.8)).add(b[1].scale(Math.sin(a - 0.35) * 0.8));
                Vec3 rr = c.add(b[0].scale(Math.cos(a + 0.35) * 0.8)).add(b[1].scale(Math.sin(a + 0.35) * 0.8));
                line(c, l, 0.12, glow(rgb, 0.18F), 0);
                line(l, tip, 0.12, glow(rgb, 0.18F), 0);
                line(tip, rr, 0.12, glow(rgb, 0.18F), 0);
                line(rr, c, 0.12, glow(rgb, 0.18F), 0);
                sound(c, SoundEvents.AMETHYST_BLOCK_PLACE, 0.6F, 1.0F + k * 0.1F);
            });
        }
        later(7, () -> {
            flash(c, rgb, 2.0F);
            shock(c, rgb, 3F, 8, f);
        });
    }

    private static void rhoAiasBlock(SmithFxNet.Burst p) {
        Vec3 at = p.pos();
        hitSpark(at, p.vec(), p.rgb(), 1.2F);
        ring(at, p.vec().lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : p.vec(), 0.5, 6, glow(p.rgb(), 0.3F), 0.0);
    }

    private static void rhoAiasBreak(SmithFxNet.Burst p) {
        Vec3 c = p.pos();
        flash(c, p.rgb(), 1.6F);
        sphere(c, n(24), 0.35, glow(p.rgb(), 0.3F));
        sphere(c, n(12), 0.5, spark(WHITE, 0.25F));
        shake(c, 8F, 0.4F, 4);
    }

    private static void bladeHit(SmithFxNet.Burst p) {
        hitSpark(p.pos(), p.vec(), p.rgb(), 1.1F);
        sparks(p.pos(), p.rgb(), 8);
    }

    private static void brokenPhantasm(SmithFxNet.Burst p) {
        Vec3 at = p.pos();
        float r = Math.max(1.5F, p.size());
        int rgb = p.rgb();
        // a lâmina brilha demais por um instante (antecipação) e explode
        add(glow(WHITE, 1.2F), at, Vec3.ZERO);
        later(2, () -> {
            nova(at, rgb, r, false);
            sphere(at, n(20), 0.6, streak(light(rgb, 0.4F), 0.6F));
            star(at, new Vec3(0, 1, 0), r * 1.2, 8, glow(rgb, 0.25F), 0.15);
            if (hi()) sphere(at, n(12), 0.3, ParticleTypes.ELECTRIC_SPARK);
            sound(at, SoundEvents.GLASS_BREAK, 1.0F, 0.6F);
        });
    }

    private static void caladbolg(SmithFxNet.Burst p) {
        Vec3 eye = p.pos();
        Vec3 look = p.vec().lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : p.vec().normalize();
        int rgb = p.rgb();
        // o espaço torce em espiral na frente do arco, depois o disparo rasga o ar
        Vec3 c = eye.add(look.scale(1.4));
        for (int k = 0; k < 4; k++) {
            final int kk = k;
            later(k, () -> ring(c.add(look.scale(kk * 0.4)), look, 0.9 - kk * 0.15, n(14), glow(rgb, 0.25F), -0.03));
        }
        later(4, () -> {
            flash(c, rgb, 1.8F);
            shock(c, rgb, 3.0F, 8, look);
            helix(c, c.add(look.scale(10)), 0.5, 4, n(40), 0F, spark(rgb, 0.25F));
            speedLines(c.add(look.scale(4)), look, 0.8, n(10), 1.4, streak(WHITE, 0.6F));
            shake(eye, 8F, 0.6F, 5);
        });
    }

    private static void ephemeral(SmithFxNet.Burst p) {
        Vec3 above = p.pos();
        int rgb = p.rgb();
        // a espada se "projeta": contorno de luz desenhado de baixo para cima
        repeat(0, 5, 1, i -> {
            double y0 = -0.8 + i * 0.35;
            line(above.add(0, y0, 0), above.add(0, y0 + 0.35, 0), 0.06, glow(rgb, 0.2F), 0);
            if (i == 1) line(above.add(-0.35, y0, 0), above.add(0.35, y0, 0), 0.06, glow(rgb, 0.2F), 0);
        });
        if (hi()) sphere(above, n(10), 0.12, rune(rgb, 0.3F));
    }

    private static void ephemeralBlast(SmithFxNet.Burst p) {
        Vec3 at = p.pos();
        float r = Math.max(1F, p.size());
        nova(at, p.rgb(), r, false);
        sphere(at, n(16), 0.4, spark(WHITE, 0.25F));
    }

    private static void caught(SmithFxNet.Burst p) {
        Vec3 at = p.pos();
        ring(at, null, 0.4, n(10), glow(p.rgb(), 0.22F), -0.05);
        add(glow(WHITE, 0.5F), at, Vec3.ZERO);
    }

    private static void learn(SmithFxNet.Burst p) {
        Vec3 c = p.pos();
        sparks(c.add(0, 1, 0), MOLTEN, 30);
        circle(c.add(0, 0.05, 0), MOLTEN, 2F, 30, 3, 0, false);
        riseSpiral(c, 1.0, 2.2, n(24), 0F, 2.0, ember(EMBER, 0.3F));
    }

    private SmithFxClient() {}
}
