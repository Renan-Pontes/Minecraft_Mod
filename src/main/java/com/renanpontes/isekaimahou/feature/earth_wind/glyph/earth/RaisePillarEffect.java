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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Erguer Pilar: uma coluna de pedra conjurada brota do chao e levanta quem estiver em cima.
 * Altura 3 (+1 por Amplificar, ate 8); dura 10 s (Estender) e depois volta para o chao.
 */
public class RaisePillarEffect extends EffectGlyph {
    public RaisePillarEffect() {
        super("raise_pillar", 8);
        element(Element.EARTH);
        tier(1);
    }

    @Override
    public boolean wantsBlocks() {
        return true;
    }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        BlockPos ground = EwUtil.groundUnder(target, 4);
        if (ground == null) {
            EwFx.sound(ctx.level(), target.position(), net.minecraft.sounds.SoundEvents.STONE_HIT, 0.6F, 0.5F);
            return;
        }
        raise(ctx, ground, stats);
    }

    @Override
    public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
        BlockPos ground = hit.getDirection() == Direction.UP ? hit.getBlockPos()
                : EwUtil.groundBelow(ctx.level(), hit.getBlockPos().relative(hit.getDirection()), 4);
        if (ground != null) raise(ctx, ground, stats);
    }

    private void raise(SpellContext ctx, BlockPos ground, SpellStats stats) {
        // a forma Si Mesmo aplica no corpo e no bloco embaixo: um pilar so por coluna
        if (!EwUtil.once(ctx, "pillar@" + ground.getX() + "," + ground.getZ())) return;
        int height = Math.max(2, Math.min(8, 3 + stats.amplify));
        List<BlockPos> column = new ArrayList<>();
        for (int i = 1; i <= height; i++) column.add(ground.above(i));
        List<List<BlockPos>> columns = new ArrayList<>();
        columns.add(column);
        // pilar largo (Expandir area): colunas vizinhas um pouco mais baixas
        if (stats.aoe > 0) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                List<BlockPos> side = new ArrayList<>();
                BlockPos base = EwUtil.groundBelow(ctx.level(), ground.relative(d).above(2), 4);
                if (base == null) continue;
                for (int i = 1; i <= height - 1; i++) side.add(base.above(i));
                columns.add(side);
            }
        }
        StoneWorks.build(ctx, columns, 2, stats.duration(200), true, true);
        Vec3 at = Vec3.atCenterOf(ground.above());
        EwFx.dustPillar(ctx.level(), at, EwFx.groundState(ctx.level(), ground), 16);
    }
}
