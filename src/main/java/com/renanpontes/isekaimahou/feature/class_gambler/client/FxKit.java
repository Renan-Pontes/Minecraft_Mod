package com.renanpontes.isekaimahou.feature.class_gambler.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.client.boss.BossCameraShake;
import com.renanpontes.isekaimahou.client.fx.FxQuality;
import com.renanpontes.isekaimahou.core.MahouConfig.Graphics;
import com.renanpontes.isekaimahou.core.boss.BossPayloads;
import com.renanpontes.isekaimahou.glyph.fx.FxOptions;
import com.renanpontes.isekaimahou.glyph.fx.SpellFxParticles;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * SÓ CLIENTE. Kit de coreografia de efeitos das classes (cópia por módulo — os módulos não dependem uns dos outros):
 * partículas da paleta {@code fx_*} (brilho, faísca, risco, anel, círculo, pilar, clarão), formas prontas (anel,
 * linha, espiral, esfera, detritos do chão), tremor de câmera, sons em camadas e uma agenda curta de passos
 * ("antecipação → impacto → rescaldo"). Tudo passa por {@link FxQuality}: quantidade, extras caros e distância.
 */
public final class FxKit {
    public static final RandomSource RND = RandomSource.create();
    private static final int MAX_JOBS = 256;

    private record Job(long due, Runnable run) {}

    private static final List<Job> JOBS = new ArrayList<>();
    private static long ticks;
    @Nullable
    private static ClientLevel lastLevel;

    // =====================================================================================
    //  Ambiente
    // =====================================================================================
    @Nullable
    public static ClientLevel level() {
        ClientLevel l = Minecraft.getInstance().level;
        if (l != lastLevel) {
            JOBS.clear();
            lastLevel = l;
        }
        return l;
    }

