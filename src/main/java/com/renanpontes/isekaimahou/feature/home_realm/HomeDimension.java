package com.renanpontes.isekaimahou.feature.home_realm;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

/**
 * Geometria da Dimensão Casa ({@code isekai_mahou:home_realm}): dimensão vazia (só ar) onde cada jogador que comprou a
 * Chave do Lar ganha um <b>terreno</b> — uma ilha flutuante com uma casinha pronta, horta, lago e espaço livre para
 * construir. Os terrenos ficam numa grade ({@link #GRID} por linha, {@link #SPACING} blocos entre centros), então um
 * nunca encosta no outro. A ilha é construída uma única vez ({@link #layout}); depois disso o módulo nunca mais mexe
 * nos blocos (o que o jogador constrói é dele).
 */
public final class HomeDimension {
    public static final ResourceKey<Level> KEY = ResourceKey.create(Registries.DIMENSION, IsekaiMahou.id("home_realm"));

    /** Distância entre centros de terrenos e quantos por linha da grade. */
    public static final int SPACING = 512, GRID = 64;
    /** Y do gramado (o jogador pisa em GROUND_Y + 1). Acima do horizonte do céu, para não escurecer a tela. */
    public static final int GROUND_Y = 80;
    /** Metade do lado do terreno onde dá para construir (quadrado de 129×129, em qualquer altura). */
    public static final int BUILD_RADIUS = 64;
    /** Raio médio da ilha inicial. */
    private static final int ISLAND_RADIUS = 28;
    /** Abaixo disto o jogador "caiu no vazio" e é levado de volta para a chegada. */
    public static final int VOID_Y = 24;

    @Nullable
    public static ServerLevel level(MinecraftServer server) {
        return server.getLevel(KEY);
    }

    public static boolean is(@Nullable Level level) {
        return level != null && level.dimension() == KEY;
    }

    /** Bloco de grama no centro do terreno. */
    public static BlockPos center(int index) {
        return new BlockPos((index % GRID) * SPACING + SPACING / 2, GROUND_Y, (index / GRID) * SPACING + SPACING / 2);
    }

    /** Terreno dono da coluna (x, z), ou -1 fora da grade. */
    public static int indexAt(double x, double z) {
        int col = Math.floorDiv(Mth.floor(x), SPACING), row = Math.floorDiv(Mth.floor(z), SPACING);
        if (col < 0 || col >= GRID || row < 0) return -1;
        return row * GRID + col;
    }

    /** A coluna está dentro da área construível do terreno? */
    public static boolean inPlot(int index, double x, double z) {
        if (index < 0) return false;
        BlockPos c = center(index);
        return Math.abs(x - (c.getX() + 0.5)) <= BUILD_RADIUS + 0.5 && Math.abs(z - (c.getZ() + 0.5)) <= BUILD_RADIUS + 0.5;
    }

    public static boolean inPlot(int index, BlockPos pos) {
        if (index < 0) return false;
        BlockPos c = center(index);
        return Math.abs(pos.getX() - c.getX()) <= BUILD_RADIUS && Math.abs(pos.getZ() - c.getZ()) <= BUILD_RADIUS;
    }

    /** Ponto de chegada: o círculo de pedra na frente da casa, olhando para a porta. */
    public static Vec3 arrival(int index) {
        BlockPos c = center(index);
        return new Vec3(c.getX() + 0.5, GROUND_Y + 1, c.getZ() + 8.5);
    }

    /** Direção (yaw) de quem chega: virado para o norte, onde está a casa. */
    public static final float ARRIVAL_YAW = 180F;

    /** Chunks ocupados pela ilha inicial (seguros carregados durante a construção). */
    public static List<ChunkPos> chunks(int index) {
        BlockPos c = center(index);
        int h = ISLAND_RADIUS + 6;
        List<ChunkPos> out = new ArrayList<>();
        for (int cx = (c.getX() - h) >> 4; cx <= (c.getX() + h) >> 4; cx++) {
            for (int cz = (c.getZ() - h) >> 4; cz <= (c.getZ() + h) >> 4; cz++) out.add(new ChunkPos(cx, cz));
        }
        return out;
    }

