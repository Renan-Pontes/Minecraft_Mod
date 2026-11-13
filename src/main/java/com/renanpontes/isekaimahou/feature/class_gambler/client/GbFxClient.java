package com.renanpontes.isekaimahou.feature.class_gambler.client;

import static com.renanpontes.isekaimahou.feature.class_gambler.client.FxKit.*;

import com.renanpontes.isekaimahou.feature.class_gambler.GbFx;
import com.renanpontes.isekaimahou.feature.class_gambler.GbFxNet;
import com.renanpontes.isekaimahou.feature.class_gambler.entity.ThrownCardEntity;
import com.renanpontes.isekaimahou.feature.class_gambler.luck.LuckBand;

import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * SÓ CLIENTE. Coreografia de anime do Apostador: cartas voando com rastro, fichas e moedas pulando, o anel de naipes
 * da rolagem (com um "giro" de caça-níquel na cor da faixa), a explosão do JACKPOT (fichas, fogos, raios dourados),
 * as auras do Jackpot, os rolos do caça-níquel parando, a roleta girando e os dados do destino.
 */
public final class GbFxClient {
    private static final int GOLD = GbFx.GOLD;
    private static final int PINK = GbFx.PINK;
    private static final int FELT = GbFx.FELT;
    private static final int WHITE = 0xFFFFFF;
    /** Cores das fichas de cassino. */
    private static final int[] CHIPS = {0xD8303A, 0x2E6BD8, 0x2ECC71, 0x26222C, GOLD, PINK};

    private static ItemParticleOption nugget;
    private static ItemParticleOption emerald;

