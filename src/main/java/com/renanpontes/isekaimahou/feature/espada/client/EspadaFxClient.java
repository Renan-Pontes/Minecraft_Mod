package com.renanpontes.isekaimahou.feature.espada.client;

import static com.renanpontes.isekaimahou.feature.espada.client.EspadaFxKit.*;

import com.renanpontes.isekaimahou.feature.espada.EspadaPayloads.Fx;
import com.renanpontes.isekaimahou.feature.espada.EspadaSounds;
import com.renanpontes.isekaimahou.feature.espada.entity.Barragan;
import com.renanpontes.isekaimahou.feature.espada.entity.EspadaBoss;
import com.renanpontes.isekaimahou.feature.espada.entity.EspadaFx;
import com.renanpontes.isekaimahou.feature.espada.entity.Grimmjow;
import com.renanpontes.isekaimahou.feature.espada.entity.Ulquiorra;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * SÓ CLIENTE. Coreografia dos efeitos dos Espada a partir do pacote {@link Fx}: antecipação (energia sugada, círculos,
 * tremor crescente), impacto (feixe, clarão, onda de choque, tremor, som em camadas) e rescaldo (fumaça, detritos,
 * fagulhas que ficam no ar). Também o "tick" visual de cada chefe (auras, chuva de reiatsu, cinzas da morte).
 * Tudo passa pelo {@code FxQuality} (quantidades, extras caros e distância).
 */
public final class EspadaFxClient {

    static void handle(Fx p) {
        if (level() == null) return;
        Vec3 a = p.a();
        // Efeitos enormes (feixes, explosões, liberações) aparecem de mais longe.
        boolean big = p.kind() == EspadaFx.BEAM || p.kind() == EspadaFx.EXPLOSION || p.kind() == EspadaFx.RELEASE || p.kind() == EspadaFx.RAIN
                || p.kind() == EspadaFx.DARKNESS || p.kind() == EspadaFx.LIGHTNING;
        if (!big && !near(a)) return;
        Entity e = entity(p.entity());
        switch (p.kind()) {
            case EspadaFx.SONIDO -> sonido(e, a, p.b(), p.color());
            case EspadaFx.CHARGE -> charge(e, a, (int) p.p1(), p.p2(), p.color(), p.aux());
            case EspadaFx.BEAM -> beam(a, p.b(), p.p1(), (int) p.p2(), p.color(), p.aux());
            case EspadaFx.EXPLOSION -> explosion(a, p.p1(), p.color(), p.aux());
            case EspadaFx.RAIN -> rain(a, p.p1(), (int) p.p2(), p.color());
            case EspadaFx.DARKNESS -> darkness(a, (int) p.p1());
            case EspadaFx.BREATH -> breath(a, p.b(), p.p1(), (int) p.p2(), p.color(), p.aux());
            case EspadaFx.BREATH_SPHERE -> breathSphere(a, p.b().x, (int) p.p2(), p.color(), p.aux());
            case EspadaFx.LIGHTNING -> lightning(a, p.b(), p.p1(), p.color(), p.aux());
            case EspadaFx.CLAW_LAUNCH -> clawLaunch(e, a, p.b(), p.p1(), (int) p.p2(), p.color());
            case EspadaFx.CRUMBLE -> crumble(a);
            case EspadaFx.CLAWS -> claw(a, p.b(), p.p1(), (int) p.p2(), p.color());
            case EspadaFx.ROAR -> roar(e, a, p.p1(), p.color());
            case EspadaFx.RELEASE -> release(e, a, p.color(), p.aux());
            case EspadaFx.SPARK -> impactSpark(a, p.color());
            case EspadaFx.HIERRO_BREAK -> hierroBreak(a);
            case EspadaFx.LANCE_FORM -> lance(e, a, (int) p.p1(), p.aux());
            case EspadaFx.SLASH -> slash(a, p.b(), p.p1(), p.color());
            case EspadaFx.FLASH -> bigFlash(a, p.p1(), p.color());
            case EspadaFx.REGEN -> regen(e, a);
            case EspadaFx.GRAB -> grab(a, p.b(), p.color());
            case EspadaFx.AURA_PULSE -> auraPulse(e, a, p.p1(), p.color());
            default -> {}
        }
    }

    // =====================================================================================
    //  Movimento
    // =====================================================================================
    private static void sonido(Entity e, Vec3 from, Vec3 to, int rgb) {
        // "Som de estalo": some numa linha fina; imagem residual onde estava; poeira onde chega.
        float yaw = e != null ? e.getYRot() : 0F;
        afterimage(from, yaw, rgb, 0.8F);
        vanish(from, dark(rgb, 0.3F), 0.8F);
        line(from.add(0, 1, 0), to.add(0, 1, 0), 0.5, streak(light(rgb, 0.5F), 0.5F), 0.4);
        if (hi()) speedLines(to.add(0, 1, 0), to.subtract(from), 0.8, n(10), 0.4, streak(0xFFFFFF, 0.4F));
        ring(to.add(0, 0.1, 0), null, 0.6, n(12), smoke(0xC8C8C8, 1.0F), 0.12);
        debris(to, n(6), 0.6, 0.25);
    }

