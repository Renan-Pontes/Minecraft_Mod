package com.renanpontes.isekaimahou.feature.renown;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.ToLongFunction;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.api.Reputation;
import com.renanpontes.isekaimahou.feature.endless_abyss.AbyssRecords;
import com.renanpontes.isekaimahou.feature.renown.RenownData.BossRecord;
import com.renanpontes.isekaimahou.feature.renown.RenownData.PlayerRow;
import com.renanpontes.isekaimahou.feature.renown.RenownNetwork.BossRow;
import com.renanpontes.isekaimahou.feature.renown.RenownNetwork.Category;
import com.renanpontes.isekaimahou.feature.renown.RenownNetwork.Row;
import com.renanpontes.isekaimahou.feature.settlements.Settlement;
import com.renanpontes.isekaimahou.feature.settlements.SettlementData;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * O <b>Ranking</b> — "Ranking dos players é legal pra geral saber". Categorias: maior nível, chefes mortos, andar do
 * Abismo, torres conquistadas, cidade mais rica e cidade mais populosa (+ os recordes de tempo por chefe, de
 * {@link BossTimer}).
 * <p>
 * Os números dos jogadores vêm dos contadores do {@code MagicData}; como ele só existe com o jogador online, cada um
 * tem uma "foto" guardada em {@link RenownData} (atualizada ao entrar, ao sair e de tempos em tempos), e é dela que o
 * quadro é montado. Nada aqui varre o mundo: só mapas em memória.
 */
public final class RenownRanking {
    public static final String LEVEL = "level", BOSSES = "bosses", ABYSS = "abyss", TOWERS = "towers",
            CITY_RICH = "city_rich", CITY_POP = "city_pop";
    /** Linhas por categoria. */
    public static final int TOP = 10;
    /** Contadores das torres (módulo towers; lidos por texto). */
    private static final String TOWERS_CLEARED = "towers.cleared", TOWERS_FLOOR = "towers.floor";
    private static final String ABYSS_BEST = "abyss.best_floor";

    /** Último pedido do quadro por jogador (tick do servidor): a aba pede sozinha, não precisa montar a cada clique. */
    private static final Map<UUID, Integer> LAST_REQUEST = new HashMap<>();

    private RenownRanking() {}

    // ------------------------------------------------------------------------------------------------ foto do jogador
    /** Copia os números do jogador para a foto do ranking. Barato: um passeio pelos contadores dele. */
    public static PlayerRow snapshot(ServerPlayer p) {
        RenownData data = RenownData.get(p.server);
        PlayerRow r = data.row(p.getUUID());
        MagicData d = MagicApi.get(p);
        int kills = 0, kinds = 0;
        for (Map.Entry<String, Integer> e : d.counters().entrySet()) {
            if (e.getKey().startsWith("boss.") && e.getValue() > 0) {
                kills += e.getValue();
                kinds++;
            }
        }
        String name = p.getGameProfile().getName();
        String title = Reputation.selectedTitleKey(p);
        if (title == null) title = "";
        int abyss = Math.max(d.counter(ABYSS_BEST), abyssRecord(p.server, p.getUUID()));
        int towers = d.counter(TOWERS_CLEARED), floors = d.counter(TOWERS_FLOOR);
        boolean changed = !name.equals(r.name) || r.level != d.level() || r.circle != d.circle() || !d.path().id.equals(r.path)
                || r.bossKills != kills || r.bossKinds != kinds || r.towers != towers || r.towerFloors != floors || r.abyss != abyss
                || !title.equals(r.title);
        if (changed) {
            r.name = name;
            r.level = d.level();
            r.circle = d.circle();
            r.path = d.path().id;
            r.bossKills = kills;
            r.bossKinds = kinds;
            r.towers = towers;
            r.towerFloors = floors;
            r.abyss = abyss;
            r.title = title;
            data.setDirty();
        }
        return r;
    }

    /** Recorde do Abismo guardado pelo módulo endless_abyss (vale também para quem está offline). */
    private static int abyssRecord(MinecraftServer server, UUID id) {
        try {
            return AbyssRecords.bestFloor(server, id);
        } catch (RuntimeException | LinkageError e) {
            return 0;
        }
    }

    // ------------------------------------------------------------------------------------------------ o quadro
    /** Manda o quadro para o jogador. {@code open} = abrir a aba (clique no bloco). */
    public static void sendBoard(ServerPlayer p, boolean open) {
        int now = p.server.getTickCount();
        Integer last = LAST_REQUEST.get(p.getUUID());
        if (!open && last != null && now - last < 10) return;
        LAST_REQUEST.put(p.getUUID(), now);
        RenownNetwork.send(p, build(p, open));
    }

    static void forget(UUID id) {
        LAST_REQUEST.remove(id);
    }

