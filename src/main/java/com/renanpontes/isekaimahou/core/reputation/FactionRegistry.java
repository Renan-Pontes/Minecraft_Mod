package com.renanpontes.isekaimahou.core.reputation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.api.Reputation;
import com.renanpontes.isekaimahou.core.api.Reputation.Faction;
import com.renanpontes.isekaimahou.core.api.Reputation.ItemReward;
import com.renanpontes.isekaimahou.core.api.Reputation.Standing;
import com.renanpontes.isekaimahou.core.api.Reputation.TierRewards;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.util.thread.EffectiveSide;

/**
 * Facções carregadas. Há duas cópias: a do SERVIDOR (lida do datapack) e a do CLIENTE (recebida no login/no /reload).
 * {@link #current()} escolhe pelo lado da thread (no single-player os dois existem no mesmo processo).
 */
public final class FactionRegistry {
    private static volatile Snapshot server = Snapshot.EMPTY;
    private static volatile Snapshot client = Snapshot.EMPTY;

    /** Cópia do lado atual (thread do cliente → cópia do cliente; se ainda vazia, a do servidor integrado). */
    public static Snapshot current() {
        if (EffectiveSide.get().isClient()) {
            Snapshot c = client;
            return c.isEmpty() ? server : c;
        }
        return server;
    }

    public static Snapshot server() { return server; }

    static void setServer(Snapshot s) { server = s; }

    public static void setClient(Snapshot s) { client = s; }

    /** Saiu do mundo: esquece a cópia do cliente. */
    public static void clearClient() { client = Snapshot.EMPTY; }

    /** Servidor parou: esquece a cópia do servidor. */
    static void clearServer() { server = Snapshot.EMPTY; }

    /** Tags mudaram (/reload ou login): o cache de "tipo de entidade → facção" fica velho. */
    public static void clearCaches() {
        server.cache.clear();
        client.cache.clear();
    }

    // =====================================================================================
    //  Snapshot
    // =====================================================================================
    public static final class Snapshot {
        static final Snapshot EMPTY = new Snapshot(List.of(), "{}");
        private static final String NONE = "";

        private final Map<String, Faction> byId = new LinkedHashMap<>();
        private final List<Faction> sorted;
        private final String json;
        private final Map<EntityType<?>, String> cache = new ConcurrentHashMap<>();

        Snapshot(List<Faction> factions, String json) {
            List<Faction> list = new ArrayList<>(factions);
            list.sort(Comparator.comparingInt(Faction::order).thenComparing(Faction::id));
            for (Faction f : list) byId.put(f.id(), f);
            this.sorted = Collections.unmodifiableList(list);
            this.json = json;
        }

        public boolean isEmpty() { return sorted.isEmpty(); }

        public List<Faction> sorted() { return sorted; }

        @Nullable public Faction get(String id) { return byId.get(id); }

        /** JSON enviado ao cliente. */
        public String json() { return json; }

        @Nullable
        public String factionOf(Entity entity) {
            if (entity instanceof Reputation.FactionMember member) {
                String f = member.factionId();
                if (f != null) return f.isEmpty() ? null : f;
            }
            if (entity instanceof Player || sorted.isEmpty()) return null;
            String id = cache.computeIfAbsent(entity.getType(), type -> {
                for (Faction f : sorted) {
                    if (type.is(f.memberTag())) return f.id();
                }
                return NONE;
            });
            return id.isEmpty() ? null : id;
        }
    }