    /**
     * Um bloco a colocar. {@code connect} = antes de colocar, o estado é ajustado aos vizinhos (vidraças se ligam às
     * paredes). O resto vai exatamente como está na lista, sem avisar vizinhos — bem mais barato e sem surpresas.
     */
    public record Placement(BlockPos pos, BlockState state, boolean connect) {}

    /** Onde nascem as árvores (posição relativa ao centro) — plantadas depois que a ilha existe. */
    public static final int[][] TREES = { { -12, 3 }, { 13, 9 }, { -10, -14 } };

    // =====================================================================================
    //  Desenho da ilha
    // =====================================================================================
    /** Lista completa de blocos do terreno inicial, na ordem em que devem ser colocados (apoios antes do que se apoia). */
    public static List<Placement> layout(int index) {
        BlockPos c = center(index);
        List<Placement> out = new ArrayList<>(22000);
        Set<Long> reserved = new HashSet<>();   // colunas do gramado que não recebem florzinhas (casa, caminho, horta...)
        island(out, c, index);
        house(out, c, reserved);
        yard(out, c, reserved);
        plants(out, c, index, reserved);
        return out;
    }

    private static long col(int dx, int dz) {
        return ((long) dx << 32) ^ (dz & 0xFFFFFFFFL);
    }

    /** Raio da borda da ilha no ângulo dado: um círculo levemente irregular, diferente em cada terreno. */
    private static double edge(double ang, int index) {
        return ISLAND_RADIUS + 2.2 * Math.sin(3 * ang + index * 1.7) + 1.4 * Math.sin(5 * ang + index * 0.9);
    }

    private static boolean onIsland(int dx, int dz, int index) {
        return Math.sqrt(dx * dx + dz * dz) <= edge(Math.atan2(dz, dx), index);
    }

    private static int hash(int a, int b, int salt) {
        int h = a * 73428767 ^ b * 912931 ^ salt * 19349663;
        h ^= h >>> 13;
        h *= 1274126177;
        return (h ^ (h >>> 16)) & 0x7FFFFFFF;
    }

