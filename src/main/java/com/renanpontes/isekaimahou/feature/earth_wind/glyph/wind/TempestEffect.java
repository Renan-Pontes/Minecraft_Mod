package com.renanpontes.isekaimahou.feature.earth_wind.glyph.wind;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.earth_wind.EwUtil;
import com.renanpontes.isekaimahou.feature.earth_wind.entity.TempestStorm;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Tempestade (lendario): invoca uma tempestade local de 15 s (raio 10) sobre o ponto — chuva que encharca,
 * rajadas que jogam os inimigos de um lado para o outro e raios que caem neles. Uma por conjurador.
 */
public class TempestEffect extends EffectGlyph {
    public TempestEffect() {
        super("tempest", 75);
        element(Element.WIND);
        legendary();
        minCircle(8);
    }

    @Override
    public boolean wantsBlocks() {
        return true;
    }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        spawn(ctx, target.position(), stats);
    }

    @Override
    public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
        spawn(ctx, hit.getLocation(), stats);
    }

    private static void spawn(SpellContext ctx, Vec3 at, SpellStats stats) {
        if (!EwUtil.once(ctx, "tempest")) return;
        BlockPos ground = EwUtil.groundBelow(ctx.level(), BlockPos.containing(at).above(1), 10);
        Vec3 pos = ground != null ? new Vec3(at.x, ground.getY() + 1.0, at.z) : at;
        TempestStorm.spawn(ctx, pos, stats.radius(10.0F), stats.duration(300), stats.power());
        // v1.3: selo no céu, vento em espiral no chão durante a tempestade inteira e um primeiro raio de abertura
        com.renanpontes.isekaimahou.core.ElementFx.skyStrike(ctx.level(), pos, Element.LIGHTNING, 0x9FE8FF, 2.5F, 5, 16);
        com.renanpontes.isekaimahou.core.ElementFx.area(ctx.level(), pos, Element.WIND, ctx.color(), stats.radius(10.0F), 4,
                Math.min(400, stats.duration(300)));
    }
}
