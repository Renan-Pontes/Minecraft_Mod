package com.renanpontes.isekaimahou.feature.depths.puzzle;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** Lâmpada Rúnica e Placa Rúnica: só acendem/apagam — quem manda é o Selo Rúnico ({@link PuzzleCoreBlockEntity}). */
public class RuneLitBlock extends Block {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public RuneLitBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(LIT);
    }
}
