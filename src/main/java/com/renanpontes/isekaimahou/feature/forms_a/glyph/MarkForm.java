package com.renanpontes.isekaimahou.feature.forms_a.glyph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.forms_a.FormAim;
import com.renanpontes.isekaimahou.feature.forms_a.FormsFx;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Mira: marca a criatura na mira (até 32 blocos; ela brilha) e, 2 s depois, a magia cai sobre ela onde quer que esteja.
 * Mirando o chão, marca o ponto. Dividir = marca também os inimigos mais próximos do alvo; Acelerar = detona antes;
 * Expandir = a detonação vira uma pequena área; Fantasma = mira através de paredes.
 * <p>
 * Estágios (v0.7): se houver uma criatura no ponto de impacto que gerou o estágio, marca ela direto — com Ao Acertar
 * é quem acabou de ser atingido; com Saltar, o próximo inimigo. Senão mira a partir do ponto de origem do estágio, na
 * direção dele (aí sem pegar o alvo anterior). Cada detonação avisa {@code impact}; depois da última, {@code end}.
 */
public class MarkForm extends FormGlyph {
    private static final double RANGE = 32.0;
    private static final int BASE_DELAY = 40;
    /** Raio de luz que cai sobre a marca na detonação: altura e duração (ticks). */
    private static final double STRIKE_HEIGHT = 9.0;
    private static final int STRIKE_LIFE = 10;

    public MarkForm() {
        super("mark", 8);
        element(Element.ARCANE);
        tier(2);
    }

    @Override
    public boolean stageable() { return true; }

    @Override
    public int baseCooldown() { return 30; }

    @Override
    public void cast(SpellContext ctx) {
        if (!FormAim.casterReady(ctx)) return;
        LivingEntity caster = ctx.caster();
        SpellStats s = ctx.formStats();
        int delay = Math.max(10, Math.round(BASE_DELAY / s.speedMult()));
        Vec3 eye = ctx.eyePos();
        Vec3 aim = FormAim.aimDir(ctx);
        Vec3 end = eye.add(aim.scale(RANGE));
        BlockHitResult bh = s.ghost ? null : FormAim.clipBlocks(ctx, eye, end, caster);
        boolean blocked = bh != null && bh.getType() == HitResult.Type.BLOCK;
        Vec3 limit = blocked ? bh.getLocation() : end;

        LivingEntity first = null;
        if (!ctx.hasOrigin() && caster instanceof Mob mob && mob.getTarget() != null && mob.getTarget().isAlive()
                && mob.distanceTo(mob.getTarget()) <= RANGE && FormAim.affects(ctx, mob.getTarget())) {
            first = mob.getTarget();
        } else {
            // Estágio nascido num ponto de impacto: marca direto a criatura que está ali — inclusive a que o estágio
            // anterior acabou de atingir (Ao Acertar → Mira marca quem foi atingido; a detonação vem 2 s depois).
            // Com Saltar a âncora são os pés do próximo inimigo, então a marca pula para ele.
            if (ctx.anchor() != null) first = FormAim.entityAt(ctx, ctx.anchor(), 1.0);
            if (first == null) {
                // Mira "generosa" (0.5 de folga) e só em quem a magia afeta
                for (FormAim.Hit h : FormAim.entitiesAlong(ctx, eye, limit, 0.5,
                        e -> e instanceof LivingEntity && e != caster && !ctx.ignores(e))) {
                    if (FormAim.affects(ctx, h.entity())) {
                        first = (LivingEntity) h.entity();
                        break;
                    }
                }
            }
        }

        // O fio da mira sai da mão do conjurador; num estágio, do ponto de origem
        Vec3 hand = ctx.hasOrigin() ? eye : eye.add(ctx.look().scale(0.5)).subtract(0, 0.25, 0);
        if (first == null) {
            if (blocked) {
                markGround(ctx, bh, delay, aim);
                FormsFx.beam(ctx, hand, bh.getLocation(), 0.06F, 6);
            } else {
                fizzle(ctx, limit, aim);
            }
            return;
        }

        List<LivingEntity> targets = new ArrayList<>();
        targets.add(first);
        int extra = s.count() - 1;
        if (extra > 0) {
            LivingEntity from = first;
            FormAim.targetsAround(ctx, first.position(), 10).stream()
                    .filter(e -> e != from && !ctx.ignores(e))
                    .sorted(Comparator.comparingDouble(e -> e.distanceToSqr(from)))
                    .limit(extra)
                    .forEach(targets::add);
        }
        for (LivingEntity t : targets) {
            mark(ctx, t, delay);
            FormsFx.beam(ctx, hand, FormAim.center(t), 0.06F, 6);
        }
        SpellScheduler.schedule(delay, () -> detonateAll(ctx, targets, aim));
        FormsFx.sound(ctx, eye, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.6F);
    }

