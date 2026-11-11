package com.renanpontes.isekaimahou.feature.forms_a.glyph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.forms_a.FormAim;
import com.renanpontes.isekaimahou.feature.forms_a.FormsFx;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Corte: arco de lâmina de 120° a 3.5 blocos (× área). Acerta todos no arco.
 * Dividir = golpes extras em sequência (cruzados, formando um "X"), cada um acertando de novo;
 * Fantasma = corta através de paredes; Sensível = também corta os blocos tocados pelo arco.
 * <p>
 * Estágios (v0.7): a lâmina surge no ponto de origem do estágio, na direção dele (o combo inteiro sai desse ponto),
 * sem atingir o alvo que o estágio anterior acabou de acertar; com Saltar, ela "voa" até perto do próximo inimigo.
 * Avisa {@code impact} em cada criatura cortada e {@code end} depois do último golpe do combo.
 */
public class SlashForm extends FormGlyph {
    private static final float REACH = 3.5F;
    private static final double HALF = Math.toRadians(60);
    /** Intervalo entre os golpes extras do Dividir. */
    private static final int COMBO_TICKS = 7;

    public SlashForm() {
        super("slash", 5);
        element(Element.ARCANE);
        tier(2);
    }

    @Override
    public boolean stageable() { return true; }

    @Override
    public int baseCooldown() { return 10; }

    @Override
    public void cast(SpellContext ctx) {
        if (!FormAim.casterReady(ctx)) return;
        int n = ctx.formStats().count();
        // Num estágio a lâmina fica parada no ponto de origem; sem estágio, cada golpe sai de onde o conjurador está
        Vec3 fixedOrigin = null;
        Vec3 fixedDir = null;
        if (ctx.hasOrigin()) {
            fixedDir = FormAim.aimDir(ctx);
            fixedOrigin = FormAim.closeIn(ctx, ctx.eyePos(), fixedDir, ctx.formStats().radius(REACH));
            FormsFx.dash(ctx, ctx.eyePos(), fixedOrigin);
        }
        slash(ctx, 0, n, fixedOrigin, fixedDir);
        for (int i = 1; i < n; i++) {
            final int idx = i;
            final Vec3 o = fixedOrigin, d = fixedDir;
            SpellScheduler.schedule(i * COMBO_TICKS, () -> slash(ctx, idx, n, o, d));
        }
    }

    private static void slash(SpellContext ctx, int idx, int total, Vec3 fixedOrigin, Vec3 fixedDir) {
        LivingEntity caster = ctx.caster();
        SpellStats s = ctx.formStats();
        Vec3 origin;
        Vec3 aim;
        if (fixedOrigin != null) {
            origin = fixedOrigin;
            aim = fixedDir;
        } else {
            if (caster == null || !caster.isAlive() || caster.level() != ctx.level()) return;
            origin = ctx.eyePos().subtract(0, 0.35, 0);
            aim = FormAim.aimDir(ctx);
            caster.swing(idx % 2 == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, true);
        }
        float reach = s.radius(REACH);
        FormAim.Frame frame = tilted(FormAim.Frame.of(aim), idx);
        double vertical = 1.4 + 0.4 * Math.max(0, s.aoe);

        List<Entity> cut = new ArrayList<>();
        for (Entity e : ctx.level().getEntities(caster, new AABB(origin, origin).inflate(reach + 1.0),
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && !ctx.ignores(e))) {
            Vec3 v = FormAim.closestPoint(e, origin).subtract(origin);
            double dist = v.length();
            if (dist > reach) continue;
            if (dist > 0.8) {
                double angle = Math.atan2(v.dot(frame.right()), v.dot(frame.forward()));
                if (Math.abs(angle) > HALF || Math.abs(v.dot(frame.up())) > vertical) continue;
            }
            if (!FormAim.affects(ctx, e)) continue;
            if (!s.ghost && !FormAim.clear(ctx, origin, FormAim.center(e), caster)) continue;
            cut.add(e);
        }
        cut.sort(Comparator.comparingDouble(e -> e.distanceToSqr(origin)));
        for (Entity e : cut) {
            Vec3 c = FormAim.center(e);
            Vec3 d = c.subtract(origin);
            // Impacto real em cada alvo cortado (Ao Acertar nasce no primeiro; Saltar parte dele)
            ctx.impact(c, d.lengthSqr() > 1.0E-4 ? d.normalize() : frame.forward(), e);
            // Golpes do combo acertam de novo: zera a invencibilidade curta do vanilla
            if (idx > 0) e.invulnerableTime = 0;
            ctx.resolveOnEntity(e, null);
            FormsFx.send(ctx.level(), ParticleTypes.DAMAGE_INDICATOR, c, 3, 0.2, 0.1);
            FormsFx.burst(ctx, c, 6, 0.2);
        }

        // Blocos tocados pelo fio da lâmina (raspão: não é impacto)
        Set<BlockPos> done = new HashSet<>();
        for (int i = 0; i < 5; i++) {
            Vec3 dir = frame.dir(-HALF + 2 * HALF * i / 4.0, 0);
            BlockHitResult bh = FormAim.clipBlocks(ctx, origin, origin.add(dir.scale(reach)), caster);
            if (bh.getType() == HitResult.Type.BLOCK && done.add(bh.getBlockPos())) ctx.resolveOnBlock(bh, null);
        }

        // Visual animado em 3 ticks (a lâmina "varre" de um lado para o outro; golpes ímpares no sentido contrário)
        boolean leftToRight = idx % 2 == 1;
        drawArc(ctx, origin, frame, reach, leftToRight, 0);
        for (int seg = 1; seg < 3; seg++) {
            final int sg = seg;
            SpellScheduler.schedule(seg, () -> drawArc(ctx, origin, frame, reach, leftToRight, sg));
        }
        FormsFx.sound(ctx, origin, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 1.0F + 0.12F * idx);
        FormsFx.sound(ctx, origin, SoundEvents.ILLUSIONER_CAST_SPELL, 0.35F, 1.8F);
        // Último golpe do combo: o corte terminou na ponta da lâmina
        if (idx == total - 1) ctx.end(origin.add(frame.forward().scale(reach)), frame.forward());
    }

