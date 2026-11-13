package com.renanpontes.isekaimahou.client.spellfx;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

import com.renanpontes.isekaimahou.client.fx.FxQuality;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.ElementReactions.Reaction;
import com.renanpontes.isekaimahou.core.MahouConfig.Graphics;
import com.renanpontes.isekaimahou.glyph.fx.FxBurst;
import com.renanpontes.isekaimahou.glyph.fx.FxOptions;
import com.renanpontes.isekaimahou.glyph.fx.SpellFxParticles;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * SÓ CLIENTE. Monta os efeitos compostos de {@link FxBurst} com as partículas {@code fx_*}. Cada efeito tem três atos —
 * antecipação (círculo + partículas convergindo), impacto (clarão, onda, estilhaços, som vem do servidor) e rescaldo
 * (fumaça, brasas, brilhos que ficam alguns ticks) — e uma "assinatura" por família de elemento:
 * <ul>
 *   <li>fogo: chafariz de brasas, fumaça escura que sobe;</li>
 *   <li>água: coroa de respingos, névoa;</li>
 *   <li>gelo/cristal: estilhaços e espinhos, neve caindo;</li>
 *   <li>terra/areia: pedras pesadas voando, poeira no chão;</li>
 *   <li>vento: espiral de riscos;</li>
 *   <li>raio: galhos em zigue-zague descendo;</li>
 *   <li>luz: raios subindo, mini pilar;</li>
 *   <li>sombra: implosão de fiapos e depois estouro;</li>
 *   <li>vazio/gravidade: sucção forte + anel escuro;</li>
 *   <li>arcano: runas em órbita, selo;</li>
 *   <li>natureza: folhas e pétalas.</li>
 * </ul>
 * Tudo passa por {@link FxQuality}: quantidades com {@code count}, camadas extras só em HIGH/ULTRA, distância máxima
 * pelo {@code range}. Os efeitos com duração rodam num agendador próprio (limitado) que é limpo ao sair do mundo.
 */
public final class FxBurstClient {
    /** Famílias visuais (cada elemento cai em uma). */
    enum Fam { FIRE, WATER, ICE, EARTH, WIND, LIGHTNING, LIGHT, SHADOW, VOID, ARCANE, NATURE }

    private static final int MAX_TASKS = 96;
    private static final List<Task> TASKS = new ArrayList<>();
    private static final List<Task> PENDING = new ArrayList<>();
    private static boolean ticking;
    private static long burstTick = Long.MIN_VALUE;
    private static int burstsThisTick;

    /** Tarefa agendada: espera {@code delay} ticks e depois roda {@code step(i)} por {@code runs} vezes a cada {@code every}. */
    private static final class Task {
        int delay;
        final int runs;
        final int every;
        final IntConsumer step;
        int done;
        int wait;

        Task(int delay, int runs, int every, IntConsumer step) {
            this.delay = delay;
            this.runs = runs;
            this.every = Math.max(1, every);
            this.step = step;
        }
    }

    // =====================================================================================
    //  Entrada
    // =====================================================================================

