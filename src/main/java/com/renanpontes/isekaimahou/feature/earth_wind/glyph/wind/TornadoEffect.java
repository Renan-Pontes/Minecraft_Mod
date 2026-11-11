package com.renanpontes.isekaimahou.feature.earth_wind.glyph.wind;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.earth_wind.EwUtil;
import com.renanpontes.isekaimahou.feature.earth_wind.entity.WindTornado;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Tornado: cria um tornado no ponto atingido que dura 10 s, persegue o inimigo mais proximo e suga, gira e
 * levanta quem pega. Cada conjurador mantem ate 2 tornados.
 */
public class TornadoEffect extends EffectGlyph {
    public TornadoEffect() {
        super("tornado", 30);
        element(Element.WIND);
        tier(4);
        minCircle(3);
    }

    @Override
    public boolean wantsBlocks() {
        return true;
    }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        Vec3 at = target == ctx.caster() ? target.position().add(EwUtil.horizontal(ctx.look()).normalize().scale(4)) : target.position();
        spawn(ctx, at, stats);
    }

    @Override
    public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
        spawn(ctx, Vec3.atBottomCenterOf(hit.getBlockPos().relative(hit.getDirection())), stats);
    }

    private static void spawn(SpellContext ctx, Vec3 at, SpellStats stats) {
        if (!EwUtil.once(ctx, "tornado")) return;
        BlockPos ground = EwUtil.groundBelow(ctx.level(), BlockPos.containing(at).above(1), 8);
        Vec3 pos = ground != null ? new Vec3(at.x, ground.getY() + 1.0, at.z) : at;
        WindTornado.spawn(ctx, pos, stats.radius(2.5F), stats.duration(200), stats.power());
    }
}