    // =====================================================================================
    //  Cero: carga e feixe
    // =====================================================================================
    private static void charge(Entity e, Vec3 at, int ticks, float size, int rgb, int style) {
        EspadaBeams.orb(at, size, Math.max(4, ticks), rgb, style, e != null ? e.getId() : -1);
        boolean darkStyle = style == EspadaFx.STYLE_DARK;
        int c = style == EspadaFx.STYLE_BLOOD ? 0x2F6BFF : rgb;
        // Energia sendo sugada: anéis que encolhem e fagulhas puxadas para a mão.
        repeat(0, Math.max(1, ticks / 4), 4, i -> {
            Vec3 h = e != null && e.isAlive() ? orbPoint(e) : at;
            implode(h, null, 1.6 + size, 3, 6, glow(darkStyle ? EspadaFx.ULQ_GREEN : c, 0.35F));
            for (int k = 0; k < n(darkStyle ? 8 : 5); k++) {
                Vec3 d = new Vec3(RND.nextGaussian(), RND.nextGaussian(), RND.nextGaussian()).normalize().scale(2.5 + size * 2);
                add(darkStyle ? smoke(0x020302, 1.0F) : spark(c, 0.3F), h.add(d), d.scale(-0.12));
            }
            if (style == EspadaFx.STYLE_BLOOD && i % 2 == 0) {
                add(ParticleTypes.FALLING_LAVA, h.x, h.y - 0.2, h.z, 0, 0, 0);
                add(dust(EspadaFx.BLOOD, 0.8F), h.x, h.y, h.z, 0, -0.05, 0);
            }
            if (darkStyle && hi()) add(wisp(EspadaFx.ULQ_DEEP, 0.6F), h, new Vec3(0, 0.02, 0));
        });
        if (darkStyle) {
            // O ar escurece em volta dele.
            Vec3 base = e != null ? e.position() : at;
            circle(base.add(0, 0.05, 0), EspadaFx.ULQ_GREEN, 3.5F, ticks + 6, 4, 0, false);
            repeat(ticks / 2, 3, Math.max(1, ticks / 6), i -> shake(at, 30, 0.3F + i * 0.25F, 6));
        } else if (style == EspadaFx.STYLE_BLOOD) {
            repeat(ticks / 2, 2, Math.max(1, ticks / 4), i -> shake(at, 24, 0.3F + i * 0.3F, 6));
        }
    }

    private static Vec3 orbPoint(Entity e) {
        Vec3 fwd = Vec3.directionFromRotation(0, e.getYRot());
        Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
        return e.position().add(0, e.getBbHeight() * 0.74, 0).add(fwd.scale(0.85)).add(right.scale(-0.3));
    }

    private static void beam(Vec3 from, Vec3 to, float width, int life, int rgb, int style) {
        EspadaBeams.beam(from, to, width, life, rgb, style);
        boolean darkStyle = style == EspadaFx.STYLE_DARK;
        int c = style == EspadaFx.STYLE_BLOOD ? 0x2F6BFF : rgb;
        Vec3 dir = to.subtract(from);
        double len = dir.length();
        // Boca do feixe: clarão + onda virada para a frente.
        flash(from, darkStyle ? EspadaFx.ULQ_GREEN : c, width * 0.9F);
        shock(from, darkStyle ? EspadaFx.ULQ_GREEN : c, width * 1.4F, 10, dir);
        if (hi()) shock(from.add(dir.normalize().scale(1.5)), light(c, 0.3F), width * 1.0F, 8, dir);
        // Hélice de energia em volta e fagulhas ao longo do caminho.
        helix(from, to, width * 0.55, len / 3.0, n((int) Math.min(90, len * 3)), RND.nextFloat() * 6F, spark(darkStyle ? EspadaFx.ULQ_GREEN : c, 0.3F));
        if (darkStyle) {
            line(from, to, 0.6, smoke(0x010201, 2.2F), width * 0.5);
            repeat(4, 3, 4, i -> line(from, to, 1.2, wisp(EspadaFx.ULQ_DEEP, 0.7F), width * 0.8));
        }
        // Rescaldo: fumaça e poeira no chão ao longo do feixe.
        repeat(life, 1, 1, i -> {
            line(from, to, 1.5, smoke(darkStyle ? 0x101410 : dark(c, 0.6F), 1.6F), width * 0.6);
            if (hi()) line(from, to, 2.0, glow(c, 0.25F), width * 0.7);
        });
        float shakeI = darkStyle ? 2.0F : style == EspadaFx.STYLE_BLOOD ? 1.6F : 0.8F;
        shake(from.add(dir.scale(0.5)), (float) len + 16, shakeI, darkStyle ? 20 : 10);
        sound(from, SoundEvents.GENERIC_EXPLODE.value(), darkStyle ? 1.6F : 0.8F, darkStyle ? 0.5F : 0.9F);
        if (darkStyle) {
            Player p = Minecraft.getInstance().player;
            if (p != null && p.distanceToSqr(from) < 48 * 48) EspadaScreenFx.flash(0x0A1A10, 0.35F);
        }
    }

