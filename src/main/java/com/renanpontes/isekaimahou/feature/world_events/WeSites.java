package com.renanpontes.isekaimahou.feature.world_events;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.api.Destruction;
import com.renanpontes.isekaimahou.core.api.Places;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * "Cidades são sagradas": aqui fica TODA a regra de onde um evento pode mexer no terreno.
 * Um lugar só serve se, ao mesmo tempo:
 * <ul>
 *   <li>está longe ({@link #PLACE_MARGIN} blocos) de qualquer lugar registrado no {@link Places} (cidades de NPC e de
 *       jogador, vilas, torres, pedras de viagem...);</li>
 *   <li>nenhum chunk em volta tem estrutura gerada (vila do vanilla, ruínas, masmorras do mod);</li>
 *   <li>nenhum jogador jamais colocou um bloco nos chunks em volta (anotado desde esta versão);</li>
 *   <li>não há nada com cara de construção por perto (baús, tábuas, portas, vidro, tochas, plantações...): isto cobre
 *       as casas feitas ANTES desta versão;</li>
 *   <li>está longe da cama/ponto de renascimento de quem está online;</li>
 *   <li>o chão é firme, seco e mais ou menos plano.</li>
 * </ul>
 */
public final class WeSites {
    /** Distância mínima (blocos) da borda de qualquer lugar registrado. */
    public static final int PLACE_MARGIN = 64;
    private static final int BED_MARGIN = 48;

    private WeSites() {}

    // ------------------------------------------------------------------ blocos de jogador
    static void onBlockPlaced(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getEntity() instanceof Player p) || p instanceof FakePlayer) return;
        if (!(e.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        WeState.get(level.getServer()).markBuilt(ChunkPos.asLong(e.getPos()));
    }

    // ------------------------------------------------------------------ distância a lugares
    /** Há algum lugar registrado a menos de {@code margin} blocos (na horizontal) desta posição? */
    public static boolean nearPlace(ServerLevel level, BlockPos pos, int margin) {
        for (Places.Place p : Places.all(level)) {
            BoundingBox b = p.box();
            if (pos.getX() >= b.minX() - margin && pos.getX() <= b.maxX() + margin
                    && pos.getZ() >= b.minZ() - margin && pos.getZ() <= b.maxZ() + margin) return true;
        }
        return false;
    }

    private static boolean nearBed(ServerLevel level, BlockPos pos) {
        for (ServerPlayer sp : level.players()) {
            BlockPos home = sp.getRespawnPosition();
            if (home == null || sp.getRespawnDimension() != level.dimension()) continue;
            if (home.distSqr(pos) <= (double) BED_MARGIN * BED_MARGIN) return true;
        }
        return false;
    }

    /** Chunk carregado, sem estrutura e sem blocos de jogador? */
    private static boolean chunkClean(ServerLevel level, WeState state, int cx, int cz) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
        if (chunk == null) return false;
        if (state.isBuilt(ChunkPos.asLong(cx, cz))) return false;
        for (LongSet refs : chunk.getAllReferences().values()) {
            if (refs != null && !refs.isEmpty()) return false;
        }
        return true;
    }

    /** Bloco com cara de coisa feita por alguém (o que a natureza do jogo não gera sozinha no ermo). */
    public static boolean looksBuilt(BlockState st) {
        if (st.isAir()) return false;
        if (st.hasBlockEntity()) return true;
        return st.is(BlockTags.PLANKS) || st.is(BlockTags.DOORS) || st.is(BlockTags.TRAPDOORS) || st.is(BlockTags.FENCES)
                || st.is(BlockTags.FENCE_GATES) || st.is(BlockTags.WALLS) || st.is(BlockTags.STAIRS) || st.is(BlockTags.SLABS)
                || st.is(BlockTags.WOOL) || st.is(BlockTags.WOOL_CARPETS) || st.is(BlockTags.BEDS) || st.is(BlockTags.RAILS)
                || st.is(BlockTags.BUTTONS) || st.is(BlockTags.PRESSURE_PLATES) || st.is(BlockTags.CROPS)
                || st.is(BlockTags.CANDLES) || st.is(BlockTags.STONE_BRICKS) || st.is(BlockTags.IMPERMEABLE)
                || st.is(BlockTags.ANVIL) || st.is(BlockTags.CLIMBABLE) && st.is(Blocks.LADDER)
                || st.is(Blocks.TORCH) || st.is(Blocks.WALL_TORCH) || st.is(Blocks.SOUL_TORCH) || st.is(Blocks.SOUL_WALL_TORCH)
                || st.is(Blocks.LANTERN) || st.is(Blocks.SOUL_LANTERN) || st.is(Blocks.CRAFTING_TABLE) || st.is(Blocks.FARMLAND)
                || st.is(Blocks.COBBLESTONE) || st.is(Blocks.BRICKS) || st.is(Blocks.GLASS_PANE) || st.is(Blocks.DIRT_PATH)
                || st.is(Blocks.HAY_BLOCK) || st.is(Blocks.BOOKSHELF) || st.is(Blocks.SMOOTH_STONE) || st.is(Blocks.IRON_BARS)
                || st.is(Blocks.REDSTONE_WIRE) || st.is(Blocks.SCAFFOLDING);
    }

    /** Chão natural em que dá para abrir cratera/fenda. */
    public static boolean isGround(BlockState st) {
        return st.is(BlockTags.BASE_STONE_OVERWORLD) || st.is(BlockTags.DIRT) || st.is(BlockTags.SAND)
                || st.is(Blocks.GRAVEL) || st.is(BlockTags.TERRACOTTA) || st.is(Blocks.SNOW_BLOCK) || st.is(Blocks.SANDSTONE)
                || st.is(Blocks.RED_SANDSTONE) || st.is(Blocks.CLAY) || st.is(BlockTags.COAL_ORES) || st.is(BlockTags.IRON_ORES)
                || st.is(BlockTags.COPPER_ORES);
    }

    /**
     * Y do chão natural nesta coluna (o bloco sólido de cima), ignorando árvores, neve e plantas.
     * Devolve {@link Integer#MIN_VALUE} se não achar chão firme (água, lava, copa de árvore alta, vazio).
     */
    public static int groundY(ServerLevel level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(x, y, z);
        for (int i = 0; i < 28 && y > level.getMinBuildHeight() + 8; i++, y--) {
            m.setY(y);
            BlockState st = level.getBlockState(m);
            if (!st.getFluidState().isEmpty()) return Integer.MIN_VALUE;
            if (isGround(st)) return y;
            if (looksBuilt(st)) return Integer.MIN_VALUE;
        }
        return Integer.MIN_VALUE;
    }

    /**
     * A área (raio {@code radius} em volta de {@code center}, que deve estar no chão) pode receber um evento que mexe
     * no terreno? Veja as regras no topo da classe. Custa uma varredura de blocos: chame poucas vezes.
     */
    public static boolean areaClean(ServerLevel level, BlockPos center, int radius) {
        if (level.dimension() != Level.OVERWORLD) return false;
        if (nearPlace(level, center, PLACE_MARGIN + radius) || nearBed(level, center)) return false;
        WeState state = WeState.get(level.getServer());
        int reach = radius + 16;
        int cx0 = (center.getX() - reach) >> 4, cx1 = (center.getX() + reach) >> 4;
        int cz0 = (center.getZ() - reach) >> 4, cz1 = (center.getZ() + reach) >> 4;
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                if (!chunkClean(level, state, cx, cz)) return false;
            }
        }
        // Varredura: nada construído por perto e chão firme/plano
        int scan = radius + 8;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int minY = Math.max(level.getMinBuildHeight() + 1, center.getY() - 10);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, center.getY() + 14);
        for (int x = center.getX() - scan; x <= center.getX() + scan; x++) {
            for (int z = center.getZ() - scan; z <= center.getZ() + scan; z++) {
                for (int y = minY; y <= maxY; y++) {
                    if (looksBuilt(level.getBlockState(m.set(x, y, z)))) return false;
                }
            }
        }
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int dx = -radius; dx <= radius; dx += Math.max(1, radius / 2)) {
            for (int dz = -radius; dz <= radius; dz += Math.max(1, radius / 2)) {
                int gy = groundY(level, center.getX() + dx, center.getZ() + dz);
                if (gy == Integer.MIN_VALUE) return false;
                lo = Math.min(lo, gy);
                hi = Math.max(hi, gy);
            }
        }
        return hi - lo <= 7;
    }

    /**
     * Procura um lugar livre a {@code minDist..maxDist} blocos de {@code around} (só em chunks carregados).
     * Devolve o bloco do CHÃO no centro, ou null se não achou (aí o evento simplesmente não acontece).
     */
    @Nullable
    public static BlockPos findSite(ServerLevel level, BlockPos around, int minDist, int maxDist, int radius, int tries) {
        RandomSource rnd = level.random;
        for (int i = 0; i < tries; i++) {
            double ang = rnd.nextDouble() * Math.PI * 2;
            double dist = minDist + rnd.nextDouble() * (maxDist - minDist);
            int x = around.getX() + Mth.floor(Math.cos(ang) * dist);
            int z = around.getZ() + Mth.floor(Math.sin(ang) * dist);
            if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) continue;
            int y = groundY(level, x, z);
            if (y == Integer.MIN_VALUE) continue;
            BlockPos pos = new BlockPos(x, y, z);
            if (areaClean(level, pos, radius)) return pos;
        }
        return null;
    }

    /** Última conferência, bloco a bloco, antes de trocar um bloco do terreno. */
    public static boolean canEdit(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos) || level.isOutsideBuildHeight(pos)) return false;
        BlockState st = level.getBlockState(pos);
        if (looksBuilt(st)) return false;
        return !Destruction.protectedAt(level, pos, null) && !Places.isSafe(level, pos);
    }
}
