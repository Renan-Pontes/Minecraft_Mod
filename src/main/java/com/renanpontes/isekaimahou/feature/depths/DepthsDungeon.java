package com.renanpontes.isekaimahou.feature.depths;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * A Masmorra em Camadas ({@code depths_dungeon}): peças empilhadas na vertical — entrada (superfície), andares 1–4 e o
 * Ninho do Verme-Rei. O "andar" de uma posição é a ordem da peça de cima para baixo (0 = entrada, 5 = ninho).
 */
public final class DepthsDungeon {
    public static final ResourceKey<Structure> KEY = ResourceKey.create(Registries.STRUCTURE, IsekaiMahou.id("depths_dungeon"));
    public static final TagKey<Structure> TAG = TagKey.create(Registries.STRUCTURE, IsekaiMahou.id("depths_dungeon"));
    public static final int FLOORS = 4;
    public static final int LAIR = FLOORS + 1;

    /** Peças da masmorra que contém {@code pos}, ordenadas de cima para baixo; vazio se não estiver numa. */
    public static List<StructurePiece> piecesAt(ServerLevel level, BlockPos pos) {
        Structure s = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(KEY);
        if (s == null) return List.of();
        StructureStart start = level.structureManager().getStructureWithPieceAt(pos, s);
        return start.isValid() ? sorted(start) : List.of();
    }

    /** Início (StructureStart) da masmorra que tem uma peça em {@code pos}, ou null. */
    @Nullable
    public static StructureStart startWithPieceAt(ServerLevel level, BlockPos pos) {
        Structure s = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(KEY);
        if (s == null) return null;
        StructureStart start = level.structureManager().getStructureWithPieceAt(pos, s);
        return start.isValid() ? start : null;
    }

    /** Andar de {@code pos} dentro das peças já ordenadas (0 entrada … 5 ninho) ou -1. */
    public static int floorIn(List<StructurePiece> pieces, BlockPos pos) {
        for (int i = 0; i < pieces.size(); i++) {
            if (pieces.get(i).getBoundingBox().isInside(pos)) return i;
        }
        return -1;
    }

    /** Andar da posição (0 entrada … 5 ninho) ou -1 fora da masmorra. */
    public static int floorAt(ServerLevel level, BlockPos pos) {
        List<StructurePiece> pieces = piecesAt(level, pos);
        for (int i = 0; i < pieces.size(); i++) {
            if (pieces.get(i).getBoundingBox().isInside(pos)) return i;
        }
        return -1;
    }

    /** Início da masmorra cujo início está no chunk de {@code found} (resultado de {@code findNearestMapStructure}). */
    @Nullable
    public static StructureStart startAt(ServerLevel level, BlockPos found) {
        Structure s = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(KEY);
        if (s == null) return null;
        StructureStart start = level.structureManager().getStartForStructure(SectionPos.of(found), s,
                level.getChunk(found.getX() >> 4, found.getZ() >> 4, ChunkStatus.STRUCTURE_STARTS));
        return start != null && start.isValid() ? start : null;
    }

    public static List<StructurePiece> sorted(StructureStart start) {
        List<StructurePiece> list = new ArrayList<>(start.getPieces());
        list.sort(Comparator.comparingInt((StructurePiece p) -> p.getBoundingBox().minY()).reversed());
        return list;
    }

    /** Centro do piso da peça (1 bloco acima da base). */
    public static BlockPos floorCenter(StructurePiece piece) {
        BoundingBox bb = piece.getBoundingBox();
        return new BlockPos(bb.getCenter().getX(), bb.minY() + 1, bb.getCenter().getZ());
    }

    private DepthsDungeon() {}
}
