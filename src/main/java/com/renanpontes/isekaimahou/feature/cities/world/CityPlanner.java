package com.renanpontes.isekaimahou.feature.cities.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * Planta da cidade: decide o que vai em cada quarteirão (os prédios obrigatórios + casas) e monta a lista de peças
 * (chão primeiro, depois praça, castelo, lotes, portões e torres). Tudo sai do mesmo "seed", então a cidade é
 * igual sempre que o chunk de início é recriado.
 */
final class CityPlanner {
    static final int S_LOT = 11;
    static final int L_LOT = 22;
    /** Prédios grandes (22x22) e pequenos (11x11) obrigatórios; o resto dos lotes pequenos recebe casas. */
    private static final String[] CITY_LARGE = {"guild_hall", "tavern", "market", "academy", "church", "barracks",
            "manor"};
    private static final String[] CAPITAL_LARGE = {"guild_hall", "tavern", "tavern", "market", "market", "academy",
            "church", "barracks", "noble_manor", "noble_manor", "park", "park"};
    private static final String[] CITY_SMALL = {"smithy", "alchemy", "jeweler", "tailor", "alley", "stable", "garden"};
    private static final String[] CAPITAL_SMALL = {"smithy", "smithy", "alchemy", "jeweler", "tailor", "alley",
            "stable", "stable", "garden", "garden"};
    private static final String[] FILLERS = {"house_a", "house_b", "house_c", "house_d", "well", "garden"};
    private static final int[] FILLER_WEIGHTS = {4, 4, 2, 4, 1, 1};
    /** Peças cujo piso de rua não fica em y=0 do .nbt (o beco tem porão): igual a GROUND_OFFSET do gerador. */
    private static final Map<String, Integer> GROUND_OFFSET = Map.of("alley", 5);
    /** Chance de uma torre lateral ter um guarda em cima. */
    private static final float TOWER_GUARD_CHANCE = 0.35F;

    record Lot(String template, int x0, int z0, int x1, int z1, Direction facing, boolean mirror) {}

    private CityPlanner() {}

    static void build(StructurePiecesBuilder builder, StructureTemplateManager manager, CityGeometry geo, CityFrame frame,
                      CityTerrace terrace, CityPalette palette, int gates, int clearHeight, long seed) {
        builder.addPiece(new CityGroundPiece(geo, frame, terrace, palette, gates, clearHeight, seed));
        RandomSource r = RandomSource.create(seed);
        Placer p = new Placer(builder, manager, frame, terrace, palette);
        int pz = CityGeometry.PLAZA;
        p.add(geo.capital ? "plaza_capital" : "plaza", -pz, -pz, pz, pz, Direction.SOUTH, false);
        if (geo.capital) p.add("castle", -pz, -geo.blockEnd(3), pz, -geo.blockStart(2), Direction.SOUTH, false);
        for (Lot lot : plan(geo, r)) p.add(lot.template, lot.x0, lot.z0, lot.x1, lot.z1, lot.facing, lot.mirror);
        walls(p, geo, gates, r);
    }

    /**
     * Frentes dos lotes (e do castelo) desta cidade, para a peça do chão pôr degraus na rua quando o terraço do
     * quarteirão ficou mais alto ou mais baixo que ela. Refaz a mesma planta de {@link #build} (mesmo seed, mesma ordem
     * de sorteio), então sai idêntica em qualquer chunk.
     */
    static List<Lot> fronts(CityGeometry geo, long seed) {
        List<Lot> lots = new ArrayList<>(plan(geo, RandomSource.create(seed)));
        int pz = CityGeometry.PLAZA;
        if (geo.capital) lots.add(new Lot("castle", -pz, -geo.blockEnd(3), pz, -geo.blockStart(2), Direction.SOUTH, false));
        return lots;
    }

