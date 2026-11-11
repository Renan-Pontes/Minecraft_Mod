package com.renanpontes.isekaimahou.feature.guild.quest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.api.Reputation;
import com.renanpontes.isekaimahou.feature.guild.GuildFaction;
import com.renanpontes.isekaimahou.feature.guild.GuildRank;

import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Registro das missões ({@code data/<ns>/guild_quests}) e das ofertas da loja ({@code data/<ns>/guild_shop}). Lado SERVIDOR.
 * O quadro do dia é sorteado de forma determinística a partir da semente do mundo, do dia e do rank:
 * todos os jogadores do mesmo rank veem o mesmo quadro, que muda à meia-noite do jogo.
 */
public final class GuildQuests {
    /** Missões abertas no quadro por dia (para o rank do jogador e os 2 abaixo). */
    public static final int BOARD_SIZE = 6;
    /** Missões do rank seguinte mostradas trancadas (para dar vontade de subir). */
    public static final int BOARD_LOCKED = 2;

    private static volatile Map<ResourceLocation, GuildQuest> quests = Map.of();
    private static volatile List<ShopOffer> shop = List.of();
    private static final Map<Long, Board> BOARDS = new HashMap<>();
    private static final Map<Long, Board> FACTION_BOARDS = new HashMap<>();

    public record Board(List<GuildQuest> open, List<GuildQuest> locked) {}

    public static GuildQuest get(ResourceLocation id) {
        return id == null ? null : quests.get(id);
    }

    public static List<ShopOffer> shop() {
        return shop;
    }

    /** Dia atual do mundo (Overworld). */
    public static long day(MinecraftServer server) {
        return server.overworld().getDayTime() / 24000L;
    }

    /** Quadro do dia para um rank. */
    public static synchronized Board board(MinecraftServer server, GuildRank rank) {
        long day = day(server);
        long key = day * 16 + rank.ordinal();
        Board cached = BOARDS.get(key);
        if (cached != null) return cached;
        if (BOARDS.size() > 64) BOARDS.clear();

        RandomSource rnd = RandomSource.create(server.overworld().getSeed() ^ (day * 0x9E3779B97F4A7C15L) ^ (rank.ordinal() * 0x632BE59BD9B4E019L));
        List<GuildQuest> pool = new ArrayList<>();
        List<GuildQuest> above = new ArrayList<>();
        for (GuildQuest q : quests.values()) {
            if (q.trial() || !q.isGuild() || !q.isAvailable(server)) continue;
            int diff = rank.ordinal() - q.rank().ordinal();
            if (diff >= 0 && diff <= 2) pool.add(q);
            else if (diff == -1) above.add(q);
        }
        List<GuildQuest> open = pickWeighted(pool, BOARD_SIZE, rnd, q -> {
            int diff = rank.ordinal() - q.rank().ordinal();
            return q.weight() * (diff == 0 ? 4 : diff == 1 ? 2 : 1);
        });
        // O quadro fica em ordem: rank mais alto primeiro.
        open.sort((a, b) -> Integer.compare(b.rank().ordinal(), a.rank().ordinal()));
        List<GuildQuest> locked = pickWeighted(above, BOARD_LOCKED, rnd, GuildQuest::weight);
        Board board = new Board(List.copyOf(open), List.copyOf(locked));
        BOARDS.put(key, board);
        return board;
    }

    /**
     * Quadro do dia de uma facção que não é a Guilda, para quem está no patamar {@code standing}: até {@link #BOARD_SIZE}
     * missões liberadas (patamar exigido &lt;= o do jogador; as do patamar dele pesam ×3) e {@link #BOARD_LOCKED} do
     * patamar seguinte, trancadas. Igual para todos no mesmo patamar; muda à meia-noite.
     */
    public static synchronized Board factionBoard(MinecraftServer server, GuildFaction faction, Reputation.Standing standing) {
        long day = day(server);
        long key = (day * 8 + faction.ordinal()) * 16 + standing.ordinal();
        Board cached = FACTION_BOARDS.get(key);
        if (cached != null) return cached;
        if (FACTION_BOARDS.size() > 128) FACTION_BOARDS.clear();
        RandomSource rnd = RandomSource.create(server.overworld().getSeed() ^ (day * 0xD1B54A32D192ED03L)
                ^ ((faction.ordinal() + 1L) * 0x8CB92BA72F3D8DD7L) ^ (standing.ordinal() * 0x632BE59BD9B4E019L));
        List<GuildQuest> pool = new ArrayList<>();
        List<GuildQuest> above = new ArrayList<>();
        Reputation.Standing next = standing.next();
        for (GuildQuest q : quests.values()) {
            if (q.faction() != faction || q.trial() || !q.isAvailable(server)) continue;
            if (standing.atLeast(q.standing())) pool.add(q);
            else if (next != null && next.atLeast(q.standing())) above.add(q);
        }
        List<GuildQuest> open = pickWeighted(pool, BOARD_SIZE, rnd, q -> q.weight() * (q.standing() == standing ? 3 : 1));
        // Ordem: patamar mais alto primeiro, depois rank.
        open.sort((a, b) -> a.standing() != b.standing() ? Integer.compare(b.standing().ordinal(), a.standing().ordinal())
                : Integer.compare(b.rank().ordinal(), a.rank().ordinal()));
        List<GuildQuest> locked = pickWeighted(above, BOARD_LOCKED, rnd, GuildQuest::weight);
        Board board = new Board(List.copyOf(open), List.copyOf(locked));
        FACTION_BOARDS.put(key, board);
        return board;
    }

