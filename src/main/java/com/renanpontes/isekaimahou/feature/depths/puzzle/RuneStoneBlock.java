package com.renanpontes.isekaimahou.feature.depths.puzzle;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/** Pedra Rúnica da "sequência de runas": 4 desenhos; o jogador toca nelas na ordem que o Selo mostrou. */
public class RuneStoneBlock extends Block {
    public static final IntegerProperty RUNE = IntegerProperty.create("rune", 0, 3);
    public static final BooleanProperty LIT = RuneLitBlock.LIT;

    public RuneStoneBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(RUNE, 0).setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(RUNE, LIT);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp) {
            PuzzleCoreBlockEntity core = PuzzleCoreBlockEntity.find(level, pos);
            if (core != null) core.onStoneClicked(sp, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
