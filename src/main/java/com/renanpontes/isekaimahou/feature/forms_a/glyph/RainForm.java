package com.renanpontes.isekaimahou.feature.forms_a.glyph;

import java.util.List;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.forms_a.FormAim;
import com.renanpontes.isekaimahou.feature.forms_a.FormsFx;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Chuva: mira um ponto (até 48 blocos); uma nuvem mágica se forma acima e, durante 3 s (× duração), caem
 * 8 × (1 + Expandir) impactos pequenos em lugares aleatórios num raio de 5 blocos. Cada impacto aplica a magia
 * num raio de 1.2 e no bloco atingido. Parte das gotas (30%) cai perto de uma criatura que a magia afeta dentro
 * da área — só ao acaso, uma gota de raio 1.2 quase nunca acertaria alguém, e a forma de 20 de mana ficaria fraca.
 * Expandir = mais impactos e área um pouco maior; Concentrar = área menor; Dividir = +4 impactos cada;
 * Teleguiado = 70% das gotas caem em cima dos alvos dentro da área, sem desvio.
 * <p>
 * Estágios (v0.7): a nuvem se forma sobre o ponto de impacto que gerou o estágio ({@code ctx.anchor()}; com Saltar,
 * sobre o próximo inimigo) e a 1ª gota cai em quem está ali. Cada gota que atinge uma criatura avisa {@code impact}
 * (gota no chão vazio é só chuva: não consome o Ao Acertar), na direção do centro da nuvem para a gota; quando a chuva
 * passa, avisa {@code end}.
 */
public class RainForm extends FormGlyph {
    private static final double RANGE = 48.0;
    private static final double CLOUD_HEIGHT = 11.0;
    private static final float IMPACT_RADIUS = 1.2F;
    /** Ticks que cada gota leva para cair (rastro animado antes do impacto). */
    private static final int FALL_FRAMES = 3;
    /** Chance de uma gota procurar um alvo na área (sem / com Teleguiado). */
    private static final float SEEK = 0.3F;
    private static final float SEEK_HOMING = 0.7F;
    /** Desvio máximo (blocos) das gotas que procuram alvo sem Teleguiado: quem se mexe pode escapar. */
    private static final double SEEK_JITTER = 0.9;
    /** Até quantos blocos abaixo do ponto de impacto a nuvem procura o chão. */
    private static final double ANCHOR_GROUND = 16.0;

    public RainForm() {
        super("rain", 20);
        element(Element.ARCANE);
        tier(4);
        // v0.7: liberada a partir do 3º Círculo (antes era o 4º)
        minCircle(3);
    }

    @Override
    public boolean stageable() { return true; }

    @Override
    public int baseCastTicks() { return 20; }

    @Override
    public int baseCooldown() { return 100; }

