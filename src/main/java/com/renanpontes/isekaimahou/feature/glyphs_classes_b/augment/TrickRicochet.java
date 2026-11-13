package com.renanpontes.isekaimahou.feature.glyphs_classes_b.augment;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.event.SpellDamageEvent;
import com.renanpontes.isekaimahou.feature.glyphs_classes_b.GcbFx;
import com.renanpontes.isekaimahou.feature.glyphs_classes_b.GcbIds;
import com.renanpontes.isekaimahou.feature.glyphs_classes_b.GcbUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Aumento do Pistoleiro — <b>Ricochete Calculado</b> (o "trick shot" de faroeste): quando o glifo modificado fere um
 * inimigo, a FORMA inteira quica dele para o inimigo mais próximo ainda não atingido (até 14 blocos) e sai de novo de lá
 * — com +25% de poder a cada ricochete (o tiro "pega embalo"). 1 ricochete por cópia do glifo (máx. 3 por conjuração).
 * Diferente do Ricochete comum (que quica em paredes) e do Encadear (que perde força): aqui o tiro é refeito do ponto
 * do impacto e fica mais forte. Só em formas que saem de um ponto (projéteis, feixes, cones...); nunca na forma G.
 */
public final class TrickRicochet {
    public static final double RANGE = 14.0;
    public static final float GAIN = 1.25F;

    private static final class Shot {
        int bounces;
        final Set<UUID> hit = new HashSet<>();
    }

    /** Estado por conjuração (chave: stats da forma da raiz, um objeto por conjuração). */
    private static final Map<SpellStats, Shot> SHOTS = new WeakHashMap<>();

    public static void onSpellDamage(SpellDamageEvent.Post e) {
        if (!e.landed()) return;
        SpellContext ctx = e.ctx();
        int copies = GcbUtil.here(e.stats(), ctx.formStats(), GcbIds.TRICK_RICOCHET);
        if (copies <= 0 || ctx.caster() == null) return;
        if (ctx.spell().form().isUltimate() || !ctx.spell().form().stageable()) return;
        LivingEntity target = e.target();
        if (!(target.level() instanceof ServerLevel sl)) return;
        Shot shot = SHOTS.computeIfAbsent(ctx.rootSpell().formStats(), k -> new Shot());
        boolean first = shot.hit.add(target.getUUID());
        if (!first || shot.bounces >= Math.min(3, copies)) return;
        LivingEntity next = next(ctx, target, shot.hit);
        if (next == null || !budget(ctx.caster().getUUID(), sl.getGameTime())) return;
        shot.bounces++;
        shot.hit.add(next.getUUID());
        float power = ctx.powerMult() * GAIN;
        Vec3 from = GcbFx.center(target);
        Vec3 to = GcbFx.center(next);
        Vec3 dir = to.subtract(from).normalize();
        Vec3 origin = from.add(dir.scale(target.getBbWidth() * 0.5 + 0.7));
        // faísca e "ping" no ponto do quique
        sl.sendParticles(ParticleTypes.CRIT, from.x, from.y, from.z, 10, 0.15, 0.15, 0.15, 0.4);
        sl.sendParticles(GcbFx.dust(GcbFx.GUN, 1.0F), from.x, from.y, from.z, 6, 0.1, 0.1, 0.1, 0.0);
        GcbFx.line(sl, origin, to, GcbFx.dust(GcbFx.GUN, 0.5F));
        GcbFx.sound(sl, from, SoundEvents.ANVIL_LAND, 0.25F, 2.0F);
        GcbFx.sound(sl, from, SoundEvents.NOTE_BLOCK_BELL.value(), 0.5F, 1.6F + 0.15F * shot.bounces);
        SpellScheduler.schedule(2, () -> {
            if (!next.isAlive() || next.level() != sl || ctx.caster() == null || ctx.caster().isRemoved()) return;
            Vec3 aim = GcbFx.center(next).subtract(origin).normalize();
            SpellContext c = ctx.withOrigin(origin, aim);
            c.setPowerMult(power);
            c.spell().form().cast(c);
        });
    }

    /** Inimigo mais perto do alvo atingido, ainda não atingido por esta conjuração, com linha de visão a partir dele. */
    @Nullable
    private static LivingEntity next(SpellContext ctx, LivingEntity from, Set<UUID> hit) {
        LivingEntity best = null;
        double bestD = RANGE * RANGE;
        for (LivingEntity e : ctx.enemiesAround(GcbFx.center(from), RANGE)) {
            if (e == from || hit.contains(e.getUUID()) || !e.isAlive() || !from.hasLineOfSight(e)) continue;
            double d = e.distanceToSqr(from);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    /** Trava de segurança: no máximo 6 ricochetes por conjurador a cada segundo (salvo de estado perdido ao recarregar). */
    private static final Map<UUID, long[]> BUDGET = new java.util.HashMap<>();

    private static boolean budget(UUID caster, long now) {
        if (BUDGET.size() > 256) BUDGET.values().removeIf(v -> now - v[0] > 40);
        long[] b = BUDGET.computeIfAbsent(caster, k -> new long[] {now, 0});
        if (now - b[0] >= 20) {
            b[0] = now;
            b[1] = 0;
        }
        if (b[1] >= 6) return false;
        b[1]++;
        return true;
    }

    public static void forget(UUID id) {
        BUDGET.remove(id);
    }

    public static void clear() {
        SHOTS.clear();
        BUDGET.clear();
    }

    private TrickRicochet() {}
}