    private static void explosion(Vec3 at, float radius, int rgb, int style) {
        boolean darkStyle = style == EspadaFx.STYLE_DARK;
        boolean respira = style == EspadaFx.STYLE_RESPIRA;
        int c = style == EspadaFx.STYLE_BLOOD ? 0x2F6BFF : rgb;
        if (respira) {
            sphere(at, n((int) (14 * radius)), 0.12 * radius, smoke(EspadaFx.RESPIRA, 1.8F));
            ring(at.add(0, 0.2, 0), null, radius * 0.4, n(20), smoke(EspadaFx.AGED, 1.6F), 0.2);
            shock(at.add(0, 0.1, 0), EspadaFx.RESPIRA_LIGHT, radius * 1.4F, 14, null);
            debris(at, n(14), radius * 0.5, 0.4);
            return;
        }
        flash(at, darkStyle ? EspadaFx.ULQ_GREEN : c, radius * 1.1F);
        shock(at.add(0, 0.1, 0), c, radius * 1.6F, 12 + (int) radius, null);
        sphere(at, n((int) (10 * radius)), 0.09 * radius, spark(c, 0.35F));
        sphere(at, n((int) (6 * radius)), 0.05 * radius, smoke(darkStyle ? 0x050806 : dark(c, 0.5F), 1.5F + radius * 0.1F));
        groundImpact(at, radius, darkStyle ? 0x101010 : 0x6A6A6A);
        if (radius >= 6) {
            // Lanza del Relámpago: coluna de luz, várias ondas e fagulhas que ficam caindo.
            pillar(at, c, radius * 0.6F, radius * 5F, 34);
            shock(at.add(0, 0.2, 0), light(c, 0.5F), radius * 2.4F, 24, null);
            repeat(4, 3, 5, i -> shock(at.add(0, 0.15, 0), c, radius * (1.0F + i * 0.6F), 16, null));
            repeat(2, 12, 3, i -> {
                for (int k = 0; k < n(10); k++) {
                    double ang = RND.nextDouble() * Math.PI * 2, r = RND.nextDouble() * radius;
                    add(ember(c, 0.5F), at.x + Math.cos(ang) * r, at.y + 0.2 + RND.nextDouble() * radius, at.z + Math.sin(ang) * r,
                            0, 0.04 + RND.nextDouble() * 0.06, 0);
                }
            });
            repeat(0, 6, 4, i -> debris(at.add((RND.nextDouble() - 0.5) * radius, 0, (RND.nextDouble() - 0.5) * radius), n(16), radius * 0.3, 0.7));
            Player p = Minecraft.getInstance().player;
            if (p != null) {
                double d = p.position().distanceTo(at);
                if (d < radius * 4) EspadaScreenFx.flash(0xD8FFE6, (float) Math.min(1.0, 1.4 - d / (radius * 4)));
            }
            sound(at, SoundEvents.LIGHTNING_BOLT_THUNDER, 2.0F, 0.6F);
            sound(at, SoundEvents.GENERIC_EXPLODE.value(), 2.0F, 0.5F);
            if (radius >= 14) giantDome(at, radius, c);
        } else {
            sound(at, SoundEvents.GENERIC_EXPLODE.value(), 0.6F, 1.3F);
        }
    }

    /**
     * w10 — a explosão GIGANTE da Lanza del Relámpago: uma CÚPULA verde-branca que cresce (anéis em várias inclinações
     * = esfera de luz), pilar branco altíssimo no meio, cogumelo de fumaça escura subindo e, depois de
     * {@link Ulquiorra#LANZA_SHOCK_DELAY} ticks, a ONDA DE CHOQUE correndo pelo chão (mesmo tempo do dano no servidor).
     * Quantidades pelo preset; os anéis da cúpula são 3 (Baixo/Médio) ou 6 (Alto+).
     */
    private static void giantDome(Vec3 at, float radius, int c) {
        float dome = radius * 0.75F;
        pillar(at, 0xF0FFF4, radius * 0.18F, radius * 9F, 30);
        later(1, () -> flash(at.add(0, dome * 0.4, 0), 0xFFFFFF, radius * 1.3F));
        int rings = hi() ? 6 : 3;
        for (int k = 0; k < rings; k++) {
            double a = Math.PI * k / rings;
            Vec3 nrm = new Vec3(Math.cos(a), 0, Math.sin(a));
            int life = 16 + k;
            later(k / 2, () -> shock(at.add(0, 0.3, 0), light(c, 0.35F), dome * 2.0F, life, nrm));
        }
        shock(at.add(0, 0.3, 0), light(c, 0.55F), dome * 2.0F, 18, new Vec3(0, 1, 0));
        // casca da cúpula: brilhos espalhados numa meia-esfera que cresce
        repeat(0, 5, 2, i -> {
            double r = dome * (0.35 + 0.16 * i);
            for (int k = 0; k < n(36); k++) {
                double th = RND.nextDouble() * Math.PI * 2, ph = Math.acos(RND.nextDouble());
                Vec3 d = new Vec3(Math.sin(ph) * Math.cos(th), Math.cos(ph), Math.sin(ph) * Math.sin(th));
                add(k % 3 == 0 ? glow(0xE8FFF0, 0.9F) : spark(c, 0.5F), at.add(d.scale(r)), d.scale(0.12));
            }
        });
        // cogumelo de fumaça escura-verde subindo
        repeat(4, 8, 3, i -> {
            Vec3 top = at.add(0, 2 + i * radius * 0.12, 0);
            ring(top, null, radius * (0.15 + 0.03 * i), n(14), smoke(i % 2 == 0 ? 0x0A1A10 : dark(c, 0.7F), 3.0F), 0.08);
        });
        // a onda de choque atrasada (o servidor fere quem ela pega no mesmo tick)
        later(Ulquiorra.LANZA_SHOCK_DELAY, () -> {
            shock(at.add(0, 0.12, 0), c, radius * 2.4F, 24, null);
            shock(at.add(0, 0.14, 0), 0xE8FFF0, radius * 2.0F, 18, null);
            ring(at.add(0, 0.3, 0), null, radius * 0.4, n(48), smoke(0x9A9A9A, 2.2F), 0.9);
            groundImpact(at, radius * 1.2, 0x6A6A6A);
            // (o tremor da onda vem do servidor, junto do dano)
        });
    }