    /** O ponto está dentro da distância de efeitos do preset (contando a câmera)? */
    public static boolean near(Vec3 pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.gameRenderer == null) return false;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        double r = FxQuality.range();
        return cam.distanceToSqr(pos) <= r * r;
    }

    /** Quantidade de partículas ajustada ao preset. */
    public static int n(int base) {
        return FxQuality.count(base);
    }

    /** Extras caros (camadas, rastros, brilho duplo) só a partir do Alto. */
    public static boolean hi() {
        return FxQuality.atLeast(Graphics.HIGH);
    }

    public static boolean ultra() {
        return FxQuality.atLeast(Graphics.ULTRA);
    }

    /** Pelo menos o Médio. */
    public static boolean mid() {
        return FxQuality.atLeast(Graphics.MEDIUM);
    }

    @Nullable
    public static Entity entity(int id) {
        ClientLevel l = level();
        return l == null || id < 0 ? null : l.getEntity(id);
    }

    // =====================================================================================
    //  Agenda (passos com atraso)
    // =====================================================================================
    /** Roda {@code r} daqui a {@code delay} ticks (0 = agora). */
    public static void later(int delay, Runnable r) {
        if (delay <= 0) {
            r.run();
            return;
        }
        if (JOBS.size() >= MAX_JOBS) JOBS.remove(0);
        JOBS.add(new Job(ticks + delay, r));
    }

    /** Roda {@code step(i)} {@code count} vezes, a cada {@code period} ticks, começando em {@code delay}. */
    public static void repeat(int delay, int count, int period, IntConsumer step) {
        for (int i = 0; i < count; i++) {
            final int k = i;
            later(delay + i * Math.max(1, period), () -> step.accept(k));
        }
    }

    /** Chamado a cada tick de cliente pelo módulo. */
    public static void tick() {
        if (level() == null || Minecraft.getInstance().isPaused()) return;
        ticks++;
        if (JOBS.isEmpty()) return;
        List<Job> due = null;
        for (int i = JOBS.size() - 1; i >= 0; i--) {
            Job j = JOBS.get(i);
            if (j.due <= ticks) {
                if (due == null) due = new ArrayList<>();
                due.add(j);
                JOBS.remove(i);
            }
        }
        if (due == null) return;
        for (int i = due.size() - 1; i >= 0; i--) {
            try {
                due.get(i).run.run();
            } catch (RuntimeException ignored) {
                // um efeito quebrado nunca pode derrubar o cliente
            }
        }
    }

    public static void clear() {
        JOBS.clear();
    }

    // =====================================================================================
    //  Partículas
    // =====================================================================================
    public static ParticleOptions fx(DeferredHolder<ParticleType<?>, ParticleType<FxOptions>> type, int rgb, float scale, int life) {
        return fx(type, rgb, scale, life, -1, 0);
    }

    public static ParticleOptions fx(DeferredHolder<ParticleType<?>, ParticleType<FxOptions>> type, int rgb, float scale, int life,
                                     int data, int flags) {
        if (!type.isBound()) return dust(rgb, Mth.clamp(scale, 0.3F, 3F));
        return new FxOptions(type.get(), rgb & 0xFFFFFF, scale, life, data, flags);
    }

    public static ParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(new org.joml.Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F),
                Mth.clamp(size, 0.05F, 4F));
    }

    public static ParticleOptions glow(int rgb, float scale) {
        return fx(SpellFxParticles.GLOW, rgb, scale, 0);
    }

    public static ParticleOptions spark(int rgb, float scale) {
        return fx(SpellFxParticles.SPARK, rgb, scale, 0);
    }

    public static ParticleOptions streak(int rgb, float scale) {
        return fx(SpellFxParticles.WIND, rgb, scale, 0);
    }

    public static ParticleOptions mote(int rgb, float scale) {
        return fx(SpellFxParticles.MOTE, rgb, scale, 0);
    }

    public static ParticleOptions ember(int rgb, float scale) {
        return fx(SpellFxParticles.EMBER, rgb, scale, 0);
    }

    public static ParticleOptions smoke(int rgb, float scale) {
        return fx(SpellFxParticles.SMOKE, rgb, scale, 0);
    }

    public static ParticleOptions wisp(int rgb, float scale) {
        return fx(SpellFxParticles.WISP, rgb, scale, 0);
    }

    public static ParticleOptions rune(int rgb, float scale) {
        return fx(SpellFxParticles.RUNE, rgb, scale, 0);
    }

    public static void add(ParticleOptions o, Vec3 p, Vec3 v) {
        add(o, p.x, p.y, p.z, v.x, v.y, v.z);
    }

    public static void add(ParticleOptions o, double x, double y, double z, double vx, double vy, double vz) {
        ClientLevel l = level();
        if (l == null) return;
        l.addParticle(o, true, x, y, z, vx, vy, vz);
    }

    /** Clarão (raio em blocos). */
    public static void flash(Vec3 p, int rgb, float radius) {
        add(fx(SpellFxParticles.FLASH, rgb, radius, 0), p, Vec3.ZERO);
    }

    /** Onda de choque crescendo até {@code radius} em {@code life} ticks; {@code normal} null = no chão. */
    public static void shock(Vec3 p, int rgb, float radius, int life, @Nullable Vec3 normal) {
        boolean ground = normal == null || Math.abs(normal.normalize().y) > 0.92;
        int flags = SpellFxParticles.flags(0, ground ? SpellFxParticles.MODE_GROUND : SpellFxParticles.MODE_NORMAL, 0);
        Vec3 v = ground ? Vec3.ZERO : normal.normalize();
        add(fx(SpellFxParticles.SHOCKWAVE, rgb, radius, life, -1, flags), p, v);
    }

    /** Círculo rúnico no chão ({@code tier} 0..5; reverse = gira ao contrário). */
    public static void circle(Vec3 p, int rgb, float radius, int life, int tier, int layer, boolean reverse) {
        int flags = SpellFxParticles.flags(tier, SpellFxParticles.MODE_GROUND, layer) | (reverse ? SpellFxParticles.FLAG_REVERSE : 0);
        add(fx(SpellFxParticles.CIRCLE, rgb, radius, life, -1, flags), p, Vec3.ZERO);
    }

    /** Círculo orientado pela normal (de frente para o alvo, numa parede, no cano da arma...). */
    public static void circleFacing(Vec3 p, Vec3 normal, int rgb, float radius, int life, int tier, int layer, boolean reverse) {
        int flags = SpellFxParticles.flags(tier, SpellFxParticles.MODE_NORMAL, layer) | (reverse ? SpellFxParticles.FLAG_REVERSE : 0);
        add(fx(SpellFxParticles.CIRCLE, rgb, radius, life, -1, flags), p, normal.normalize());
    }

    /** Círculo preso a uma entidade (aos pés, à frente ou no céu) — segue quem conjura. */
    public static void circleOn(Entity e, int rgb, float radius, int life, int tier, int mode, int layer, boolean release) {
        int flags = SpellFxParticles.flags(tier, mode, layer) | (release ? SpellFxParticles.FLAG_RELEASE : 0);
        add(fx(SpellFxParticles.CIRCLE, rgb, radius, life, e.getId(), flags), e.position(), Vec3.ZERO);
    }

    /** Pilar de luz subindo de {@code base}. */
    public static void pillar(Vec3 base, int rgb, float radius, float height, int life) {
        int h16 = Mth.clamp((int) (height * 16), 16, 16 * 64);
        add(fx(SpellFxParticles.PILLAR, rgb, radius, life, h16, 0), base, Vec3.ZERO);
    }

    // =====================================================================================
    //  Formas
    // =====================================================================================
    /** Base ortonormal {direita, cima} perpendicular a {@code dir}. */
    public static Vec3[] basis(Vec3 dir) {
        Vec3 f = dir.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : dir.normalize();
        Vec3 right = f.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1.0E-4) right = new Vec3(1, 0, 0);
        right = right.normalize();
        Vec3 up = right.cross(f).normalize();
        return new Vec3[] {right, up};
    }

    /** Anel de {@code count} partículas no plano perpendicular a {@code normal}, saindo com {@code out} de velocidade. */
    public static void ring(Vec3 c, @Nullable Vec3 normal, double r, int count, ParticleOptions o, double out) {
        Vec3[] b = normal == null ? new Vec3[] {new Vec3(1, 0, 0), new Vec3(0, 0, 1)} : basis(normal);
        double off = RND.nextDouble() * Math.PI * 2;
        for (int i = 0; i < count; i++) {
            double a = off + Math.PI * 2 * i / count;
            Vec3 d = b[0].scale(Math.cos(a)).add(b[1].scale(Math.sin(a)));
            add(o, c.add(d.scale(r)), d.scale(out));
        }
    }

    /** Linha de partículas de {@code a} até {@code b} (no máx. 96 pontos). */
    public static void line(Vec3 a, Vec3 b, double step, ParticleOptions o, double jitter) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 1.0E-3) return;
        int count = Math.min(96, Math.max(2, (int) (len / step)));
        for (int i = 0; i <= count; i++) {
            Vec3 q = a.add(d.scale(i / (double) count));
            add(o, q.x + (RND.nextDouble() - 0.5) * jitter, q.y + (RND.nextDouble() - 0.5) * jitter, q.z + (RND.nextDouble() - 0.5) * jitter, 0, 0, 0);
        }
    }

    /** Explosão esférica: {@code count} partículas saindo do centro com velocidade {@code speed} (± 40%). */
    public static void sphere(Vec3 c, int count, double speed, ParticleOptions o) {
        for (int i = 0; i < count; i++) {
            Vec3 d = new Vec3(RND.nextGaussian(), RND.nextGaussian(), RND.nextGaussian());
            if (d.lengthSqr() < 1.0E-4) continue;
            d = d.normalize().scale(speed * (0.6 + RND.nextDouble() * 0.8));
            add(o, c, d);
        }
    }

    /** Cone de partículas na direção {@code dir} (abertura em radianos). */
    public static void cone(Vec3 c, Vec3 dir, double spread, int count, double speed, ParticleOptions o) {
        Vec3 f = dir.normalize();
        Vec3[] b = basis(f);
        for (int i = 0; i < count; i++) {
            double a = RND.nextDouble() * Math.PI * 2;
            double s = Math.tan(spread) * Math.sqrt(RND.nextDouble());
            Vec3 d = f.add(b[0].scale(Math.cos(a) * s)).add(b[1].scale(Math.sin(a) * s)).normalize();
            add(o, c, d.scale(speed * (0.6 + RND.nextDouble() * 0.8)));
        }
    }

    /** Hélice em volta do eixo {@code a → b} (rastro de giro). */
    public static void helix(Vec3 a, Vec3 b, double r, double turns, int count, float phase, ParticleOptions o) {
        Vec3 d = b.subtract(a);
        if (d.lengthSqr() < 1.0E-6) return;
        Vec3[] bs = basis(d);
        for (int i = 0; i <= count; i++) {
            double t = i / (double) count;
            double ang = phase + t * turns * Math.PI * 2;
            Vec3 q = a.add(d.scale(t)).add(bs[0].scale(Math.cos(ang) * r)).add(bs[1].scale(Math.sin(ang) * r));
            add(o, q, Vec3.ZERO);
        }
    }

    /** Espiral subindo em volta de um ponto (aura, giro pelo corpo). */
    public static void riseSpiral(Vec3 base, double r, double h, int count, float phase, double turns, ParticleOptions o) {
        for (int i = 0; i < count; i++) {
            double t = i / (double) count;
            double a = phase + t * Math.PI * 2 * turns;
            double rr = r * (1.0 - 0.35 * t);
            add(o, base.x + Math.cos(a) * rr, base.y + t * h, base.z + Math.sin(a) * rr, -Math.sin(a) * 0.03, 0.02, Math.cos(a) * 0.03);
        }
    }

    /** Arco de corte: meia-lua de {@code arc} radianos à frente de {@code c}, no plano dado por {@code dir} e {@code tilt}. */
    public static void slashArc(Vec3 c, Vec3 dir, double tilt, double r, double arc, int count, ParticleOptions o, double out) {
        Vec3 f = dir.normalize();
        Vec3[] b = basis(f);
        // gira o "lado" em volta da frente para inclinar o corte
        Vec3 side = b[0].scale(Math.cos(tilt)).add(b[1].scale(Math.sin(tilt)));
        for (int i = 0; i <= count; i++) {
            double a = -arc / 2 + arc * i / count;
            Vec3 d = f.scale(Math.cos(a)).add(side.scale(Math.sin(a)));
            add(o, c.add(d.scale(r)), d.scale(out));
        }
    }

    /** Estrela de {@code points} pontas (linhas do centro para fora) no plano perpendicular a {@code normal}. */
    public static void star(Vec3 c, Vec3 normal, double r, int points, ParticleOptions o, double step) {
        Vec3[] b = basis(normal);
        double off = RND.nextDouble() * Math.PI;
        for (int i = 0; i < points; i++) {
            double a = off + Math.PI * 2 * i / points;
            Vec3 d = b[0].scale(Math.cos(a)).add(b[1].scale(Math.sin(a)));
            line(c, c.add(d.scale(r * (i % 2 == 0 ? 1.0 : 0.55))), step, o, 0);
        }
    }

    /** Linhas de velocidade: riscos paralelos a {@code dir} espalhados num cilindro de raio {@code r} em volta de {@code c}. */
    public static void speedLines(Vec3 c, Vec3 dir, double r, int count, double speed, ParticleOptions o) {
        Vec3 f = dir.normalize();
        Vec3[] b = basis(f);
        for (int i = 0; i < count; i++) {
            double a = RND.nextDouble() * Math.PI * 2;
            double rr = r * (0.3 + RND.nextDouble() * 0.7);
            Vec3 p = c.add(b[0].scale(Math.cos(a) * rr)).add(b[1].scale(Math.sin(a) * rr)).add(f.scale((RND.nextDouble() - 0.5) * 2));
            add(o, p, f.scale(speed * (0.7 + RND.nextDouble() * 0.6)));
        }
    }

    /** Anel que encolhe (energia sendo sugada para o centro): {@code steps} anéis em {@code ticks} ticks. */
    public static void implode(Vec3 c, @Nullable Vec3 normal, double r0, int steps, int ticks, ParticleOptions o) {
        int per = Math.max(1, ticks / Math.max(1, steps));
        repeat(0, steps, per, i -> {
            double r = r0 * (1.0 - i / (double) steps) + 0.15;
            ring(c, normal, r, n(14), o, -0.04);
        });
    }

    /** Detritos do bloco abaixo do ponto (terra que pula). */
    public static void debris(Vec3 at, int count, double spread, double up) {
        ClientLevel l = level();
        if (l == null || count <= 0) return;
        BlockPos below = BlockPos.containing(at.x, at.y - 0.3, at.z);
        BlockState st = l.getBlockState(below);
        if (st.isAir()) {
            below = below.below();
            st = l.getBlockState(below);
            if (st.isAir()) return;
        }
        BlockParticleOption o = new BlockParticleOption(ParticleTypes.BLOCK, st);
        for (int i = 0; i < count; i++) {
            double a = RND.nextDouble() * Math.PI * 2;
            double r = RND.nextDouble() * spread;
            l.addParticle(o, at.x + Math.cos(a) * r, at.y + 0.1, at.z + Math.sin(a) * r,
                    Math.cos(a) * 0.15 * spread, up * (0.5 + RND.nextDouble()), Math.sin(a) * 0.15 * spread);
        }
    }

    /** Detritos que voam em anel (impacto no chão) com fumaça baixa. */
    public static void groundImpact(Vec3 at, double radius, int rgbDust) {
        debris(at, n(18), radius * 0.6, 0.45);
        ring(at.add(0, 0.15, 0), null, radius * 0.25, n(16), smoke(rgbDust, 1.6F), 0.18);
        if (hi()) ring(at.add(0, 0.1, 0), null, radius * 0.2, n(10), ParticleTypes.CLOUD, 0.25);
    }

    // =====================================================================================
    //  Receitas prontas (antecipação / impacto / rescaldo)
    // =====================================================================================
    /** Carga: anéis fechando e fagulhas sendo sugadas para {@code c} durante {@code ticks}, com zumbido subindo. */
    public static void chargeUp(Vec3 c, int rgb, double radius, int ticks) {
        int steps = Math.max(2, ticks / 2);
        repeat(0, steps, 2, i -> {
            double r = radius * (1.0 - i / (double) steps) + 0.2;
            ring(c, new Vec3(Math.sin(i * 0.7), 1, Math.cos(i * 0.7)), r, n(12), glow(rgb, 0.25F), -0.06);
            for (int k = 0; k < n(4); k++) {
                Vec3 d = new Vec3(RND.nextGaussian(), RND.nextGaussian(), RND.nextGaussian()).normalize().scale(radius * 1.3);
                add(mote(light(rgb, 0.3F), 0.22F), c.add(d), d.scale(-0.09));
            }
            if (i % 2 == 0) sound(c, net.minecraft.sounds.SoundEvents.BEACON_AMBIENT, 0.5F, 0.7F + 1.1F * i / steps);
        });
        later(ticks, () -> add(glow(0xFFFFFF, (float) radius * 0.5F), c, Vec3.ZERO));
    }

    /** Explosão grande: clarão, duas ondas, esfera de faíscas, brilho, poeira do chão e tremor. */
    public static void nova(Vec3 c, int rgb, float radius, boolean ground) {
        flash(c, light(rgb, 0.3F), radius * 1.4F);
        later(1, () -> flash(c, 0xFFFFFF, radius * 0.7F));
        shock(c.add(0, ground ? 0.1 : 0, 0), rgb, radius * 2.2F, 12, ground ? null : new Vec3(0, 1, 0));
        if (hi()) later(2, () -> shock(c.add(0, ground ? 0.12 : 0, 0), light(rgb, 0.4F), radius * 1.4F, 10, ground ? null : new Vec3(0, 1, 0)));
        sphere(c, n((int) (18 + radius * 6)), 0.25 + radius * 0.08, spark(rgb, 0.35F));
        sphere(c, n((int) (8 + radius * 3)), 0.12 + radius * 0.04, glow(light(rgb, 0.3F), 0.5F));
        if (ground) groundImpact(c, radius * 1.5, dark(rgb, 0.4F));
        later(3, () -> sphere(c, n(10), 0.06, smoke(dark(rgb, 0.5F), 1.8F)));
        shake(c, 8F + radius * 4F, Math.min(2F, 0.35F + radius * 0.25F), 6 + (int) radius * 2);
    }

    /** Corte de luz: meia-lua de rastro com núcleo branco e faíscas saindo; {@code tilt} gira o plano do corte. */
    public static void slashStreak(Vec3 c, Vec3 dir, double tilt, double r, int rgb, double arc) {
        int pts = n(hi() ? 30 : 16);
        slashArc(c, dir, tilt, r, arc, pts, streak(rgb, 0.7F), 0.05);
        slashArc(c, dir, tilt, r * 0.97, arc * 0.9, Math.max(4, pts / 2), glow(light(rgb, 0.6F), 0.35F), 0.0);
        if (hi()) slashArc(c, dir, tilt, r * 1.08, arc * 0.8, Math.max(4, pts / 3), spark(rgb, 0.25F), 0.15);
    }

    /** Clarão do cano: luz, estrela, cone de faíscas e fumaça. */
    public static void muzzleFlash(Vec3 at, Vec3 dir, int rgb, float scale) {
        add(glow(light(rgb, 0.5F), 0.6F * scale), at, Vec3.ZERO);
        if (scale >= 1.4F) flash(at, rgb, 0.6F * scale);
        star(at.add(dir.normalize().scale(0.15)), dir, 0.35 * scale, 6, glow(rgb, 0.18F), 0.08);
        cone(at, dir, 0.35, n((int) (6 * scale)), 0.35 * scale, ember(rgb, 0.3F));
        if (hi()) cone(at, dir, 0.2, n(4), 0.6 * scale, spark(light(rgb, 0.4F), 0.2F));
        add(smoke(0x8A8A8A, 0.9F * scale), at.add(dir.normalize().scale(0.3)), dir.normalize().scale(0.03).add(0, 0.02, 0));
    }

    /** Traçante: núcleo brilhante + risco, com clarão no fim. */
    public static void tracerLine(Vec3 from, Vec3 to, int rgb, float width) {
        line(from, to, hi() ? 0.35 : 0.6, glow(rgb, 0.2F * width), 0.0);
        if (hi()) line(from, to, 0.9, streak(light(rgb, 0.5F), 0.35F * width), 0.04);
        add(glow(0xFFFFFF, 0.5F * width), to, Vec3.ZERO);
    }

    /** Faísca de acerto. */
    public static void hitSpark(Vec3 at, Vec3 dir, int rgb, float scale) {
        Vec3 back = dir.lengthSqr() < 1.0E-4 ? new Vec3(0, 1, 0) : dir.normalize().scale(-1);
        add(glow(0xFFFFFF, 0.5F * scale), at, Vec3.ZERO);
        cone(at, back, 0.9, n((int) (8 * scale)), 0.3 * scale, spark(rgb, 0.25F));
        if (hi()) ring(at, back, 0.25 * scale, n(8), glow(rgb, 0.2F), 0.12 * scale);
    }

    /** Some numa nuvem: anel de fumaça baixo, fios subindo e um clarão escuro. */
    public static void vanish(Vec3 feet, int rgb, float scale) {
        ring(feet.add(0, 0.2, 0), null, 0.4 * scale, n(14), smoke(rgb, 1.4F * scale), 0.12);
        for (int i = 0; i < n(10); i++) {
            add(wisp(rgb, 0.4F), feet.x + (RND.nextDouble() - 0.5) * 0.8, feet.y + RND.nextDouble() * 1.8, feet.z + (RND.nextDouble() - 0.5) * 0.8,
                    0, 0.03 + RND.nextDouble() * 0.04, 0);
        }
        if (hi()) sphere(feet.add(0, 1, 0), n(8), 0.15, smoke(dark(rgb, 0.4F), 1.2F));
    }

    /** Silhueta de fumaça/luz no formato do corpo (imagem residual) que se desfaz. */
    public static void afterimage(Vec3 feet, float yawDeg, int rgb, float alpha) {
        double yaw = Math.toRadians(yawDeg);
        Vec3 side = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
        int pts = n(hi() ? 22 : 12);
        for (int i = 0; i < pts; i++) {
            double h = RND.nextDouble() * 1.8;
            double w = h > 1.4 ? 0.18 : (h > 0.75 ? 0.32 : 0.14);
            double s = (RND.nextDouble() - 0.5) * 2 * w + (h < 0.75 ? (RND.nextBoolean() ? 0.1 : -0.1) : 0);
            add(wisp(rgb, 0.3F * alpha + 0.1F), feet.add(side.scale(s)).add(0, h, 0), new Vec3(0, 0.005, 0));
        }
    }

    /** Raio: núcleo, hélice de energia em volta e clarões nas pontas. */
    public static void beam(Vec3 from, Vec3 to, int rgb, float width) {
        line(from, to, 0.3, glow(light(rgb, 0.4F), 0.3F * width), 0.0);
        helix(from, to, 0.25 * width, from.distanceTo(to) / 2.5, n((int) Math.min(80, from.distanceTo(to) * 4)), RND.nextFloat() * 6F, spark(rgb, 0.2F));
        flash(from, rgb, 0.6F * width);
        flash(to, rgb, 0.9F * width);
    }

    // =====================================================================================
    //  Câmera e som
    // =====================================================================================
    /** Tremor de câmera (soma com os dos chefes; some com a distância). */
    public static void shake(Vec3 at, float radius, float intensity, int ticks) {
        float k = switch (FxQuality.level()) {
            case LOW -> 0.5F;
            case MEDIUM -> 0.8F;
            default -> 1F;
        };
        BossCameraShake.receive(new BossPayloads.Shake(at.x, at.y, at.z, radius, Math.min(3F, intensity * k), ticks));
    }

    public static void sound(Vec3 p, SoundEvent s, float volume, float pitch) {
        ClientLevel l = level();
        if (l == null) return;
        l.playLocalSound(p.x, p.y, p.z, s, SoundSource.PLAYERS, volume, pitch, false);
    }

    public static void sound(Vec3 p, Holder<SoundEvent> s, float volume, float pitch) {
        sound(p, s.value(), volume, pitch);
    }

    /** Som com variação aleatória de tom (± {@code var}). */
    public static void soundVar(Vec3 p, SoundEvent s, float volume, float pitch, float var) {
        sound(p, s, volume, pitch + (RND.nextFloat() - 0.5F) * 2F * var);
    }

    // =====================================================================================
    //  Cor
    // =====================================================================================
    public static int mix(int a, int b, float f) {
        f = Mth.clamp(f, 0F, 1F);
        int r = (int) Mth.lerp(f, (a >> 16) & 255, (b >> 16) & 255);
        int g = (int) Mth.lerp(f, (a >> 8) & 255, (b >> 8) & 255);
        int bl = (int) Mth.lerp(f, a & 255, b & 255);
        return (r << 16) | (g << 8) | bl;
    }

    public static int light(int rgb, float f) {
        return mix(rgb, 0xFFFFFF, f);
    }

    public static int dark(int rgb, float f) {
        return mix(rgb, 0x000000, f);
    }

    /** Centro do corpo (ou o ponto dado se a entidade sumiu). */
    public static Vec3 center(@Nullable Entity e, Vec3 fallback) {
        return e == null ? fallback : e.position().add(0, e.getBbHeight() * 0.55, 0);
    }

    /** Ponto da mão (à frente e ao lado do olho). */
    public static Vec3 hand(@Nullable Entity e, Vec3 fallback) {
        if (e == null) return fallback;
        Vec3 look = e.getLookAngle();
        Vec3 right = basis(look)[0];
        return e.getEyePosition().add(look.scale(0.6)).add(right.scale(0.35)).add(0, -0.3, 0);
    }

    private FxKit() {}
}
