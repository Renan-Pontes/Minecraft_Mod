package com.renanpontes.isekaimahou.feature.forms_a.glyph;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.forms_a.FormAim;
import com.renanpontes.isekaimahou.feature.forms_a.FormsFx;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.glyph.fx.SpellVisuals;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Feixe: raio instantâneo de até 24 blocos.
 * Perfurar = atravessa N alvos; Dividir = feixes em leque; Espalhar = direções aleatórias; Expandir = feixe mais grosso;
 * Acelerar = +4 blocos de alcance; Ricochete = reflete nos blocos; Fantasma = atravessa blocos;
 * Teleguiado = "gruda" no inimigo mais perto da mira. Aliados que a magia não afeta não bloqueiam o feixe.
 * <p>
 * Estágios (v0.7): sai de {@code ctx.eyePos()}/{@code ctx.look()} (o ponto onde o estágio anterior acertou) e
 * atravessa o alvo que ele acabou de atingir. Avisa {@code impact} em cada criatura atingida e no bloco onde para,
 * e {@code end} quando chega ao alcance máximo sem parar em nada (Ao Terminar nasce no fim do raio).
 * Visual: raio de luz de verdade ({@link FormsFx#beam}) + faíscas do elemento.
 */
public class BeamForm extends FormGlyph {
    private static final double RANGE = 24.0;
    private static final double FAN_DEGREES = 7.0;
    /** Ticks que o raio fica visível (abre num instante e afina até sumir). */
    private static final int BEAM_LIFE = 9;

    public BeamForm() {
        super("beam", 6);
        element(Element.ARCANE);
        tier(1);
    }

    @Override
    public boolean stageable() { return true; }

    @Override
    public int baseCooldown() { return 12; }

    @Override
    public void cast(SpellContext ctx) {
        if (!FormAim.casterReady(ctx)) return;
        SpellStats s = ctx.formStats();
        Vec3 eye = ctx.eyePos();
        FormAim.Frame frame = FormAim.Frame.of(FormAim.aimDir(ctx));
        double range = RANGE + 4.0 * Math.max(0, s.speed);
        double width = (s.concentrate ? 0.08 : 0.15) + 0.35 * Math.max(0, s.aoe);
        // Sai da mão do conjurador; num estágio, do próprio ponto de origem (não há mão lá)
        Vec3 hand = ctx.hasOrigin() ? eye
                : eye.add(frame.forward().scale(0.45)).add(frame.right().scale(0.32)).add(frame.up().scale(-0.25));

        int n = s.count();
        Set<UUID> homed = new HashSet<>();
        for (int i = 0; i < n; i++) {
            double yaw = n == 1 ? 0 : Math.toRadians((i - (n - 1) / 2.0) * FAN_DEGREES);
            double pitch = 0;
            if (s.spread) {
                yaw = Math.toRadians((ctx.random().nextDouble() - 0.5) * 30.0);
                pitch = Math.toRadians((ctx.random().nextDouble() - 0.5) * 16.0);
            }
            Vec3 dir = frame.dir(yaw, pitch);
            if (s.homing) dir = homingDir(ctx, eye, dir, range, homed);
            fire(ctx, eye, hand, dir, range, width);
        }
        FormsFx.sound(ctx, hand, SoundEvents.BEACON_POWER_SELECT, 0.45F, 1.9F);
        FormsFx.sound(ctx, hand, SoundEvents.AMETHYST_BLOCK_CHIME, 0.5F, 0.6F);
    }

    /**
     * Teleguiado: troca a direção pela do alvo visível mais alinhado com a mira (até 25°).
     * "Alvo" = quem a magia afeta (inimigo num feixe de dano, aliado num feixe de cura), fora o alvo do estágio anterior.
     */
    private static Vec3 homingDir(SpellContext ctx, Vec3 eye, Vec3 dir, double range, Set<UUID> taken) {
        LivingEntity best = null;
        double bestDot = Math.cos(Math.toRadians(25));
        for (LivingEntity e : FormAim.targetsAround(ctx, eye, range)) {
            if (taken.contains(e.getUUID()) || ctx.ignores(e)) continue;
            Vec3 to = FormAim.center(e).subtract(eye).normalize();
            double dot = to.dot(dir);
            if (dot > bestDot && FormAim.clear(ctx, eye, FormAim.center(e), ctx.caster())) {
                bestDot = dot;
                best = e;
            }
        }
        if (best == null) return dir;
        taken.add(best.getUUID());
        return FormAim.center(best).subtract(eye).normalize();
    }

    /** Largura visual do raio: Expandir engrossa, Amplificar/Carregar dão um pouco mais de corpo, Concentrar afina. */
    private static float visualWidth(SpellStats s) {
        float w = (s.concentrate ? 0.15F : 0.24F) + 0.16F * Math.max(0, s.aoe) + 0.04F * Math.max(0, s.amplify + s.charge);
        return Math.min(1.2F, w);
    }

    /**
     * Um feixe: atravessa quem a magia não afeta (e o alvo do estágio anterior), aplica nos alvos (Perfurar),
     * reflete (Ricochete) e para no bloco. Cada trecho reto vira um raio de luz.
     */
    private static void fire(SpellContext ctx, Vec3 origin, Vec3 visualOrigin, Vec3 dir, double range, double width) {
        SpellStats s = ctx.formStats();
        LivingEntity caster = ctx.caster();
        Set<UUID> hit = new HashSet<>();
        int pierceLeft = Math.max(0, s.pierce);
        int bounces = Math.max(0, s.ricochet);
        float vis = visualWidth(s);
        Vec3 from = origin;
        Vec3 visFrom = visualOrigin;
        double remaining = range;

        while (true) {
            Vec3 to = from.add(dir.scale(remaining));
            BlockHitResult bh = s.ghost ? null : FormAim.clipBlocks(ctx, from, to, caster);
            boolean blocked = bh != null && bh.getType() == HitResult.Type.BLOCK;
            Vec3 end = blocked ? bh.getLocation() : to;

            List<FormAim.Hit> along = FormAim.entitiesAlong(ctx, from, end, width,
                    e -> e != caster && !hit.contains(e.getUUID()) && !ctx.ignores(e));
            for (FormAim.Hit h : along) {
                if (!FormAim.affects(ctx, h.entity())) continue;
                hit.add(h.entity().getUUID());
                // Impacto real: Ao Acertar/Saltar nascem aqui (avisado antes de aplicar, como pede o motor)
                ctx.impact(h.pos(), dir, h.entity());
                ctx.resolveOnEntity(h.entity(), null);
                FormsFx.burst(ctx, h.pos(), 12, 0.25);
                ElementFx.impact(ctx.level(), h.pos(), ctx.element(), ctx.color(), Math.max(0, SpellVisuals.tier(ctx) - 1), dir.scale(-1));
                if (pierceLeft-- <= 0) {
                    segment(ctx, visFrom, h.pos(), vis);
                    FormsFx.sound(ctx, h.pos(), SoundEvents.SHULKER_BULLET_HIT, 0.8F, 1.4F);
                    return;
                }
            }
            segment(ctx, visFrom, end, vis);

            if (!blocked) {
                // Fim do alcance: o feixe se dissipa (e Ao Terminar nasce aqui)
                FormsFx.send(ctx.level(), FormsFx.dust(ctx.color(), 0.8F), end, 6, 0.3, 0);
                ctx.end(end, dir);
                return;
            }
            double seg = from.distanceTo(end);
            // Para na parede se acabou o Ricochete (ou o alcance): isso é um impacto real; quicar não é
            boolean stops = bounces <= 0 || remaining - seg <= 0.5;
            if (stops) ctx.impact(end, dir, null);
            ctx.resolveOnBlock(bh, null);
            impactBlock(ctx, bh);
            if (stops) return;
            bounces--;
            remaining -= seg;
            Vec3 normal = Vec3.atLowerCornerOf(bh.getDirection().getNormal());
            dir = dir.subtract(normal.scale(2 * dir.dot(normal))).normalize();
            from = end.add(normal.scale(0.02));
            visFrom = from;
        }
    }

    /** Um trecho visível do feixe: raio de luz + faíscas do elemento. */
    private static void segment(SpellContext ctx, Vec3 from, Vec3 to, float width) {
        FormsFx.beam(ctx, from, to, width, BEAM_LIFE);
        FormsFx.sparks(ctx, from, to, 1.6);
    }

    private static void impactBlock(SpellContext ctx, BlockHitResult bh) {
        Vec3 p = bh.getLocation();
        FormsFx.burst(ctx, p, 10, 0.2);
        ElementFx.impact(ctx.level(), p, ctx.element(), ctx.color(), SpellVisuals.tier(ctx),
                Vec3.atLowerCornerOf(bh.getDirection().getNormal()));
        BlockState state = ctx.level().getBlockState(bh.getBlockPos());
        if (!state.isAir()) {
            FormsFx.send(ctx.level(), new BlockParticleOption(ParticleTypes.BLOCK, state), p, 8, 0.15, 0.1);
        }
        FormsFx.sound(ctx, p, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.7F, 1.7F);
    }
}