    public static void receive(FxBurst.Payload p) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        Vec3 pos = new Vec3(p.x(), p.y(), p.z());
        boolean big = p.kind() == FxBurst.NOVA || p.kind() == FxBurst.DOMAIN || p.kind() == FxBurst.SKY_STRIKE || p.tier() >= 4;
        double max = big ? Math.max(96, FxQuality.range()) : FxQuality.range();
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        if (cam.distanceToSqr(pos) > max * max) return;
        // Muitos efeitos no mesmo tick (área acertando 20 alvos): os primeiros saem inteiros, os outros viram um
        // impacto simples, e depois de um teto são ignorados — o FPS não despenca numa luta grande
        long now = level.getGameTime();
        if (now != burstTick) {
            burstTick = now;
            burstsThisTick = 0;
        }
        int full = FxQuality.atLeast(Graphics.HIGH) ? 10 : FxQuality.atLeast(Graphics.MEDIUM) ? 6 : 3;
        int n = ++burstsThisTick;
        if (n > full * 3) return;
        int tier = n > full && p.kind() == FxBurst.IMPACT ? Math.min(1, p.tier()) : p.tier();
        Element e = p.elementOrArcane();
        int rgb = p.rgb() & 0xFFFFFF;
        Vec3 dir = new Vec3(p.dx(), p.dy(), p.dz());
        SpellFxClient.LOCAL = true;
        try {
            switch (p.kind()) {
                case FxBurst.IMPACT -> impact(level, e, rgb, pos, dir, p.scale(), tier);
                case FxBurst.GATHER -> gather(level, e, rgb, pos, p.scale(), p.tier(), p.data(), p.ticks());
                case FxBurst.NOVA -> nova(level, e, rgb, pos, p.scale(), p.tier());
                case FxBurst.AREA -> area(level, e, rgb, pos, p.scale(), p.tier(), p.ticks());
                case FxBurst.AURA -> aura(level, e, rgb, p.data(), p.scale(), p.tier(), p.ticks());
                case FxBurst.BEAM -> beam(level, e, rgb, pos, dir, p.scale(), p.tier());
                case FxBurst.PORTAL -> portal(level, e, rgb, pos, dir, p.scale(), p.tier(), p.ticks());
                case FxBurst.DOMAIN -> domain(level, e, rgb, pos, p.scale(), p.tier(), p.ticks());
                case FxBurst.SKY_STRIKE -> skyStrike(level, e, rgb, pos, p.scale(), p.tier(), p.ticks());
                case FxBurst.REACTION -> reaction(level, pos, dir, p.data(), p.scale(), p.ticks() == 1 || n > full);
                case FxBurst.SLASH -> slash(level, e, rgb, pos, dir, p.scale(), p.tier());
                default -> { }
            }
        } finally {
            SpellFxClient.LOCAL = false;
        }
    }

    static void onClientTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            if (!TASKS.isEmpty()) clear();
            return;
        }
        if (mc.isPaused() || TASKS.isEmpty() && PENDING.isEmpty()) return;
        if (!PENDING.isEmpty()) {
            TASKS.addAll(PENDING);
            PENDING.clear();
        }
        ticking = true;
        SpellFxClient.LOCAL = true;
        try {
            TASKS.removeIf(t -> {
                if (t.delay > 0) {
                    t.delay--;
                    return false;
                }
                if (t.wait > 0) {
                    t.wait--;
                    return false;
                }
                try {
                    t.step.accept(t.done);
                } catch (RuntimeException ex) {
                    return true; // efeito quebrado (entidade sumiu etc.): descarta sem derrubar o jogo
                }
                t.done++;
                t.wait = t.every - 1;
                return t.done >= t.runs;
            });
        } finally {
            ticking = false;
            SpellFxClient.LOCAL = false;
        }
    }

    static void clear() {
        TASKS.clear();
        PENDING.clear();
    }

    private static void schedule(int delay, int runs, int every, IntConsumer step) {
        if (runs <= 0) return;
        List<Task> into = ticking ? PENDING : TASKS;
        if (TASKS.size() + PENDING.size() >= MAX_TASKS) return;
        into.add(new Task(Math.max(0, delay), runs, every, step));
    }

    private static void after(int delay, Runnable r) {
        if (delay <= 0) {
            r.run();
            return;
        }
        schedule(delay, 1, 1, i -> r.run());
    }

    // =====================================================================================
    //  Família
    // =====================================================================================

    static Fam fam(Element e) {
        return switch (e) {
            case FIRE, MAGMA, SUN, HELLFIRE -> Fam.FIRE;
            case WATER, PURITY, ABYSS, STEAM -> Fam.WATER;
            case ICE, CRYSTAL -> Fam.ICE;
            case EARTH, SAND -> Fam.EARTH;
            case WIND -> Fam.WIND;
            case LIGHTNING -> Fam.LIGHTNING;
            case LIGHT, ASTRAL -> Fam.LIGHT;
            case SHADOW, TWILIGHT -> Fam.SHADOW;
            case VOID, GRAVITY -> Fam.VOID;
            case NATURE -> Fam.NATURE;
            default -> Fam.ARCANE;
        };
    }

    /** Estilhaço/detrito da família (cai com gravidade quando faz sentido). */
    private static ParticleOptions debris(Fam f, Element e, int rgb, float size) {
        return switch (f) {
            case FIRE -> o(SpellFxParticles.EMBER, rgb, size * 1.2F, 0);
            case WATER -> o(SpellFxParticles.DROPLET, rgb, size * 1.3F, 0);
            case ICE -> o(SpellFxParticles.FROST, rgb, size * 1.5F, 0);
            case EARTH -> o(SpellFxParticles.STONE, e == Element.SAND ? 0xE3C77A : FxDraw.darken(e.color, 0.85F), size * 1.8F, 0);
            case WIND -> o(SpellFxParticles.WIND, rgb, size * 0.8F, 0);
            case LIGHTNING -> o(SpellFxParticles.SPARK, FxDraw.lighten(rgb, 0.5F), size, 0);
            case LIGHT -> o(SpellFxParticles.MOTE, rgb, size * 1.2F, 0);
            case SHADOW, VOID -> o(SpellFxParticles.WISP, rgb, size * 1.2F, 0);
            case NATURE -> o(SpellFxParticles.LEAF, rgb, size * 1.3F, 0);
            case ARCANE -> o(SpellFxParticles.RUNE, rgb, size, 0);
        };
    }

    /** O que fica no ar depois (fumaça, névoa, poeira, brilhos). */
    private static ParticleOptions linger(Fam f, Element e, int rgb, float size) {
        return switch (f) {
            case FIRE -> o(SpellFxParticles.SMOKE, e == Element.SUN ? 0x8A7A60 : 0x40342E, size * 1.4F, 0);
            case WATER -> o(SpellFxParticles.SMOKE, 0xDDEEFF, size * 1.3F, 0);
            case ICE -> o(SpellFxParticles.FROST, FxDraw.lighten(rgb, 0.5F), size * 0.8F, 0);
            case EARTH -> o(SpellFxParticles.SMOKE, e == Element.SAND ? 0xE8D49A : 0xA08A70, size * 1.5F, 0);
            case WIND -> o(SpellFxParticles.WIND, FxDraw.lighten(rgb, 0.3F), size * 0.7F, 0);
            case LIGHTNING -> o(SpellFxParticles.GLOW, FxDraw.lighten(rgb, 0.3F), size * 0.8F, 0);
            case LIGHT -> o(SpellFxParticles.MOTE, FxDraw.lighten(rgb, 0.4F), size, 0);
            case SHADOW -> o(SpellFxParticles.WISP, rgb, size * 1.3F, 0);
            case VOID -> o(SpellFxParticles.WISP, FxDraw.darken(rgb, 0.6F), size * 1.5F, 0);
            case NATURE -> o(SpellFxParticles.LEAF, rgb, size, 0);
            case ARCANE -> o(SpellFxParticles.RUNE, rgb, size * 0.9F, 0);
        };
    }

    // =====================================================================================
    //  IMPACTO
    // =====================================================================================

    static void impact(ClientLevel lvl, Element e, int rgb, Vec3 pos, Vec3 normal, float size, int tier) {
        final int t = Mth.clamp(tier, 0, 5);
        final float s = Mth.clamp(size, 0.2F, 6F);
        RandomSource r = lvl.random;
        Fam f = fam(e);
        Vec3 n = normal.lengthSqr() < 1.0E-4 ? new Vec3(0, 1, 0) : normal.normalize();
        Vec3 c = pos.add(n.scale(0.1));
        int light = FxDraw.lighten(rgb, 0.5F);

        // Clarão (núcleo branco por cima nos tiers altos)
        flash(lvl, c, rgb, (0.6F + 0.35F * t) * s);
        if (t >= 2 && FxQuality.atLeast(Graphics.MEDIUM)) flash(lvl, c, FxDraw.lighten(rgb, 0.85F), (0.3F + 0.18F * t) * s);
        // Onda de choque (+ segunda, mais larga e atrasada)
        if (t >= 1) ring(lvl, c, n, rgb, (0.8F + 0.6F * t) * s, 8 + 2 * t);
        if (t >= 3 && FxQuality.atLeast(Graphics.MEDIUM)) {
            after(3, () -> ring(lvl, c, n, FxDraw.lighten(rgb, 0.4F), (1.3F + 0.9F * t) * s, 12 + 2 * t));
        }
        // Núcleo: partícula do elemento voando para fora
        ParticleOptions sig = ElementFx.style(e, rgb, 1.0F + 0.12F * t);
        int k = FxQuality.count(6 + 5 * t);
        for (int i = 0; i < k; i++) {
            Vec3 v = unit(r).add(n.scale(0.6)).normalize().scale((0.1 + 0.05 * t) * s * (0.6 + r.nextDouble() * 0.7));
            add(lvl, sig, c, v);
        }
        ParticleOptions g = ElementFx.glow(rgb, 1.3F + 0.2F * t);
        k = FxQuality.count(4 + 3 * t);
        for (int i = 0; i < k; i++) add(lvl, g, c.add(unit(r).scale(0.2 * s)), unit(r).scale(0.05 + 0.02 * t));
        // Faíscas
        ParticleOptions sp = o(SpellFxParticles.SPARK, light, 0.8F + 0.1F * t, 0);
        k = FxQuality.count(3 + 3 * t);
        for (int i = 0; i < k; i++) {
            Vec3 v = unit(r).add(n.scale(0.9)).normalize().scale(0.25 + 0.06 * t + r.nextDouble() * 0.15);
            add(lvl, sp, c, v);
        }
        // Detritos
        if (t >= 1) {
            ParticleOptions d = debris(f, e, rgb, 0.9F + 0.1F * t);
            k = FxQuality.count(2 + 3 * t);
            for (int i = 0; i < k; i++) {
                Vec3 v = unit(r).add(n.scale(1.2)).normalize().scale((0.2 + 0.05 * t) * (0.6 + r.nextDouble() * 0.8));
                add(lvl, d, c, v);
            }
        }
        flourish(lvl, f, e, rgb, c, n, s, t);
        // Rescaldo: o que fica no ar
        if (t >= 1) {
            final float ls = 0.8F + 0.1F * t;
            final double spread = (0.3 + 0.15 * t) * s;
            final ParticleOptions lo = linger(f, e, rgb, ls);
            int runs = FxQuality.atLeast(Graphics.HIGH) ? 4 + t : 2 + t / 2;
            schedule(2, runs, 2, i -> {
                int m = FxQuality.count(1 + t / 2);
                for (int j = 0; j < m; j++) {
                    add(lvl, lo, c.add((r.nextDouble() - 0.5) * spread * 2, r.nextDouble() * spread, (r.nextDouble() - 0.5) * spread * 2),
                            new Vec3((r.nextDouble() - 0.5) * 0.02, 0.02 + r.nextDouble() * 0.03, (r.nextDouble() - 0.5) * 0.02));
                }
            });
        }
        if (t >= 3 && n.y > 0.5) decal(lvl, pos.add(0, 0.05, 0), rgb, (1.0F + 0.5F * t) * s, 24 + 6 * t, t);
        if (t >= 4) pillar(lvl, pos, rgb, 0.5F + 0.15F * t, 3.0F + t, 14);
    }

    /** A "assinatura" de cada família no impacto. */
    private static void flourish(ClientLevel lvl, Fam f, Element e, int rgb, Vec3 c, Vec3 n, float s, int t) {
        RandomSource r = lvl.random;
        int light = FxDraw.lighten(rgb, 0.5F);
        switch (f) {
            case FIRE -> {
                // Chafariz de brasas
                ParticleOptions em = o(SpellFxParticles.EMBER, rgb, 1.2F + 0.15F * t, 0);
                int k = FxQuality.count(4 + 3 * t);
                for (int i = 0; i < k; i++) {
                    add(lvl, em, c, new Vec3((r.nextDouble() - 0.5) * 0.25, 0.15 + r.nextDouble() * (0.2 + 0.05 * t), (r.nextDouble() - 0.5) * 0.25));
                }
                if (t >= 2) ring(lvl, c, n, 0xFFB050, (0.6F + 0.4F * t) * s, 6 + t);
            }
            case WATER -> {
                // Coroa de respingos
                ParticleOptions dr = o(SpellFxParticles.DROPLET, rgb, 1.3F, 0);
                int k = FxQuality.count(8 + 3 * t);
                for (int i = 0; i < k; i++) {
                    double a = Math.PI * 2 * i / k;
                    double sp = 0.12 + 0.03 * t;
                    add(lvl, dr, c.add(Math.cos(a) * 0.3, 0, Math.sin(a) * 0.3), new Vec3(Math.cos(a) * sp, 0.3 + r.nextDouble() * 0.15, Math.sin(a) * sp));
                }
                if (t >= 2) ring(lvl, c, n, FxDraw.lighten(rgb, 0.6F), (0.5F + 0.5F * t) * s, 10);
            }
            case ICE -> {
                // Espinhos de gelo (faíscas brancas para cima) + estilhaços grandes
                ParticleOptions spike = o(SpellFxParticles.SPARK, 0xF0FCFF, 1.2F + 0.15F * t, 8);
                int k = FxQuality.count(4 + 2 * t);
                for (int i = 0; i < k; i++) {
                    Vec3 v = n.scale(0.5).add(unit(r).scale(0.25)).normalize().scale(0.35 + r.nextDouble() * 0.2);
                    add(lvl, spike, c, v);
                }
                ParticleOptions sh = o(SpellFxParticles.FROST, light, 2.0F, 0);
                k = FxQuality.count(3 + 2 * t);
                for (int i = 0; i < k; i++) add(lvl, sh, c, unit(r).scale(0.2 + 0.04 * t));
            }
            case EARTH -> {
                // Pedras pesadas + anel de poeira rente ao chão
                ParticleOptions st = o(SpellFxParticles.STONE, e == Element.SAND ? 0xE3C77A : 0x8A6A44, 2.4F, 0);
                int k = FxQuality.count(4 + 3 * t);
                for (int i = 0; i < k; i++) {
                    add(lvl, st, c, new Vec3((r.nextDouble() - 0.5) * 0.35, 0.3 + r.nextDouble() * (0.25 + 0.05 * t), (r.nextDouble() - 0.5) * 0.35));
                }
                ParticleOptions dust = o(SpellFxParticles.SMOKE, e == Element.SAND ? 0xE8D49A : 0xA08A70, 1.6F, 0);
                k = FxQuality.count(6 + 2 * t);
                for (int i = 0; i < k; i++) {
                    double a = Math.PI * 2 * i / k;
                    add(lvl, dust, c, new Vec3(Math.cos(a) * 0.12 * s, 0.01, Math.sin(a) * 0.12 * s));
                }
            }
            case WIND -> {
                // Espiral de riscos em volta da normal
                Vec3[] b = basis(n);
                ParticleOptions w = o(SpellFxParticles.WIND, rgb, 0.9F + 0.1F * t, 0);
                int k = FxQuality.count(6 + 3 * t);
                for (int i = 0; i < k; i++) {
                    double a = Math.PI * 2 * i / k;
                    Vec3 radial = b[0].scale(Math.cos(a)).add(b[1].scale(Math.sin(a)));
                    Vec3 tangent = b[0].scale(-Math.sin(a)).add(b[1].scale(Math.cos(a)));
                    add(lvl, w, c.add(radial.scale(0.5 * s)), tangent.scale(0.3).add(radial.scale(0.12)).add(n.scale(0.05)));
                }
            }
            case LIGHTNING -> {
                // Galhos descendo do alto até o ponto
                int bolts = 1 + t / 2;
                for (int i = 0; i < bolts; i++) {
                    Vec3 top = c.add((r.nextDouble() - 0.5) * 2.5, 3.0 + t + r.nextDouble() * 2, (r.nextDouble() - 0.5) * 2.5);
                    bolt(lvl, top, c, light, 0.9F + 0.1F * t);
                }
                ring(lvl, c, new Vec3(0, 1, 0), light, (0.7F + 0.4F * t) * s, 6);
            }
            case LIGHT -> {
                // Raios subindo + mini pilar
                ParticleOptions m = o(SpellFxParticles.MOTE, light, 1.3F, 0);
                int k = FxQuality.count(6 + 3 * t);
                for (int i = 0; i < k; i++) {
                    add(lvl, m, c.add((r.nextDouble() - 0.5) * 0.8 * s, 0, (r.nextDouble() - 0.5) * 0.8 * s),
                            new Vec3(0, 0.12 + r.nextDouble() * 0.15, 0));
                }
                if (t >= 2) pillar(lvl, c, rgb, 0.25F + 0.08F * t, 2.0F + t, 10);
            }
            case SHADOW, VOID -> {
                // Implosão: fiapos vêm de fora para dentro, depois estouram
                ParticleOptions wi = o(SpellFxParticles.WISP, rgb, 1.4F, 0);
                int k = FxQuality.count(6 + 3 * t);
                double rad = (1.0 + 0.3 * t) * s;
                for (int i = 0; i < k; i++) {
                    Vec3 off = unit(r).scale(rad);
                    add(lvl, wi, c.add(off), off.scale(-0.07));
                }
                final int kk = FxQuality.count(4 + 2 * t);
                final int dark = f == Fam.VOID ? 0x14061E : FxDraw.darken(rgb, 0.5F);
                after(4, () -> {
                    flash(lvl, c, dark, (0.5F + 0.25F * t) * s);
                    ParticleOptions burst = o(SpellFxParticles.WISP, dark, 1.6F, 0);
                    for (int i = 0; i < kk; i++) add(lvl, burst, c, unit(r).scale(0.15 + 0.03 * t));
                });
                if (f == Fam.VOID && t >= 2) ring(lvl, c, n, 0x2A0F40, (0.8F + 0.5F * t) * s, 12);
            }
            case ARCANE -> {
                // Runas em órbita + selo na face atingida
                ParticleOptions ru = o(SpellFxParticles.RUNE, light, 1.1F, 0);
                Vec3[] b = basis(n);
                int k = FxQuality.count(4 + 2 * t);
                for (int i = 0; i < k; i++) {
                    double a = Math.PI * 2 * i / k;
                    Vec3 radial = b[0].scale(Math.cos(a)).add(b[1].scale(Math.sin(a)));
                    add(lvl, ru, c.add(radial.scale(0.6 * s)), radial.scale(0.05).add(n.scale(0.04)));
                }
                if (t >= 1) {
                    lvl.addParticle(o(SpellFxParticles.CIRCLE, rgb, (0.5F + 0.2F * t) * s, 14,
                            SpellFxParticles.flags(t, SpellFxParticles.MODE_NORMAL, 3) | SpellFxParticles.FLAG_RELEASE), true,
                            c.x, c.y, c.z, n.x, n.y, n.z);
                }
            }
            case NATURE -> {
                ParticleOptions lf = o(SpellFxParticles.LEAF, rgb, 1.3F, 0);
                ParticleOptions petal = o(SpellFxParticles.LEAF, 0xFFB7D5, 1.0F, 0);
                int k = FxQuality.count(6 + 3 * t);
                for (int i = 0; i < k; i++) {
                    add(lvl, (i & 1) == 0 ? lf : petal, c, unit(r).add(0, 0.8, 0).normalize().scale(0.15 + 0.04 * t));
                }
            }
        }
    }

    // =====================================================================================
    //  ANTECIPAÇÃO (partículas convergindo)
    // =====================================================================================

    static void gather(ClientLevel lvl, Element e, int rgb, Vec3 pos, float radius, int t, int follow, int ticks) {
        RandomSource r = lvl.random;
        Fam f = fam(e);
        int dur = Mth.clamp(ticks <= 0 ? 10 : ticks, 1, 80);
        float rad = Mth.clamp(radius, 0.4F, 12F);
        ParticleOptions g = ElementFx.glow(rgb, 1.1F + 0.12F * t);
        ParticleOptions m = o(SpellFxParticles.MOTE, FxDraw.lighten(rgb, 0.4F), 0.9F, 0);
        ParticleOptions acc = switch (f) {
            case FIRE -> o(SpellFxParticles.EMBER, rgb, 1.0F, 0);
            case SHADOW, VOID -> o(SpellFxParticles.WISP, rgb, 1.0F, 0);
            case ARCANE -> o(SpellFxParticles.RUNE, rgb, 0.9F, 0);
            case WIND -> o(SpellFxParticles.WIND, rgb, 0.6F, 0);
            default -> m;
        };
        int every = FxQuality.atLeast(Graphics.MEDIUM) ? 1 : 2;
        schedule(0, (dur + every - 1) / every, every, i -> {
            Vec3 c = center(lvl, pos, follow);
            if (c == null) return;
            float prog = Math.min(1F, (i * every + 1F) / dur);
            int k = FxQuality.count(1 + t);
            for (int j = 0; j < k; j++) {
                Vec3 off = unit(r).scale(rad * (0.7 + r.nextDouble() * 0.5));
                add(lvl, j % 3 == 2 ? acc : (j & 1) == 0 ? g : m, c.add(off), off.scale(-0.1));
            }
            // Núcleo crescendo
            if (i % 3 == 0) {
                lvl.addParticle(o(SpellFxParticles.GLOW, FxDraw.lighten(rgb, 0.3F), 1.2F + prog * (1.5F + 0.4F * t), 6), c.x, c.y, c.z, 0, 0, 0);
            }
            if (f == Fam.LIGHTNING && r.nextInt(3) == 0) {
                add(lvl, o(SpellFxParticles.SPARK, FxDraw.lighten(rgb, 0.5F), 0.6F, 4), c, unit(r).scale(0.15));
            }
            if (i * every + every >= dur) flash(lvl, c, rgb, 0.5F + 0.2F * t);
        });
    }

    // =====================================================================================
    //  NOVA (explosão de ultimate)
    // =====================================================================================

    static void nova(ClientLevel lvl, Element e, int rgb, Vec3 c, float radius, int t) {
        RandomSource r = lvl.random;
        Fam f = fam(e);
        float rad = Mth.clamp(radius, 1F, 40F);
        boolean hi = FxQuality.atLeast(Graphics.HIGH);
        flash(lvl, c, FxDraw.lighten(rgb, 0.8F), Math.min(14F, rad * 0.5F));
        if (FxQuality.atLeast(Graphics.MEDIUM)) flash(lvl, c, rgb, Math.min(20F, rad * 0.9F));
        ring(lvl, c, new Vec3(0, 1, 0), rgb, rad, 18);
        after(3, () -> ring(lvl, c, new Vec3(0, 1, 0), FxDraw.lighten(rgb, 0.5F), rad * 0.6F, 14));
        if (hi) {
            after(6, () -> ring(lvl, c, new Vec3(0, 1, 0), FxDraw.darken(rgb, 0.7F), rad * 1.35F, 22));
            // Anel vertical virado para a câmera ("bolha" da explosão)
            Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            Vec3 toCam = cam.subtract(c).multiply(1, 0, 1);
            if (toCam.lengthSqr() > 1.0E-3) ring(lvl, c.add(0, rad * 0.25, 0), toCam.normalize(), FxDraw.lighten(rgb, 0.3F), rad * 0.55F, 14);
        }
        pillar(lvl, c, rgb, Math.max(0.6F, rad * 0.12F), Math.min(48F, rad * 2.5F), 26);
        ParticleOptions sig = ElementFx.style(e, rgb, 1.5F);
        double sp = Mth.clamp(rad / 10.0, 0.25, 1.2);
        int k = FxQuality.count(40);
        for (int i = 0; i < k; i++) add(lvl, sig, c.add(0, 0.5, 0), unit(r).scale(sp * (0.4 + r.nextDouble() * 0.6)));
        ParticleOptions gl = ElementFx.glow(rgb, 2.2F);
        k = FxQuality.count(20);
        for (int i = 0; i < k; i++) add(lvl, gl, c.add(0, 0.5, 0), unit(r).scale(sp * 0.5 * r.nextDouble()));
        // Detritos saindo rente ao chão
        ParticleOptions d = debris(f, e, rgb, 1.6F);
        k = FxQuality.count(24);
        for (int i = 0; i < k; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            add(lvl, d, c.add(0, 0.2, 0), new Vec3(Math.cos(a) * sp * 0.6, 0.25 + r.nextDouble() * 0.4, Math.sin(a) * sp * 0.6));
        }
        flourish(lvl, f, e, rgb, c, new Vec3(0, 1, 0), Math.min(4F, rad / 3F), 5);
        decal(lvl, c.add(0, 0.05, 0), rgb, rad * 0.9F, 60, 5);
        // Rescaldo: o chão continua "fervendo" por 2 s
        final ParticleOptions lo = linger(f, e, rgb, 1.4F);
        final ParticleOptions mo = o(SpellFxParticles.MOTE, FxDraw.lighten(rgb, 0.4F), 1.0F, 0);
        schedule(4, hi ? 20 : 8, 2, i -> {
            int m = FxQuality.count(3);
            for (int j = 0; j < m; j++) {
                double a = r.nextDouble() * Math.PI * 2, dd = Math.sqrt(r.nextDouble()) * rad * 0.8;
                Vec3 p = c.add(Math.cos(a) * dd, 0.1, Math.sin(a) * dd);
                add(lvl, (j & 1) == 0 ? lo : mo, p, new Vec3(0, 0.03 + r.nextDouble() * 0.05, 0));
            }
        });
    }

    // =====================================================================================
    //  ÁREA (zona no chão)
    // =====================================================================================

    static void area(ClientLevel lvl, Element e, int rgb, Vec3 c, float radius, int t, int ticks) {
        RandomSource r = lvl.random;
        Fam f = fam(e);
        float rad = Mth.clamp(radius, 0.5F, 32F);
        int dur = Mth.clamp(ticks <= 0 ? 40 : ticks, 4, 400);
        decal(lvl, c.add(0, 0.04, 0), rgb, rad, dur, Math.max(1, t));
        int every = FxQuality.atLeast(Graphics.HIGH) ? 2 : FxQuality.atLeast(Graphics.MEDIUM) ? 3 : 5;
        final ParticleOptions rim = ElementFx.glow(rgb, 1.3F);
        schedule(0, dur / every, every, i -> {
            int k = FxQuality.count(1 + (int) (rad * 0.6F));
            for (int j = 0; j < k; j++) {
                double a = r.nextDouble() * Math.PI * 2, dd = Math.sqrt(r.nextDouble()) * rad;
                Vec3 p = c.add(Math.cos(a) * dd, 0.1, Math.sin(a) * dd);
                areaParticle(lvl, f, e, rgb, p, a, r);
            }
            if ((i * every) % 6 < every) {
                int m = FxQuality.count(Math.min(24, 4 + (int) (rad * 2)));
                for (int j = 0; j < m; j++) {
                    double a = Math.PI * 2 * j / m + i * 0.15;
                    add(lvl, rim, c.add(Math.cos(a) * rad, 0.15, Math.sin(a) * rad), new Vec3(0, 0.02, 0));
                }
            }
            if (f == Fam.LIGHTNING && FxQuality.atLeast(Graphics.MEDIUM) && (i * every) % 10 < every) {
                double a = r.nextDouble() * Math.PI * 2, b = a + 1 + r.nextDouble() * 2;
                double d1 = r.nextDouble() * rad, d2 = r.nextDouble() * rad;
                bolt(lvl, c.add(Math.cos(a) * d1, 0.2, Math.sin(a) * d1), c.add(Math.cos(b) * d2, 0.2, Math.sin(b) * d2),
                        FxDraw.lighten(rgb, 0.5F), 0.7F);
            }
        });
    }

    private static void areaParticle(ClientLevel lvl, Fam f, Element e, int rgb, Vec3 p, double a, RandomSource r) {
        switch (f) {
            case FIRE -> {
                add(lvl, o(SpellFxParticles.EMBER, rgb, 1.1F, 0), p, new Vec3(0, 0.05 + r.nextDouble() * 0.08, 0));
                if (r.nextInt(4) == 0) add(lvl, linger(f, e, rgb, 1.0F), p.add(0, 0.6, 0), new Vec3(0, 0.03, 0));
            }
            case WATER -> {
                add(lvl, o(SpellFxParticles.DROPLET, rgb, 1.1F, 0), p.add(0, 2.2 + r.nextDouble(), 0), new Vec3(0, -0.2, 0));
                if (r.nextInt(3) == 0) add(lvl, linger(f, e, rgb, 1.0F), p, new Vec3(0, 0.01, 0));
            }
            case ICE -> add(lvl, o(SpellFxParticles.FROST, FxDraw.lighten(rgb, 0.4F), 1.0F, 0), p.add(0, 2.5 + r.nextDouble(), 0),
                    new Vec3((r.nextDouble() - 0.5) * 0.03, -0.05, (r.nextDouble() - 0.5) * 0.03));
            case EARTH -> {
                add(lvl, debris(f, e, rgb, 0.8F), p, new Vec3(0, 0.15 + r.nextDouble() * 0.1, 0));
                if (r.nextInt(3) == 0) add(lvl, linger(f, e, rgb, 0.9F), p, new Vec3(0, 0.01, 0));
            }
            case WIND -> add(lvl, o(SpellFxParticles.WIND, rgb, 0.8F, 0), p.add(0, 0.3 + r.nextDouble() * 1.6, 0),
                    new Vec3(-Math.sin(a) * 0.25, 0.02, Math.cos(a) * 0.25));
            case LIGHTNING -> add(lvl, o(SpellFxParticles.SPARK, FxDraw.lighten(rgb, 0.4F), 0.6F, 5), p,
                    new Vec3((r.nextDouble() - 0.5) * 0.1, 0.2, (r.nextDouble() - 0.5) * 0.1));
            case LIGHT -> add(lvl, o(SpellFxParticles.MOTE, FxDraw.lighten(rgb, 0.4F), 1.1F, 0), p, new Vec3(0, 0.06 + r.nextDouble() * 0.05, 0));
            case SHADOW, VOID -> add(lvl, o(SpellFxParticles.WISP, rgb, 1.2F, 0), p, new Vec3(0, 0.04, 0));
            case NATURE -> {
                add(lvl, o(SpellFxParticles.LEAF, rgb, 1.0F, 0), p.add(0, 1.5 + r.nextDouble(), 0), Vec3.ZERO);
                if (r.nextInt(2) == 0) add(lvl, ElementFx.glow(0xB8FF9A, 0.9F), p, new Vec3(0, 0.04, 0));
            }
            case ARCANE -> add(lvl, o(SpellFxParticles.RUNE, rgb, 1.0F, 0), p, new Vec3(0, 0.04, 0));
        }
    }

    // =====================================================================================
    //  AURA (casca em volta da entidade)
    // =====================================================================================

    static void aura(ClientLevel lvl, Element e, int rgb, int id, float size, int t, int ticks) {
        RandomSource r = lvl.random;
        Fam f = fam(e);
        int dur = Mth.clamp(ticks <= 0 ? 40 : ticks, 2, 600);
        int every = FxQuality.atLeast(Graphics.HIGH) ? 2 : 4;
        final ParticleOptions sig = ElementFx.style(e, rgb, 0.9F + 0.1F * t);
        final ParticleOptions gl = ElementFx.glow(rgb, 1.0F + 0.1F * t);
        schedule(0, dur / every, every, i -> {
            Entity en = lvl.getEntity(id);
            if (en == null || !en.isAlive()) return;
            double w = en.getBbWidth() * 0.6 * size + 0.2, h = en.getBbHeight();
            int k = FxQuality.count(2 + t);
            for (int j = 0; j < k; j++) {
                double a = r.nextDouble() * Math.PI * 2;
                Vec3 p = en.position().add(Math.cos(a) * w, r.nextDouble() * h, Math.sin(a) * w);
                add(lvl, (j & 1) == 0 ? sig : gl, p, new Vec3(-Math.sin(a) * 0.03, 0.04, Math.cos(a) * 0.03));
            }
            if (t >= 3 && FxQuality.atLeast(Graphics.HIGH) && (i * every) % 16 < every) {
                ring(lvl, en.position().add(0, 0.1, 0), new Vec3(0, 1, 0), rgb, (float) w * 1.8F, 12);
            }
            if (f == Fam.LIGHTNING && r.nextInt(4) == 0) {
                Vec3 a = en.position().add((r.nextDouble() - 0.5) * w * 2, r.nextDouble() * h, (r.nextDouble() - 0.5) * w * 2);
                Vec3 b = a.add((r.nextDouble() - 0.5) * 0.8, (r.nextDouble() - 0.5) * 0.8, (r.nextDouble() - 0.5) * 0.8);
                bolt(lvl, a, b, FxDraw.lighten(rgb, 0.5F), 0.5F);
            }
        });
    }

    // =====================================================================================
    //  FEIXE
    // =====================================================================================

    static void beam(ClientLevel lvl, Element e, int rgb, Vec3 from, Vec3 vec, float width, int t) {
        RandomSource r = lvl.random;
        Fam f = fam(e);
        double len = vec.length();
        if (len < 0.2) return;
        Vec3 d = vec.scale(1 / len);
        Vec3 to = from.add(vec);
        float w = Mth.clamp(width, 0.05F, 4F);
        int light = FxDraw.lighten(rgb, 0.5F);
        // Boca do feixe: selo virado para a frente
        lvl.addParticle(o(SpellFxParticles.CIRCLE, rgb, w * 3 + 0.4F, 12,
                SpellFxParticles.flags(t, SpellFxParticles.MODE_NORMAL, t >= 3 ? 0 : 3) | SpellFxParticles.FLAG_RELEASE), true,
                from.x, from.y, from.z, d.x, d.y, d.z);
        flash(lvl, from, rgb, 0.5F + w);
        if (f == Fam.LIGHTNING) {
            bolt(lvl, from, to, light, 1.0F + w);
            if (FxQuality.atLeast(Graphics.MEDIUM)) bolt(lvl, from, to, rgb, 0.7F + w);
        } else {
            double step = Math.max(0.4, len / Math.max(8, FxQuality.count(64)));
            ParticleOptions core = o(SpellFxParticles.GLOW, light, 2.0F + w * 6, 8);
            ParticleOptions sig = ElementFx.style(e, rgb, 0.9F + 0.1F * t);
            ParticleOptions sp = o(SpellFxParticles.SPARK, light, 0.8F + w, 5);
            int i = 0;
            for (double s = 0; s <= len; s += step, i++) {
                Vec3 p = from.add(d.scale(s));
                add(lvl, core, p, Vec3.ZERO);
                if (i % 2 == 0) add(lvl, sig, p.add(unit(r).scale(w)), unit(r).scale(0.03));
                if (i % 3 == 0) add(lvl, sp, p, d.scale(0.4));
            }
        }
        // Anéis viajando ao longo do feixe
        double ringStep = Math.max(2.0, len / (FxQuality.atLeast(Graphics.HIGH) ? 8 : 4));
        int n = 0;
        for (double s = ringStep * 0.5; s < len && n < 12; s += ringStep, n++) {
            final Vec3 p = from.add(d.scale(s));
            after(n / 2, () -> ring(lvl, p, d, rgb, w * 2.2F + 0.3F, 8));
        }
        impact(lvl, e, rgb, to, d.reverse(), 0.8F, t);
        // Rastro que fica
        if (FxQuality.atLeast(Graphics.MEDIUM)) {
            final ParticleOptions lo = linger(f, e, rgb, 0.8F);
            schedule(1, 4, 2, j -> {
                int m = FxQuality.count(2 + (int) (len / 3));
                for (int q = 0; q < m; q++) add(lvl, lo, from.add(d.scale(r.nextDouble() * len)), new Vec3(0, 0.02, 0));
            });
        }
    }

    // =====================================================================================
    //  PORTAL / INVOCAÇÃO
    // =====================================================================================

    static void portal(ClientLevel lvl, Element e, int rgb, Vec3 c, Vec3 normal, float radius, int t, int ticks) {
        RandomSource r = lvl.random;
        Fam f = fam(e);
        Vec3 n = normal.lengthSqr() < 1.0E-4 ? new Vec3(0, 1, 0) : normal.normalize();
        float rad = Mth.clamp(radius, 0.4F, 16F);
        int dur = Mth.clamp(ticks <= 0 ? 30 : ticks, 6, 400);
        int mode = Math.abs(n.y) > 0.92 ? SpellFxParticles.MODE_GROUND : SpellFxParticles.MODE_NORMAL;
        lvl.addParticle(o(SpellFxParticles.CIRCLE, rgb, rad, dur, SpellFxParticles.flags(Math.max(2, t), mode, 0)), true, c.x, c.y, c.z, n.x, n.y, n.z);
        lvl.addParticle(o(SpellFxParticles.CIRCLE, FxDraw.lighten(rgb, 0.3F), rad * 0.6F, dur,
                SpellFxParticles.flags(Math.max(2, t), mode, 1) | SpellFxParticles.FLAG_REVERSE), true, c.x, c.y, c.z, n.x, n.y, n.z);
        flash(lvl, c, rgb, rad * 0.7F);
        ring(lvl, c, n, rgb, rad * 1.6F, 12);
        Vec3[] b = basis(n);
        final ParticleOptions sw = f == Fam.LIGHT ? o(SpellFxParticles.MOTE, rgb, 1.1F, 0) : o(SpellFxParticles.WISP, rgb, 1.2F, 0);
        final ParticleOptions gl = ElementFx.glow(rgb, 1.2F);
        int every = FxQuality.atLeast(Graphics.HIGH) ? 2 : 4;
        schedule(0, dur / every, every, i -> {
            int k = FxQuality.count(3 + t);
            for (int j = 0; j < k; j++) {
                double a = r.nextDouble() * Math.PI * 2;
                Vec3 radial = b[0].scale(Math.cos(a)).add(b[1].scale(Math.sin(a)));
                Vec3 tangent = b[0].scale(-Math.sin(a)).add(b[1].scale(Math.cos(a)));
                add(lvl, (j & 1) == 0 ? sw : gl, c.add(radial.scale(rad)), tangent.scale(0.12).add(radial.scale(-0.06)));
            }
        });
        after(dur, () -> {
            flash(lvl, c, FxDraw.lighten(rgb, 0.5F), rad * 0.5F);
            ParticleOptions sig = ElementFx.style(e, rgb, 1.2F);
            int k = FxQuality.count(12);
            for (int j = 0; j < k; j++) add(lvl, sig, c, unit(r).scale(0.12));
        });
    }

    // =====================================================================================
    //  DOMÍNIO (cúpula se fechando)
    // =====================================================================================

    static void domain(ClientLevel lvl, Element e, int rgb, Vec3 c, float radius, int t, int ticks) {
        RandomSource r = lvl.random;
        float rad = Mth.clamp(radius, 2F, 48F);
        int dur = Mth.clamp(ticks <= 0 ? 30 : ticks, 8, 120);
        boolean hi = FxQuality.atLeast(Graphics.HIGH);
        decal(lvl, c.add(0, 0.05, 0), rgb, rad, dur + 60, 5);
        final ParticleOptions gl = ElementFx.glow(rgb, 1.6F);
        final ParticleOptions gw = ElementFx.glow(FxDraw.lighten(rgb, 0.6F), 1.9F);
        final ParticleOptions sig = ElementFx.style(e, rgb, 1.2F);
        final int meridians = hi ? 12 : 6;
        schedule(0, dur, 1, i -> {
            double p = (i + 1.0) / dur;
            double th = p * Math.PI / 2;
            double rr = rad * Math.cos(th), h = rad * Math.sin(th);
            // Anel da latitude atual (a cúpula sobe do chão até fechar no topo)
            int pts = FxQuality.count(Mth.clamp((int) (rr * 3), 6, 90));
            double spin = i * 0.05;
            for (int j = 0; j < pts; j++) {
                double a = Math.PI * 2 * j / pts + spin;
                add(lvl, j % 4 == 0 ? sig : gl, c.add(Math.cos(a) * rr, h, Math.sin(a) * rr), Vec3.ZERO);
            }
            // Meridianos subindo (as "costelas" da barreira)
            for (int m = 0; m < meridians; m++) {
                double a = Math.PI * 2 * m / meridians;
                add(lvl, gw, c.add(Math.cos(a) * rr, h, Math.sin(a) * rr), Vec3.ZERO);
            }
        });
        // Fechou: clarão de "troca de paisagem" + ondas
        after(dur, () -> {
            flash(lvl, c.add(0, rad * 0.4, 0), FxDraw.lighten(rgb, 0.85F), Math.min(24F, rad * 1.2F));
            flash(lvl, c.add(0, rad, 0), rgb, Math.min(12F, rad * 0.4F));
            ring(lvl, c.add(0, 0.1, 0), new Vec3(0, 1, 0), rgb, rad * 1.15F, 20);
            ring(lvl, c.add(0, rad * 0.5, 0), new Vec3(0, 1, 0), FxDraw.lighten(rgb, 0.4F), rad * 0.9F, 16);
            pillar(lvl, c, rgb, Math.max(0.8F, rad * 0.08F), rad * 1.4F, 30);
            int k = FxQuality.count(40);
            for (int j = 0; j < k; j++) add(lvl, sig, c.add(0, rad * 0.3, 0), unit(r).scale(0.3 + r.nextDouble() * 0.4));
        });
        if (hi) {
            final ParticleOptions lo = linger(fam(e), e, rgb, 1.2F);
            schedule(dur, 20, 2, i -> {
                int m = FxQuality.count(4);
                for (int j = 0; j < m; j++) {
                    double a = r.nextDouble() * Math.PI * 2, dd = Math.sqrt(r.nextDouble()) * rad * 0.9;
                    add(lvl, lo, c.add(Math.cos(a) * dd, 0.2, Math.sin(a) * dd), new Vec3(0, 0.05, 0));
                }
            });
        }
    }

    // =====================================================================================
    //  GOLPE DO CÉU
    // =====================================================================================

    static void skyStrike(ClientLevel lvl, Element e, int rgb, Vec3 g, float radius, int t, int delay) {
        RandomSource r = lvl.random;
        float rad = Mth.clamp(radius, 0.5F, 24F);
        int d = Mth.clamp(delay, 0, 100);
        Vec3 sky = g.add(0, 10 + 2 * t, 0);
        int flagsTop = SpellFxParticles.flags(Math.max(3, t), SpellFxParticles.MODE_GROUND, 0);
        lvl.addParticle(o(SpellFxParticles.CIRCLE, rgb, rad * 1.3F, d + 20, flagsTop), true, sky.x, sky.y, sky.z, 0, 0, 0);
        if (t >= 4 && FxQuality.atLeast(Graphics.MEDIUM)) {
            lvl.addParticle(o(SpellFxParticles.CIRCLE, FxDraw.lighten(rgb, 0.3F), rad * 1.8F, d + 20,
                    SpellFxParticles.flags(5, SpellFxParticles.MODE_GROUND, 2) | SpellFxParticles.FLAG_REVERSE), true, sky.x, sky.y + 0.02, sky.z, 0, 0, 0);
        }
        // Aviso no chão
        lvl.addParticle(o(SpellFxParticles.CIRCLE, rgb, rad, d + 10, SpellFxParticles.flags(1, SpellFxParticles.MODE_GROUND, 3)), true,
                g.x, g.y + 0.05, g.z, 0, 0, 0);
        if (d > 0) gather(lvl, e, rgb, sky, rad * 0.9F, t, -1, d);
        after(d, () -> {
            pillar(lvl, g, rgb, rad * 0.35F + 0.3F, (float) (sky.y - g.y) + 2, 18);
            impact(lvl, e, rgb, g.add(0, 0.1, 0), new Vec3(0, 1, 0), Math.max(1F, rad / 2F), t);
            if (t >= 4) nova(lvl, e, rgb, g, rad * 1.5F, t);
            ParticleOptions sp = o(SpellFxParticles.SPARK, FxDraw.lighten(rgb, 0.6F), 1.2F, 6);
            int k = FxQuality.count(8);
            for (int i = 0; i < k; i++) add(lvl, sp, sky.add(unit(r).scale(rad * 0.3)), new Vec3(0, -1.2, 0));
        });
    }

    // =====================================================================================
    //  CORTE
    // =====================================================================================

    static void slash(ClientLevel lvl, Element e, int rgb, Vec3 c, Vec3 dir, float length, int t) {
        Vec3 d = dir.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : dir.normalize();
        Vec3 side = d.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1.0E-4) side = new Vec3(1, 0, 0);
        final Vec3 sd = side.normalize();
        float rad = Mth.clamp(length, 0.5F, 16F) * 0.5F;
        int light = FxDraw.lighten(rgb, 0.6F);
        final ParticleOptions sp = o(SpellFxParticles.SPARK, light, 1.0F + 0.15F * t, 6);
        final ParticleOptions gl = ElementFx.glow(rgb, 1.2F + 0.15F * t);
        final ParticleOptions sig = ElementFx.style(e, rgb, 1.0F);
        final int pts = FxQuality.count(14 + 3 * t);
        // Meia-lua varrendo em 3 ticks (de um lado ao outro)
        schedule(0, 3, 1, step -> {
            for (int i = step * pts / 3; i < (step + 1) * pts / 3; i++) {
                double a = -1.1 + 2.2 * i / Math.max(1, pts - 1);
                Vec3 radial = sd.scale(Math.sin(a)).add(d.scale(Math.cos(a)));
                Vec3 tangent = sd.scale(Math.cos(a)).add(d.scale(-Math.sin(a)));
                Vec3 p = c.add(radial.scale(rad));
                add(lvl, sp, p, tangent.scale(0.35));
                add(lvl, gl, p, Vec3.ZERO);
                if ((i & 3) == 0) add(lvl, sig, p, radial.scale(0.08));
            }
        });
        flash(lvl, c.add(d.scale(rad * 0.6)), rgb, 0.4F + 0.15F * t);
    }

    // =====================================================================================
    //  REAÇÕES (cada uma com cara própria)
    // =====================================================================================

    /**
     * Explosão de uma reação elemental. Release 1.0 (perf): mais leve e ainda forte — o impacto vem de peças GRANDES e
     * baratas (clarões, ondas, selo no chão, raio, coluna: um quadrado cada) e as partículas soltas caíram para menos da
     * metade (antes o servidor ainda mandava ~100 partículas do vanilla por cima, fora do preset). Tudo é contado pelo
     * Preset Gráfico; {@code lite} (muitas reações no mesmo tick) fica só com clarão, onda e um punhado de partículas.
     *
     * @param toGround vetor do centro até os pés do alvo (0 = desconhecido: estima pelo tamanho)
     */
    static void reaction(ClientLevel lvl, Vec3 c, Vec3 toGround, int ord, float size, boolean lite) {
        Reaction re = Reaction.byOrdinal(ord);
        if (re == null) return;
        RandomSource r = lvl.random;
        float s = Mth.clamp(size, 0.4F, 4F);
        int col = re.color();
        Vec3 up = new Vec3(0, 1, 0);
        boolean mid = !lite && FxQuality.atLeast(Graphics.MEDIUM);
        boolean hi = !lite && FxQuality.atLeast(Graphics.HIGH);
        // Chão sob o alvo (ondas rasteiras e selos nascem aqui, um tico acima para não sumir dentro do bloco)
        Vec3 g = (toGround.lengthSqr() > 1.0E-4 ? c.add(toGround) : c.add(0, -0.4 * s, 0)).add(0, 0.06, 0);
        float k = lite ? 0.4F : 1F;
        switch (re) {
            case ELECTROCUTE -> {
                flash(lvl, c, col, 1.7F * s);
                if (mid) flash(lvl, c, 0xFFFFFF, 0.8F * s);
                // Raio principal descendo do alto + galhos curtos nos presets altos
                bolt(lvl, c.add((r.nextDouble() - 0.5) * 2.0, 5.0 + 2.0 * s, (r.nextDouble() - 0.5) * 2.0), c, 0xDFF6FF, 1.2F);
                if (hi) for (int i = 0; i < 2; i++) bolt(lvl, c.add(unit(r).scale(1.6 * s)), c, 0xBFEFFF, 0.8F);
                ring(lvl, g, up, col, 2.2F * s, 9);
                if (mid) ring(lvl, c, unit(r), 0xDFF6FF, 1.5F * s, 7);
                ParticleOptions sp = o(SpellFxParticles.SPARK, 0xBFEFFF, 1.0F, 0);
                int n = part(14, k);
                for (int i = 0; i < n; i++) add(lvl, sp, c, unit(r).scale(0.35 + r.nextDouble() * 0.2));
                ParticleOptions dr = o(SpellFxParticles.DROPLET, 0x3FA9FF, 1.2F, 0);
                n = part(6, k);
                for (int i = 0; i < n; i++) add(lvl, dr, c, unit(r).add(0, 1, 0).scale(0.15));
            }
            case FREEZE -> {
                flash(lvl, c, 0xFFFFFF, 1.3F * s);
                ring(lvl, g, up, col, 2.2F * s, 14);
                // Selo de geada no chão: o alvo "trava" no lugar
                if (!lite) decal(lvl, g, col, 1.5F * s, 30, 1);
                ParticleOptions fr = o(SpellFxParticles.FROST, 0xE8FAFF, 2.2F, 30);
                int n = part(10, k);
                for (int i = 0; i < n; i++) add(lvl, fr, c.add(unit(r).scale(0.6 * s)), Vec3.ZERO);
                ParticleOptions spike = o(SpellFxParticles.SPARK, 0xF0FCFF, 1.3F, 8);
                n = part(8, k);
                for (int i = 0; i < n; i++) add(lvl, spike, c, up.scale(0.6).add(unit(r).scale(0.4)).normalize().scale(0.3));
                shards(lvl, Blocks.ICE, c, s, part(8, k), 0.12);
            }
            case VAPOR -> {
                flash(lvl, c, 0xF0F6FA, 1.0F * s);
                ring(lvl, c, up, 0xFFFFFF, 2.6F * s, 16);
                if (mid) after(3, () -> ring(lvl, g, up, 0xD8E6EE, 3.4F * s, 20));
                ParticleOptions sm = o(SpellFxParticles.SMOKE, 0xF0F6FA, 2.6F, 40);
                int n = part(14, k);
                for (int i = 0; i < n; i++) add(lvl, sm, c.add(unit(r).scale(0.5)), unit(r).multiply(1, 0.3, 1).scale(0.08).add(0, 0.03, 0));
            }
            case WILDFIRE -> {
                flash(lvl, c, col, 1.3F * s);
                ring(lvl, g, up, col, 3.2F * s, 16);
                if (mid) after(3, () -> ring(lvl, g, up, 0xFFD080, 4.2F * s, 18));
                ParticleOptions em = o(SpellFxParticles.EMBER, col, 1.4F, 0);
                int n = part(18, k);
                for (int i = 0; i < n; i++) {
                    double a = r.nextDouble() * Math.PI * 2;
                    add(lvl, em, c, new Vec3(Math.cos(a) * 0.3, 0.1 + r.nextDouble() * 0.15, Math.sin(a) * 0.3));
                }
                // Riscos de vento girando: é o vento que espalha o fogo
                ParticleOptions w = o(SpellFxParticles.WIND, 0xFFD0A0, 0.8F, 0);
                n = part(6, k);
                for (int i = 0; i < n; i++) {
                    double a = Math.PI * 2 * i / n;
                    add(lvl, w, c.add(Math.cos(a) * 0.8 * s, 0, Math.sin(a) * 0.8 * s), new Vec3(-Math.sin(a) * 0.3, 0.03, Math.cos(a) * 0.3));
                }
            }
            case MELT -> {
                flash(lvl, c, 0xFFE0B0, 1.4F * s);
                ring(lvl, c, up, col, 1.9F * s, 10);
                ParticleOptions dr = o(SpellFxParticles.DROPLET, 0xFFB060, 1.4F, 0);
                int n = part(12, k);
                for (int i = 0; i < n; i++) add(lvl, dr, c.add(unit(r).scale(0.4)), new Vec3(0, -0.05, 0));
                ParticleOptions sm = o(SpellFxParticles.SMOKE, 0xFFFFFF, 1.8F, 0);
                n = part(8, k);
                for (int i = 0; i < n; i++) add(lvl, sm, c, new Vec3((r.nextDouble() - 0.5) * 0.05, 0.08, (r.nextDouble() - 0.5) * 0.05));
            }
            case OVERLOAD -> {
                flash(lvl, c, col, 2.8F * s);
                flash(lvl, c, 0xFFFFFF, 1.3F * s);
                ring(lvl, c, up, col, 3.0F * s, 10);
                if (!lite) after(2, () -> ring(lvl, g, up, 0xFFB0D0, 4.2F * s, 14));
                // Coluna curta da explosão (uma peça só, lê como "estouro" de longe)
                if (mid) pillar(lvl, g, col, 0.45F * s, 2.5F + s, 8);
                ParticleOptions em = o(SpellFxParticles.EMBER, 0xFF8040, 1.5F, 0);
                ParticleOptions sp = o(SpellFxParticles.SPARK, 0xFFD0F0, 1.1F, 0);
                int n = part(14, k);
                for (int i = 0; i < n; i++) {
                    add(lvl, em, c, unit(r).scale(0.3));
                    add(lvl, sp, c, unit(r).scale(0.5));
                }
                ParticleOptions sm = o(SpellFxParticles.SMOKE, 0x40303A, 2.0F, 0);
                n = part(6, k);
                for (int i = 0; i < n; i++) add(lvl, sm, c, unit(r).scale(0.06).add(0, 0.04, 0));
            }
            case SUPERCONDUCT -> {
                flash(lvl, c, col, 1.1F * s);
                ring(lvl, g, up, col, 2.6F * s, 16);
                if (!lite) decal(lvl, g, col, 1.6F * s, 24, 1);
                ParticleOptions sp = o(SpellFxParticles.SPARK, 0xD8D0FF, 0.9F, 0);
                ParticleOptions fr = o(SpellFxParticles.FROST, 0xC8C0FF, 1.2F, 0);
                int n = part(12, k);
                for (int i = 0; i < n; i++) {
                    double a = Math.PI * 2 * i / n;
                    Vec3 p = c.add(Math.cos(a) * 1.0, 0.8, Math.sin(a) * 1.0);
                    add(lvl, sp, p, new Vec3(-Math.cos(a) * 0.1, -0.3, -Math.sin(a) * 0.1));
                    add(lvl, fr, p, new Vec3(-Math.cos(a) * 0.05, -0.05, -Math.sin(a) * 0.05));
                }
            }
            case SHATTER -> {
                flash(lvl, c, 0xFFFFFF, 1.7F * s);
                ring(lvl, c, unit(r), col, 2.0F * s, 8);
                if (mid) ring(lvl, g, up, 0xB8D8E8, 2.8F * s, 12);
                ParticleOptions fr = o(SpellFxParticles.FROST, 0xE8FAFF, 2.4F, 0);
                ParticleOptions st = o(SpellFxParticles.STONE, 0xB8D8E8, 2.0F, 0);
                int n = part(16, k);
                for (int i = 0; i < n; i++) {
                    Vec3 v = unit(r).scale(0.35 + r.nextDouble() * 0.2);
                    add(lvl, (i & 1) == 0 ? fr : st, c, v.add(0, 0.15, 0));
                }
                shards(lvl, Blocks.PACKED_ICE, c, s, part(10, k), 0.25);
            }
            case LACERATE -> {
                flash(lvl, c, 0xE0303A, 0.8F * s);
                ParticleOptions sp = o(SpellFxParticles.SPARK, 0xFF4050, 1.3F, 6);
                // Dois cortes em X
                for (int x = 0; x < 2; x++) {
                    Vec3 dir = new Vec3(x == 0 ? 1 : -1, 1, r.nextDouble() - 0.5).normalize();
                    int n = part(8, k);
                    for (int i = 0; i < n; i++) {
                        double f = (i / (double) Math.max(1, n - 1) - 0.5) * 1.6 * s;
                        add(lvl, sp, c.add(dir.scale(f)), dir.scale(0.2));
                    }
                }
                ParticleOptions bl = o(SpellFxParticles.DROPLET, 0xB0101C, 1.3F, 0);
                int n = part(10, k);
                for (int i = 0; i < n; i++) add(lvl, bl, c, unit(r).scale(0.15).add(0, 0.1, 0));
            }
            case RUPTURE -> {
                // Luz e sombra se anulando: implosão branca, estouro roxo
                ParticleOptions m = o(SpellFxParticles.MOTE, 0xFFF4D0, 1.4F, 0);
                ParticleOptions w = o(SpellFxParticles.WISP, 0x6A3AA0, 1.4F, 0);
                int n = part(12, k);
                for (int i = 0; i < n; i++) {
                    Vec3 off = unit(r).scale(1.4 * s);
                    add(lvl, (i & 1) == 0 ? m : w, c.add(off), off.scale(-0.08));
                }
                flash(lvl, c, 0xFFFFFF, 1.0F * s);
                after(4, () -> {
                    flash(lvl, c, col, 2.2F * s);
                    ring(lvl, c, up, 0xFFF4D0, 2.4F * s, 10);
                    ring(lvl, c, up, 0x6A3AA0, 3.2F * s, 14);
                });
            }
            case SAND_EYES -> {
                ring(lvl, c, up, col, 1.9F * s, 10);
                ParticleOptions sd = o(SpellFxParticles.STONE, 0xE3C77A, 1.0F, 0);
                ParticleOptions sm = o(SpellFxParticles.SMOKE, 0xE8D49A, 2.0F, 0);
                int n = part(14, k);
                for (int i = 0; i < n; i++) {
                    double a = Math.PI * 2 * i / n;
                    Vec3 p = c.add(Math.cos(a) * 1.2, (r.nextDouble() - 0.5), Math.sin(a) * 1.2);
                    add(lvl, (i & 1) == 0 ? sd : sm, p, new Vec3(-Math.sin(a) * 0.2, 0.05, Math.cos(a) * 0.2));
                }
                shards(lvl, Blocks.SAND, c, s, part(6, k), 0.15);
            }
        }
    }

    /** Quantidade de partículas soltas de uma reação: preset gráfico × versão enxuta. */
    private static int part(int base, float k) {
        return FxQuality.count(Math.max(1, Math.round(base * k)));
    }

    /** Lascas de bloco (gelo, pedra, areia) voando do alvo — partícula de bloco do vanilla, já contada pelo preset. */
    private static void shards(ClientLevel lvl, Block block, Vec3 c, float s, int n, double speed) {
        RandomSource r = lvl.random;
        ParticleOptions p = new BlockParticleOption(ParticleTypes.BLOCK, block.defaultBlockState());
        for (int i = 0; i < n; i++) {
            Vec3 at = c.add(unit(r).scale(0.35 * s));
            Vec3 v = unit(r).scale(speed).add(0, speed * 0.6, 0);
            lvl.addParticle(p, at.x, at.y, at.z, v.x, v.y, v.z);
        }
    }

    // =====================================================================================
    //  Peças básicas
    // =====================================================================================

    private static ParticleOptions o(DeferredHolder<ParticleType<?>, ParticleType<FxOptions>> type, int rgb, float scale, int life) {
        return ElementFx.fx(type, rgb, scale, life, -1, 0);
    }

    private static ParticleOptions o(DeferredHolder<ParticleType<?>, ParticleType<FxOptions>> type, int rgb, float scale, int life, int flags) {
        return ElementFx.fx(type, rgb, scale, life, -1, flags);
    }

    private static void add(ClientLevel lvl, ParticleOptions p, Vec3 at, Vec3 v) {
        lvl.addParticle(p, at.x, at.y, at.z, v.x, v.y, v.z);
    }

    static void flash(ClientLevel lvl, Vec3 c, int rgb, float radius) {
        lvl.addParticle(o(SpellFxParticles.FLASH, rgb, Mth.clamp(radius, 0.1F, 24F), 0), true, c.x, c.y, c.z, 0, 0, 0);
    }

    /** Onda de choque no plano de {@code n}. */
    static void ring(ClientLevel lvl, Vec3 c, Vec3 n, int rgb, float radius, int life) {
        boolean ground = Math.abs(n.y) > 0.92;
        int flags = SpellFxParticles.flags(0, ground ? SpellFxParticles.MODE_GROUND : SpellFxParticles.MODE_NORMAL, 0);
        ParticleOptions p = o(SpellFxParticles.SHOCKWAVE, rgb, Mth.clamp(radius, 0.2F, 48F), life, flags);
        if (ground) lvl.addParticle(p, true, c.x, c.y, c.z, 0, 0, 0);
        else lvl.addParticle(p, true, c.x, c.y, c.z, n.x, n.y, n.z);
    }

    static void decal(ClientLevel lvl, Vec3 c, int rgb, float radius, int life, int tier) {
        int layer = tier <= 1 ? 3 : 0;
        lvl.addParticle(o(SpellFxParticles.CIRCLE, rgb, radius, life, SpellFxParticles.flags(tier, SpellFxParticles.MODE_GROUND, layer)), true,
                c.x, c.y, c.z, 0, 0, 0);
        if (tier >= 3 && FxQuality.atLeast(Graphics.MEDIUM)) {
            lvl.addParticle(o(SpellFxParticles.CIRCLE, FxDraw.lighten(rgb, 0.3F), radius * 0.55F, life,
                    SpellFxParticles.flags(tier, SpellFxParticles.MODE_GROUND, 1) | SpellFxParticles.FLAG_REVERSE), true, c.x, c.y + 0.01, c.z, 0, 0, 0);
        }
        if (tier >= 5 && FxQuality.atLeast(Graphics.HIGH)) {
            lvl.addParticle(o(SpellFxParticles.CIRCLE, rgb, radius * 1.3F, life, SpellFxParticles.flags(tier, SpellFxParticles.MODE_GROUND, 2)), true,
                    c.x, c.y - 0.01, c.z, 0, 0, 0);
        }
    }

    static void pillar(ClientLevel lvl, Vec3 base, int rgb, float radius, float height, int life) {
        int h16 = Mth.clamp((int) (height * 16), 16, 16 * 64);
        lvl.addParticle(ElementFx.fx(SpellFxParticles.PILLAR, rgb, radius, life, h16, 0), true, base.x, base.y, base.z, 0, 0, 0);
    }

    /** Raio em zigue-zague de {@code a} até {@code b} (faíscas curtas alinhadas com cada segmento). */
    static void bolt(ClientLevel lvl, Vec3 a, Vec3 b, int rgb, float width) {
        RandomSource r = lvl.random;
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 0.1) return;
        Vec3[] bs = basis(d.scale(1 / len));
        int segs = Mth.clamp((int) (len / 0.6), 2, FxQuality.atLeast(Graphics.HIGH) ? 40 : 16);
        ParticleOptions sp = o(SpellFxParticles.SPARK, rgb, width, 3);
        ParticleOptions gl = ElementFx.glow(rgb, 0.8F + width * 0.5F);
        Vec3 prev = a;
        for (int i = 1; i <= segs; i++) {
            double f = i / (double) segs;
            double jit = i == segs ? 0 : 0.35 * Math.sin(f * Math.PI) * Math.min(1.0, len / 3);
            Vec3 p = a.add(d.scale(f)).add(bs[0].scale((r.nextDouble() - 0.5) * 2 * jit)).add(bs[1].scale((r.nextDouble() - 0.5) * 2 * jit));
            Vec3 seg = p.subtract(prev);
            Vec3 mid = prev.add(seg.scale(0.5));
            add(lvl, sp, mid, seg.scale(0.3));
            if ((i & 1) == 0) add(lvl, gl, p, Vec3.ZERO);
            prev = p;
        }
        flash(lvl, b, rgb, 0.4F + width * 0.3F);
    }

    /** Centro de um efeito que segue uma entidade (frente da mão) ou o ponto fixo. */
    private static Vec3 center(ClientLevel lvl, Vec3 fixed, int follow) {
        if (follow < 0) return fixed;
        Entity en = lvl.getEntity(follow);
        if (en == null || !en.isAlive()) return null;
        Vec3 look = en.getLookAngle();
        Minecraft mc = Minecraft.getInstance();
        // Em primeira pessoa o ponto fica mais à frente e mais baixo (na altura da mão) para não tapar a mira
        if (en == mc.player && mc.options.getCameraType().isFirstPerson()) {
            return en.getEyePosition().add(look.scale(1.4)).add(0, -0.55, 0);
        }
        return en.getEyePosition().add(look.scale(0.8)).add(0, -0.3, 0);
    }

    private static Vec3 unit(RandomSource r) {
        Vec3 v = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian());
        double l = v.length();
        return l < 1.0E-4 ? new Vec3(0, 1, 0) : v.scale(1 / l);
    }

    /** Dois vetores perpendiculares a {@code n}. */
    private static Vec3[] basis(Vec3 n) {
        Vec3 a = Math.abs(n.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 u = n.cross(a).normalize();
        Vec3 v = n.cross(u).normalize();
        return new Vec3[] {u, v};
    }

    private FxBurstClient() {}
}
