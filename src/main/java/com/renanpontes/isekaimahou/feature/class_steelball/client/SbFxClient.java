package com.renanpontes.isekaimahou.feature.class_steelball.client;

import static com.renanpontes.isekaimahou.feature.class_steelball.client.FxKit.*;

import com.renanpontes.isekaimahou.feature.class_steelball.SbFx;
import com.renanpontes.isekaimahou.feature.class_steelball.SbFxNet;
import com.renanpontes.isekaimahou.feature.class_steelball.entity.SpinNailEntity;
import com.renanpontes.isekaimahou.feature.class_steelball.entity.SteelBallEntity;
import com.renanpontes.isekaimahou.feature.class_steelball.entity.TuskNailEntity;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * SÓ CLIENTE. A coreografia de anime da Rotação: cada {@link SbFxNet.Burst} vira "antecipação → impacto → rescaldo"
 * (anéis dourados girando na mão, espiral de perfuração, Retângulo Dourado, a esfera espectral do Ball Breaker, a
 * cortina de luz do Love Train...). Também desenha os rastros de voo da bola e das unhas a cada tick, localmente.
 */
public final class SbFxClient {
    private static final int GOLD = SbFx.GOLD;
    private static final int STEEL = SbFx.STEEL;
    private static final int NAIL = SbFx.NAIL;
    private static final int HEAL = SbFx.HEAL;
    private static final int AMBER = 0xFFB43A;
    private static final int VIOLET = 0xB45CFF;
    private static final int WHITE = 0xFFFFFF;
    private static final double PHI = 1.6180339887;

