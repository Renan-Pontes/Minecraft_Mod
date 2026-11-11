package com.renanpontes.isekaimahou.feature.cities;

import java.util.Map;

import com.renanpontes.isekaimahou.core.api.Reputation;
import com.renanpontes.isekaimahou.feature.cities.world.CityKingdom;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.RandomSource;

/**
 * Falas dos NPCs da cidade (todas por tradução, ver tools/gen/cities.py). Chaves:
 * {@code dialogue.isekai_mahou.cities.<grupo>.<n>}, onde o grupo é o tipo/profissão + o "humor" vindo da reputação.
 * As quantidades aqui precisam bater com as listas do script.
 */
public final class CityLines {
    public static final String PREFIX = "dialogue.isekai_mahou.cities.";

    /** Quantas falas existem em cada grupo. */
    private static final Map<String, Integer> COUNTS = Map.ofEntries(
            Map.entry("citizen.ambient", 10), Map.entry("citizen.night", 4), Map.entry("citizen.bad", 4),
            Map.entry("citizen.neutral", 6), Map.entry("citizen.good", 6), Map.entry("citizen.exalted", 2),
            Map.entry("citizen.hostile", 2), Map.entry("citizen.wanted", 2), Map.entry("citizen.hint", 8),
            Map.entry("guard.ambient", 6), Map.entry("guard.night", 3), Map.entry("guard.bad", 3),
            Map.entry("guard.neutral", 4), Map.entry("guard.good", 4), Map.entry("guard.exalted", 2),
            Map.entry("guard.hostile", 2), Map.entry("guard.wanted", 3), Map.entry("guard.battle", 4),
            Map.entry("lord.ambient", 4), Map.entry("lord.bad", 2), Map.entry("lord.neutral", 3), Map.entry("lord.good", 3),
            Map.entry("lord.exalted", 2), Map.entry("lord.hostile", 2), Map.entry("lord.news", 6),
            Map.entry("merchant.bad", 2), Map.entry("merchant.neutral", 3), Map.entry("merchant.good", 3),
            Map.entry("merchant.exalted", 2), Map.entry("merchant.hostile", 2), Map.entry("merchant.thanks", 4),
            Map.entry("merchant.refuse", 2), Map.entry("siege.cheer", 4), Map.entry("siege.fear", 4),
            // v0.9: sabor de cada reino (balões dos guardas/cidadãos e do Lorde)
            Map.entry("kingdom.aldoria.ambient", 6), Map.entry("kingdom.aldoria.lord", 3),
            Map.entry("kingdom.sahra.ambient", 6), Map.entry("kingdom.sahra.lord", 3),
            Map.entry("kingdom.nordheim.ambient", 6), Map.entry("kingdom.nordheim.lord", 3),
            // v1.0: guerra de cidades e abordagem da guarda
            Map.entry("guard.enemy", 3), Map.entry("citizen.enemy", 3), Map.entry("merchant.enemy", 2),
            Map.entry("citizen.call_guard", 3), Map.entry("confront.halt", 4), Map.entry("confront.accuse.chest", 3),
            Map.entry("confront.accuse.block", 3), Map.entry("confront.accuse.theft", 3), Map.entry("confront.persuade_ok", 3),
            Map.entry("confront.persuade_fail", 3), Map.entry("confront.bribe_ok", 3), Map.entry("confront.bribe_refuse", 3),
            Map.entry("confront.return_ok", 2), Map.entry("confront.return_clean", 2), Map.entry("confront.flee", 3),
            Map.entry("confront.fight", 3), Map.entry("confront.impatient", 2), Map.entry("confront.repeat", 3),
            Map.entry("confront.waiting", 3));
    /** Falas próprias de cada profissão (saudação e balão). */
    public static final int PROFESSION_GREETINGS = 2;
    public static final int PROFESSION_AMBIENT = 3;

    public static int count(String group) {
        return COUNTS.getOrDefault(group, 0);
    }

    /** Uma fala aleatória do grupo (ou a primeira, se o grupo for desconhecido). */
    public static MutableComponent pick(String group, RandomSource r) {
        int n = count(group);
        return Component.translatable(PREFIX + group + "." + (n > 0 ? r.nextInt(n) : 0));
    }

    public static MutableComponent pick(String group, RandomSource r, Object... args) {
        int n = count(group);
        return Component.translatable(PREFIX + group + "." + (n > 0 ? r.nextInt(n) : 0), args);
    }

    /** Fala solta com o sabor do reino (Aldória: Coroa e campos; Sahra: sol e caravanas; Nordheim: neve e hidromel). */
    public static MutableComponent kingdomFlavor(CityKingdom k, RandomSource r) {
        return pick("kingdom." + k.id + ".ambient", r);
    }

    /** Fala solta do Lorde/Sultão/Jarl do reino. */
    public static MutableComponent kingdomLord(CityKingdom k, RandomSource r) {
        return pick("kingdom." + k.id + ".lord", r);
    }

    /** Tradução com chave montada em tempo de execução (prefixo + id). */
    public static MutableComponent tr(String key, Object... args) {
        return Component.translatable(key, args);
    }

    public static MutableComponent line(String key, Object... args) {
        return Component.translatable(PREFIX + key, args);
    }

    /** "Humor" da fala pelo patamar: hostile, bad, neutral, good, exalted. */
    public static String mood(Reputation.Standing s) {
        if (!s.canTrade()) return "hostile";
        if (s == Reputation.Standing.UNFRIENDLY) return "bad";
        if (s == Reputation.Standing.EXALTED) return "exalted";
        if (s.atLeast(Reputation.Standing.FRIENDLY)) return "good";
        return "neutral";
    }

    private CityLines() {}
}
