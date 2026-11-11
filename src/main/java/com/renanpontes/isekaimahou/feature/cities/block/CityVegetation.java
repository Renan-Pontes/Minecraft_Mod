package com.renanpontes.isekaimahou.feature.cities.block;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Limpa a vegetação NATURAL que nasceu por cima da cidade (a decoração de árvores do bioma roda depois da estrutura):
 * folhas não-persistentes (as da cidade são todas persistentes), troncos que pertencem a essas árvores, colmeias, cactos
 * e bambus. Trabalha um chunk carregado por vez, dentro da caixa da cidade, e lembra quais chunks já limpou.
 * <p>Tronco natural = tronco cujos 6 vizinhos são só ar, folhas, troncos, terra/grama, plantas ou água — vigas de casas
 * encostam em tábuas/pedra/vidro e por isso nunca são removidas.
 */
final class CityVegetation {
    /** Máximo de troncos de uma árvore (árvores gigantes de carvalho escuro/selva cabem com folga). */
    private static final int MAX_TREE_LOGS = 600;

    static void cleanChunk(ServerLevel sl, ChunkPos cp, BoundingBox box, int minY, int maxY) {
        int x0 = Math.max(box.minX(), cp.getMinBlockX()), x1 = Math.min(box.maxX(), cp.getMaxBlockX());
        int z0 = Math.max(box.minZ(), cp.getMinBlockZ()), z1 = Math.min(box.maxZ(), cp.getMaxBlockZ());
        if (x0 > x1 || z0 > z1) return;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        Set<BlockPos> logs = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        // 1) folhas naturais + troncos encostados nelas (sementes da busca)
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                for (int y = maxY; y >= minY; y--) {
                    m.set(x, y, z);
                    BlockState st = sl.getBlockState(m);
                    if (st.isAir()) continue;
                    if (isNaturalLeaves(st)) {
                        for (Direction d : Direction.values()) {
                            BlockPos n = m.relative(d);
                            if (inside(n, box, minY, maxY) && sl.getBlockState(n).is(BlockTags.LOGS) && looksNatural(sl, n)
                                    && logs.add(n.immutable())) queue.add(n.immutable());
                        }
                        sl.setBlock(m, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    } else if (st.is(Blocks.BEE_NEST) || st.is(Blocks.CACTUS) || st.is(Blocks.BAMBOO) || st.is(Blocks.VINE)) {
                        sl.setBlock(m, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    }
                }
            }
        }
        // 2) o resto do tronco/galhos da mesma árvore
        while (!queue.isEmpty() && logs.size() < MAX_TREE_LOGS * 8) {
            BlockPos p = queue.poll();
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dy == 0 && dz == 0) continue;
                BlockPos n = p.offset(dx, dy, dz);
                if (logs.contains(n) || !inside(n, box, minY, maxY) || !sl.isLoaded(n)) continue;
                if (sl.getBlockState(n).is(BlockTags.LOGS) && looksNatural(sl, n)) {
                    logs.add(n);
                    queue.add(n);
                }
            }
        }
        for (BlockPos p : logs) {
            if (sl.isLoaded(p)) sl.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    private static boolean inside(BlockPos p, BoundingBox box, int minY, int maxY) {
        return p.getX() >= box.minX() && p.getX() <= box.maxX() && p.getZ() >= box.minZ() && p.getZ() <= box.maxZ()
                && p.getY() >= minY && p.getY() <= maxY;
    }

    private static boolean isNaturalLeaves(BlockState st) {
        return st.getBlock() instanceof LeavesBlock && st.hasProperty(LeavesBlock.PERSISTENT) && !st.getValue(LeavesBlock.PERSISTENT);
    }

    private static boolean looksNatural(ServerLevel sl, BlockPos p) {
        for (Direction d : Direction.values()) {
            if (d == Direction.DOWN) continue;   // o tronco pode estar plantado em qualquer chão
            BlockPos n = p.relative(d);
            if (!sl.isLoaded(n)) continue;
            BlockState st = sl.getBlockState(n);
            if (st.isAir() || st.is(BlockTags.LEAVES) || st.is(BlockTags.LOGS) || st.is(BlockTags.DIRT) || st.canBeReplaced()
                    || st.is(Blocks.VINE) || st.is(Blocks.COCOA) || st.is(Blocks.BEE_NEST) || st.is(Blocks.SNOW)
                    || st.is(Blocks.MOSS_CARPET) || st.is(Blocks.SAND) || st.is(Blocks.GRAVEL)
                    || !st.getFluidState().isEmpty()) continue;
            return false;
        }
        return true;
    }

    private CityVegetation() {}
}