    /** Marca uma criatura: brilho + anel que fecha em volta dela + contagem regressiva (a detonação vem depois). */
    private static void mark(SpellContext ctx, LivingEntity target, int delay) {
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, delay + 6, 0, false, false), ctx.caster());
        markFx(ctx, target, 0F);
        FormsFx.sound(ctx, target.position(), SoundEvents.ILLUSIONER_PREPARE_BLINDNESS, 0.6F, 1.5F);
        for (int t = 4; t < delay; t += 4) {
            final int tick = t;
            SpellScheduler.schedule(t, () -> {
                if (!valid(ctx, target)) return;
                float progress = tick / (float) delay;
                markFx(ctx, target, progress);
                if (tick % 8 == 0) FormsFx.sound(ctx, target.position(), SoundEvents.NOTE_BLOCK_HAT, 0.5F, 1.0F + progress);
            });
        }
    }

    private static boolean valid(SpellContext ctx, LivingEntity target) {
        return target.isAlive() && !target.isRemoved() && target.level() == ctx.level();
    }

    /** Anel que se fecha sobre a cabeça e no chão, conforme a detonação se aproxima. */
    private static void markFx(SpellContext ctx, LivingEntity target, float progress) {
        double r = (target.getBbWidth() * 0.9 + 0.7) * (1.0 - progress * 0.7);
        FormsFx.ring(ctx, target.position().add(0, target.getBbHeight() + 0.35, 0), r, 4.0);
        FormsFx.ring(ctx, target.position().add(0, 0.05, 0), r + 0.3, 3.0);
    }

    /** Hora da detonação: a magia cai em cada marca; o estágio termina na marca principal. */
    private static void detonateAll(SpellContext ctx, List<LivingEntity> targets, Vec3 aim) {
        for (LivingEntity t : targets) detonate(ctx, t, aim);
        LivingEntity main = targets.get(0);
        if (main.level() == ctx.level()) ctx.end(FormAim.center(main), aim);
    }

    private static void detonate(SpellContext ctx, LivingEntity target, Vec3 aim) {
        if (!valid(ctx, target)) {
            // Morreu ou sumiu antes: a marca se desfaz onde ele estava
            if (target.level() == ctx.level()) FormsFx.send(ctx.level(), ParticleTypes.SMOKE, FormAim.center(target), 8, 0.2, 0.02);
            return;
        }
        SpellStats s = ctx.formStats();
        Vec3 c = FormAim.center(target);
        // A detonação é o impacto real (Ao Acertar nasce no alvo marcado; Saltar leva a marca ao próximo)
        ctx.impact(c, aim, target);
        if (s.aoe > 0) {
            ctx.resolveArea(c, s.radius(1.5F), null, s.sensitive);
        } else {
            ctx.resolveOnEntity(target, null);
            if (s.sensitive) {
                ctx.resolveOnBlock(new BlockHitResult(target.position(), Direction.UP, target.blockPosition().below(), false), null);
            }
        }
        strikeFx(ctx, c, target.position(), s.aoe > 0 ? s.radius(1.5F) : target.getBbWidth() * 0.8 + 0.5);
    }

    /** Mirou o chão: marca o ponto e, depois do atraso, a magia cai ali (área pequena + bloco). */
    private static void markGround(SpellContext ctx, BlockHitResult bh, int delay, Vec3 aim) {
        SpellStats s = ctx.formStats();
        Vec3 p = bh.getLocation();
        float radius = s.radius(1.0F);
        FormsFx.ring(ctx, p.add(0, 0.05, 0), radius, 3.0);
        for (int t = 4; t < delay; t += 4) {
            final float progress = t / (float) delay;
            SpellScheduler.schedule(t, () -> FormsFx.ring(ctx, p.add(0, 0.05, 0), radius * (1.0 - progress * 0.6), 3.0));
        }
        SpellScheduler.schedule(delay, () -> {
            ctx.impact(p, aim, null);
            ctx.resolveOnBlock(bh, null);
            ctx.resolveArea(p, radius, null, false);
            strikeFx(ctx, p.add(0, 0.5, 0), p, radius);
            ctx.end(p, aim);
        });
    }

    /** A magia "caindo" no alvo: raio de luz do céu + explosão de partículas + anel. */
    private static void strikeFx(SpellContext ctx, Vec3 center, Vec3 feet, double radius) {
        FormsFx.beam(ctx, center.add(0, STRIKE_HEIGHT, 0), feet, (float) Math.min(1.0, 0.3 + radius * 0.15), STRIKE_LIFE);
        FormsFx.sparks(ctx, center.add(0, STRIKE_HEIGHT, 0), center, 1.5);
        FormsFx.burst(ctx, center, 24, 0.4);
        FormsFx.ring(ctx, feet.add(0, 0.05, 0), radius, 3.0);
        FormsFx.send(ctx.level(), ParticleTypes.FLASH, center, 1, 0, 0);
        FormsFx.sound(ctx, center, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.0F, 0.8F);
        FormsFx.sound(ctx, center, SoundEvents.ILLUSIONER_CAST_SPELL, 0.7F, 1.4F);
    }

    /** Nada na mira: a marca se desfaz (avisa o jogador, se foi ele quem mirou) e o estágio termina no fim da mira. */
    private static void fizzle(SpellContext ctx, Vec3 limit, Vec3 aim) {
        Vec3 p = ctx.eyePos().add(aim.scale(2.0));
        FormsFx.send(ctx.level(), Vfx.smoke(), p, 10, 0.15, 0.02);
        FormsFx.sound(ctx, p, SoundEvents.FIRE_EXTINGUISH, 0.5F, 1.6F);
        ServerPlayer sp = ctx.casterPlayer();
        if (sp != null && !ctx.hasOrigin()) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.forms_a.mark_no_target")
                    .withStyle(ChatFormatting.GRAY), true);
        }
        ctx.end(limit, aim);
    }
}