    // =====================================================================================
    //  Ulquiorra: liberações, lança, Hierro, regeneração
    // =====================================================================================
    private static void rain(Vec3 center, float radius, int ticks, int rgb) {
        // Chuva de reiatsu negro-verde caindo sobre a arena inteira.
        repeat(0, Math.max(1, ticks / 2), 2, i -> {
            Player p = Minecraft.getInstance().player;
            Vec3 c = p != null && p.position().distanceTo(center) < radius * 1.5 ? p.position() : center;
            for (int k = 0; k < n(hi() ? 26 : 14); k++) {
                double a = RND.nextDouble() * Math.PI * 2, r = Math.sqrt(RND.nextDouble()) * radius;
                double x = c.x + Math.cos(a) * r, z = c.z + Math.sin(a) * r, y = c.y + 10 + RND.nextDouble() * 8;
                boolean black = RND.nextInt(3) > 0;
                add(black ? streak(0x041008, 0.7F) : streak(rgb, 0.6F), x, y, z, 0, -0.9 - RND.nextDouble() * 0.4, 0);
            }
        });
    }

    private static void darkness(Vec3 at, int ticks) {
        Player p = Minecraft.getInstance().player;
        if (p != null && p.distanceToSqr(at) < 64 * 64) EspadaScreenFx.darkFall(ticks);
        ring(at.add(0, 0.2, 0), null, 2.0, n(30), smoke(0x010201, 2.4F), 0.3);
        if (hi()) sphere(at.add(0, 1.2, 0), n(30), 0.25, wisp(EspadaFx.ULQ_DEEP, 0.8F));
    }

    private static void release(Entity e, Vec3 at, int rgb, int variant) {
        Vec3 c = at.add(0, 1.0, 0);
        switch (variant) {
            case 0 -> { // Murciélago: explosão negro-verde, penas/asas de sombra
                flash(c, EspadaFx.ULQ_GREEN, 5F);
                pillar(at, EspadaFx.ULQ_GREEN, 2.2F, 40F, 40);
                shock(at.add(0, 0.1, 0), EspadaFx.ULQ_GREEN, 16F, 22, null);
                shock(c, 0x0A2A16, 10F, 18, new Vec3(0, 1, 0));
                sphere(c, n(70), 0.6, smoke(0x020403, 2.2F));
                sphere(c, n(50), 0.8, spark(EspadaFx.ULQ_GREEN, 0.5F));
                // "Asas" de fumaça abrindo para os lados.
                Vec3 side = e != null ? new Vec3(-Vec3.directionFromRotation(0, e.getYRot()).z, 0, Vec3.directionFromRotation(0, e.getYRot()).x) : new Vec3(1, 0, 0);
                for (int s = -1; s <= 1; s += 2) {
                    for (int k = 0; k < n(24); k++) {
                        double t = k / 24.0;
                        Vec3 p = c.add(0, 0.8 + Math.sin(t * Math.PI) * 1.4, 0).add(side.scale(s * (0.4 + t * 3.5)));
                        add(wisp(0x020302, 0.9F), p, side.scale(s * 0.05).add(0, 0.01, 0));
                    }
                }
                groundImpact(at, 10, 0x202020);
                shake(at, 40, 2.0F, 30);
            }
            case 1 -> { // Segunda Etapa: o mundo escurece, coluna negra enorme
                flash(c, 0x1A5A30, 6F);
                pillar(at, EspadaFx.ULQ_DEEP, 3.0F, 64F, 50);
                shock(at.add(0, 0.1, 0), EspadaFx.ULQ_GREEN, 22F, 28, null);
                repeat(0, 4, 4, i -> shock(at.add(0, 0.1, 0), 0x0F3A20, 8F + i * 5F, 18, null));
                sphere(c, n(90), 0.9, smoke(0x000000, 2.6F));
                repeat(0, 20, 2, i -> riseSpiral(at, 2.5, 6, n(16), i * 0.7F, 2, wisp(EspadaFx.ULQ_GREEN, 0.6F)));
                groundImpact(at, 14, 0x101010);
                repeat(0, 5, 3, i -> debris(at.add((RND.nextDouble() - 0.5) * 12, 0, (RND.nextDouble() - 0.5) * 12), n(16), 2, 0.8));
                shake(at, 56, 3.0F, 40);
            }
            case 2 -> { // Arrogante: carne vira pó, coroa de miasma
                flash(c, EspadaFx.RESPIRA_LIGHT, 4F);
                shock(at.add(0, 0.1, 0), EspadaFx.RESPIRA_LIGHT, 14F, 22, null);
                sphere(c, n(80), 0.45, smoke(EspadaFx.RESPIRA, 2.2F));
                sphere(c.add(0, 0.8, 0), n(40), 0.3, smoke(EspadaFx.AGED, 1.6F));
                riseSpiral(at, 2.0, 4.0, n(40), 0F, 3, wisp(EspadaFx.RESPIRA_LIGHT, 0.6F));
                circle(at.add(0, 0.05, 0), EspadaFx.RESPIRA_LIGHT, 6F, 60, 4, 0, true);
                groundImpact(at, 9, 0x4A3A2A);
                shake(at, 40, 2.0F, 30);
            }
            default -> { // Pantera: explosão azul e rugido
                flash(c, EspadaFx.GRIMM_BLUE, 4.5F);
                pillar(at, EspadaFx.GRIMM_BLUE, 1.8F, 30F, 30);
                shock(at.add(0, 0.1, 0), EspadaFx.GRIMM_BLUE, 14F, 20, null);
                sphere(c, n(70), 0.7, spark(EspadaFx.GRIMM_BLUE, 0.5F));
                speedLines(c, new Vec3(0, 1, 0), 3, n(30), 0.8, streak(0xD8F4FF, 0.6F));
                groundImpact(at, 10, 0x505050);
                shake(at, 40, 2.0F, 24);
            }
        }
    }

