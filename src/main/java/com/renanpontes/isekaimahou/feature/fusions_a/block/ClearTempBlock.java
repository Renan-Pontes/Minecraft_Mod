package com.renanpontes.isekaimahou.feature.fusions_a.block;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Bloco temporário transparente (Gelo da ponte/estátua, Prisão de Vidro): esconde as faces entre blocos iguais,
 * deixa passar luz e mostra o que está preso dentro.
 */
public class ClearTempBlock extends FaTempBlock {
    private final Supplier<ParticleOptions> expireParticle;

    public ClearTempBlock(Properties props, Supplier<BlockState> fallback, Supplier<ParticleOptions> expireParticle) {
        super(props, fallback);
        this.expireParticle = expireParticle;
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacent, Direction side) {
        return adjacent.is(this) || super.skipRendering(state, adjacent, side);
    }

    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.empty();
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    public void onExpire(ServerLevel level, BlockPos pos) {
        level.sendParticles(expireParticle.get(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.02);
    }
}
