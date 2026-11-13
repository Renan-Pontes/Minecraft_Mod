package com.renanpontes.isekaimahou.feature.depths.puzzle.enigma.logic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * "Feixe dos Espelhos": um feixe sai do Emissor e anda reto; Espelhos (girar com o botão direito) o desviam 90°;
 * pilares o bloqueiam. Objetivo: levar o feixe até o Cristal. Há espelhos de enfeite que não fazem parte do caminho.
 * <ul><li>nível 1: 2 curvas, 1 espelho falso; nível 2: 3 curvas, 2 falsos, 2 pilares; nível 3: 5 curvas, 3 falsos, 3 pilares.</li></ul>
 * Coordenadas do enigma: coluna x (0..W−1, para a direita) e linha y (0..H−1, para o fundo); o Emissor fica na linha
 * −1 apontando para o fundo. Direções: 0 = +x, 1 = +y, 2 = −x, 3 = −y. Espelho tipo 0 troca x↔y (0↔1, 2↔3);
 * tipo 1 troca com sinal (0↔3, 1↔2).
 * <p><b>Solução garantida:</b> o caminho é desenhado primeiro e cada espelho de curva recebe o tipo certo;
 * {@link #trace(int[])} confere que, com a solução, o feixe chega ao Cristal (e que embaralhado, não chega).
 */
public final class BeamLogic {
    public static final int W = 7, H = 6;

    public final int emitterX;
    public final int target;
    /** Células com espelho (y * W + x): primeiro os do caminho, depois os falsos. */
    public final int[] mirrors;
    /** Tipo de cada espelho na solução (falsos: qualquer). */
    public final int[] solution;
    /** Tipo de cada espelho no começo (embaralhado: o feixe não chega). */
    public final int[] scrambled;
    public final Set<Integer> obstacles;
    /** Quantos dos {@link #mirrors} são do caminho. */
    public final int pathMirrors;

    private BeamLogic(int emitterX, int target, int[] mirrors, int[] solution, int[] scrambled, Set<Integer> obstacles, int pathMirrors) {
        this.emitterX = emitterX;
        this.target = target;
        this.mirrors = mirrors;
        this.solution = solution;
        this.scrambled = scrambled;
        this.obstacles = obstacles;
        this.pathMirrors = pathMirrors;
    }

    private static final int[][] D = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};

    public static int reflect(int dir, int type) {
        return type == 0 ? new int[] {1, 0, 3, 2}[dir] : new int[] {3, 2, 1, 0}[dir];
    }

    public static BeamLogic generate(long seed, int tier) {
        Random r = new Random(seed);
        int turns = tier <= 1 ? 2 : tier == 2 ? 3 : 5;
        int decoys = tier <= 1 ? 1 : tier == 2 ? 2 : 3;
        int pillars = tier <= 1 ? 0 : tier == 2 ? 2 : 3;
        for (int attempt = 0; attempt < 2000; attempt++) {
            int ex = r.nextInt(W);
            int x = ex, y = -1, dir = 1;
            Set<Integer> used = new HashSet<>();
            List<Integer> mir = new ArrayList<>();
            List<Integer> types = new ArrayList<>();
            boolean ok = true;
            int target = -1;
            for (int t = 0; t <= turns && ok; t++) {
                List<Integer> lens = new ArrayList<>();
                for (int len = 1; len <= Math.max(W, H); len++) {
                    int ex2 = x + D[dir][0] * len, ey2 = y + D[dir][1] * len;
                    if (ex2 < 0 || ex2 >= W || ey2 < 0 || ey2 >= H) break;
                    if (mir.contains(ey2 * W + ex2)) break;      // passaria por um espelho
                    if (!used.contains(ey2 * W + ex2)) lens.add(len);
                }
                if (lens.isEmpty()) {
                    ok = false;
                    break;
                }
                int len = lens.get(r.nextInt(lens.size()));
                for (int s = 1; s <= len; s++) {
                    x += D[dir][0];
                    y += D[dir][1];
                    if (s < len) used.add(y * W + x);
                }
                int cell = y * W + x;
                if (t < turns) {
                    int nd = r.nextBoolean() ? (dir + 1) % 4 : (dir + 3) % 4;
                    mir.add(cell);
                    types.add(reflect(dir, 0) == nd ? 0 : 1);
                    used.add(cell);
                    dir = nd;
                } else {
                    target = cell;
                    used.add(cell);
                }
            }
            if (!ok || target < 0) continue;
            int pathMirrors = mir.size();
            // espelhos falsos e pilares fora do caminho do feixe
            List<Integer> free = new ArrayList<>();
            for (int c = 0; c < W * H; c++) if (!used.contains(c) && c != ex) free.add(c);
            java.util.Collections.shuffle(free, r);
            if (free.size() < decoys + pillars) continue;
            for (int i = 0; i < decoys; i++) {
                mir.add(free.get(i));
                types.add(r.nextInt(2));
            }
            Set<Integer> obst = new HashSet<>(free.subList(decoys, decoys + pillars));
            int[] mirrors = mir.stream().mapToInt(Integer::intValue).toArray();
            int[] sol = types.stream().mapToInt(Integer::intValue).toArray();
            BeamLogic test = new BeamLogic(ex, target, mirrors, sol, sol, obst, pathMirrors);
            if (!test.trace(sol)) continue;
            int[] scr = sol.clone();
            boolean scrambledOk = false;
            for (int tries = 0; tries < 50; tries++) {
                for (int i = 0; i < scr.length; i++) scr[i] = r.nextInt(2);
                if (!test.trace(scr) && wrongCount(scr, sol, pathMirrors) >= Math.min(pathMirrors, tier + 1)) {
                    scrambledOk = true;
                    break;
                }
            }
            if (!scrambledOk) continue;
            return new BeamLogic(ex, target, mirrors, sol, scr, obst, pathMirrors);
        }
        // reserva: emissor na coluna 0, um espelho virando para a direita, cristal no fim da linha
        int[] mirrors = {2 * W};
        int[] sol = {reflect(1, 0) == 0 ? 0 : 1};
        int[] scr = {1 - sol[0]};
        return new BeamLogic(0, 2 * W + 4, mirrors, sol, scr, Set.of(), 1);
    }

    private static int wrongCount(int[] a, int[] b, int n) {
        int w = 0;
        for (int i = 0; i < n; i++) if (a[i] != b[i]) w++;
        return w;
    }

    /** Com estes tipos de espelho, o feixe chega ao Cristal? */
    public boolean trace(int[] types) {
        int x = emitterX, y = -1, dir = 1;
        for (int step = 0; step < 200; step++) {
            x += D[dir][0];
            y += D[dir][1];
            if (x < 0 || x >= W || y < 0 || y >= H) return false;
            int c = y * W + x;
            if (c == target) return true;
            if (obstacles.contains(c)) return false;
            for (int i = 0; i < mirrors.length; i++) {
                if (mirrors[i] == c) {
                    dir = reflect(dir, types[i]);
                    break;
                }
            }
        }
        return false;
    }
}
