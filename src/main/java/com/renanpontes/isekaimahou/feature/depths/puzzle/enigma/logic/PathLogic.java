package com.renanpontes.isekaimahou.feature.depths.puzzle.enigma.logic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * "Caminho das Cores": piso de cores; só se pisa seguindo um ciclo de cores (ex.: vermelho → amarelo → azul → vermelho…),
 * um passo por vez para uma casa vizinha (frente, trás ou lado). Começa na primeira fileira, termina na última.
 * <ul>
 *   <li>nível 1: 3 cores, a ordem do ciclo fica exposta numa coluna de blocos ao lado;</li>
 *   <li>nível 2: 4 cores, ordem exposta, mais caminhos falsos (becos sem saída: é preciso planejar);</li>
 *   <li>nível 3: 4 cores e a ordem NÃO é mostrada — só as primeiras 3 casas do caminho são marcadas; a 4ª cor e o
 *       resto se deduzem.</li>
 * </ul>
 * <b>Solução garantida:</b> o caminho é desenhado primeiro; as outras casas recebem cores (muitas "iscas" que parecem
 * continuar o ciclo). {@link #solvable()} confere por busca em largura que existe uma rota válida até a última fileira.
 */
public final class PathLogic {
    public static final int COLS = 5;
    public final int rows;
    public final int k;
    /** Índices de cor (na paleta 0..5) na ordem do ciclo. */
    public final int[] cycle;
    /** Cor (índice na paleta) de cada casa, [linha][coluna]; linha 0 = mais perto da entrada. */
    public final int[][] grid;
    /** Caminho desenhado (linha * COLS + coluna), do começo ao fim. */
    public final List<Integer> path;

    private PathLogic(int rows, int k, int[] cycle, int[][] grid, List<Integer> path) {
        this.rows = rows;
        this.k = k;
        this.cycle = cycle;
        this.grid = grid;
        this.path = path;
    }

    public static int rowsFor(int tier) {
        return tier <= 1 ? 6 : 7;
    }

    public static PathLogic generate(long seed, int tier) {
        Random r = new Random(seed);
        int rows = rowsFor(tier);
        int k = tier <= 1 ? 3 : 4;
        int minLen = tier <= 1 ? rows + 2 : tier == 2 ? rows + 4 : rows + 6;
        for (int attempt = 0; attempt < 500; attempt++) {
            int[] palette = {0, 1, 2, 3, 4, 5};
            for (int i = palette.length - 1; i > 0; i--) {
                int j = r.nextInt(i + 1);
                int t = palette[i];
                palette[i] = palette[j];
                palette[j] = t;
            }
            int[] cycle = java.util.Arrays.copyOf(palette, k);
            List<Integer> path = walk(r, rows);
            if (path == null || path.size() < minLen) continue;
            int[][] grid = new int[rows][COLS];
            for (int[] row : grid) java.util.Arrays.fill(row, -1);
            for (int i = 0; i < path.size(); i++) {
                int c = path.get(i);
                grid[c / COLS][c % COLS] = cycle[i % k];
            }
            int decoy = tier <= 1 ? 45 : 70;
            for (int i = 0; i < path.size(); i++) {
                int c = path.get(i);
                int row = c / COLS, col = c % COLS;
                int[][] nb = {{row + 1, col}, {row - 1, col}, {row, col + 1}, {row, col - 1}};
                for (int[] n : nb) {
                    if (n[0] < 0 || n[0] >= rows || n[1] < 0 || n[1] >= COLS || grid[n[0]][n[1]] != -1) continue;
                    // isca: parece o próximo passo do ciclo
                    if (r.nextInt(100) < decoy) grid[n[0]][n[1]] = cycle[(i + 1) % k];
                }
            }
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < COLS; col++) if (grid[row][col] == -1) grid[row][col] = cycle[r.nextInt(k)];
            }
            PathLogic p = new PathLogic(rows, k, cycle, grid, path);
            if (p.solvable() && p.validPath(path)) return p;
        }
        // reserva: coluna reta no meio
        int[] cycle = java.util.Arrays.copyOf(new int[] {0, 1, 2, 3}, k);
        int[][] grid = new int[rows][COLS];
        List<Integer> path = new ArrayList<>();
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < COLS; col++) grid[row][col] = cycle[(row + 1 + col) % k];
            grid[row][2] = cycle[row % k];
            path.add(row * COLS + 2);
        }
        return new PathLogic(rows, k, cycle, grid, path);
    }

    /** Passeio sem repetir casa, da linha 0 até a última, com desvios laterais. */
    private static List<Integer> walk(Random r, int rows) {
        boolean[] used = new boolean[rows * COLS];
        List<Integer> path = new ArrayList<>();
        int row = 0, col = r.nextInt(COLS);
        path.add(col);
        used[col] = true;
        int guard = 0;
        while (row < rows - 1 && guard++ < 200) {
            int choice = r.nextInt(100);
            int nr = row, nc = col;
            if (choice < 40) nr = row + 1;
            else if (choice < 70) nc = col + 1;
            else if (choice < 95) nc = col - 1;
            else if (row > 1) nr = row - 1;
            if (nr < 0 || nr >= rows || nc < 0 || nc >= COLS) continue;
            int idx = nr * COLS + nc;
            if (used[idx]) continue;
            // não encostar em casas já usadas além da atual (o caminho não "se toca", fica legível)
            int touches = 0;
            int[][] nb = {{nr + 1, nc}, {nr - 1, nc}, {nr, nc + 1}, {nr, nc - 1}};
            for (int[] n : nb) {
                if (n[0] < 0 || n[0] >= rows || n[1] < 0 || n[1] >= COLS) continue;
                if (used[n[0] * COLS + n[1]]) touches++;
            }
            if (touches > 1) continue;
            row = nr;
            col = nc;
            used[idx] = true;
            path.add(idx);
        }
        return row == rows - 1 ? path : null;
    }

    /** O passo de {@code from} (índice i no ciclo) para {@code to} é válido? */
    public boolean step(int from, int to, int nextIndex) {
        int fr = from / COLS, fc = from % COLS, tr = to / COLS, tc = to % COLS;
        if (Math.abs(fr - tr) + Math.abs(fc - tc) != 1) return false;
        return grid[tr][tc] == cycle[nextIndex % k];
    }

    public int color(int cell) {
        return grid[cell / COLS][cell % COLS];
    }

    private boolean validPath(List<Integer> p) {
        if (p.isEmpty() || p.get(0) / COLS != 0 || color(p.get(0)) != cycle[0]) return false;
        for (int i = 1; i < p.size(); i++) if (!step(p.get(i - 1), p.get(i), i)) return false;
        return p.get(p.size() - 1) / COLS == rows - 1;
    }

    /** Busca em largura sobre (casa, posição no ciclo): dá para ir da primeira à última fileira seguindo a regra? */
    public boolean solvable() {
        boolean[][] seen = new boolean[rows * COLS][k];
        ArrayDeque<int[]> q = new ArrayDeque<>();
        for (int col = 0; col < COLS; col++) {
            if (grid[0][col] == cycle[0]) {
                seen[col][0] = true;
                q.add(new int[] {col, 0});
            }
        }
        while (!q.isEmpty()) {
            int[] s = q.poll();
            if (s[0] / COLS == rows - 1) return true;
            int row = s[0] / COLS, col = s[0] % COLS;
            int[][] nb = {{row + 1, col}, {row - 1, col}, {row, col + 1}, {row, col - 1}};
            for (int[] n : nb) {
                if (n[0] < 0 || n[0] >= rows || n[1] < 0 || n[1] >= COLS) continue;
                int to = n[0] * COLS + n[1];
                int ni = (s[1] + 1) % k;
                if (grid[n[0]][n[1]] != cycle[ni] || seen[to][ni]) continue;
                seen[to][ni] = true;
                q.add(new int[] {to, ni});
            }
        }
        return false;
    }
}
