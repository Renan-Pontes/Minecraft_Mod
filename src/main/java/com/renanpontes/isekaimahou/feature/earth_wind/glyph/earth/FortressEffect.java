package com.renanpontes.isekaimahou.feature.earth_wind.glyph.earth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 * Fortaleza: um domo de pedra conjurada se fecha em volta do alvo (abrigo em Si Mesmo, prisao num inimigo).
 * Raio 3.5 (Expandir aumenta), dura 15 s e depois desmorona de volta ao normal.
 */
public class FortressEffect extends EffectGlyph {
    public FortressEffect() {
        super("fortress", 28);
        element(Element.EARTH);
        tier(4);
        minCircle(3);
    }

    @Override
    public boolean wantsBlocks() {
        return true;
    }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        BlockPos ground = EwUtil.groundUnder(target, 4);
        BlockPos floor = ground != null ? ground.above() : target.blockPosition();
        dome(ctx, floor, stats);
    }

    @Override
    public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
        BlockPos ground = hit.getDirection() == Direction.UP ? hit.getBlockPos()
                : EwUtil.groundBelow(ctx.level(), hit.getBlockPos().relative(hit.getDirection()), 4);
        if (ground != null) dome(ctx, ground.above(), stats);
    }

    private static void dome(SpellContext ctx, BlockPos floor, SpellStats stats) {
        if (!EwUtil.once(ctx, "fortress")) return;
        ServerLevel level = ctx.level();
        double radius = Math.min(7.0, stats.radius(3.5F));
        int r = (int) Math.ceil(radius);
        double cx = floor.getX() + 0.5;
        double cz = floor.getZ() + 0.5;
        // colunas (x,z) com as posicoes da casca do hemisferio, de baixo para cima
        Map<Long, List<BlockPos>> byColumn = new HashMap<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = 0; dy <= r; dy++) {
                    double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (d > radius || d <= radius - 1.2) continue;
                    BlockPos pos = floor.offset(dx, dy, dz);
                    byColumn.computeIfAbsent(BlockPos.asLong(pos.getX(), 0, pos.getZ()), k -> new ArrayList<>()).add(pos);
                }
            }
        }
        List<List<BlockPos>> columns = new ArrayList<>();
        for (List<BlockPos> col : byColumn.values()) {
            col.sort((a, b) -> Integer.compare(a.getY(), b.getY()));
            // terreno mais baixo na borda: estende a parede ate o chao para nao ficar fresta
            BlockPos lowest = col.get(0);
            if (lowest.getY() == floor.getY()) {
                for (int i = 1; i <= 3; i++) {
                    BlockPos below = lowest.below(i);
                    if (!level.getBlockState(below).canBeReplaced()) break;
                    col.add(0, below);
                }
            }
            columns.add(col);
        }
        StoneWorks.build(ctx, columns, 2, stats.duration(300), false, false);
        Vec3 c = new Vec3(cx, floor.getY(), cz);
        EwFx.groundRing(level, c, radius, 28);
        EwFx.sound(level, c, SoundEvents.RAVAGER_ROAR, 0.6F, 0.4F);
        EwFx.sound(level, c, SoundEvents.ROOTED_DIRT_BREAK, 1.5F, 0.4F);
        EwUtil.shake(level, c, radius * 3, 0.5F, 20);
    }
}
