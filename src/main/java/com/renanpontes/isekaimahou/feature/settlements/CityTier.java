package com.renanpontes.isekaimahou.feature.settlements;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.core.api.Cutscenes;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.feature.settlements.build.Blueprint;
import com.renanpontes.isekaimahou.feature.settlements.build.Blueprints;
import com.renanpontes.isekaimahou.feature.settlements.build.CityLayout;
import com.renanpontes.isekaimahou.feature.settlements.build.TierLook;
import com.renanpontes.isekaimahou.feature.ui_hud.Hints;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * DEGRAUS da cidade fundada (onda 5 — "temos que dar upgrades nela, pra que saiam de uma vila pra uma cidade... e uma
 * mudança de lvl mais significativa"). O degrau É o nível do centro (1..5, o mesmo número salvo de sempre), agora com
 * nome próprio e peso de verdade:
 * <pre>
 *   1 Acampamento → 2 Vila → 3 Burgo → 4 Cidade → 5 Capital
 * </pre>
 * <ul>
 *   <li><b>Nome</b> no letreiro do lugar e no mapa ({@link #placeName}), no painel e no anúncio do servidor;</li>
 *   <li><b>Requisitos que pesam</b> ({@link #reqs}, somados aos de {@code CityEconomy.centerReqs}): prédios-chave,
 *       tamanho, cofre (gasto na reforma) e um GRANDE PROJETO por degrau;</li>
 *   <li><b>O que cada degrau libera</b> ({@link #gains}): prédios, nível dos prédios, construtores, anéis, moradores e
 *       guardas, caravanas, defesa e o visual (ruas, postes, praça, muralha — {@link CityRenewal});</li>
 *   <li><b>Cerimônia</b> curta e pulável ao subir ({@link #celebrate}).</li>
 * </ul>
 * Cidades que já existem mantêm o nível: só ganham o nome e, aos poucos, o visual do degrau delas.
 */
public final class CityTier {
    public static final int MAX = Blueprints.MAX_LEVEL;
    /** Ids dos degraus (chaves de texto {@code rank.<id>}). */
    private static final String[] IDS = {"camp", "village", "town", "city", "capital"};
    /** Até quantos anéis a cidade pode crescer em cada degrau (5×5, 7×7, 11×11, 17×17, 25×25). */
    private static final int[] MAX_RADIUS = {2, 3, 5, 8, CityLayout.MAX_RADIUS};
    /** Moedas do cofre gastas para SUBIR para cada degrau. */
    private static final int[] COST = {0, 40, 150, 400, 900};
    /** Tamanho (anéis) pedido para subir para cada degrau: Burgo 7×7, Cidade 9×9, Capital 11×11. */
    private static final int[] NEED_RADIUS = {0, 2, 3, 4, 5};
    /** Prédios-chave pedidos para subir para cada degrau. */
    private static final String[][] KEY_BUILDINGS = {{}, {"builders_hall", "farm"}, {"market", "barracks"}, {"temple", "smithy"},
            {"guild", "magic_tower"}};

    public static int clamp(int level) {
        return Math.max(1, Math.min(MAX, level));
    }

    public static String id(int level) {
        return IDS[clamp(level) - 1];
    }

    /** Nome do degrau: Acampamento, Vila, Burgo, Cidade, Capital. */
    public static MutableComponent name(int level) {
        return SettlementsLang.tr("rank." + id(level));
    }

    /** Nome do lugar no letreiro e no mapa: "Brisa · Vila". */
    public static Component placeName(Settlement s) {
        return SettlementsLang.tr("rank.place", Component.literal(s.name), name(s.level));
    }

    public static int maxRadius(int level) {
        return MAX_RADIUS[clamp(level) - 1];
    }

    /** Degrau que libera o anel {@code ring}. */
    public static int levelForRing(int ring) {
        for (int l = 1; l <= MAX; l++) if (ring <= maxRadius(l)) return l;
        return MAX;
    }

    /** Moedas do cofre gastas para subir PARA o degrau {@code toLevel}. */
    public static int cost(int toLevel) {
        return COST[clamp(toLevel) - 1];
    }

    // ---- moradores e guardas a mais por degrau (somados em Settlement.cap)
    public static int guards(int level) {
        return clamp(level) - 1;
    }

    /** Mascates: um no Burgo, dois na Capital (ficam na praça mesmo sem Mercado). */
    public static int merchants(int level) {
        return level >= 5 ? 2 : level >= 3 ? 1 : 0;
    }

    public static int residents(int level) {
        return 2 * (clamp(level) - 1);
    }

    // =====================================================================================
    //  Requisitos do próximo degrau
    // =====================================================================================
    /** Requisitos DO DEGRAU para a cidade subir do nível atual para o próximo (vazio na Capital). */
    public static List<CityEconomy.Req> reqs(MinecraftServer server, Settlement s) {
        List<CityEconomy.Req> out = new ArrayList<>();
        int to = s.level + 1;
        if (!s.own() || to > MAX) return out;
        for (String key : KEY_BUILDINGS[to - 1]) {
            Blueprint bp = Blueprints.get(s.style, key);
            if (bp != null) out.add(new CityEconomy.Req(SettlementsLang.tr("rank.req.building", bp.displayName()), s.has(key)));
        }
        int need = NEED_RADIUS[to - 1];
        if (need > CityLayout.START_RADIUS) {
            out.add(new CityEconomy.Req(SettlementsLang.tr("rank.req.size", 2 * s.radius + 1, 2 * need + 1), s.radius >= need));
        }
        out.add(project(server, s, to));
        int cost = cost(to);
        out.add(new CityEconomy.Req(SettlementsLang.tr("rank.req.treasury", s.treasury, cost), s.treasury >= cost));
        return out;
    }

    /** O GRANDE PROJETO de cada degrau: uma obra ou um feito da cidade inteira. */
    private static CityEconomy.Req project(MinecraftServer server, Settlement s, int to) {
        switch (to) {
            case 2:
                return new CityEconomy.Req(SettlementsLang.tr("rank.project.festival", Math.min(1, s.festivals), 1), s.festivals >= 1);
            case 3:
                return new CityEconomy.Req(SettlementsLang.tr("rank.project.wall", Math.min(1, s.walls.size()), 1), !s.walls.isEmpty());
            case 4:
                // mundo sem ataques de monstros: o feito vira comércio (senão o requisito seria impossível)
                if (!SettlementData.get(server).raidsEnabled) {
                    return new CityEconomy.Req(SettlementsLang.tr("rank.project.trade", Math.min(6, s.caravansSent), 6), s.caravansSent >= 6);
                }
                return new CityEconomy.Req(SettlementsLang.tr("rank.project.siege", Math.min(2, s.raidsWon), 2), s.raidsWon >= 2);
            default:
                return new CityEconomy.Req(SettlementsLang.tr("rank.project.citadel", Math.min(2, s.walls.size()), 2), s.walls.size() >= 2);
        }
    }

    // =====================================================================================
    //  O que o degrau libera
    // =====================================================================================
    /** "O que você ganha" ao chegar no degrau {@code to} (linhas curtas para o painel). */
    public static List<Component> gains(Settlement s, int to) {
        List<Component> out = new ArrayList<>();
        to = clamp(to);
        MutableComponent names = Component.empty();
        int n = 0;
        for (Blueprint bp : Blueprints.of(s.style)) {
            if (bp.minLevel() != to || bp.isPlaza()) continue;
            if (n++ > 0) names.append(", ");
            names.append(bp.displayName());
        }
        if (n > 0) out.add(SettlementsLang.tr("rank.gain.buildings", names));
        if (to > 1) {
            out.add(SettlementsLang.tr("rank.gain.blevel", to));
            out.add(SettlementsLang.tr("rank.gain.builders"));
            if (maxRadius(to) > maxRadius(to - 1)) {
                int size = 2 * maxRadius(to) + 1;
                out.add(SettlementsLang.tr("rank.gain.size", size, size));
            }
            int plots = 0;
            for (int c = 0; c < CityLayout.CELLS; c++) if (c != CityLayout.CENTER_CELL && CityLayout.unlockLevel(c) == to) plots++;
            if (plots > 0) out.add(SettlementsLang.tr("rank.gain.plots", plots));
            int merch = merchants(to) - merchants(to - 1);
            out.add(merch > 0 ? SettlementsLang.tr("rank.gain.people_merchant") : SettlementsLang.tr("rank.gain.people"));
            if (TierLook.wallTier(to) != TierLook.wallTier(to - 1) || to == 2) out.add(SettlementsLang.tr("rank.gain.wall." + TierLook.wallTier(to)));
            if (to >= 4) out.add(SettlementsLang.tr("rank.gain.caravan"));
            // onda 6: cada degrau deixa mais monumentos ativos (Vila 1, Burgo 2, Cidade 3, Capital 5)
            if (CityMonuments.limit(to) > CityMonuments.limit(to - 1)) out.add(SettlementsLang.tr("mon.gain", CityMonuments.limit(to)));
        }
        // onda 6 (city_trades): comerciantes de ofício — estoque maior e, nos degraus altos, itens raros
        Component trade = CityTrades.rankGain(to);
        if (trade != null) out.add(trade);
        out.add(SettlementsLang.tr("rank.gain.look." + to));
        return out;
    }

    // =====================================================================================
    //  Subir de degrau
    // =====================================================================================
    /**
     * A cidade acabou de mudar de degrau (reforma do centro pronta, ou comando): nome novo no letreiro e no mapa,
     * anúncio no servidor, cerimônia curta e PULÁVEL para quem está por perto (ninguém perde o controle), fogos e
     * enfeites até o fim do dia, e a reforma da cidade inteira começa pela praça.
     */
    public static void celebrate(ServerLevel sl, Settlement s, int from) {
        MinecraftServer server = sl.getServer();
        if (!s.own()) return;
        SettlementService.registerPlace(sl, s);
        CityRenewal.restart(s);
        if (s.level <= from) return;   // rebaixada por comando: só refaz o visual, sem festa
        long today = SettlementService.day(server);
        s.tierDay = today;
        Component city = SettlementService.cityName(server, s).copy().withColor(s.style.color);
        MutableComponent rank = name(s.level).withColor(Rewards.GOLD);
        server.getPlayerList().broadcastSystemMessage(s.ownerName.isEmpty() ? SettlementsLang.tr("rank.announce.free", city, rank)
                : SettlementsLang.tr("rank.announce", city, s.ownerName, rank), false);
        for (ServerPlayer g : CityEconomy.governors(server, s)) {
            Rewards.banner(g, SettlementsLang.tr("rank.up", SettlementService.cityName(server, s), name(s.level)), s.style.color);
            Hints.show(g, "city_tier");
        }
        double r = CityLayout.half(s) + 16;
        for (ServerPlayer p : sl.players()) {
            if (p.distanceToSqr(s.heart.getX() + 0.5, s.heart.getY(), s.heart.getZ() + 0.5) > r * r) continue;
            Cutscenes.play(p, "ascension", p, name(s.level), SettlementsLang.tr("rank.up.sub", SettlementService.cityName(server, s)),
                    s.style.color, 60, false);
        }
        sl.playSound(null, s.heart, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 2.5F, 1.0F);
        sl.playSound(null, s.heart, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.BLOCKS, 1.5F, 1.0F);
        for (int i = 0; i < 4 + s.level; i++) CityDecor.firework(sl, s);
        // enfeites de festa e fogos à noite até o fim do dia (se já há festival, ele manda)
        if (!CityDecor.isOn(s)) CityDecor.setUntil(s, today);
    }

    /** Põe a cidade num degrau na hora (comandos de teste): com cerimônia se subiu. Devolve o degrau novo. */
    public static int set(ServerLevel sl, Settlement s, int level) {
        int from = s.level;
        s.level = clamp(level);
        s.points = Math.max(s.points, Blueprints.pointsFor(s.style, s.level));
        celebrate(sl, s, from);
        return s.level;
    }

    private CityTier() {}
}
