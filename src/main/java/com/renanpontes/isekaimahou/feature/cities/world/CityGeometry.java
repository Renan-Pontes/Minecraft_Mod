package com.renanpontes.isekaimahou.feature.cities.world;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;

/**
 * Medidas da cidade num quadro "canônico": centro em (0,0), norte = -z (a rotação para o mundo fica em
 * {@link CityFrame}). Os números são os mesmos de tools/gen/city_build.py (tamanho dos lotes e das peças).
 * Ao longo de cada semieixo, de dentro para fora:
 * <pre>
 *  avenida (|v| <= 3) · quarteirões de 22 com ruas de 5 entre eles (o 1º anel de quarteirões é a praça 51x51)
 *  · rua da muralha (5) · muralha (3) · torres/portões saindo para fora (até towerOut) · faixa de transição (10)
 * </pre>
 * Cidade (rings=2): muralha em 58..60, torres até 66 (~133 de largura). Capital (rings=3): muralha em 85..87,
 * torres até 93 (~187), castelo ao norte da praça.
 */
public final class CityGeometry {
    public static final int AVENUE = 3;
    public static final int BLOCK = 22;
    public static final int STREET = 5;
    public static final int PLAZA = 25;
    public static final int WALL = 3;
    public static final int TOWER_DEPTH = 9;
    public static final int TOWER_HALF = 4;
    public static final int GATE_HALF = 7;
    public static final int MARGIN = 10;
    /** Corpo da muralha em piso+1 .. piso+WALL_HEIGHT; anda-se em cima em piso+WALL_HEIGHT+1 (igual às torres). */
    public static final int WALL_HEIGHT = 7;
    public static final int GATE_N = 1;
    public static final int GATE_E = 2;
    public static final int GATE_S = 4;
    public static final int GATE_W = 8;
    public static final int MIN_RINGS = 2;
    public static final int MAX_RINGS = 3;

    /** O que fica numa coluna (x,z) da cidade. */
    public enum Zone { OUTSIDE, MARGIN, BUTTRESS, WALL, STRUCTURE, RING, STREET, AVENUE, PLAZA, CASTLE, LOT }

    public final int rings;
    public final boolean capital;
    public final int ringStart;
    public final int ringEnd;
    public final int wallIn;
    public final int wallOut;
    public final int towerOut;
    public final int extent;
    private final int[] towerCenters;
    private final int[] buttresses;
    private final int[] lampLines;

    public CityGeometry(int rings, boolean capital) {
        this.capital = capital;
        // A capital precisa de 3 anéis: o castelo ocupa os quarteirões ao norte da praça.
        this.rings = Math.max(capital ? MAX_RINGS : MIN_RINGS, Math.min(MAX_RINGS, rings));
        this.ringStart = blockEnd(this.rings) + 1;
        this.ringEnd = ringStart + STREET - 1;
        this.wallIn = ringEnd + 1;
        this.wallOut = wallIn + WALL - 1;
        this.towerOut = wallIn + TOWER_DEPTH - 1;
        this.extent = towerOut + MARGIN;
        // Torres no fim de cada rua que encontra a muralha.
        towerCenters = new int[this.rings - 1];
        for (int i = 1; i < this.rings; i++) towerCenters[i - 1] = blockEnd(i) + 1 + STREET / 2;
        // Contrafortes nos trechos livres de muralha (longe do portão e das torres), a cada 7 blocos.
        boolean[] free = new boolean[wallIn];
        for (int v = GATE_HALF + 2; v <= wallIn - 2; v++) {
            free[v] = true;
            for (int c : towerCenters) {
                if (Math.abs(v - c) <= TOWER_HALF + 1) free[v] = false;
            }
        }
        List<Integer> b = new ArrayList<>();
        int v = 0;
        while (v < wallIn) {
            if (!free[v]) {
                v++;
                continue;
            }
            int start = v;
            while (v < wallIn && free[v]) v++;
            for (int p = start + 3; p <= v - 3; p += 7) b.add(p);
        }
        buttresses = b.stream().mapToInt(Integer::intValue).toArray();
        // Linhas dos postes: a rua logo fora de cada borda de quarteirão (esquinas = cruzamento de duas linhas).
        TreeSet<Integer> lines = new TreeSet<>();
        for (int i = 1; i <= this.rings; i++) {
            lines.add(blockStart(i) - 1);
            lines.add(blockEnd(i) + 1);
        }
        lampLines = lines.stream().mapToInt(Integer::intValue).toArray();
    }

