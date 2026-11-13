package com.renanpontes.isekaimahou.feature.class_ranger.client;

import static com.renanpontes.isekaimahou.feature.class_ranger.client.FxKit.*;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.renanpontes.isekaimahou.feature.class_ranger.RangerFxNet;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * SÓ CLIENTE. Coreografia de anime do Caçador: rastro de vento em espiral nas flechas (dourado nas carregadas), clarão
 * do disparo no máximo, a chuva de flechas (círculo de mira + flechas cravando), flecha explosiva, a coluna da Flecha
 * Sagrada, as correntes conjuradas e o mergulho da águia das Mil Penas.
 */
public final class RangerFxClient {
    private static final int GREEN = 0x6BCB5A;
    private static final int WIND = 0xD8FFE0;
    private static final int GOLD = 0xFFD86B;
    private static final int SACRED = 0xFF9AD8;
    private static final int WHITE = 0xFFFFFF;

    /** Flechas com rastro: id → (cor, estilo, tick de início). */
    private record Trail(int rgb, int style, long start) {}

    private static final Map<Integer, Trail> TRAILS = new HashMap<>();
    private static long clock;

    public static void init() {
        RangerFxNet.clientBurst = RangerFxClient::onBurst;
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
            FxKit.tick();
            tickTrails();
        });
    }

    /** Teto de flechas com rastro ao mesmo tempo (chuvas grandes não pesam). */
    private static int maxTrails() {
        return ultra() ? 64 : hi() ? 40 : mid() ? 20 : 8;
    }

    private static void tickTrails() {
        if (Minecraft.getInstance().isPaused()) return;
        clock++;
        if (TRAILS.isEmpty()) return;
        if (level() == null) {
            TRAILS.clear();
            return;
        }
        Iterator<Map.Entry<Integer, Trail>> it = TRAILS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Trail> en = it.next();
            Trail t = en.getValue();
            Entity e = entity(en.getKey());
            long age = clock - t.start;
            if (e == null) {
                // o pacote pode chegar antes da flecha: espera um pouco
                if (age > 20) it.remove();
                continue;
            }
            if (e.isRemoved() || age > 200 || (e instanceof AbstractArrow a && a.isInWater())) {
                it.remove();
                continue;
            }
            Vec3 v = e.getDeltaMovement();
            Vec3 p = e.position();
            if (e instanceof AbstractArrow a && (v.lengthSqr() < 1.0E-3 || a.onGround() || isStuck(a))) {
                // cravou: um último sopro de vento e acabou
                ring(p, new Vec3(0, 1, 0), 0.3, n(8), glow(t.rgb, 0.2F), 0.08);
                it.remove();
                continue;
            }
            if (!near(p)) continue;
            Vec3 back = p.subtract(v.scale(1.0));
            float ph = (float) (age * 0.9);
            // espiral de vento em volta do eixo de voo + risco central
            helix(back, p, t.style == 1 ? 0.25 : 0.16, 1.0, hi() ? 6 : 3, ph, streak(t.style == 1 ? GOLD : WIND, 0.35F));
            if (age % 2 == 0) add(glow(t.rgb, t.style == 1 ? 0.3F : 0.18F), p, Vec3.ZERO);
            if (t.style == 1 && hi() && age % 3 == 0) ring(p, v, 0.35, n(8), spark(GOLD, 0.2F), 0.02);
        }
    }

    private static boolean isStuck(AbstractArrow a) {
        return a.getDeltaMovement().lengthSqr() < 1.0E-4;
    }

    private static void onBurst(RangerFxNet.Burst p) {
        if (level() == null) return;
        if (p.kind() == RangerFxNet.ARROW_TRAIL) {
            if (TRAILS.size() < maxTrails() && near(p.pos())) {
                TRAILS.put(p.entity(), new Trail(p.data() == 1 ? GOLD : (p.rgb() == 0 ? GREEN : p.rgb()), p.data(), clock));
            }
            return;
        }
        if (!near(p.pos())) return;
        try {
            switch (p.kind()) {
                case RangerFxNet.CHARGED_RELEASE -> chargedRelease(p);
                case RangerFxNet.HAND_FLARE -> handFlare(p);
                case RangerFxNet.EXPLOSIVE -> nova(p.pos(), p.rgb(), Math.max(1F, p.size() * 0.7F), true);
                case RangerFxNet.RAIN_MARK -> rainMark(p);
                case RangerFxNet.RAIN_LAND -> rainLand(p);
                case RangerFxNet.SACRED -> sacred(p);
                case RangerFxNet.CHAINS -> chains(p);
                case RangerFxNet.FEATHERS_START -> feathersStart(p);
                case RangerFxNet.FEATHERS_DIVE -> feathersDive(p);
                case RangerFxNet.LEARN -> learn(p);
                case RangerFxNet.NET -> net(p);
                default -> {}
            }
        } catch (RuntimeException ignored) {
            // efeito quebrado nunca derruba o cliente
        }
    }

    private static void chargedRelease(RangerFxNet.Burst p) {
        Vec3 at = p.pos();
        Vec3 dir = p.vec().lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : p.vec().normalize();
        // clarão dourado na corda, anel de vento empurrado para trás, linhas de velocidade à frente
        flash(at, GOLD, 0.9F);
        shock(at.add(dir.scale(0.3)), WIND, 1.8F, 7, dir);
        helix(at, at.add(dir.scale(3)), 0.3, 2, n(20), 0F, streak(WIND, 0.4F));
        if (hi()) speedLines(at.add(dir.scale(2)), dir, 0.4, n(6), 1.0, streak(WHITE, 0.4F));
        sound(at, SoundEvents.TRIDENT_RIPTIDE_1.value(), 0.5F, 1.8F);
    }

    private static void handFlare(RangerFxNet.Burst p) {
        Vec3 at = p.pos();
        Vec3 dir = p.vec().lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : p.vec().normalize();
        add(glow(GREEN, 0.5F), at, Vec3.ZERO);
        cone(at, dir, 0.4, n(6), 0.2, ParticleTypes.CHERRY_LEAVES);
        ring(at, dir, 0.25 + p.data() * 0.05, n(8), streak(WIND, 0.3F), 0.1);
    }

    private static void rainMark(RangerFxNet.Burst p) {
        Vec3 g = p.pos();
        float r = Math.max(2F, p.size());
        circle(g.add(0, 0.06, 0), p.rgb(), r, 50, 3, 0, false);
        if (hi()) circle(g.add(0, 0.08, 0), WIND, r * 0.55F, 50, 2, 1, true);
        // no céu, um círculo de mira gêmeo
        circleFacing(g.add(0, 12, 0), new Vec3(0, -1, 0), p.rgb(), r * 0.8F, 40, 2, 0, true);
    }

    private static void rainLand(RangerFxNet.Burst p) {
        Vec3 at = p.pos();
        add(glow(p.rgb(), 0.3F), at.add(0, 0.1, 0), Vec3.ZERO);
        debris(at, n(3), 0.2, 0.2);
        if (hi()) ring(at.add(0, 0.05, 0), null, 0.2, n(6), streak(WIND, 0.25F), 0.08);
    }

    private static void sacred(RangerFxNet.Burst p) {
        Vec3 at = p.pos();
        float r = Math.max(1.5F, p.size());
        boolean strong = p.data() == 1;
        pillar(at, SACRED, 0.7F, strong ? 9F : 6F, 26);
        circle(at.add(0, 0.05, 0), SACRED, r, 30, 3, 0, false);
        riseSpiral(at, r * 0.5, 3.2, n(26), 0F, 2.0, glow(WHITE, 0.25F));
        for (int i = 0; i < n(14); i++) {
            double a = RND.nextDouble() * Math.PI * 2, d = RND.nextDouble() * r;
            add(mote(i % 2 == 0 ? SACRED : WHITE, 0.25F), at.x + Math.cos(a) * d, at.y + 0.2, at.z + Math.sin(a) * d, 0, 0.05, 0);
        }
        flash(at.add(0, 1, 0), SACRED, strong ? 2.2F : 1.4F);
        if (strong) shake(at, 10F, 0.5F, 5);
    }

    private static void chains(RangerFxNet.Burst p) {
        Entity t = entity(p.entity());
        Vec3 c = center(t, p.pos());
        double w = t == null ? 0.7 : Math.max(0.7, t.getBbWidth());
        // correntes douradas surgem do chão em volta e se fecham no corpo
        for (int i = 0; i < 4; i++) {
            final int k = i;
            later(i, () -> {
                double a = k * Math.PI / 2 + 0.4;
                Vec3 from = c.add(Math.cos(a) * 2.2, -1.0, Math.sin(a) * 2.2);
                line(from, c, 0.18, glow(0xC8CCD6, 0.22F), 0);
                if (hi()) line(from, c, 0.36, spark(GOLD, 0.18F), 0);
            });
        }
        later(4, () -> {
            ring(c.add(0, -0.3, 0), null, w, n(14), glow(GOLD, 0.25F), 0);
            ring(c.add(0, 0.3, 0), null, w, n(14), glow(GOLD, 0.25F), 0);
            add(glow(GOLD, 0.8F), c, Vec3.ZERO);
        });
    }

    private static void feathersStart(RangerFxNet.Burst p) {
        Vec3 c = p.pos();
        float r = Math.max(4F, p.size());
        circle(c.add(0, 0.05, 0), p.rgb(), r, 80, 4, 0, false);
        if (hi()) circle(c.add(0, 0.07, 0), WIND, r * 0.6F, 80, 3, 1, true);
        // penas de luz subindo em espiral (a águia ganhando altura)
        repeat(0, 8, 3, i -> riseSpiral(c, r * 0.4, 10, n(14), i * 0.8F, 1.5, glow(i % 2 == 0 ? p.rgb() : WHITE, 0.3F)));
    }

    private static void feathersDive(RangerFxNet.Burst p) {
        Vec3 c = p.pos();
        float r = Math.max(4F, p.size());
        // a águia mergulha: um risco de luz do céu, depois o impacto enorme
        line(c.add(0, 18, 0), c.add(0, 1, 0), 0.4, streak(p.rgb(), 0.9F), 0.2);
        later(2, () -> {
            nova(c.add(0, 0.5, 0), p.rgb(), r * 0.45F, true);
            shock(c.add(0, 0.12, 0), WIND, r * 2F, 14, null);
            for (int i = 0; i < n(30); i++) {
                double a = RND.nextDouble() * Math.PI * 2;
                double s = 0.2 + RND.nextDouble() * 0.4;
                add(mote(i % 2 == 0 ? p.rgb() : WHITE, 0.3F), c.add(0, 0.8, 0), new Vec3(Math.cos(a) * s, 0.15 + RND.nextDouble() * 0.2, Math.sin(a) * s));
            }
        });
    }

    private static void learn(RangerFxNet.Burst p) {
        Vec3 c = p.pos();
        riseSpiral(c, 1.1, 2.4, n(36), 0F, 3.0, streak(GREEN, 0.4F));
        sphere(c.add(0, 1.2, 0), n(16), 0.15, ParticleTypes.CHERRY_LEAVES);
        flash(c.add(0, 1, 0), GREEN, 1.2F);
    }

    private static void net(RangerFxNet.Burst p) {
        Vec3 c = p.pos();
        float r = Math.max(1.5F, p.size());
        ring(c, null, r, n(20), glow(0xE8E4D8, 0.22F), 0);
        ring(c, null, r * 0.55, n(12), glow(p.rgb(), 0.2F), 0);
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 4;
            Vec3 off = new Vec3(Math.cos(a) * r, 0, Math.sin(a) * r);
            line(c.add(off), c.subtract(off), 0.3, glow(0xD8D2C0, 0.18F), 0);
        }
    }

    private RangerFxClient() {}
}
