package com.renanpontes.isekaimahou.feature.flat_lands.block;

import com.mojang.serialization.MapCodec;
import com.renanpontes.isekaimahou.feature.flat_lands.FlatLandsContent;
import com.renanpontes.isekaimahou.feature.flat_lands.Omikuji;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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

/**
 * Caixa do Omikuji (fica no Santuário Torii; também dá para fazer). Com uma oferenda na mão → tira a sorte do dia
 * (1×/dia do jogo por jogador). Com um papel de azar na mão → amarra o papel e a má sorte some.
 */
public class OmikujiBoxBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<OmikujiBoxBlock> CODEC = simpleCodec(OmikujiBoxBlock::new);
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(2, 0, 2, 14, 2, 14),
            Block.box(4, 2, 4, 12, 13, 12),
            Block.box(3.5, 13, 3.5, 12.5, 14, 12.5));

    public OmikujiBoxBlock(Properties props) {
        super(props);
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
        return SHAPE;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        boolean slip = stack.is(FlatLandsContent.FORTUNE_SLIP.get());
        boolean offering = stack.is(Omikuji.OFFERINGS);
        if (!slip && !offering) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!(player instanceof ServerPlayer sp)) return ItemInteractionResult.SUCCESS;
        if (slip) {
            return Omikuji.tie(sp, pos, stack) ? ItemInteractionResult.SUCCESS : ItemInteractionResult.CONSUME;
        }
        if (Omikuji.drewToday(sp)) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.sakura.omikuji.already"), true);
            return ItemInteractionResult.CONSUME;
        }
        stack.consume(1, sp);
        Omikuji.draw(sp, pos, null);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.translatable(Omikuji.drewToday(sp)
                    ? "message.isekai_mahou.sakura.omikuji.already" : "message.isekai_mahou.sakura.omikuji.hint"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Uma pétala de vez em quando caindo da caixa. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(6) == 0) {
            level.addParticle(ParticleTypes.CHERRY_LEAVES, pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 1.1,
                    pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0, 0, 0);
        }
    }
}
