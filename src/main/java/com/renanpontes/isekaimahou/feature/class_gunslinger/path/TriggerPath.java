package com.renanpontes.isekaimahou.feature.class_gunslinger.path;

import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_gunslinger.GunKind;

import net.minecraft.network.chat.Component;

/**
 * Caminho do Gatilho — a progressão própria do Pistoleiro (dados comuns: o servidor valida, a aba do menu mostra).
 * Cinco ranks; cada um pede nível/Círculo e contadores de tiro (acertos, tiros na cabeça, abates com bala, abates de
 * longe, recargas), chefes vencidos (IV 2, V 4 — soma dos contadores {@code boss.*} do núcleo) e, nos ranks III e V,
 * vencer o Duelo ao Meio-Dia contra o Forasteiro Espectral.
 * Concluir grava a flag {@code gunslinger.rank.<n>}, soma {@code ritual_rank} (+1, convenção de todas as classes) e
 * {@code ritual_rank.gunslinger}, ensina glifos, libera uma arma e dá XP.
 * <p>Dificuldade igual à das outras classes da v1.0: Círculos 1/2/4/6/8 (níveis 8/20/35/50/70), XP
 * 600/1500/3500/7000/14000, e os contadores saem jogando normalmente com a classe.
 */
public final class TriggerPath {
    public static final int MAX_RANK = 5;
    /** Prefixo do título dos ranks ({@code path.isekai_mahou.gunslinger.rank.<n>}). */
    private static final String RANK_KEY = "path.isekai_mahou.gunslinger.rank.";

    // Contadores do módulo (MagicData.counters)
    public static final String HITS = "gunslinger.hits";
    public static final String HEADSHOTS = "gunslinger.headshots";
    public static final String KILLS = "gunslinger.kills";
    public static final String LONGSHOTS = "gunslinger.longshots";
    public static final String RELOADS = "gunslinger.reloads";
    public static final String DUEL = "gunslinger.duel";
    public static final String DUEL2 = "gunslinger.duel2";
    /** Chefes vencidos: não é contador próprio, é a soma de todos os {@code boss.*} (gravados pelo BossRewards do núcleo). */
    public static final String BOSSES = "boss";
    /** Convenção de todas as classes/rituais (+1 por rank). */
    public static final String RITUAL_RANK = "ritual_rank";
    public static final String RITUAL_RANK_OWN = "ritual_rank.gunslinger";

    /** Distância mínima (blocos) para um abate contar como "de longe". */
    public static final double LONG_SHOT = 28.0;

    public record Req(String counter, int amount) {
        public String langKey() {
            return "path.isekai_mahou.gunslinger.req." + counter.substring(counter.indexOf('.') + 1);
        }

        public int current(MagicData d) {
            if (counter.equals(BOSSES)) {
                int sum = 0;
                for (Map.Entry<String, Integer> e : d.counters().entrySet()) {
                    if (e.getKey().startsWith("boss.") && e.getValue() > 0) sum += e.getValue();
                }
                return sum;
            }
            return d.counter(counter);
        }
    }

    /**
     * Um rank: nível/Círculo mínimos, requisitos, glifos ensinados, arma liberada (pode ser null), XP e o pergaminho
     * de lenda entregue (id de {@code Legends}, ou null).
     */
    public record Rank(int number, int level, int circle, List<Req> reqs, List<String> glyphs, @Nullable GunKind weapon, int xp,
                       @Nullable String legend) {
        public int duelTier() {
            for (Req r : reqs) {
                if (r.counter.equals(DUEL)) return 1;
                if (r.counter.equals(DUEL2)) return 2;
            }
            return 0;
        }
    }

