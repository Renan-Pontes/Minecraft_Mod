package com.renanpontes.isekaimahou.feature.ritual_mage.block;

import java.util.List;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;
import com.renanpontes.isekaimahou.feature.ritual_mage.MageRegistries;
import com.renanpontes.isekaimahou.feature.ritual_mage.network.MagePayloads;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Mesa de Pesquisa: escrivaninha com livro aberto, vela e tinteiro. Abre a tela das Teses.
 * Estantes num raio de 4 blocos liberam as teses avançadas.
 */
public class ResearchTableBlock extends BaseEntityBlock {
    public static final MapCodec<ResearchTableBlock> CODEC = simpleCodec(ResearchTableBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(0, 12, 0, 16, 15, 16),
            Block.box(1, 0, 1, 3, 12, 3), Block.box(13, 0, 1, 15, 12, 3),
            Block.box(1, 0, 13, 3, 12, 15), Block.box(13, 0, 13, 15, 12, 15),
            Block.box(2, 3, 2, 14, 4, 14),
            Block.box(3, 15, 4, 13, 16.25, 11));

    public ResearchTableBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
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
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ResearchTableBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, MageRegistries.RESEARCH_TABLE_BE.get(), ResearchTableBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof ResearchTableBlockEntity table) {
            table.refreshShelves();
            MagePayloads.syncAtlas(sp);
            sp.openMenu(table, buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof ResearchTableBlockEntity table) {
            Containers.dropContents(level, pos, table.items());
            level.updateNeighbourForOutputSignal(pos, this);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    /** Partículas: letras voando das estantes (como a mesa de encantamento), chama da vela e brilho no livro. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        for (BlockPos offset : EnchantingTableBlock.BOOKSHELF_OFFSETS) {
            if (random.nextInt(18) == 0 && EnchantingTableBlock.isValidBookShelf(level, pos, offset)) {
                level.addParticle(Vfx.enchant(), pos.getX() + 0.5, pos.getY() + 1.6, pos.getZ() + 0.5,
                        offset.getX() + random.nextFloat() - 0.5, offset.getY() - random.nextFloat() - 1.0F,
                        offset.getZ() + random.nextFloat() - 0.5);
            }
        }
        double[] flame = rotate(state.getValue(FACING), 13.5, 13.0);
        level.addParticle(Vfx.smallFlame(), pos.getX() + flame[0], pos.getY() + 19.4 / 16.0, pos.getZ() + flame[1], 0, 0, 0);
        if (random.nextInt(4) == 0) {
            level.addParticle(Vfx.smoke(), pos.getX() + flame[0], pos.getY() + 19.8 / 16.0, pos.getZ() + flame[1], 0, 0.01, 0);
        }
        if (random.nextInt(6) == 0) {
            double[] book = rotate(state.getValue(FACING), 4 + random.nextDouble() * 8, 5 + random.nextDouble() * 5);
            level.addParticle(Vfx.endRod(), pos.getX() + book[0], pos.getY() + 1.05, pos.getZ() + book[1],
                    0, 0.012 + random.nextDouble() * 0.01, 0);
        }
    }

    /** Converte uma posição (x, z em 0..16) do modelo virado para o norte para a rotação atual. */
    private static double[] rotate(Direction facing, double x, double z) {
        double rx, rz;
        switch (facing) {
            case EAST -> { rx = 16 - z; rz = x; }
            case SOUTH -> { rx = 16 - x; rz = 16 - z; }
            case WEST -> { rx = z; rz = 16 - x; }
            default -> { rx = x; rz = z; }
        }
        return new double[]{rx / 16.0, rz / 16.0};
    }

    /** Item da mesa com dica no tooltip. */
    public static class TableItem extends BlockItem {
        public TableItem(Block block, Item.Properties properties) {
            super(block, properties);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("block.isekai_mahou.research_table.tooltip").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("block.isekai_mahou.research_table.tooltip2").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
