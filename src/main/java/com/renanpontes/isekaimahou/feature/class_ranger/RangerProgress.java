package com.renanpontes.isekaimahou.feature.class_ranger;

import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * Trilha do Ranger: a progressão própria da classe em 5 ranks (Batedor → Rastreador → Guardião da Mata → Mestre das
 * Feras → Senhor das Feras). Cada rank pede nível/Círculo e feitos de caçador (contadores {@code ranger.*} somados
 * pelo módulo), e ao ser concluído na aba "Trilha do Ranger" grava {@code ranger.rank.<n>}, soma
 * {@code ritual_rank} (+1, convenção de todas as classes) e dá glifos de origem, arcos, companheiros e XP.
 * Dados comuns: o servidor valida e a aba do menu mostra (lendo {@code ClientMagic.data()}).
 */
public final class RangerProgress {
    public static final int MAX_RANK = 5;
    /** Abates com flechas (de arco ou da forma Flecha). */
    public static final String ARROW_KILL = "ranger.arrow_kill";
    /** Abates a 20+ blocos de distância. */
    public static final String LONG_KILL = "ranger.long_kill";
    /** Abates feitos pelo Lobo, pela Águia ou pelo Corcel. */
    public static final String COMPANION_KILL = "ranger.companion_kill";
    /** Abates de alvos com a SUA Marca do Caçador. */
    public static final String MARK_KILL = "ranger.mark_kill";
    /** Grandes presas: chefes e criaturas com 150+ de vida. */
    public static final String GREAT_PREY = "ranger.great_prey";
    /** Blocos percorridos montado no Corcel. */
    public static final String RIDE = "ranger.ride_dist";

    /** Um requisito: contador ≥ quantidade. */
    public record Req(String counter, int amount) {
        public String langKey() {
            return "trail.isekai_mahou.class_ranger.req." + counter.substring(counter.indexOf('.') + 1);
        }

        public int current(MagicData d) {
            return d.counter(counter);
        }
    }

    /**
     * Um rank: nível/Círculo mínimos, requisitos, XP, os glifos ensinados, o grau do Arco do Ranger entregue (0 = nenhum)
     * e se dá as duas Lendas da classe.
     */
    public record Rank(int number, int level, int circle, List<Req> reqs, int xp, List<String> glyphs, int bowTier, boolean legends) {}

    /** Glifos ensinados ao escolher a classe (o resto vem pela Trilha). */
    public static final List<String> START_GLYPHS = List.of(RangerGlyphIds.EXPLOSIVE, RangerGlyphIds.MARK, RangerGlyphIds.PACK);

    // Dificuldade: acompanha a curva da v1.0 (C3 ~ nível 25, C5 ~ 45, C8 ~ 75) — cada rank cabe no ritmo normal de jogo
    // do Círculo indicado; XP igual ao das outras classes de Destreza (600 → 14000).
    private static final List<Rank> RANKS = List.of(
            new Rank(1, 8, 1, List.of(new Req(ARROW_KILL, 25), new Req(COMPANION_KILL, 10)), 600,
                    List.of(RangerGlyphIds.NET, RangerGlyphIds.SEEKING, RangerGlyphIds.SACRED), 0, false),
            new Rank(2, 20, 2, List.of(new Req(LONG_KILL, 25), new Req(MARK_KILL, 20), new Req(COMPANION_KILL, 40)), 1500,
                    List.of(RangerGlyphIds.CHAIN, RangerGlyphIds.STEED, RangerGlyphIds.TRIPLE), 2, false),
            new Rank(3, 35, 4, List.of(new Req(RIDE, 1500), new Req(ARROW_KILL, 250), new Req(GREAT_PREY, 3)), 3500,
                    List.of(RangerGlyphIds.PIERCING, RangerGlyphIds.RAIN, RangerGlyphIds.CHAINS), 3, false),
            new Rank(4, 55, 6, List.of(new Req(COMPANION_KILL, 200), new Req(MARK_KILL, 150), new Req(GREAT_PREY, 6)), 7000,
                    List.of(RangerGlyphIds.QUICKDRAW, RangerGlyphIds.THOUSAND_FEATHERS, RangerGlyphIds.BULLET_LINE), 4, false),
            new Rank(5, 75, 8, List.of(new Req(LONG_KILL, 250), new Req(GREAT_PREY, 12), new Req(COMPANION_KILL, 400)), 14000,
                    List.of(RangerGlyphIds.WOLF_GOD), 0, true));

    public static List<Rank> ranks() {
        return RANKS;
    }

    @Nullable
    public static Rank rankDef(int n) {
        return n >= 1 && n <= RANKS.size() ? RANKS.get(n - 1) : null;
    }

    public static String flag(int n) {
        return "ranger.rank." + n;
    }

    /** Ranks concluídos (sequenciais). */
    public static int rank(MagicData d) {
        int r = 0;
        while (r < MAX_RANK && d.hasFlag(flag(r + 1))) r++;
        return r;
    }

    public static MutableComponent title(int n) {
        String key = "trail.isekai_mahou.class_ranger.rank." + n;
        return Component.translatable(key);
    }

    /** Soma um feito da Trilha (só para Rangers de verdade: jogadores falsos e outras classes não contam). */
    public static void count(@Nullable ServerPlayer sp, String key, int n) {
        if (sp == null || n <= 0 || sp instanceof FakePlayer) return;
        if (MagicApi.get(sp).path() != MagicPath.RANGER) return;
        MagicApi.addCounter(sp, key, n);
    }

    /**
     * O jogador cumpre o rank {@code n}? Acrescenta uma linha por requisito que falta. Serve no servidor e no cliente.
     */
    public static boolean meets(MagicData d, int n, @Nullable List<Component> missing) {
        Rank r = rankDef(n);
        if (r == null) return false;
        boolean ok = true;
        if (d.path() != MagicPath.RANGER) {
            ok = false;
            if (missing != null) missing.add(Component.translatable("trail.isekai_mahou.class_ranger.need_class"));
        }
        if (rank(d) != n - 1) {
            ok = false;
            if (missing != null) missing.add(Component.translatable("trail.isekai_mahou.class_ranger.need_previous"));
        }
        if (d.level() < r.level()) {
            ok = false;
            if (missing != null) missing.add(Component.translatable("trail.isekai_mahou.class_ranger.need_level", r.level()));
        }
        if (d.circle() < r.circle()) {
            ok = false;
            if (missing != null) missing.add(Component.translatable("trail.isekai_mahou.class_ranger.need_circle", r.circle()));
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

    private RangerProgress() {}
}