    private static final List<Rank> RANKS = List.of(
            new Rank(1, 8, 1, List.of(new Req(HITS, 60), new Req(RELOADS, 15)),
                    List.of("gun_burst", "gun_on_reload"), GunKind.PISTOL, 600, null),
            new Rank(2, 20, 2, List.of(new Req(HEADSHOTS, 20), new Req(KILLS, 80)),
                    List.of("gun_charged_shot", "gun_incendiary"), GunKind.SHOTGUN, 1500, null),
            new Rank(3, 35, 4, List.of(new Req(DUEL, 1), new Req(HEADSHOTS, 60), new Req(LONGSHOTS, 10)),
                    List.of("gun_ricochet", "gun_explosive"), GunKind.DUAL_PISTOLS, 3500, "preachers_cross"),
            new Rank(4, 50, 6, List.of(new Req(KILLS, 500), new Req(HEADSHOTS, 200), new Req(LONGSHOTS, 40), new Req(BOSSES, 2)),
                    List.of("gun_piercing", "gun_fan_hammer"), GunKind.RIFLE, 7000, null),
            new Rank(5, 70, 8, List.of(new Req(DUEL2, 1), new Req(KILLS, 700), new Req(LONGSHOTS, 100), new Req(BOSSES, 4)),
                    List.of("gun_high_noon"), null, 14000, "silver_jackal"));

    /** Glifos ensinados ao escolher a classe. */
    public static final List<String> STARTER_GLYPHS = List.of("gun_bullet", "gun_mark");

    public static List<Rank> ranks() {
        return RANKS;
    }

    @Nullable
    public static Rank rank(int n) {
        return n >= 1 && n <= RANKS.size() ? RANKS.get(n - 1) : null;
    }

    public static String flag(int rank) {
        return "gunslinger.rank." + rank;
    }

    /** Ranks concluídos (sequenciais). */
    public static int rankOf(MagicData d) {
        int r = 0;
        while (r < MAX_RANK && d.hasFlag(flag(r + 1))) r++;
        return r;
    }

    /** A arma está liberada para materializar? (O revólver sempre.) */
    public static boolean unlocked(MagicData d, GunKind kind) {
        return kind.unlockRank == 0 || rankOf(d) >= kind.unlockRank;
    }

    /** Duelo que falta para o próximo rank (1 ou 2), ou 0. */
    public static int pendingDuel(MagicData d) {
        Rank next = rank(rankOf(d) + 1);
        if (next == null) return 0;
        int tier = next.duelTier();
        if (tier == 0) return 0;
        return d.counter(tier == 2 ? DUEL2 : DUEL) >= 1 ? 0 : tier;
    }

    /**
     * O jogador cumpre o rank {@code n}? Acrescenta uma linha por requisito que falta. Serve no servidor e no cliente
     * ({@code ClientMagic.data()}).
     */
    public static boolean meets(MagicData d, int n, @Nullable List<Component> missing) {
        Rank r = rank(n);
        if (r == null) return false;
        boolean ok = true;
        if (d.path() != MagicPath.GUNSLINGER) {
            ok = false;
            if (missing != null) missing.add(Component.translatable("path.isekai_mahou.gunslinger.need_class"));
        }
        if (rankOf(d) != n - 1) {
            ok = false;
            if (missing != null) missing.add(Component.translatable("path.isekai_mahou.gunslinger.need_previous"));
        }
        if (d.level() < r.level()) {
            ok = false;
            if (missing != null) missing.add(Component.translatable("path.isekai_mahou.gunslinger.need_level", r.level()));
        }
        if (d.circle() < r.circle()) {
            ok = false;
            if (missing != null) missing.add(Component.translatable("path.isekai_mahou.gunslinger.need_circle", r.circle()));
        }
        for (Req q : r.reqs()) {
            if (q.current(d) < q.amount()) {
                ok = false;
                if (missing != null) {
                    missing.add(Component.translatable(q.langKey(), q.amount()).append(" (" + q.current(d) + "/" + q.amount() + ")"));
                }
            }
        }
        return ok;
    }

    public static Component title(int n) {
        return Component.translatable(RANK_KEY + n);
    }

    private TriggerPath() {}
}
