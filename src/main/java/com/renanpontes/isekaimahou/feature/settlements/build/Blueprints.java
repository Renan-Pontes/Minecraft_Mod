package com.renanpontes.isekaimahou.feature.settlements.build;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.settlements.SettlementStyle;
import com.renanpontes.isekaimahou.feature.settlements.build.Blueprint.Effects;

/**
 * Catálogo das plantas por estilo (8–12 prédios + muralha) e a tabela de níveis da cidade.
 * <p>Equilíbrio: todo estilo chega ao nível 5 com ~75 de prosperidade (Nórdico ×0,65). O Reino tem o Castelo e a
 * Torre de Vigia; o Desértico troca a Taverna e a Torre pelas 3 maravilhas (bônus fortes); o Nórdico é o mais barato.
 */
public final class Blueprints {
    public static final int MAX_LEVEL = 5;
    /** Prosperidade exigida para cada nível (índice = nível − 1), antes do multiplicador do estilo. */
    private static final int[] LEVEL_POINTS = {0, 8, 22, 45, 75};

    private static final Map<SettlementStyle, List<Blueprint>> CATALOG = new EnumMap<>(SettlementStyle.class);
    /** Templos elementais (v1.4): um por elemento base + arcano. */
    public static final List<String> ELEMENT_TEMPLES = List.of("temple_fire", "temple_water", "temple_earth", "temple_wind",
            "temple_light", "temple_shadow", "temple_arcane");
    /** Plantas que usam os lotes pequenos (11×11) das cidades de reino, na paleta que combina com o estilo. */
    public static final List<String> CITY_LOTS = List.of("smithy", "stable", "well", "garden");

    static {
        for (SettlementStyle s : SettlementStyle.values()) {
            List<Blueprint> l = new ArrayList<>();
            l.add(bp(Blueprint.PLAZA, s, 1, 0, 1, fx(0, 0, 0, 0, 0, 0, 0, 0)));
            l.add(bp("house_small", s, 1, 3, 8, fx(2, 0, 0, 0, 0, 0, 0, 0)));
            l.add(bp("builders_hall", s, 1, 4, 1, fx(0, 0, 0, 1, 25, 0, 0, 0)));
            l.add(bp("farm", s, 1, 3, 4, fx(1, 0, 0, 0, 0, 1, 0, 0)));
            l.add(bp("house_large", s, 2, 5, 6, fx(4, 0, 0, 0, 0, 0, 0, 0)));
            l.add(bp("market", s, 2, 6, 1, fx(0, 0, 2, 0, 0, 0, 10, 0)));
            l.add(bp("barracks", s, 2, 6, 1, fx(0, 3, 0, 0, 0, 0, 0, 3)));
            l.add(bp(Blueprint.WALL, s, 2, 10, 1, fx(0, 1, 0, 0, 0, 1, 0, 0)));
            if (s != SettlementStyle.DESERT) {
                l.add(bp("watchtower", s, 2, 3, 4, fx(0, 1, 0, 0, 0, 0, 0, 0)));
                l.add(bp("tavern", s, 3, 6, 1, fx(1, 0, 1, 0, 0, 2, 0, 0)));
            }
            l.add(bp("temple", s, 3, 8, 1, fx(0, 0, 0, 0, 0, 1, 0, 0)));
            l.add(bp(Blueprint.TOWN_HALL, s, 3, 10, 1, fx(0, 1, 0, 0, 0, 0, 25, 0)));
            switch (s) {
                case KINGDOM -> l.add(new Blueprint("castle", s, 5, 15, 1,
                        new Effects(0, 3, 0, 0, 0, 1, 10, 3, true, "")));
                case DESERT -> {
                    l.add(new Blueprint("obelisk", s, 3, 8, 1, new Effects(0, 0, 0, 0, 0, 1, 0, 0, false, "obelisk")));
                    l.add(new Blueprint("pyramid", s, 4, 15, 1, new Effects(0, 0, 0, 0, 0, 3, 50, 0, false, "pyramid")));
                    l.add(new Blueprint("sphinx", s, 5, 15, 1, new Effects(0, 2, 0, 0, 0, 1, 0, 2, true, "sphinx")));
                }
                case NORDIC -> { }
            }
            // v1.4: prédios novos (moldes procedurais ProcBuildings e lotes pequenos das cidades de reino)
            l.add(bp("granary", s, 1, 3, 3, fx(0, 0, 0, 0, 0, 0, 0, 0)));
            l.add(bp("well", s, 1, 2, 4, fx(0, 0, 0, 0, 0, 0, 0, 0)));
            l.add(bp("garden", s, 1, 2, 4, fx(0, 0, 0, 0, 0, 0, 0, 0)));
            l.add(bp("port", s, 1, 3, 2, fx(1, 0, 0, 0, 0, 0, 0, 0)));
            l.add(bp("smithy", s, 2, 4, 1, fx(0, 0, 1, 0, 0, 0, 0, 0)));
            l.add(bp("stable", s, 2, 3, 2, fx(1, 0, 0, 0, 0, 0, 0, 1)));
            l.add(bp("hospital", s, 2, 5, 1, fx(0, 0, 0, 0, 0, 0, 0, 0)));
            l.add(bp("sawmill", s, 2, 4, 2, fx(0, 0, 0, 0, 20, 0, 0, 0)));
            l.add(bp("workshop", s, 2, 4, 1, fx(0, 0, 0, 1, 15, 0, 0, 0)));
            l.add(bp("mine", s, 2, 4, 2, fx(0, 0, 0, 0, 0, 0, 5, 0)));
            l.add(bp("statue", s, 2, 3, 3, fx(0, 0, 0, 0, 0, 0, 0, 0)));
            l.add(bp("arch", s, 2, 2, 2, fx(0, 0, 0, 0, 0, 0, 0, 0)));
            l.add(bp("library", s, 3, 6, 1, fx(0, 0, 0, 0, 0, 0, 0, 0)));
            l.add(bp("guild", s, 3, 7, 1, fx(0, 1, 1, 0, 0, 0, 5, 0)));
            l.add(bp("arena", s, 3, 6, 1, fx(0, 1, 0, 0, 0, 0, 0, 1)));
            for (String el : ELEMENT_TEMPLES) l.add(bp(el, s, 3, 6, 1, fx(0, 0, 0, 0, 0, 0, 0, 0)));
            l.add(bp("magic_tower", s, 4, 10, 1, fx(0, 1, 0, 0, 0, 0, 0, 0)));
            // v1.5 (city_arcana): prédios mágicos — efeitos em feature/city_arcana (CityArcana), necessidades no CityEconomy
            l.add(bp("arcane_beacon", s, 2, 4, 2, fx(0, 0, 0, 0, 0, 0, 0, 0)));
            l.add(bp("mana_spire", s, 3, 6, 2, fx(0, 0, 0, 0, 0, 1, 0, 0)));
            l.add(bp("arcane_ward", s, 3, 8, 1, fx(0, 0, 0, 0, 0, 0, 0, 0)));
            l.add(bp("null_obelisk", s, 4, 10, 1, fx(0, 0, 0, 0, 0, 0, 0, 0)));
            // v1.0 (city_life): defesa de cerco — efeitos em CityDefense, segurança no CityEconomy
            l.add(bp("trap_field", s, 2, 3, 2, fx(0, 0, 0, 0, 0, 0, 0, 0)));
            // onda 10 (vehicles_mechanic): Oficina Mecânica — traz o Mecânico (veículos, peças, consertos, resgate)
            l.add(bp("garage", s, 1, 4, 1, fx(0, 0, 0, 0, 0, 0, 0, 0)));
            // onda 6 (city_monuments): monumentos novos e a maravilha de cada estilo — bônus em CityMonuments
            l.addAll(MonumentBuildings.blueprints(s));
            CATALOG.put(s, List.copyOf(l));
        }
    }