    private static void lance(Entity e, Vec3 at, int ticks, int aux) {
        if (aux == 1) {
            // Lança cravada no chão: brilha, faísca e "respira" até a explosão.
            EspadaBeams.lancePlanted(at, ticks);
            circle(at.add(0, 0.05, 0), EspadaFx.ULQ_GREEN, 3.5F, ticks, 5, 0, false);
            repeat(0, Math.max(1, ticks / 3), 3, i -> {
                ring(at.add(0, 0.3, 0), null, 0.6 + i * 0.12, n(10), spark(EspadaFx.ULQ_GREEN, 0.3F), 0.2);
                if (i % 2 == 0) add(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 1 + RND.nextDouble() * 3, at.z, 0, 0, 0);
            });
            sound(at, SoundEvents.BEACON_POWER_SELECT, 2.0F, 0.6F);
            return;
        }
        if (e == null) return;
        // A lança em si é um osso do modelo (brilha pela glowmask); aqui só a energia em volta.
        sound(e.position(), SoundEvents.BEACON_ACTIVATE, 1.0F, 1.6F);
        repeat(0, Math.max(1, ticks / 3), 3, i -> {
            Entity en = entity(e.getId());
            if (en == null) return;
            Vec3 h = en.position().add(0, en.getBbHeight() * 1.2, 0);
            for (int k = 0; k < n(4); k++) {
                Vec3 d = new Vec3(RND.nextGaussian(), RND.nextGaussian(), RND.nextGaussian()).normalize().scale(2);
                add(spark(EspadaFx.ULQ_GREEN, 0.3F), h.add(d), d.scale(-0.15));
            }
            if (i % 3 == 0) add(ParticleTypes.ELECTRIC_SPARK, h.x, h.y, h.z, 0, 0.1, 0);
        });
    }

    private static void impactSpark(Vec3 at, int rgb) {
        sphere(at, n(8), 0.18, spark(rgb, 0.25F));
        if (hi()) add(glow(rgb, 0.4F), at, Vec3.ZERO);
    }

    private static void hierroBreak(Vec3 at) {
        flash(at, 0xE8FFF0, 2.2F);
        sphere(at, n(30), 0.35, spark(0xE8FFF0, 0.4F));
        sphere(at, n(16), 0.2, glow(EspadaFx.ULQ_GREEN, 0.5F));
        shock(at, 0xE8FFF0, 3F, 8, Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().subtract(at));
    }

    private static void regen(Entity e, Vec3 at) {
        Vec3 c = e != null ? e.position() : at;
        riseSpiral(c, 0.7, 2.0, n(24), RND.nextFloat() * 6F, 2, glow(EspadaFx.ULQ_GREEN, 0.35F));
        ring(c.add(0, 1, 0), null, 1.0, n(14), mote(0xCFFFE0, 0.4F), -0.03);
    }

    private static void slash(Vec3 c, Vec3 dir, float r, int rgb) {
        double tilt = (RND.nextDouble() - 0.5) * 1.2;
        slashArc(c, dir, tilt, r, 2.2, n(28), streak(rgb, 0.5F), 0.05);
        if (hi()) slashArc(c, dir, tilt, r * 0.85, 2.0, n(18), glow(light(rgb, 0.4F), 0.25F), 0.0);
    }

    private static void bigFlash(Vec3 at, float r, int rgb) {
        flash(at, rgb, r);
    }

    private static void grab(Vec3 from, Vec3 to, int rgb) {
        line(from, to, 0.25, glow(rgb, 0.35F), 0.1);
        sphere(to, n(14), 0.2, smoke(rgb, 1.2F));
    }

    private static void auraPulse(Entity e, Vec3 at, float r, int rgb) {
        Vec3 c = e != null ? e.position() : at;
        shock(c.add(0, 0.1, 0), rgb == EspadaFx.ULQ_BLACK ? EspadaFx.ULQ_DEEP : rgb, r, 12, null);
        ring(c.add(0, 0.3, 0), null, r * 0.5, n(16), smoke(rgb, 1.4F), 0.1);
    }

    // =====================================================================================
    //  Barragan: Respira e pó
    // =====================================================================================
    /**
     * Respira (v1.3.2): simula no cliente os mesmos "bolos" de miasma do servidor (mesma direção, velocidade, crescimento
     * e parada em paredes) — a nuvem que se vê é exatamente a que envelhece. Roxo-escuro e negro por dentro, pó cor de
     * sépia caindo, fiapos lilás nas bordas (Alto+).
     */
    private static void breath(Vec3 origin, Vec3 vel, float angleDeg, int life, int rgb, int aux) {
        boolean ring = (aux & 1) != 0;
        int count = Math.max(1, aux >> 1);
        // Anel: a velocidade (com sinal) vem em x e p1 é o raio inicial. Leque: vetor = direção × velocidade.
        double speed = ring ? vel.x : vel.length();
        Vec3 dir = ring || Math.abs(speed) < 1.0E-6 ? new Vec3(1, 0, 0) : vel.scale(1.0 / speed);
        float yaw = (float) (Math.atan2(-dir.x, dir.z) * (180.0 / Math.PI));
        sound(origin, EspadaSounds.RESPIRA, 1.0F, 0.8F);
        Vec3[] pos = new Vec3[count];
        Vec3[] step = new Vec3[count];
        for (int k = 0; k < count; k++) {
            Vec3 d;
            if (ring) {
                double a = Math.PI * 2 * k / count;
                d = new Vec3(Math.cos(a), 0.02, Math.sin(a));
                pos[k] = origin.add(d.scale(angleDeg)).add(0, -0.2, 0);
            } else {
                float a = yaw - angleDeg / 2 + angleDeg * (k + 0.5F) / count;
                d = Vec3.directionFromRotation(4F, a);
                pos[k] = origin.add(d.scale(0.8));
            }
            step[k] = d.scale(speed);
        }
        puffs(pos, step, life, rgb, ring ? 1.4 : 1.2, 3);
    }

