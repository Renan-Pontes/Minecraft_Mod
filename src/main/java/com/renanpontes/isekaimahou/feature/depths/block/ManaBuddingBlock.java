package com.renanpontes.isekaimahou.feature.depths.block;

import com.renanpontes.isekaimahou.feature.depths.DepthsRegistries;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.AmethystBlock;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

/**
 * Cristal de Mana Germinante: como a ametista germinante, faz brotar Brotos de Mana nas faces livres, que viram
 * Aglomerados de Mana (a fonte renovável de Cristais de Mana). Cresce devagar (1 em 5 ticks aleatórios).
 */
public class ManaBuddingBlock extends AmethystBlock {
    private static final Direction[] DIRS = Direction.values();

    public ManaBuddingBlock(Properties props) {
        super(props);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(5) != 0) return;
        Direction dir = DIRS[random.nextInt(DIRS.length)];
        BlockPos at = pos.relative(dir);
        BlockState there = level.getBlockState(at);
        Block next = null;
        if (canGrowInto(there)) {
            next = DepthsRegistries.MANA_BUD.get();
        } else if (there.is(DepthsRegistries.MANA_BUD.get()) && there.getValue(AmethystClusterBlock.FACING) == dir) {
            next = DepthsRegistries.MANA_CLUSTER.get();
        }
        if (next == null) return;
        BlockState grown = next.defaultBlockState().setValue(AmethystClusterBlock.FACING, dir)
                .setValue(AmethystClusterBlock.WATERLOGGED, there.getFluidState().getType() == Fluids.WATER);
        level.setBlockAndUpdate(at, grown);
    }

    private static boolean canGrowInto(BlockState state) {
        return state.isAir() || state.is(Blocks.WATER) && state.getFluidState().getAmount() == 8;
    }
}
