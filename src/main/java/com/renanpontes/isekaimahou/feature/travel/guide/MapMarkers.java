package com.renanpontes.isekaimahou.feature.travel.guide;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.core.api.Travel;
import com.renanpontes.isekaimahou.core.event.PlaceEvent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * v1.0 — <b>pontos no minimapa criados sozinhos</b>: quando o jogador descobre uma estrutura importante (cidade,
 * masmorra, torre, cidadela, biblioteca, portal, covil), ela vira um ponto no minimapa do Xaero (o cliente aplica; sem
 * o Xaero nada acontece). Duas fontes:
 * <ul>
 *   <li><b>Lugares</b> do {@code Places} que o jogador já visitou (cidades com nome próprio, covis, portais, hubs);</li>
 *   <li><b>Estruturas</b> do mod sem lugar próprio (masmorras, torres, Cidadela, Biblioteca...): a cada 2 s, só quando o
 *       jogador muda de chunk, lê as referências de estrutura do chunk onde ele está (dado que o chunk já tem — nenhuma
 *       varredura). O que foi achado fica guardado no jogador (no máximo {@value #MAX_STORED}).</li>
 * </ul>
 * O servidor manda a lista INTEIRA da dimensão atual ao entrar, ao trocar de dimensão e a cada descoberta nova.
 */
public final class MapMarkers {
    private static final String TAG = "isekai_mahou_map_marks";
    private static final int MAX_STORED = 256, MAX_SENT = 300;
    private static final int CHECK_EVERY = 40;

    /** Tipo de lugar do {@code Places} → categoria do ponto. */
    private static final Map<String, String> PLACE_TYPES = Map.of(
            "capital", "city", "city", "city", "village", "city",
            "portal_site", "portal", Travel.HUB_TYPE, "portal",
            "boss_lair", "lair", "sanctum", "sanctum");

    /** Estrutura do mod → categoria do ponto. */
    private static final Map<String, String> STRUCTURES = new HashMap<>();

    static {
        put("city", "guild_city", "capital_city", "capital_sahra", "capital_nordheim", "dwarf_hold", "guild_hall_village");
        put("dungeon", "depths_dungeon", "forgotten_crypt", "minotaur_labyrinth", "goblin_fortress", "steelball_saint_tomb");
        put("tower", "tower_frost", "tower_sand", "tower_stone", "mage_tower", "renegade_tower");
        put("citadel", "nether_citadel");
        put("library", "end_library");
        put("portal", "elemental_gate", "abyssal_chasm", "celestial_stairway", "twilight_rupture", "garganta_site", "fey_ring");
        put("lair", "hydra_lair", "wyvern_peak", "basilisk_tomb", "colossus_ruins", "manticore_den", "false_hydra_lair",
                "mimic_vault", "kobold_den", "harpy_nest", "guardian_temple_fire", "guardian_temple_water",
                "guardian_temple_earth", "guardian_temple_wind", "abyss_throne", "celestial_cathedral");
        put("sanctum", "ascension_sanctum");
    }

    private static void put(String category, String... structures) {
        for (String s : structures) STRUCTURES.put(s, category);
    }

    /** Outro módulo pode marcar a sua estrutura como importante (categorias: city, dungeon, tower, citadel, library, portal, lair, sanctum). */
    public static void registerStructure(String structurePath, String category) {
        STRUCTURES.put(structurePath, category);
    }

    /** Estrutura → caminho do id (ou "" se não interessa), resolvido uma vez por servidor. */
    private static final Map<Structure, String> PATHS = new IdentityHashMap<>();
    /** Por jogador: chaves já guardadas ou já descartadas (cobertas por um lugar), e o último chunk conferido. */
    private static final Map<UUID, Set<String>> SEEN = new HashMap<>();
    private static final Map<UUID, Long> LAST_CHUNK = new HashMap<>();
    /** Sincronizações agendadas (tick do servidor em que devem sair) e a chave nova a anunciar. */
    private static final Map<UUID, Integer> DUE = new HashMap<>();
    private static final Map<UUID, String> DUE_NEW = new HashMap<>();

    // ------------------------------------------------------------------ eventos
    public static void tick(MinecraftServer server) {
        int now = server.getTickCount();
        if (!DUE.isEmpty()) {
            List<UUID> ready = new ArrayList<>();
            for (Map.Entry<UUID, Integer> e : DUE.entrySet()) if (now >= e.getValue()) ready.add(e.getKey());
            for (UUID id : ready) {
                DUE.remove(id);
                String newKey = DUE_NEW.remove(id);
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p != null) sync(p, newKey == null ? "" : newKey);
            }
        }
        if (now % CHECK_EVERY != 0) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p instanceof FakePlayer || p.isSpectator()) continue;
            try {
                detect(p);
            } catch (RuntimeException e) {
                // Ler referências de estrutura nunca derruba o servidor: sem ponto novo desta vez.
                IsekaiMahou.LOGGER.debug("Marcadores do mapa: falha ao conferir estruturas", e);
            }
        }
    }

    /** Pede uma sincronização daqui a {@code delay} ticks (junta pedidos seguidos). */
    public static void schedule(ServerPlayer p, int delay, @Nullable String newKey) {
        MinecraftServer server = p.getServer();
        if (server == null) return;
        DUE.merge(p.getUUID(), server.getTickCount() + delay, Math::min);
        if (newKey != null) DUE_NEW.put(p.getUUID(), newKey);
    }

    public static void onPlaceEnter(PlaceEvent.Enter e) {
        // Sem aviso: o lugar já mostra o próprio letreiro ao entrar (um aviso de cada vez)
        if (e.isFirstVisit() && PLACE_TYPES.containsKey(e.getPlace().type())) schedule(e.getPlayer(), 10, null);
    }

    public static void onLogout(UUID id) {
        SEEN.remove(id);
        LAST_CHUNK.remove(id);
        DUE.remove(id);
        DUE_NEW.remove(id);
    }

    public static void clearAll() {
        PATHS.clear();
        SEEN.clear();
        LAST_CHUNK.clear();
        DUE.clear();
        DUE_NEW.clear();
    }

    // ------------------------------------------------------------------ descoberta de estruturas
    private static void detect(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        ChunkPos chunk = p.chunkPosition();
        // Mesmo chunk da última conferência (nesta dimensão): nada mudou.
        long stamp = chunk.toLong() ^ ((long) level.dimension().location().hashCode() << 17);
        Long last = LAST_CHUNK.put(p.getUUID(), stamp);
        if (last != null && last == stamp) return;
        if (!level.hasChunk(chunk.x, chunk.z)) return;

        List<StructureStart> starts = level.structureManager().startsForStructure(chunk, s -> !path(level, s).isEmpty());
        if (starts.isEmpty()) return;
        Set<String> seen = SEEN.computeIfAbsent(p.getUUID(), k -> loadKeys(p));
        for (StructureStart start : starts) {
            String path = path(level, start.getStructure());
            if (path.isEmpty()) continue;
            String key = "s:" + path + "@" + start.getChunkPos().x + "," + start.getChunkPos().z;
            if (!seen.add(key)) continue;
            BoundingBox box = start.getBoundingBox();
            BlockPos center = box.getCenter();
            // Já existe um lugar do Places ali (cidade com nome, covil, portal): o ponto vem do lugar, não daqui.
            if (coveredByPlace(level, center) || coveredByPlace(level, p.blockPosition())) continue;
            store(p, key, path, level, center);
            schedule(p, 5, key);
        }
    }

    private static String path(ServerLevel level, Structure structure) {
        String cached = PATHS.get(structure);
        if (cached != null) return cached;
        ResourceLocation id = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(structure);
        String path = id != null && IsekaiMahou.MODID.equals(id.getNamespace()) && STRUCTURES.containsKey(id.getPath())
                ? id.getPath() : "";
        PATHS.put(structure, path);
        return path;
    }

    private static boolean coveredByPlace(ServerLevel level, BlockPos pos) {
        Places.Place place = Places.at(level, pos);
        return place != null && PLACE_TYPES.containsKey(place.type());
    }

    // ------------------------------------------------------------------ guardado no jogador
    private static ListTag stored(ServerPlayer p) {
        return GuideService.persisted(p).getList(TAG, Tag.TAG_COMPOUND);
    }

    private static Set<String> loadKeys(ServerPlayer p) {
        Set<String> keys = new HashSet<>();
        ListTag list = stored(p);
        for (int i = 0; i < list.size(); i++) keys.add(list.getCompound(i).getString("K"));
        return keys;
    }

    private static void store(ServerPlayer p, String key, String path, ServerLevel level, BlockPos pos) {
        ListTag list = stored(p);
        // Cheio: sai o mais antigo (o jogador pode guardar os que quiser como ponto normal do Xaero).
        while (list.size() >= MAX_STORED) list.remove(0);
        CompoundTag t = new CompoundTag();
        t.putString("K", key);
        t.putString("S", path);
        t.putString("D", level.dimension().location().toString());
        t.putInt("X", pos.getX());
        t.putInt("Y", pos.getY());
        t.putInt("Z", pos.getZ());
        list.add(t);
        GuideService.persisted(p).put(TAG, list);
    }

    /** Apaga os pontos de estrutura guardados (comando de teste). */
    public static void forget(ServerPlayer p) {
        GuideService.persisted(p).remove(TAG);
        SEEN.remove(p.getUUID());
        LAST_CHUNK.remove(p.getUUID());
        schedule(p, 1, null);
    }

    // ------------------------------------------------------------------ lista para o cliente
    /** Todos os pontos do jogador na dimensão onde ele está. */
    public static List<GuideNet.Marker> collect(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        List<GuideNet.Marker> out = new ArrayList<>();
        List<Places.Place> shown = new ArrayList<>();
        for (Places.Place place : Places.all(level)) {
            String category = PLACE_TYPES.get(place.type());
            if (category == null || !Travel.discovered(p, place.id())) continue;
            shown.add(place);
            out.add(new GuideNet.Marker("p:" + place.id(), place.name(), category, place.center()));
            if (out.size() >= MAX_SENT) return out;
        }
        String dim = level.dimension().location().toString();
        ListTag list = stored(p);
        for (int i = list.size() - 1; i >= 0 && out.size() < MAX_SENT; i--) {
            CompoundTag t = list.getCompound(i);
            if (!dim.equals(t.getString("D"))) continue;
            String path = t.getString("S");
            String category = STRUCTURES.get(path);
            if (category == null) continue;
            BlockPos pos = new BlockPos(t.getInt("X"), t.getInt("Y"), t.getInt("Z"));
            // Um lugar já mostrado cobre este ponto (a cidade ganhou nome depois): fica só o do lugar.
            boolean covered = false;
            for (Places.Place place : shown) {
                if (place.contains(pos)) {
                    covered = true;
                    break;
                }
            }
            if (covered) continue;
            out.add(new GuideNet.Marker(t.getString("K"), structureName(path), category, pos));
        }
        return out;
    }

    public static Component structureName(String path) {
        return Component.translatableWithFallback("map." + IsekaiMahou.MODID + ".structure." + path, path.replace('_', ' '));
    }

    public static void sync(ServerPlayer p, String newKey) {
        PacketDistributor.sendToPlayer(p, new GuideNet.Markers(p.level().dimension().location(), collect(p), newKey));
    }

    private MapMarkers() {}
}