    // ------------------------------------------------------------------------------------------ quarteirões
    static List<Lot> plan(CityGeometry geo, RandomSource r) {
        int n = geo.rings;
        List<int[]> blocks = new ArrayList<>();
        for (int i = -n; i <= n; i++) {
            for (int j = -n; j <= n; j++) {
                if (i == 0 || j == 0) continue;
                int ai = Math.abs(i);
                int aj = Math.abs(j);
                if (ai == 1 && aj == 1) continue;                    // praça
                if (geo.capital && ai == 1 && j <= -2) continue;     // castelo
                blocks.add(new int[] {i, j});
            }
        }
        shuffle(blocks, r);
        String[] large = geo.capital ? CAPITAL_LARGE : CITY_LARGE;
        String[] small = geo.capital ? CAPITAL_SMALL : CITY_SMALL;
        List<Lot> lots = new ArrayList<>();
        // A Guilda fica de frente para a praça (um dos quarteirões encostados nela).
        int guild = -1;
        for (int k = 0; k < blocks.size() && guild < 0; k++) {
            int ai = Math.abs(blocks.get(k)[0]);
            int aj = Math.abs(blocks.get(k)[1]);
            if ((ai == 1 && aj == 2) || (ai == 2 && aj == 1)) guild = k;
        }
        if (guild > 0) blocks.add(0, blocks.remove(guild));
        int used = Math.min(large.length, blocks.size());
        for (int k = 0; k < used; k++) {
            int[] b = blocks.get(k);
            int[] xr = range(geo, b[0]);
            int[] zr = range(geo, b[1]);
            lots.add(new Lot(large[k], xr[0], zr[0], xr[1], zr[1], largeFacing(b[0], b[1], r), r.nextBoolean()));
        }
        // O resto vira 4 lotes pequenos por quarteirão, cada um de frente para a rua do seu lado.
        List<Lot> smallLots = new ArrayList<>();
        for (int k = used; k < blocks.size(); k++) {
            int i = blocks.get(k)[0];
            int j = blocks.get(k)[1];
            int[] xr = range(geo, i);
            int[] zr = range(geo, j);
            boolean axisX = Math.abs(i) == 1 || (Math.abs(j) != 1 && r.nextBoolean());
            for (int hx = 0; hx < 2; hx++) {
                for (int hz = 0; hz < 2; hz++) {
                    int x0 = xr[0] + hx * S_LOT;
                    int z0 = zr[0] + hz * S_LOT;
                    // Cada metade do quarteirão dá para a rua do seu lado (oeste/leste ou norte/sul).
                    Direction f = axisX ? (hx == 0 ? Direction.WEST : Direction.EAST)
                            : (hz == 0 ? Direction.NORTH : Direction.SOUTH);
                    smallLots.add(new Lot(null, x0, z0, x0 + S_LOT - 1, z0 + S_LOT - 1, f, r.nextBoolean()));
                }
            }
        }
        shuffle(smallLots, r);
        for (int k = 0; k < smallLots.size(); k++) {
            Lot l = smallLots.get(k);
            String t = k < small.length ? small[k] : weightedFiller(r);
            lots.add(new Lot(t, l.x0, l.z0, l.x1, l.z1, l.facing, l.mirror));
        }
        return lots;
    }

    /** Faixa [mín, máx] de um quarteirão pelo índice com sinal (1 = o mais perto do centro). */
    private static int[] range(CityGeometry geo, int idx) {
        int a = Math.abs(idx);
        return idx > 0 ? new int[] {geo.blockStart(a), geo.blockEnd(a)} : new int[] {-geo.blockEnd(a), -geo.blockStart(a)};
    }

    /** Prédio grande: de frente para a praça se encostar nela, senão para a avenida, senão para a rua de dentro. */
    private static Direction largeFacing(int i, int j, RandomSource r) {
        int ai = Math.abs(i);
        int aj = Math.abs(j);
        Direction towardX = i > 0 ? Direction.WEST : Direction.EAST;
        Direction towardZ = j > 0 ? Direction.NORTH : Direction.SOUTH;
        if (ai == 1 && aj == 2) return towardZ;
        if (ai == 2 && aj == 1) return towardX;
        if (ai == 1) return towardX;
        if (aj == 1) return towardZ;
        return r.nextBoolean() ? towardX : towardZ;
    }

    private static String weightedFiller(RandomSource r) {
        int total = 0;
        for (int w : FILLER_WEIGHTS) total += w;
        int v = r.nextInt(total);
        for (int i = 0; i < FILLERS.length; i++) {
            v -= FILLER_WEIGHTS[i];
            if (v < 0) return FILLERS[i];
        }
        return FILLERS[0];
    }

    private static <T> void shuffle(List<T> list, RandomSource r) {
        for (int i = list.size() - 1; i > 0; i--) {
            int k = r.nextInt(i + 1);
            T tmp = list.get(i);
            list.set(i, list.get(k));
            list.set(k, tmp);
        }
    }