    // =====================================================================================
    //  Leitura do JSON
    // =====================================================================================
    /** Monta a cópia a partir dos arquivos {@code factions/*.json} (chave = id do arquivo). */
    static Snapshot fromFiles(Map<ResourceLocation, JsonElement> files) {
        List<Faction> list = new ArrayList<>();
        JsonObject sync = new JsonObject();
        files.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            ResourceLocation file = entry.getKey();
            try {
                Faction f = parse(file, entry.getValue().getAsJsonObject());
                if (f == null) return;
                list.removeIf(o -> o.id().equals(f.id()));
                list.add(f);
                sync.add(file.toString(), entry.getValue());
            } catch (RuntimeException e) {
                IsekaiMahou.LOGGER.error("Facção inválida em {}: {}", file, e.getMessage());
            }
        });
        return new Snapshot(list, sync.toString());
    }

    /** Monta a cópia do cliente a partir do JSON recebido do servidor. */
    public static Snapshot fromSync(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            Map<ResourceLocation, JsonElement> files = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                ResourceLocation rl = ResourceLocation.tryParse(e.getKey());
                if (rl != null) files.put(rl, e.getValue());
            }
            return fromFiles(files);
        } catch (RuntimeException e) {
            IsekaiMahou.LOGGER.error("Não foi possível ler as facções enviadas pelo servidor", e);
            return Snapshot.EMPTY;
        }
    }

    @Nullable
    private static Faction parse(ResourceLocation file, JsonObject o) {
        String id = file.getPath();
        if (!id.matches("[a-z0-9_]+")) {
            IsekaiMahou.LOGGER.warn("Facção ignorada: id inválido '{}' (use só a-z, 0-9 e _ num arquivo factions/<id>.json)", file);
            return null;
        }
        int color = parseColor(o.get("color"), 0xCCCCCC);
        ResourceLocation icon = ResourceLocation.tryParse(GsonHelper.getAsString(o, "icon", "minecraft:paper"));
        if (icon == null) icon = ResourceLocation.withDefaultNamespace("paper");
        int order = GsonHelper.getAsInt(o, "order", 100);
        Map<String, Float> rivals = weights(o.get("rivals"));
        Map<String, Float> allies = weights(o.get("allies"));
        rivals.remove(id);
        allies.remove(id);
        int killPenalty = GsonHelper.getAsInt(o, "kill_penalty", -25);
        Map<ResourceLocation, Integer> killPenalties = new LinkedHashMap<>();
        JsonObject kp = GsonHelper.getAsJsonObject(o, "kill_penalties", new JsonObject());
        for (Map.Entry<String, JsonElement> e : kp.entrySet()) {
            ResourceLocation rl = ResourceLocation.tryParse(e.getKey());
            if (rl != null) killPenalties.put(rl, e.getValue().getAsInt());
        }
        Map<String, Integer> killRewards = intMap(GsonHelper.getAsJsonObject(o, "kill_rewards", new JsonObject()));
        killRewards.remove(id);
        int killCap = Math.max(0, GsonHelper.getAsInt(o, "kill_reward_daily_cap", 40));
        Map<String, Integer> counterRewards = intMap(GsonHelper.getAsJsonObject(o, "counter_rewards", new JsonObject()));
        counterRewards.keySet().removeIf(k -> k.startsWith("rep."));
        boolean neutral = GsonHelper.getAsBoolean(o, "neutral_when_friendly", false);
        Set<ResourceLocation> guards = idSet(o, "guards");
        Set<ResourceLocation> traders = idSet(o, "traders");
        Map<Standing, TierRewards> rewards = new EnumMap<>(Standing.class);
        JsonObject rw = GsonHelper.getAsJsonObject(o, "rewards", new JsonObject());
        for (Map.Entry<String, JsonElement> e : rw.entrySet()) {
            Standing s = Standing.byId(e.getKey());
            if (s == null || !s.atLeast(Standing.FRIENDLY)) {
                IsekaiMahou.LOGGER.warn("Facção {}: patamar de recompensa desconhecido '{}' (use friendly/honored/revered/exalted)", id, e.getKey());
                continue;
            }
            rewards.put(s, tierRewards(e.getValue().getAsJsonObject()));
        }
        TagKey<EntityType<?>> tag = TagKey.create(Registries.ENTITY_TYPE,
                ResourceLocation.fromNamespaceAndPath(file.getNamespace(), "faction/" + id));
        return new Faction(id, color, icon, order, Map.copyOf(rivals), Map.copyOf(allies), killPenalty, Map.copyOf(killPenalties),
                Map.copyOf(killRewards), killCap, Map.copyOf(counterRewards), neutral, Set.copyOf(guards), Set.copyOf(traders),
                Collections.unmodifiableMap(rewards), tag);
    }

    /** Lista de ids de entidade ({@code ["minecraft:iron_golem", ...]}); ids inválidos são ignorados. */
    private static Set<ResourceLocation> idSet(JsonObject o, String field) {
        Set<ResourceLocation> out = new LinkedHashSet<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(o, field, new JsonArray())) {
            ResourceLocation rl = e.isJsonPrimitive() ? ResourceLocation.tryParse(e.getAsString()) : null;
            if (rl != null) out.add(rl);
        }
        return out;
    }

    private static TierRewards tierRewards(JsonObject o) {
        int xp = Math.max(0, GsonHelper.getAsInt(o, "xp", 0));
        List<ItemReward> items = new ArrayList<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(o, "items", new JsonArray())) {
            JsonObject io = e.getAsJsonObject();
            ResourceLocation rl = ResourceLocation.tryParse(GsonHelper.getAsString(io, "id", ""));
            int count = Math.max(1, GsonHelper.getAsInt(io, "count", 1));
            if (rl != null) items.add(new ItemReward(rl, count));
        }
        int scrolls = 0;
        int tier = 1;
        JsonElement gs = o.get("glyph_scrolls");
        if (gs != null && gs.isJsonObject()) {
            scrolls = GsonHelper.getAsInt(gs.getAsJsonObject(), "count", 1);
            tier = GsonHelper.getAsInt(gs.getAsJsonObject(), "tier", 1);
        } else if (gs != null) {
            scrolls = gs.getAsInt();
            tier = GsonHelper.getAsInt(o, "glyph_tier", 1);
        }
        String legend = o.has("legend") && !o.get("legend").isJsonNull() ? GsonHelper.getAsString(o, "legend") : null;
        if (legend != null && legend.isBlank()) legend = null;
        String title = o.has("title") && !o.get("title").isJsonNull() ? GsonHelper.getAsString(o, "title") : null;
        if (title != null && title.isBlank()) title = null;
        return new TierRewards(xp, List.copyOf(items), Math.max(0, Math.min(8, scrolls)), Math.max(1, Math.min(5, tier)), legend, title);
    }

    /** {@code {"horde": 1.0, "underworld": 0.5}} ou {@code ["horde", "underworld"]} (peso 1). */
    private static Map<String, Float> weights(@Nullable JsonElement el) {
        Map<String, Float> out = new LinkedHashMap<>();
        if (el == null || el.isJsonNull()) return out;
        if (el.isJsonArray()) {
            for (JsonElement e : el.getAsJsonArray()) out.put(e.getAsString(), 1F);
        } else if (el.isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
                float w = e.getValue().getAsFloat();
                if (w > 0) out.put(e.getKey(), Math.min(4F, w));
            }
        }
        return out;
    }

    private static Map<String, Integer> intMap(JsonObject o) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            int v = e.getValue().getAsInt();
            if (v != 0) out.put(e.getKey(), v);
        }
        return out;
    }

    /** {@code "#E8B84A"}, {@code "E8B84A"} ou número. */
    private static int parseColor(@Nullable JsonElement el, int fallback) {
        if (el == null || el.isJsonNull()) return fallback;
        try {
            if (el.getAsJsonPrimitive().isNumber()) return el.getAsInt() & 0xFFFFFF;
            String s = el.getAsString().trim();
            if (s.startsWith("#")) s = s.substring(1);
            if (s.startsWith("0x") || s.startsWith("0X")) s = s.substring(2);
            return Integer.parseInt(s, 16) & 0xFFFFFF;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private FactionRegistry() {}
}
