package com.renanpontes.isekaimahou.feature.depths.puzzle.enigma.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * "Fileiras do Oráculo": fileiras de 7 runas (acesa/apagada); cada fileira nasce das anteriores por UMA regra fixa.
 * O jogador vê as primeiras e precisa montar a próxima. Não é reflexo nem memória: é descobrir a regra.
 * <ul>
 *   <li>nível 1: girar 1 casa (esquerda/direita) ou inverter tudo;</li>
 *   <li>nível 2: giro que cresce (1, 2, 3…), inverter-e-girar, girar de 2 em 2, espelhar-e-girar;</li>
 *   <li>nível 3: "ou exclusivo" das duas fileiras anteriores, autômatos (regra 90 / regra 150), fileira XOR ela girada.</li>
 * </ul>
 * <b>Sem ambiguidade (conferido):</b> testa as fileiras mostradas contra TODAS as regras conhecidas (de todos os
 * níveis); só aceita se toda regra que explica o que foi mostrado prevê a MESMA resposta.
 * Bit i = coluna i da esquerda para a direita (vista de quem está na frente).
 */
public final class PatternLogic {
    public static final int W = 7;
    public static final int MASK = (1 << W) - 1;

    /** Regra: quantas fileiras-semente ela precisa e como calcula a fileira k a partir das anteriores. */
    public enum Rule {
        ROT_L1(1, 1), ROT_R1(1, 1), INVERT(1, 1),
        ROT_INC_L(1, 2), ROT_INC_R(1, 2), INV_ROT_L(1, 2), INV_ROT_R(1, 2), ROT_L2(1, 2), ROT_R2(1, 2), MIRROR_ROT(1, 2),
        XOR2(2, 3), RULE90(1, 3), RULE150(1, 3), XOR_ROT(1, 3);

        public final int seeds;
        public final int tier;

        Rule(int seeds, int tier) {
            this.seeds = seeds;
            this.tier = tier;
        }

        public int next(int[] rows, int k) {
            int p = rows[k - 1];
            return switch (this) {
                case ROT_L1 -> rotL(p, 1);
                case ROT_R1 -> rotR(p, 1);
                case INVERT -> ~p & MASK;
                case ROT_INC_L -> rotL(p, k);
                case ROT_INC_R -> rotR(p, k);
                case INV_ROT_L -> rotL(~p & MASK, 1);
                case INV_ROT_R -> rotR(~p & MASK, 1);
                case ROT_L2 -> rotL(p, 2);
                case ROT_R2 -> rotR(p, 2);
                case MIRROR_ROT -> rotL(mirror(p), 1);
                case XOR2 -> p ^ rows[k - 2];
                case RULE90 -> ((p << 1) ^ (p >>> 1)) & MASK;
                case RULE150 -> ((p << 1) ^ p ^ (p >>> 1)) & MASK;
                case XOR_ROT -> p ^ rotL(p, 1);
            };
        }
    }

    /** Fileiras mostradas (as primeiras) e a resposta (a fileira seguinte). */
    public final int[] shown;
    public final int answer;
    public final Rule rule;

    private PatternLogic(int[] shown, int answer, Rule rule) {
        this.shown = shown;
        this.answer = answer;
        this.rule = rule;
    }

    public static int rotL(int x, int s) {
        s = ((s % W) + W) % W;
        return ((x << s) | (x >>> (W - s))) & MASK;
    }

    public static int rotR(int x, int s) {
        return rotL(x, -s);
    }

    public static int mirror(int x) {
        int out = 0;
        for (int i = 0; i < W; i++) if ((x >> i & 1) != 0) out |= 1 << (W - 1 - i);
        return out;
    }

    public static int shownRows(int tier) {
        return tier >= 3 ? 4 : 3;
    }

    public static PatternLogic generate(long seed, int tier) {
        Random r = new Random(seed);
        List<Rule> family = new ArrayList<>();
        for (Rule ru : Rule.values()) if (ru.tier == Math.max(1, Math.min(3, tier))) family.add(ru);
        int count = shownRows(tier);
        for (int attempt = 0; attempt < 2000; attempt++) {
            Rule rule = family.get(r.nextInt(family.size()));
            int[] rows = new int[count + 1];
            for (int i = 0; i < rule.seeds; i++) rows[i] = randomRow(r);
            for (int k = rule.seeds; k <= count; k++) rows[k] = rule.next(rows, k);
            if (!interesting(rows)) continue;
            int[] shown = java.util.Arrays.copyOf(rows, count);
            if (unambiguous(shown, rows[count])) return new PatternLogic(shown, rows[count], rule);
        }
        // Reserva (conferida no teste): girar 1 à esquerda a partir de 0b0010011
        int[] rows = new int[count + 1];
        rows[0] = 0b0010011;
        for (int k = 1; k <= count; k++) rows[k] = Rule.ROT_L1.next(rows, k);
        return new PatternLogic(java.util.Arrays.copyOf(rows, count), rows[count], Rule.ROT_L1);
    }

    private static int randomRow(Random r) {
        while (true) {
            int x = r.nextInt(MASK + 1);
            int bits = Integer.bitCount(x);
            if (bits >= 2 && bits <= 4) return x;
        }
    }

    /** Fileiras diferentes entre si, nenhuma toda apagada ou toda acesa (inclusive a resposta). */
    private static boolean interesting(int[] rows) {
        for (int i = 0; i < rows.length; i++) {
            if (rows[i] == 0 || rows[i] == MASK) return false;
            for (int j = 0; j < i; j++) if (rows[i] == rows[j]) return false;
        }
        return true;
    }

    /** Toda regra (de todos os níveis) que explica as fileiras mostradas dá a mesma resposta? */
    public static boolean unambiguous(int[] shown, int answer) {
        boolean any = false;
        for (Rule ru : Rule.values()) {
            if (ru.seeds >= shown.length) continue;
            int[] rows = java.util.Arrays.copyOf(shown, shown.length + 1);
            boolean ok = true;
            for (int k = ru.seeds; k < shown.length; k++) {
                if (ru.next(rows, k) != shown[k]) {
                    ok = false;
                    break;
                }
            }
            if (!ok) continue;
            any = true;
            if (ru.next(rows, shown.length) != answer) return false;
        }
        return any;
    }
}
