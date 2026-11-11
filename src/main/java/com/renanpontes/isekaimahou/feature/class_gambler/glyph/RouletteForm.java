package com.renanpontes.isekaimahou.feature.class_gambler.glyph;

import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_gambler.GbFx;
import com.renanpontes.isekaimahou.feature.class_gambler.entity.RouletteWheelEntity;
import com.renanpontes.isekaimahou.feature.class_gambler.luck.LuckService;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Roleta: uma roleta mágica aparece no chão onde você mira (até 24 blocos; num estágio, no ponto do estágio) e gira
 * por 2,5 s com o aviso da área no chão. Vermelho ×1,25; Preto ×0,85 duas vezes; Zero verde ×3. Raio 4,5
 * (Expandir/Concentrar valem). Não usa o Dividir; em média conta 1,5 cópia (o Preto acerta duas vezes).
 */
public class RouletteForm extends FormGlyph {
    public static final float BASE_RADIUS = 4.5F;
    private static final double RANGE = 24;

    public RouletteForm() {
        super("roulette_wheel", 9);
        origin(MagicPath.GAMBLER);
        tier(3);
        minCircle(2);
    }

    @Override
    public boolean stageable() {
        return true;
    }

    @Override
    public int baseCooldown() {
        return 40;
    }

    @Override
    public float copies(SpellStats stats, int circle) {
        return 1.5F;
    }

    @Override
    public float hintRadius() {
        return BASE_RADIUS;
    }

    @Override
    public float hintRange() {
        return (float) RANGE;
    }

    @Override
    public int hintDuration() {
        return RouletteWheelEntity.SPIN_TICKS;
    }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return;
        ServerLevel level = ctx.level();
        Vec3 center;
        if (ctx.hasOrigin()) {
            center = ctx.anchor() != null ? ctx.anchor() : ctx.eyePos();
        } else if (caster instanceof Mob mob && mob.getTarget() != null) {
            center = mob.getTarget().position();
        } else {
            Vec3 eye = ctx.eyePos();
            Vec3 end = eye.add(ctx.look().scale(RANGE));
            BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
            center = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
        }
        center = ground(level, center);
        float radius = ctx.formStats().radius(BASE_RADIUS);
        level.addFreshEntity(RouletteWheelEntity.create(ctx, center, radius, LuckService.luck(caster)));
        GbFx.sound(level, center, SoundEvents.ENCHANTMENT_TABLE_USE, 0.9F, 1.3F);
        GbFx.groundRing(level, center, radius, GbFx.GOLD, 32);
    }

    /** Desce até o chão (até 8 blocos) para a roleta não flutuar. */
    static Vec3 ground(ServerLevel level, Vec3 p) {
        BlockPos pos = BlockPos.containing(p);
        // mirou numa parede: sobe até achar ar
        for (int i = 0; i < 3 && !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty(); i++) pos = pos.above();
        for (int i = 0; i < 8; i++) {
            if (!level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty()) {
                return new Vec3(p.x, pos.getY(), p.z);
            }
            pos = pos.below();
        }
        return p;
    }
}