    /** Ilha flutuante: grama em cima, terra, e um cone de pedra por baixo. */
    private static void island(List<Placement> out, BlockPos c, int index) {
        int reach = ISLAND_RADIUS + 4;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                double r = edge(Math.atan2(dz, dx), index);
                if (d > r) continue;
                double t = d / r;
                int depth = 2 + (int) (12 * (1 - t * t)) + hash(dx, dz, index) % 2;
                for (int y = -depth; y <= 0; y++) {
                    BlockState s;
                    if (y == 0) s = Blocks.GRASS_BLOCK.defaultBlockState();
                    else if (y >= -3) s = Blocks.DIRT.defaultBlockState();
                    else {
                        int h = hash(dx * 31 + y, dz, index + 7) % 100;
                        s = h < 6 ? Blocks.ANDESITE.defaultBlockState() : h < 10 ? Blocks.COBBLESTONE.defaultBlockState()
                                : h < 12 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.STONE.defaultBlockState();
                    }
                    out.add(new Placement(c.offset(dx, y, dz), s, false));
                }
            }
        }
    }

    /** Casinha de 9×7: base de pedra, cantos de tronco, telhado de duas águas, e por dentro cama, baú, bancada e fornalha. */
    private static void house(List<Placement> out, BlockPos c, Set<Long> reserved) {
        final int x0 = -4, x1 = 4, z0 = -9, z1 = -3;
        // Piso e base
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean rim = x == x0 || x == x1 || z == z0 || z == z1;
                put(out, c, x, 0, z, rim ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.SPRUCE_PLANKS.defaultBlockState());
            }
        }
        for (int x = x0 - 1; x <= x1 + 1; x++) for (int z = z0 - 1; z <= z1 + 1; z++) reserved.add(col(x, z));
        // Paredes (y 1..3) e a viga de cima (y 4)
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean rim = x == x0 || x == x1 || z == z0 || z == z1;
                if (!rim) continue;
                boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
                for (int y = 1; y <= 3; y++) {
                    put(out, c, x, y, z, corner ? Blocks.SPRUCE_LOG.defaultBlockState() : Blocks.OAK_PLANKS.defaultBlockState());
                }
                Direction.Axis axis = corner ? Direction.Axis.Y : (z == z0 || z == z1) ? Direction.Axis.X : Direction.Axis.Z;
                put(out, c, x, 4, z, Blocks.SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis));
            }
        }
        // Empenas (paredes leste/oeste sob o telhado)
        for (int x : new int[] { x0, x1 }) {
            for (int z = -8; z <= -4; z++) put(out, c, x, 5, z, Blocks.OAK_PLANKS.defaultBlockState());
            for (int z = -7; z <= -5; z++) put(out, c, x, 6, z, Blocks.OAK_PLANKS.defaultBlockState());
            put(out, c, x, 7, -6, Blocks.OAK_PLANKS.defaultBlockState());
        }
        // Telhado: duas águas subindo para a cumeeira em z = -6, com beiral de 1 bloco
        BlockState south = Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
        BlockState north = Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH);
        for (int x = x0 - 1; x <= x1 + 1; x++) {
            for (int i = 0; i < 4; i++) {
                put(out, c, x, 4 + i, -2 - i, south);
                put(out, c, x, 4 + i, -10 + i, north);
            }
            put(out, c, x, 8, -6, Blocks.DARK_OAK_SLAB.defaultBlockState());
        }
        // Janelas (vidraças: ligadas às paredes na hora de colocar) e a porta virada para o sul
        BlockState pane = Blocks.GLASS_PANE.defaultBlockState();
        for (int x : new int[] { -2, 2 }) {
            link(out, c, x, 2, z1, pane);
            link(out, c, x, 2, z0, pane);
        }
        link(out, c, 0, 2, z0, pane);
        for (int x : new int[] { x0, x1 }) {
            link(out, c, x, 2, -6, pane);
            link(out, c, x, 5, -6, pane);
        }
        BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
        put(out, c, 0, 1, z1, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        put(out, c, 0, 2, z1, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        // Móveis
        BlockState bed = Blocks.LIGHT_BLUE_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
        put(out, c, -3, 1, -7, bed.setValue(BedBlock.PART, BedPart.FOOT));
        put(out, c, -3, 1, -8, bed.setValue(BedBlock.PART, BedPart.HEAD));
        put(out, c, -2, 1, -8, Blocks.BOOKSHELF.defaultBlockState());
        BlockState chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH);
        put(out, c, 3, 1, -8, chest.setValue(ChestBlock.TYPE, ChestType.LEFT));
        put(out, c, 2, 1, -8, chest.setValue(ChestBlock.TYPE, ChestType.RIGHT));
        put(out, c, -3, 1, -4, Blocks.CRAFTING_TABLE.defaultBlockState());
        put(out, c, -2, 1, -4, Blocks.FURNACE.defaultBlockState().setValue(FurnaceBlock.FACING, Direction.NORTH));
        put(out, c, 3, 1, -4, Blocks.BARREL.defaultBlockState());
        for (int x = -1; x <= 1; x++) for (int z = -6; z <= -5; z++) put(out, c, x, 1, z, Blocks.LIGHT_BLUE_CARPET.defaultBlockState());
        // Luz: lanterna pendurada na cumeeira e duas no beiral da frente
        BlockState hanging = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
        put(out, c, 0, 7, -6, hanging);
        put(out, c, -2, 3, -2, hanging);
        put(out, c, 2, 3, -2, hanging);
    }

    /** Quintal: caminho até o círculo de chegada, postes de luz, horta com água e um laguinho. */
    private static void yard(List<Placement> out, BlockPos c, Set<Long> reserved) {
        // Caminho da porta até a chegada
        for (int z = -2; z <= 5; z++) {
            put(out, c, 0, 0, z, Blocks.DIRT_PATH.defaultBlockState());
            reserved.add(col(0, z));
        }
        // Círculo de chegada
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > 3.3) continue;
                reserved.add(col(dx, 8 + dz));
                if (d > 2.6) continue;
                BlockState s = d < 0.5 ? Blocks.CHISELED_STONE_BRICKS.defaultBlockState()
                        : d < 1.6 ? Blocks.POLISHED_ANDESITE.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
                put(out, c, dx, 0, 8 + dz, s);
            }
        }
        // Postes de luz ao lado da chegada
        for (int x : new int[] { -4, 4 }) {
            reserved.add(col(x, 8));
            put(out, c, x, 1, 8, Blocks.SPRUCE_FENCE.defaultBlockState());
            put(out, c, x, 2, 8, Blocks.SPRUCE_FENCE.defaultBlockState());
            put(out, c, x, 3, 8, Blocks.LANTERN.defaultBlockState());
        }
        // Horta: duas faixas de terra arada de cada lado de um canal de água, já com trigo plantado
        for (int z = -7; z <= -3; z++) {
            put(out, c, 10, 0, z, Blocks.WATER.defaultBlockState());
            reserved.add(col(10, z));
            for (int x : new int[] { 8, 9, 11, 12 }) {
                reserved.add(col(x, z));
                put(out, c, x, 0, z, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
                int age = 1 + hash(x, z, 3) % 4;
                put(out, c, x, 1, z, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, age));
            }
        }
        // Laguinho
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz * 1.4);
                if (d > 3.4) continue;
                reserved.add(col(-9 + dx, 10 + dz));
                if (d > 2.4) continue;
                put(out, c, -9 + dx, 0, 10 + dz, Blocks.WATER.defaultBlockState());
                put(out, c, -9 + dx, -1, 10 + dz, d < 1.2 ? Blocks.CLAY.defaultBlockState() : Blocks.SAND.defaultBlockState());
            }
        }
        put(out, c, -9, 1, 10, Blocks.LILY_PAD.defaultBlockState());
        put(out, c, -8, 1, 11, Blocks.LILY_PAD.defaultBlockState());
        for (int[] t : TREES) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) reserved.add(col(t[0] + dx, t[1] + dz));
    }

    /** Capim e flores espalhados pelo gramado livre. */
    private static void plants(List<Placement> out, BlockPos c, int index, Set<Long> reserved) {
        BlockState[] flowers = { Blocks.DANDELION.defaultBlockState(), Blocks.POPPY.defaultBlockState(),
                Blocks.OXEYE_DAISY.defaultBlockState(), Blocks.CORNFLOWER.defaultBlockState(), Blocks.AZURE_BLUET.defaultBlockState() };
        int reach = ISLAND_RADIUS + 4;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                if (reserved.contains(col(dx, dz)) || !onIsland(dx, dz, index)) continue;
                int h = hash(dx, dz, index + 41) % 100;
                if (h < 9) put(out, c, dx, 1, dz, Blocks.SHORT_GRASS.defaultBlockState());
                else if (h < 12) put(out, c, dx, 1, dz, flowers[hash(dz, dx, index) % flowers.length]);
            }
        }
    }

    private static void put(List<Placement> out, BlockPos c, int dx, int dy, int dz, BlockState s) {
        out.add(new Placement(c.offset(dx, dy, dz), s, false));
    }

    private static void link(List<Placement> out, BlockPos c, int dx, int dy, int dz, BlockState s) {
        out.add(new Placement(c.offset(dx, dy, dz), s, true));
    }

    private HomeDimension() {}
}
