package com.renanpontes.isekaimahou.feature.depths.puzzle.enigma.logic;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * "Estátuas da Verdade" (cavaleiros e patifes): cada estátua é Fiel (sempre diz a verdade) ou Traidora (sempre mente).
 * Exatamente uma guarda a Chave. Cada uma diz UMA frase; o jogador deduz quem guarda a chave e puxa a alavanca dela.
 * <ul><li>nível 1: 2 estátuas; nível 2: 3; nível 3: 4 (frases de contagem e "ou").</li></ul>
 * <b>Solução única (conferida):</b> testa TODAS as combinações (Fiel/Traidora × dono da chave); só aceita o enigma se
 * todas as combinações coerentes com as frases apontam o MESMO dono. Nos níveis 2–3 também exige que nenhuma frase
 * sozinha resolva (precisa cruzar informações).
 */
public final class StatuesLogic {
    /** Tipos de frase. a/b = índice de estátua. */
    public enum Kind {
        KNIGHT, KNAVE, SAME, DIFF, KEY, NOTKEY, ONE_KNIGHT, TWO_KNAVES, KEY_KNIGHT, KEY_KNAVE, KNAVE_OR_KEY
    }

    public record Statement(Kind kind, int a, int b) {
        /** A frase é verdadeira no mundo (fiel[], chave)? */
        public boolean eval(boolean[] knight, int key) {
            int knights = 0;
            for (boolean k : knight) if (k) knights++;
            return switch (kind) {
                case KNIGHT -> knight[a];
                case KNAVE -> !knight[a];
                case SAME -> knight[a] == knight[b];
                case DIFF -> knight[a] != knight[b];
                case KEY -> key == a;
                case NOTKEY -> key != a;
                case ONE_KNIGHT -> knights == 1;
                case TWO_KNAVES -> knight.length - knights >= 2;
                case KEY_KNIGHT -> knight[key];
                case KEY_KNAVE -> !knight[key];
                case KNAVE_OR_KEY -> !knight[a] || key == a;
            };
        }
    }

    public final int n;
    public final Statement[] says;
    public final boolean[] knight;
    public final int key;

    private StatuesLogic(int n, Statement[] says, boolean[] knight, int key) {
        this.n = n;
        this.says = says;
        this.knight = knight;
        this.key = key;
    }

    public static int countFor(int tier) {
        return tier <= 1 ? 2 : tier == 2 ? 3 : 4;
    }

    public static StatuesLogic generate(long seed, int tier) {
        Random r = new Random(seed);
        int n = countFor(tier);
        for (int attempt = 0; attempt < 20000; attempt++) {
            boolean strict = attempt < 15000 && tier >= 2;
            boolean[] knight = new boolean[n];
            for (int i = 0; i < n; i++) knight[i] = r.nextBoolean();
            int key = r.nextInt(n);
            Statement[] says = new Statement[n];
            for (int s = 0; s < n; s++) {
                for (int tries = 0; tries < 50 && says[s] == null; tries++) {
                    Statement st = random(r, n, s, tier);
                    if (st != null && st.eval(knight, key) == knight[s]) says[s] = st;
                }
                if (says[s] == null) break;
            }
            if (says[n - 1] == null) continue;
            StatuesLogic l = new StatuesLogic(n, says, knight, key);
            if (l.uniqueKey() != key) continue;
            if (strict && !l.needsCrossing()) continue;
            return l;
        }
        // Reserva (conferida no teste): I diz "somos de tipos diferentes" — seja I fiel ou traidora, II é Traidora;
        // II diz "a chave está comigo", logo a chave está com I.
        Statement[] says = new Statement[n];
        says[0] = new Statement(Kind.DIFF, 0, 1);
        says[1] = new Statement(Kind.KEY, 1, 0);
        for (int i = 2; i < n; i++) says[i] = new Statement(Kind.KNAVE, 1, 0);
        boolean[] knight = new boolean[n];
        knight[0] = true;
        for (int i = 2; i < n; i++) knight[i] = true;
        return new StatuesLogic(n, says, knight, 0);
    }

    private static Statement random(Random r, int n, int self, int tier) {
        int other = (self + 1 + r.nextInt(n - 1)) % n;
        int third = n > 2 ? pickOther(r, n, self, other) : other;
        int maxKind = tier <= 1 ? 9 : tier == 2 ? 10 : 11;
        Kind kind = Kind.values()[r.nextInt(maxKind)];
        return switch (kind) {
            case KNIGHT, KNAVE -> new Statement(kind, other, 0);
            case SAME, DIFF -> n > 2 && r.nextBoolean() ? new Statement(kind, other, third) : new Statement(kind, self, other);
            case KEY, NOTKEY -> new Statement(kind, r.nextInt(3) == 0 ? self : other, 0);
            case ONE_KNIGHT, KEY_KNIGHT, KEY_KNAVE -> new Statement(kind, 0, 0);
            case TWO_KNAVES -> n >= 3 ? new Statement(kind, 0, 0) : null;
            case KNAVE_OR_KEY -> new Statement(kind, other, 0);
        };
    }

    private static int pickOther(Random r, int n, int a, int b) {
        while (true) {
            int c = r.nextInt(n);
            if (c != a && c != b) return c;
        }
    }

    /** Dono da chave se for o mesmo em todos os mundos coerentes; -1 se ambíguo ou nenhum mundo coerente. */
    public int uniqueKey() {
        Set<Integer> keys = keysConsistent(-1);
        return keys.size() == 1 ? keys.iterator().next() : -1;
    }

    /** Donos possíveis usando só a frase {@code only} (ou todas, se -1). */
    private Set<Integer> keysConsistent(int only) {
        Set<Integer> keys = new HashSet<>();
        boolean[] k = new boolean[n];
        for (int mask = 0; mask < (1 << n); mask++) {
            for (int i = 0; i < n; i++) k[i] = (mask >> i & 1) != 0;
            for (int key = 0; key < n; key++) {
                boolean ok = true;
                for (int s = 0; s < n && ok; s++) {
                    if (only >= 0 && s != only) continue;
                    ok = says[s].eval(k, key) == k[s];
                }
                if (ok) keys.add(key);
            }
        }
        return keys;
    }

    /** Nenhuma frase sozinha basta para saber o dono da chave. */
    public boolean needsCrossing() {
        for (int s = 0; s < n; s++) if (keysConsistent(s).size() <= 1) return false;
        return true;
    }

    /** Fiel em todos os mundos coerentes? Devolve 1 (sempre fiel), 0 (sempre traidora) ou -1 (depende). */
    public int certainNature(int statue) {
        Boolean seen = null;
        boolean[] k = new boolean[n];
        for (int mask = 0; mask < (1 << n); mask++) {
            for (int i = 0; i < n; i++) k[i] = (mask >> i & 1) != 0;
            for (int key = 0; key < n; key++) {
                boolean ok = true;
                for (int s = 0; s < n && ok; s++) ok = says[s].eval(k, key) == k[s];
                if (!ok) continue;
                if (seen == null) seen = k[statue];
                else if (seen != k[statue]) return -1;
            }
        }
        return seen == null ? -1 : seen ? 1 : 0;
    }
}
