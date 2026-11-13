package com.renanpontes.isekaimahou.feature.class_rogue.client;

import static com.renanpontes.isekaimahou.feature.class_rogue.client.FxKit.*;

import com.renanpontes.isekaimahou.feature.class_rogue.RogueFx;
import com.renanpontes.isekaimahou.feature.class_rogue.RogueFxNet;
import com.renanpontes.isekaimahou.feature.class_rogue.entity.RogueKnifeEntity;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * SÓ CLIENTE. Coreografia de anime do Ladino: passo de sombra (some em fumaça, rastro e imagem residual no lugar),
 * cortes pelas costas com risco vermelho, críticos com estalo de sangue, a garra do assassino, o cartão de visita do
 * Ladrão Fantasma, o véu de fumaça, a Velocidade Divina e os drenos (orbes que viajam do alvo até você).
 */
public final class RogueFxClient {
    private static final int PURPLE = RogueFx.PURPLE;
    private static final int SHADOW = RogueFx.SHADOW;
    private static final int BLOOD = RogueFx.BLOOD;
    private static final int GOLD = RogueFx.GOLD;
    private static final int MANA = RogueFx.MANA;
    private static final int CARD = RogueFx.CARD;
    private static final int WHITE = 0xFFFFFF;

    public static void init() {
        RogueFxNet.clientBurst = RogueFxClient::onBurst;
        RogueFxNet.clientTrail = RogueFxClient::trail;
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> FxKit.tick());
    }

    /** Facas lançadas: risco fino e brilho da lâmina girando. */
    private static void trail(Entity e) {
        if (!(e instanceof RogueKnifeEntity k)) return;
        Vec3 p = e.position();
        if (!near(p)) return;
        Vec3 v = e.getDeltaMovement();
        if (v.lengthSqr() < 1.0E-4) return;
        int rgb = k.color();
        add(streak(rgb, 0.4F), p, v.scale(0.05));
        if (hi() && e.tickCount % 2 == 0) add(spark(light(rgb, 0.5F), 0.18F), p, Vec3.ZERO);
    }

    private static void onBurst(RogueFxNet.Burst p) {
        if (level() == null || !near(p.pos())) return;
        try {
            switch (p.kind()) {
                case RogueFxNet.VANISH -> vanishPuff(p.pos(), p.rgb() == 0 ? SHADOW : p.rgb());
                case RogueFxNet.STEP -> step(p);
                case RogueFxNet.SLASH -> backSlash(p);
                case RogueFxNet.CRIT -> crit(p);
                case RogueFxNet.CARD -> card(p);
                case RogueFxNet.CLAW -> claw(p);
                case RogueFxNet.HEART -> heart(p);
                case RogueFxNet.GODSPEED -> godspeed(p);
                case RogueFxNet.SMOKE_VEIL -> smokeVeil(p);
                case RogueFxNet.DRAIN -> drain(p);
                case RogueFxNet.ONE_CUT -> oneCut(p);
                case RogueFxNet.KNIFE_FAN -> knifeFan(p);
                case RogueFxNet.LUNGE -> lunge(p);
                case RogueFxNet.VITAL_MARK -> vitalMark(p);
                case RogueFxNet.LEARN -> learn(p);
                default -> {}
            }
        } catch (RuntimeException ignored) {
            // efeito quebrado nunca derruba o cliente
        }
    }

    private static void vanishPuff(Vec3 feet, int rgb) {
        vanish(feet, rgb, 1.2F);
        add(glow(PURPLE, 0.8F), feet.add(0, 1, 0), Vec3.ZERO);
        if (hi()) sphere(feet.add(0, 1, 0), n(10), -0.12, ParticleTypes.REVERSE_PORTAL);
    }

    private static void step(RogueFxNet.Burst p) {
        Vec3 a = p.pos(), b = p.vec();
        Entity e = entity(p.entity());
        float yaw = e == null ? 0F : e.getYRot() + 90F;
        // imagem residual no ponto de partida, fumaça, e um risco de sombra até o destino
        afterimage(a, yaw, PURPLE, 1F);
        vanishPuff(a, SHADOW);
        line(a.add(0, 1, 0), b.add(0, 1, 0), 0.3, streak(PURPLE, 0.5F), 0.4);
        if (hi()) line(a.add(0, 0.6, 0), b.add(0, 0.6, 0), 0.5, smoke(SHADOW, 0.8F), 0.3);
        later(1, () -> {
            vanishPuff(b, SHADOW);
            ring(b.add(0, 0.1, 0), null, 0.6, n(12), glow(PURPLE, 0.25F), 0.12);
        });
    }

    /** Corte em arco atrás do alvo: risco brilhante + gotas. */
    private static void backSlash(RogueFxNet.Burst p) {
        Entity t = entity(p.entity());
        Vec3 c = center(t, p.pos());
        Vec3 dir = t == null ? new Vec3(0, 0, 1) : Vec3.directionFromRotation(0, t.getYRot());
        int rgb = p.rgb();
        slashStreak(c, dir.scale(-1), 0.6 + RND.nextDouble() * 0.6, 0.7, rgb, Math.PI * 0.8);
        if (rgb == BLOOD) cone(c, dir, 0.8, n(10), 0.2, dust(BLOOD, 0.9F));
        hitSpark(c, dir, light(rgb, 0.3F), 1F);
    }

    private static void crit(RogueFxNet.Burst p) {
        Entity t = entity(p.entity());
        Vec3 c = center(t, p.pos());
        boolean vital = p.data() == 1;
        // estalo: X rápido + faíscas; vital = vermelho com respingo e mini-tremor
        int rgb = vital ? BLOOD : WHITE;
        Vec3[] b = basis(new Vec3(1, 0, 0.3));
        line(c.add(b[0].scale(0.5)).add(b[1].scale(0.5)), c.subtract(b[0].scale(0.5)).subtract(b[1].scale(0.5)), 0.08, glow(rgb, 0.22F), 0);
        line(c.add(b[0].scale(-0.5)).add(b[1].scale(0.5)), c.subtract(b[0].scale(-0.5)).subtract(b[1].scale(0.5)), 0.08, glow(rgb, 0.22F), 0);
        sphere(c, n(vital ? 16 : 8), 0.3, spark(vital ? 0xFF5050 : 0xFFF2B0, 0.25F));
        if (vital) {
            sphere(c, n(14), 0.2, dust(BLOOD, 1.1F));
            flash(c, BLOOD, 0.9F);
            shake(c, 5F, 0.3F, 3);
            sound(c, SoundEvents.PLAYER_ATTACK_CRIT, 0.8F, 0.7F);
        }
    }

    private static void card(RogueFxNet.Burst p) {
        Vec3 at = p.pos().add(0, 1.2, 0);
        // o cartão gira e se desfaz em pétalas de luz
        repeat(0, 6, 2, i -> {
            double a = i * 0.5;
            Vec3 n = new Vec3(Math.cos(a), 0, Math.sin(a));
            Vec3[] b = basis(n);
            Vec3 r = b[0].scale(0.35), u = b[1].scale(0.5);
            ParticleOptions o = glow(CARD, 0.18F);
            line(at.add(r).add(u), at.subtract(r).add(u), 0.08, o, 0);
            line(at.subtract(r).add(u), at.subtract(r).subtract(u), 0.08, o, 0);
            line(at.subtract(r).subtract(u), at.add(r).subtract(u), 0.08, o, 0);
            line(at.add(r).subtract(u), at.add(r).add(u), 0.08, o, 0);
        });
        later(12, () -> {
            flash(at, CARD, 1.0F);
            sphere(at, n(18), 0.2, mote(CARD, 0.25F));
            if (hi()) sphere(at, n(10), 0.3, ParticleTypes.FIREWORK);
            sound(at, SoundEvents.FIREWORK_ROCKET_TWINKLE_FAR, 0.6F, 1.6F);
        });
    }

    private static void claw(RogueFxNet.Burst p) {
        Entity t = entity(p.entity());
        Vec3 c = center(t, p.pos());
        Vec3 dir = p.vec().lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : p.vec().normalize();
        Vec3 side = new Vec3(-dir.z, 0, dir.x).normalize();
        // três garras cruzando em sequência rápida
        for (int k = -1; k <= 1; k++) {
            final int kk = k;
            later(k + 1, () -> {
                Vec3 off = side.scale(kk * 0.2);
                Vec3 a = c.add(off).add(side.scale(-0.55)).add(0, 0.55, 0);
                Vec3 b = c.add(off).add(side.scale(0.55)).add(0, -0.55, 0);
                line(a, b, 0.06, streak(BLOOD, 0.45F), 0);
                line(a, b, 0.12, glow(WHITE, 0.16F), 0);
            });
        }
        later(3, () -> {
            cone(c, dir, 0.7, n(12), 0.25, dust(BLOOD, 1.0F));
            hitSpark(c, dir, BLOOD, 1.2F);
        });
    }

    private static void heart(RogueFxNet.Burst p) {
        Entity t = entity(p.entity());
        Vec3 c = center(t, p.pos());
        flash(c, BLOOD, 1.6F);
        sphere(c, n(30), 0.35, dust(BLOOD, 1.4F));
        shock(c, BLOOD, 2.5F, 10, new Vec3(0, 1, 0));
        shake(c, 8F, 0.6F, 6);
    }

    private static void godspeed(RogueFxNet.Burst p) {
        Entity t = entity(p.entity());
        Vec3 c = center(t, p.pos());
        int rgb = p.rgb();
        flash(c, rgb, 1.4F);
        sphere(c, n(20), 0.4, spark(rgb, 0.3F));
        // arcos elétricos zigue-zague
        for (int i = 0; i < n(4); i++) {
            Vec3 a = c;
            for (int s = 0; s < 4; s++) {
                Vec3 b = a.add((RND.nextDouble() - 0.5) * 0.9, 0.3 + RND.nextDouble() * 0.3, (RND.nextDouble() - 0.5) * 0.9);
                line(a, b, 0.1, glow(light(rgb, 0.4F), 0.15F), 0);
                a = b;
            }
        }
        if (hi()) ring(c, null, 0.8, n(14), ParticleTypes.ELECTRIC_SPARK, 0.2);
    }

    private static void smokeVeil(RogueFxNet.Burst p) {
        Vec3 c = p.pos();
        double r = Math.max(2, p.size());
        // a fumaça explode em anel e sobe numa cúpula escura
        ring(c.add(0, 0.2, 0), null, 0.5, n(24), smoke(SHADOW, 2.6F), 0.25 * r / 3);
        repeat(1, 4, 3, i -> {
            for (int k = 0; k < n(14); k++) {
                double a = RND.nextDouble() * Math.PI * 2;
                double rr = Math.sqrt(RND.nextDouble()) * r;
                add(smoke(i % 2 == 0 ? SHADOW : 0x2A2A2A, 2.4F), c.x + Math.cos(a) * rr, c.y + 0.2 + RND.nextDouble() * 1.8, c.z + Math.sin(a) * rr,
                        0, 0.01, 0);
            }
        });
        if (hi()) sphere(c.add(0, 1, 0), n(12), 0.2, ParticleTypes.LARGE_SMOKE);
    }

    private static void drain(RogueFxNet.Burst p) {
        int rgb = switch (p.data()) {
            case 1 -> MANA;
            case 2 -> GOLD;
            default -> p.rgb();
        };
        Vec3 from = p.pos();
        // orbes que viajam em curva do alvo até quem rouba, ao longo de 8 ticks
        Vec3 bend = new Vec3((RND.nextDouble() - 0.5) * 1.5, 0.8 + RND.nextDouble() * 0.6, (RND.nextDouble() - 0.5) * 1.5);
        sphere(from, n(8), 0.1, wisp(rgb, 0.3F));
        repeat(0, 8, 1, i -> {
            Entity thief = entity(p.entity());
            Vec3 to = center(thief, from);
            for (int k = 0; k < 3; k++) {
                double t = Math.min(1, (i + k * 0.33) / 8.0);
                Vec3 q = from.scale((1 - t) * (1 - t)).add(from.add(to).scale(0.5).add(bend).scale(2 * t * (1 - t))).add(to.scale(t * t));
                add(glow(k == 0 ? WHITE : rgb, 0.25F), q, Vec3.ZERO);
                if (hi()) add(mote(rgb, 0.18F), q, new Vec3(0, 0.01, 0));
            }
            if (i == 7) ring(to, null, 0.6, n(12), glow(rgb, 0.22F), 0.08);
        });
    }

    private static void oneCut(RogueFxNet.Burst p) {
        Entity t = entity(p.entity());
        Vec3 c = center(t, p.pos());
        Vec3 side = p.vec().lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : p.vec().normalize();
        // o mundo "para" (clarão escuro), depois um único traço limpo e longo
        add(smoke(0x100008, 1.6F), c, Vec3.ZERO);
        later(2, () -> {
            Vec3 a = c.add(side.scale(-1.6)).add(0, 0.9, 0), b = c.add(side.scale(1.6)).add(0, -0.9, 0);
            line(a, b, 0.05, glow(0x8A0A1E, 0.2F), 0);
            line(a, b, 0.1, glow(WHITE, 0.12F), 0);
            flash(c, 0x8A0A1E, 1.2F);
            cone(c, side, 0.4, n(10), 0.3, dust(0x8A0A1E, 1.0F));
            sound(c, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8F, 0.5F);
        });
    }

    private static void knifeFan(RogueFxNet.Burst p) {
        Vec3 at = p.pos();
        Vec3 dir = p.vec().lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : p.vec().normalize();
        slashArc(at, dir, 0, 0.8, Math.toRadians(Math.max(20, p.size())), n(12), glow(p.rgb(), 0.22F), 0.2);
        add(glow(WHITE, 0.5F), at, Vec3.ZERO);
    }

    private static void lunge(RogueFxNet.Burst p) {
        line(p.pos(), p.vec(), 0.25, streak(p.rgb(), 0.5F), 0.15);
        slashStreak(p.vec(), p.vec().subtract(p.pos()), RND.nextDouble() * Math.PI, 0.6, p.rgb(), Math.PI * 0.7);
    }

    private static void vitalMark(RogueFxNet.Burst p) {
        Entity t = entity(p.entity());
        Vec3 c = t == null ? p.pos() : t.position().add(0, t.getBbHeight() + 0.45, 0);
        // mira vermelha girando que encolhe até o ponto
        implode(c, new Vec3(0, 1, 0), 0.9, 4, 6, glow(BLOOD, 0.2F));
        star(c, new Vec3(0, 0, 1), 0.4, 4, glow(BLOOD, 0.15F), 0.06);
    }

    private static void learn(RogueFxNet.Burst p) {
        Vec3 c = p.pos();
        riseSpiral(c, 1.1, 2.2, n(30), 0F, 2.5, wisp(PURPLE, 0.3F));
        vanish(c, SHADOW, 1F);
        flash(c.add(0, 1, 0), PURPLE, 1.2F);
    }

    private RogueFxClient() {}
}
