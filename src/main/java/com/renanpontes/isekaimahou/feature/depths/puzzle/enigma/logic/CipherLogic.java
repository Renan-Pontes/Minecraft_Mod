package com.renanpontes.isekaimahou.feature.depths.puzzle.enigma.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * "Cifra Rúnica": cada runa esconde um número diferente de 1 a 9. Tabuletas espalhadas pela sala trazem pistas
 * (equações entre runas, sem palavras: funciona em qualquer idioma). A fechadura mostra um número; o jogador toca as
 * runas que o "escrevem", na ordem.
 * <ul>
 *   <li>nível 1: 3 runas, código de 3 dígitos, pistas podem dar um valor direto;</li>
 *   <li>nível 2: 4 runas, código de 4 dígitos;</li>
 *   <li>nível 3: 5 runas, código de 5 dígitos, NENHUM valor direto: só relações (somas, diferenças, produtos, "maior que").</li>
 * </ul>
 * <b>Solução única (conferida):</b> as pistas são sorteadas entre as verdadeiras e só entram se cortarem candidatos;
 * para quando sobra exatamente UMA atribuição de valores (testa todas as permutações de 1..9).
 */
public final class CipherLogic {
    public static final String[] RUNES = {"ᚠ", "ᚢ", "ᚦ", "ᚨ", "ᚱ", "ᚲ", "ᚷ", "ᚹ"};

    public enum Kind { EQ, SUM_N, DIFF_N, MUL_N, GT, SUM3_N, SUM_REL, DOUBLE }

    public record Clue(Kind kind, int a, int b, int c, int num) {
        public boolean holds(int[] v) {
            return switch (kind) {
                case EQ -> v[a] == num;
                case SUM_N -> v[a] + v[b] == num;
                case DIFF_N -> v[a] - v[b] == num;
                case MUL_N -> v[a] * v[b] == num;
                case GT -> v[a] > v[b];
                case SUM3_N -> v[a] + v[b] + v[c] == num;
                case SUM_REL -> v[a] + v[b] == v[c];
                case DOUBLE -> v[a] * 2 == v[b];
            };
        }

        /** Texto da pista (só runas, números e sinais). {@code runes} = símbolos das runas deste enigma. */
        public String text(String[] runes) {
            return switch (kind) {
                case EQ -> runes[a] + " = " + num;
                case SUM_N -> runes[a] + " + " + runes[b] + " = " + num;
                case DIFF_N -> runes[a] + " − " + runes[b] + " = " + num;
                case MUL_N -> runes[a] + " × " + runes[b] + " = " + num;
                case GT -> runes[a] + " > " + runes[b];
                case SUM3_N -> runes[a] + "+" + runes[b] + "+" + runes[c] + " = " + num;
                case SUM_REL -> runes[a] + " + " + runes[b] + " = " + runes[c];
                case DOUBLE -> runes[a] + " × 2 = " + runes[b];
            };
        }
    }

    /** Símbolos das runas deste enigma. */
    public final String[] runes;
    /** Valor secreto de cada runa (1..9, todos diferentes). */
    public final int[] values;
    public final List<Clue> clues;
    /** Dígitos da fechadura. */
    public final int[] code;

    private CipherLogic(String[] runes, int[] values, List<Clue> clues, int[] code) {
        this.runes = runes;
        this.values = values;
        this.clues = clues;
        this.code = code;
    }

    public static int runeCount(int tier) {
        return tier <= 1 ? 3 : tier == 2 ? 4 : 5;
    }

