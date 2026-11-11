package com.renanpontes.isekaimahou.feature.earth_wind.glyph.earth;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.earth_wind.EwFx;
import com.renanpontes.isekaimahou.feature.earth_wind.EwUtil;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Escavar: quebra uma area 3x3 de terra/pedra (Expandir aumenta a area, Amplificar a profundidade).
 * Os blocos caem como itens; Fortuna e Toque Suave funcionam. Num alvo, abre um buraco sob os pes dele.
 */
public class ExcavateEffect extends EffectGlyph {
    /** Dureza maxima (pedra 1.5, ardosia 3, minerios 3; obsidiana 50 fica de fora). */
    private static final float MAX_HARDNESS = 5.0F;

    public ExcavateEffect() {
        super("excavate", 8);
        element(Element.EARTH);
        tier(2);
    }

    @Override
    public boolean wantsBlocks() {
        return true;
    }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        BlockPos ground = EwUtil.groundUnder(target, 3);
        if (ground != null) dig(ctx, ground, Direction.UP, stats);
    }

    @Override
    public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
        dig(ctx, hit.getBlockPos(), hit.getDirection(), stats);
    }

    private static void dig(SpellContext ctx, BlockPos center, Direction face, SpellStats stats) {
        if (!EwUtil.once(ctx, "excavate@" + center.asLong())) return;
        ServerLevel level = ctx.level();
        int r = Math.max(0, Math.min(3, 1 + stats.aoe - (stats.concentrate ? 1 : 0)));
        int depth = Math.max(1, Math.min(4, 1 + stats.amplify));
        Direction into = face.getOpposite();
        Direction u;
        Direction v;
        switch (face.getAxis()) {
            case X -> { u = Direction.UP; v = Direction.SOUTH; }
            case Y -> { u = Direction.EAST; v = Direction.SOUTH; }
            default -> { u = Direction.UP; v = Direction.EAST; }
        }
        ItemStack tool = EwUtil.miningTool(level, stats);
        int broken = 0;
        for (int d = 0; d < depth; d++) {
            for (int a = -r; a <= r; a++) {
                for (int b = -r; b <= r; b++) {
                    BlockPos pos = center.relative(into, d).relative(u, a).relative(v, b);
                    BlockState state = level.getBlockState(pos);
                    if (!canDig(level, pos, state) || !EwUtil.canBreak(ctx, pos, state)) continue;
                    Block.dropResources(state, level, pos, null, ctx.caster(), tool);
                    level.destroyBlock(pos, false, ctx.caster());
                    broken++;
                }
            }
        }
        if (broken > 0) {
            Vec3 at = Vec3.atCenterOf(center);
            EwFx.sound(level, at, SoundEvents.ROOTED_DIRT_BREAK, 1.2F, 0.7F);
            EwFx.sound(level, at, SoundEvents.STONE_BREAK, 1.0F, 0.6F);
            EwFx.debris(level, at, EwFx.groundState(level, center.relative(into, depth)), 18, 0.8);
        }
    }

    private static boolean canDig(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir() || state.hasBlockEntity() || !state.getFluidState().isEmpty()) return false;
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0 || hardness > MAX_HARDNESS) return false;
        return state.is(BlockTags.MINEABLE_WITH_PICKAXE) || state.is(BlockTags.MINEABLE_WITH_SHOVEL);
    }
}
