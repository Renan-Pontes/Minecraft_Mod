package com.renanpontes.isekaimahou.feature.depths.puzzle;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Espelho do enigma de luz: uma placa na diagonal que desvia o feixe em 90 graus. Botão direito gira.
 * Visto de cima (norte para cima): {@code flipped=false} é a diagonal que sobe para a direita (leste vira norte,
 * oeste vira sul); {@code flipped=true} é a que desce para a direita (leste vira sul, oeste vira norte).
 */
public class LightMirrorBlock extends Block {
    public static final BooleanProperty FLIPPED = BooleanProperty.create("flipped");
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 15, 14);

    public LightMirrorBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FLIPPED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FLIPPED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    /** Para onde vai um feixe que chega andando em {@code dir}. */
    public static Direction reflect(Direction dir, boolean flipped) {
        return switch (dir) {
            case EAST -> flipped ? Direction.SOUTH : Direction.NORTH;
            case WEST -> flipped ? Direction.NORTH : Direction.SOUTH;
            case NORTH -> flipped ? Direction.WEST : Direction.EAST;
            case SOUTH -> flipped ? Direction.EAST : Direction.WEST;
            default -> dir;
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            level.setBlock(pos, state.cycle(FLIPPED), 3);
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 1.0F, 1.2F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // Moldes de estrutura giram/espelham: um quarto de volta (ou um espelhamento) troca a diagonal.
    @Override
    protected BlockState rotate(BlockState state, Rotation rot) {
        return rot == Rotation.CLOCKWISE_90 || rot == Rotation.COUNTERCLOCKWISE_90 ? state.cycle(FLIPPED) : state;
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return mirror == Mirror.NONE ? state : state.cycle(FLIPPED);
    }
}