    /**
     * Respira em 360° (w10): os mesmos bolos do servidor saindo em TODAS as direções ({@link EspadaFx#respiraDirs}),
     * mais um "estouro" de miasma negro-roxo em volta de quem sopra e um anel de pó sépia no chão.
     */
    private static void breathSphere(Vec3 origin, double speed, int life, int rgb, int count) {
        sound(origin, EspadaSounds.RESPIRA, 1.4F, 0.6F);
        sound(origin, SoundEvents.WITHER_AMBIENT, 0.8F, 0.4F);
        Vec3[] dirs = EspadaFx.respiraDirs(count);
        Vec3[] pos = new Vec3[dirs.length];
        Vec3[] step = new Vec3[dirs.length];
        for (int k = 0; k < dirs.length; k++) {
            pos[k] = origin.add(dirs[k].scale(0.8));
            step[k] = dirs[k].scale(speed);
        }
        // o miasma sai de dentro dele: núcleo negro, casca roxa, pó caindo
        sphere(origin, n(40), 0.18, smoke(0x07020C, 2.4F));
        sphere(origin, n(30), 0.3, smoke(rgb, 2.0F));
        shock(origin.add(0, -0.6, 0), EspadaFx.RESPIRA_LIGHT, (float) (speed * life * 0.5), 18, null);
        ring(origin.add(0, -1.0, 0), null, 1.2, n(24), smoke(EspadaFx.AGED, 1.6F), 0.25);
        if (hi()) riseSpiral(origin.add(0, -1.2, 0), 1.6, 3.0, n(30), RND.nextFloat() * 6F, 2, wisp(EspadaFx.RESPIRA_LIGHT, 0.6F));
        puffs(pos, step, life, rgb, 1.4, 2);
    }

    /**
     * Simula os bolos de miasma (anda 2 ticks por passo, para em parede, cresce) e solta fumaça neles. Nuvens grandes
     * (muitos bolos) soltam menos fumaça por bolo. {@code blackOdds}: 1 em N bolinhas sai negra.
     */
    private static void puffs(Vec3[] pos, Vec3[] step, int life, int rgb, double r0, int blackOdds) {
        int count = pos.length;
        final int per = count > 12 ? 2 : 3;
        int steps = Math.max(1, life / 2);
        repeat(0, steps, 2, i -> {
            if (level() == null) return;
            int t = i * 2;
            double rad = Math.min(3.2, r0 + 0.035 * t);
            for (int k = 0; k < count; k++) {
                // anda 2 ticks (parando em parede, como o servidor)
                Vec3 next = pos[k].add(step[k].scale(2));
                BlockPos bp = BlockPos.containing(next);
                if (level().getBlockState(bp).getCollisionShape(level(), bp).isEmpty()) pos[k] = next;
                else step[k] = Vec3.ZERO;
                Vec3 c = pos[k];
                for (int q = 0; q < n(per); q++) {
                    Vec3 o = new Vec3(RND.nextGaussian(), RND.nextGaussian() * 0.6, RND.nextGaussian()).normalize().scale(rad * Math.sqrt(RND.nextDouble()));
                    boolean black = RND.nextInt(blackOdds) == 0;
                    add(smoke(black ? 0x0A0410 : rgb, (float) (1.6 + rad * 0.35)), c.add(o), step[k].scale(0.4).add(0, 0.005, 0));
                }
                if (i % 2 == 0) {
                    add(dust(EspadaFx.AGED, 0.9F), c.x + (RND.nextDouble() - 0.5) * rad, c.y + (RND.nextDouble() - 0.5) * rad,
                            c.z + (RND.nextDouble() - 0.5) * rad, 0, -0.04, 0);
                }
                if (hi() && i % 3 == 0) {
                    add(wisp(EspadaFx.RESPIRA_LIGHT, 0.6F), c.add((RND.nextDouble() - 0.5) * rad * 1.6, 0.3, (RND.nextDouble() - 0.5) * rad * 1.6),
                            new Vec3(0, 0.02, 0));
                }
            }
        });
    }

