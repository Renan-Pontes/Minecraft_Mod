package com.renanpontes.isekaimahou.feature.dimensions.block;

import com.mojang.serialization.MapCodec;
import com.renanpontes.isekaimahou.feature.dimensions.DimRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Grama do céu (Plano Elemental). Funciona como a grama comum, mas com a terra do céu: morre (vira
 * {@code sky_soil}) quando coberta e se espalha devagar para terra do céu exposta.
 */
public class SkyGrassBlock extends Block {
    public static final MapCodec<SkyGrassBlock> CODEC = simpleCodec(SkyGrassBlock::new);

    public SkyGrassBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    private static boolean canStayGrass(LevelReader level, BlockPos pos) {
        BlockPos above = pos.above();
        BlockState state = level.getBlockState(above);
        return !state.isFaceSturdy(level, above, net.minecraft.core.Direction.DOWN) && state.getFluidState().isEmpty();
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!canStayGrass(level, pos)) {
            level.setBlockAndUpdate(pos, DimRegistry.SKY_SOIL.get().defaultBlockState());
            return;
        }
        for (int i = 0; i < 3; i++) {
            BlockPos target = pos.offset(random.nextInt(3) - 1, random.nextInt(5) - 3, random.nextInt(3) - 1);
            if (level.getBlockState(target).is(DimRegistry.SKY_SOIL.get()) && canStayGrass(level, target)) {
                level.setBlockAndUpdate(target, defaultBlockState());
            }
        }
    }

    /** Um pólen brilhante de vez em quando (a grama do céu é levemente mágica). */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(60) == 0 && level.getBlockState(pos.above()).isAir()) {
            level.addParticle(ParticleTypes.WAX_OFF, pos.getX() + random.nextDouble(), pos.getY() + 1.1,
                    pos.getZ() + random.nextDouble(), 0.0, 0.02, 0.0);
        }
    }
}
