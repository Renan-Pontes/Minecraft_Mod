package com.renanpontes.isekaimahou.feature.ascension.block;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Bloco de Cristal de Mana: brilha e solta faíscas arcanas. Peça do Círculo de Ascensão (alvo 4+). */
public class ManaCrystalBlock extends Block {
    public ManaCrystalBlock(Properties props) {
        super(props);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) != 0) return;
        Direction d = Direction.getRandom(random);
        BlockPos side = pos.relative(d);
        if (level.getBlockState(side).isSolidRender(level, side)) return;
        double x = pos.getX() + 0.5 + d.getStepX() * 0.55 + (d.getStepX() == 0 ? (random.nextDouble() - 0.5) : 0);
        double y = pos.getY() + 0.5 + d.getStepY() * 0.55 + (d.getStepY() == 0 ? (random.nextDouble() - 0.5) : 0);
        double z = pos.getZ() + 0.5 + d.getStepZ() * 0.55 + (d.getStepZ() == 0 ? (random.nextDouble() - 0.5) : 0);
        level.addParticle(random.nextBoolean() ? ParticleTypes.END_ROD : ElementFx.dust(Element.ARCANE, 0.8F), x, y, z, 0, 0.01, 0);
    }
}