    public int blockStart(int i) {
        return AVENUE + 1 + (i - 1) * (BLOCK + STREET);
    }

    public int blockEnd(int i) {
        return blockStart(i) + BLOCK - 1;
    }

    /** Centros (ao longo da muralha, valor absoluto) das torres laterais. */
    public int[] towerCenters() {
        return towerCenters.clone();
    }

    /** Raio usado no city_heart e nos lugares nomeados: até a ponta das torres. */
    public int radius() {
        return towerOut;
    }

    public boolean inStreetBand(int v) {
        for (int i = 1; i < rings; i++) {
            int lo = blockEnd(i) + 1;
            if (v >= lo && v < lo + STREET) return true;
        }
        return false;
    }

    /** Castelo da capital: quarteirões ao norte da praça (x -25..25, z -79..-31) com a avenida entre eles. */
    public boolean inCastle(int x, int z) {
        return capital && Math.abs(x) <= PLAZA && z <= -blockStart(2) && z >= -blockEnd(3);
    }

    public Zone zone(int x, int z, int gates) {
        int ax = Math.abs(x);
        int az = Math.abs(z);
        int m = Math.max(ax, az);
        if (m > extent) return Zone.OUTSIDE;
        if (m >= wallIn && m <= towerOut && inStructure(x, z, gates)) return Zone.STRUCTURE;
        if (m > wallOut) return isButtress(x, z) ? Zone.BUTTRESS : Zone.MARGIN;
        if (m >= wallIn) return Zone.WALL;
        if (m >= ringStart) return Zone.RING;
        if (ax <= PLAZA && az <= PLAZA) return Zone.PLAZA;
        if (inCastle(x, z)) return Zone.CASTLE;
        if (ax <= AVENUE || az <= AVENUE) return Zone.AVENUE;
        if (inStreetBand(ax) || inStreetBand(az)) return Zone.STREET;
        return Zone.LOT;
    }

    /** Torres (cantos, laterais, meio de lado sem portão) e portões: ocupam wallIn..towerOut para fora. */
    public boolean inStructure(int x, int z, int gates) {
        int ax = Math.abs(x);
        int az = Math.abs(z);
        if (ax >= wallIn && az >= wallIn) return ax <= towerOut && az <= towerOut;
        boolean ns = az >= wallIn;
        int depth = ns ? az : ax;
        if (depth < wallIn || depth > towerOut) return false;
        int along = Math.abs(ns ? x : z);
        if ((gates & side(x, z)) != 0) {
            if (along <= GATE_HALF) return true;
        } else if (along <= TOWER_HALF) {
            return true;
        }
        for (int c : towerCenters) {
            if (Math.abs(along - c) <= TOWER_HALF) return true;
        }
        return false;
    }

    /** Coluna de contraforte: logo fora da face externa da muralha, nos pontos escolhidos. */
    public boolean isButtress(int x, int z) {
        int ax = Math.abs(x);
        int az = Math.abs(z);
        if (ax >= wallIn && az >= wallIn) return false;
        if (Math.max(ax, az) != wallOut + 1) return false;
        return Arrays.binarySearch(buttresses, ax >= wallIn ? az : ax) >= 0;
    }

    public boolean isLampSpot(int x, int z) {
        return Arrays.binarySearch(lampLines, Math.abs(x)) >= 0 && Arrays.binarySearch(lampLines, Math.abs(z)) >= 0;
    }

    /** Lado da muralha (GATE_N/E/S/W) de uma coluna fora do miolo: o eixo mais distante do centro manda. */
    public static int side(int x, int z) {
        if (Math.abs(z) >= Math.abs(x)) return z < 0 ? GATE_N : GATE_S;
        return x < 0 ? GATE_W : GATE_E;
    }

    /** Coordenada ao longo da muralha desse lado. */
    public static int along(int x, int z) {
        return Math.abs(z) >= Math.abs(x) ? x : z;
    }
}