    public static CipherLogic generate(long seed, int tier) {
        Random r = new Random(seed);
        int k = runeCount(tier);
        int maxClues = tier <= 1 ? 4 : tier == 2 ? 6 : 7;
        for (int attempt = 0; attempt < 400; attempt++) {
            List<String> pool = new ArrayList<>(List.of(RUNES));
            java.util.Collections.shuffle(pool, r);
            String[] runes = pool.subList(0, k).toArray(new String[0]);
            List<Integer> digits = new ArrayList<>(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9));
            java.util.Collections.shuffle(digits, r);
            int[] values = new int[k];
            for (int i = 0; i < k; i++) values[i] = digits.get(i);
            List<int[]> cands = permutations(k);
            List<Clue> clues = new ArrayList<>();
            int guard = 0;
            while (cands.size() > 1 && clues.size() < maxClues && guard++ < 300) {
                Clue c = randomClue(r, k, values, tier);
                if (c == null || !c.holds(values)) continue;
                List<int[]> left = new ArrayList<>();
                for (int[] v : cands) if (c.holds(v)) left.add(v);
                if (left.size() == cands.size()) continue;
                cands = left;
                clues.add(c);
            }
            if (cands.size() != 1) continue;
            int len = tier <= 1 ? 3 : tier == 2 ? 4 : 5;
            int[] code = new int[len];
            boolean[] used = new boolean[k];
            for (int i = 0; i < len; i++) {
                int pick = r.nextInt(k);
                code[i] = values[pick];
                used[pick] = true;
            }
            // toda runa aparece pelo menos uma vez quando dá (tem que decifrar todas)
            for (int i = 0; i < k && i < len; i++) {
                if (!used[i]) {
                    int slot = r.nextInt(len);
                    code[slot] = values[i];
                }
            }
            java.util.Collections.shuffle(clues, r);
            CipherLogic l = new CipherLogic(runes, values, clues, code);
            if (l.verify()) return l;
        }
        // reserva conferida: 3 runas com valor direto
        String[] runes = {RUNES[0], RUNES[1], RUNES[2]};
        int[] values = {3, 7, 5};
        List<Clue> clues = List.of(new Clue(Kind.EQ, 0, 0, 0, 3), new Clue(Kind.EQ, 1, 0, 0, 7), new Clue(Kind.SUM_N, 0, 2, 0, 8));
        return new CipherLogic(runes, values, clues, new int[] {7, 3, 5});
    }

    private static Clue randomClue(Random r, int k, int[] v, int tier) {
        int a = r.nextInt(k), b = r.nextInt(k), c = r.nextInt(k);
        if (a == b) return null;
        int roll = r.nextInt(100);
        if (tier <= 1 && roll < 15) return new Clue(Kind.EQ, a, 0, 0, v[a]);
        if (roll < 30) return new Clue(Kind.SUM_N, a, b, 0, v[a] + v[b]);
        if (roll < 45) return v[a] > v[b] ? new Clue(Kind.DIFF_N, a, b, 0, v[a] - v[b]) : null;
        if (roll < 60) return v[a] * v[b] <= 72 ? new Clue(Kind.MUL_N, a, b, 0, v[a] * v[b]) : null;
        if (roll < 72) return v[a] > v[b] ? new Clue(Kind.GT, a, b, 0, 0) : new Clue(Kind.GT, b, a, 0, 0);
        if (roll < 82) return c != a && c != b ? new Clue(Kind.SUM3_N, a, b, c, v[a] + v[b] + v[c]) : null;
        if (roll < 94) return c != a && c != b && v[a] + v[b] == v[c] ? new Clue(Kind.SUM_REL, a, b, c, 0) : null;
        return v[a] * 2 == v[b] ? new Clue(Kind.DOUBLE, a, b, 0, 0) : null;
    }

    /** Todas as atribuições de valores distintos 1..9 para k runas. */
    public static List<int[]> permutations(int k) {
        List<int[]> out = new ArrayList<>();
        perm(new int[k], 0, new boolean[10], out);
        return out;
    }

    private static void perm(int[] cur, int i, boolean[] used, List<int[]> out) {
        if (i == cur.length) {
            out.add(cur.clone());
            return;
        }
        for (int d = 1; d <= 9; d++) {
            if (used[d]) continue;
            used[d] = true;
            cur[i] = d;
            perm(cur, i + 1, used, out);
            used[d] = false;
        }
    }

    /** Exatamente uma atribuição satisfaz todas as pistas, e é a secreta. */
    public boolean verify() {
        int found = 0;
        for (int[] v : permutations(values.length)) {
            boolean ok = true;
            for (Clue c : clues) ok &= c.holds(v);
            if (ok) {
                found++;
                if (!java.util.Arrays.equals(v, values)) return false;
            }
        }
        return found == 1;
    }

    /** Índice da runa cujo valor é {@code digit}, ou -1. */
    public int runeOf(int digit) {
        for (int i = 0; i < values.length; i++) if (values[i] == digit) return i;
        return -1;
    }
}