    public static void init() {
        SbFxNet.clientBurst = SbFxClient::onBurst;
        SbFxNet.clientTrail = SbFxClient::trail;
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> FxKit.tick());
    }

    // =====================================================================================
    //  Rastros de voo (a cada tick, no cliente)
    // =====================================================================================
    private static void trail(Entity e) {
        Vec3 p = e.position().add(0, e.getBbHeight() * 0.5, 0);
        if (!near(p)) return;
        Vec3 v = e.getDeltaMovement();
        if (v.lengthSqr() < 1.0E-4) v = e.getLookAngle();
        float ph = (e.tickCount + e.getId()) * 0.9F;
        if (e instanceof SteelBallEntity b) {
            int rgb = b.golden() ? GOLD : b.color();
            // duas faixas em hélice em volta da bola: a rotação visível
            Vec3 back = p.subtract(v.scale(0.9));
            helix(back, p, 0.22, 1.0, hi() ? 6 : 3, ph, spark(rgb, 0.35F));
            if (e.tickCount % 2 == 0) helix(back, p, 0.22, 1.0, hi() ? 6 : 3, ph + (float) Math.PI, glow(light(rgb, 0.4F), 0.25F));
            if (b.golden() && e.tickCount % 3 == 0) ring(p, v, 0.45, n(10), glow(GOLD, 0.3F), 0.0);
            if (b.returning() && e.tickCount % 2 == 0) add(streak(STEEL, 0.6F), p, v.scale(-0.2));
            return;
        }
        boolean gold;
        int rgb;
        if (e instanceof TuskNailEntity t) {
            gold = t.golden();
            rgb = gold ? GOLD : NAIL;
        } else if (e instanceof SpinNailEntity s) {
            gold = s.golden();
            rgb = gold ? GOLD : NAIL;
        } else return;
        // unha: risco fino + espiral apertada (o giro de broca) + brilho da ponta
        Vec3 back = p.subtract(v.scale(1.2));
        add(streak(rgb, 0.5F), p, v.scale(0.05));
        helix(back, p, 0.12, 2.0, hi() ? 8 : 4, ph, glow(rgb, 0.22F));
        if (gold && e.tickCount % 2 == 0) add(glow(WHITE, 0.4F), p, Vec3.ZERO);
        if (hi() && e.tickCount % 4 == 0) star(p, v, 0.3, 4, spark(light(rgb, 0.5F), 0.2F), 0.1);
    }

    // =====================================================================================
    //  Bursts
    // =====================================================================================
    private static void onBurst(SbFxNet.Burst p) {
        if (level() == null || !near(p.pos())) return;
        try {
            switch (p.kind()) {
                case SbFxNet.BALL_THROW -> ballThrow(p);
                case SbFxNet.BALL_HIT -> ballHit(p);
                case SbFxNet.NAIL_FIRE -> nailFire(p);
                case SbFxNet.NAIL_HIT -> nailHit(p);
                case SbFxNet.CHASE_MARK -> chaseMark(p);
                case SbFxNet.WORMHOLE -> wormhole(p);
                case SbFxNet.BREAKER_CHARGE -> breakerCharge(p);
                case SbFxNet.BREAKER_HIT -> breakerHit(p);
                case SbFxNet.LOVE_TRAIN -> loveTrain(p);
                case SbFxNet.INFINITE -> infinite(p);
                case SbFxNet.VORTEX -> vortex(p);
                case SbFxNet.GOLDEN_RECT -> goldenRect(p.pos(), p.vec(), p.size(), 0);
                case SbFxNet.SPIN_HEAL -> spinHeal(p);
                case SbFxNet.SCAN -> scan(p);
                case SbFxNet.GALLOP -> gallop(p);
                case SbFxNet.LEARN -> learn(p);
                case SbFxNet.SPIRAL -> riseSpiral(p.pos(), Math.max(0.3, p.size()), Math.max(0.5, p.vec().y), n(18), RND.nextFloat() * 6F, 3.0,
                        glow(p.rgb(), 0.25F));
                case SbFxNet.RING -> {
                    ring(p.pos(), null, Math.max(0.2, p.size()), n((int) Math.min(32, 8 + p.size() * 6)), glow(p.rgb(), 0.22F), 0.0);
                    if (hi()) ring(p.pos(), null, Math.max(0.2, p.size()) * 0.9, n(6), spark(light(p.rgb(), 0.4F), 0.18F), 0.04);
                }
                default -> {}
            }
        } catch (RuntimeException ignored) {
            // efeito quebrado nunca derruba o cliente
        }
    }

    /** Retângulo Dourado: espiral áurea (quartos de círculo crescendo por φ) desenhada aos poucos, de frente para {@code facing}. */
    static void goldenRect(Vec3 c, Vec3 facing, float size, int delay) {
        Vec3 f = facing.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : facing.normalize();
        Vec3[] b = basis(f);
        double a = size / Math.pow(PHI, 4);
        int pts = hi() ? 64 : 36;
        int steps = 4;
        repeat(delay, steps, 1, k -> {
            for (int i = k * pts / steps; i <= (k + 1) * pts / steps; i++) {
                double th = i / (double) pts * Math.PI * 2;
                double r = a * Math.pow(PHI, 2 * th / Math.PI);
                Vec3 q = c.add(b[0].scale(Math.cos(th) * r)).add(b[1].scale(Math.sin(th) * r));
                add(glow(i % 6 == 0 ? WHITE : GOLD, 0.32F), q, Vec3.ZERO);
            }
        });
        // as linhas do retângulo (só no Alto): as proporções áureas aparecem por um instante
        if (hi()) {
            later(delay + steps, () -> {
                double w = size * 0.95, h = w / PHI;
                Vec3 r = b[0].scale(w * 0.5), u = b[1].scale(h * 0.5);
                ParticleOptions o = spark(GOLD, 0.18F);
                line(c.add(r).add(u), c.subtract(r).add(u), 0.18, o, 0);
                line(c.subtract(r).add(u), c.subtract(r).subtract(u), 0.18, o, 0);
                line(c.subtract(r).subtract(u), c.add(r).subtract(u), 0.18, o, 0);
                line(c.add(r).subtract(u), c.add(r).add(u), 0.18, o, 0);
            });
        }
        later(delay + steps, () -> flash(c, GOLD, size * 0.6F));
    }

    private static void ballThrow(SbFxNet.Burst p) {
        Vec3 dir = p.vec();
        boolean gold = p.size() >= 2;
        int rgb = gold ? GOLD : (p.rgb() == 0 ? STEEL : p.rgb());
        Entity owner = entity(p.entity());
        // antecipação: dois anéis girando em volta da mão, cruzados
        repeat(0, 3, 1, i -> {
            Vec3 h = owner == null ? p.pos() : hand(owner, p.pos());
            ring(h, dir, 0.35 - i * 0.05, n(10), spark(rgb, 0.25F), 0.0);
            ring(h, new Vec3(0, 1, 0), 0.3 - i * 0.04, n(8), glow(light(rgb, 0.3F), 0.22F), 0.0);
        });
        later(3, () -> {
            Vec3 h = p.pos();
            add(glow(WHITE, 0.9F), h, Vec3.ZERO);
            cone(h, dir, 0.25, n(10), 0.55, streak(rgb, 0.5F));
            ring(h.add(dir.scale(0.4)), dir, 0.25, n(12), spark(rgb, 0.25F), 0.12);
            if (gold) goldenRect(h.add(dir.scale(1.0)), dir, 0.9F, 0);
            soundVar(h, SoundEvents.TRIDENT_THROW.value(), 0.7F, 1.5F, 0.08F);
            sound(h, SoundEvents.CHAIN_HIT, 0.5F, 1.9F);
            if (gold) sound(h, SoundEvents.AMETHYST_BLOCK_CHIME, 0.9F, 1.4F);
        });
    }

    private static void ballHit(SbFxNet.Burst p) {
        Vec3 at = p.pos();
        Vec3 dir = p.vec().lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : p.vec().normalize();
        float force = Math.max(1F, p.size());
        boolean gold = p.rgb() == GOLD;
        int rgb = gold ? GOLD : STEEL;
        // impacto: clarão + espiral de perfuração entrando no alvo + faíscas de aço
        flash(at, gold ? GOLD : WHITE, 0.9F + force * 0.4F);
        helix(at.subtract(dir.scale(0.8)), at.add(dir.scale(0.8)), 0.35 * force, 2.5, n(22), RND.nextFloat() * 6F, spark(rgb, 0.3F));
        sphere(at, n(14 + (int) (6 * force)), 0.35, spark(light(rgb, 0.3F), 0.3F));
        cone(at, dir.scale(-1), 0.7, n(8), 0.4, ParticleTypes.CRIT);
        shock(at, rgb, 1.2F + force * 0.6F, 8, dir);
        if (hi()) ring(at, dir, 0.6, n(16), glow(rgb, 0.3F), 0.18);
        if (force >= 2) shake(at, 10F, 0.5F * force, 6);
        // rescaldo: a espiral continua girando um pouco no lugar
        repeat(2, 4, 2, i -> ring(at, dir, 0.25 + i * 0.12, n(8), glow(rgb, 0.22F), 0.0));
        sound(at, SoundEvents.ANVIL_LAND, 0.35F, 1.9F);
        soundVar(at, SoundEvents.CHAIN_BREAK, 0.6F, 1.5F, 0.1F);
        if (gold) sound(at, SoundEvents.BELL_BLOCK, 0.5F, 1.8F);
    }

    private static void nailFire(SbFxNet.Burst p) {
        Vec3 at = p.pos();
        Vec3 dir = p.vec();
        int act = Math.max(1, (int) p.size());
        int rgb = act == 2 || act >= 4 ? GOLD : (act == 3 ? VIOLET : NAIL);
        // estrela da unha na ponta do dedo
        star(at, dir, 0.35 + act * 0.08, 8, glow(rgb, 0.2F), 0.07);
        add(glow(WHITE, 0.5F), at, Vec3.ZERO);
        ring(at, dir, 0.18, n(8), spark(rgb, 0.2F), 0.14);
        if (hi()) speedLines(at.add(dir.scale(1.5)), dir, 0.3, n(5), 0.8, streak(rgb, 0.4F));
        soundVar(at, SoundEvents.ARROW_SHOOT, 0.4F, 2.0F, 0.05F);
        sound(at, SoundEvents.AMETHYST_BLOCK_HIT, 0.35F, 1.9F);
    }

    private static void nailHit(SbFxNet.Burst p) {
        Vec3 at = p.pos();
        Vec3 dir = p.vec().lengthSqr() < 1.0E-4 ? new Vec3(0, 1, 0) : p.vec().normalize();
        int act = Math.max(1, (int) p.size());
        int rgb = act == 2 || act >= 4 ? GOLD : NAIL;
        // o furo em espiral: anéis que fecham girando
        add(glow(WHITE, 0.6F), at, Vec3.ZERO);
        implode(at, dir, 0.55, 4, 6, glow(rgb, 0.22F));
        sphere(at, n(8), 0.25, spark(rgb, 0.22F));
        if (act >= 2) goldenRect(at.subtract(dir.scale(0.3)), dir, 0.9F, 1);
        sound(at, SoundEvents.AMETHYST_BLOCK_HIT, 0.6F, 1.9F);
        if (act >= 2) sound(at, SoundEvents.TRIDENT_RIPTIDE_2.value(), 0.5F, 1.6F);
    }

    private static void chaseMark(SbFxNet.Burst p) {
        // o buraco que persegue: espiral roxa/dourada no chão aos pés do alvo por ~1,5 s
        repeat(0, 10, 3, i -> {
            Entity t = entity(p.entity());
            Vec3 c = t == null ? p.pos() : t.position().add(0, 0.06, 0);
            float ph = i * 0.8F;
            for (int k = 0; k < 3; k++) {
                double r = 0.25 + k * 0.25;
                double a = ph + k * 2.1;
                add(glow(k == 1 ? GOLD : VIOLET, 0.25F), c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r, 0, 0.01, 0);
            }
            if (i % 3 == 0) ring(c, null, 0.8, n(10), spark(GOLD, 0.2F), -0.03);
        });
        sound(p.pos(), SoundEvents.GRINDSTONE_USE, 0.4F, 1.9F);
    }

    private static void wormhole(SbFxNet.Burst p) {
        Vec3 in = p.pos();
        Vec3 out = p.vec();
        // entrada: o espaço é sugado num redemoinho
        circle(in.add(0, 0.05, 0), VIOLET, 1.4F, 16, 3, 1, true);
        implode(in.add(0, 1.0, 0), new Vec3(0, 1, 0), 1.4, 6, 8, glow(VIOLET, 0.3F));
        if (hi()) sphere(in.add(0, 1, 0), n(16), -0.15, ParticleTypes.REVERSE_PORTAL);
        sound(in, SoundEvents.PORTAL_TRIGGER, 0.3F, 2.0F);
        if (out.lengthSqr() < 1.0E-4) return;
        // saída (8 ticks depois): explosão em espiral + clarão
        later(8, () -> {
            flash(out.add(0, 1, 0), VIOLET, 2.2F);
            riseSpiral(out, 1.6, 2.6, n(36), RND.nextFloat() * 6F, 2.0, glow(VIOLET, 0.3F));
            riseSpiral(out, 1.1, 2.0, n(22), RND.nextFloat() * 6F, 2.0, spark(GOLD, 0.25F));
            shock(out.add(0, 0.1, 0), VIOLET, 4.0F, 10, null);
            groundImpact(out, 3.0, 0x6A3A8A);
            shake(out, 12F, 0.8F, 8);
            sound(out, SoundEvents.CHORUS_FRUIT_TELEPORT, 1.0F, 0.7F);
            sound(out, SoundEvents.TRIDENT_RIPTIDE_3.value(), 0.8F, 1.3F);
        });
    }

    private static void breakerCharge(SbFxNet.Burst p) {
        float r = Math.max(1F, p.size());
        // a esfera espectral gigante se forma: casca de luz âmbar sendo sugada para a forma de bola + zumbido subindo
        repeat(0, 8, 2, i -> {
            Entity t = entity(p.entity());
            Vec3 c = (t == null ? p.pos() : t.position().add(0, t.getBbHeight() + r * 0.9, 0));
            double rr = r * (1.6 - i * 0.075);
            int pts = n(hi() ? 26 : 14);
            for (int k = 0; k < pts; k++) {
                Vec3 d = new Vec3(RND.nextGaussian(), RND.nextGaussian(), RND.nextGaussian()).normalize();
                add(glow(k % 4 == 0 ? WHITE : AMBER, 0.35F), c.add(d.scale(rr)), d.scale(-0.05));
            }
            ring(c, new Vec3(Math.sin(i), 1, Math.cos(i)), rr, n(14), spark(GOLD, 0.22F), 0.0);
            if (i % 2 == 0) sound(c, SoundEvents.BEACON_AMBIENT, 0.6F, 0.6F + i * 0.12F);
        });
        sound(p.pos(), SoundEvents.BEACON_ACTIVATE, 1.0F, 0.6F);
    }

    private static void breakerHit(SbFxNet.Burst p) {
        Vec3 at = p.pos();
        float r = Math.max(1F, p.size());
        // a bola espectral cai: clarão enorme, anel de choque, espiral dourada e poeira
        flash(at, AMBER, r * 2.4F);
        later(1, () -> flash(at, WHITE, r * 1.4F));
        shock(at.add(0, 0.1, 0), AMBER, r * 4F, 12, null);
        if (hi()) shock(at.add(0, 0.1, 0), GOLD, r * 2.5F, 8, null);
        sphere(at, n(40), 0.5, spark(AMBER, 0.35F));
        sphere(at, n(18), 0.25, glow(GOLD, 0.45F));
        goldenRect(at.add(0, r, 0), new Vec3(0, 0, 1), r * 1.6F, 2);
        groundImpact(at, r * 3, 0xC8B89A);
        shake(at, 16F, 1.2F, 10);
        later(3, () -> riseSpiral(at, r * 1.2, r * 2.0, n(30), 0F, 2.5, ember(AMBER, 0.4F)));
        sound(at, SoundEvents.GENERIC_EXPLODE.value(), 0.8F, 1.3F);
        sound(at, SoundEvents.AMETHYST_BLOCK_BREAK, 1.0F, 0.5F);
        sound(at, SoundEvents.ANVIL_LAND, 0.6F, 0.7F);
    }

    private static void loveTrain(SbFxNet.Burst p) {
        Vec3 c = p.pos();
        float r = Math.max(2F, p.size());
        // antecipação: batida do coração + anéis no chão
        circle(c.add(0, 0.05, 0), GOLD, r, 60, 4, 0, false);
        if (hi()) circle(c.add(0, 0.07, 0), 0xFFF2B0, r * 0.6F, 60, 2, 1, true);
        // a cortina de luz desce: colunas verticais em volta, de cima para baixo, por 2 s
        repeat(4, 10, 4, i -> {
            Entity holder = entity(p.entity());
            Vec3 cc = holder == null ? c : holder.position();
            int cols = n(hi() ? 22 : 12);
            for (int k = 0; k < cols; k++) {
                double a = Math.PI * 2 * k / cols + i * 0.11;
                double x = cc.x + Math.cos(a) * r, z = cc.z + Math.sin(a) * r;
                for (int y = 0; y < 5; y++) {
                    double yy = cc.y + 4.5 - y * 1.0 - (i % 2) * 0.5;
                    add(glow(y % 2 == 0 ? GOLD : 0xFFF2B0, 0.3F), x, yy, z, 0, -0.08, 0);
                }
            }
            if (i % 3 == 0) sound(cc, SoundEvents.AMETHYST_BLOCK_CHIME, 0.7F, 1.2F + i * 0.05F);
        });
        pillar(c, GOLD, 1.0F, 8F, 30);
        flash(c.add(0, 1, 0), GOLD, r);
        shake(c, r * 3, 0.6F, 10);
        for (int beat = 0; beat < 3; beat++) {
            final int b = beat;
            later(b * 8, () -> sound(c, SoundEvents.WARDEN_HEARTBEAT, 1.4F, 0.9F + b * 0.08F));
        }
        sound(c, SoundEvents.BELL_RESONATE, 1.0F, 1.4F);
    }

    private static void infinite(SbFxNet.Burst p) {
        float r = Math.max(1F, p.size());
        // a espiral dourada fecha no alvo, por 1 s; depois o clarão
        repeat(0, 10, 2, i -> {
            Entity t = entity(p.entity());
            Vec3 c = t == null ? p.pos() : t.position();
            double h = t == null ? 2.0 : t.getBbHeight() + 0.4;
            double rr = r * 1.8 * (1.0 - i / 12.0);
            riseSpiral(c, rr, h, n(18), i * 0.9F, 1.5, glow(i % 2 == 0 ? GOLD : WHITE, 0.3F));
            if (hi()) ring(c.add(0, h * 0.5, 0), null, rr * 0.8, n(12), spark(GOLD, 0.2F), -0.05);
        });
        later(20, () -> {
            Entity t = entity(p.entity());
            Vec3 c = t == null ? p.pos() : t.position().add(0, t.getBbHeight() * 0.5, 0);
            flash(c, GOLD, r * 1.6F);
            shock(c, GOLD, r * 3F, 10, new Vec3(0, 1, 0));
            goldenRect(c, new Vec3(0, 0, 1), 1.4F * r, 0);
            sound(c, SoundEvents.AMETHYST_BLOCK_BREAK, 1.0F, 0.7F);
        });
        sound(p.pos(), SoundEvents.BEACON_ACTIVATE, 1.0F, 1.6F);
    }

    private static void vortex(SbFxNet.Burst p) {
        Vec3 c = p.pos();
        float r = Math.max(1F, p.size());
        int rgb = p.rgb() == 0 ? STEEL : p.rgb();
        repeat(0, 6, 2, i -> {
            double rr = r * (0.3 + i * 0.14);
            ring(c.add(0, 0.15 + i * 0.12, 0), null, rr, n(16), streak(rgb, 0.5F), 0.12);
            if (hi()) ring(c.add(0, 0.6 + i * 0.2, 0), null, rr * 0.7, n(10), glow(light(rgb, 0.3F), 0.25F), 0.05);
        });
        shock(c.add(0, 0.1, 0), rgb, r * 2, 10, null);
        debris(c, n(10), r * 0.5, 0.3);
        sound(c, SoundEvents.TRIDENT_RIPTIDE_1.value(), 0.8F, 1.5F);
    }

    private static void spinHeal(SbFxNet.Burst p) {
        Entity t = entity(p.entity());
        Vec3 base = t == null ? p.pos() : t.position();
        double h = t == null ? 2.0 : t.getBbHeight() + 0.2;
        repeat(0, 4, 3, i -> {
            riseSpiral(base, 0.8, h, n(16), i * 1.5F, 2.0, glow(HEAL, 0.28F));
            if (hi()) riseSpiral(base, 0.6, h, n(10), i * 1.5F + 3F, 2.0, mote(0xE6FFF0, 0.2F));
        });
        ring(base.add(0, 0.1, 0), null, 0.9, n(14), spark(HEAL, 0.2F), 0.04);
        sound(base, SoundEvents.AMETHYST_BLOCK_CHIME, 0.9F, 1.3F);
        sound(base, SoundEvents.BREWING_STAND_BREW, 0.4F, 1.8F);
    }

    private static void scan(SbFxNet.Burst p) {
        Vec3 c = p.pos();
        float r = Math.max(2F, p.size());
        // pulso de radar: duas frentes de onda e um zumbido
        shock(c, 0x7FE8FF, r, 14, new Vec3(0, 1, 0));
        later(4, () -> shock(c, 0x4FB8FF, r * 0.8F, 12, new Vec3(0, 1, 0)));
        ring(c, null, 0.6, n(16), glow(0x7FE8FF, 0.3F), 0.35);
        sound(c, SoundEvents.NOTE_BLOCK_CHIME.value(), 0.6F, 1.8F);
    }

    private static void gallop(SbFxNet.Burst p) {
        Vec3 dir = p.vec().lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : p.vec().multiply(1, 0, 1).normalize();
        Entity rider = entity(p.entity());
        // ferraduras de luz: arcos dourados no chão atrás da montaria, a cada 3 ticks
        repeat(0, 8, 3, i -> {
            Entity e = rider == null ? null : (rider.getVehicle() != null ? rider.getVehicle() : rider);
            Vec3 c = e == null ? p.pos() : e.position();
            Vec3 d = e == null ? dir : (e.getDeltaMovement().horizontalDistanceSqr() > 1.0E-3 ? e.getDeltaMovement().multiply(1, 0, 1).normalize() : dir);
            Vec3 side = basis(d)[0].scale(i % 2 == 0 ? 0.35 : -0.35);
            Vec3 hoof = c.add(side).add(0, 0.08, 0);
            slashArc(hoof, d.scale(-1), Math.PI / 2, 0.3, Math.PI * 1.2, n(8), glow(GOLD, 0.22F), 0.0);
            if (hi()) speedLines(c.add(0, 1.0, 0), d.scale(-1), 0.9, n(4), 0.6, streak(GOLD, 0.5F));
            if (i % 2 == 0) sound(c, SoundEvents.HORSE_GALLOP, 0.5F, 1.3F);
        });
    }

    private static void learn(SbFxNet.Burst p) {
        Vec3 c = p.pos();
        riseSpiral(c, 1.3, 2.6, n(40), 0F, 3.0, glow(GOLD, 0.3F));
        goldenRect(c.add(0, 1.4, 0), p.vec().lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : p.vec(), 1.8F, 4);
        pillar(c, GOLD, 0.7F, 5F, 24);
        sound(c, SoundEvents.PLAYER_LEVELUP, 0.8F, 1.2F);
        sound(c, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 1.0F);
    }

    private SbFxClient() {}
}
