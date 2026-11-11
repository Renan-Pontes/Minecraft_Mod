package com.renanpontes.isekaimahou.feature.settlements.block;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;
import com.renanpontes.isekaimahou.feature.settlements.SettlementService;
import com.renanpontes.isekaimahou.feature.settlements.SettlementStyle;
import com.renanpontes.isekaimahou.feature.settlements.SettlementsContent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Coração da cidade do jogador: pedestal com o cristal do estilo flutuando (desenhado pelo renderer). Clicar abre o
 * governo da cidade (dono) ou as informações/cidadania (visitante). Quebrar só no criativo (desfaz a cidade).
 */
public class SettlementHeartBlock extends BaseEntityBlock {
    public static final MapCodec<SettlementHeartBlock> CODEC = simpleCodec(SettlementHeartBlock::new);
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(0, 0, 0, 16, 3, 16),
            Block.box(2, 3, 2, 14, 10, 14),
            Block.box(1, 10, 1, 15, 13, 15));

    public SettlementHeartBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SettlementHeartBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, SettlementsContent.HEART_BE.get(), SettlementHeartBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof SettlementHeartBlockEntity be) {
            SettlementService.openHeart(sp, be.cityId());
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel sl
                && level.getBlockEntity(pos) instanceof SettlementHeartBlockEntity be && !be.cityId().isEmpty()) {
            SettlementService.onHeartRemoved(sl, be.cityId());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** Brilho do cristal na cor do estilo: faíscas, runas e o "sabor" (areia dourada, neve). */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double x = pos.getX() + 0.5, y = pos.getY() + 1.7, z = pos.getZ() + 0.5;
        if (random.nextInt(2) == 0) {
            level.addParticle(ParticleTypes.END_ROD, x + (random.nextDouble() - 0.5) * 0.8, y + random.nextDouble() * 0.6,
                    z + (random.nextDouble() - 0.5) * 0.8, 0.0, 0.02, 0.0);
        }
        level.addParticle(ParticleTypes.ENCHANT, x, y + 0.2, z, (random.nextDouble() - 0.5) * 2.0, -random.nextDouble() * 0.6,
                (random.nextDouble() - 0.5) * 2.0);
        if (random.nextInt(3) == 0 && level.getBlockEntity(pos) instanceof SettlementHeartBlockEntity be) {
            SettlementStyle s = be.style();
            if (s == SettlementStyle.DESERT) {
                level.addParticle(ParticleTypes.WAX_ON, x + (random.nextDouble() - 0.5) * 1.2, y + random.nextDouble() * 0.8,
                        z + (random.nextDouble() - 0.5) * 1.2, 0.0, 0.03, 0.0);
            } else if (s == SettlementStyle.NORDIC) {
                level.addParticle(ParticleTypes.SNOWFLAKE, x + (random.nextDouble() - 0.5) * 1.4, y + 0.9,
                        z + (random.nextDouble() - 0.5) * 1.4, 0.0, -0.02, 0.0);
            } else {
                level.addParticle(ParticleTypes.GLOW, x + (random.nextDouble() - 0.5) * 1.2, y + random.nextDouble() * 0.8,
                        z + (random.nextDouble() - 0.5) * 1.2, 0.0, 0.02, 0.0);
            }
        }
    }
}
