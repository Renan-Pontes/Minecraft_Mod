package com.renanpontes.isekaimahou.core.worldgen;

import java.util.List;

import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

/**
 * "Tem um chunk de início do conjunto X a até N chunks daqui?" — o mesmo que
 * {@link ChunkGeneratorStructureState#hasStructureChunkInRange}, mas rápido para as zonas grandes (9–16 chunks) que as
 * cidades usam: em vez de testar os (2N+1)² chunks, testa só o chunk sorteado de cada região da grade (random_spread)
 * ou as posições já calculadas dos anéis (concentric_rings). O resultado é idêntico ao do vanilla.
 */
public final class StructureProximity {
    /** Proteção contra ciclos de exclusão (A exclui B que exclui A): além disso, "nada por perto". */
    private static final int MAX_DEPTH = 6;
    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    public static boolean hasStructureNear(ChunkGeneratorStructureState state, Holder<StructureSet> set, int x, int z,
                                           int range) {
        if (!set.isBound()) return false;
        int[] depth = DEPTH.get();
        if (depth[0] >= MAX_DEPTH) return false;
        depth[0]++;
        try {
            StructurePlacement placement = set.value().placement();
            if (placement instanceof RandomSpreadStructurePlacement spread && hasGridLayout(spread)) {
                return nearOnGrid(state, spread, x, z, range);
            }
            if (placement instanceof ConcentricRingsStructurePlacement rings) {
                List<ChunkPos> ring = state.getRingPositionsFor(rings);
                if (ring == null) return false;
                for (ChunkPos p : ring) {
                    if (Math.abs(p.x - x) <= range && Math.abs(p.z - z) <= range
                            && rings.isStructureChunk(state, p.x, p.z)) {
                        return true;
                    }
                }
                return false;
            }
            return state.hasStructureChunkInRange(set, x, z, range);
        } finally {
            depth[0]--;
        }
    }

    /** Só confiamos no atalho da grade para as classes cujo sorteio é o do vanilla (as nossas e a original). */
    private static boolean hasGridLayout(RandomSpreadStructurePlacement p) {
        return p.getClass() == RandomSpreadStructurePlacement.class || p instanceof RandomSpreadMultiPlacement;
    }

    private static boolean nearOnGrid(ChunkGeneratorStructureState state, RandomSpreadStructurePlacement p, int x, int z,
                                      int range) {
        int spacing = p.spacing();
        long seed = state.getLevelSeed();
        int rx0 = Math.floorDiv(x - range, spacing);
        int rx1 = Math.floorDiv(x + range, spacing);
        int rz0 = Math.floorDiv(z - range, spacing);
        int rz1 = Math.floorDiv(z + range, spacing);
        for (int rx = rx0; rx <= rx1; rx++) {
            for (int rz = rz0; rz <= rz1; rz++) {
                ChunkPos c = p.getPotentialStructureChunk(seed, rx * spacing, rz * spacing);
                if (Math.abs(c.x - x) <= range && Math.abs(c.z - z) <= range && p.isStructureChunk(state, c.x, c.z)) {
                    return true;
                }
            }
        }
        return false;
    }

    private StructureProximity() {}
}
