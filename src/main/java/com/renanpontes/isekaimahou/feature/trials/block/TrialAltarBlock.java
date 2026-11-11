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

/**
 * Altar da Provação. Na recepção: o anfitrião clica para começar a contagem (clicar de novo cancela); os outros
 * veem quem falta. Fora do Reino das Provações: mostra as Provações disponíveis do jogador (links clicáveis).
 */
public class TrialAltarBlock extends Block {
    public static final MapCodec<TrialAltarBlock> CODEC = simpleCodec(TrialAltarBlock::new);
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(1, 0, 1, 15, 3, 15),
            Block.box(4, 3, 4, 12, 10, 12),
            Block.box(2, 10, 2, 14, 13, 14));

    public TrialAltarBlock(Properties props) {
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
        if (player instanceof ServerPlayer sp) TrialManager.onAltarUse(sp, pos);
        return InteractionResult.CONSUME;
    }

    /** Runas subindo do tampo e um fio de luz no centro (só visual, lado do cliente). */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource r) {
        double x = pos.getX() + 0.5, y = pos.getY() + 0.85, z = pos.getZ() + 0.5;
        for (int i = 0; i < 2; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            level.addParticle(ParticleTypes.ENCHANT, x + Math.cos(a) * 0.9, y + 0.6 + r.nextDouble() * 0.6, z + Math.sin(a) * 0.9,
                    -Math.cos(a) * 0.6, -0.3, -Math.sin(a) * 0.6);
        }
        if (r.nextInt(3) == 0) level.addParticle(ParticleTypes.END_ROD, x, y + 0.1, z, 0, 0.03 + r.nextDouble() * 0.03, 0);
    }
}
