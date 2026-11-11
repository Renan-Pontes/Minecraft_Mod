package com.renanpontes.isekaimahou.feature.guild.block;

import java.util.Comparator;
import java.util.List;

import com.mojang.serialization.MapCodec;
import com.renanpontes.isekaimahou.feature.guild.GuildService;
import com.renanpontes.isekaimahou.feature.guild.entity.GuildReceptionistEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Balcão da Guilda: decorativo e funcional — clicar no balcão chama a recepcionista mais próxima (até 8 blocos)
 * e abre o atendimento, mesmo que ela esteja do outro lado do balcão.
 */
public class GuildCounterBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<GuildCounterBlock> CODEC = simpleCodec(GuildCounterBlock::new);
    private static final VoxelShape SHAPE = Shapes.or(Block.box(1, 0, 1, 15, 13, 15), Block.box(0, 13, 0, 16, 16, 16));

    public GuildCounterBlock(Properties properties) {
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
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide || !(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        List<GuildReceptionistEntity> near = level.getEntitiesOfClass(GuildReceptionistEntity.class, new AABB(pos).inflate(8.0),
                GuildReceptionistEntity::isAlive);
        if (near.isEmpty()) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.guild.counter_empty").withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.CONSUME;
        }
        // Toca a "sineta" do balcão e a recepcionista mais próxima atende.
        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 0.6F, 1.6F);
        GuildReceptionistEntity r = near.stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(pos.getCenter()))).get();
        r.faceFor(sp, 80);
        r.triggerAnim("action", "bow");
        GuildService.openReception(sp, r);
        return InteractionResult.CONSUME;
    }
}
