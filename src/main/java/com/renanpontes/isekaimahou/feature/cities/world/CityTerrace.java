package com.renanpontes.isekaimahou.feature.cities.world;

import com.renanpontes.isekaimahou.feature.cities.world.CityGeometry.Zone;

import net.minecraft.util.Mth;

/**
 * Relevo do piso da cidade (v1.4): em vez de um piso único, um plano levemente inclinado que acompanha o terreno, com a
 * praça central sempre plana. Tudo em matemática inteira e só a partir de valores salvos (floorY, gradientes em
 * milésimos, nível mínimo), então qualquer chunk calcula exatamente a mesma altura para a mesma coluna.
 * <pre>
 *  h2(x,z) = arred( 2·(gx·f(x) + gz·f(z)) )   em MEIOS blocos, no plano canônico
 *  f(v)    = sinal(v)·máx(0, |v| − PLAZA_FLAT)  (a praça 51x51 e a borda dela ficam em floorY)
 *  H       = floorY + floorDiv(h2, 2)            nível inteiro (bloco do piso); h2 ímpar = meia laje por cima
 * </pre>
 * Ruas/avenidas/anel seguem H coluna a coluna (com meia laje); lotes, castelo, torres e portões ficam num terraço plano
 * no nível do seu quarteirão/peça ({@link #surface}); muralha e contrafortes seguem a linha do meio-fio de dentro.
 * Gradiente zero (cidades salvas antes da v1.4) = piso único em floorY, igual a antes.
 */
public final class CityTerrace {
    /** Inclinação máxima por eixo (≈ 1 bloco de subida a cada 11). */
    public static final double MAX_GRADE = 0.09;
    /** Escala dos gradientes salvos (milésimos de bloco por bloco). */
    public static final int SCALE = 1000;
    /** Até aqui (|v|) o piso é plano: a praça (25) e mais 2 de rua em volta. */
    public static final int PLAZA_FLAT = CityGeometry.PLAZA + 2;

    private final CityGeometry geo;
    private final int floorY;
    private final int gates;
    /** Gradientes salvos (eixos do MUNDO, milésimos). */
    private final int gxMilli;
    private final int gzMilli;
    /** Nível mínimo (nível do mar na hora da geração): a parte de baixo nunca afunda abaixo da água. */
    private final int minLevel;
    /** Gradientes já levados para o plano canônico (milésimos). */
    private final int lgx;
    private final int lgz;
    private final int minH2;

    public CityTerrace(CityGeometry geo, CityFrame frame, int gates, int gxMilli, int gzMilli, int minLevel) {
        this.geo = geo;
        this.floorY = frame.floorY();
        this.gates = gates;
        this.gxMilli = gxMilli;
        this.gzMilli = gzMilli;
        this.minLevel = Math.min(minLevel, frame.floorY());
        // Altura no mundo = gx·dx + gz·dz, com dx/dz lineares em (x,z) canônicos: troca de eixos/sinal pela rotação.
        int ax = frame.worldX(1, 0) - frame.centerX();
        int az = frame.worldZ(1, 0) - frame.centerZ();
        int bx = frame.worldX(0, 1) - frame.centerX();
        int bz = frame.worldZ(0, 1) - frame.centerZ();
        this.lgx = gxMilli * ax + gzMilli * az;
        this.lgz = gxMilli * bx + gzMilli * bz;
        this.minH2 = 2 * (this.minLevel - floorY);
    }

    /** Gradiente (blocos por bloco) para o valor salvo, já limitado a ±MAX_GRADE. */
    public static int toMilli(double grade) {
        return (int) Math.round(Mth.clamp(grade, -MAX_GRADE, MAX_GRADE) * SCALE);
    }

    /** f(v) do cabeçalho, em coordenadas dobradas (v2 = 2·v; serve para centros "de meio bloco"). */
    static int flat2(int v2) {
        int a = Math.abs(v2) - 2 * PLAZA_FLAT;
        if (a <= 0) return 0;
        return v2 < 0 ? -a : a;
    }

    /** f(v) em blocos (inteiro), usado na medição do terreno. */
    static int flat1(int v) {
        int a = Math.abs(v) - PLAZA_FLAT;
        if (a <= 0) return 0;
        return v < 0 ? -a : a;
    }

    public int floorY() {
        return floorY;
    }

    public int gxMilli() {
        return gxMilli;
    }

    public int gzMilli() {
        return gzMilli;
    }

    public int minLevel() {
        return minLevel;
    }

    public boolean isFlat() {
        return lgx == 0 && lgz == 0;
    }

    /** Altura em meios blocos acima de floorY num ponto canônico dado em coordenadas DOBRADAS. */
    int h2(int x2, int z2) {
        long num = (long) lgx * flat2(x2) + (long) lgz * flat2(z2);
        int h = (int) Math.floorDiv(num + SCALE / 2, (long) SCALE);
        return Math.max(h, minH2);
    }

    /** Nível inteiro (bloco do piso) num ponto em coordenadas dobradas. */
    int level2(int x2, int z2) {
        return floorY + Math.floorDiv(h2(x2, z2), 2);
    }

    /** Nível inteiro da própria coluna (ruas). */
    public int level(int x, int z) {
        return level2(2 * x, 2 * z);
    }

    /** A coluna de rua leva meia laje por cima do piso (h2 ímpar). */
    public boolean half(int x, int z) {
        return (h2(2 * x, 2 * z) & 1) != 0;
    }