    /** Raios verdes: explosão (from == to) ou crepitar ao longo de um feixe. */
    private static void lightning(Vec3 from, Vec3 to, float radius, int rgb, int style) {
        boolean blast = from.distanceToSqr(to) < 0.01;
        int core = style == EspadaFx.STYLE_DARK ? EspadaFx.ULQ_GREEN : rgb;
        if (blast) {
            // Raios caindo do céu no ponto e em volta, e raios correndo pelo chão para fora.
            int waves = hi() ? 3 : 2;
            repeat(0, waves, 4, w -> {
                int bolts = Math.max(3, n(w == 0 ? 7 : 4));
                for (int k = 0; k < bolts; k++) {
                    double a = RND.nextDouble() * Math.PI * 2, r = (k == 0 && w == 0) ? 0 : RND.nextDouble() * radius * 0.8;
                    Vec3 g = from.add(Math.cos(a) * r, 0.05, Math.sin(a) * r);
                    Vec3 sky = g.add((RND.nextDouble() - 0.5) * 4, 16 + RND.nextDouble() * 10, (RND.nextDouble() - 0.5) * 4);
                    EspadaBeams.lightning(sky, g, k == 0 ? 0.9F : 0.5F, 7, core, 1.6);
                    flash(g.add(0, 0.3, 0), core, 1.4F);
                    add(ParticleTypes.ELECTRIC_SPARK, g.x, g.y + 0.3, g.z, 0, 0.2, 0);
                }
                int forks = Math.max(4, n(9));
                for (int k = 0; k < forks; k++) {
                    double a = Math.PI * 2 * (k + RND.nextDouble() * 0.6) / forks;
                    double rr = radius * (0.6 + RND.nextDouble() * 0.5);
                    Vec3 end = from.add(Math.cos(a) * rr, 0.15, Math.sin(a) * rr);
                    EspadaBeams.lightning(from.add(0, 0.2, 0), end, 0.35F, 6, core, 0.7);
                }
            });
            repeat(2, 6, 3, i -> {
                for (int k = 0; k < n(6); k++) {
                    double a = RND.nextDouble() * Math.PI * 2, r = RND.nextDouble() * radius;
                    add(ParticleTypes.ELECTRIC_SPARK, from.x + Math.cos(a) * r, from.y + 0.2 + RND.nextDouble() * 2, from.z + Math.sin(a) * r, 0, 0.1, 0);
                }
            });
            sound(from, SoundEvents.LIGHTNING_BOLT_IMPACT, 2.0F, 0.8F);
            return;
        }
        // Crepitar no feixe: arcos curtos que vão e voltam enquanto ele dura.
        double len = from.distanceTo(to);
        repeat(0, hi() ? 6 : 3, 4, i -> {
            int arcs = Math.max(2, n(4));
            for (int k = 0; k < arcs; k++) {
                double t0 = RND.nextDouble() * 0.85, t1 = Math.min(1.0, t0 + 0.12 + RND.nextDouble() * 0.2);
                Vec3 a = from.lerp(to, t0), b = from.lerp(to, t1);
                Vec3 off = new Vec3(RND.nextGaussian(), RND.nextGaussian(), RND.nextGaussian()).normalize().scale(radius * 0.45);
                EspadaBeams.lightning(a.add(off), b.add(off.scale(-0.6)), 0.28F, 5, core, Math.min(1.2, len * 0.04 + 0.4));
            }
        });
    }

    /** Desgarrón: os cinco arcos das garras na frente das mãos, quando as lâminas saem. */
    private static void clawLaunch(Entity e, Vec3 origin, Vec3 dirIn, float spacing, int count, int rgb) {
        Vec3 dir = dirIn.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : dirIn.normalize();
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        for (int k = 0; k < count; k++) {
            Vec3 c = origin.add(side.scale((k - (count - 1) / 2.0) * spacing));
            slashStreak(c, dir, Math.PI / 2, 1.6, rgb, 2.2);
        }
        flash(origin, rgb, 2.4F);
        shock(origin, light(rgb, 0.3F), 3.5F, 8, dir);
        if (hi()) speedLines(origin, dir, 2.0, n(18), 0.9, streak(0xE8F8FF, 0.5F));
        sound(origin, SoundEvents.PLAYER_ATTACK_SWEEP, 1.6F, 0.6F);
    }

    private static void crumble(Vec3 at) {
        sphere(at, n(10), 0.08, smoke(EspadaFx.AGED, 0.9F));
        for (int k = 0; k < n(6); k++) {
            add(dust(0x8A7A5A, 0.8F), at.x + (RND.nextDouble() - 0.5) * 0.5, at.y + (RND.nextDouble() - 0.5) * 0.5,
                    at.z + (RND.nextDouble() - 0.5) * 0.5, 0, -0.05, 0);
        }
    }

    // =====================================================================================
    //  Grimmjow: garras e rugido
    // =====================================================================================
    private static void claw(Vec3 from, Vec3 dirLen, float len, int index, int rgb) {
        Vec3 dir = dirLen.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : dirLen.normalize();
        Vec3 to = from.add(dir.scale(len));
        // Risco largo e brilhante: três camadas (borda azul, miolo branco, faíscas que ficam).
        line(from, to, 0.35, streak(rgb, 0.8F), 0.05);
        line(from, to, 0.5, glow(0xE8F8FF, 0.35F), 0.0);
        if (hi()) line(from.add(0, 0.25, 0), to.add(0, 0.25, 0), 0.6, streak(light(rgb, 0.4F), 0.5F), 0.05);
        repeat(2, 3, 3, i -> line(from, to, 1.4, spark(rgb, 0.25F), 0.4));
        EspadaBeams.beam(from, to, 0.55F, 6, rgb, EspadaFx.STYLE_BLUE);
        if (index == 4) {
            flash(to, rgb, 1.8F);
            groundImpact(to, 3, 0x505050);
        }
    }

    private static void roar(Entity e, Vec3 at, float r, int rgb) {
        Vec3 c = at.add(0, 1.4, 0);
        shock(at.add(0, 0.15, 0), rgb, r * 1.3F, 14, null);
        repeat(0, 3, 3, i -> shock(c, light(rgb, 0.4F), r * (0.5F + i * 0.4F), 10,
                e != null ? Vec3.directionFromRotation(0, e.getYRot()) : new Vec3(0, 0, 1)));
        speedLines(c, new Vec3(0, 0, 1), r * 0.6, n(20), 0.6, streak(0xFFFFFF, 0.5F));
        ring(at.add(0, 0.2, 0), null, r * 0.3, n(24), smoke(0x909090, 1.6F), 0.35);
        debris(at, n(14), r * 0.4, 0.4);
    }

