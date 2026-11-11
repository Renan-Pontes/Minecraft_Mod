package com.renanpontes.isekaimahou.feature.earth_wind.glyph.wind;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.earth_wind.EwFx;
import com.renanpontes.isekaimahou.feature.earth_wind.EwTasks;
import com.renanpontes.isekaimahou.feature.earth_wind.EwUtil;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Redemoinho: por 3 s um vortice no ponto do alvo puxa os inimigos em volta (raio 5) e os faz girar,
 * com cortes de vento a cada meio segundo.
 */
public class WhirlwindEffect extends EffectGlyph {
    public WhirlwindEffect() {
        super("whirlwind", 16);
        element(Element.WIND);
        tier(3);
    }

    /** Efeito de area num ponto: um projetil que acerta o chao tambem dispara. */
    @Override
    public boolean wantsBlocks() {
        return true;
    }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        vortex(ctx, target.position(), stats);
    }

    @Override
    public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
        vortex(ctx, hit.getLocation(), stats);
    }

    private static void vortex(SpellContext ctx, Vec3 center, SpellStats stats) {
        if (!EwUtil.once(ctx, "whirlwind")) return;
        ServerLevel level = ctx.level();
        float radius = stats.radius(5.0F);
        int ticks = stats.duration(60);
        EwFx.sound(level, center, SoundEvents.BREEZE_WHIRL, 1.4F, 0.8F);
        EwTasks.repeat(ticks, age -> {
            for (LivingEntity e : ctx.enemiesAround(center, radius)) {
                Vec3 rel = EwUtil.horizontal(e.position().subtract(center));
                double dist = rel.length();
                Vec3 radial = dist > 1.0E-3 ? rel.scale(1 / dist) : new Vec3(1, 0, 0);
                Vec3 tangent = new Vec3(-radial.z, 0, radial.x);
                double k = EwUtil.knockbackFactor(e);
                Vec3 v = tangent.scale(0.3).add(radial.scale(dist > 1.2 ? -0.14 : 0.0)).scale(k);
                double up = e.getY() < center.y + 1.5 ? 0.06 : 0.0;
                EwUtil.setVelocity(e, new Vec3(v.x, Math.max(e.getDeltaMovement().y, up), v.z));
                e.resetFallDistance();
                EwUtil.holdAloft(e);
                if (age % 10 == 0) ctx.damage(e, 1.5F, stats, null);
            }
            if (age % 2 == 0) EwFx.windSpiral(level, center, radius * 0.8, 2.5, 14, age * 0.5);
            if (age % 20 == 10) EwFx.sound(level, center, SoundEvents.BREEZE_WHIRL, 1.0F, 1.0F);
            return true;
        });
    }
}
