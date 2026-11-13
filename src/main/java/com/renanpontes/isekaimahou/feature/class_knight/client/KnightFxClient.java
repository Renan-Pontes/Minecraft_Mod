package com.renanpontes.isekaimahou.feature.class_knight.client;

import static com.renanpontes.isekaimahou.feature.class_knight.client.FxKit.*;

import com.renanpontes.isekaimahou.feature.class_knight.KnightFx;
import com.renanpontes.isekaimahou.feature.class_knight.KnightFxNet;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * SÓ CLIENTE. Coreografia de anime do Cavaleiro: ondas de lâmina em meia-lua (núcleo branco + bainha colorida + rastro),
 * círculos de runas, colunas de luz, a Excalibur (o céu desce na lâmina e o feixe rasga o chão em pulsos), a Balmung
 * (meias-luas crepusculares em leque), a Chuva de Espadas cravando com estilhaços, o túnel do Strike Air, a Égide de
 * placas de pedra e a cruz sagrada do Julgamento.
 */
public final class KnightFxClient {
    private static final int GOLD = KnightFx.GOLD;
    private static final int RUNE = KnightFx.RUNE_BLUE;
    private static final int WHITE = 0xFFFFFF;
    private static final int WIND = 0xCFEFFF;
    private static final int STONE = 0x6E7480;

