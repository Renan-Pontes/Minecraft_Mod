package com.renanpontes.isekaimahou.feature.world_v09.block;

import org.joml.Vector3f;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Veio de Mana: minério comum do Overworld (y −16..80). Solta 1–3 Cristais de Mana (Fortuna) e XP.
 * Faíscas lilases saem das faces expostas — fácil de achar numa caverna escura.
 */
public class ManaVeinOreBlock extends DropExperienceBlock {
    /** Mesma cor do Cristal de Mana (lilás 0x9B6BFF). */
    public static final DustParticleOptions MANA_DUST = new DustParticleOptions(new Vector3f(0.61F, 0.42F, 1.0F), 0.7F);

    public ManaVeinOreBlock(IntProvider xp, Properties properties) {
        super(xp, properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(5) != 0) return;
        Direction d = Direction.getRandom(random);
        BlockPos side = pos.relative(d);
        if (level.getBlockState(side).isSolidRender(level, side)) return;
        double x = pos.getX() + 0.5 + (d.getStepX() == 0 ? random.nextDouble() - 0.5 : d.getStepX() * 0.55);
        double y = pos.getY() + 0.5 + (d.getStepY() == 0 ? random.nextDouble() - 0.5 : d.getStepY() * 0.55);
        double z = pos.getZ() + 0.5 + (d.getStepZ() == 0 ? random.nextDouble() - 0.5 : d.getStepZ() * 0.55);
        level.addParticle(MANA_DUST, x, y, z, 0.0, 0.01, 0.0);
        if (random.nextInt(6) == 0) level.addParticle(ParticleTypes.ENCHANT, x, y, z, d.getStepX() * 0.3, 0.2, d.getStepZ() * 0.3);
    }
}
