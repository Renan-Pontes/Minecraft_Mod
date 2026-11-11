package com.renanpontes.isekaimahou.feature.settlements.build;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.feature.settlements.SettlementStyle;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Planta baixa da cidade do jogador: grade 5×5 de terrenos de 16 blocos em volta do coração (o do meio é a praça),
 * muralha quadrada a 42 blocos com torres nos cantos e 4 portões, território de ±46 blocos.
 * <pre>
 *   níveis que liberam cada terreno:   4 3 3 3 4
 *                                      3 2 1 2 3
 *                                      3 1 P 1 3
 *                                      3 2 1 2 3
 *                                      4 3 3 3 4
 * </pre>
 * Os prédios giram para a entrada (lado +Z do molde) olhar para a praça.
 */
public final class CityLayout {
    public static final int CELL = 16;
    public static final int GRID = 2;
    public static final int WALL = 42;
    public static final int TERRITORY = 46;
    public static final int BOX_DOWN = 24;
    public static final int BOX_UP = 56;
    /** Distância mínima entre o coração novo e o centro de outra cidade. */
    public static final int MIN_CITY_DISTANCE = 220;
    public static final int CELLS = 25;
    public static final int CENTER_CELL = 12;

    public static int cellIndex(int i, int j) {
        return (i + GRID) * 5 + (j + GRID);
    }

    public static int cellI(int cell) {
        return cell / 5 - GRID;
    }

    public static int cellJ(int cell) {
        return cell % 5 - GRID;
    }

    /** Nível da cidade que libera o terreno. */
    public static int unlockLevel(int cell) {
        int ai = Math.abs(cellI(cell)), aj = Math.abs(cellJ(cell));
        if (ai + aj == 1) return 1;
        if (ai == 1 && aj == 1) return 2;
        if (ai == 2 && aj == 2) return 4;
        return 3;
    }

    /** Terrenos liberados neste nível, do mais perto da praça para o mais longe. */
    public static List<Integer> cellsFor(int level) {
        List<Integer> out = new ArrayList<>();
        for (int ring = 1; ring <= 4; ring++) {
            for (int c = 0; c < CELLS; c++) {
                if (c == CENTER_CELL) continue;
                int u = unlockLevel(c);
                if (u == ring && u <= level) out.add(c);
            }
        }
        return out;
    }

    public static BlockPos cellCenter(BlockPos heart, int cell) {
        return heart.offset(cellI(cell) * CELL, 0, cellJ(cell) * CELL);
    }

    /** Rotação para a entrada do prédio (lado +Z) olhar para a praça. */
    public static Rotation facingHeart(int cell) {
        int i = cellI(cell), j = cellJ(cell);
        if (Math.abs(i) >= Math.abs(j) && i != 0) {
            return i > 0 ? Rotation.CLOCKWISE_90 : Rotation.COUNTERCLOCKWISE_90;   // olhar para oeste / leste
        }
        return j > 0 ? Rotation.CLOCKWISE_180 : Rotation.NONE;                     // olhar para norte / sul
    }

    public static BoundingBox territory(BlockPos heart) {
        return new BoundingBox(heart.getX() - TERRITORY, heart.getY() - BOX_DOWN, heart.getZ() - TERRITORY,
                heart.getX() + TERRITORY, heart.getY() + BOX_UP, heart.getZ() + TERRITORY);
    }

    /** Peças da muralha: 4 torres, 32 trechos e 4 portões, nesta ordem (as torres primeiro firmam os cantos). */
    public static List<Project.Piece> wallPieces(SettlementStyle style, BlockPos heart) {
        List<Project.Piece> out = new ArrayList<>();
        int hx = heart.getX(), hz = heart.getZ();
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                out.add(new Project.Piece(Blueprint.template(style, "wall_tower"), hx + sx * WALL, hz + sz * WALL,
                        Rotation.NONE, Project.Piece.TOWER, Integer.MIN_VALUE));
            }
        }
        // trechos de 9 blocos entre a torre (±40) e o portão (±3): centros a 8, 17, 26, 35
        for (int side = 0; side < 4; side++) {
            for (int s = -1; s <= 1; s += 2) {
                for (int k = 0; k < 4; k++) {
                    int along = s * (8 + 9 * k);
                    out.add(sidePiece(style, heart, side, along, "wall_segment", Project.Piece.WALL));
                }
            }
        }
        for (int side = 0; side < 4; side++) out.add(sidePiece(style, heart, side, 0, "wall_gate", Project.Piece.GATE));
        return out;
    }

    /** Lados: 0 norte, 1 sul (peças ao longo de X), 2 oeste, 3 leste (giradas 90°, ao longo de Z). */
    private static Project.Piece sidePiece(SettlementStyle style, BlockPos heart, int side, int along, String piece, int kind) {
        int hx = heart.getX(), hz = heart.getZ();
        return switch (side) {
            case 0 -> new Project.Piece(Blueprint.template(style, piece), hx + along, hz - WALL, Rotation.NONE, kind, Integer.MIN_VALUE);
            case 1 -> new Project.Piece(Blueprint.template(style, piece), hx + along, hz + WALL, Rotation.NONE, kind, Integer.MIN_VALUE);
            case 2 -> new Project.Piece(Blueprint.template(style, piece), hx - WALL, hz + along, Rotation.CLOCKWISE_90, kind, Integer.MIN_VALUE);
            default -> new Project.Piece(Blueprint.template(style, piece), hx + WALL, hz + along, Rotation.CLOCKWISE_90, kind, Integer.MIN_VALUE);
        };
    }

    /** Ponto de chegada dos moradores novos: na frente de um portão (ou na borda da praça, sem muralha). */
    public static BlockPos arrivalPoint(BlockPos heart, int which, boolean hasWall) {
        int d = hasWall ? WALL - 4 : 9;
        return switch (Math.floorMod(which, 4)) {
            case 0 -> heart.offset(0, 0, -d);
            case 1 -> heart.offset(0, 0, d);
            case 2 -> heart.offset(-d, 0, 0);
            default -> heart.offset(d, 0, 0);
        };
    }

    private CityLayout() {}
}