    /** Menor e maior nível dentro do quadrado |x|,|z| <= r (o plano é monótono em cada eixo: basta olhar os cantos). */
    public int lowest(int r) {
        int m = Integer.MAX_VALUE;
        for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) m = Math.min(m, level2(2 * r * sx, 2 * r * sz));
        return Math.min(m, floorY);
    }

    public int highest(int r) {
        int m = Integer.MIN_VALUE;
        for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) m = Math.max(m, level2(2 * r * sx, 2 * r * sz));
        return Math.max(m, floorY);
    }

    /** Nível do piso de uma coluna canônica qualquer (calcula a zona). */
    public int surface(int x, int z) {
        return surface(x, z, geo.zone(x, z, gates));
    }

    /**
     * Nível do piso (bloco onde fica o calçamento / o y=0 do template) de uma coluna, pela zona. É a MESMA função na
     * planta (onde os templates são postos) e na peça do chão (onde o terreno é preparado), então os dois sempre
     * concordam:
     * <ul>
     *  <li>rua, avenida, anel e faixa de fora: a própria coluna;</li>
     *  <li>praça: floorY; castelo: o centro do castelo;</li>
     *  <li>lote: o centro do quarteirão 22x22 (todos os lotes do quarteirão no mesmo terraço);</li>
     *  <li>torre/portão: o meio-fio de dentro na frente do centro da peça (o portão fica no nível da rua do anel);</li>
     *  <li>muralha, contraforte e talude: o meio-fio de dentro na mesma posição ao longo da muralha.</li>
     * </ul>
     */
    public int surface(int x, int z, Zone zone) {
        return switch (zone) {
            case PLAZA -> floorY;
            case CASTLE -> level2(0, -(geo.blockStart(2) + geo.blockEnd(3)));
            case LOT -> level2(blockCenter2(x), blockCenter2(z));
            case STRUCTURE -> structureLevel(x, z);
            case WALL, BUTTRESS -> wallLevel(x, z);
            case MARGIN -> wallLine(x, z);
            default -> level(x, z);
        };
    }

    /**
     * Nível de uma coluna da muralha (ou contraforte): o do meio-fio de dentro na mesma posição, mas as 2 colunas
     * encostadas numa torre/portão ficam no nível dela — assim a porta do passeio da torre nunca fica meio bloco
     * enterrada; o degrau do passeio cai no meio do trecho.
     */
    public int wallLevel(int x, int z) {
        boolean ns = Math.abs(z) >= geo.wallIn;
        int depth = ns ? (z < 0 ? -geo.wallIn : geo.wallIn) : (x < 0 ? -geo.wallIn : geo.wallIn);
        int along = ns ? x : z;
        for (int d = 1; d <= 2; d++) {
            for (int s = -1; s <= 1; s += 2) {
                int a = along + s * d;
                int px = ns ? a : depth;
                int pz = ns ? depth : a;
                if (geo.inStructure(px, pz, gates)) return structureLevel(px, pz);
            }
        }
        return wallLine(x, z);
    }

    /** Muralha/talude: projeta a coluna no quadrado do meio-fio de dentro (ringEnd). */
    public int wallLine(int x, int z) {
        int re = geo.ringEnd;
        return level2(2 * Mth.clamp(x, -re, re), 2 * Mth.clamp(z, -re, re));
    }

    /** Nível do portão do lado (GATE_N/E/S/W): o meio-fio de dentro no meio do lado. */
    public int gateLevel(int side) {
        int re2 = 2 * geo.ringEnd;
        return switch (side) {
            case CityGeometry.GATE_N -> level2(0, -re2);
            case CityGeometry.GATE_S -> level2(0, re2);
            case CityGeometry.GATE_W -> level2(-re2, 0);
            default -> level2(re2, 0);
        };
    }

    /** Centro (dobrado, com sinal) do quarteirão que contém v; fora de quarteirão, a própria coluna. */
    private int blockCenter2(int v) {
        int a = Math.abs(v);
        for (int i = 1; i <= geo.rings; i++) {
            if (a >= geo.blockStart(i) && a <= geo.blockEnd(i)) {
                int c = geo.blockStart(i) + geo.blockEnd(i);
                return v < 0 ? -c : c;
            }
        }
        return 2 * v;
    }

    /** Mesma divisão de {@link CityGeometry#inStructure}: canto, portão/torre do meio ou torre lateral. */
    private int structureLevel(int x, int z) {
        int ax = Math.abs(x);
        int az = Math.abs(z);
        int re2 = 2 * geo.ringEnd;
        if (ax >= geo.wallIn && az >= geo.wallIn) return level2(x < 0 ? -re2 : re2, z < 0 ? -re2 : re2);
        boolean ns = az >= geo.wallIn;
        int along = ns ? x : z;
        int a = Math.abs(along);
        int c = 0;
        boolean middle = (gates & CityGeometry.side(x, z)) != 0 ? a <= CityGeometry.GATE_HALF : a <= CityGeometry.TOWER_HALF;
        if (!middle) {
            for (int t : geo.towerCenters()) {
                if (Math.abs(a - t) <= CityGeometry.TOWER_HALF) {
                    c = along < 0 ? -t : t;
                    break;
                }
            }
        }
        int depth2 = (ns ? z : x) < 0 ? -re2 : re2;
        return ns ? level2(2 * c, depth2) : level2(depth2, 2 * c);
    }
}
