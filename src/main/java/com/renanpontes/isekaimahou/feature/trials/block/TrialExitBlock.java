package com.renanpontes.isekaimahou.feature.trials.block;

import com.mojang.serialization.MapCodec;
import com.renanpontes.isekaimahou.feature.trials.TrialManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Portal de Saída: clique para voltar ao lugar de onde você veio (recepção ou arena vencida). */
public class TrialExitBlock extends Block {
    public static final MapCodec<TrialExitBlock> CODEC = simpleCodec(TrialExitBlock::new);
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(0, 0, 0, 16, 2, 16),
            Block.box(3, 2, 3, 13, 12, 13));

    public TrialExitBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp) TrialManager.onExitUse(sp);
        return InteractionResult.CONSUME;
    }

    /** Redemoinho de portal girando em volta do cristal (só visual). */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource r) {
        double x = pos.getX() + 0.5, y = pos.getY() + 0.75, z = pos.getZ() + 0.5;
        long t = level.getGameTime();
        for (int i = 0; i < 3; i++) {
            double a = t * 0.25 + i * Math.PI * 2 / 3;
            level.addParticle(ParticleTypes.REVERSE_PORTAL, x + Math.cos(a) * 0.55, y + r.nextDouble() * 0.5, z + Math.sin(a) * 0.55,
                    0, 0.02, 0);
        }
        if (r.nextInt(4) == 0) level.addParticle(ParticleTypes.GLOW, x, y + 0.6, z, 0, 0.05, 0);
    }
}
