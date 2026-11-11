package com.renanpontes.isekaimahou.feature.class_gambler.rank;

import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.MagicData;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Progressão própria do Apostador: as 5 FICHAS DA CASA (Bronze, Prata, Ouro, Platina, Diamante). Lado comum (o
 * servidor valida, a aba mostra). Cada rank pede nível/Círculo, contadores de jogo (rolagens, Jackpots, cartas que
 * acertam, grandes vitórias, abates de sorte, Cassinos abertos, chefes) e vencer a RODADA DA CASA (abater N inimigos
 * no tempo, apostando fichas de cassino). Subir grava {@code gambler.rank} e soma {@code ritual_rank} (+1).
 * Dificuldade calibrada com a curva da v1.0 (níveis 5/15/35/55/75, Círculos 1/2/4/6/8,
 * chefes III 1 · IV 2 · V 4, XP 600/1500/3500/7000/14000).
 */
public final class HouseRanks {
    public static final int MAX_RANK = 5;
    public static final String RANK = "gambler.rank";

    /** Um requisito de contador. {@code counter} "boss" = soma de todos os {@code boss.*}. */
    public record Req(String counter, int amount) {
        public int current(MagicData d) {
            if (counter.equals("boss")) {
                int sum = 0;
                for (Map.Entry<String, Integer> e : d.counters().entrySet()) {
                    if (e.getKey().startsWith("boss.")) sum += Math.max(0, e.getValue());
                }
                return sum;
            }
            return d.counter(counter);
        }

        public String langKey() {
            return "rank.isekai_mahou.gambler.req." + (counter.contains(".") ? counter.substring(counter.indexOf('.') + 1) : counter);
        }
    }

    /**
     * Um rank: nível/Círculo mínimos, requisitos, a Rodada (abates e tempo em segundos), a aposta em fichas,
     * a recompensa em XP, os glifos ensinados e o grau do baralho entregue (0 = nenhum).
     */
    public record Rank(int number, int level, int circle, List<Req> reqs, int kills, int seconds, int stake, int xp,
                       List<String> glyphs, int deckTier, boolean legends) {
        public MutableComponent title() {
            return Component.translatable(String.join("", "rank.isekai_mahou.gambler.", String.valueOf(number)));
        }
    }

    private static final List<Rank> RANKS = List.of(
            new Rank(1, 5, 1, List.of(req("gambler.rolls", 40), req("gambler.card_hit", 25)), 6, 60, 1, 600,
                    List.of("dice_roll", "double_down", "gambler_fate_dice"), 0, false),
            new Rank(2, 15, 2, List.of(req("gambler.rolls", 200), req("gambler.jackpot", 2), req("gambler.big_win", 20)), 10, 75, 2, 1500,
                    List.of("roulette_wheel", "loaded_dice", "bungee_gum"), 2, false),
            new Rank(3, 35, 4, List.of(req("gambler.rolls", 500), req("gambler.jackpot", 6), req("gambler.lucky_kill", 40), req("boss", 1)), 15, 90, 4, 3500,
                    List.of("slot_machine", "royal_flush", "casino_domain", "texture_surprise"), 0, false),
            new Rank(4, 55, 6, List.of(req("gambler.rolls", 1000), req("gambler.jackpot", 14), req("gambler.casino", 3), req("boss", 2)), 20, 90, 7, 7000,
                    List.of("trigger_jackpot", "gambler_all_in"), 3, false),
            new Rank(5, 75, 8, List.of(req("gambler.rolls", 2000), req("gambler.jackpot", 30), req("boss", 4)), 30, 120, 12, 14000,
                    List.of("idle_death_gamble"), 4, true));

    private static Req req(String counter, int amount) {
        return new Req(counter, amount);
    }

    public static List<Rank> ranks() {
        return RANKS;
    }

    @Nullable
    public static Rank rank(int n) {
        return n >= 1 && n <= RANKS.size() ? RANKS.get(n - 1) : null;
    }

    /** Rank atual da Casa (0..5). */
    public static int rank(MagicData d) {
        return Math.max(0, Math.min(MAX_RANK, d.counter(RANK)));
    }

    /** Requisitos do rank {@code n} cumpridos (fora a Rodada)? Os que faltam vão para {@code missing}. */
    public static boolean meets(MagicData d, Rank r, @Nullable List<Component> missing) {
        boolean ok = true;
        if (d.level() < r.level() || d.circle() < r.circle()) {
            ok = false;
            if (missing != null) missing.add(Component.translatable("rank.isekai_mahou.gambler.missing_level", r.level(), r.circle()));
        }
        for (Req q : r.reqs()) {
            int cur = q.current(d);
            if (cur < q.amount()) {
                ok = false;
                if (missing != null) missing.add(Component.translatable(q.langKey(), q.amount()).append(" (" + cur + "/" + q.amount() + ")"));
            }
        }
        return ok;
    }

    private HouseRanks() {}
}
