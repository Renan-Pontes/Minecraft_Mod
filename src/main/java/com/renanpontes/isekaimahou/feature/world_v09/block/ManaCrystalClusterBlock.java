package com.renanpontes.isekaimahou.feature.world_v09.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Aglomerado de Cristal de Mana: decoração que nasce perto de santuários e portais. Quebrado dá Cristais de Mana
 * (1 com a mão, 2 com picareta, mais com Fortuna; Toque de Seda pega o próprio aglomerado). Brilha e solta faíscas.
 */
public class ManaCrystalClusterBlock extends AmethystClusterBlock {

    public ManaCrystalClusterBlock(Properties properties) {
        super(7.0F, 3.0F, properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        Direction facing = state.getValue(FACING);
        double cx = pos.getX() + 0.5 + facing.getStepX() * 0.3;
        double cy = pos.getY() + 0.5 + facing.getStepY() * 0.3;
        double cz = pos.getZ() + 0.5 + facing.getStepZ() * 0.3;
        if (random.nextInt(3) == 0) {
            level.addParticle(ManaVeinOreBlock.MANA_DUST, cx + (random.nextDouble() - 0.5) * 0.6, cy + (random.nextDouble() - 0.5) * 0.6,
                    cz + (random.nextDouble() - 0.5) * 0.6, 0.0, 0.03, 0.0);
        }
        if (random.nextInt(14) == 0) {
            level.addParticle(ParticleTypes.END_ROD, cx, cy, cz, facing.getStepX() * 0.02, 0.03 + facing.getStepY() * 0.02,
                    facing.getStepZ() * 0.02);
        }
        if (random.nextInt(200) == 0) {
            level.playLocalSound(cx, cy, cz, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.4F,
                    1.2F + random.nextFloat() * 0.4F, false);
        }
    }
}
