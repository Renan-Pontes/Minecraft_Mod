package com.renanpontes.isekaimahou.feature.depths.puzzle.enigma.logic;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * "Pedras do Peso" (estilo Sokoban): caixas que só se EMPURRAM (nunca puxam), uma casa por vez, até ficarem todas
 * sobre as placas. Pilares (2 de altura) atrapalham; quinas e paredes prendem caixas para sempre (botão de recomeçar).
 * <p>Modelo do jogo: o jogador pula por cima de obstáculos de 1 bloco, então pode chegar em qualquer casa livre; para
 * empurrar a caixa em c na direção d ele precisa estar NO CHÃO da casa c−d (livre, sem caixa, sem pilar) e a casa c+d
 * tem que estar livre.
 * <p><b>Solução garantida:</b> as caixas começam nas placas e são PUXADAS ao contrário (movimento inverso válido);
 * depois uma busca em largura para a frente confere que dá para resolver e mede o mínimo de empurrões (dificuldade).
 */
public final class CratesLogic {
    public final int w, d;
    /** Pilares (true = pilar), índice = linha * w + coluna. */
    public final boolean[] pillar;
    public final int[] goals;
    public final int[] start;
    /** Mínimo de empurrões para resolver a partir do início. */
    public final int minPushes;

    private static final int[][] DIRS = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};

    private CratesLogic(int w, int d, boolean[] pillar, int[] goals, int[] start, int minPushes) {
        this.w = w;
        this.d = d;
        this.pillar = pillar;
        this.goals = goals;
        this.start = start;
        this.minPushes = minPushes;
    }

    public static int widthFor(int tier) {
        return tier <= 1 ? 5 : tier == 2 ? 6 : 7;
    }

    public static final int DEPTH = 5;

    /** Os mesmos enigmas são recriados ao carregar o chunk: guarda os últimos (a busca do nível 3 leva dezenas de ms). */
    private static final Map<Long, CratesLogic> CACHE = new java.util.LinkedHashMap<>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, CratesLogic> e) {
            return size() > 64;
        }
    };

    public static CratesLogic generate(long seed, int tier) {
        long k = seed * 31 + tier;
        synchronized (CACHE) {
            CratesLogic c = CACHE.get(k);
            if (c != null) return c;
        }
        CratesLogic c = create(seed, tier);
        synchronized (CACHE) {
            CACHE.put(k, c);
        }
        return c;
    }

    private static CratesLogic create(long seed, int tier) {
        Random r = new Random(seed);
        int w = widthFor(tier), d = DEPTH;
        int crates = tier <= 2 ? 2 : 3;
        int pillars = tier <= 1 ? 1 : 2;
        int want = tier <= 1 ? 4 : tier == 2 ? 8 : 12;
        CratesLogic best = null;
        for (int attempt = 0; attempt < 250; attempt++) {
            boolean[] pil = new boolean[w * d];
            int placed = 0, guard = 0;
            while (placed < pillars && guard++ < 100) {
                int x = 1 + r.nextInt(w - 2), y = 1 + r.nextInt(d - 2);
                boolean near = false;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        int nx = x + dx, ny = y + dy;
                        if (nx >= 0 && nx < w && ny >= 0 && ny < d && pil[ny * w + nx]) near = true;
                    }
                }
                if (near) continue;
                pil[y * w + x] = true;
                placed++;
            }
            int[] goals = new int[crates];
            for (int i = 0; i < crates; i++) {
                int g;
                do {
                    g = r.nextInt(w * d);
                } while (pil[g] || contains(goals, i, g));
                goals[i] = g;
            }
            int[] cur = goals.clone();
            int pulls = 30 + r.nextInt(120);
            for (int p = 0; p < pulls; p++) {
                int ci = r.nextInt(crates);
                int[] dir = DIRS[r.nextInt(4)];
                int cx = cur[ci] % w, cy = cur[ci] / w;
                // puxar: a caixa vai para c-d; o jogador estava em c-d e recua para c-2d
                int ax = cx - dir[0], ay = cy - dir[1], bx = cx - 2 * dir[0], by = cy - 2 * dir[1];
                if (!free(w, d, pil, cur, ax, ay) || !free(w, d, pil, cur, bx, by)) continue;
                cur[ci] = ay * w + ax;
            }
            if (onGoals(cur, goals) > 0) continue;
            int min = solve(w, d, pil, cur, goals);
            if (min < 0) continue;
            CratesLogic c = new CratesLogic(w, d, pil, goals, cur, min);
            if (best == null || min > best.minPushes) best = c;
            if (min >= want) break;
        }
        if (best != null) return best;
        // reserva: uma caixa a 2 casas da placa, numa fila livre
        boolean[] pil = new boolean[w * d];
        return new CratesLogic(w, d, pil, new int[] {2 * w + 1}, new int[] {2 * w + 3}, 2);
    }

    private static boolean contains(int[] a, int n, int v) {
        for (int i = 0; i < n; i++) if (a[i] == v) return true;
        return false;
    }

    private static boolean free(int w, int d, boolean[] pil, int[] crates, int x, int y) {
        if (x < 0 || x >= w || y < 0 || y >= d || pil[y * w + x]) return false;
        for (int c : crates) if (c == y * w + x) return false;
        return true;
    }

    private static int onGoals(int[] crates, int[] goals) {
        int n = 0;
        for (int c : crates) if (contains(goals, goals.length, c)) n++;
        return n;
    }

    private static long key(int[] crates) {
        int[] s = crates.clone();
        Arrays.sort(s);
        long k = 0;
        for (int c : s) k = k * 64 + c;
        return k;
    }

    /** Busca em largura: mínimo de empurrões até todas as caixas nas placas, ou -1 se impossível. */
    public static int solve(int w, int d, boolean[] pil, int[] start, int[] goals) {
        int[] first = firstPush(w, d, pil, start, goals);
        return first == null ? -1 : first[2];
    }

    /**
     * Primeiro empurrão de uma solução mínima a partir de {@code start}: {caixa (casa), direção 0..3, total de empurrões};
     * {..., 0} se já resolvido; null se não tem solução (caixa presa: precisa recomeçar).
     */
    public static int[] firstPush(int w, int d, boolean[] pil, int[] start, int[] goals) {
        if (onGoals(start, goals) == goals.length) return new int[] {-1, -1, 0};
        Map<Long, long[]> seen = new HashMap<>();   // estado -> {estado anterior, casa empurrada, direção, profundidade}
        ArrayDeque<int[]> q = new ArrayDeque<>();
        long k0 = key(start);
        seen.put(k0, new long[] {Long.MIN_VALUE, -1, -1, 0});
        q.add(start.clone());
        while (!q.isEmpty() && seen.size() < 200_000) {
            int[] s = q.poll();
            long ks = key(s);
            long depth = seen.get(ks)[3];
            for (int ci = 0; ci < s.length; ci++) {
                int cx = s[ci] % w, cy = s[ci] / w;
                for (int di = 0; di < 4; di++) {
                    int[] dir = DIRS[di];
                    if (!free(w, d, pil, s, cx - dir[0], cy - dir[1])) continue;
                    if (!free(w, d, pil, s, cx + dir[0], cy + dir[1])) continue;
                    int[] n = s.clone();
                    n[ci] = (cy + dir[1]) * w + cx + dir[0];
                    long kn = key(n);
                    if (seen.containsKey(kn)) continue;
                    seen.put(kn, new long[] {ks, s[ci], di, depth + 1});
                    if (onGoals(n, goals) == goals.length) {
                        // volta pelo caminho até o primeiro empurrão
                        long cur = kn;
                        long[] info = seen.get(cur);
                        while (info[0] != k0) {
                            cur = info[0];
                            info = seen.get(cur);
                        }
                        return new int[] {(int) info[1], (int) info[2], (int) (depth + 1)};
                    }
                    q.add(n);
                }
            }
        }
        return null;
    }

    /** Direções na ordem usada pelos índices: 0 = +coluna, 1 = +linha, 2 = −coluna, 3 = −linha. */
    public static int[] dir(int i) {
        return DIRS[i];
    }
}
