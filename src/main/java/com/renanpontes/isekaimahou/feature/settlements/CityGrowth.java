package com.renanpontes.isekaimahou.feature.settlements;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.renanpontes.isekaimahou.feature.settlements.build.Blueprint;
import com.renanpontes.isekaimahou.feature.settlements.build.CityLayout;

import net.minecraft.server.MinecraftServer;

/**
 * Crescimento da cidade fundada (v1.0, city_life) — "crescer a rodo, mas pelo sistema": abrir o próximo anel de
 * terrenos depende de GENTE, FELICIDADE e PRÉDIOS (nada de espera longa), e o catálogo cresce junto (uma cidade de
 * 15×15 precisa de bem mais casas, fazendas e torres do que uma de 5×5).
 * <ul>
 *   <li>habitantes: 12, 24, 40, 60, 84, 112... (cresce com o tamanho);</li>
 *   <li>felicidade: 40 no começo, +5 por anel, até 60;</li>
 *   <li>prédios: metade dos terrenos atuais com prédio pronto;</li>
 *   <li>centro: nível 3 até 9×9, nível 4 até 13×13, nível 5 daí em diante.</li>
 * </ul>
 */
public final class CityGrowth {
    /** Serviços "de bairro": um a mais a cada 2 anéis de expansão. */
    private static final Set<String> DISTRICT = Set.of("market", "tavern", "barracks", "smithy", "hospital", "workshop", "garage", "library",
            "guild", "arena", "temple");

    /**
     * Quantos prédios deste tipo a cidade pode ter no tamanho atual. Prédios repetíveis (casas, fazendas, torres...)
     * multiplicam a cada anel de expansão; os de bairro ganham +1 a cada 2 anéis; os únicos continuam únicos.
     */
    public static int maxCount(Settlement s, Blueprint bp) {
        int base = bp.maxCount();
        if (!s.own() || bp.isWall() || bp.isPlaza() || bp.isWonder()) return base;
        int extra = Math.max(0, s.radius - CityLayout.START_RADIUS);
        if (base > 1) return base * (1 + extra);
        if (DISTRICT.contains(bp.key())) return 1 + extra / 2;
        return base;
    }

    /** Habitantes pedidos para a cidade de raio {@code radius} abrir o próximo anel. */
    public static int needPeople(int radius) {
        int n = Math.max(0, radius - CityLayout.START_RADIUS);
        return 12 + 10 * n + 2 * n * n;
    }

    public static int needHappy(int radius) {
        return Math.min(60, 40 + 5 * Math.max(0, radius - CityLayout.START_RADIUS));
    }

    /** Terrenos com prédio pedidos: metade dos que a cidade já tem. */
    public static int needBuildings(Settlement s) {
        int half = (int) Math.ceil(CityLayout.cellsWithin(s.radius).size() * 0.5);
        // Onda 7 (erro achado pelo dono: "pra virar town preciso do 7x7, mas pra virar 7x7 preciso ter 12 construções...
        // n ta sendo possível upar"): no 5×5 os terrenos abrem com o degrau (a Vila só tem 8 dos 24), então pedir metade
        // dos 24 era impossível. O pedido nunca passa de 3/4 dos terrenos que a cidade PODE usar agora.
        int open = 0;
        for (int c : CityLayout.cellsWithin(s.radius)) if (CityLayout.unlocked(s, c)) open++;
        return Math.max(1, Math.min(half, (int) Math.ceil(open * 0.75)));
    }

    /** Nível do centro pedido para abrir o anel {@code ring}. */
    public static int needLevel(int ring) {
        // onda 5: cada degrau libera mais anéis (Vila 7×7, Burgo 11×11, Cidade 17×17, Capital 25×25)
        return CityTier.levelForRing(ring);
    }

    /** Requisitos para abrir o próximo anel (vazio no tamanho máximo). */
    public static List<CityEconomy.Req> expandReqs(MinecraftServer server, Settlement s) {
        List<CityEconomy.Req> out = new ArrayList<>();
        if (!s.own() || s.radius >= CityLayout.MAX_RADIUS) return out;
        CityEconomy.ensureInit(s, null);
        int people = needPeople(s.radius), happy = needHappy(s.radius), built = needBuildings(s), level = needLevel(s.radius + 1);
        int have = CityPlanning.builtPlots(s);
        out.add(new CityEconomy.Req(SettlementsLang.tr("expand.req.people", s.people, people), s.people >= people));
        out.add(new CityEconomy.Req(SettlementsLang.tr("expand.req.happy", s.affinity, happy), s.affinity >= happy));
        out.add(new CityEconomy.Req(SettlementsLang.tr("expand.req.buildings", have, built), have >= built));
        out.add(new CityEconomy.Req(SettlementsLang.tr("expand.req.level", s.level, level), s.level >= level));
        return out;
    }

    private CityGrowth() {}
}
