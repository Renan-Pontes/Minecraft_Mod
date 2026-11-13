package com.renanpontes.isekaimahou.feature.endless_abyss;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Geometria do Abismo Infinito ({@code isekai_mahou:endless_abyss}): dimensão vazia (só ar) onde cada descida ocupa uma
 * "vaga" própria — arena circular centrada em {@code (n·1024+512, 100, 512)}. A arena é construída uma vez por vaga e
 * reaproveitada (fica salva no mundo; ninguém consegue quebrar blocos lá).
 */
public final class AbyssDimension {
    public static final ResourceKey<Level> KEY = ResourceKey.create(Registries.DIMENSION, IsekaiMahou.id("endless_abyss"));

    public static final int SPACING = 1024;
    /** Y do piso (os jogadores ficam em BASE_Y + 1). */
    public static final int BASE_Y = 100;
    /** Raio do piso da arena. */
    public static final int RADIUS = 22;
    /** Altura do muro de pedra e da barreira invisível acima dele (contando do piso). */
    private static final int WALL_HEIGHT = 3, BARRIER_HEIGHT = 14;
    /** Muda quando o desenho da arena muda: vagas antigas são reconstruídas por cima. */
    public static final int LAYOUT_VERSION = 1;

    @Nullable
    public static ServerLevel level(MinecraftServer server) {
        return server.getLevel(KEY);
    }

    public static boolean is(Level level) {
        return level.dimension() == KEY;
    }

    /** Bloco central do piso da arena da vaga. */
    public static BlockPos center(int index) {
        return new BlockPos(index * SPACING + 512, BASE_Y, 512);
    }

    /** Centro da arena onde os jogadores pisam. */
    public static Vec3 centerVec(int index) {
        BlockPos c = center(index);
        return new Vec3(c.getX() + 0.5, c.getY() + 1, c.getZ() + 0.5);
    }

    /** Vaga dona da coluna x. */
    public static int indexAt(double x) {
        return Math.floorDiv(Mth.floor(x), SPACING);
    }

    /** Caixa da arena inteira (piso, muro e a altura útil). */
    public static AABB box(int index) {
        BlockPos c = center(index);
        int h = RADIUS + 4;
        return new AABB(c.getX() - h, c.getY() - 2, c.getZ() - h, c.getX() + h + 1, c.getY() + BARRIER_HEIGHT + 6, c.getZ() + h + 1);
    }

    public static List<ChunkPos> chunks(int index) {
        BlockPos c = center(index);
        int h = RADIUS + 4;
        List<ChunkPos> out = new ArrayList<>();
        for (int cx = (c.getX() - h) >> 4; cx <= (c.getX() + h) >> 4; cx++) {
            for (int cz = (c.getZ() - h) >> 4; cz <= (c.getZ() + h) >> 4; cz++) out.add(new ChunkPos(cx, cz));
        }
        return out;
    }

    /** O ponto está dentro da área de luta (com folga)? Fora disso o módulo puxa jogadores e criaturas de volta. */
    public static boolean inside(int index, Vec3 p, double slack) {
        Vec3 c = centerVec(index);
        double dx = p.x - c.x, dz = p.z - c.z;
        double r = RADIUS + slack;
        return dx * dx + dz * dz <= r * r && p.y >= BASE_Y - 4 && p.y <= BASE_Y + BARRIER_HEIGHT + 4;
    }

    /** Um bloco a colocar. */
    public record Placement(BlockPos pos, BlockState state) {}

    /**
     * Lista de blocos da arena (piso desenhado, muro baixo com pilares e lanternas, barreira invisível por cima). O
     * gerenciador coloca aos poucos, alguns por tick.
     */
    public static List<Placement> layout(int index) {
        BlockPos c = center(index);
        List<Placement> out = new ArrayList<>(5200);
        double floorR = RADIUS + 0.5, wallR = RADIUS + 2.0;
        int reach = RADIUS + 2;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d <= floorR) {
                    out.add(new Placement(c.offset(dx, 0, dz), floorBlock(dx, dz, d)));
                } else if (d <= wallR) {
                    // Pilar com lanterna a cada 22,5°: dá leitura da borda sem iluminar demais
                    double ang = Math.toDegrees(Math.atan2(dz, dx));
                    boolean pillar = d <= floorR + 1.0 && Math.abs(ang / 22.5 - Math.round(ang / 22.5)) < 0.06;
                    int wall = pillar ? WALL_HEIGHT + 2 : WALL_HEIGHT;
                    out.add(new Placement(c.offset(dx, 0, dz), Blocks.DEEPSLATE_TILES.defaultBlockState()));
                    for (int y = 1; y <= BARRIER_HEIGHT; y++) {
                        BlockState s;
                        if (y <= wall) s = pillar ? Blocks.POLISHED_BLACKSTONE.defaultBlockState() : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
                        else if (pillar && y == wall + 1) s = Blocks.SOUL_LANTERN.defaultBlockState();
                        else s = Blocks.BARRIER.defaultBlockState();
                        out.add(new Placement(c.offset(dx, y, dz), s));
                    }
                }
            }
        }
        return out;
    }

    /** Desenho do piso: selo central, dois anéis de obsidiana, raios e manchas de sculk. */
    private static BlockState floorBlock(int dx, int dz, double d) {
        if (d <= 1.5) return Blocks.CRYING_OBSIDIAN.defaultBlockState();
        if (Math.abs(d - 4.0) < 0.5 || Math.abs(d - 12.0) < 0.5 || Math.abs(d - 20.0) < 0.5) return Blocks.OBSIDIAN.defaultBlockState();
        if (d > 4.5 && (dx == 0 || dz == 0 || Math.abs(dx) == Math.abs(dz))) return Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        int h = Mth.abs((dx * 73428767) ^ (dz * 912931) ^ (dx * dz * 31)) % 100;
        if (h < 7) return Blocks.SCULK.defaultBlockState();
        if (h < 9) return Blocks.GILDED_BLACKSTONE.defaultBlockState();
        if (h < 30) return Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        if (h < 38) return Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        return Blocks.POLISHED_BLACKSTONE.defaultBlockState();
    }

    private AbyssDimension() {}
}
