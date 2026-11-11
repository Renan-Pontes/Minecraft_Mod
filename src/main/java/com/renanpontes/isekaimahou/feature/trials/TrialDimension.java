package com.renanpontes.isekaimahou.feature.trials;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Geometria do Reino das Provações ({@code isekai_mahou:trial_realm}): uma dimensão vazia (só ar) onde cada
 * instância ocupa uma "faixa" própria. Instância n: recepção centrada em {@code (n·1024+512, 100, 512)} e arena
 * 96 blocos ao sul. Longe o bastante para duas instâncias nunca dividirem chunks carregados.
 */
public final class TrialDimension {
    public static final ResourceKey<Level> KEY = ResourceKey.create(Registries.DIMENSION, IsekaiMahou.id("trial_realm"));

    public static final int SPACING = 1024;
    /** Y do piso (os jogadores ficam em BASE_Y + 1). */
    public static final int BASE_Y = 100;
    public static final int ARENA_OFFSET = 96;
    /** Meia-largura da recepção (21×21 contando as paredes). */
    public static final int LOBBY_HALF = 10;
    /** Altura do teto de barreira da recepção e da arena (acima do piso). */
    public static final int LOBBY_ROOF = 12;
    public static final int ARENA_ROOF = 27;
    /** Margem da caixa da arena além do maior raio (parede + barreira + aba decorativa). */
    private static final int ARENA_MARGIN = 5;
    public static final int MAX_RADIUS = 24;
    /**
     * Distância (além do raio) da face interna da parede da arena: o piso vai até {@code raio + 0,5} (centro dos blocos)
     * e a parede começa logo depois. A borda visível ({@code ArenaBounds}) fica um pouco antes da pedra para a luz não
     * "entrar" nos blocos.
     */
    public static final double ARENA_EDGE = 0.4;
    /** Espessura da parede (a partir de {@code raio + 0,5}); toda ela é coberta por barreira até o teto. */
    public static final double WALL_OUTER = 2.5;

    @Nullable
    public static ServerLevel level(MinecraftServer server) {
        return server.getLevel(KEY);
    }

    public static boolean is(Level level) {
        return level.dimension() == KEY;
    }

    /** Bloco central do piso da recepção. */
    public static BlockPos origin(int index) {
        return new BlockPos(index * SPACING + 512, BASE_Y, 512);
    }

    /** Bloco central do piso da arena. */
    public static BlockPos arenaCenter(int index) {
        return origin(index).offset(0, 0, ARENA_OFFSET);
    }

    /** Raio da arena pelo rank (I: 18 … VII: 24). */
    public static int arenaRadius(int rank) {
        return Mth.clamp(17 + rank, 18, MAX_RADIUS);
    }

    /** Raio da borda real da arena (face interna da parede) — o limite mostrado aos jogadores. */
    public static double arenaEdge(int rank) {
        return arenaRadius(rank) + ARENA_EDGE;
    }

    /** Índice da instância dona da coluna x (pode ser negativo se alguém andar longe — nunca é usado). */
    public static int indexAt(double x) {
        return Math.floorDiv(Mth.floor(x), SPACING);
    }

    public static BoundingBox lobbyBox(int index) {
        BlockPos o = origin(index);
        int h = LOBBY_HALF + 2;
        return new BoundingBox(o.getX() - h, o.getY() - 2, o.getZ() - h, o.getX() + h, o.getY() + LOBBY_ROOF + 2, o.getZ() + h);
    }

    public static BoundingBox arenaBox(int index) {
        BlockPos c = arenaCenter(index);
        int h = MAX_RADIUS + ARENA_MARGIN;
        return new BoundingBox(c.getX() - h, c.getY() - 3, c.getZ() - h, c.getX() + h, c.getY() + ARENA_ROOF + 2, c.getZ() + h);
    }

    public static AABB aabb(BoundingBox box) {
        return new AABB(box.minX(), box.minY(), box.minZ(), box.maxX() + 1, box.maxY() + 1, box.maxZ() + 1);
    }

    /** Todos os chunks tocados pela recepção e pela arena da instância. */
    public static List<ChunkPos> chunks(int index) {
        Set<ChunkPos> out = new LinkedHashSet<>();
        for (BoundingBox b : List.of(lobbyBox(index), arenaBox(index))) {
            for (int cx = b.minX() >> 4; cx <= b.maxX() >> 4; cx++) {
                for (int cz = b.minZ() >> 4; cz <= b.maxZ() >> 4; cz++) out.add(new ChunkPos(cx, cz));
            }
        }
        return new ArrayList<>(out);
    }

    /** Ponto de chegada na recepção (diante do altar, de costas para a saída). */
    public static Vec3 lobbySpawn(int index) {
        BlockPos o = origin(index);
        return new Vec3(o.getX() + 0.5, o.getY() + 1, o.getZ() + 5.5);
    }

    private TrialDimension() {}
}
