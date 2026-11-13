package com.renanpontes.isekaimahou.feature.arcane.block;

import com.mojang.serialization.MapCodec;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Pedra conjurada (glifo Colocar Bloco sem bloco na mão secundária): bloco sólido temporário que
 * desmorona sozinho. Não deixa nada cair ao ser quebrado (não dá para "farmar" pedra com mana).
 */
public class ConjuredStoneBlock extends Block {
    public static final MapCodec<ConjuredStoneBlock> CODEC = simpleCodec(ConjuredStoneBlock::new);

    public ConjuredStoneBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    /** Coloca a pedra se o lugar estiver livre (sem entidades dentro). */
    public boolean placeFor(ServerLevel level, BlockPos pos, int ticks) {
        BlockState here = level.getBlockState(pos);
        if (!(here.isAir() || here.canBeReplaced())) return false;
        BlockState stone = defaultBlockState();
        if (!level.isUnobstructed(stone, pos, CollisionContext.empty())) return false;
        level.setBlock(pos, stone, 3);
        level.scheduleTick(pos, this, Math.max(20, ticks));
        return true;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // Desmorona com as partículas/som de quebra normais e um brilho arcano
        level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
        level.removeBlock(pos, false);
        level.sendParticles(Vfx.witch(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.0);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(4) != 0) return;
        level.addParticle(Vfx.enchant(), pos.getX() + random.nextDouble(), pos.getY() + 1.05,
                pos.getZ() + random.nextDouble(), 0, 0.05, 0);
    }
}
