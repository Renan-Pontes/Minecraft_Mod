package com.renanpontes.isekaimahou.feature.class_steelball.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_steelball.SbFx;
import com.renanpontes.isekaimahou.feature.class_steelball.SbFxNet;
import com.renanpontes.isekaimahou.feature.class_steelball.entity.SpinVortexEntity;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellCost;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Turbilhão Giratório (forma de origem, tier 3, Círculo 3): crava uma bola girando no chão mirado (até 24 blocos) que
 * vira um redemoinho de raio 3,5 (Expandir) por 4 s (Estender/Persistente). Puxa os inimigos em espiral e aplica os
 * efeitos 1×/s. Custo por cópia: pulsos moderados (1 + √pulsos).
 */
public class SpinVortexForm extends FormGlyph {
    public static final int BASE_LIFE = 80;
    public static final float BASE_RADIUS = 3.5F;
    public static final double RANGE = 24.0;

    public SpinVortexForm() {
        super(SbGlyphs.SPIN_VORTEX, 10);
        origin(MagicPath.STEELBALL);
        element(Element.WIND);
        tier(3);
        minCircle(3);
    }

    @Override
    public float copies(SpellStats stats, int circle) {
        return SpellCost.pulseCopies(stats.duration(BASE_LIFE), 20);
    }

    @Override
    public boolean stageable() { return true; }

    @Override
    public boolean isPersistent() { return true; }

    @Override
    public int baseCastTicks() { return 6; }

    @Override
    public int baseCooldown() { return 60; }

    @Override
    public float hintRange() { return (float) RANGE; }

    @Override
    public float hintRadius() { return BASE_RADIUS; }

    @Override
    public int hintDuration() { return BASE_LIFE; }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        SpellStats s = ctx.formStats();
        Vec3 at;
        if (ctx.hasOrigin()) {
            at = ctx.anchor() != null ? ctx.anchor() : ctx.eyePos();
            at = ground(level, at, caster);
        } else {
            if (caster == null) return;
            Vec3 eye = caster.getEyePosition();
            Vec3 end = eye.add(caster.getLookAngle().scale(RANGE));
            BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
            at = ground(level, hit.getType() == HitResult.Type.MISS ? end : hit.getLocation(), caster);
        }
        boolean golden = s.custom(SbGlyphs.GOLDEN) > 0;
        SpinVortexEntity v = SpinVortexEntity.create(ctx, at, s.radius(BASE_RADIUS), s.duration(BASE_LIFE), golden);
        level.addFreshEntity(v);
        SbFxNet.at(level, SbFxNet.VORTEX, at, Vec3.ZERO, s.radius(BASE_RADIUS), golden ? SbFx.GOLD : SbFx.STEEL, -1);
        SbFx.sound(level, at, SoundEvents.ANVIL_LAND, 0.5F, 1.4F);
        SbFx.sound(level, at, SoundEvents.TRIDENT_RIPTIDE_2.value(), 0.8F, 1.2F);
    }

    /** Desce até o chão (no máximo 8 blocos). */
    private static Vec3 ground(ServerLevel level, Vec3 from, LivingEntity caster) {
        Vec3 down = from.subtract(0, 8, 0);
        BlockHitResult hit = level.clip(new ClipContext(from.add(0, 0.5, 0), down, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                caster == null ? CollisionContext.empty() : CollisionContext.of(caster)));
        return hit.getType() == HitResult.Type.MISS ? from : hit.getLocation();
    }
}
