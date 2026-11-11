package com.renanpontes.isekaimahou.feature.earth_wind.glyph.earth;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.earth_wind.EwFx;
import com.renanpontes.isekaimahou.feature.earth_wind.EwRegistries;
import com.renanpontes.isekaimahou.feature.earth_wind.EwUtil;
import com.renanpontes.isekaimahou.feature.earth_wind.TempBlocks;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Areia Movedica: o chao em volta do alvo vira areia movedica por 8 s. Quem esta em cima afunda devagar,
 * fica preso e, com a cabeca enterrada, perde o ar. O chao volta ao normal depois (e "cospe" quem ficou dentro).
 */
public class QuicksandEffect extends EffectGlyph {
    public QuicksandEffect() {
        super("quicksand", 18);
        element(Element.EARTH);
        tier(3);
    }

    @Override
    public boolean wantsBlocks() {
        return true;
    }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        if (target instanceof LivingEntity le) ctx.status(le, MobEffects.MOVEMENT_SLOWDOWN, 100, 1, stats);
        BlockPos ground = EwUtil.groundUnder(target, 3);
        if (ground != null) sink(ctx, ground, stats);
        EwUtil.push(target, Vec3.ZERO, -0.3);
    }

    @Override
    public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
        BlockPos ground = hit.getDirection() == Direction.UP ? hit.getBlockPos()
                : EwUtil.groundBelow(ctx.level(), hit.getBlockPos().relative(hit.getDirection()), 3);
        if (ground != null) sink(ctx, ground, stats);
    }

    private static void sink(SpellContext ctx, BlockPos ground, SpellStats stats) {
        if (!EwUtil.once(ctx, "quicksand@" + ground.asLong())) return;
        ServerLevel level = ctx.level();
        float radius = stats.radius(1.6F);
        int r = (int) Math.ceil(radius);
        int depth = 2 + (stats.amplify > 0 ? 1 : 0);
        int ticks = stats.duration(160);
        BlockState sand = EwRegistries.EARTHEN_QUICKSAND.get().defaultBlockState();
        int converted = 0;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > radius * radius + 0.5) continue;
                // cada coluna acha o proprio topo (terreno irregular)
                BlockPos top = EwUtil.groundBelow(level, ground.offset(dx, 2, dz), 5);
                if (top == null || Math.abs(top.getY() - ground.getY()) > 2) continue;
                int column = 0;
                for (int d = 0; d < depth; d++) {
                    BlockPos pos = top.below(d);
                    if (!canConvert(level, pos) || !EwUtil.canBreak(ctx, pos, level.getBlockState(pos))) break;
                    if (TempBlocks.convert(level, pos, sand, ticks)) column++;
                }
                converted += column;
                // grama/flores em cima ficariam boiando sobre a areia: somem junto e voltam depois
                BlockPos above = top.above();
                BlockState aboveState = level.getBlockState(above);
                if (column > 0 && !aboveState.isAir() && aboveState.canBeReplaced() && aboveState.getFluidState().isEmpty()
                        && !aboveState.hasBlockEntity()) {
                    TempBlocks.carve(level, above, ticks);
                }
            }
        }
        if (converted > 0) {
            Vec3 at = Vec3.atCenterOf(ground.above());
            EwFx.debris(level, at, Blocks.SAND.defaultBlockState(), 40, radius * 0.6);
            EwFx.dustPillar(level, at, Blocks.SAND.defaultBlockState(), 20);
            EwFx.sound(level, at, SoundEvents.SAND_BREAK, 1.4F, 0.6F);
            EwFx.sound(level, at, SoundEvents.GRAVEL_BREAK, 1.0F, 0.5F);
        }
    }

    private static boolean canConvert(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (s.isAir() || s.hasBlockEntity() || !s.getFluidState().isEmpty()) return false;
        if (s.is(EwRegistries.EARTHEN_QUICKSAND.get())) return false;
        float hardness = s.getDestroySpeed(level, pos);
        return hardness >= 0 && hardness <= 3.0F && s.isCollisionShapeFullBlock(level, pos);
    }
}
