package com.renanpontes.isekaimahou.feature.depths.puzzle.enigma;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.depths.puzzle.PuzzleBlocks;
import com.renanpontes.isekaimahou.feature.depths.puzzle.PuzzleCoreBlock;
import com.renanpontes.isekaimahou.feature.depths.puzzle.PuzzleCoreBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Coloca Selos-Enigma no mundo: confere se a área (9 × 8 a partir do Selo) está livre, limpa e põe o Selo. */
public final class EnigmaPlacer {
    /** Dá para montar um enigma com o Selo em {@code core} apontando para {@code forward}? (piso firme, ar até 3 de altura) */
    public static boolean fits(ServerLevel sl, BlockPos core, Direction forward, int u0) {
        Frame f = new Frame(core, forward, u0);
        for (int u = 0; u < Frame.W; u++) {
            for (int v = 0; v <= Frame.D; v++) {
                BlockPos floor = f.pos(u, v, -1);
                if (!sl.isLoaded(floor) || !sl.getBlockState(floor).isFaceSturdy(sl, floor, Direction.UP)) return false;
                for (int h = 0; h <= 2; h++) {
                    BlockPos p = f.pos(u, v, h);
                    if (!clearable(sl.getBlockState(p)) || !sl.getFluidState(p).isEmpty()) return false;
                }
            }
        }
        return true;
    }

    /** Ar ou enfeite que pode sumir (alvo de treino, velas, teias, tapetes, plantinhas). */
    private static boolean clearable(BlockState s) {
        return s.isAir() || s.canBeReplaced() || s.is(Blocks.TARGET) || s.is(Blocks.COBWEB) || s.is(BlockTags.CANDLES)
                || s.is(BlockTags.WOOL_CARPETS);
    }

    /** Limpa a área (h 0..3) e põe o Selo-Enigma. Devolve o Selo (ou null se o bloco não pegou). */
    @Nullable
    public static PuzzleCoreBlockEntity place(ServerLevel sl, BlockPos core, Direction forward, int u0, @Nullable EnigmaType type,
                                              int tier, long seed, String chain, int role, boolean locked) {
        Frame f = new Frame(core, forward, u0);
        for (int u = 0; u < Frame.W; u++) {
            for (int v = 0; v <= Frame.D; v++) {
                for (int h = 0; h <= 3; h++) {
                    BlockPos p = f.pos(u, v, h);
                    if (!sl.getBlockState(p).isAir()) sl.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        sl.setBlock(core, PuzzleBlocks.CORE.get().defaultBlockState().setValue(PuzzleCoreBlock.FACING, forward), Block.UPDATE_ALL);
        if (!(sl.getBlockEntity(core) instanceof PuzzleCoreBlockEntity be)) return null;
        be.setupEnigma(type, tier, seed == 0 ? 1 : seed, u0, chain, role, locked);
        return be;
    }

    /** Teste: piso de pedra-negra polida + área limpa, Selo 2 blocos à frente de {@code feet}. */
    @Nullable
    public static PuzzleCoreBlockEntity demo(ServerLevel sl, BlockPos feet, Direction forward, @Nullable EnigmaType type, int tier,
                                             long seed, String chain, int role) {
        BlockPos core = feet.relative(forward, 2);
        Frame f = new Frame(core, forward, 4);
        for (int u = -1; u <= Frame.W; u++) {
            for (int v = -1; v <= Frame.D + 1; v++) {
                sl.setBlock(f.pos(u, v, -1), Blocks.POLISHED_DEEPSLATE.defaultBlockState(), Block.UPDATE_CLIENTS);
                for (int h = 0; h <= 4; h++) sl.setBlock(f.pos(u, v, h), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        return place(sl, core, forward, 4, type, tier, seed, chain, role, false);
    }

    private EnigmaPlacer() {}
}
