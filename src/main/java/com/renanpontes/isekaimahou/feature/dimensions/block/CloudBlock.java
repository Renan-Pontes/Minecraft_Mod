package com.renanpontes.isekaimahou.feature.dimensions.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Nuvem sólida (Santuário Celestial) e nuvem de tempestade (Céus da Tempestade).
 * É macia: absorve 80% do dano de queda, como um fardo de feno. A de tempestade solta faíscas por baixo.
 */
public class CloudBlock extends Block {
    private final boolean stormy;

    public CloudBlock(Properties properties, boolean stormy) {
        super(properties);
        this.stormy = stormy;
    }

    @Override
    public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
        entity.causeFallDamage(fallDistance, 0.2F, level.damageSources().fall());
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (stormy) {
            if (random.nextInt(40) == 0 && level.getBlockState(pos.below()).isAir()) {
                level.addParticle(ParticleTypes.ELECTRIC_SPARK, pos.getX() + random.nextDouble(), pos.getY() - 0.05,
                        pos.getZ() + random.nextDouble(), 0.0, -0.05, 0.0);
            }
        } else if (random.nextInt(120) == 0 && level.getBlockState(pos.above()).isAir()) {
            level.addParticle(ParticleTypes.END_ROD, pos.getX() + random.nextDouble(), pos.getY() + 1.05,
                    pos.getZ() + random.nextDouble(), 0.0, 0.01, 0.0);
        }
    }
}