    // ------------------------------------------------------------------------------------------ muralha
    /** Portões (ou torre do meio, sem portão), torres nas pontas das ruas e as 4 torres de canto. */
    private static void walls(Placer p, CityGeometry geo, int gates, RandomSource r) {
        int[][] sides = {{CityGeometry.GATE_N, 0}, {CityGeometry.GATE_E, 1}, {CityGeometry.GATE_S, 2}, {CityGeometry.GATE_W, 3}};
        Direction[] out = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        for (int[] side : sides) {
            Direction facing = out[side[1]];
            if ((gates & side[0]) != 0) {
                sideRect(p, geo, facing, -CityGeometry.GATE_HALF, CityGeometry.GATE_HALF, "gatehouse");
            } else {
                sideRect(p, geo, facing, -CityGeometry.TOWER_HALF, CityGeometry.TOWER_HALF, towerKind(r));
            }
            for (int c : geo.towerCenters()) {
                for (int sign : new int[] {-1, 1}) {
                    int a = sign * c;
                    sideRect(p, geo, facing, a - CityGeometry.TOWER_HALF, a + CityGeometry.TOWER_HALF, towerKind(r));
                }
            }
        }
        int in = geo.wallIn;
        int to = geo.towerOut;
        p.add("tower_corner", in, in, to, to, Direction.SOUTH, false);        // sudeste
        p.add("tower_corner", in, -to, to, -in, Direction.EAST, false);       // nordeste
        p.add("tower_corner", -to, -to, -in, -in, Direction.NORTH, false);    // noroeste
        p.add("tower_corner", -to, in, -in, to, Direction.WEST, false);       // sudoeste
    }

    private static String towerKind(RandomSource r) {
        return r.nextFloat() < TOWER_GUARD_CHANCE ? "tower_guard" : "tower";
    }

    /** Peça encostada na muralha de um lado: [a0..a1] ao longo dela, de wallIn até towerOut para fora. */
    private static void sideRect(Placer p, CityGeometry geo, Direction side, int a0, int a1, String template) {
        int in = geo.wallIn;
        int to = geo.towerOut;
        switch (side) {
            case SOUTH -> p.add(template, a0, in, a1, to, side, false);
            case NORTH -> p.add(template, a0, -to, a1, -in, side, false);
            case EAST -> p.add(template, in, a0, to, a1, side, false);
            default -> p.add(template, -to, a0, -in, a1, side, false);
        }
    }

    /** Põe templates em retângulos do plano canônico, já girados para o mundo. */
    private record Placer(StructurePiecesBuilder builder, StructureTemplateManager manager, CityFrame frame,
                          CityTerrace terrace, CityPalette palette) {

        void add(String name, int x0, int z0, int x1, int z1, Direction facing, boolean mirror) {
            ResourceLocation id = IsekaiMahou.id("city/" + palette.id + "/" + name);
            StructureTemplate tpl = manager.getOrCreate(id);
            Vec3i size = tpl.getSize();
            if (size.getX() <= 0 || size.getZ() <= 0) {
                IsekaiMahou.LOGGER.warn("Cidade: template {} não encontrado; lote ficou vazio", id);
                return;
            }
            Rotation rot = rotationFor(frame.toWorld(facing));
            Mirror mir = mirror ? Mirror.FRONT_BACK : Mirror.NONE;
            int ax = frame.worldX(x0, z0);
            int az = frame.worldZ(x0, z0);
            int bx = frame.worldX(x1, z1);
            int bz = frame.worldZ(x1, z1);
            int minX = Math.min(ax, bx);
            int minZ = Math.min(az, bz);
            int spanX = Math.abs(bx - ax) + 1;
            int spanZ = Math.abs(bz - az) + 1;
            BoundingBox bb = tpl.getBoundingBox(new StructurePlaceSettings().setRotation(rot).setMirror(mir), BlockPos.ZERO);
            if (bb.getXSpan() > spanX || bb.getZSpan() > spanZ) {
                IsekaiMahou.LOGGER.warn("Cidade: template {} ({}x{}) maior que o lote ({}x{})", id, bb.getXSpan(),
                        bb.getZSpan(), spanX, spanZ);
            }
            // Centralizado no lote (os templates têm o tamanho exato do lote; isso só protege contra .nbt errado).
            int px = minX + (spanX - bb.getXSpan()) / 2 - bb.minX();
            int pz = minZ + (spanZ - bb.getZSpan()) / 2 - bb.minZ();
            // Piso do terraço da peça (quarteirão, praça, castelo, torre/portão): a mesma conta que a peça do chão usa
            // para aplainar essas colunas. Qualquer coluna do retângulo serve — o canto (x0,z0) está sempre dentro.
            int py = terrace.surface(x0, z0) - GROUND_OFFSET.getOrDefault(name, 0);
            builder.addPiece(new CityBuildingPiece(manager, id, new BlockPos(px, py, pz), rot, mir, palette));
        }

        /** A frente dos templates é o lado sul: gira o sul para a direção pedida. */
        private static Rotation rotationFor(Direction facing) {
            return switch (facing) {
                case WEST -> Rotation.CLOCKWISE_90;
                case NORTH -> Rotation.CLOCKWISE_180;
                case EAST -> Rotation.COUNTERCLOCKWISE_90;
                default -> Rotation.NONE;
            };
        }
    }
}
