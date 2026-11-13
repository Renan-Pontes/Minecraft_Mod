package com.renanpontes.isekaimahou.feature.settlements.build;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.feature.settlements.Settlement;
import com.renanpontes.isekaimahou.feature.settlements.SettlementStyle;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Planta baixa da cidade do jogador: anéis de terrenos de 16 blocos em volta do coração (o do meio é a praça). A cidade
 * começa com 5×5 (raio 2) e CRESCE anel por anel ({@link Settlement#radius}, até {@link #MAX_RADIUS}).
 * <pre>
 *   níveis que liberam os 5×5 do começo:   4 3 3 3 4
 *                                          3 2 1 2 3
 *                                          3 1 P 1 3
 *                                          3 2 1 2 3
 *                                          4 3 3 3 4
 * </pre>
 * Os anéis de fora (3, 4, ...) são liberados EXPANDINDO a cidade. A cada 2 anéis existe um "vão" de 8 blocos reservado
 * para uma muralha (anel de muralha k = 1, 2, 3...: fica em volta dos anéis 2k). Dá para ter várias muralhas, uma em cada
 * vão. A muralha k fica a {@link #wallDist} blocos do coração (k=1 → 42, como sempre foi).
 * <p>Ids dos terrenos: os 5×5 antigos mantêm 0..24 (mundos salvos continuam valendo, praça = 12); os de fora usam
 * {@code 1000 + (i+R)*(2R+1) + (j+R)}.
 * <p>Os prédios giram para a entrada (lado +Z do molde) olhar para a praça.
 * <p><b>v1.0 (city_plan) — posição livre</b>: além da grade, um prédio pode ficar em QUALQUER ponto da cidade, virado
 * para qualquer lado. O id de um lote livre GUARDA a posição: {@code FREE | rotação | dx | dz} (deslocamento a partir do
 * coração). Assim {@link #cellCenter} e {@link #facingHeart} servem para os dois tipos e nada novo precisa ser salvo —
 * os mapas da cidade (prédios, níveis, estilos, alturas) usam o id como sempre. Regras de onde cabe: {@code CityPlots}.
 */
public final class CityLayout {
    public static final int CELL = 16;
    /** Raio da grade antiga (5×5). */
    public static final int GRID = 2;
    /** Raio inicial de uma cidade nova. */
    public static final int START_RADIUS = 2;
    /** Até quantos anéis a cidade pode crescer (25×25 terrenos, ~500 blocos de lado). */
    public static final int MAX_RADIUS = 12;
    /** Muralha do primeiro anel (compatibilidade). */
    public static final int WALL = 42;
    /** Território da cidade de 5×5 (compatibilidade). */
    public static final int TERRITORY = 46;
    public static final int BOX_DOWN = 24;
    public static final int BOX_UP = 56;
    /** Distância mínima entre o coração novo e o centro de outra cidade. */
    public static final int MIN_CITY_DISTANCE = 220;
    /** Onda 9 (fundar mais fácil): distância mínima até o centro de uma VILA (as caixas nunca podem se cruzar). */
    public static final int MIN_VILLAGE_DISTANCE = 140;
    /** Ids antigos (5×5). */
    public static final int CELLS = 25;
    public static final int CENTER_CELL = 12;
    private static final int OUTER = 1000;
    private static final int SPAN = 2 * MAX_RADIUS + 1;
    /** Largura do vão reservado para uma muralha entre os anéis 2k e 2k+1. */
    private static final int WALL_GAP = 8;

    // =====================================================================================
    //  Ids e coordenadas
    // =====================================================================================
    public static int cellIndex(int i, int j) {
        if (Math.abs(i) <= GRID && Math.abs(j) <= GRID) return (i + GRID) * 5 + (j + GRID);
        return OUTER + (i + MAX_RADIUS) * SPAN + (j + MAX_RADIUS);
    }

    // ---- lotes livres (v1.0): o id guarda dx, dz e a rotação
    /** Marca de lote livre no id (acima de qualquer id da grade). */
    public static final int FREE = 1 << 24;
    private static final int FREE_BIAS = 512, FREE_MASK = 1023;

    public static boolean isFree(int cell) {
        return cell >= FREE && cell < FREE << 1;
    }

    /** Id do lote livre com centro em (dx, dz) a partir do coração, entrada virada pela rotação. */
    public static int freeCell(int dx, int dz, Rotation rot) {
        int x = Math.max(-FREE_BIAS + 1, Math.min(FREE_BIAS - 1, dx)), z = Math.max(-FREE_BIAS + 1, Math.min(FREE_BIAS - 1, dz));
        return FREE | rot.ordinal() << 20 | (x + FREE_BIAS) << 10 | (z + FREE_BIAS);
    }

    public static int freeDx(int cell) {
        return (cell >> 10 & FREE_MASK) - FREE_BIAS;
    }

    public static int freeDz(int cell) {
        return (cell & FREE_MASK) - FREE_BIAS;
    }

    public static Rotation freeRot(int cell) {
        return Rotation.values()[cell >> 20 & 3];
    }

    /** Deslocamento (dx, dz) do centro de QUALQUER lote (grade ou livre) a partir do coração. */
    public static int offsetX(int cell) {
        return isFree(cell) ? freeDx(cell) : isIsle(cell) ? ISLE_PITCH * isleI(cell) : axis(cellI(cell));
    }

    public static int offsetZ(int cell) {
        return isFree(cell) ? freeDz(cell) : isIsle(cell) ? ISLE_PITCH * isleJ(cell) : axis(cellJ(cell));
    }

    /** Índice da grade mais perto do deslocamento {@code d} (lotes livres "pertencem" ao anel do terreno mais próximo). */
    public static int nearestIndex(int d) {
        int best = 0, bd = Integer.MAX_VALUE;
        for (int i = -MAX_RADIUS; i <= MAX_RADIUS; i++) {
            int dist = Math.abs(d - axis(i));
            if (dist < bd) {
                bd = dist;
                best = i;
            }
        }
        return best;
    }

    public static int cellI(int cell) {
        if (isIsle(cell)) return isleI(cell);
        if (isFree(cell)) return nearestIndex(freeDx(cell));
        return cell >= OUTER ? (cell - OUTER) / SPAN - MAX_RADIUS : cell / 5 - GRID;
    }

    public static int cellJ(int cell) {
        if (isIsle(cell)) return isleJ(cell);
        if (isFree(cell)) return nearestIndex(freeDz(cell));
        return cell >= OUTER ? (cell - OUTER) % SPAN - MAX_RADIUS : cell % 5 - GRID;
    }

    /** Id que existe na grade (qualquer tamanho até o máximo). */
    public static boolean valid(int cell) {
        if (cell >= 0 && cell < CELLS) return true;
        if (isIsle(cell)) {
            int r = Math.max(Math.abs(isleI(cell)), Math.abs(isleJ(cell)));
            return r >= 1 && r <= MAX_RADIUS;
        }
        if (cell < OUTER || cell >= OUTER + SPAN * SPAN) return false;
        return ring(cell) > GRID;
    }

    /** Anel do terreno (0 = praça, 1 = em volta dela...). */
    public static int ring(int cell) {
        return Math.max(Math.abs(cellI(cell)), Math.abs(cellJ(cell)));
    }

    /** Distância (em blocos, num eixo) do centro do terreno de índice i até o coração, contando os vãos das muralhas. */
    public static int axis(int i) {
        int a = Math.abs(i);
        int d = CELL * a + WALL_GAP * Math.max(0, (a - 1) / 2);
        return i < 0 ? -d : d;
    }

    /** Índice do terreno que contém o deslocamento {@code d} (num eixo), ou Integer.MIN_VALUE se cair num vão. */
    public static int indexAt(int d) {
        for (int i = -MAX_RADIUS; i <= MAX_RADIUS; i++) {
            if (Math.abs(d - axis(i)) <= CELL / 2) return i;
        }
        return Integer.MIN_VALUE;
    }

    /** Nível do centro que libera o terreno (só os 5×5 do começo; os de fora dependem da expansão). */
    public static int unlockLevel(int cell) {
        int ai = Math.abs(cellI(cell)), aj = Math.abs(cellJ(cell));
        if (ai > GRID || aj > GRID) return 5;
        if (ai + aj == 1) return 1;
        if (ai == 1 && aj == 1) return 2;
        if (ai == 2 && aj == 2) return 4;
        return 3;
    }

    /** O terreno está dentro da cidade e liberado (raio + nível do centro)? */
    public static boolean unlocked(Settlement s, int cell) {
        // lote livre: vale a área da cidade (as regras de onde cabe ficam em CityPlots)
        if (isFree(cell)) return Math.max(Math.abs(freeDx(cell)), Math.abs(freeDz(cell))) + 7 <= axis(s.radius) + 7;
        if (!valid(cell) || cell == CENTER_CELL) return false;
        int r = ring(cell);
        if (r > s.radius) return false;
        return r > GRID || unlockLevel(cell) <= s.level;
    }

    /** Todos os terrenos de um raio (sem a praça), anel por anel. */
    public static List<Integer> cellsWithin(int radius) {
        List<Integer> out = new ArrayList<>();
        for (int r = 1; r <= radius; r++) {
            for (int i = -r; i <= r; i++) {
                for (int j = -r; j <= r; j++) {
                    if (Math.max(Math.abs(i), Math.abs(j)) == r) out.add(cellIndex(i, j));
                }
            }
        }
        return out;
    }

    /** Terrenos liberados agora, do mais perto da praça para o mais longe. */
    public static List<Integer> cellsFor(Settlement s) {
        List<Integer> out = new ArrayList<>();
        // os 5×5 seguem a ordem antiga (pelo nível que libera)
        for (int lvl = 1; lvl <= 4; lvl++) {
            for (int c = 0; c < CELLS; c++) {
                if (c != CENTER_CELL && unlockLevel(c) == lvl && unlocked(s, c)) out.add(cellAt(s, cellI(c), cellJ(c)));
            }
        }
        // onda 9 (isles): numa cidade de ilhas os mesmos terrenos, com o id de ilha
        for (int c : cellsWithin(s.radius)) if (ring(c) > GRID && unlocked(s, c)) out.add(cellAt(s, cellI(c), cellJ(c)));
        return out;
    }

    public static BlockPos cellCenter(BlockPos heart, int cell) {
        if (isFree(cell)) return heart.offset(freeDx(cell), 0, freeDz(cell));
        if (isIsle(cell)) return heart.offset(ISLE_PITCH * isleI(cell), 0, ISLE_PITCH * isleJ(cell));
        return heart.offset(axis(cellI(cell)), 0, axis(cellJ(cell)));
    }

    /** Rotação para a entrada do prédio (lado +Z) olhar para a praça (lote livre: a rotação escolhida pelo jogador). */
    public static Rotation facingHeart(int cell) {
        if (isFree(cell)) return freeRot(cell);
        int i = cellI(cell), j = cellJ(cell);
        if (Math.abs(i) >= Math.abs(j) && i != 0) {
            return i > 0 ? Rotation.CLOCKWISE_90 : Rotation.COUNTERCLOCKWISE_90;   // olhar para oeste / leste
        }
        return j > 0 ? Rotation.CLOCKWISE_180 : Rotation.NONE;                     // olhar para norte / sul
    }

    // =====================================================================================
    //  Tamanho e muralhas
    // =====================================================================================
    /** Distância da muralha k (1, 2, 3...) ao coração: no vão depois do anel 2k. */
    public static int wallDist(int k) {
        return axis(2 * k) + CELL / 2 + 2;
    }

    /**
     * O deslocamento (dx, dz) a partir do coração cai na faixa reservada de alguma muralha (muro, torres, portões)?
     * A terraplenagem dos lotes nunca mexe aí.
     */
    public static boolean inWallBand(int dx, int dz) {
        int m = Math.max(Math.abs(dx), Math.abs(dz));
        for (int k = 1; k <= wallSlots(MAX_RADIUS); k++) {
            int d = wallDist(k);
            if (m < d - 2) return false;
            if (m <= d + 2) return true;
        }
        return false;
    }

    /** Quantas muralhas cabem num raio (uma a cada 2 anéis, a última pode cercar o anel ímpar de fora). */
    public static int wallSlots(int radius) {
        return Math.max(1, (radius + 1) / 2);
    }

    /** Meia-largura do território de um raio (cobre o último anel e a muralha dele). */
    public static int half(int radius) {
        return Math.max(axis(radius) + CELL / 2 + 6, wallDist(wallSlots(radius)) + 4);
    }

    public static int half(Settlement s) {
        // onda 9 (isles): o arquipélago é mais espaçado (vão para as pontes entre as ilhas)
        return s.isles ? isleHalf(s.radius) : half(s.radius);
    }

    public static BoundingBox territory(BlockPos heart) {
        return territory(heart, START_RADIUS);
    }

    public static BoundingBox territory(BlockPos heart, int radius) {
        int h = half(radius);
        return new BoundingBox(heart.getX() - h, heart.getY() - BOX_DOWN, heart.getZ() - h,
                heart.getX() + h, heart.getY() + BOX_UP, heart.getZ() + h);
    }

    /** Peças da muralha k com os moldes .nbt antigos do estilo (compatibilidade). */
    public static List<Project.Piece> wallPieces(SettlementStyle style, BlockPos heart, int k) {
        return wallPieces(heart, k, piece -> Blueprint.template(style, piece));
    }

    /** Onda 5: peças da muralha k no GRAU de muralha {@code wallTier} (1 paliçada .. 4 real; 0 = moldes antigos). */
    public static List<Project.Piece> wallPieces(SettlementStyle style, BlockPos heart, int k, int wallTier) {
        return wallPieces(heart, k, piece -> TierWalls.any(style, wallTier, piece));
    }

    /** Onda 5: peças de REFORMA da muralha k (moldes de troca: tira o grau/estilo velho, põe o novo). */
    public static List<Project.Piece> wallSwapPieces(SettlementStyle oldStyle, int oldTier, SettlementStyle newStyle, int newTier,
                                                    BlockPos heart, int k) {
        return wallPieces(heart, k, piece -> TierWalls.swapId(oldStyle, oldTier, newStyle, newTier, piece));
    }

    /** Peças da muralha k: 4 torres, os trechos e 4 portões, nesta ordem (as torres primeiro firmam os cantos). */
    private static List<Project.Piece> wallPieces(BlockPos heart, int k, java.util.function.Function<String, net.minecraft.resources.ResourceLocation> tpl) {
        return wallPieces(heart, wallDist(Math.max(1, k)), tpl, true);
    }

    /** Peças de uma muralha a {@code dist} blocos do coração (o último parâmetro só separa esta assinatura da de cima). */
    private static List<Project.Piece> wallPieces(BlockPos heart, int dist, java.util.function.Function<String, net.minecraft.resources.ResourceLocation> tpl,
                                                 boolean byDist) {
        List<Project.Piece> out = new ArrayList<>();
        int hx = heart.getX(), hz = heart.getZ();
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                out.add(new Project.Piece(tpl.apply("wall_tower"), hx + sx * dist, hz + sz * dist,
                        Rotation.NONE, Project.Piece.TOWER, Integer.MIN_VALUE));
            }
        }
        // trechos de 9 blocos entre o portão (±3) e a torre (±dist-2): centros a 8, 17, 26... e um último encostado na torre
        List<Integer> along = new ArrayList<>();
        int a = 8;
        for (; a <= dist - 7; a += 9) along.add(a);
        if (along.isEmpty() || along.get(along.size() - 1) < dist - 7) along.add(dist - 7);
        for (int side = 0; side < 4; side++) {
            for (int s = -1; s <= 1; s += 2) {
                for (int v : along) out.add(sidePiece(tpl.apply("wall_segment"), heart, dist, side, s * v, Project.Piece.WALL));
            }
        }
        for (int side = 0; side < 4; side++) out.add(sidePiece(tpl.apply("wall_gate"), heart, dist, side, 0, Project.Piece.GATE));
        return out;
    }

    /** Lados: 0 norte, 1 sul (peças ao longo de X), 2 oeste, 3 leste (giradas 90°, ao longo de Z). */
    private static Project.Piece sidePiece(net.minecraft.resources.ResourceLocation tpl, BlockPos heart, int dist, int side, int along, int kind) {
        int hx = heart.getX(), hz = heart.getZ();
        return switch (side) {
            case 0 -> new Project.Piece(tpl, hx + along, hz - dist, Rotation.NONE, kind, Integer.MIN_VALUE);
            case 1 -> new Project.Piece(tpl, hx + along, hz + dist, Rotation.NONE, kind, Integer.MIN_VALUE);
            case 2 -> new Project.Piece(tpl, hx - dist, hz + along, Rotation.CLOCKWISE_90, kind, Integer.MIN_VALUE);
            default -> new Project.Piece(tpl, hx + dist, hz + along, Rotation.CLOCKWISE_90, kind, Integer.MIN_VALUE);
        };
    }

    /** Ponto de chegada dos moradores novos: na frente de um portão da muralha de fora (ou na borda da praça, sem muralha). */
    public static BlockPos arrivalPoint(Settlement s, int which) {
        int outer = s.outerWall();
        // onda 9 (isles): no céu as ilhotas da muralha não têm ponte — quem chega aparece na ilha do meio
        int d = outer > 0 && !(s.sky && s.isles) ? wallDist(s, outer) - 4 : 9;
        BlockPos heart = s.heart;
        BlockPos at = switch (Math.floorMod(which, 4)) {
            case 0 -> heart.offset(0, 0, -d);
            case 1 -> heart.offset(0, 0, d);
            case 2 -> heart.offset(-d, 0, 0);
            default -> heart.offset(d, 0, 0);
        };
        // onda 7 (sky_access): numa Ilha Celeste a chegada é sempre em terra que já existe (cidade no chão: o ponto de sempre)
        if (s.sky) return com.renanpontes.isekaimahou.feature.settlements.SkyAccess.onLand(s, at);
        // onda 8 (water_city): na Cidade das Águas a chegada é sempre em piso firme (passarela, deque ou margem)
        return s.water ? com.renanpontes.isekaimahou.feature.settlements.WaterCity.arrival(s, at) : at;
    }

    /** Projeto de muralha: o anel vai no "cell" negativo (-100 - k). Muralhas antigas (cell -1) são a de número 1. */
    public static int wallCell(int k) {
        return -100 - k;
    }

    public static int wallOf(Project p) {
        return p.cell <= -101 ? -100 - p.cell : 1;
    }

    // =====================================================================================
    //  Onda 9 (isles): cidade de ILHAS (céu e água)
    // =====================================================================================
    /*
     * Pedido do dono (2026-11-13): "a cidade voadora... ir criando ilhas com pontes... assim como a de água, não ser que
     * nem o tradicional... tem que ir expandindo conforme a necessidade dele". Numa cidade de ilhas (s.isles) os
     * terrenos continuam sendo a MESMA planta (anéis, níveis que liberam, expansão, painel), mas cada terreno fica a
     * ISLE_PITCH blocos do vizinho — sobra um vão de céu/água entre um lote e outro, que vira ponte. O id do terreno
     * guarda a marca ISLE e o (i, j): cellCenter, facingHeart e todo o resto funcionam igual, e os mapas da cidade
     * (prédios, níveis, alturas) usam o id como sempre. A praça continua sendo CENTER_CELL.
     */
    /** Distância entre os centros de dois terrenos vizinhos numa cidade de ilhas (lote de 17 + vão da ponte). */
    public static final int ISLE_PITCH = 30;
    /** Marca do id de terreno de cidade de ilhas (acima de todo id da grade e dos lotes livres). */
    public static final int ISLE = 1 << 26;
    private static final int ISLE_BIAS = 64;

    public static boolean isIsle(int cell) {
        return cell >= ISLE && cell < ISLE << 1;
    }

    /** Id do terreno (i, j) de uma cidade de ilhas (o do meio é a praça). */
    public static int isleCell(int i, int j) {
        if (i == 0 && j == 0) return CENTER_CELL;
        return ISLE | (i + ISLE_BIAS) << 8 | (j + ISLE_BIAS);
    }

    private static int isleI(int cell) {
        return (cell >> 8 & 0xFF) - ISLE_BIAS;
    }

    private static int isleJ(int cell) {
        return (cell & 0xFF) - ISLE_BIAS;
    }

    /** Id do terreno (i, j) desta cidade: ilha numa cidade de ilhas, grade nas outras. */
    public static int cellAt(Settlement s, int i, int j) {
        return s.isles ? isleCell(i, j) : cellIndex(i, j);
    }

    /** O mesmo tipo de id de {@code like} (ilha ou grade) para (i, j). */
    public static int sameKind(int like, int i, int j) {
        return isIsle(like) ? isleCell(i, j) : cellIndex(i, j);
    }

    /** Distância (num eixo) do centro do terreno de índice i até o coração, nesta cidade. */
    public static int axis(Settlement s, int i) {
        return s.isles ? ISLE_PITCH * i : axis(i);
    }

    /** Índice do terreno que contém o deslocamento {@code d} nesta cidade, ou Integer.MIN_VALUE (vão, fora). */
    public static int indexAt(Settlement s, int d) {
        if (!s.isles) return indexAt(d);
        int i = Math.round(d / (float) ISLE_PITCH);
        return Math.abs(i) <= MAX_RADIUS && Math.abs(d - ISLE_PITCH * i) <= CELL / 2 ? i : Integer.MIN_VALUE;
    }

    /** O terreno (grade ou ilha, sem os lotes livres) que contém o deslocamento (dx, dz), ou -1 (praça, vão, fora do raio). */
    public static int cellAtOffset(Settlement s, int dx, int dz) {
        int i = indexAt(s, dx), j = indexAt(s, dz);
        if (i == Integer.MIN_VALUE || j == Integer.MIN_VALUE || Math.max(Math.abs(i), Math.abs(j)) > s.radius) return -1;
        int cell = cellAt(s, i, j);
        return cell == CENTER_CELL ? -1 : cell;
    }

    /** Muralha k de uma cidade de ilhas: no meio do vão entre o anel 2k e o 2k+1. */
    public static int isleWallDist(int k) {
        return ISLE_PITCH * 2 * Math.max(1, k) + ISLE_PITCH / 2;
    }

    public static int wallDist(Settlement s, int k) {
        return s.isles ? isleWallDist(k) : wallDist(k);
    }

    /** Meia-largura do território de uma cidade de ilhas (último anel com a ilha inteira + a muralha dele). */
    public static int isleHalf(int radius) {
        // onda 9 (fundar mais fácil): só a muralha que cerca um anel PAR que já existe conta (isleWallSlots) — a caixa
        // não pula 30 blocos a mais nos raios ímpares, e cidades vizinhas a 220 blocos crescem mais antes de se encostar
        return Math.max(ISLE_PITCH * radius + CELL / 2 + 8, isleWallDist(isleWallSlots(radius)) + 4);
    }

    /** Muralhas possíveis num arquipélago de raio {@code radius}: uma em volta de cada anel par que já existe. */
    public static int isleWallSlots(int radius) {
        return Math.max(1, radius / 2);
    }

    /** Quantas muralhas cabem nesta cidade agora. */
    public static int wallSlots(Settlement s) {
        return s.isles ? isleWallSlots(s.radius) : wallSlots(s.radius);
    }

    public static BoundingBox territory(Settlement s) {
        return territory(s, s.radius);
    }

    public static BoundingBox territory(Settlement s, int radius) {
        if (!s.isles) return territory(s.heart, radius);
        int h = isleHalf(radius);
        BlockPos heart = s.heart;
        return new BoundingBox(heart.getX() - h, heart.getY() - BOX_DOWN, heart.getZ() - h,
                heart.getX() + h, heart.getY() + BOX_UP, heart.getZ() + h);
    }

    /** Como {@link #inWallBand(int, int)}, nesta cidade (numa cidade de ilhas as faixas ficam nos vãos entre os anéis). */
    public static boolean inWallBand(Settlement s, int dx, int dz) {
        if (!s.isles) return inWallBand(dx, dz);
        int m = Math.max(Math.abs(dx), Math.abs(dz));
        for (int k = 1; k <= wallSlots(MAX_RADIUS); k++) {
            int d = isleWallDist(k);
            if (m < d - 3) return false;
            if (m <= d + 3) return true;
        }
        return false;
    }

    /**
     * Peças da muralha k desta cidade no grau {@code wallTier}. Cidade de ilhas: no vão entre os anéis 2k e 2k+1; no
     * CÉU só as 4 torres e os 4 portões (cada um ganha a sua ilhota — um muro inteiro boiando no vazio não faria
     * sentido); na água o quebra-mar inteiro, sobre estacas.
     */
    public static List<Project.Piece> wallPieces(Settlement s, int k, int wallTier) {
        if (!s.isles) return wallPieces(s.style, s.heart, k, wallTier);
        return islePieces(s, wallPieces(s.heart, isleWallDist(k), piece -> TierWalls.any(s.style, wallTier, piece), true));
    }

    /** Como {@link #wallSwapPieces(SettlementStyle, int, SettlementStyle, int, BlockPos, int)}, nesta cidade. */
    public static List<Project.Piece> wallSwapPieces(Settlement s, SettlementStyle oldStyle, int oldTier, SettlementStyle newStyle, int newTier, int k) {
        if (!s.isles) return wallSwapPieces(oldStyle, oldTier, newStyle, newTier, s.heart, k);
        return islePieces(s, wallPieces(s.heart, isleWallDist(k), piece -> TierWalls.swapId(oldStyle, oldTier, newStyle, newTier, piece), true));
    }

    private static List<Project.Piece> islePieces(Settlement s, List<Project.Piece> all) {
        if (!s.sky) return all;
        List<Project.Piece> out = new ArrayList<>();
        for (Project.Piece p : all) if (p.kind != Project.Piece.WALL) out.add(p);
        return out;
    }


    private CityLayout() {}
}
