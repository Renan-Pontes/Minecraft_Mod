package com.renanpontes.isekaimahou.feature.class_smith.glyph;

import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_smith.SmithFx;
import com.renanpontes.isekaimahou.feature.class_smith.entity.StarHammerEntity;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Forma G "Forja Estelar" (ultimate do Ferreiro): um martelo colossal de aço estelar surge no céu sobre o ponto mirado
 * (até 32 blocos) e bate TRÊS vezes, cada martelada aplicando os efeitos numa área de 5 blocos (Expandir aumenta);
 * a terceira bate com 150% do poder. Ondas de choque, brasas, tremor de câmera — e o cenário fica intacto.
 * Custo por cópia: 3 marteladas.
 */
public class StarforgeForm extends FormGlyph {
    public static final float RADIUS = 5.0F;
    public static final double RANGE = 32.0;

    public StarforgeForm() {
        super("smith_starforge", 75);
        origin(MagicPath.SMITH);
        tier(4);
        minCircle(5);
    }

    @Override
    public boolean isUltimate() { return true; }

    @Override
    public int baseCastTicks() { return 30; }

    @Override
    public int baseCooldown() { return 600; }

    @Override
    public float copies(SpellStats stats, int circle) {
        return StarHammerEntity.SLAMS;
    }

    @Override
    public float hintRange() { return (float) RANGE; }

    @Override
    public float hintRadius() { return RADIUS; }

    @Override
    public int hintDuration() { return StarHammerEntity.LIFE; }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        ServerLevel sl = ctx.level();
        if (caster == null) return;
        Vec3 point = aim(ctx, caster, sl);
        Vec3 ground = SmithFx.ground(sl, point.add(0, 1.0, 0), 12);
        float radius = ctx.formStats().radius(RADIUS);
        int color = ctx.recipe().color() >= 0 ? ctx.recipe().color() : SmithFx.STAR;
        sl.addFreshEntity(StarHammerEntity.create(sl, ctx, ground, radius, color));
        SmithFx.sound(sl, ground, SoundEvents.BEACON_ACTIVATE, 1.5F, 0.6F);
        SmithFx.sound(sl, ground, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.8F, 1.4F);
        SmithFx.ring(sl, ground.add(0, 0.1, 0), radius, SmithFx.dust(color, 1.4F), 48);
    }

    /** Ponto mirado: alvo do mob; senão o primeiro bloco/criatura na linha de visão (até 32 blocos). */
    private static Vec3 aim(SpellContext ctx, LivingEntity caster, ServerLevel sl) {
        if (!ctx.hasOrigin() && caster instanceof Mob mob && mob.getTarget() != null) return mob.getTarget().position();
        if (ctx.hasOrigin() && ctx.anchor() != null) return ctx.anchor();
        Vec3 eye = ctx.eyePos();
        Vec3 end = eye.add(ctx.look().scale(RANGE));
        BlockHitResult block = sl.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        Vec3 stop = block.getType() == HitResult.Type.BLOCK ? block.getLocation() : end;
        EntityHitResult ent = ProjectileUtil.getEntityHitResult(sl, caster, eye, stop,
                new AABB(eye, stop).inflate(1.0), e -> e instanceof LivingEntity && e.isAlive() && !ctx.isAlly(e) && e != caster, 0.3F);
        if (ent != null) {
            Entity e = ent.getEntity();
            return e.position();
        }
        return stop;
    }
}