    // =====================================================================================
    //  Tick visual de cada chefe (gancho EspadaBoss.clientTickHook)
    // =====================================================================================
    static void bossTick(EspadaBoss boss) {
        if (level() == null) return;
        Vec3 pos = boss.position();
        Player me = Minecraft.getInstance().player;
        double d2 = me != null ? me.distanceToSqr(boss) : Double.MAX_VALUE;
        // Segunda Etapa: a escuridão fica enquanto ele estiver perto e vivo.
        if (boss instanceof Ulquiorra u && u.form() == Ulquiorra.FORM_SEGUNDA && u.isAlive() && d2 < 48 * 48) {
            EspadaScreenFx.keepDark(0.4F);
        }
        if (!near(pos)) return;
        int t = boss.tickCount;
        if (boss.deathTime > 0) {
            deathFx(boss, boss.deathTime);
            return;
        }
        if (boss instanceof Ulquiorra u) {
            if (u.form() >= Ulquiorra.FORM_MURCIELAGO && t % 3 == 0) {
                // Penas de sombra caindo das asas.
                Vec3 back = Vec3.directionFromRotation(0, u.getYRot()).scale(-0.5);
                add(wisp(0x020302, 0.5F), pos.x + back.x + (RND.nextDouble() - 0.5) * 2.4, pos.y + 1.8 + RND.nextDouble(),
                        pos.z + back.z + (RND.nextDouble() - 0.5) * 2.4, 0, -0.03, 0);
            }
            if (u.form() == Ulquiorra.FORM_SEGUNDA && t % 2 == 0) {
                add(smoke(0x000000, 1.4F), pos.x + (RND.nextDouble() - 0.5) * 1.6, pos.y + RND.nextDouble() * 2.4,
                        pos.z + (RND.nextDouble() - 0.5) * 1.6, 0, 0.02, 0);
                if (hi() && t % 6 == 0) add(mote(EspadaFx.ULQ_GREEN, 0.3F), pos.x, pos.y + 1.8, pos.z, (RND.nextDouble() - 0.5) * 0.1, 0.05, (RND.nextDouble() - 0.5) * 0.1);
            }
            if (u.hierroBroken() && t % 4 == 0) add(spark(0xE8FFF0, 0.2F), pos.x, pos.y + 1 + RND.nextDouble(), pos.z, 0, 0.02, 0);
            if (u.isTransforming() && t % 2 == 0) {
                riseSpiral(pos, 1.2, 2.6, n(6), t * 0.3F, 1, wisp(u.form() >= 1 ? EspadaFx.ULQ_GREEN : 0x020302, 0.5F));
            }
        } else if (boss instanceof Barragan b) {
            int c = b.form() == Barragan.FORM_ARROGANTE ? EspadaFx.RESPIRA_LIGHT : EspadaFx.RESPIRA;
            if (t % (b.form() == Barragan.FORM_ARROGANTE ? 2 : 5) == 0) {
                add(smoke(c, 1.2F), pos.x + (RND.nextDouble() - 0.5) * 1.4, pos.y + 0.2 + RND.nextDouble() * 2.2,
                        pos.z + (RND.nextDouble() - 0.5) * 1.4, 0, 0.015, 0);
            }
            if (b.form() == Barragan.FORM_ARROGANTE && hi() && t % 7 == 0) {
                ring(pos.add(0, 0.15, 0), null, 3.8, n(10), smoke(EspadaFx.RESPIRA, 1.0F), 0.0);
            }
        } else if (boss instanceof Grimmjow g) {
            if (g.form() == Grimmjow.FORM_PANTERA && t % 3 == 0) {
                add(spark(EspadaFx.GRIMM_BLUE, 0.2F), pos.x + (RND.nextDouble() - 0.5), pos.y + 0.4 + RND.nextDouble() * 1.6,
                        pos.z + (RND.nextDouble() - 0.5), 0, 0.03, 0);
            }
            if (g.isTransforming() && t % 2 == 0) riseSpiral(pos, 1.0, 2.2, n(6), t * 0.4F, 1, glow(EspadaFx.GRIMM_BLUE, 0.3F));
        }
    }

    private static void deathFx(EspadaBoss boss, int t) {
        Vec3 pos = boss.position();
        if (boss instanceof Ulquiorra) {
            // Vira cinza de baixo para cima e o vento leva.
            float k = Math.min(1F, t / 100F);
            for (int i = 0; i < n(2 + (int) (k * 6)); i++) {
                double h = RND.nextDouble() * 2.0 * (0.3 + k);
                add(smoke(0x8A8A86, 0.7F), pos.x + (RND.nextDouble() - 0.5) * 0.7, pos.y + Math.min(2.0, h), pos.z + (RND.nextDouble() - 0.5) * 0.7,
                        0.03 + RND.nextDouble() * 0.04, 0.02 + RND.nextDouble() * 0.03, 0.01);
                if (RND.nextInt(3) == 0) add(dust(0x5A5A56, 0.5F), pos.x, pos.y + RND.nextDouble() * 2, pos.z, 0.05, 0.03, 0.02);
            }
            if (t % 10 == 0 && hi()) add(mote(EspadaFx.ULQ_GREEN, 0.25F), pos.x, pos.y + 1.6, pos.z, 0.02, 0.04, 0);
        } else if (boss instanceof Barragan) {
            if (t % 2 == 0) add(smoke(EspadaFx.AGED, 1.0F), pos.x + (RND.nextDouble() - 0.5), pos.y + RND.nextDouble() * 2.4,
                    pos.z + (RND.nextDouble() - 0.5), 0, -0.02, 0);
        } else if (t % 3 == 0) {
            add(spark(EspadaFx.GRIMM_BLUE, 0.2F), pos.x, pos.y + 1, pos.z, (RND.nextDouble() - 0.5) * 0.2, 0.05, (RND.nextDouble() - 0.5) * 0.2);
        }
    }

    private EspadaFxClient() {}
}