    /** Quantas missões existem (e estão disponíveis neste jogo) para o quadro de uma facção. */
    public static int count(MinecraftServer server, GuildFaction faction) {
        int n = 0;
        for (GuildQuest q : quests.values()) if (q.faction() == faction && !q.trial() && q.isAvailable(server)) n++;
        return n;
    }

    /**
     * Prova para subir ao rank {@code target}. Entre as provas disponíveis deste rank ("trial_c", "trial_c_alt",
     * "trial_c_chamber"...) o Mestre sorteia UMA por jogador e por dia (semente: jogador + dia + rank, pesada pelo
     * "weight"): a mesma o dia inteiro — o que o jogador vê na sala do Mestre é o que ele aceita — e outra no dia seguinte.
     * Uma prova já aceita fica guardada pelo id e não muda com o dia.
     */
    public static GuildQuest trialFor(MinecraftServer server, GuildRank target, UUID player) {
        List<GuildQuest> list = trials(server, target);
        if (list.size() <= 1) return list.isEmpty() ? null : list.get(0);
        long day = day(server);
        long seed = player.getMostSignificantBits() * 0x9E3779B97F4A7C15L ^ player.getLeastSignificantBits()
                ^ (day * 0xC2B2AE3D27D4EB4FL) ^ (target.ordinal() * 0x165667B19E3779F9L);
        // pickWeighted ordena por id: o resultado não depende da ordem de carregamento dos arquivos.
        return pickWeighted(list, 1, RandomSource.create(seed), GuildQuest::weight).get(0);
    }

    /** Quantas provas diferentes o Mestre pode sortear para subir a {@code target} neste jogo. */
    public static int trialOptions(MinecraftServer server, GuildRank target) {
        return trials(server, target).size();
    }

    private static List<GuildQuest> trials(MinecraftServer server, GuildRank target) {
        List<GuildQuest> list = new ArrayList<>();
        for (GuildQuest q : quests.values()) if (q.trial() && q.rank() == target && q.isAvailable(server)) list.add(q);
        return list;
    }

    private static List<GuildQuest> pickWeighted(List<GuildQuest> source, int n, RandomSource rnd,
                                                 java.util.function.ToIntFunction<GuildQuest> weight) {
        List<GuildQuest> left = new ArrayList<>(source);
        left.sort((a, b) -> a.id().compareTo(b.id()));
        List<GuildQuest> out = new ArrayList<>();
        while (out.size() < n && !left.isEmpty()) {
            int total = 0;
            for (GuildQuest q : left) total += Math.max(1, weight.applyAsInt(q));
            int r = rnd.nextInt(total);
            for (int i = 0; i < left.size(); i++) {
                r -= Math.max(1, weight.applyAsInt(left.get(i)));
                if (r < 0) {
                    out.add(left.remove(i));
                    break;
                }
            }
        }
        return out;
    }

    private static synchronized void clearBoards() {
        BOARDS.clear();
        FACTION_BOARDS.clear();
    }

    // =====================================================================================
    //  Carregadores (AddReloadListenerEvent)
    // =====================================================================================
    public static final class QuestLoader extends SimpleJsonResourceReloadListener {
        private static final Gson GSON = new GsonBuilder().create();
        private final HolderLookup.Provider registries;

        public QuestLoader(HolderLookup.Provider registries) {
            super(GSON, "guild_quests");
            this.registries = registries;
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
            DynamicOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registries);
            Map<ResourceLocation, GuildQuest> loaded = new LinkedHashMap<>();
            List<ResourceLocation> ids = new ArrayList<>(files.keySet());
            Collections.sort(ids);
            for (ResourceLocation id : ids) {
                GuildQuest.FILE_CODEC.parse(ops, files.get(id))
                        .resultOrPartial(err -> IsekaiMahou.LOGGER.warn("Missao da Guilda ignorada {}: {}", id, err))
                        .ifPresent(q -> loaded.put(id, q.withId(id)));
            }
            quests = Collections.unmodifiableMap(loaded);
            clearBoards();
            IsekaiMahou.LOGGER.info("Guilda: {} missoes carregadas", loaded.size());
        }
    }

    public static final class ShopLoader extends SimpleJsonResourceReloadListener {
        private static final Gson GSON = new GsonBuilder().create();
        private final HolderLookup.Provider registries;

        public ShopLoader(HolderLookup.Provider registries) {
            super(GSON, "guild_shop");
            this.registries = registries;
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
            DynamicOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registries);
            List<ShopOffer> loaded = new ArrayList<>();
            List<ResourceLocation> ids = new ArrayList<>(files.keySet());
            Collections.sort(ids);
            for (ResourceLocation id : ids) {
                ShopOffer.FILE_CODEC.parse(ops, files.get(id))
                        .resultOrPartial(err -> IsekaiMahou.LOGGER.warn("Loja da Guilda: arquivo ignorado {}: {}", id, err))
                        .ifPresent(loaded::addAll);
            }
            // Ordem da loja: por rank e depois por preço.
            loaded.sort((a, b) -> a.rank() != b.rank() ? Integer.compare(a.rank().ordinal(), b.rank().ordinal())
                    : Integer.compare(a.price(), b.price()));
            // v0.9: o mesmo item (mesma quantidade e rank) vindo de dois arquivos aparece uma vez só (a oferta mais barata).
            java.util.Set<String> seen = new java.util.HashSet<>();
            loaded.removeIf(o -> o.item().isPresent()
                    && !seen.add(o.item().get().id() + "#" + o.item().get().count() + "#" + o.rank() + "#" + o.standing().map(st -> st.id).orElse("")));
            shop = List.copyOf(loaded);
            IsekaiMahou.LOGGER.info("Guilda: {} ofertas na loja", loaded.size());
        }
    }

    private GuildQuests() {}
}
