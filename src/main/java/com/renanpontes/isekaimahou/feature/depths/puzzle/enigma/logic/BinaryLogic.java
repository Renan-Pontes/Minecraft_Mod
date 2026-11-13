package com.renanpontes.isekaimahou.feature.depths.puzzle.enigma.logic;

import java.util.Random;

/**
 * "Alavancas do Tesoureiro": cada alavanca levantada soma um peso; os pesos dobram da direita para a esquerda
 * (I, II, IV, VIII…, numeral romano). O Selo pede um número em algarismos romanos; o jogador levanta as alavancas
 * cuja soma dá o número e aperta o botão do julgamento.
 * <ul>
 *   <li>nível 1: 4 alavancas, legenda dos pesos exposta;</li>
 *   <li>nível 2: 6 alavancas, sem legenda (só a regra "cada uma vale o dobro da da direita");</li>
 *   <li>nível 3: 7 alavancas e o número vem como uma conta em romano ("XC − XXXVII") que precisa ser resolvida antes.</li>
 * </ul>
 * <b>Solução única e garantida:</b> todo número de 1 a 2ⁿ−1 tem exatamente uma combinação (escrita binária).
 */
public final class BinaryLogic {
    public final int bits;
    public final int target;
    /** Texto mostrado no Selo (romano ou conta em romano). */
    public final String display;

    private BinaryLogic(int bits, int target, String display) {
        this.bits = bits;
        this.target = target;
        this.display = display;
    }

    public static int bitsFor(int tier) {
        return tier <= 1 ? 4 : tier == 2 ? 6 : 7;
    }

    public static BinaryLogic generate(long seed, int tier) {
        Random r = new Random(seed);
        int bits = bitsFor(tier);
        int max = (1 << bits) - 1;
        int minBits = tier <= 1 ? 2 : 3;
        int t;
        do {
            t = 1 + r.nextInt(max);
        } while (Integer.bitCount(t) < minBits || Integer.bitCount(t) > bits - 1);
        String display = roman(t);
        if (tier >= 3) {
            // conta em romano: a + b, a − b ou a × b, sempre dando t
            int op = r.nextInt(3);
            if (op == 0) {
                int a = 1 + r.nextInt(t - 1);
                display = roman(a) + " + " + roman(t - a);
            } else if (op == 1) {
                int b = 2 + r.nextInt(40);
                display = roman(t + b) + " − " + roman(b);
            } else {
                int f = smallFactor(t);
                if (f > 1) display = roman(t / f) + " × " + roman(f);
                else {
                    int b = 2 + r.nextInt(40);
                    display = roman(t + b) + " − " + roman(b);
                }
            }
        }
        return new BinaryLogic(bits, t, display);
    }

    private static int smallFactor(int t) {
        for (int f = 3; f <= 9; f++) if (t % f == 0 && t / f > 1) return f;
        return t % 2 == 0 && t / 2 > 1 ? 2 : 1;
    }

    /** Alavanca {@code i} (0 = a da ESQUERDA) deve ficar levantada? */
    public boolean up(int i) {
        return (target >> (bits - 1 - i) & 1) != 0;
    }

    /** Peso da alavanca {@code i} (0 = esquerda). */
    public int weight(int i) {
        return 1 << (bits - 1 - i);
    }

    public static String roman(int n) {
        int[] v = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] s = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < v.length; i++) {
            while (n >= v[i]) {
                b.append(s[i]);
                n -= v[i];
            }
        }
        return b.toString();
    }

    /** Confere: a conta mostrada (se houver) dá o alvo. */
    public boolean verify() {
        String d = display.replace(" ", "");
        int val;
        if (d.contains("+")) {
            String[] p = d.split("\\+");
            val = fromRoman(p[0]) + fromRoman(p[1]);
        } else if (d.contains("−")) {
            String[] p = d.split("−");
            val = fromRoman(p[0]) - fromRoman(p[1]);
        } else if (d.contains("×")) {
            String[] p = d.split("×");
            val = fromRoman(p[0]) * fromRoman(p[1]);
        } else val = fromRoman(d);
        return val == target && target >= 1 && target < (1 << bits);
    }

    public static int fromRoman(String s) {
        int total = 0, prev = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            int v = switch (s.charAt(i)) {
                case 'I' -> 1; case 'V' -> 5; case 'X' -> 10; case 'L' -> 50; case 'C' -> 100; case 'D' -> 500; case 'M' -> 1000;
                default -> 0;
            };
            total += v < prev ? -v : v;
            prev = Math.max(prev, v);
        }
        return total;
    }
}