    public static void init() {
        GbFxNet.clientBurst = GbFxClient::onBurst;
        GbFxNet.clientTrail = GbFxClient::trail;
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> FxKit.tick());
    }

    private static ParticleOptions coin(boolean green) {
        if (nugget == null) {
            nugget = new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.GOLD_NUGGET));
            emerald = new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.EMERALD));
        }
        return green ? emerald : nugget;
    }

    private static int chip() {
        return CHIPS[RND.nextInt(CHIPS.length)];
    }

    /** Cartas lançadas: rastro em fita (duas faixas) e brilho girando. */
    private static void trail(Entity e) {
        if (!(e instanceof ThrownCardEntity c)) return;
        Vec3 p = e.position();
        if (!near(p)) return;
        Vec3 v = e.getDeltaMovement();
        if (v.lengthSqr() < 1.0E-4) return;
        int rgb = c.color();
        Vec3 side = basis(v)[0].scale(0.08);
        add(streak(rgb, 0.4F), p.add(side), v.scale(0.03));
        if (hi()) add(streak(light(rgb, 0.5F), 0.3F), p.subtract(side), v.scale(0.03));
        if (e.tickCount % 3 == 0) add(glow(WHITE, 0.2F), p, Vec3.ZERO);
    }

    private static void onBurst(GbFxNet.Burst p) {
        if (level() == null || !near(p.pos())) return;
        try {
            switch (p.kind()) {
                case GbFxNet.ROLL -> roll(p);
                case GbFxNet.COINS -> coins(p.pos(), (int) Math.max(2, p.size()));
                case GbFxNet.JACKPOT -> jackpot(p);
                case GbFxNet.JACKPOT_AURA -> jackpotAura(p);
                case GbFxNet.IMMORTAL_AURA -> immortalAura(p);
                case GbFxNet.CARD_BURST -> cardBurst(p);
                case GbFxNet.GROUND_RING -> groundRing(p);
                case GbFxNet.BLAST -> blast(p);
                case GbFxNet.CARD_FAN -> cardFan(p);
                case GbFxNet.SLOT_REEL -> slotReel(p);
                case GbFxNet.SLOT_END -> slotEnd(p);
                case GbFxNet.ALL_IN -> allIn(p);
                case GbFxNet.DICE_TWELVE -> diceTwelve(p);
                case GbFxNet.DICE_SNAKE -> diceSnake(p);
                case GbFxNet.GUM_SNAP -> gumSnap(p);
                case GbFxNet.IDLE_WIN -> idleWin(p);
                case GbFxNet.SPIN -> spin(p);
                case GbFxNet.FATE_COIN -> fateCoin(p);
                default -> {}
            }
        } catch (RuntimeException ignored) {
            // efeito quebrado nunca derruba o cliente
        }
    }

    /** Moedas saltando + fichas coloridas girando. */
    static void coins(Vec3 at, int count) {
        for (int i = 0; i < n(count); i++) {
            double a = RND.nextDouble() * Math.PI * 2;
            double s = 0.08 + RND.nextDouble() * 0.15;
            add(coin(i % 4 == 0), at, new Vec3(Math.cos(a) * s, 0.2 + RND.nextDouble() * 0.2, Math.sin(a) * s));
        }
        for (int i = 0; i < n(Math.max(2, count / 2)); i++) {
            double a = RND.nextDouble() * Math.PI * 2;
            double s = 0.1 + RND.nextDouble() * 0.15;
            add(spark(chip(), 0.3F), at, new Vec3(Math.cos(a) * s, 0.15 + RND.nextDouble() * 0.2, Math.sin(a) * s));
        }
    }

    private static void roll(GbFxNet.Burst p) {
        Entity e = entity(p.entity());
        Vec3 head = e == null ? p.pos().add(0, 2.3, 0) : e.position().add(0, e.getBbHeight() + 0.45, 0);
        LuckBand b = p.data() >= 0 && p.data() < LuckBand.values().length ? LuckBand.values()[p.data()] : LuckBand.EVEN;
        int rgb = b.rgb;
        // o anel de naipes gira rápido e desacelera (como um rolo) na cor da faixa
        repeat(0, 4, 2, i -> {
            Entity cur = entity(p.entity());
            Vec3 h = cur == null ? head : cur.position().add(0, cur.getBbHeight() + 0.45, 0);
            ring(h, null, 0.55, n(b.good ? 12 : 8), glow(rgb, b.good ? 0.25F : 0.18F), 0.0);
        });
        switch (b) {
            case BUST, BAD -> later(8, () -> sphere(head, n(b == LuckBand.BUST ? 10 : 6), 0.05, smoke(0x3A3446, 1.2F)));
            case GREAT -> later(8, () -> star(head, new Vec3(0, 0, 1), 0.6, 8, glow(rgb, 0.18F), 0.08));
            case NATURAL -> later(8, () -> {
                flash(head, GOLD, 1.0F);
                coins(head, 6);
                sphere(head, n(12), 0.2, ParticleTypes.FIREWORK);
            });
            case JACKPOT -> later(8, () -> {
                flash(head, PINK, 1.4F);
                coins(head, 10);
                sphere(head, n(20), 0.3, ParticleTypes.FIREWORK);
            });
            default -> {}
        }
    }

    private static void jackpot(GbFxNet.Burst p) {
        Entity e = entity(p.entity());
        Vec3 base = e == null ? p.pos() : e.position();
        Vec3 c = base.add(0, 1, 0);
        // antecipação: três "7" de luz se alinhando sobre a cabeça, um a um
        for (int k = 0; k < 3; k++) {
            final int kk = k;
            later(k * 3, () -> {
                Vec3 s = c.add(0, 1.6, 0).add(basis(new Vec3(0, 0, 1))[0].scale((kk - 1) * 0.6));
                line(s.add(-0.18, 0.25, 0), s.add(0.18, 0.25, 0), 0.06, glow(GOLD, 0.2F), 0);
                line(s.add(0.18, 0.25, 0), s.add(-0.05, -0.25, 0), 0.06, glow(GOLD, 0.2F), 0);
                sound(s, SoundEvents.NOTE_BLOCK_BIT.value(), 0.8F, 1.0F + kk * 0.25F);
            });
        }
        // impacto: explosão de fichas, fogos, raios dourados e onda rosa
        later(9, () -> {
            nova(c, GOLD, 2.2F, false);
            shock(base.add(0, 0.1, 0), PINK, 5F, 14, null);
            coins(c.add(0, 0.8, 0), 30);
            sphere(c, n(40), 0.4, ParticleTypes.FIREWORK);
            if (hi()) sphere(c, n(30), 0.5, ParticleTypes.TOTEM_OF_UNDYING);
            for (int i = 0; i < n(8); i++) {
                double a = Math.PI * 2 * i / 8;
                line(c, c.add(Math.cos(a) * 3, 2.5, Math.sin(a) * 3), 0.25, glow(i % 2 == 0 ? GOLD : PINK, 0.25F), 0);
            }
            pillar(base, GOLD, 1.0F, 10F, 24);
        });
    }

    private static void jackpotAura(GbFxNet.Burst p) {
        Entity e = entity(p.entity());
        if (e == null) return;
        long age = p.data();
        double a = age * 0.45;
        double y = e.getY() + (age % 20) * 0.1;
        for (int k = 0; k < 2; k++) {
            double ang = a + Math.PI * k;
            add(glow(k == 0 ? GOLD : PINK, 0.28F), e.getX() + Math.cos(ang) * 0.8, y, e.getZ() + Math.sin(ang) * 0.8, 0, 0.02, 0);
        }
        if (hi()) add(spark(chip(), 0.25F), e.getX() + (RND.nextDouble() - 0.5) * 1.2, e.getY() + RND.nextDouble() * 2, e.getZ()
                + (RND.nextDouble() - 0.5) * 1.2, 0, 0.03, 0);
        if (age % 10 == 0) coins(e.position().add(0, e.getBbHeight() + 0.2, 0), 2);
    }

    private static void immortalAura(GbFxNet.Burst p) {
        Entity e = entity(p.entity());
        if (e == null) return;
        long age = p.data();
        double h = e.getBbHeight();
        double a = age * 0.6;
        for (int k = 0; k < 3; k++) {
            double ang = a + Math.PI * 2 * k / 3;
            double y = e.getY() + ((age * 0.12 + k * 0.6) % (h + 0.6));
            add(glow(k == 0 ? 0x4AFFB4 : k == 1 ? WHITE : PINK, 0.28F), e.getX() + Math.cos(ang), y, e.getZ() + Math.sin(ang), 0, 0.01, 0);
        }
        if (age % 6 == 0) add(ParticleTypes.NOTE, e.getX(), e.getY() + h + 0.4, e.getZ(), RND.nextDouble(), 0, 0);
        if (age % 12 == 0) add(ParticleTypes.HEART, e.getX(), e.getY() + h * 0.6, e.getZ(), 0, 0.05, 0);
    }

    private static void cardBurst(GbFxNet.Burst p) {
        Vec3 at = p.pos();
        int rgb = p.rgb();
        // estouro cinético: clarão na cor, naipe de luz e faíscas
        flash(at, rgb, 1.1F);
        sphere(at, n(14), 0.3, spark(rgb, 0.3F));
        star(at, new Vec3(0, 1, 0), 0.6, 4, glow(light(rgb, 0.4F), 0.2F), 0.08);
        if (hi()) sphere(at, n(6), 0.1, smoke(0x6A6A6A, 0.9F));
        shake(at, 5F, 0.25F, 3);
    }

    private static void groundRing(GbFxNet.Burst p) {
        Vec3 c = p.pos();
        float r = Math.max(1F, p.size());
        int pts = n((int) Math.min(56, r * 6));
        double off = RND.nextDouble() * Math.PI * 2;
        for (int i = 0; i < pts; i++) {
            double a = off + Math.PI * 2 * i / pts;
            add(glow(i % 6 == 0 ? WHITE : p.rgb(), 0.25F), c.x + Math.cos(a) * r, c.y + 0.1, c.z + Math.sin(a) * r, 0, 0.01, 0);
        }
    }

    private static void blast(GbFxNet.Burst p) {
        Vec3 c = p.pos().add(0, 0.4, 0);
        float r = Math.max(1F, p.size());
        nova(c, p.rgb(), Math.min(4F, r * 0.7F), true);
        circle(p.pos().add(0, 0.05, 0), p.rgb(), r, 20, 2, 0, false);
        for (int i = 0; i < n((int) (6 + r * 2)); i++) {
            double a = RND.nextDouble() * Math.PI * 2;
            double s = 0.15 + RND.nextDouble() * 0.2;
            add(spark(chip(), 0.35F), c, new Vec3(Math.cos(a) * s, 0.2 + RND.nextDouble() * 0.2, Math.sin(a) * s));
        }
    }

    private static void cardFan(GbFxNet.Burst p) {
        Vec3 at = p.pos();
        Vec3 dir = p.vec().lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : p.vec().normalize();
        // leque de cartas abrindo na mão (retângulos de luz em arco)
        Vec3[] b = basis(dir);
        for (int i = -2; i <= 2; i++) {
            final int k = i;
            later(k + 2, () -> {
                double ang = k * 0.3;
                Vec3 up = b[1].scale(Math.cos(ang)).add(b[0].scale(Math.sin(ang)));
                Vec3 tip = at.add(up.scale(0.45));
                line(at, tip, 0.06, glow(k % 2 == 0 ? p.rgb() : WHITE, 0.16F), 0);
            });
        }
        add(glow(light(p.rgb(), 0.4F), 0.5F), at, Vec3.ZERO);
    }

    private static void slotReel(GbFxNet.Burst p) {
        Vec3 at = p.pos();
        int sym = p.data();
        int rgb = switch (sym % 4) {
            case 0 -> GOLD;
            case 1 -> PINK;
            case 2 -> 0xD8303A;
            default -> 0x5AC8FF;
        };
        // o rolo trava: símbolos passando de cima para baixo e um clarão na cor
        repeat(0, 3, 1, i -> add(glow(i == 2 ? rgb : WHITE, 0.3F), at.add(0, 0.4 - i * 0.4, 0), Vec3.ZERO));
        later(3, () -> {
            flash(at, rgb, 0.7F);
            ring(at, new Vec3(0, 0, 1), 0.3, n(8), spark(rgb, 0.2F), 0.1);
        });
    }

    private static void slotEnd(GbFxNet.Burst p) {
        Vec3 c = p.pos().add(0, 1.1, 0);
        sphere(c, n(20), 0.25, spark(GOLD, 0.3F));
        coins(c, 8);
        vanish(p.pos(), 0x6A5A78, 0.8F);
    }

    private static void allIn(GbFxNet.Burst p) {
        Vec3 a = p.pos(), b = p.vec();
        // o fio da aposta: fio dourado e rosa trançado, desenhado de um para o outro
        int steps = 4;
        for (int s = 0; s < steps; s++) {
            final int k = s;
            later(s, () -> {
                Vec3 x = a.lerp(b, k / (double) steps), y = a.lerp(b, (k + 1) / (double) steps);
                helix(x, y, 0.12, 1.5, n(10), k * 2F, glow(GOLD, 0.2F));
                helix(x, y, 0.12, 1.5, n(10), k * 2F + (float) Math.PI, glow(PINK, 0.2F));
            });
        }
        later(steps, () -> {
            flash(a, GOLD, 0.8F);
            flash(b, PINK, 0.8F);
        });
    }

    private static void diceTwelve(GbFxNet.Burst p) {
        Vec3 c = p.pos();
        line(c.add(0, 10, 0), c, 0.25, glow(WHITE, 0.35F), 0.1);
        later(1, () -> {
            nova(c, GOLD, 1.8F, true);
            pillar(c.subtract(0, 1, 0), GOLD, 0.5F, 6F, 16);
        });
    }

    private static void diceSnake(GbFxNet.Burst p) {
        Vec3 c = p.pos();
        sphere(c, n(14), 0.12, smoke(0x2A2236, 1.6F));
        ring(c, null, 0.6, n(10), glow(0x6A6478, 0.2F), -0.05);
    }

    private static void gumSnap(GbFxNet.Burst p) {
        Vec3 c = p.pos();
        flash(c, 0xFF6EC7, 1.2F);
        sphere(c, n(18), 0.35, glow(0xFF6EC7, 0.3F));
        shock(c, 0xFFA8DD, 2.2F, 8, new Vec3(0, 1, 0));
    }

    private static void idleWin(GbFxNet.Burst p) {
        Vec3 c = p.pos();
        float r = Math.max(3F, p.size());
        for (int k = 0; k < 4; k++) {
            final int kk = k;
            later(k * 2, () -> shock(c.add(0, 0.1, 0), kk % 2 == 0 ? 0x3AE07A : PINK, r * (0.5F + kk * 0.4F), 12, null));
        }
        nova(c.add(0, 1, 0), GOLD, 2.5F, true);
        sphere(c.add(0, 1, 0), n(50), 0.5, ParticleTypes.FIREWORK);
        if (hi()) sphere(c, n(40), 0.6, ParticleTypes.TOTEM_OF_UNDYING);
        coins(c.add(0, 1, 0), 30);
    }

    private static void spin(GbFxNet.Burst p) {
        Vec3 c = p.pos();
        float r = Math.max(1.5F, p.size());
        int ticks = Math.max(10, p.data());
        // fichas correndo na borda da roleta, desacelerando
        repeat(0, ticks / 2, 2, i -> {
            double speed = 1.0 - i / (ticks / 2.0);
            double a = i * 0.9 * (0.4 + speed);
            add(glow(i % 2 == 0 ? 0xD8303A : 0x26222C, 0.3F), c.x + Math.cos(a) * r * 0.9, c.y + 0.35, c.z + Math.sin(a) * r * 0.9, 0, 0, 0);
            add(glow(WHITE, 0.2F), c.x + Math.cos(a + 0.2) * r * 0.75, c.y + 0.4, c.z + Math.sin(a + 0.2) * r * 0.75, 0, 0, 0);
        });
        circle(c.add(0, 0.05, 0), FELT, r, ticks + 10, 3, 0, false);
    }

    private static void fateCoin(GbFxNet.Burst p) {
        Vec3 top = p.pos();
        boolean heads = p.data() == 1;
        int rgb = heads ? 0xFFE27A : 0x5A3A78;
        // a moeda gira no ar (anel achatando) e mostra o lado
        repeat(0, 5, 1, i -> {
            double t = i * 0.6;
            ring(top.add(0, i * 0.08, 0), new Vec3(Math.cos(t), 0, Math.sin(t)), 0.25, n(10), glow(rgb, 0.2F), 0);
        });
        later(6, () -> {
            flash(top.add(0, 0.4, 0), rgb, 0.9F);
            if (heads) sphere(top.add(0, 0.4, 0), n(10), 0.15, spark(GOLD, 0.25F));
            else sphere(top.add(0, 0.4, 0), n(8), 0.08, smoke(0x2A1A38, 1.0F));
        });
    }

    private GbFxClient() {}
}
