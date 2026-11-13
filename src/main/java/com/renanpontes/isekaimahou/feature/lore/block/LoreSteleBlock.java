package com.renanpontes.isekaimahou.feature.lore.block;

import com.renanpontes.isekaimahou.feature.lore.LoreService;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * <b>Estela Antiga</b>: a pedra gravada das Ruínas de Velmora. Clicar numa estela GRAVADA ({@link #INSCRIBED}, só as
 * que nascem com o mundo) dá um fragmento de lore — uma vez por jogador, por estela — e revela o lugar de que o
 * fragmento fala. A estela posta por um jogador é lisa (decoração): sem isso daria para "farmar" fragmentos
 * quebrando e recolocando.
 */
public class LoreSteleBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty INSCRIBED = BooleanProperty.create("inscribed");

    private static final VoxelShape SHAPE_Z = Block.box(2, 0, 5, 14, 16, 11);
    private static final VoxelShape SHAPE_X = Block.box(5, 0, 2, 11, 16, 14);

    public LoreSteleBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(INSCRIBED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, INSCRIBED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(FACING).getAxis() == Direction.Axis.X ? SHAPE_X : SHAPE_Z;
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
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        if (!state.getValue(INSCRIBED)) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.lore.stele_blank"), true);
            return InteractionResult.CONSUME;
        }
        LoreService.readStele(sp, pos);
        return InteractionResult.CONSUME;
    }

    /** Runas soltando letras de vez em quando: é assim que se acha a estela gravada no meio da ruína. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(INSCRIBED) || random.nextInt(5) != 0) return;
        level.addParticle(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.6, pos.getZ() + 0.5,
                (random.nextDouble() - 0.5) * 1.2, -0.6 - random.nextDouble() * 0.4, (random.nextDouble() - 0.5) * 1.2);
    }
}
