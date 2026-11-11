package com.renanpontes.isekaimahou.feature.ritual_necro.block;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Multibloco do Altar Ossuário (vista de cima, altar no centro A):
 * <pre>
 *   C . . . C      y-1: anel de 8 Blocos de Osso em volta do pé do altar (B)
 *   . B B B .      y / y+1: nos 4 cantos (C, a 2 blocos na diagonal) um crânio/cabeça,
 *   . B A B .            uma vela ACESA ou uma luz de alma (tocha, lanterna, fogueira acesa)
 *   . B B B .
 *   C . . . C
 * </pre>
 * Ranks altos pedem que alguns cantos sejam crânios de verdade (esqueleto ou esqueleto wither).
 */
public final class OssuaryStructure {
    public static final int CORNER_OFFSET = 2;

    /** Resultado da validação: o que falta (para mostrar ao jogador) e quantos crânios verdadeiros há. */
    public record Report(List<BlockPos> missingRing, List<BlockPos> missingCorners, List<BlockPos> cornersWithoutSkull, int skulls) {
        public boolean complete() {
            return missingRing.isEmpty() && missingCorners.isEmpty();
        }
    }

    public static List<BlockPos> ring(BlockPos altar) {
        List<BlockPos> out = new ArrayList<>(8);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) out.add(altar.offset(dx, -1, dz));
            }
        }
        return out;
    }

    /** Cantos na altura do altar (a peça pode ficar ali ou 1 bloco acima, sobre um pilar). */
    public static List<BlockPos> corners(BlockPos altar) {
        int o = CORNER_OFFSET;
        return List.of(altar.offset(-o, 0, -o), altar.offset(o, 0, -o), altar.offset(-o, 0, o), altar.offset(o, 0, o));
    }

    public static Report check(BlockGetter level, BlockPos altar) {
        List<BlockPos> missingRing = new ArrayList<>();
        for (BlockPos p : ring(altar)) {
            if (!level.getBlockState(p).is(Blocks.BONE_BLOCK)) missingRing.add(p);
        }
        List<BlockPos> missingCorners = new ArrayList<>();
        List<BlockPos> withoutSkull = new ArrayList<>();
        int skulls = 0;
        for (BlockPos base : corners(altar)) {
            BlockState low = level.getBlockState(base);
            BlockState high = level.getBlockState(base.above());
            if (isTrueSkull(low) || isTrueSkull(high)) {
                skulls++;
            } else if (isCornerPiece(low) || isCornerPiece(high)) {
                withoutSkull.add(base);
            } else {
                missingCorners.add(base);
                withoutSkull.add(base);
            }
        }
        return new Report(missingRing, missingCorners, withoutSkull, skulls);
    }

    /** Peça aceita num canto: qualquer crânio/cabeça, vela acesa ou luz de alma. */
    public static boolean isCornerPiece(BlockState s) {
        if (s.getBlock() instanceof AbstractSkullBlock) return true;
        if (s.is(Blocks.SOUL_TORCH) || s.is(Blocks.SOUL_WALL_TORCH) || s.is(Blocks.SOUL_LANTERN)) return true;
        if (s.is(Blocks.SOUL_CAMPFIRE)) return s.getValue(CampfireBlock.LIT);
        return s.is(BlockTags.CANDLES) && s.hasProperty(CandleBlock.LIT) && s.getValue(CandleBlock.LIT);
    }

    /** Crânio "de verdade" (pedido nos ranks altos): de esqueleto ou de esqueleto wither, no chão ou na parede. */
    public static boolean isTrueSkull(BlockState s) {
        if (!(s.getBlock() instanceof AbstractSkullBlock skull)) return false;
        return skull.getType() == SkullBlock.Types.SKELETON || skull.getType() == SkullBlock.Types.WITHER_SKELETON;
    }

    private OssuaryStructure() {}
}