    /** Inclina o plano do arco (golpes extras do combo saem em diagonal, alternando). */
    private static FormAim.Frame tilted(FormAim.Frame f, int idx) {
        if (idx == 0) return f;
        double t = Math.toRadians(idx % 2 == 1 ? 28 : -28);
        Vec3 r = f.right().scale(Math.cos(t)).add(f.up().scale(Math.sin(t)));
        Vec3 u = f.up().scale(Math.cos(t)).subtract(f.right().scale(Math.sin(t)));
        return new FormAim.Frame(f.forward(), r, u);
    }

    /** Desenha 1/3 do arco: varredura (SWEEP_ATTACK) + fio de pó colorido em duas camadas + faíscas do elemento. */
    private static void drawArc(SpellContext ctx, Vec3 origin, FormAim.Frame frame, float reach, boolean leftToRight, int seg) {
        ServerLevel level = ctx.level();
        DustParticleOptions edge = FormsFx.dust(ctx.color(), 1.3F);
        DustParticleOptions core = FormsFx.dust(FormsFx.lighten(ctx.color(), 0.6F), 0.8F);
        ParticleOptions sig = FormsFx.jet(ctx.element());
        double segSize = 2 * HALF / 3;
        double a0 = -HALF + segSize * seg;
        double a1 = a0 + segSize;
        if (!leftToRight) {
            double b0 = -a1, b1 = -a0;
            a0 = b0;
            a1 = b1;
        }
        int points = Math.max(6, (int) (reach * segSize * 4));
        for (int i = 0; i <= points; i++) {
            double a = a0 + (a1 - a0) * i / points;
            Vec3 dir = frame.dir(a, 0);
            Vec3 outer = origin.add(dir.scale(reach));
            Vec3 inner = origin.add(dir.scale(reach * 0.78));
            FormsFx.send(level, edge, outer.x, outer.y, outer.z, 1, 0.02, 0.02, 0.02, 0);
            FormsFx.send(level, core, inner.x, inner.y, inner.z, 1, 0.02, 0.02, 0.02, 0);
            if (i % 3 == 0) FormsFx.moving(level, sig, outer, dir, 0.08);
        }
        // Varredura do vanilla: o tamanho acompanha o alcance (xSpeed negativo aumenta a partícula)
        float size = reach / REACH;
        Vec3 mid = origin.add(frame.dir((a0 + a1) / 2, 0).scale(reach * 0.62));
        FormsFx.moving(level, ParticleTypes.SWEEP_ATTACK, mid, new Vec3((1.0 - size) * 2.0, 0, 0), 1.0);
    }
}
