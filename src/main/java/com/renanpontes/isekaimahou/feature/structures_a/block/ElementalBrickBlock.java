package com.renanpontes.isekaimahou.feature.structures_a.block;

import com.renanpontes.isekaimahou.core.Element;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Tijolo elemental (decoração dos santuários). Cada elemento solta, de vez em quando, uma partícula discreta
 * numa face livre: faísca (fogo), gota (água), poeira (terra), cinza branca (vento), brilho (luz), portal (sombra).
 */
public class ElementalBrickBlock extends Block {
    private final Element element;

    public ElementalBrickBlock(Element element, Properties props) {
        super(props);
        this.element = element;
    }

    public Element element() {
        return element;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(5) != 0) return;
        Direction dir = Direction.getRandom(random);
        BlockPos side = pos.relative(dir);
        if (level.getBlockState(side).isSolidRender(level, side)) return;
        // Ponto na face escolhida, um pouco para fora do bloco.
        double x = pos.getX() + 0.5 + dir.getStepX() * 0.55 + (dir.getStepX() == 0 ? (random.nextDouble() - 0.5) : 0);
        double y = pos.getY() + 0.5 + dir.getStepY() * 0.55 + (dir.getStepY() == 0 ? (random.nextDouble() - 0.5) : 0);
        double z = pos.getZ() + 0.5 + dir.getStepZ() * 0.55 + (dir.getStepZ() == 0 ? (random.nextDouble() - 0.5) : 0);
        ParticleOptions p;
        double vy = 0;
        switch (element) {
            case FIRE -> { p = ParticleTypes.SMALL_FLAME; vy = 0.01; }
            case WATER -> {
                if (dir != Direction.DOWN) return; // a gota só cai por baixo
                p = ParticleTypes.DRIPPING_WATER;
            }
            case EARTH -> {
                if (dir != Direction.DOWN) return;
                p = new BlockParticleOption(ParticleTypes.FALLING_DUST, state);
            }
            case WIND -> { p = ParticleTypes.WHITE_ASH; vy = 0.02; }
            case LIGHT -> p = ParticleTypes.WAX_OFF;
            default -> { p = ParticleTypes.REVERSE_PORTAL; vy = 0.01; }
        }
        level.addParticle(p, x, y, z, 0, vy, 0);
    }
}
