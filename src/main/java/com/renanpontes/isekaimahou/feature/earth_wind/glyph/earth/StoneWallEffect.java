package com.renanpontes.isekaimahou.feature.earth_wind.glyph.earth;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.earth_wind.EwFx;
import com.renanpontes.isekaimahou.feature.earth_wind.EwUtil;
import com.renanpontes.isekaimahou.feature.earth_wind.glyph.StoneWorks;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Muralha de Pedra: ergue uma muralha temporaria (5 de largura x 3 de altura) de frente para o conjurador.
 * Num inimigo, a muralha sobe entre ele e voce; em Si Mesmo, na sua frente. Volta ao normal em 12 s.
 */
public class StoneWallEffect extends EffectGlyph {
    public StoneWallEffect() {
        super("stone_wall", 18);
        element(Element.EARTH);
        tier(3);
    }

    @Override
    public boolean wantsBlocks() {
        return true;
    }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        if (!EwUtil.once(ctx, "wall")) return;
        Vec3 facing;
        Vec3 base;
        if (target == ctx.caster() || ctx.caster() == null) {
            facing = EwUtil.horizontal(ctx.look());
            base = target.position().add(facing.normalize().scale(2.5));
        } else {
            facing = EwUtil.horizontal(target.position().subtract(ctx.caster().position()));
            Vec3 back = facing.lengthSqr() > 1.0E-4 ? facing.normalize().scale(-1.8) : Vec3.ZERO;
            base = target.position().add(back);
        }
        build(ctx, base, facing, stats);
    }

    @Override
    public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
        if (!EwUtil.once(ctx, "wall")) return;
        Vec3 base = Vec3.atBottomCenterOf(hit.getBlockPos().relative(hit.getDirection()));
        build(ctx, base, EwUtil.horizontal(ctx.look()), stats);
    }

    private static void build(SpellContext ctx, Vec3 base, Vec3 facing, SpellStats stats) {
        ServerLevel level = ctx.level();
        // alinha a muralha aos eixos para ficar reta
        Direction front = facing.lengthSqr() < 1.0E-4 ? Direction.NORTH : Direction.getNearest(facing.x, 0, facing.z);
        Direction side = front.getClockWise();
        int half = Math.max(1, Math.round(stats.radius(2.0F)));
        int height = Math.max(2, Math.min(6, 3 + stats.amplify));
        BlockPos center = BlockPos.containing(base);
        List<List<BlockPos>> columns = new ArrayList<>();
        for (int i = -half; i <= half; i++) {
            BlockPos col = center.relative(side, i);
            BlockPos ground = EwUtil.groundBelow(level, col.above(2), 6);
            if (ground == null) continue;
            List<BlockPos> column = new ArrayList<>();
            for (int h = 1; h <= height; h++) column.add(ground.above(h));
            columns.add(column);
            if (Math.abs(i) % 2 == 0) EwFx.dustPillar(level, Vec3.atBottomCenterOf(ground.above()), EwFx.groundState(level, ground), 4);
        }
        StoneWorks.build(ctx, columns, 2, stats.duration(240), true, true);
        EwFx.sound(level, base, SoundEvents.RAVAGER_STEP, 1.2F, 0.5F);
        EwUtil.shake(level, base, 10, 0.3F, 10);
    }
}
