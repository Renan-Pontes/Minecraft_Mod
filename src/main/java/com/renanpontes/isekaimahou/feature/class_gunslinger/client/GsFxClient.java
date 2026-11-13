package com.renanpontes.isekaimahou.feature.class_gunslinger.client;

import static com.renanpontes.isekaimahou.feature.class_gunslinger.client.FxKit.*;

import com.renanpontes.isekaimahou.feature.class_gunslinger.GsFxNet;
import com.renanpontes.isekaimahou.feature.class_gunslinger.GunKind;
import com.renanpontes.isekaimahou.feature.class_gunslinger.entity.ManaBulletEntity;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * SÓ CLIENTE. Coreografia de anime do Pistoleiro: clarão do cano com estrela e fumaça, traçantes com núcleo branco,
 * faíscas de acerto, a Espada de Fótons (corte com brilho), a Explosão Estelar (golpes alternando corte azul e tiro
 * dourado, terminando numa supernova), Estrela Negra (cruz vermelha + estrela de 5 pontas), o estrondo do antimaterial,
 * as badaladas do Meio-Dia e a Arma Espiritual. Também o rastro das balas de mana, desenhado a cada tick.
 */
public final class GsFxClient {
    private static final int WHITE = 0xFFFFFF;
    private static final int PHOTON = 0x7FD4FF;
    private static final int PHOTON_CORE = 0xFFE27A;
    private static final int RED = 0xFF2A2A;
    private static final int BLACK = 0x1A0A0A;
    private static final int NOON = 0xFFC23A;