    static RenownNetwork.Board build(ServerPlayer viewer, boolean open) {
        MinecraftServer server = viewer.server;
        snapshot(viewer);
        RenownData data = RenownData.get(server);
        UUID me = viewer.getUUID();
        List<Category> cats = new ArrayList<>();
        cats.add(players(data, me, LEVEL, r -> r.level, r -> r.circle));
        cats.add(players(data, me, BOSSES, r -> r.bossKills, r -> r.bossKinds));
        cats.add(abyss(server, data, me));
        cats.add(players(data, me, TOWERS, r -> r.towers, r -> r.towerFloors));
        addCities(server, me, cats);

        List<BossRow> bosses = new ArrayList<>();
        PlayerRow mine = data.players.get(me);
        for (Map.Entry<String, BossRecord> e : data.bosses.entrySet()) {
            BossRecord rec = e.getValue();
            bosses.add(new BossRow(e.getKey(), rec.ticks, clip(String.join(", ", rec.names), 120), rec.day,
                    mine == null ? 0 : mine.bossBest.getOrDefault(e.getKey(), 0)));
        }
        return new RenownNetwork.Board(cats, bosses, open);
    }

    private interface Aux {
        int of(PlayerRow r);
    }

    /** Categoria de jogadores: maior valor primeiro; empate pelo nome (ordem estável). Só entra quem tem valor > 0. */
    private static Category players(RenownData data, UUID me, String id, ToLongFunction<PlayerRow> value, Aux aux) {
        List<Map.Entry<UUID, PlayerRow>> list = new ArrayList<>();
        for (Map.Entry<UUID, PlayerRow> e : data.players.entrySet()) {
            // Nível 1 é o começo de todo mundo: não é feito para o quadro
            long v = value.applyAsLong(e.getValue());
            if (v > (LEVEL.equals(id) ? 1 : 0)) list.add(e);
        }
        list.sort(Comparator.<Map.Entry<UUID, PlayerRow>>comparingLong(e -> -value.applyAsLong(e.getValue()))
                .thenComparing(e -> e.getValue().name));
        List<Row> rows = new ArrayList<>();
        int selfRank = 0;
        for (int i = 0; i < list.size(); i++) {
            Map.Entry<UUID, PlayerRow> e = list.get(i);
            boolean self = e.getKey().equals(me);
            if (self) selfRank = i + 1;
            if (i < TOP) {
                PlayerRow r = e.getValue();
                rows.add(new Row(clip(r.name, 60), r.title, value.applyAsLong(r), r.path, aux.of(r), self));
            }
        }
        PlayerRow mine = data.players.get(me);
        return new Category(id, rows, selfRank, mine == null ? 0 : value.applyAsLong(mine));
    }

    /** Andar do Abismo: usa o ranking do próprio módulo (desempata por quem chegou antes); sem ele, a foto. */
    private static Category abyss(MinecraftServer server, RenownData data, UUID me) {
        List<AbyssRecords.Entry> top;
        try {
            top = AbyssRecords.top(server, 4096);
        } catch (RuntimeException | LinkageError e) {
            IsekaiMahou.LOGGER.debug("[renown] ranking do Abismo indisponível", e);
            return players(data, me, ABYSS, r -> r.abyss, r -> 0);
        }
        List<Row> rows = new ArrayList<>();
        int selfRank = 0;
        long selfValue = 0;
        for (int i = 0; i < top.size(); i++) {
            AbyssRecords.Entry e = top.get(i);
            boolean self = e.player().equals(me);
            if (self) {
                selfRank = i + 1;
                selfValue = e.bestFloor();
            }
            if (i < TOP) {
                PlayerRow r = data.players.get(e.player());
                String name = r != null && !r.name.isEmpty() ? r.name : e.name();
                rows.add(new Row(clip(name, 60), r == null ? "" : r.title, e.bestFloor(), r == null ? "" : r.path, e.shadowWins(), self));
            }
        }
        return new Category(ABYSS, rows, selfRank, selfValue);
    }

    /** Cidades com dono (fundadas ou conquistadas por jogadores): mais rica (tesouro) e mais populosa. */
    private static void addCities(MinecraftServer server, UUID me, List<Category> cats) {
        List<Settlement> cities = new ArrayList<>();
        try {
            for (Settlement s : SettlementData.get(server).all()) if (s.owner != null) cities.add(s);
        } catch (RuntimeException | LinkageError e) {
            IsekaiMahou.LOGGER.debug("[renown] cidades indisponíveis", e);
        }
        cats.add(cities(cities, me, CITY_RICH, s -> s.treasury));
        cats.add(cities(cities, me, CITY_POP, RenownRanking::population));
    }

    /** Habitantes da cidade (a população simulada; em mundo antigo, sem ela, os NPCs visíveis). */
    static int population(Settlement s) {
        return s.people >= 0 ? s.people : s.totalPopulation();
    }

    private static Category cities(List<Settlement> all, UUID me, String id, ToLongFunction<Settlement> value) {
        List<Settlement> list = new ArrayList<>();
        for (Settlement s : all) if (value.applyAsLong(s) > 0) list.add(s);
        list.sort(Comparator.<Settlement>comparingLong(s -> -value.applyAsLong(s)).thenComparing(s -> s.name));
        List<Row> rows = new ArrayList<>();
        int selfRank = 0;
        long selfValue = 0;
        for (int i = 0; i < list.size(); i++) {
            Settlement s = list.get(i);
            boolean self = me.equals(s.owner);
            // Quem tem várias cidades: vale a mais bem colocada
            if (self && selfRank == 0) {
                selfRank = i + 1;
                selfValue = value.applyAsLong(s);
            }
            if (i < TOP) rows.add(new Row(clip(s.name, 60), "", value.applyAsLong(s), clip(s.ownerName, 60), s.level, self));
        }
        return new Category(id, rows, selfRank, selfValue);
    }

    static String clip(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
