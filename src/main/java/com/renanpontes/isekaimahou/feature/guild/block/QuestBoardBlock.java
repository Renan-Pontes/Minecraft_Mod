package com.renanpontes.isekaimahou.feature.guild.block;

import com.mojang.serialization.MapCodec;
import com.renanpontes.isekaimahou.feature.guild.GuildService;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Quadro de Missões: painel de madeira com cartazes. Clique para ver as missões do dia e entregar as concluídas. */
public class QuestBoardBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<QuestBoardBlock> CODEC = simpleCodec(QuestBoardBlock::new);

    // Quadro fino entre dois postes (virado para norte/sul ou leste/oeste).
    private static final VoxelShape SHAPE_NS = Shapes.or(Block.box(1, 0, 6.5, 3, 16, 9.5), Block.box(13, 0, 6.5, 15, 16, 9.5),
            Block.box(0, 4, 7, 16, 16, 9));
    private static final VoxelShape SHAPE_EW = Shapes.or(Block.box(6.5, 0, 1, 9.5, 16, 3), Block.box(6.5, 0, 13, 9.5, 16, 15),
            Block.box(7, 4, 0, 9, 16, 16));

    public QuestBoardBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? SHAPE_NS : SHAPE_EW;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) GuildService.openBoard(sp, pos, com.renanpontes.isekaimahou.feature.guild.GuildFaction.GUILD);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** De vez em quando um cartaz "balança" (partícula de papel/encantamento discreta). */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(14) != 0) return;
        Direction f = state.getValue(FACING);
        double x = pos.getX() + 0.5 + f.getStepX() * 0.18 + (f.getAxis() == Direction.Axis.Z ? (random.nextDouble() - 0.5) * 0.8 : 0);
        double z = pos.getZ() + 0.5 + f.getStepZ() * 0.18 + (f.getAxis() == Direction.Axis.X ? (random.nextDouble() - 0.5) * 0.8 : 0);
        level.addParticle(ParticleTypes.ENCHANT, x, pos.getY() + 0.5 + random.nextDouble() * 0.4, z, 0, 0.1, 0);
    }
}