    private static Effects fx(int residents, int guards, int merchants, int builders, int speedPct, int affinity, int taxPct,
                              int militia) {
        return new Effects(residents, guards, merchants, builders, speedPct, affinity, taxPct, militia, false, "");
    }

    private static Blueprint bp(String key, SettlementStyle s, int minLevel, int points, int max, Effects fx) {
        return new Blueprint(key, s, minLevel, points, max, fx);
    }

    public static List<Blueprint> of(SettlementStyle style) {
        return CATALOG.get(style);
    }

    @Nullable
    public static Blueprint get(SettlementStyle style, String key) {
        for (Blueprint b : CATALOG.get(style)) if (b.key().equals(key)) return b;
        return null;
    }

    /** Quantas variantes de molde a planta tem (casas variam para a cidade não ficar repetida). */
    public static int variants(String key) {
        return switch (key) {
            case "house_small" -> 3;
            case "house_large" -> 2;
            default -> 1;
        };
    }

    /** Prosperidade para chegar ao nível (1..5). */
    public static int pointsFor(SettlementStyle style, int level) {
        int i = Math.max(0, Math.min(MAX_LEVEL, level) - 1);
        return Math.round(LEVEL_POINTS[i] * style.levelCost);
    }

    /** Nível pela prosperidade; o nível 4 exige a Prefeitura (ou equivalente) pronta. */
    public static int levelFor(SettlementStyle style, int points, boolean hasTownHall) {
        int level = 1;
        for (int l = 2; l <= MAX_LEVEL; l++) {
            if (points < pointsFor(style, l)) break;
            if (l >= 4 && !hasTownHall) break;
            level = l;
        }
        return level;
    }

    private Blueprints() {}
}