    @Override
    public void cast(SpellContext ctx) {
        if (!FormAim.casterReady(ctx)) return;
        SpellStats s = ctx.formStats();
        FormAim.Point point = FormAim.areaPoint(ctx, RANGE, ANCHOR_GROUND);
        Vec3 center = point.pos();
        Vec3 flat = FormAim.horizontalAim(ctx);
        // Num estágio, a 1ª gota cai em quem está no ponto de impacto: com Saltar a chuva segue DELE para o próximo
        // inimigo (uma gota ao acaso no alvo anterior faria a magia voltar para trás)
        LivingEntity focus = ctx.anchor() != null && point.entity() instanceof LivingEntity le ? le : null;
        double area = (s.concentrate ? 3.0 : 5.0) + Math.max(0, s.aoe);
        int duration = s.duration(60);
        int drops = 8 * (1 + Math.max(0, s.aoe)) + 4 * Math.max(0, s.split);

        // Altura da nuvem: 11 blocos acima, ou logo abaixo do teto
        Vec3 from = center.add(0, 0.5, 0);
        BlockHitResult roof = ctx.level().clip(FormAim.clipContext(from, center.add(0, CLOUD_HEIGHT, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null));
        double top = roof.getType() == HitResult.Type.BLOCK ? roof.getLocation().y - 0.6 : center.y + CLOUD_HEIGHT;

        for (int t = 0; t <= duration; t += 5) {
            final boolean showArea = t % 20 == 0;
            Runnable cloud = () -> cloud(ctx, center, top, area, showArea);
            if (t == 0) cloud.run();
            else SpellScheduler.schedule(t, cloud);
        }

        for (int k = 0; k < drops; k++) {
            LivingEntity onto = k == 0 ? focus : null;
            int at = onto != null ? FALL_FRAMES + 3 : FALL_FRAMES + 3 + ctx.random().nextInt(Math.max(1, duration - FALL_FRAMES - 3));
            SpellScheduler.schedule(at - FALL_FRAMES, () -> drop(ctx, center, area, top, flat, onto));
        }
        // A última gota cai antes de "duration + FALL_FRAMES": depois disso a chuva passou (Ao Terminar nasce no centro)
        SpellScheduler.schedule(duration + FALL_FRAMES + 1, () -> ctx.end(center.add(0, 0.4, 0), flat));
        FormsFx.sound(ctx, center, SoundEvents.ILLUSIONER_PREPARE_MIRROR, 1.0F, 0.8F);
    }

    /**
     * Começa a cair uma gota: escolhe a coluna AGORA (às vezes perto de um alvo que esteja na área neste momento;
     * com Teleguiado, mais vezes e em cima dele), anima o rastro por {@link #FALL_FRAMES} ticks e então impacta.
     * {@code onto} (opcional): criatura em que esta gota cai em cheio, se ainda estiver viva e debaixo da nuvem.
     */
    private static void drop(SpellContext ctx, Vec3 center, double area, double top, Vec3 flat, LivingEntity onto) {
        Vec3 column = null;
        boolean homing = ctx.formStats().homing;
        if (onto != null && onto.isAlive() && onto.level() == ctx.level()
                && Math.hypot(onto.getX() - center.x, onto.getZ() - center.z) <= area + 1.0) {
            column = new Vec3(onto.getX(), center.y, onto.getZ());
        } else if (ctx.random().nextFloat() < (homing ? SEEK_HOMING : SEEK)) {
            List<LivingEntity> targets = FormAim.targetsAround(ctx, center, area + 1.0);
            if (!targets.isEmpty()) {
                LivingEntity e = targets.get(ctx.random().nextInt(targets.size()));
                double j = homing ? 0.0 : SEEK_JITTER;
                column = new Vec3(e.getX() + (ctx.random().nextDouble() * 2 - 1) * j, center.y,
                        e.getZ() + (ctx.random().nextDouble() * 2 - 1) * j);
            }
        }
        if (column == null) {
            double r = area * Math.sqrt(ctx.random().nextDouble());
            double a = ctx.random().nextDouble() * Math.PI * 2;
            column = center.add(Math.cos(a) * r, 0, Math.sin(a) * r);
        }
        Vec3 ground = ground(ctx, column, top, center.y);
        streak(ctx, ground, top, 0);
        for (int f = 1; f < FALL_FRAMES; f++) {
            final int frame = f;
            SpellScheduler.schedule(f, () -> streak(ctx, ground, top, frame));
        }
        SpellScheduler.schedule(FALL_FRAMES, () -> impact(ctx, ground, center, flat));
    }

    /** Ponto de impacto de uma gota: o primeiro bloco/líquido descendo da nuvem naquela coluna. */
    private static Vec3 ground(SpellContext ctx, Vec3 column, double top, double baseY) {
        Vec3 from = new Vec3(column.x, top, column.z);
        BlockHitResult bh = ctx.level().clip(FormAim.clipContext(from, new Vec3(column.x, baseY - 10, column.z),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, null));
        return bh.getType() == HitResult.Type.MISS ? new Vec3(column.x, baseY, column.z) : bh.getLocation();
    }

    /** Nuvem mágica acima da área (e, de vez em quando, o círculo no chão mostrando a área). */
    private static void cloud(SpellContext ctx, Vec3 center, double top, double area, boolean showArea) {
        ServerLevel level = ctx.level();
        ParticleOptions dark = FormsFx.dust(darken(ctx.color()), 2.5F);
        ParticleOptions light = FormsFx.dust(FormsFx.lighten(ctx.color(), 0.4F), 2.0F);
        int puffs = (int) (area * area * 0.9);
        for (int i = 0; i < puffs; i++) {
            double r = (area + 0.8) * Math.sqrt(ctx.random().nextDouble());
            double a = ctx.random().nextDouble() * Math.PI * 2;
            double x = center.x + Math.cos(a) * r, z = center.z + Math.sin(a) * r;
            double y = top + ctx.random().nextDouble() * 0.8;
            FormsFx.send(level, i % 3 == 0 ? light : dark, x, y, z, 1, 0.2, 0.1, 0.2, 0);
            if (i % 4 == 0) FormsFx.send(level, ParticleTypes.CLOUD, x, y + 0.3, z, 1, 0.3, 0.1, 0.3, 0.005);
        }
        if (showArea) FormsFx.ring(ctx, center.add(0, 0.05, 0), area, 1.5);
    }

    /** Um quadro do rastro vertical da gota caindo (3 quadros até o chão). */
    private static void streak(SpellContext ctx, Vec3 ground, double top, int frame) {
        double y0 = top - (top - ground.y) * frame / FALL_FRAMES;
        double y1 = top - (top - ground.y) * (frame + 1) / FALL_FRAMES;
        ServerLevel level = ctx.level();
        ParticleOptions dust = FormsFx.dust(ctx.color(), 1.1F);
        ParticleOptions sig = ElementFx.signature(ctx.element());
        int steps = Math.max(3, (int) ((y0 - y1) * 3));
        for (int i = 0; i <= steps; i++) {
            double y = y0 + (y1 - y0) * i / steps;
            FormsFx.send(level, dust, ground.x, y, ground.z, 1, 0.02, 0.05, 0.02, 0);
        }
        FormsFx.send(level, sig, ground.x, y1, ground.z, 1, 0.05, 0.05, 0.05, 0.0);
    }

    private static void impact(SpellContext ctx, Vec3 ground, Vec3 center, Vec3 flat) {
        // Gota que atinge uma criatura é um impacto real (Ao Acertar nasce ali; Saltar leva a nuvem ao próximo inimigo)
        LivingEntity victim = FormAim.nearestAffected(ctx, ground, IMPACT_RADIUS);
        if (victim != null) {
            Vec3 out = new Vec3(ground.x - center.x, 0, ground.z - center.z);
            ctx.impact(ground.add(0, 0.4, 0), out.lengthSqr() > 0.01 ? out.normalize() : flat, victim);
        }
        ctx.resolveArea(ground, IMPACT_RADIUS, null, true);
        ServerLevel level = ctx.level();
        Vec3 up = ground.add(0, 0.2, 0);
        FormsFx.burst(ctx, up, 10, 0.3);
        FormsFx.ring(ctx, ground.add(0, 0.05, 0), IMPACT_RADIUS, 3.0);
        BlockState st = level.getBlockState(BlockPos.containing(ground.x, ground.y - 0.2, ground.z));
        if (!st.getFluidState().isEmpty()) {
            FormsFx.send(level, Vfx.splash(), up, 12, 0.3, 0.2);
        } else if (!st.isAir() && st.getRenderShape() != RenderShape.INVISIBLE) {
            FormsFx.send(level, new BlockParticleOption(ParticleTypes.BLOCK, st), up, 8, 0.25, 0.1);
        }
        FormsFx.sound(ctx, ground, SoundEvents.SHULKER_BULLET_HIT, 0.5F, 1.3F);
    }

    private static int darken(int rgb) {
        int r = (int) (((rgb >> 16) & 255) * 0.55), g = (int) (((rgb >> 8) & 255) * 0.55), b = (int) ((rgb & 255) * 0.55);
        return (r << 16) | (g << 8) | b;
    }
}
