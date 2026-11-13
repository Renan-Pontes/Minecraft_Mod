package com.renanpontes.isekaimahou.feature.market;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
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

/** Quadro do Mercado: painel com etiquetas de preço. Clique para abrir o Mercado (o mesmo da aba do menu). */
public class MarketBoardBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<MarketBoardBlock> CODEC = simpleCodec(MarketBoardBlock::new);

    // Painel fino entre dois postes (virado para norte/sul ou leste/oeste)
    private static final VoxelShape SHAPE_NS = Shapes.or(Block.box(1, 0, 6.5, 3, 16, 9.5), Block.box(13, 0, 6.5, 15, 16, 9.5),
            Block.box(0, 4, 7, 16, 16, 9));
    private static final VoxelShape SHAPE_EW = Shapes.or(Block.box(6.5, 0, 1, 9.5, 16, 3), Block.box(6.5, 0, 13, 9.5, 16, 15),
            Block.box(7, 4, 0, 9, 16, 16));

    public MarketBoardBlock(Properties properties) {
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
        if (!level.isClientSide && player instanceof ServerPlayer sp) MarketService.open(sp);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
