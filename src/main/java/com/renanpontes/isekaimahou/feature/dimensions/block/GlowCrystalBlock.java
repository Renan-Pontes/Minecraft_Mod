package com.renanpontes.isekaimahou.feature.dimensions.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Cristais das dimensões: Cristal do Vazio (luz fraca, partículas de portal invertido) e Cristal Radiante
 * (luz total, faíscas de luz). Tilintam como ametista quando atingidos por projéteis.
 */
public class GlowCrystalBlock extends Block {
    private final boolean radiant;

    public GlowCrystalBlock(Properties properties, boolean radiant) {
        super(properties);
        this.radiant = radiant;
    }

    @Override
    protected void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
        if (!level.isClientSide) {
            BlockPos pos = hit.getBlockPos();
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.BLOCKS, 1.0F, radiant ? 1.4F : 0.6F);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0F, radiant ? 1.2F : 0.7F);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(radiant ? 6 : 8) != 0) return;
        Direction d = Direction.getRandom(random);
        BlockPos side = pos.relative(d);
        if (level.getBlockState(side).isSolidRender(level, side)) return;
        double x = pos.getX() + 0.5 + d.getStepX() * 0.55 + (d.getStepX() == 0 ? random.nextDouble() - 0.5 : 0);
        double y = pos.getY() + 0.5 + d.getStepY() * 0.55 + (d.getStepY() == 0 ? random.nextDouble() - 0.5 : 0);
        double z = pos.getZ() + 0.5 + d.getStepZ() * 0.55 + (d.getStepZ() == 0 ? random.nextDouble() - 0.5 : 0);
        if (radiant) {
            level.addParticle(ParticleTypes.END_ROD, x, y, z, d.getStepX() * 0.02, d.getStepY() * 0.02 + 0.01, d.getStepZ() * 0.02);
        } else {
            level.addParticle(ParticleTypes.REVERSE_PORTAL, x, y, z, d.getStepX() * 0.03, d.getStepY() * 0.03, d.getStepZ() * 0.03);
        }
    }
}
