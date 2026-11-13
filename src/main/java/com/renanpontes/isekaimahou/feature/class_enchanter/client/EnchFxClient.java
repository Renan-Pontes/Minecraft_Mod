package com.renanpontes.isekaimahou.feature.class_enchanter.client;

import static com.renanpontes.isekaimahou.feature.class_enchanter.client.FxKit.*;

import com.renanpontes.isekaimahou.feature.class_enchanter.EnchFx;
import com.renanpontes.isekaimahou.feature.class_enchanter.EnchFxNet;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * SÓ CLIENTE. Coreografia de anime do Encantador: círculos de inscrição com hexagrama que se desenham, espirais de runas,
 * circuitos rúnicos acesos nas manoplas, o elo de encantamento, o estilhaço hexagonal da Anti-Magia e as transformações
 * (Portões com onda de choque e pressão, Kaioken em chamas vermelhas, a explosão "sem energia" da Restrição Celestial).
 */
public final class EnchFxClient {
    private static final int TEAL = EnchFx.TEAL;
    private static final int WHITE = EnchFx.WHITE;
    private static final int GOLD = EnchFx.GOLD;
    private static final int ANTI = EnchFx.ANTI;
    private static final int STEEL = EnchFx.STEEL;
    private static final int RED = EnchFx.RED;

