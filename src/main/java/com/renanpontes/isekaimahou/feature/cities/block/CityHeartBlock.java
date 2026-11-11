package com.renanpontes.isekaimahou.feature.cities.block;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.feature.cities.CitiesContent;
import com.renanpontes.isekaimahou.feature.cities.CitySiege;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Coração da cidade: pedestal de pedra com um cristal (na cor do reino) flutuando (o cristal é desenhado pelo renderer do cliente).
 * Registra a cidade como lugar nomeado, repõe os moradores mortos e conduz o Cerco. Quebrar (criativo) desfaz o lugar.
 */
public class CityHeartBlock extends BaseEntityBlock {
    public static final MapCodec<CityHeartBlock> CODEC = simpleCodec(CityHeartBlock::new);
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(1, 0, 1, 15, 3, 15),
            Block.box(3, 3, 3, 13, 11, 13),
            Block.box(2, 11, 2, 14, 13, 14));

    public CityHeartBlock(Properties props) {
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
        return new CityHeartBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, CitiesContent.CITY_HEART_BE.get(), CityHeartBlockEntity::serverTick);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel sl
                && level.getBlockEntity(pos) instanceof CityHeartBlockEntity heart) {
            CitySiege.stop(sl, heart.placeId(), false);
            Places.remove(sl, heart.placeId());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** Brilho do cristal: faíscas e runas subindo. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 1.6;
        double z = pos.getZ() + 0.5;
        if (random.nextInt(2) == 0) {
            level.addParticle(ParticleTypes.END_ROD, x + (random.nextDouble() - 0.5) * 0.8, y + random.nextDouble() * 0.6,
                    z + (random.nextDouble() - 0.5) * 0.8, 0.0, 0.02, 0.0);
        }
        for (int i = 0; i < 2; i++) {
            level.addParticle(ParticleTypes.ENCHANT, x, y + 0.2, z, (random.nextDouble() - 0.5) * 2.0,
                    -random.nextDouble() * 0.6, (random.nextDouble() - 0.5) * 2.0);
        }
        // Sabor do reino: brasas douradas no sol de Sahra, flocos de neve em Nordheim.
        if (random.nextInt(3) == 0 && level.getBlockEntity(pos) instanceof CityHeartBlockEntity heart) {
            switch (heart.kingdom()) {
                case SAHRA -> level.addParticle(ParticleTypes.WAX_ON, x + (random.nextDouble() - 0.5) * 1.2,
                        y + random.nextDouble() * 0.8, z + (random.nextDouble() - 0.5) * 1.2, 0.0, 0.03, 0.0);
                case NORDHEIM -> level.addParticle(ParticleTypes.SNOWFLAKE, x + (random.nextDouble() - 0.5) * 1.4,
                        y + 0.9, z + (random.nextDouble() - 0.5) * 1.4, 0.0, -0.02, 0.0);
                default -> { }
            }
        }
    }
}
