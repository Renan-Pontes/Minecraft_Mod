package com.renanpontes.isekaimahou.feature.renown;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.core.api.Reputation;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.core.reputation.ReputationService;
import com.renanpontes.isekaimahou.feature.renown.RenownNetwork.TitleRow;
import com.renanpontes.isekaimahou.feature.settlements.Settlement;
import com.renanpontes.isekaimahou.feature.settlements.SettlementData;
import com.renanpontes.isekaimahou.feature.ui_hud.Hints;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * <b>Títulos por feitos</b> — "Títulos são legais". Cada título tem um requisito (contadores do {@code MagicData},
 * nível, a cidade do jogador, recordes de chefe); ao cumprir, o título é liberado e fica na lista de títulos do
 * jogador — a MESMA lista dos títulos de reputação ({@link Reputation#grantTitle}), então o jogador tem um título em
 * uso só, escolhido na aba Renome do menu B. Títulos não dão poder nenhum: são só para mostrar.
 * <p>
 * Chave de tradução do título: {@code renown.isekai_mahou.title.<id>} (+ {@code .desc} com o requisito).
 */
public final class RenownTitles {
    public static final int COMMON = 0, RARE = 1, EPIC = 2, LEGENDARY = 3;
    /** Título que não é do Renome (reputação, chefes...): dourado, como no resto do mod. */
    public static final int OTHER = -1;
    private static final int[] TIER_RGB = {0xD8D8D8, 0x6FB7FF, 0xC08BFF, 0xFFD86B};
    public static final String KEY_PREFIX = "renown.isekai_mahou.title.";
    /** Contador: quantas vezes o jogador bateu o recorde do servidor numa luta de chefe. */
    public static final String COUNTER_WORLD_RECORDS = "renown.world_records";

    /** O que o requisito pode olhar (montado uma vez por checagem). */
    record Ctx(ServerPlayer player, MagicData data, int bossKills, int bossKinds, int worlds, int personalBests,
               @Nullable Settlement city) {
        int counter(String key) {
            return data.counter(key);
        }
    }

    private interface Progress {
        int of(Ctx c);
    }

    /** Um título: requisito cumprido quando {@code progress >= goal}. */
    public record Def(String id, int tier, int goal, Progress progress) {
        public String key() {
            return KEY_PREFIX + id;
        }
    }

    private static final Map<String, Def> DEFS = new LinkedHashMap<>();

    private static void def(String id, int tier, int goal, Progress progress) {
        DEFS.put(KEY_PREFIX + id, new Def(id, tier, goal, progress));
    }

    static {
        // Jornada (upar é o requisito principal: "upar tem que ser mais requisito que itens")
        def("awakened", COMMON, 10, c -> c.data.level());
        def("veteran", RARE, 50, c -> c.data.level());
        def("living_legend", EPIC, 100, c -> c.data.level());
        def("beyond_limits", LEGENDARY, Progression.MAX_LEVEL, c -> c.data.level());
        def("circle_master", RARE, 5, c -> c.data.circle());
        def("archmage", LEGENDARY, Progression.MAX_CIRCLE, c -> c.data.circle());
        // Combate
        def("monster_hunter", COMMON, 500, c -> c.counter("kill.total"));
        def("exterminator", EPIC, 10000, c -> c.counter("kill.total"));
        def("boss_slayer", COMMON, 1, c -> c.bossKills);
        def("boss_hunter", RARE, 10, c -> c.bossKinds);
        def("calamity", EPIC, 100, c -> c.bossKills);
        def("parry_master", RARE, 100, c -> c.counter("feat.parry"));
        def("iron_wall", RARE, 500, c -> c.counter("feat.guard_block"));
        def("duelist", RARE, 10, c -> c.counter("duel.win"));
        def("returned", RARE, 5, c -> c.counter("afterlife.returned"));
        // Recordes de tempo
        def("timekeeper", RARE, 10, c -> c.personalBests);
        def("lightning", EPIC, 1, c -> c.counter(COUNTER_WORLD_RECORDS));
        // Torres, Abismo, provações, mundos
        def("tower_conqueror", RARE, 1, c -> c.counter("towers.cleared"));
        def("tower_lord", EPIC, 5, c -> c.counter("towers.cleared"));
        def("abyss_diver", RARE, 10, c -> c.counter("abyss.best_floor"));
        def("abyss_walker", EPIC, 50, c -> c.counter("abyss.best_floor"));
        def("abyss_sovereign", LEGENDARY, 100, c -> c.counter("abyss.best_floor"));
        def("shadow_breaker", EPIC, 10, c -> c.counter("abyss.shadow_wins"));
        def("trial_champion", RARE, 5, c -> c.counter("trial.clear"));
        def("world_walker", RARE, 4, c -> c.worlds);
        // Cidade ("a cidade do player deve ser a mecânica mais legal após as magias")
        def("founder", COMMON, 1, c -> c.city == null ? 0 : 1);
        def("city_architect", RARE, 7, c -> c.city == null ? 0 : c.city.radius);
        def("great_lord", EPIC, 100, c -> c.city == null ? 0 : RenownRanking.population(c.city));
        def("tycoon", EPIC, 50000, c -> c.city == null ? 0 : c.city.treasury);
        def("festival_host", RARE, 3, c -> c.city == null ? 0 : c.city.festivals);
        def("road_merchant", RARE, 10, c -> c.city == null ? 0 : c.city.caravansSent);
        def("city_protector", RARE, 5, c -> c.city == null ? 0 : c.city.raidsWon);
        def("conqueror", EPIC, 1, c -> c.counter("settlements.lord"));
        // Povo e Guilda
        def("guild_hero", RARE, 50, c -> c.counter("guild_quests"));
        def("friend_of_the_people", RARE, 20, c -> c.counter("cities.sidequest"));
        def("city_defender", RARE, 3, c -> c.counter("cities.siege_won"));
        // Ofícios
        def("alchemist", COMMON, 100, c -> c.counter("brew"));
        def("scholar", COMMON, 100, c -> c.counter("study"));
        def("ascetic", COMMON, 200, c -> c.counter("meditate"));
    }

    /** Jogadores cujo progresso mudou (contador, nível...) e precisam de nova checagem. */
    private static final Set<UUID> DIRTY = new HashSet<>();
    /** Título em uso de quem está online (o que os clientes já sabem). */
    private static final Map<UUID, String> CURRENT = new HashMap<>();

    private RenownTitles() {}

    // ------------------------------------------------------------------------------------------------ consulta
    public static List<Def> all() {
        return List.copyOf(DEFS.values());
    }

    /** Raridade do título pela chave ({@link #OTHER} se não é do Renome). */
    public static int tierOf(String key) {
        Def d = DEFS.get(key);
        return d == null ? OTHER : d.tier;
    }

    public static int tierColor(int tier) {
        return tier < 0 || tier >= TIER_RGB.length ? Rewards.GOLD : TIER_RGB[tier];
    }

    /** O título, traduzido e na cor da raridade. */
    public static MutableComponent display(String key) {
        return Component.translatable(key).withColor(tierColor(tierOf(key)));
    }

    /** Título em uso do jogador online ("" = nenhum). */
    public static String current(UUID player) {
        return CURRENT.getOrDefault(player, "");
    }

    // ------------------------------------------------------------------------------------------------ checagem
    static void markDirty(ServerPlayer p) {
        DIRTY.add(p.getUUID());
    }

    private static Ctx ctx(ServerPlayer p) {
        MagicData d = MagicApi.get(p);
        int kills = 0, kinds = 0, worlds = 0;
        for (Map.Entry<String, Integer> e : d.counters().entrySet()) {
            if (e.getValue() <= 0) continue;
            String k = e.getKey();
            if (k.startsWith("boss.")) {
                kills += e.getValue();
                kinds++;
            } else if (k.startsWith("visit." + IsekaiMahou.MODID + ".")) {
                worlds++;
            }
        }
        Settlement city = null;
        try {
            city = SettlementData.get(p.server).ownedBy(p.getUUID());
        } catch (RuntimeException e) {
            IsekaiMahou.LOGGER.debug("[renown] cidades indisponíveis", e);
        }
        RenownData.PlayerRow row = RenownData.get(p.server).players.get(p.getUUID());
        return new Ctx(p, d, kills, kinds, worlds, row == null ? 0 : row.bossBest.size(), city);
    }

    /** Libera os títulos cujo requisito o jogador já cumpre. Devolve quantos foram liberados agora. */
    public static int check(ServerPlayer p) {
        // Sem classe ainda (tela de entrada aberta): nada de avisos por cima dela
        if (!MagicApi.get(p).path().isPlayable()) return 0;
        Ctx c = ctx(p);
        List<Def> earned = new ArrayList<>();
        for (Def def : DEFS.values()) {
            if (Reputation.hasTitle(p, def.key())) continue;
            if (progress(def, c) >= def.goal) earned.add(def);
        }
        if (earned.isEmpty()) return 0;
        if (earned.size() == 1) {
            // Um título: o aviso normal (uma linha no chat + o cartão de recompensa)
            Reputation.grantTitle(p, earned.get(0).key());
        } else {
            // Vários de uma vez (mundo antigo, comando): UM aviso só — nada "explodindo na tela"
            for (Def def : earned) ReputationService.grantTitle(p, def.key(), false);
            p.sendSystemMessage(Component.translatable("renown.isekai_mahou.titles.many", earned.size()).withStyle(ChatFormatting.GOLD));
        }
        Hints.show(p, "renown.title");
        return earned.size();
    }

    private static int progress(Def def, Ctx c) {
        try {
            return Math.max(0, def.progress.of(c));
        } catch (RuntimeException e) {
            return 0;
        }
    }

    // ------------------------------------------------------------------------------------------------ lista (aba)
    static void sendList(ServerPlayer p) {
        Ctx c = ctx(p);
        List<TitleRow> rows = new ArrayList<>();
        Set<String> listed = new HashSet<>();
        for (Def def : DEFS.values()) {
            boolean unlocked = Reputation.hasTitle(p, def.key());
            rows.add(new TitleRow(def.key(), def.tier, unlocked, unlocked ? def.goal : Math.min(def.goal, progress(def, c)), def.goal, true));
            listed.add(def.key());
        }
        // Títulos de outros sistemas (reputação, chefes): entram na mesma lista, já liberados
        for (String key : Reputation.titles(p)) {
            if (listed.add(key)) rows.add(new TitleRow(key, OTHER, true, 1, 1, false));
        }
        String selected = Reputation.selectedTitleKey(p);
        RenownNetwork.send(p, new RenownNetwork.Titles(rows, selected == null ? "" : selected));
    }

    // ------------------------------------------------------------------------------------------------ ciclo
    /** A cada segundo: checa quem mudou e avisa os clientes de quem trocou de título. */
    static void tick(MinecraftServer server) {
        int t = server.getTickCount();
        if (t % 20 != 0) return;
        // De minuto em minuto todos são checados (títulos de cidade não vêm de contadores)
        boolean all = t % 1200 == 0;
        Map<UUID, String> changed = new HashMap<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (all || DIRTY.contains(p.getUUID())) check(p);
            String key = Reputation.selectedTitleKey(p);
            if (key == null) key = "";
            String old = CURRENT.put(p.getUUID(), key);
            if (!key.equals(old == null ? "" : old)) {
                changed.put(p.getUUID(), key);
                RenownRanking.snapshot(p);
            }
        }
        DIRTY.clear();
        if (!changed.isEmpty()) PacketDistributor.sendToAllPlayers(new RenownNetwork.TitleSync(false, changed));
    }

    static void onLogin(ServerPlayer p) {
        String key = Reputation.selectedTitleKey(p);
        // A lista inteira para quem chegou; os outros ficam sabendo dele no próximo tick() (CURRENT ainda não o tem)
        Map<UUID, String> full = new HashMap<>();
        CURRENT.forEach((id, k) -> {
            if (!k.isEmpty()) full.put(id, k);
        });
        if (key != null) full.put(p.getUUID(), key);
        RenownNetwork.send(p, new RenownNetwork.TitleSync(true, full));
        markDirty(p);
    }

    static void onLogout(ServerPlayer p) {
        DIRTY.remove(p.getUUID());
        String old = CURRENT.remove(p.getUUID());
        if (old != null && !old.isEmpty()) {
            PacketDistributor.sendToAllPlayers(new RenownNetwork.TitleSync(false, Map.of(p.getUUID(), "")));
        }
    }

    static void clear() {
        DIRTY.clear();
        CURRENT.clear();
    }
}