    public static void init() {
        EnchFxNet.clientBurst = EnchFxClient::onBurst;
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> FxKit.tick());
    }

    private static void onBurst(EnchFxNet.Burst p) {
        if (level() == null || !near(p.pos())) return;
        try {
            switch (p.kind()) {
                case EnchFxNet.RING -> runeRing(p.pos(), p.size(), p.rgb());
                case EnchFxNet.SIGIL -> sigil(p.pos(), p.size(), p.rgb(), p.data());
                case EnchFxNet.SPIRAL -> spiral(p);
                case EnchFxNet.BEAM -> circuit(p.pos(), p.vec(), p.rgb());
                case EnchFxNet.SHATTER -> shatter(p.pos(), p.size());
                case EnchFxNet.GATE -> gate(p);
                case EnchFxNet.DEATH_GATE -> deathGate(p);
                case EnchFxNet.KAIOKEN -> kaioken(p);
                case EnchFxNet.HEAVENLY -> heavenly(p);
                case EnchFxNet.IMBUE_GLOW -> imbueGlow(p);
                case EnchFxNet.IMBUE_HIT -> imbueHit(p);
                case EnchFxNet.RITE_DONE -> riteDone(p);
                case EnchFxNet.SANCTUM_OPEN -> sanctum(p);
                default -> {}
            }
        } catch (RuntimeException ignored) {
            // efeito quebrado nunca derruba o cliente
        }
    }

    private static void runeRing(Vec3 c, double r, int rgb) {
        ring(c, null, r, n((int) Math.min(48, 8 + r * 8)), glow(rgb, 0.22F), 0.0);
        if (hi()) for (int i = 0; i < n(4); i++) {
            double a = RND.nextDouble() * Math.PI * 2;
            add(rune(rgb, 0.3F), c.x + Math.cos(a) * r, c.y + 0.1, c.z + Math.sin(a) * r, 0, 0.04, 0);
        }
    }

    /** Círculo de inscrição: dois anéis e o hexagrama, desenhados traço a traço, com runas subindo. */
    static void sigil(Vec3 c, double r, int rgb, int accent) {
        circle(c, rgb, (float) r, 30, 3, 0, false);
        if (hi()) circle(c.add(0, 0.02, 0), accent, (float) r * 0.82F, 30, 2, 1, true);
        for (int tri = 0; tri < 2; tri++) {
            for (int k = 0; k < 3; k++) {
                final double a0 = tri * Math.PI / 3 + k * Math.PI * 2 / 3;
                later(tri * 3 + k, () -> {
                    double a1 = a0 + Math.PI * 2 / 3;
                    Vec3 p0 = new Vec3(c.x + Math.cos(a0) * r * 0.82, c.y + 0.03, c.z + Math.sin(a0) * r * 0.82);
                    Vec3 p1 = new Vec3(c.x + Math.cos(a1) * r * 0.82, c.y + 0.03, c.z + Math.sin(a1) * r * 0.82);
                    line(p0, p1, 0.12, glow(accent, 0.16F), 0);
                });
            }
        }
        for (int i = 0; i < n((int) Math.min(20, r * 4)); i++) {
            double a = RND.nextDouble() * Math.PI * 2, d = RND.nextDouble() * r;
            add(rune(i % 2 == 0 ? rgb : accent, 0.3F), c.x + Math.cos(a) * d, c.y + 0.1, c.z + Math.sin(a) * d, 0, 0.05, 0);
        }
    }

    private static void spiral(EnchFxNet.Burst p) {
        Entity e = entity(p.entity());
        Vec3 base = e == null ? p.pos() : e.position();
        double h = e == null ? 1.8 : Math.max(1.0, e.getBbHeight());
        double r = e == null ? 0.6 : Math.max(0.55, e.getBbWidth() * 0.8);
        riseSpiral(base, r, h, n(24), 0F, 2.5, glow(p.rgb(), 0.25F));
        if (hi()) riseSpiral(base, r, h, n(12), (float) Math.PI, 2.5, rune(p.data(), 0.3F));
        later(4, () -> ring(base.add(0, h + 0.2, 0), null, r * 0.6, n(10), glow(p.data(), 0.2F), -0.03));
        sound(base, SoundEvents.ENCHANTMENT_TABLE_USE, 0.5F, 1.4F);
    }

    /** Elo: circuito em ângulos retos (como trilha de placa) indo de um ponto ao outro. */
    private static void circuit(Vec3 from, Vec3 to, int rgb) {
        Vec3 d = to.subtract(from);
        if (d.lengthSqr() < 0.01) return;
        Vec3 mid1 = from.add(d.x * 0.5, 0, 0);
        Vec3 mid2 = mid1.add(0, d.y, d.z * 0.5);
        line(from, mid1, 0.15, glow(rgb, 0.16F), 0);
        line(mid1, mid2, 0.15, glow(rgb, 0.16F), 0);
        line(mid2, to, 0.15, glow(rgb, 0.16F), 0);
        add(glow(WHITE, 0.4F), mid1, Vec3.ZERO);
        add(glow(WHITE, 0.4F), mid2, Vec3.ZERO);
        // um pulso de luz corre pelo circuito
        repeat(1, 6, 1, i -> add(glow(WHITE, 0.3F), from.lerp(to, (i + 1) / 6.0), Vec3.ZERO));
    }

    private static void shatter(Vec3 pos, double radius) {
        // hexágono de vidro que trinca e se desfaz em cacos
        Vec3 n = new Vec3(0, 0, 1);
        Vec3[] b = basis(n);
        Vec3[] pts = new Vec3[6];
        for (int i = 0; i < 6; i++) {
            double a = Math.PI / 3 * i;
            pts[i] = pos.add(b[0].scale(Math.cos(a) * radius)).add(b[1].scale(Math.sin(a) * radius));
        }
        for (int i = 0; i < 6; i++) line(pts[i], pts[(i + 1) % 6], 0.12, glow(ANTI, 0.18F), 0);
        later(2, () -> {
            for (int i = 0; i < 6; i++) line(pos, pts[i], 0.15, spark(WHITE, 0.15F), 0);
            sphere(pos, n(14), 0.25, glow(ANTI, 0.2F));
            if (hi()) sphere(pos, n(6), 0.3, ParticleTypes.ELECTRIC_SPARK);
            sound(pos, SoundEvents.AMETHYST_CLUSTER_BREAK, 0.6F, 1.6F);
        });
    }

    private static void gate(EnchFxNet.Burst p) {
        Entity e = entity(p.entity());
        Vec3 feet = e == null ? p.pos() : e.position();
        int gate = Math.max(1, p.data());
        int rgb = p.rgb();
        // o portão se abre: pressão para fora (ondas no chão), aura subindo, vapor nos últimos portões
        for (int k = 0; k < 1 + gate / 3; k++) {
            final int kk = k;
            later(k * 2, () -> shock(feet.add(0, 0.1, 0), kk % 2 == 0 ? rgb : WHITE, 2.0F + kk * 1.6F + gate * 0.25F, 10, null));
        }
        flash(feet.add(0, 1, 0), rgb, 0.8F + gate * 0.2F);
        debris(feet, n(6 + gate * 2), 1.2, 0.3 + gate * 0.04);
        repeat(0, 6, 2, i -> {
            Entity cur = entity(p.entity());
            Vec3 f = cur == null ? feet : cur.position();
            riseSpiral(f, 0.7, 2.2, n(10 + gate), i * 1.1F, 1.5, glow(rgb, 0.28F));
        });
        if (gate >= 6) repeat(0, 10, 2, i -> {
            Entity cur = entity(p.entity());
            Vec3 f = cur == null ? feet : cur.position();
            add(smoke(0xE8E8E8, 1.4F), f.x + (RND.nextDouble() - 0.5), f.y + 0.5 + RND.nextDouble() * 1.4, f.z + (RND.nextDouble() - 0.5), 0, 0.05, 0);
        });
        shake(feet, 8F + gate * 2, 0.2F + gate * 0.1F, 6 + gate);
    }

    private static void deathGate(EnchFxNet.Burst p) {
        Entity e = entity(p.entity());
        Vec3 feet = e == null ? p.pos() : e.position();
        nova(feet.add(0, 1, 0), RED, 3.2F, true);
        for (int k = 0; k < 4; k++) {
            final int kk = k;
            later(k * 2, () -> shock(feet.add(0, 0.15, 0), kk % 2 == 0 ? RED : 0x3A0A0A, 4F + kk * 2.6F, 14, null));
        }
        pillar(feet, RED, 1.2F, 12F, 30);
        shake(feet, 30F, 1.8F, 16);
    }

    private static void kaioken(EnchFxNet.Burst p) {
        Entity e = entity(p.entity());
        Vec3 feet = e == null ? p.pos() : e.position();
        int step = Math.max(0, p.data());
        int red = 0xFF3B30;
        // a aura vermelha explode e fica ardendo por ~1,5 s
        flash(feet.add(0, 1, 0), red, 1.2F + step * 0.5F);
        shock(feet.add(0, 0.1, 0), red, 3F + step, 10, null);
        repeat(0, 15, 2, i -> {
            Entity cur = entity(p.entity());
            Vec3 f = cur == null ? feet : cur.position();
            for (int k = 0; k < n(6 + step * 3); k++) {
                double a = RND.nextDouble() * Math.PI * 2;
                double rr = 0.4 + RND.nextDouble() * 0.4;
                add(ember(k % 3 == 0 ? 0xFF6A5A : red, 0.4F), f.x + Math.cos(a) * rr, f.y + RND.nextDouble() * 2, f.z + Math.sin(a) * rr, 0, 0.08, 0);
            }
            if (hi()) add(glow(red, 0.6F), f.add(0, 1, 0), Vec3.ZERO);
        });
        shake(feet, 10F, 0.4F + step * 0.3F, 8);
    }

    private static void heavenly(EnchFxNet.Burst p) {
        Entity e = entity(p.entity());
        Vec3 feet = e == null ? p.pos() : e.position();
        // pressão física sem energia: ondas de poeira, cacos de chão, nada de brilho
        for (int k = 0; k < 3; k++) {
            final int kk = k;
            later(k * 2, () -> ring(feet.add(0, 0.15, 0), null, 0.6, n(20), smoke(kk == 1 ? RED : STEEL, 1.8F), 0.2 + kk * 0.12));
        }
        groundImpact(feet, 3.0, 0x6A6A6A);
        sphere(feet.add(0, 1, 0), n(14), 0.35, ParticleTypes.CRIT);
        shake(feet, 12F, 0.8F, 8);
    }

    private static void imbueGlow(EnchFxNet.Burst p) {
        Entity e = entity(p.entity());
        if (!(e instanceof LivingEntity le)) return;
        int rgb = p.rgb();
        // circuitos rúnicos acesos na manopla: pequenos traços em volta da mão, acendendo em sequência
        repeat(0, 5, 4, i -> {
            Vec3 hand = hand(le);
            Vec3 d = new Vec3(RND.nextGaussian(), RND.nextGaussian(), RND.nextGaussian()).normalize().scale(0.16);
            Vec3 a = hand.add(d), b = a.add(d.cross(new Vec3(0, 1, 0)).normalize().scale(0.12));
            line(a, b, 0.04, glow(rgb, 0.08F), 0);
            add(glow(WHITE, 0.12F), b, Vec3.ZERO);
            if (hi() && i % 2 == 0) add(rune(rgb, 0.18F), hand, new Vec3(0, 0.03, 0));
        });
    }

    private static Vec3 hand(LivingEntity e) {
        float yaw = e.yBodyRot * ((float) Math.PI / 180F);
        double side = e.getMainArm() == HumanoidArm.RIGHT ? -1 : 1;
        return new Vec3(e.getX() + Math.cos(yaw) * 0.38 * side, e.getY() + e.getBbHeight() * 0.45, e.getZ() + Math.sin(yaw) * 0.38 * side);
    }

    private static void imbueHit(EnchFxNet.Burst p) {
        Vec3 c = p.pos();
        hitSpark(c, p.vec(), p.rgb(), 1.2F);
        star(c, new Vec3(0, 1, 0), 0.5, 6, rune(p.rgb(), 0.25F), 0.12);
        ring(c, null, 0.5, n(10), glow(p.rgb(), 0.22F), 0.12);
    }

    private static void riteDone(EnchFxNet.Burst p) {
        Vec3 c = p.pos();
        sigil(c.add(0, 0.08, 0), 3.0, GOLD, TEAL);
        pillar(c, GOLD, 0.8F, 8F, 30);
        sphere(c.add(0, 1, 0), n(30), 0.35, rune(TEAL, 0.35F));
        flash(c.add(0, 1, 0), GOLD, 2.0F);
        if (hi()) sphere(c.add(0, 1, 0), n(30), 0.5, ParticleTypes.TOTEM_OF_UNDYING);
    }

    private static void sanctum(EnchFxNet.Burst p) {
        Vec3 c = p.pos();
        float r = Math.max(3F, p.size());
        sigil(c.add(0, 0.1, 0), r, GOLD, TEAL);
        // cúpula de luz: anéis subindo e fechando
        repeat(2, 8, 2, i -> {
            double y = i * 0.6;
            double rr = Math.sqrt(Math.max(0.1, r * r - y * y));
            ring(c.add(0, y, 0), null, rr, n(20), glow(i % 2 == 0 ? GOLD : WHITE, 0.2F), 0.0);
        });
        pillar(c, GOLD, 1.0F, 8F, 30);
        shock(c.add(0, 0.1, 0), GOLD, r * 2F, 14, null);
        shake(c, r + 8, 0.5F, 8);
    }

    private EnchFxClient() {}
}
