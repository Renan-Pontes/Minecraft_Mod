package com.renanpontes.isekaimahou.feature.fusions_a.block;

import java.util.function.Supplier;

import com.renanpontes.isekaimahou.feature.fusions_a.FaTempBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;

/**
 * Base dos blocos mágicos temporários. Quem devolve o bloco original é o {@link FaTempBlocks}; este bloco só tem
 * uma trava de segurança: se por algum motivo ninguém mais o estiver rastreando (ex.: comando /setblock), ele se
 * desfaz sozinho no {@code fallback} em vez de ficar para sempre no mundo.
 */
public class FaTempBlock extends Block {
    private static final int CHECK_TICKS = 200;
    private final Supplier<BlockState> fallback;

    public FaTempBlock(Properties props, Supplier<BlockState> fallback) {
        super(props);
        this.fallback = fallback;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide && !oldState.is(this)) level.scheduleTick(pos, this, CHECK_TICKS);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (FaTempBlocks.isTracked(level, pos)) {
            level.scheduleTick(pos, this, CHECK_TICKS);
        } else {
            level.setBlock(pos, fallback.get(), Block.UPDATE_ALL);
        }
    }

    /** Chamado quando o tempo acaba e o bloco original volta (sons/partículas extras). */
    public void onExpire(ServerLevel level, BlockPos pos) {}

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }
}