    public static void init() {
        GsFxNet.clientBurst = GsFxClient::onBurst;
        GsFxNet.clientTrail = GsFxClient::trail;
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> FxKit.tick());
    }

    // =====================================================================================
    //  Rastro das balas de mana
    // =====================================================================================
    private static void trail(Entity e) {
        if (!(e instanceof ManaBulletEntity b)) return;
        Vec3 p = e.position().add(0, e.getBbHeight() * 0.5, 0);
        if (!near(p)) return;
        Vec3 v = e.getDeltaMovement();
        if (v.lengthSqr() < 1.0E-4) return;
        int rgb = b.color();
        float size = b.size();
        // risco luminoso atrás da bala (comprimento acompanha a velocidade)
        Vec3 back = p.subtract(v.scale(0.8));
        line(back, p, 0.35, glow(rgb, 0.16F * size + 0.06F), 0.0);
        if (hi() && e.tickCount % 2 == 0) add(streak(light(rgb, 0.5F), 0.4F * size), p, v.scale(0.05));
        if (size > 1.4F && e.tickCount % 2 == 0) helix(back, p, 0.15 * size, 1.0, n(6), e.tickCount * 0.8F, spark(rgb, 0.2F));
    }

    // =====================================================================================
    //  Bursts
    // =====================================================================================
    private static void onBurst(GsFxNet.Burst p) {
        if (level() == null || !near(p.pos())) return;
        try {
            switch (p.kind()) {
                case GsFxNet.MUZZLE -> muzzle(p);
                case GsFxNet.TRACER -> tracerLine(p.pos(), p.vec(), p.rgb(), Math.max(0.4F, p.size()));
                case GsFxNet.IMPACT -> impact(p);
                case GsFxNet.PHOTON_SLASH -> photonSlash(p);
                case GsFxNet.PHOTON_HIT -> photonHit(p);
                case GsFxNet.CAMO -> camo(p);
                case GsFxNet.BLACK_STAR_CHARGE -> blackStarCharge(p);
                case GsFxNet.BLACK_STAR -> blackStar(p);
                case GsFxNet.ANTI_MATERIEL -> antiMateriel(p);
                case GsFxNet.STARBURST_START -> starburstStart(p);
                case GsFxNet.STARBURST_HIT -> starburstHit(p);
                case GsFxNet.STARBURST_FINISH -> starburstFinish(p);
                case GsFxNet.HIGH_NOON_TOLL -> noonToll(p);
                case GsFxNet.HIGH_NOON_MARK -> noonMark(p);
                case GsFxNet.SPIRIT_GUN -> spiritGun(p);
                case GsFxNet.EXPLOSION -> nova(p.pos(), p.rgb(), Math.max(1F, p.size()), true);
                case GsFxNet.CHARGED_SHOT -> chargedShot(p);
                case GsFxNet.LEARN -> learn(p);
                case GsFxNet.BLINK -> blink(p);
                default -> {}
            }
        } catch (RuntimeException ignored) {
            // efeito quebrado nunca derruba o cliente
        }
    }

    private static void muzzle(GsFxNet.Burst p) {
        Vec3 dir = p.vec().lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : p.vec().normalize();
        GunKind kind = p.data() >= 0 && p.data() < GunKind.values().length ? GunKind.values()[p.data()] : GunKind.REVOLVER;
        float s = Math.max(0.5F, p.size());
        switch (kind) {
            case SHOTGUN -> {
                // leque largo de chamas e fumaça grossa
                muzzleFlash(p.pos(), dir, p.rgb(), s * 1.5F);
                cone(p.pos(), dir, 0.45, n(10), 0.5, ParticleTypes.SMALL_FLAME);
                if (hi()) cone(p.pos(), dir, 0.3, n(5), 0.15, smoke(0x6A6A6A, 1.4F));
            }
            case RIFLE -> {
                // anel de pressão no cano + jato longo
                muzzleFlash(p.pos(), dir, p.rgb(), s * 1.3F);
                ring(p.pos().add(dir.scale(0.3)), dir, 0.3, n(12), smoke(0xBFBFBF, 0.7F), 0.12);
                if (hi()) speedLines(p.pos().add(dir.scale(2)), dir, 0.2, n(4), 1.2, streak(light(p.rgb(), 0.5F), 0.5F));
            }
            default -> muzzleFlash(p.pos(), dir, p.rgb(), s);
        }
    }

    private static void impact(GsFxNet.Burst p) {
        boolean entity = p.data() == 1;
        float s = Math.max(0.6F, p.size());
        hitSpark(p.pos(), p.vec(), p.rgb(), s);
        if (!entity) {
            // bloco: lascas e fumacinha
            debris(p.pos(), n(5), 0.2, 0.25);
            add(smoke(0x7A7A7A, 0.7F), p.pos(), new Vec3(0, 0.03, 0));
        } else if (hi()) {
            sphere(p.pos(), n(5), 0.18, ParticleTypes.CRIT);
        }
    }

    private static void photonSlash(GsFxNet.Burst p) {
        Vec3 dir = p.vec().lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : p.vec().normalize();
        float reach = Math.max(1.5F, p.size());
        double tilt = Math.toRadians(p.data() - 360);
        int rgb = p.rgb() == 0 ? PHOTON : p.rgb();
        Vec3 c = p.pos();
        // antecipação curtíssima: a lâmina "liga" com um zumbido
        add(glow(light(rgb, 0.6F), 0.7F), c, Vec3.ZERO);
        // corte em dois quadros (o arco percorre), com núcleo branco
        slashStreak(c, dir, tilt, reach * 0.8, rgb, Math.PI * 0.75);
        later(1, () -> {
            slashStreak(c, dir, tilt + 0.05, reach, rgb, Math.PI * 0.9);
            if (hi()) slashArc(c, dir, tilt, reach * 0.9, Math.PI * 0.6, n(10), glow(WHITE, 0.3F), 0.0);
        });
        // rescaldo: fiapos de luz que caem devagar
        later(3, () -> slashArc(c, dir, tilt, reach * 0.95, Math.PI * 0.8, n(8), mote(rgb, 0.2F), 0.02));
        soundVar(c, SoundEvents.PLAYER_ATTACK_SWEEP, 0.7F, 1.7F, 0.1F);
        sound(c, SoundEvents.BEACON_DEACTIVATE, 0.35F, 2.0F);
    }

    private static void photonHit(GsFxNet.Burst p) {
        Entity t = entity(p.entity());
        Vec3 c = center(t, p.pos());
        int rgb = p.rgb() == 0 ? PHOTON : p.rgb();
        Vec3 dir = p.vec().lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : p.vec();
        // corte em X no alvo
        double tilt = RND.nextDouble() * Math.PI;
        line(c.add(basis(dir)[0].scale(0.8).add(0, 0.6, 0)), c.subtract(basis(dir)[0].scale(0.8).add(0, 0.6, 0)), 0.12, glow(rgb, 0.3F), 0.0);
        if (hi()) line(c.add(basis(dir)[0].scale(-0.8).add(0, 0.6, 0)), c.subtract(basis(dir)[0].scale(-0.8).add(0, 0.6, 0)), 0.12, glow(WHITE, 0.22F), 0.0);
        hitSpark(c, dir, rgb, 1.2F);
        ring(c, dir, 0.4, n(10), spark(rgb, 0.22F), 0.2 + tilt * 0.01);
        sound(c, SoundEvents.PLAYER_ATTACK_CRIT, 0.6F, 1.5F);
    }

    private static void camo(GsFxNet.Burst p) {
        Entity e = entity(p.entity());
        Vec3 base = e == null ? p.pos() : e.position();
        double h = e == null ? 1.8 : e.getBbHeight();
        double w = e == null ? 0.6 : e.getBbWidth();
        // o manto óptico se fecha dos pés à cabeça: anéis com distorção e faíscas elétricas
        repeat(0, 7, 1, i -> {
            Entity cur = entity(p.entity());
            Vec3 b = cur == null ? base : cur.position();
            Vec3 c = b.add(0, h * i / 6.0, 0);
            ring(c, null, w * 0.9 + 0.2, n(14), glow(0xD8F0FF, 0.2F), 0.0);
            if (hi()) ring(c, null, w * 0.9 + 0.25, n(6), ParticleTypes.ELECTRIC_SPARK, 0.02);
        });
        later(7, () -> {
            Entity cur = entity(p.entity());
            Vec3 b = cur == null ? base : cur.position();
            sphere(b.add(0, h * 0.5, 0), n(12), 0.08, wisp(0xBFE8FF, 0.3F));
        });
        sound(base, SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.8F, 1.3F);
    }

    private static void blackStarCharge(GsFxNet.Burst p) {
        Vec3 chest = p.pos();
        Vec3 look = p.vec().lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : p.vec().normalize();
        Vec3 right = basis(look)[0];
        // o sinal da cruz, traçado em dois movimentos, depois a pressão escura
        line(chest.add(0, 0.3, 0), chest.add(0, -0.3, 0), 0.05, glow(RED, 0.18F), 0.0);
        later(2, () -> line(chest.add(right.scale(-0.2)).add(0, 0.1, 0), chest.add(right.scale(0.2)).add(0, 0.1, 0), 0.05, glow(RED, 0.18F), 0.0));
        later(3, () -> implode(chest, look, 0.9, 3, 3, smoke(BLACK, 0.8F)));
    }

    private static void blackStar(GsFxNet.Burst p) {
        Vec3 from = p.pos(), to = p.vec();
        Vec3 dir = to.subtract(from);
        if (dir.lengthSqr() < 1.0E-4) return;
        dir = dir.normalize();
        flash(from, RED, 0.9F);
        muzzleFlash(from, dir, RED, 1.4F);
        // rastro negro com núcleo rubro
        line(from, to, 0.3, smoke(BLACK, 0.9F), 0.05);
        line(from, to, 0.35, glow(RED, 0.2F), 0.0);
        shock(from.add(dir.scale(0.5)), RED, 1.6F, 8, dir);
        Entity t = entity(p.entity());
        if (t == null) return;
        // a estrela de 5 pontas negra e vermelha sobre o alvo, desenhada traço a traço
        Vec3 c = t.position().add(0, t.getBbHeight() + 0.7, 0);
        Vec3[] b = basis(new Vec3(dir.x, 0, dir.z).lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : new Vec3(dir.x, 0, dir.z));
        Vec3[] pts = new Vec3[5];
        for (int i = 0; i < 5; i++) {
            double a = Math.PI / 2 + i * 2 * Math.PI / 5;
            pts[i] = c.add(b[0].scale(Math.cos(a) * 0.65)).add(b[1].scale(Math.sin(a) * 0.65));
        }
        for (int i = 0; i < 5; i++) {
            final Vec3 a = pts[i], z = pts[(i + 2) % 5];
            later(1 + i, () -> {
                line(a, z, 0.06, glow(RED, 0.2F), 0.0);
                if (hi()) line(a, z, 0.12, smoke(BLACK, 0.4F), 0.0);
            });
        }
        later(7, () -> {
            flash(c, RED, 1.2F);
            sphere(center(entity(p.entity()), c), n(14), 0.2, smoke(BLACK, 1.0F));
            sound(c, SoundEvents.WITHER_HURT, 0.5F, 1.6F);
        });
    }

    private static void antiMateriel(GsFxNet.Burst p) {
        Vec3 from = p.pos(), to = p.vec();
        if (to.subtract(from).lengthSqr() < 1.0E-4) return;
        Vec3 dir = to.subtract(from).normalize();
        int rgb = p.rgb() == 0 ? 0x9AF0FF : p.rgb();
        // estrondo no cano: clarão grande, 2 anéis de pressão, nuvem
        flash(from, WHITE, 1.6F);
        muzzleFlash(from, dir, rgb, 2.2F);
        shock(from.add(dir.scale(0.6)), 0xE8F8FF, 3.5F, 10, dir);
        if (hi()) later(2, () -> shock(from.add(dir.scale(1.5)), rgb, 2.5F, 8, dir));
        ring(from, dir, 0.6, n(16), smoke(0xD0D0D0, 1.4F), 0.25);
        // rastro grosso que demora a sumir (vapor de ar rasgado)
        tracerLine(from, to, rgb, 1.8F);
        line(from, to, 0.8, smoke(0xE0E8F0, 0.8F), 0.12);
        if (hi()) helix(from, to, 0.35, from.distanceTo(to) / 3.0, n(60), 0F, glow(light(rgb, 0.4F), 0.18F));
        // impacto do outro lado
        later(1, () -> nova(to, rgb, 1.4F, false));
        shake(from, 10F, 1.2F, 6);
        sound(from, SoundEvents.GENERIC_EXPLODE.value(), 0.6F, 1.6F);
    }

    private static void starburstStart(GsFxNet.Burst p) {
        Entity e = entity(p.entity());
        Vec3 c = center(e, p.pos());
        // aura de duas cores (fóton azul + dourado) subindo em espiral
        riseSpiral(e == null ? p.pos() : e.position(), 1.0, 2.2, n(26), 0F, 2.5, glow(PHOTON, 0.28F));
        riseSpiral(e == null ? p.pos() : e.position(), 1.0, 2.2, n(26), (float) Math.PI, 2.5, glow(PHOTON_CORE, 0.28F));
        flash(c, PHOTON, 1.4F);
        if (e != null) circleOn(e, PHOTON, 2.2F, 40, 3, 0, 0, false);
        sound(c, SoundEvents.BEACON_ACTIVATE, 0.6F, 1.8F);
    }

    private static void starburstHit(GsFxNet.Burst p) {
        Vec3 c = p.pos();
        Vec3 dir = p.vec().lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : p.vec().normalize();
        int k = p.data();
        boolean slash = k % 2 == 0;
        int rgb = slash ? (p.rgb() == 0 ? PHOTON : p.rgb()) : PHOTON_CORE;
        if (slash) {
            // golpe estilo "estrela": riscos cruzados que atravessam o alvo
            double tilt = k * 0.7 + RND.nextDouble() * 0.5;
            Vec3[] b = basis(dir);
            Vec3 a = b[0].scale(Math.cos(tilt)).add(b[1].scale(Math.sin(tilt))).scale(1.4);
            line(c.add(a), c.subtract(a), 0.1, streak(rgb, 0.6F), 0.0);
            line(c.add(a.scale(0.9)), c.subtract(a.scale(0.9)), 0.12, glow(WHITE, 0.25F), 0.0);
        }
        hitSpark(c, dir, rgb, 1.1F + k * 0.03F);
        if (hi()) star(c, dir, 0.5 + k * 0.02, 4, glow(rgb, 0.16F), 0.1);
        if (k % 4 == 3) shake(c, 6F, 0.35F, 3);
        sound(c, slash ? SoundEvents.PLAYER_ATTACK_SWEEP : SoundEvents.PLAYER_ATTACK_CRIT, 0.6F, 1.3F + k * 0.04F);
    }

    private static void starburstFinish(GsFxNet.Burst p) {
        Vec3 c = p.pos();
        // supernova: tudo se contrai por um instante, depois explode em estrelas azuis e douradas
        implode(c, null, 2.0, 3, 3, glow(PHOTON_CORE, 0.3F));
        later(3, () -> {
            nova(c, PHOTON, 2.4F, false);
            star(c, new Vec3(0, 0, 1), 3.0, 8, glow(PHOTON_CORE, 0.25F), 0.15);
            if (hi()) star(c, new Vec3(1, 0, 0), 2.4, 8, glow(PHOTON, 0.25F), 0.15);
            sphere(c, n(30), 0.5, ParticleTypes.FIREWORK);
            sound(c, SoundEvents.FIREWORK_ROCKET_TWINKLE, 1.2F, 1.0F);
            sound(c, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 1.0F, 0.6F);
        });
    }

    private static void noonToll(GsFxNet.Burst p) {
        Entity e = entity(p.entity());
        Vec3 base = e == null ? p.pos() : e.position();
        int t = p.data();
        // relógio no chão: círculo dourado + o ponteiro avança a cada badalada
        circle(base.add(0, 0.06, 0), NOON, 2.2F, 22, 2, 0, false);
        double ang = -Math.PI / 2 + t * Math.PI * 2 / 3;
        Vec3 tip = base.add(Math.cos(ang) * 1.8, 0.08, Math.sin(ang) * 1.8);
        line(base.add(0, 0.08, 0), tip, 0.12, glow(NOON, 0.22F), 0.0);
        shock(base.add(0, 0.1, 0), NOON, 3.0F + t, 10, null);
        // a poeira do faroeste girando em volta
        if (hi()) ring(base.add(0, 0.2, 0), null, 2.4, n(14), smoke(0xC8A070, 1.2F), 0.06);
    }

    private static void noonMark(GsFxNet.Burst p) {
        Entity e = entity(p.entity());
        if (e == null) return;
        Vec3 c = e.position().add(0, e.getBbHeight() + 0.6, 0);
        // mira dourada girando sobre a cabeça
        ring(c, new Vec3(0, 0, 1), 0.45, n(12), glow(NOON, 0.2F), 0.0);
        line(c.add(-0.6, 0, 0), c.add(-0.25, 0, 0), 0.08, glow(NOON, 0.18F), 0);
        line(c.add(0.25, 0, 0), c.add(0.6, 0, 0), 0.08, glow(NOON, 0.18F), 0);
        line(c.add(0, -0.6, 0), c.add(0, -0.25, 0), 0.08, glow(NOON, 0.18F), 0);
        line(c.add(0, 0.25, 0), c.add(0, 0.6, 0), 0.08, glow(NOON, 0.18F), 0);
    }

    private static void spiritGun(GsFxNet.Burst p) {
        Vec3 at = p.pos();
        Vec3 dir = p.vec().lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : p.vec().normalize();
        int rgb = p.rgb() == 0 ? 0x6FD8FF : p.rgb();
        float s = Math.max(1F, p.size());
        // carga no dedo (curta, já que o tiro sai junto) + estouro em anel
        implode(at, dir, 0.8 * s, 3, 3, glow(rgb, 0.3F));
        later(3, () -> {
            flash(at, rgb, 1.2F * s);
            shock(at.add(dir.scale(0.3)), rgb, 2.2F * s, 8, dir);
            cone(at, dir, 0.3, n(14), 0.7, glow(light(rgb, 0.3F), 0.3F));
            ring(at, dir, 0.6, n(16), spark(rgb, 0.25F), 0.25);
            shake(at, 8F, 0.5F, 4);
        });
    }

    private static void chargedShot(GsFxNet.Burst p) {
        Vec3 at = p.pos();
        Vec3 dir = p.vec().lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : p.vec().normalize();
        flash(at, p.rgb(), 1.2F);
        shock(at.add(dir.scale(0.4)), p.rgb(), 2.4F, 9, dir);
        cone(at, dir, 0.2, n(10), 0.9, streak(light(p.rgb(), 0.4F), 0.5F));
        shake(at, 8F, 0.6F, 4);
    }

    private static void learn(GsFxNet.Burst p) {
        Vec3 c = p.pos();
        riseSpiral(c, 1.1, 2.4, n(32), 0F, 2.5, glow(p.rgb(), 0.28F));
        pillar(c, p.rgb(), 0.6F, 4F, 20);
        sphere(c.add(0, 1.2, 0), n(16), 0.2, ParticleTypes.FIREWORK);
        sound(c, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.9F, 1.5F);
    }

    private static void blink(GsFxNet.Burst p) {
        Vec3 a = p.pos(), b = p.vec();
        afterimage(a, 0F, PHOTON, 1F);
        ParticleOptions o = streak(PHOTON, 0.5F);
        line(a.add(0, 1, 0), b.add(0, 1, 0), 0.4, o, 0.3);
        add(glow(PHOTON_CORE, 0.8F), b.add(0, 1, 0), Vec3.ZERO);
    }

    private GsFxClient() {}
}
