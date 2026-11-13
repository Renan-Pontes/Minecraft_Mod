package com.renanpontes.isekaimahou.feature.depths.puzzle.enigma.logic;

import java.util.Random;

/**
 * "Apague/Acenda Tudo" (Lights Out) em grade n×n: tocar numa runa inverte ela e as 4 vizinhas. Objetivo: todas acesas.
 * <p>Geração com solução GARANTIDA: parte de "tudo aceso" e aperta um conjunto sorteado de casas; apertar o mesmo
 * conjunto de novo desfaz tudo (cada toque é a sua própria inversa e a ordem não importa). {@link #verify()} confere.
 * Lógica pura (sem Minecraft) para poder ser testada sozinha.
 */
public final class LightsLogic {
    public final int n;
    /** Estado inicial (true = acesa), índice = linha * n + coluna. */
    public final boolean[] start;
    /** Um conjunto de toques que resolve a partir do início. */
    public final boolean[] solution;

    private LightsLogic(int n, boolean[] start, boolean[] solution) {
        this.n = n;
        this.start = start;
        this.solution = solution;
    }

    public static int sizeFor(int tier) {
        return tier <= 1 ? 3 : tier == 2 ? 4 : 5;
    }

    public static LightsLogic generate(long seed, int tier) {
        int n = sizeFor(tier);
        int minPress = tier <= 1 ? 2 : tier == 2 ? 4 : 7;
        Random r = new Random(seed);
        for (int attempt = 0; attempt < 200; attempt++) {
            boolean[] press = new boolean[n * n];
            int count = 0;
            for (int i = 0; i < press.length; i++) {
                if (r.nextInt(100) < 45) {
                    press[i] = true;
                    count++;
                }
            }
            while (count < minPress) {
                int i = r.nextInt(press.length);
                if (!press[i]) {
                    press[i] = true;
                    count++;
                }
            }
            boolean[] lit = new boolean[n * n];
            java.util.Arrays.fill(lit, true);
            for (int i = 0; i < press.length; i++) if (press[i]) toggle(lit, n, i);
            if (!allLit(lit)) {
                LightsLogic l = new LightsLogic(n, lit, press);
                if (l.verify()) return l;
            }
        }
        // Nunca deveria chegar aqui: um toque só no canto (sempre resolvível tocando nele de novo)
        boolean[] lit = new boolean[n * n];
        java.util.Arrays.fill(lit, true);
        boolean[] press = new boolean[n * n];
        press[0] = true;
        toggle(lit, n, 0);
        return new LightsLogic(n, lit, press);
    }

    /** Inverte a casa {@code idx} e as vizinhas (cima, baixo, esquerda, direita). */
    public static void toggle(boolean[] lit, int n, int idx) {
        int row = idx / n, col = idx % n;
        lit[idx] = !lit[idx];
        if (row > 0) lit[idx - n] = !lit[idx - n];
        if (row < n - 1) lit[idx + n] = !lit[idx + n];
        if (col > 0) lit[idx - 1] = !lit[idx - 1];
        if (col < n - 1) lit[idx + 1] = !lit[idx + 1];
    }

    public static boolean allLit(boolean[] lit) {
        for (boolean b : lit) if (!b) return false;
        return true;
    }

    /** Aplicar a solução ao estado inicial acende tudo? */
    public boolean verify() {
        boolean[] lit = start.clone();
        for (int i = 0; i < solution.length; i++) if (solution[i]) toggle(lit, n, i);
        return allLit(lit);
    }
}
