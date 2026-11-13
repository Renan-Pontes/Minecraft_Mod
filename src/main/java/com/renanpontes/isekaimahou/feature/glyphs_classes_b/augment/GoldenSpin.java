package com.renanpontes.isekaimahou.feature.glyphs_classes_b.augment;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.event.SpellDamageEvent;
import com.renanpontes.isekaimahou.feature.class_steelball.SbProgress;
import com.renanpontes.isekaimahou.feature.class_steelball.SbSpin;
import com.renanpontes.isekaimahou.feature.glyphs_classes_b.GcbFx;
import com.renanpontes.isekaimahou.feature.glyphs_classes_b.GcbIds;
import com.renanpontes.isekaimahou.feature.glyphs_classes_b.GcbUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Aumento do Steel Ball — <b>Rotação Dourada</b>: o giro na proporção áurea entra no corpo do alvo e CRESCE. Depois do
 * golpe, o alvo fica Girado e sofre 4 pulsos de rotação (um a cada 0,5 s), cada um 1,618× mais forte que o anterior:
 * 6% → 10% → 16% → 26% do dano do golpe (+58% no total). Quem é Steel Ball (a técnica é da família Zeppeli) ganha um
 * 5º pulso de 42%. Um giro por alvo (um golpe novo reinicia a espiral, não soma). O dano dos pulsos não dispara
 * aumentos de novo.
 */
public final class GoldenSpin {
    public static final float[] PULSES = {0.06F, 0.10F, 0.16F, 0.26F, 0.42F};
    public static final int INTERVAL = 10;

    /** Espiral ativa por alvo (um número que muda a cada golpe novo: o pulso de uma espiral antiga para). */
    private static final Map<UUID, Integer> ACTIVE = new HashMap<>();
    private static int serial;

    public static void onSpellDamage(SpellDamageEvent.Post e) {
        if (!e.landed() || e.killed() || e.dealt() <= 0) return;
        if (GcbUtil.here(e.stats(), e.ctx().formStats(), GcbIds.GOLDEN_SPIN) <= 0) return;
        SpellContext ctx = e.ctx();
        LivingEntity target = e.target();
        if (ctx.caster() == null || !target.isAlive() || !(target.level() instanceof ServerLevel sl)) return;
        if (ACTIVE.size() > 256 && !ACTIVE.containsKey(target.getUUID())) return;
        int id = ++serial;
        ACTIVE.put(target.getUUID(), id);
        float base = e.dealt();
        int pulses = SbProgress.isSteelBall(ctx.caster()) ? 5 : 4;
        SbSpin.apply(target, ctx.caster(), 0, INTERVAL * (pulses + 1));
        GcbFx.goldenSpiral(sl, GcbFx.center(target), 1.0F, GcbFx.GOLD);
        for (int i = 0; i < pulses; i++) {
            int n = i;
            SpellScheduler.schedule(INTERVAL * (i + 1), () -> pulse(sl, ctx, target, id, base * PULSES[n], n, pulses));
        }
    }

    private static void pulse(ServerLevel sl, SpellContext ctx, LivingEntity target, int id, float amount, int n, int total) {
        Integer cur = ACTIVE.get(target.getUUID());
        if (cur == null || cur != id) return;
        if (!target.isAlive() || target.level() != sl || ctx.caster() == null || ctx.caster().isRemoved()) {
            ACTIVE.remove(target.getUUID());
            return;
        }
        if (n == total - 1) ACTIVE.remove(target.getUUID());
        Vec3 c = GcbFx.center(target);
        GcbFx.goldenSpiral(sl, c, 1.0F + n * 0.6F, GcbFx.GOLD);
        GcbFx.sound(sl, c, SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F, 0.8F + 0.18F * n);
        if (n == total - 1) GcbFx.sound(sl, c, SoundEvents.TRIDENT_HIT_GROUND, 0.7F, 1.4F);
        target.invulnerableTime = 0;
        target.hurt(ctx.damageSource(null), Math.max(0.5F, amount));
    }

    public static void forget(UUID entity) {
        ACTIVE.remove(entity);
    }

    public static void clear() {
        ACTIVE.clear();
    }

    private GoldenSpin() {}
}
