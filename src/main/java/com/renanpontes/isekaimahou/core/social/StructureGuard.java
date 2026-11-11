package com.renanpontes.isekaimahou.core.social;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.api.Places;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;

/**
 * Áreas SEMPRE protegidas (hubs/templos de chefe): estruturas registradas por id e lugares ({@code Places}) por id.
 * Lá ninguém (fora do criativo) quebra blocos, explosões não destroem e a {@code Destruction} não mexe — com ou sem luta.
 * As caixas das estruturas ficam em cache por chunk (1 min) para o teste por bloco ser barato.
 */
final class StructureGuard {
    /** Estruturas de chefe do mod (módulo dimensions): templos dos guardiões, Trono do Abismo, Catedral Celeste, Arena da Fenda. */
    static final List<String> DEFAULT_STRUCTURES = List.of("guardian_temple_fire", "guardian_temple_water", "guardian_temple_earth",
            "guardian_temple_wind", "abyss_throne", "celestial_cathedral", "twilight_arena");
    /** Hub da Fenda do Crepúsculo (arena do Arauto; não é zona segura, então a Destruction o alcançaria). */
    static final List<String> DEFAULT_PLACES = List.of("hub_twilight_rift");
    private static final int CACHE_TICKS = 1200;
    /** Folga em volta das peças (paredes externas, degraus). */
    private static final int MARGIN = 2;

    private static final Set<ResourceLocation> STRUCTURES = new CopyOnWriteArraySet<>();
    private static final Set<String> PLACES = new CopyOnWriteArraySet<>();

    private record Entry(long expires, List<BoundingBox> boxes) {}

    private static final Map<ResourceKey<Level>, Long2ObjectOpenHashMap<Entry>> CACHE = new HashMap<>();

    private record PlaceEntry(long expires, List<Places.Place> places) {}

    private static final int PLACE_CACHE_TICKS = 100;
    private static final Map<ResourceKey<Level>, PlaceEntry> PLACE_CACHE = new HashMap<>();

    static {
        for (String s : DEFAULT_STRUCTURES) STRUCTURES.add(IsekaiMahou.id(s));
        PLACES.addAll(DEFAULT_PLACES);
    }

    static void addStructure(ResourceLocation id) {
        if (id != null && STRUCTURES.add(id)) CACHE.clear();
    }

    static void addPlace(String id) {
        if (id != null && !id.isBlank() && PLACES.add(id)) PLACE_CACHE.clear();
    }

    static Set<ResourceLocation> structures() {
        return Set.copyOf(STRUCTURES);
    }

    static Set<String> places() {
        return Set.copyOf(PLACES);
    }

    /** A posição está numa área sempre protegida? Chunks não carregados contam como não protegidos (ninguém mexe lá). */
    static boolean isProtected(ServerLevel level, BlockPos pos) {
        // Caches e leitura de chunk só na thread do servidor (todas as chamadas do jogo vêm dela)
        if (!level.getServer().isSameThread()) return false;
        for (Places.Place p : placesIn(level)) {
            if (p.contains(pos)) return true;
        }
        if (STRUCTURES.isEmpty()) return false;
        for (BoundingBox box : boxes(level, pos.getX() >> 4, pos.getZ() >> 4)) {
            if (box.isInside(pos)) return true;
        }
        return false;
    }

    /** Caixa (peça) de estrutura protegida que contém a posição, ou null. */
    @Nullable
    static BoundingBox boxAt(ServerLevel level, BlockPos pos) {
        if (STRUCTURES.isEmpty()) return null;
        for (BoundingBox box : boxes(level, pos.getX() >> 4, pos.getZ() >> 4)) {
            if (box.isInside(pos)) return box;
        }
        return null;
    }

    /**
     * Lugares sempre protegidos DESTE nível (cache de 5 s). O teste roda por bloco (explosões, Destruction) e a cada
     * consulta de mobGriefing — {@code Places.get} varre todas as dimensões quando o lugar é de outra, então aqui só se
     * olha a lista do próprio nível, uma vez a cada 100 ticks.
     */
    private static List<Places.Place> placesIn(ServerLevel level) {
        if (PLACES.isEmpty()) return List.of();
        long now = level.getGameTime();
        PlaceEntry e = PLACE_CACHE.get(level.dimension());
        if (e != null && e.expires() > now && e.expires() - now <= PLACE_CACHE_TICKS) return e.places();
        List<Places.Place> out = new ArrayList<>(2);
        for (Places.Place p : Places.all(level)) {
            if (PLACES.contains(p.id()) && p.dim().equals(level.dimension())) out.add(p);
        }
        List<Places.Place> list = List.copyOf(out);
        PLACE_CACHE.put(level.dimension(), new PlaceEntry(now + PLACE_CACHE_TICKS, list));
        return list;
    }

    private static List<BoundingBox> boxes(ServerLevel level, int cx, int cz) {
        long key = ChunkPos.asLong(cx, cz);
        long now = level.getGameTime();
        Long2ObjectOpenHashMap<Entry> map = CACHE.computeIfAbsent(level.dimension(), k -> new Long2ObjectOpenHashMap<>());
        Entry e = map.get(key);
        if (e != null && e.expires() > now) return e.boxes();
        if (!level.hasChunk(cx, cz)) return List.of();
        List<BoundingBox> out = new ArrayList<>();
        try {
            LevelChunk chunk = level.getChunk(cx, cz);
            Map<Structure, LongSet> refs = chunk.getAllReferences();
            if (!refs.isEmpty()) {
                Registry<Structure> reg = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
                for (Map.Entry<Structure, LongSet> ref : refs.entrySet()) {
                    ResourceLocation id = reg.getKey(ref.getKey());
                    if (id == null || !STRUCTURES.contains(id)) continue;
                    level.structureManager().fillStartsForStructure(ref.getKey(), ref.getValue(), start -> {
                        for (StructurePiece piece : start.getPieces()) out.add(piece.getBoundingBox().inflatedBy(MARGIN));
                    });
                }
            }
        } catch (RuntimeException ex) {
            IsekaiMahou.LOGGER.error("[social] falha ao ler estruturas protegidas no chunk {},{}", cx, cz, ex);
        }
        if (map.size() > 4096) map.clear();
        map.put(key, new Entry(now + CACHE_TICKS, out));
        return out;
    }

    static void clear() {
        CACHE.clear();
        PLACE_CACHE.clear();
    }

    private StructureGuard() {}
}
