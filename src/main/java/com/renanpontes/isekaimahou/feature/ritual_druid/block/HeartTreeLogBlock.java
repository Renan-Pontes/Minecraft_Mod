package com.renanpontes.isekaimahou.feature.ritual_druid.block;

import org.joml.Vector3f;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Tronco da Árvore Coração: casca com runas que brilham (faces emissivas no modelo) e soltam faíscas verdes. */
public class HeartTreeLogBlock extends RotatedPillarBlock {
    public static final MapCodec<HeartTreeLogBlock> CODEC = simpleCodec(HeartTreeLogBlock::new);
    private static final DustParticleOptions RUNE_DUST = new DustParticleOptions(new Vector3f(0.55F, 1.0F, 0.75F), 0.7F);

    public HeartTreeLogBlock(Properties props) {
        super(props);
    }

    @Override
    public MapCodec<? extends RotatedPillarBlock> codec() {
        return CODEC;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(14) != 0) return;
        // Faísca saindo de uma face lateral (as runas)
        Direction d = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        if (!level.getBlockState(pos.relative(d)).isAir()) return;
        double x = pos.getX() + 0.5 + d.getStepX() * 0.55 + (d.getStepX() == 0 ? (random.nextDouble() - 0.5) * 0.8 : 0);
        double z = pos.getZ() + 0.5 + d.getStepZ() * 0.55 + (d.getStepZ() == 0 ? (random.nextDouble() - 0.5) * 0.8 : 0);
        double y = pos.getY() + 0.1 + random.nextDouble() * 0.8;
        level.addParticle(RUNE_DUST, x, y, z, 0, 0.02, 0);
    }
}
