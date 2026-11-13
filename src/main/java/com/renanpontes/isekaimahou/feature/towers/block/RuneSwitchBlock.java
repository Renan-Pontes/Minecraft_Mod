package com.renanpontes.isekaimahou.feature.towers.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Alavanca Rúnica do Salão das Runas: ao usar, alterna a própria runa E as vizinhas da mesma fileira (estilo "Lights Out").
 * O Núcleo do Andar embaralha a fileira quando o desafio começa (sempre com solução) e vence o andar com todas acesas.
 */
public class RuneSwitchBlock extends Block {
    public static final BooleanProperty LIT = BooleanProperty.create("lit");

    public RuneSwitchBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(LIT, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel sl) {
            press(sl, pos, true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Alterna esta runa e as vizinhas (horizontais, mesma altura). */
    public static void press(ServerLevel level, BlockPos pos, boolean fx) {
        toggle(level, pos, fx);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos n = pos.relative(d);
            if (level.getBlockState(n).getBlock() instanceof RuneSwitchBlock) toggle(level, n, fx);
        }
        if (fx) {
            level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.8F, 1.1F);
        }
    }

    private static void toggle(ServerLevel level, BlockPos pos, boolean fx) {
        BlockState st = level.getBlockState(pos);
        if (!(st.getBlock() instanceof RuneSwitchBlock)) return;
        boolean lit = !st.getValue(LIT);
        level.setBlock(pos, st.setValue(LIT, lit), Block.UPDATE_ALL);
        if (fx && lit) {
            level.sendParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 8, 0.4, 0.4, 0.4, 0.3);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.9F, 1.4F);
        }
    }

    public static boolean isLit(BlockState state) {
        return state.getBlock() instanceof RuneSwitchBlock && state.getValue(LIT);
    }
}