    public static void init() {
        KnightFxNet.clientBurst = KnightFxClient::onBurst;
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> FxKit.tick());
    }

    private static void onBurst(KnightFxNet.Burst p) {
        if (level() == null || !near(p.pos())) return;
        try {
            switch (p.kind()) {
                case KnightFxNet.CRESCENT -> crescent(p.pos(), dirOf(p), p.size(), p.rgb(), p.data() == 1);
                case KnightFxNet.WAVE_BURST -> waveBurst(p);
                case KnightFxNet.RUNE_CIRCLE -> runeCircle(p.pos(), p.size(), p.rgb());
                case KnightFxNet.LINE -> holyLine(p);
                case KnightFxNet.PILLAR -> holyPillar(p.pos(), p.size(), Math.max(2, p.data()), p.rgb());
                case KnightFxNet.EXCALIBUR_CHARGE -> excaliburCharge(p);
                case KnightFxNet.EXCALIBUR_PULSE -> excaliburPulse(p);
                case KnightFxNet.BALMUNG_ARC -> balmungArc(p);
                case KnightFxNet.SWORD_LAND -> swordLand(p);
                case KnightFxNet.STRIKE_AIR -> strikeAir(p);
                case KnightFxNet.AEGIS_UP -> aegisUp(p);
                case KnightFxNet.AEGIS_HIT -> aegisHit(p);
                case KnightFxNet.AEGIS_BREAK -> aegisBreak(p);
                case KnightFxNet.JUDGMENT -> judgment(p);
                case KnightFxNet.ANTI_MAGIC -> antiMagic(p);
                case KnightFxNet.RUNE_MARK -> runeMark(p);
                case KnightFxNet.CALIBURN -> caliburn(p);
                default -> {}
            }
        } catch (RuntimeException ignored) {
            // efeito quebrado nunca derruba o cliente
        }
    }

    private static Vec3 dirOf(KnightFxNet.Burst p) {
        return p.vec().lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : p.vec().normalize();
    }

    /** Meia-lua de lâmina curvada para trás: núcleo branco na frente, bainha colorida e rastro de faíscas. */
    static void crescent(Vec3 pos, Vec3 dir, double half, int rgb, boolean big) {
        Vec3 s = KnightFx.side(dir);
        Vec3 up = s.cross(dir).normalize();
        int n = n(big ? 16 : 10);
        for (int k = -n; k <= n; k++) {
            double t = k / (double) n;
            Vec3 q = pos.add(s.scale(t * half)).subtract(dir.scale(t * t * half * 0.55)).add(up.scale(t * 0.15 * half));
            add(streak(rgb, big ? 0.8F : 0.55F), q, dir.scale(0.05));
            if ((k & 1) == 0) add(glow(WHITE, big ? 0.3F : 0.22F), q.add(dir.scale(0.15)), Vec3.ZERO);
            if (hi() && (k % 3) == 0) add(spark(light(rgb, 0.3F), 0.2F), q.subtract(dir.scale(0.3)), dir.scale(-0.05));
        }
    }

    private static void waveBurst(KnightFxNet.Burst p) {
        boolean big = p.data() == 1;
        Vec3 dir = dirOf(p);
        flash(p.pos(), p.rgb(), big ? 1.6F : 0.9F);
        sphere(p.pos(), n(big ? 20 : 10), 0.3, spark(p.rgb(), 0.3F));
        ring(p.pos(), dir, big ? 0.8 : 0.5, n(14), glow(light(p.rgb(), 0.3F), 0.25F), 0.2);
        if (big) shake(p.pos(), 10F, 0.4F, 4);
    }

    /** Círculo de runas: anel girando (partícula de círculo) + runas flutuando. */
    static void runeCircle(Vec3 c, double r, int rgb) {
        circle(c.add(0, 0.05, 0), rgb, (float) r, 30, 3, 0, false);
        if (hi()) circle(c.add(0, 0.07, 0), GOLD, (float) r * 0.62F, 30, 2, 1, true);
        for (int i = 0; i < n(8); i++) {
            double a = RND.nextDouble() * Math.PI * 2;
            double rr = r * (0.4 + RND.nextDouble() * 0.6);
            add(rune(i % 2 == 0 ? rgb : GOLD, 0.35F), c.x + Math.cos(a) * rr, c.y + 0.2, c.z + Math.sin(a) * rr, 0, 0.04, 0);
        }
        sound(c, SoundEvents.ENCHANTMENT_TABLE_USE, 0.6F, 1.2F);
    }

    private static void holyLine(KnightFxNet.Burst p) {
        float w = Math.max(0.5F, p.size());
        line(p.pos(), p.vec(), 0.25, glow(p.rgb(), 0.22F * w), 0.0);
        if (hi()) line(p.pos(), p.vec(), 0.4, glow(WHITE, 0.15F * w), 0.0);
        add(glow(WHITE, 0.6F * w), p.vec(), Vec3.ZERO);
    }

    static void holyPillar(Vec3 base, double r, double h, int rgb) {
        pillar(base, rgb, (float) r, (float) h, 26);
        riseSpiral(base, r + 0.3, h * 0.6, n(20), RND.nextFloat() * 6F, 2.0, glow(light(rgb, 0.3F), 0.25F));
        flash(base.add(0, 1, 0), rgb, (float) r * 2F);
    }

    private static void excaliburCharge(KnightFxNet.Burst p) {
        Entity e = entity(p.entity());
        Vec3 base = e == null ? p.pos() : e.position();
        int rgb = p.rgb();
        double h = e == null ? 1.8 : e.getBbHeight();
        Vec3 top = base.add(0, h + 0.9, 0);
        // a luz desce do céu como um raio, depois envolve a lâmina erguida
        line(top.add(0, 14, 0), top, 0.3, glow(WHITE, 0.3F), 0.0);
        pillar(base, rgb, 0.8F, 16F, 30);
        circle(base.add(0, 0.05, 0), rgb, 3.0F, 30, 4, 0, false);
        if (hi()) circle(base.add(0, 0.07, 0), GOLD, 2.0F, 30, 3, 1, true);
        chargeUp(top, rgb, 1.6, 6);
        sphere(top, n(16), 0.15, glow(GOLD, 0.3F));
        flash(top, WHITE, 2.0F);
        shake(base, 18F, 0.5F, 6);
        sound(top, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 0.8F);
    }

    private static void excaliburPulse(KnightFxNet.Burst p) {
        Vec3 from = p.pos(), to = p.vec();
        Vec3 dir = to.subtract(from);
        if (dir.lengthSqr() < 1.0E-4) return;
        Vec3 d = dir.normalize();
        float r = Math.max(1F, p.size());
        boolean first = p.data() == 1;
        int rgb = p.rgb();
        // núcleo branco + bainha de luz + anéis ao longo do feixe, que avançam em 3 quadros
        double len = dir.length();
        for (int f = 0; f < 3; f++) {
            final int fr = f;
            later(f, () -> {
                Vec3 a = from.add(d.scale(len * fr / 3.0));
                Vec3 b = from.add(d.scale(len * (fr + 1) / 3.0));
                line(a, b, 0.35, glow(WHITE, 0.45F), 0.0);
                helix(a, b, r * 0.7, len / 9.0, n(36), fr * 2F, glow(rgb, 0.35F));
                if (hi()) helix(a, b, r, len / 7.0, n(24), fr * 2F + 3F, streak(light(rgb, 0.4F), 0.6F));
                for (double t = 0; t < len / 3.0; t += 3) ring(a.add(d.scale(t)), d, r, n(12), spark(rgb, 0.25F), 0.12);
            });
        }
        flash(from, rgb, r * 1.5F);
        later(3, () -> nova(to, rgb, Math.min(4F, r * 1.3F), true));
        if (first) shock(from, rgb, r * 3F, 10, d);
        shake(from, 30F, first ? 1.4F : 0.8F, 8);
    }

    private static void balmungArc(KnightFxNet.Burst p) {
        Vec3 origin = p.pos();
        Vec3 dir = dirOf(p);
        double d = Math.max(1, p.size());
        int rgb = p.rgb();
        double base = Math.atan2(dir.z, dir.x);
        int pts = n((int) Math.min(60, 10 + d * 4));
        for (int i = 0; i <= pts; i++) {
            double a = base + Math.toRadians(-70 + 140.0 * i / pts);
            double y = origin.y + 0.2 * Math.sin(i * 0.6);
            add(streak(rgb, 0.8F), origin.x + Math.cos(a) * d, y, origin.z + Math.sin(a) * d, Math.cos(a) * 0.15, 0, Math.sin(a) * 0.15);
            if (i % 2 == 0) add(glow(WHITE, 0.3F), origin.x + Math.cos(a) * (d + 0.15), y + 0.05, origin.z + Math.sin(a) * (d + 0.15), 0, 0, 0);
            if (hi() && i % 4 == 0) add(ember(light(rgb, 0.3F), 0.3F), origin.x + Math.cos(a) * d, y, origin.z + Math.sin(a) * d, 0, 0.05, 0);
        }
        if (d < 2) debris(origin.add(dir.scale(2)), n(8), 1.5, 0.3);
    }

    private static void swordLand(KnightFxNet.Burst p) {
        Vec3 land = p.pos();
        int rgb = p.rgb();
        flash(land.add(0, 0.5, 0), rgb, 1.0F);
        shock(land.add(0, 0.08, 0), rgb, 2.2F, 8, null);
        groundImpact(land, 1.6, 0x5A5A5A);
        ring(land.add(0, 0.3, 0), null, 0.4, n(10), spark(rgb, 0.25F), 0.2);
        if (hi()) cone(land, new Vec3(0, 1, 0), 0.6, n(8), 0.3, ParticleTypes.CRIT);
        shake(land, 6F, 0.3F, 3);
    }

    private static void strikeAir(KnightFxNet.Burst p) {
        Vec3 start = p.pos();
        Vec3 v = p.vec();
        double len = v.length();
        if (len < 0.5) return;
        Vec3 dir = v.normalize();
        double rad = Math.max(0.6, p.size());
        // explosão de ar na lâmina, depois o túnel avança em 7 quadros
        flash(start, WIND, 1.4F);
        shock(start, WIND, (float) rad * 2.5F, 8, dir);
        repeat(0, 7, 1, k -> {
            double d0 = len * k / 7.0, d1 = len * (k + 1) / 7.0;
            Vec3 a = start.add(dir.scale(d0));
            Vec3 b = start.add(dir.scale(d1));
            helix(a, b, rad, (d1 - d0) / 1.6, n(16), k * 1.4F, streak(WIND, 0.6F));
            if (hi()) helix(a, b, rad * 0.6, (d1 - d0) / 1.2, n(10), k * 1.4F + 2F, glow(WHITE, 0.2F));
            ring(b, dir, rad * 1.1, n(10), ParticleTypes.CLOUD, 0.05);
            if (k % 2 == 0) speedLines(a.add(b).scale(0.5), dir, rad * 1.2, n(6), 0.9, streak(WHITE, 0.4F));
        });
        shake(start, 12F, 0.6F, 6);
    }

    private static void aegisUp(KnightFxNet.Burst p) {
        Entity e = entity(p.entity());
        Vec3 c = center(e, p.pos());
        // placas de pedra orbitam e se encaixam no corpo
        for (int i = 0; i < 6; i++) {
            final int k = i;
            later(i, () -> {
                Entity cur = entity(p.entity());
                Vec3 cc = center(cur, c);
                double a = k * Math.PI / 3;
                Vec3 q = cc.add(Math.cos(a) * 0.9, (k % 2 == 0 ? 0.3 : -0.3), Math.sin(a) * 0.9);
                sphere(q, n(4), 0.04, glow(GOLD, 0.25F));
                add(dust(STONE, 1.6F), q, cc.subtract(q).scale(0.15));
            });
        }
        ring(c, null, 1.0, n(16), glow(GOLD, 0.25F), -0.05);
        shock(c, GOLD, 2.0F, 8, new Vec3(0, 1, 0));
    }

    private static void aegisHit(KnightFxNet.Burst p) {
        Entity e = entity(p.entity());
        Vec3 c = center(e, p.pos());
        // hexágono dourado na hora do bloqueio + onda
        ring(c, p.vec().lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : p.vec(), 0.9, 6, glow(GOLD, 0.45F), 0.0);
        shock(c, GOLD, 1.8F, 6, p.vec().lengthSqr() < 1.0E-4 ? new Vec3(0, 1, 0) : p.vec());
        sphere(c, n(8), 0.2, spark(GOLD, 0.25F));
    }

    private static void aegisBreak(KnightFxNet.Burst p) {
        Entity e = entity(p.entity());
        Vec3 c = center(e, p.pos());
        float r = Math.max(2F, p.size());
        nova(c, GOLD, r * 0.8F, false);
        shock((e == null ? p.pos() : e.position()).add(0, 0.1, 0), GOLD, r * 2F, 12, null);
        sphere(c, n(18), 0.45, dust(STONE, 1.8F));
        runeCircle(e == null ? p.pos() : e.position(), r, GOLD);
    }

    private static void judgment(KnightFxNet.Burst p) {
        Vec3 base = p.pos();
        // a cruz sagrada se desenha no ar, cai e explode em luz
        Vec3 top = base.add(0, 5, 0);
        line(top, top.add(0, 2.5, 0), 0.12, glow(GOLD, 0.35F), 0.0);
        line(top.add(-1, 1.7, 0), top.add(1, 1.7, 0), 0.12, glow(GOLD, 0.35F), 0.0);
        later(4, () -> {
            line(base.add(0, 4.5, 0), base, 0.2, glow(WHITE, 0.4F), 0.0);
            line(base.add(-0.9, 2.2, 0), base.add(0.9, 2.2, 0), 0.15, glow(WHITE, 0.35F), 0.0);
            pillar(base, GOLD, 0.6F, 6F, 20);
        });
        later(6, () -> {
            nova(base.add(0, 1, 0), GOLD, 1.8F, true);
            runeCircle(base, 2.2, GOLD);
        });
        sound(base, SoundEvents.BELL_BLOCK, 1.0F, 1.6F);
    }

    private static void antiMagic(KnightFxNet.Burst p) {
        Vec3 c = p.pos();
        Vec3 dir = dirOf(p);
        // corte negro com borda verde, fumaça engolindo a magia, runas se quebrando
        crescent(c, dir, 1.5, 0x101010, true);
        later(1, () -> crescent(c.add(0, 0.25, 0), dir, 1.15, 0x5BE37A, false));
        sphere(c, n(14), 0.12, smoke(0x101010, 1.6F));
        if (hi()) sphere(c, n(10), 0.25, rune(0x5BE37A, 0.3F));
        shock(c, 0x5BE37A, 2.0F, 8, dir);
        sound(c, SoundEvents.WITHER_HURT, 0.4F, 0.6F);
    }

    private static void runeMark(KnightFxNet.Burst p) {
        Entity e = entity(p.entity());
        Vec3 c = center(e, p.pos());
        ring(c, null, 0.7, n(10), rune(RUNE, 0.35F), 0.0);
        star(c, new Vec3(0, 0, 1), 0.6, 6, glow(RUNE, 0.2F), 0.08);
        add(glow(WHITE, 0.6F), c, Vec3.ZERO);
    }

    private static void caliburn(KnightFxNet.Burst p) {
        Vec3 c = p.pos();
        // a lâmina dourada desce do céu em 3 quadros e crava
        repeat(0, 3, 1, k -> line(c.add(0, 7 - k * 2, 0), c.add(0, 5 - k * 2, 0), 0.15, glow(GOLD, 0.35F), 0.0));
        later(3, () -> {
            flash(c, GOLD, 2.0F);
            pillar(c.subtract(0, 1, 0), GOLD, 0.5F, 5F, 18);
            shock(c, GOLD, 2.5F, 8, new Vec3(0, 1, 0));
            sphere(c, n(14), 0.25, spark(GOLD, 0.3F));
            shake(c, 8F, 0.4F, 4);
        });
    }

    private KnightFxClient() {}
}
