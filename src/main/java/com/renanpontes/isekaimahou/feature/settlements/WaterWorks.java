package com.renanpontes.isekaimahou.feature.settlements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.renanpontes.isekaimahou.feature.settlements.build.CityLayout;
import com.renanpontes.isekaimahou.feature.settlements.build.CityTerrain;
import com.renanpontes.isekaimahou.feature.settlements.build.ConstructionEngine;
import com.renanpontes.isekaimahou.feature.settlements.build.Project;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Onda 8 (water_city) — <b>passarelas, pontes, cais e enfeites</b> da Cidade das Águas. Os lotes ganham o deque deles
 * com a obra do prédio ({@link WaterCity#deckColumn}); aqui fica o que LIGA tudo, decidido só pelo estado da cidade
 * (a "planta" é refeita quando um lote ganha ou perde plataforma, quando a cidade cresce, muda de degrau ou de estilo):
 * <ul>
 *   <li><b>Passarela até o vizinho de dentro</b>: todo lote com plataforma se liga ao lote entre ele e a praça; se esse
 *       vizinho ainda é água, uma passarela de 3 blocos atravessa o meio dele (some debaixo do deque quando o lote for
 *       construído) e a corrente continua até achar piso.</li>
 *   <li><b>Canal principal e pontes baixas</b>: o vão de cada muralha tem, do lado de fora dela, um canal de 3 blocos
 *       que NUNCA recebe piso — dá a volta na cidade inteira de barco. Só se cruza o canal nos 4 eixos (onde ficam os
 *       portões), por uma ponte em arco com vão livre para o barco passar; quem mora depois do canal chega ao eixo por
 *       um cais de 2 blocos na margem de fora.</li>
 *   <li><b>Cais</b>: na frente da praça, depois do último canal — um píer com 2–3 barcos (repostos devagar), barris e
 *       lanternas. Acompanha a borda quando a cidade cresce.</li>
 *   <li><b>Enfeites</b>: lanternas na beira das passarelas, vitórias-régias nos cantos calmos, e por estilo um torii
 *       na água (Oriental) ou um drakkar ancorado (Nórdico). Tudo anotado na cidade para refazer sem deixar lixo.</li>
 * </ul>
 * Tudo aos poucos, com o orçamento de blocos das obras ({@link ConstructionEngine#claim}), só em chunk carregado, só
 * em ar/água (ou trocando piso nosso de outro degrau/estilo). O piso é reconhecido pelo bloco e pela planta, por isso
 * não precisa ser salvo; só os enfeites são ({@code Decor}).
 */
public final class WaterWorks {
    /**
     * Colunas conferidas e blocos mudados por tick. Poucos blocos de propósito: a passarela avança umas duas fileiras
     * por tick — dá para VER ela crescendo sobre a água; conferir o que já está pronto (quase tudo) é rápido.
     */
    private static final int COLUMNS_PER_TICK = 40, BLOCKS_PER_TICK = 6;
    private static final int DECOR_PER_TICK = 12;
    /** Espera antes de voltar às colunas que ficaram em chunk descarregado, e entre uma conferência completa e outra. */
    private static final int RETRY_WAIT = 200, VERIFY_EVERY = 6000;
    /** Tempo mínimo (ticks) entre um barco reposto e o próximo. */
    private static final int BOAT_WAIT = 2400;
    public static final String BOAT_TAG = "isekai_mahou.water_boat";
    private static final int WALK = 0, BRIDGE = 1;

    /** A planta de uma cidade: retângulos de piso, enfeites e o andamento — só em memória (recalcular é inofensivo). */
    private static final class Plan {
        long sig;
        /** {x0, z0, x1, z1, tipo, direção (só ponte: para onde ela se afasta da praça)}. */
        final List<int[]> rects = new ArrayList<>();
        final Set<String> gaps = new HashSet<>();
        final Set<Integer> walked = new HashSet<>();
        final LinkedHashMap<Long, BlockState> decor = new LinkedHashMap<>();
        final List<BlockPos> moorings = new ArrayList<>();
        int rect, col, wait, decorIdx;
        boolean laid, decorated, skipped;
        long verifyAt;
    }

    private static final Map<String, Plan> PLANS = new HashMap<>();

    public static void clear() {
        PLANS.clear();
    }

    // =====================================================================================
    //  Planta
    // =====================================================================================
    /** O lote tem (ou vai ter já) uma plataforma: praça, prédio, lote aplainado ou obra na fila. */
    private static boolean platform(Settlement s, int cell) {
        return cell == CityLayout.CENTER_CELL || s.buildings.containsKey(cell) || s.plotBase.containsKey(cell) || s.projectAt(cell) != null;
    }

    private static Set<Integer> platforms(Settlement s) {
        Set<Integer> out = new java.util.TreeSet<>();
        for (int c : s.buildings.keySet()) if (c >= 0 && c != CityLayout.CENTER_CELL) out.add(c);
        for (int c : s.plotBase.keySet()) if (c >= 0 && c != CityLayout.CENTER_CELL) out.add(c);
        for (Project p : s.projects) if (p.cell >= 0 && p.cell != CityLayout.CENTER_CELL) out.add(p.cell);
        return out;
    }

    /** Muda quando a planta precisa ser refeita. */
    private static long signature(Settlement s) {
        long h = s.radius * 31L + s.style.ordinal() * 131L + s.level * 1009L + s.plazaRot * 7919L;
        for (int c : platforms(s)) h += (c + 17L) * 0x9E3779B97F4A7C15L;
        for (int k = 1; k <= CityLayout.wallSlots(s.radius); k++) if (walled(s, k)) h += k * 0x632BE59BD9B4E019L;
        return h;
    }

    /** A muralha k existe ou está em obra? (o portão dela tem piso próprio: a passarela não passa por dentro dele) */
    private static boolean walled(Settlement s, int k) {
        if (s.walls.contains(k)) return true;
        for (Project q : s.projects) {
            if (q.cell < 0 && com.renanpontes.isekaimahou.feature.settlements.build.Blueprint.WALL.equals(q.key) && CityLayout.wallOf(q) == k) return true;
        }
        return false;
    }

    private static void rect(Plan p, int x0, int z0, int x1, int z1, int kind, Direction dir) {
        p.rects.add(new int[] {Math.min(x0, x1), Math.min(z0, z1), Math.max(x0, x1), Math.max(z0, z1), kind, dir.ordinal()});
    }

    /** Retângulo dado no eixo de um LADO da cidade: {@code a} = distância do coração para fora, {@code l} = para o lado. */
    private static void sideRect(Plan p, Settlement s, Direction side, int a0, int a1, int l0, int l1, int kind) {
        int hx = s.heart.getX(), hz = s.heart.getZ(), sx = side.getStepX(), sz = side.getStepZ();
        if (sx != 0) rect(p, hx + sx * a0, hz + l0, hx + sx * a1, hz + l1, kind, side);
        else rect(p, hx + l0, hz + sz * a0, hx + l1, hz + sz * a1, kind, side);
    }

    private static BlockPos sidePos(Settlement s, Direction side, int a, int l, int y) {
        int sx = side.getStepX(), sz = side.getStepZ();
        return sx != 0 ? new BlockPos(s.heart.getX() + sx * a, y, s.heart.getZ() + l) : new BlockPos(s.heart.getX() + l, y, s.heart.getZ() + sz * a);
    }

    private static int cellOn(Direction side, int index) {
        return CityLayout.cellIndex(side.getStepX() * index, side.getStepZ() * index);
    }

    /** Travessia do vão da muralha k num lado: patamar (onde fica o portão) e a ponte em arco sobre o canal. */
    private static void gap(Plan p, Settlement s, Direction side, int k) {
        if (!p.gaps.add(side.ordinal() + ":" + k)) return;
        int d = CityLayout.wallDist(k);
        // sem muralha, o patamar atravessa a faixa dela; com muralha, o portão já é o piso (só a soleira de dentro é nossa)
        sideRect(p, s, side, d - 2, walled(s, k) ? d - 2 : d + 1, -1, 1, WALK);
        sideRect(p, s, side, d + 2, d + 6, -1, 1, BRIDGE);
    }

    /**
     * Passarela de 3 blocos dentro de um lote que ainda é água: do MEIO dele até a borda do lado do vizinho de dentro
     * (a frente do lote, na direção da praça).
     */
    private static void through(Plan p, Settlement s, int cell) {
        if (!p.walked.add(cell)) return;
        int n = CityTerrain.inward(cell);
        int sx = Integer.signum(CityLayout.cellI(n) - CityLayout.cellI(cell)), sz = Integer.signum(CityLayout.cellJ(n) - CityLayout.cellJ(cell));
        half(p, s, cell, sx, sz);
    }

    /** Passarela do meio do lote até a borda dele no lado (sx, sz) — é por onde chega quem vem do lote vizinho daquele lado. */
    private static void enter(Plan p, Settlement s, int cell, int sx, int sz) {
        if (p.gaps.add("in:" + cell + ":" + sx + ":" + sz)) half(p, s, cell, sx, sz);
    }

    private static void half(Plan p, Settlement s, int cell, int sx, int sz) {
        BlockPos c = CityLayout.cellCenter(s.heart, cell);
        if (sx != 0) rect(p, c.getX(), c.getZ() - 1, c.getX() + 8 * sx, c.getZ() + 1, WALK, Direction.EAST);
        else if (sz != 0) rect(p, c.getX() - 1, c.getZ(), c.getX() + 1, c.getZ() + 8 * sz, WALK, Direction.SOUTH);
    }

    /** Liga o lote à praça, de vizinho em vizinho (para quando encontra uma plataforma que já foi ligada). */
    private static void chain(Plan p, Settlement s, int start, Set<Integer> seen) {
        int c = start;
        int hx = s.heart.getX(), hz = s.heart.getZ();
        for (int guard = 0; guard < 64 && c != CityLayout.CENTER_CELL; guard++) {
            if (!seen.add(c)) return;
            int i = CityLayout.cellI(c), j = CityLayout.cellJ(c);
            if (CityLayout.isFree(c)) {
                // lote livre: um "L" de passarela até o terreno da grade mais perto, e segue por ele
                int fx = CityLayout.freeDx(c), fz = CityLayout.freeDz(c), gx = CityLayout.axis(i), gz = CityLayout.axis(j);
                rect(p, hx + fx, hz + fz - 1, hx + gx, hz + fz + 1, WALK, Direction.EAST);
                rect(p, hx + gx - 1, hz + fz, hx + gx + 1, hz + gz, WALK, Direction.SOUTH);
                c = CityLayout.cellIndex(i, j);
                if (c != CityLayout.CENTER_CELL && !platform(s, c)) through(p, s, c);
                continue;
            }
            int n = CityTerrain.inward(c);
            int ni = CityLayout.cellI(n), nj = CityLayout.cellJ(n);
            boolean alongX = ni != i;
            int ca = alongX ? CityLayout.axis(i) : CityLayout.axis(j), na = alongX ? CityLayout.axis(ni) : CityLayout.axis(nj);
            int lat = alongX ? CityLayout.axis(j) : CityLayout.axis(i);
            if (Math.abs(ca - na) > CityLayout.CELL) {
                // há um vão de muralha entre este lote e o vizinho de dentro
                int a = Math.abs(alongX ? i : j), k = Math.max(1, (a - 1) / 2), d = CityLayout.wallDist(k);
                Direction side = alongX ? (i > 0 ? Direction.EAST : Direction.WEST) : (j > 0 ? Direction.SOUTH : Direction.NORTH);
                if (Math.abs(lat) > d + 2) {
                    // canto, fora do quadrado da muralha: não há muro ali — a passarela atravessa o vão direto
                    sideRect(p, s, side, Math.abs(na) + 8, Math.abs(ca) - 8, lat - 1, lat + 1, WALK);
                } else {
                    // cais do canal (2 blocos, na margem de fora) até o eixo; ali, a ponte e o patamar do portão
                    sideRect(p, s, side, d + 6, d + 7, Math.min(lat, 0) - 1, Math.max(lat, 0) + 1, WALK);
                    gap(p, s, side, k);
                    n = cellOn(side, a - 1);
                    ni = CityLayout.cellI(n);
                    nj = CityLayout.cellJ(n);
                    // dali em diante quem chega vem de fora, pelo eixo
                    i = ni + side.getStepX();
                    j = nj + side.getStepZ();
                }
            }
            if (n == CityLayout.CENTER_CELL) return;
            if (!platform(s, n)) {
                // o vizinho ainda é água: passarela da borda por onde se chega até o meio dele, e do meio até a frente dele
                enter(p, s, n, Integer.signum(i - ni), Integer.signum(j - nj));
                through(p, s, n);
            }
            c = n;
        }
    }

    private static Plan plan(Settlement s) {
        // onda 9 (isles): cidade das águas nova = arquipélago de plataformas ligadas por pontes
        if (s.isles) return islePlan(s);
        Plan p = new Plan();
        Set<Integer> seen = new HashSet<>();
        Direction front = WaterCity.front(s);
        int outer = CityLayout.wallSlots(s.radius);
        // 1) pontes: nos vãos de dentro, os 4 lados; no vão de fora, só o lado do cais — e o caminho delas até a praça
        for (int k = 1; k <= outer; k++) {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                if (k == outer && side != front) continue;
                gap(p, s, side, k);
                int in = cellOn(side, 2 * k);
                if (!platform(s, in)) {
                    enter(p, s, in, side.getStepX(), side.getStepZ());
                    through(p, s, in);
                }
                chain(p, s, in, seen);
            }
        }
        boolean pier = p.gaps.contains(front.ordinal() + ":" + outer);
        if (pier) pier(p, s, front, CityLayout.wallDist(outer));
        // 2) cada lote com plataforma se liga ao vizinho de dentro
        for (int cell : platforms(s)) chain(p, s, cell, seen);
        lamps(p, s);
        if (pier) extras(p, s, front, CityLayout.wallDist(outer));
        return p;
    }

    // =====================================================================================
    //  Onda 9 (isles): arquipélago sobre a água
    // =====================================================================================
    /*
     * Pedido do dono: "...ir criando ilhas com pontes... assim como a de água, não ser que nem o tradicional". Numa
     * cidade das águas de ilhas (s.isles) os lotes ficam espaçados (CityLayout.ISLE_PITCH): cada obra ergue a SUA
     * plataforma (o deque do lote, como sempre) e entre uma e outra fica água aberta, de barco. Aqui só as PONTES:
     * cada plataforma se liga à do terreno de dentro (CityTerrain.inward), com uma ponte em arco no meio do vão (o barco
     * passa por baixo); se o terreno de dentro ainda é água, um flutuante 5×5 marca a passagem e a ponte continua dali
     * até a praça. Tudo nasce com a obra que precisou dele — nada de grade pronta. O cais dos barcos fica no porto de
     * dentro, num canto da praça, e não muda de lugar quando a cidade cresce.
     */
    private static final int ISLE_DECK = CityTerrain.LEVEL_HALF + 1;
    private static final int PONTOON = 2;

    private static Plan islePlan(Settlement s) {
        Plan p = new Plan();
        Set<Integer> linked = new HashSet<>();
        for (int cell : platforms(s)) {
            if (!CityLayout.isIsle(cell)) continue;
            int c = cell;
            for (int guard = 0; guard < 2 * CityLayout.MAX_RADIUS + 2 && c != CityLayout.CENTER_CELL; guard++) {
                if (!linked.add(c)) break;
                int n = CityTerrain.inward(c);
                isleSpan(p, s, c, n);
                if (n == CityLayout.CENTER_CELL || platform(s, n)) break;
                // o terreno de dentro ainda é água: flutuante de passagem, e a ponte continua dele
                BlockPos nc = CityLayout.cellCenter(s.heart, n);
                rect(p, nc.getX() - PONTOON, nc.getZ() - PONTOON, nc.getX() + PONTOON, nc.getZ() + PONTOON, WALK, Direction.NORTH);
                c = n;
            }
        }
        Direction front = WaterCity.front(s);
        islePier(p, s, front);
        isleExtras(p, s, front);
        return p;
    }

    /** Ponte do lote {@code c} até o terreno de dentro {@code n}: tábua nas pontas e o arco no meio do vão. */
    private static void isleSpan(Plan p, Settlement s, int c, int n) {
        BlockPos cc = CityLayout.cellCenter(s.heart, c);
        int ax = Integer.signum(CityLayout.cellI(n) - CityLayout.cellI(c)), az = Integer.signum(CityLayout.cellJ(n) - CityLayout.cellJ(c));
        if (ax == 0 && az == 0) return;
        int t0 = platform(s, c) ? ISLE_DECK : PONTOON + 1;
        int t1 = CityLayout.ISLE_PITCH - (n == CityLayout.CENTER_CELL || platform(s, n) ? ISLE_DECK : PONTOON + 1);
        if (t1 < t0) return;
        // a ponte se afasta da praça no sentido contrário ao do vizinho de dentro
        Direction out = Direction.fromDelta(-ax, 0, -az);
        if (out == null) out = Direction.NORTH;
        // vão com muralha (quebra-mar): o portão é a passagem — só tábua reta, sem arco
        boolean walled = false;
        for (int t = t0; t <= t1 && !walled; t++) walled = CityLayout.inWallBand(s, cc.getX() + ax * t - s.heart.getX(), cc.getZ() + az * t - s.heart.getZ());
        int mid = (t0 + t1) / 2;
        if (walled || t1 - t0 + 1 < 9) {
            span(p, cc, ax, az, t0, t1, WALK, out);
        } else {
            span(p, cc, ax, az, t0, mid - 3, WALK, out);
            span(p, cc, ax, az, mid - 2, mid + 2, BRIDGE, out);
            span(p, cc, ax, az, mid + 3, t1, WALK, out);
        }
        // um poste do degrau na ponta da ponte que encosta no lote de fora (com o pé de tábua dele)
        int px = -az, pz = ax;
        int lx = cc.getX() + ax * t0 + px * 2, lz = cc.getZ() + az * t0 + pz * 2;
        if (!walled) {
            p.rects.add(new int[] {lx, lz, lx, lz, WALK, 0});
            BlockState[] lamp = com.renanpontes.isekaimahou.feature.settlements.build.TierLook.lamp(s.style, s.level);
            for (int y = 0; y < lamp.length; y++) decor(p, new BlockPos(lx, WaterCity.deckY(s) + 1 + y, lz), lamp[y]);
        }
    }

    /** Retângulo de 3 de largura ao longo de (ax, az) a partir de {@code c}, de {@code t0} a {@code t1} blocos. */
    private static void span(Plan p, BlockPos c, int ax, int az, int t0, int t1, int kind, Direction out) {
        if (t1 < t0) return;
        int px = -az, pz = ax;
        int x0 = c.getX() + ax * t0 - px, z0 = c.getZ() + az * t0 - pz;
        int x1 = c.getX() + ax * t1 + px, z1 = c.getZ() + az * t1 + pz;
        rect(p, x0, z0, x1, z1, kind, out);
    }

    /** Cais do porto de dentro: haste saindo do canto da praça (de lado, longe da ponte da frente) e a cabeça em "T". */
    private static void islePier(Plan p, Settlement s, Direction side) {
        sideRect(p, s, side, 9, 13, 6, 8, WALK);
        sideRect(p, s, side, 14, 16, 5, 13, WALK);
        int deck = WaterCity.deckY(s);
        WaterCity.Pal pal = WaterCity.pal(s.style);
        for (int l : new int[] {5, 13}) {
            decor(p, sidePos(s, side, 16, l, deck + 1), pal.post());
            decor(p, sidePos(s, side, 16, l, deck + 2), pal.lamp());
        }
        BlockState barrel = Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP);
        decor(p, sidePos(s, side, 14, 12, deck + 1), barrel);
        decor(p, sidePos(s, side, 14, 12, deck + 2), barrel);
        decor(p, sidePos(s, side, 15, 12, deck + 1), barrel.setValue(BarrelBlock.FACING, side.getOpposite()));
        decor(p, sidePos(s, side, 14, 6, deck + 1), Blocks.COMPOSTER.defaultBlockState());
        decor(p, sidePos(s, side, 15, 6, deck + 1), Blocks.HAY_BLOCK.defaultBlockState());
        p.moorings.add(sidePos(s, side, 11, 10, deck));
        p.moorings.add(sidePos(s, side, 13, 10, deck));
        p.moorings.add(sidePos(s, side, 18, 9, deck));
    }

    /** Enfeites do porto de dentro (nas águas entre a praça e os lotes da diagonal): chafariz, torii ou drakkar. */
    private static void isleExtras(Plan p, Settlement s, Direction side) {
        int deck = WaterCity.deckY(s), sea = WaterCity.seaY(s);
        for (int y = sea - 2; y <= deck + 1; y++) decor(p, sidePos(s, side, 19, 19, y), Blocks.PRISMARINE_BRICKS.defaultBlockState());
        decor(p, sidePos(s, side, 19, 19, deck + 2), Blocks.SEA_LANTERN.defaultBlockState());
        decor(p, sidePos(s, side, 19, 19, deck + 3), Blocks.WATER.defaultBlockState());
        if (s.style == SettlementStyle.ORIENTAL) {
            // torii na água do outro canto: os barcos passam por baixo
            int a = 16;
            BlockState pillar = Blocks.STRIPPED_MANGROVE_LOG.defaultBlockState();
            for (int l : new int[] {-19, -13}) {
                for (int y = sea - 2; y <= deck + 5; y++) decor(p, sidePos(s, side, a, l, y), pillar);
            }
            for (int l = -20; l <= -12; l++) if (l != -19 && l != -13) decor(p, sidePos(s, side, a, l, deck + 4), Blocks.MANGROVE_PLANKS.defaultBlockState());
            for (int l = -21; l <= -11; l++) decor(p, sidePos(s, side, a, l, deck + 6), Blocks.DARK_OAK_PLANKS.defaultBlockState());
            for (int l : new int[] {-22, -10}) decor(p, sidePos(s, side, a, l, deck + 7), Blocks.DARK_OAK_SLAB.defaultBlockState());
        } else if (s.style == SettlementStyle.NORDIC) {
            // drakkar ancorado no outro canto do porto
            int l0 = -16;
            BlockState hull = Blocks.SPRUCE_PLANKS.defaultBlockState(), rim = Blocks.DARK_OAK_PLANKS.defaultBlockState();
            BlockState fence = Blocks.DARK_OAK_FENCE.defaultBlockState();
            for (int t = 0; t <= 8; t++) {
                int a = 11 + t;
                boolean end = t == 0 || t == 8;
                for (int w = -1; w <= 1; w++) {
                    if (end && w != 0) continue;
                    decor(p, sidePos(s, side, a, l0 + w, sea), end ? rim : hull);
                    if (end || w != 0) decor(p, sidePos(s, side, a, l0 + w, deck), rim);
                }
                if (end) {
                    decor(p, sidePos(s, side, a, l0, deck + 1), rim);
                    decor(p, sidePos(s, side, a, l0, deck + 2), fence);
                }
            }
            int mast = 15;
            for (int y = deck; y <= deck + 6; y++) decor(p, sidePos(s, side, mast, l0, y), Blocks.SPRUCE_FENCE.defaultBlockState());
            for (int y = deck + 3; y <= deck + 5; y++) {
                for (int w = -2; w <= 2; w++) {
                    if (w == 0) continue;
                    decor(p, sidePos(s, side, mast, l0 + w, y), (Math.abs(w) == 1 ? Blocks.RED_WOOL : Blocks.WHITE_WOOL).defaultBlockState());
                }
            }
        }
    }

    // ---- cais
    private static void pier(Plan p, Settlement s, Direction side, int d) {
        // haste de 3 blocos e a cabeça em "T"
        sideRect(p, s, side, d + 7, d + 16, -1, 1, WALK);
        sideRect(p, s, side, d + 14, d + 16, -4, 4, WALK);
        int deck = WaterCity.deckY(s);
        WaterCity.Pal pal = WaterCity.pal(s.style);
        // lanternas nas duas pontas da cabeça
        for (int l : new int[] {-4, 4}) {
            decor(p, sidePos(s, side, d + 16, l, deck + 1), pal.post());
            decor(p, sidePos(s, side, d + 16, l, deck + 2), pal.lamp());
        }
        // carga do cais: barris e um caixote
        BlockState barrel = Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP);
        decor(p, sidePos(s, side, d + 14, -4, deck + 1), barrel);
        decor(p, sidePos(s, side, d + 14, -4, deck + 2), barrel);
        decor(p, sidePos(s, side, d + 14, -3, deck + 1), barrel.setValue(BarrelBlock.FACING, side.getOpposite()));
        decor(p, sidePos(s, side, d + 14, 4, deck + 1), Blocks.COMPOSTER.defaultBlockState());
        decor(p, sidePos(s, side, d + 15, 4, deck + 1), Blocks.HAY_BLOCK.defaultBlockState());
        // vagas dos barcos, encostadas na haste
        p.moorings.add(sidePos(s, side, d + 9, 3, deck));
        p.moorings.add(sidePos(s, side, d + 12, -3, deck));
        p.moorings.add(sidePos(s, side, d + 10, -3, deck));
    }

    // ---- enfeites
    private static void decor(Plan p, BlockPos pos, BlockState st) {
        p.decor.put(pos.asLong(), st);
    }

    /** A coluna cai num lote com plataforma (ou na praça)? Ali quem enfeita é a obra do lote. */
    private static boolean onPlatform(Settlement s, int x, int z) {
        int dx = x - s.heart.getX(), dz = z - s.heart.getZ();
        int i = CityLayout.indexAt(dx), j = CityLayout.indexAt(dz);
        if (i == Integer.MIN_VALUE || j == Integer.MIN_VALUE) return false;
        if (Math.max(Math.abs(i), Math.abs(j)) > CityLayout.MAX_RADIUS) return false;
        return platform(s, CityLayout.cellIndex(i, j));
    }

    /** Lanternas na beira das passarelas compridas (uma a cada 8 blocos, alternando o lado) e vitórias-régias ao lado. */
    private static void lamps(Plan p, Settlement s) {
        int deck = WaterCity.deckY(s);
        // o poste é o do DEGRAU da cidade (o mesmo das ruas das outras cidades): muda quando ela sobe de degrau
        BlockState[] lamp = com.renanpontes.isekaimahou.feature.settlements.build.TierLook.lamp(s.style, s.level);
        List<int[]> posts = new ArrayList<>();
        for (int[] r : p.rects) {
            if (r[4] != WALK) continue;
            int w = r[2] - r[0] + 1, d = r[3] - r[1] + 1;
            boolean alongX = w >= d;
            int len = alongX ? w : d, wide = alongX ? d : w;
            if (len < 9 || wide > 3) continue;
            for (int t = 4; t < len - 2; t += 8) {
                // o lado de cada lanterna sai da posição (a mesma cidade fica sempre igual)
                int off = (WaterCity.hash(r[0] + t, r[1] - t) & 1) == 0 ? -1 : wide;
                int x = alongX ? r[0] + t : r[0] + off, z = alongX ? r[1] + off : r[1] + t;
                if (onPlatform(s, x, z) || inCanal(s, x, z) || CityLayout.inWallBand(x - s.heart.getX(), z - s.heart.getZ())) continue;
                // nunca no meio de outra passarela (cruzamentos, a haste do cais)
                if (inRects(p, x, z)) continue;
                posts.add(new int[] {x, z});
                for (int y = 0; y < lamp.length; y++) decor(p, new BlockPos(x, deck + 1 + y, z), lamp[y]);
                // vitória-régia do outro lado, um pouco adiante (canto calmo, fora do caminho dos barcos do canal)
                int lx = alongX ? x + 3 : r[0] + (off < 0 ? wide + 1 : -2), lz = alongX ? r[1] + (off < 0 ? wide + 1 : -2) : z + 3;
                if (WaterCity.hash(lx, lz) % 3 != 0 && !onPlatform(s, lx, lz) && !inCanal(s, lx, lz) && !inRects(p, lx, lz)) {
                    decor(p, new BlockPos(lx, deck, lz), Blocks.LILY_PAD.defaultBlockState());
                }
            }
        }
        // o pé de cada poste é um bloco de passarela (com estaca): se um prédio vier para cá, vira parte do deque dele
        for (int[] q : posts) p.rects.add(new int[] {q[0], q[1], q[0], q[1], WALK, 0});
    }

    private static boolean inRects(Plan p, int x, int z) {
        for (int[] r : p.rects) if (x >= r[0] && x <= r[2] && z >= r[1] && z <= r[3]) return true;
        return false;
    }

    /** A coluna fica no canal de alguma muralha (os 3 blocos de água do lado de fora dela)? */
    private static boolean inCanal(Settlement s, int x, int z) {
        int m = Math.max(Math.abs(x - s.heart.getX()), Math.abs(z - s.heart.getZ()));
        for (int k = 1; k <= CityLayout.wallSlots(CityLayout.MAX_RADIUS); k++) {
            int d = CityLayout.wallDist(k);
            if (m < d + 2) return false;
            if (m <= d + 6) return true;
        }
        return false;
    }

    /** Enfeite do estilo, na água ao lado do cais: torii (Oriental) ou drakkar ancorado (Nórdico). */
    private static void extras(Plan p, Settlement s, Direction side, int d) {
        int deck = WaterCity.deckY(s), sea = WaterCity.seaY(s);
        // chafariz do porto: um pilar no mar aberto, depois da ponta do cais, com a água caindo pelos quatro lados de volta
        // para o mar (longe de qualquer piso: nada é inundado; sai do lugar junto com o cais quando a cidade cresce)
        for (int y = sea - 2; y <= deck + 1; y++) decor(p, sidePos(s, side, d + 20, 0, y), Blocks.PRISMARINE_BRICKS.defaultBlockState());
        decor(p, sidePos(s, side, d + 20, 0, deck + 2), Blocks.SEA_LANTERN.defaultBlockState());
        decor(p, sidePos(s, side, d + 20, 0, deck + 3), Blocks.WATER.defaultBlockState());
        if (s.style == SettlementStyle.ORIENTAL) {
            // torii na água, depois do chafariz: os barcos passam por baixo
            int a = d + 24;
            BlockState pillar = Blocks.STRIPPED_MANGROVE_LOG.defaultBlockState();
            for (int l : new int[] {-3, 3}) {
                for (int y = sea - 2; y <= deck + 5; y++) decor(p, sidePos(s, side, a, l, y), pillar);
            }
            for (int l = -4; l <= 4; l++) if (Math.abs(l) != 3) decor(p, sidePos(s, side, a, l, deck + 4), Blocks.MANGROVE_PLANKS.defaultBlockState());
            for (int l = -5; l <= 5; l++) decor(p, sidePos(s, side, a, l, deck + 6), Blocks.DARK_OAK_PLANKS.defaultBlockState());
            for (int l : new int[] {-6, 6}) decor(p, sidePos(s, side, a, l, deck + 7), Blocks.DARK_OAK_SLAB.defaultBlockState());
        } else if (s.style == SettlementStyle.NORDIC) {
            // drakkar ancorado ao lado do cais: casco de 9 blocos, proa e popa erguidas, mastro e vela listrada
            int l0 = 8;
            BlockState hull = Blocks.SPRUCE_PLANKS.defaultBlockState(), rim = Blocks.DARK_OAK_PLANKS.defaultBlockState();
            BlockState fence = Blocks.DARK_OAK_FENCE.defaultBlockState();
            for (int t = 0; t <= 8; t++) {
                int a = d + 8 + t;
                boolean end = t == 0 || t == 8;
                for (int w = -1; w <= 1; w++) {
                    if (end && w != 0) continue;
                    decor(p, sidePos(s, side, a, l0 + w, sea), end ? rim : hull);
                    if (end || w != 0) decor(p, sidePos(s, side, a, l0 + w, deck), rim);
                }
                if (end) {
                    decor(p, sidePos(s, side, a, l0, deck + 1), rim);
                    decor(p, sidePos(s, side, a, l0, deck + 2), fence);
                }
            }
            int mast = d + 12;
            for (int y = deck; y <= deck + 6; y++) decor(p, sidePos(s, side, mast, l0, y), Blocks.SPRUCE_FENCE.defaultBlockState());
            for (int y = deck + 3; y <= deck + 5; y++) {
                for (int w = -2; w <= 2; w++) {
                    if (w == 0) continue;
                    decor(p, sidePos(s, side, mast, l0 + w, y), (Math.abs(w) == 1 ? Blocks.RED_WOOL : Blocks.WHITE_WOOL).defaultBlockState());
                }
            }
        }
    }

    // =====================================================================================
    //  Tick
    // =====================================================================================
    private static Plan current(Settlement s, boolean check) {
        Plan p = PLANS.get(s.id);
        if (p == null || check) {
            long sig = signature(s);
            if (p == null || p.sig != sig) {
                p = plan(s);
                p.sig = sig;
                PLANS.put(s.id, p);
            }
        }
        return p;
    }

    /** Tick do Coração (só com o chunk dele carregado). Cidade que não é das águas sai na primeira linha. */
    public static void tick(ServerLevel sl, Settlement s, int age) {
        if (!s.water) return;
        Plan p = current(s, age % 40 == 9);
        if (p.wait > 0) {
            p.wait--;
        } else if (!p.laid) {
            int budget = ConstructionEngine.claim(sl, BLOCKS_PER_TICK);
            if (budget > 0) lay(sl, s, p, budget, COLUMNS_PER_TICK);
        } else if (!p.decorated) {
            decorate(sl, s, p, DECOR_PER_TICK);
        } else if (sl.getGameTime() >= p.verifyAt) {
            // de tempos em tempos confere tudo de novo (repõe o que quebrou; quase nada muda)
            restart(p);
        }
        if (age % 600 == 450 && p.laid) boats(sl, s, p, false);
    }

    private static void restart(Plan p) {
        p.rect = p.col = p.decorIdx = 0;
        p.laid = p.decorated = p.skipped = false;
    }

    /** Avança o piso: até {@code blocks} blocos mudados e {@code columns} colunas conferidas. */
    private static int lay(ServerLevel sl, Settlement s, Plan p, int blocks, int columns) {
        int placed = 0, seen = 0;
        while (p.rect < p.rects.size() && placed < blocks && seen < columns) {
            int[] r = p.rects.get(p.rect);
            int w = r[2] - r[0] + 1, total = w * (r[3] - r[1] + 1);
            while (p.col < total && placed < blocks && seen < columns) {
                int x = r[0] + p.col % w, z = r[1] + p.col / w;
                p.col++;
                seen++;
                if (!sl.hasChunk(x >> 4, z >> 4)) {
                    p.skipped = true;
                    continue;
                }
                int n = r[4] == BRIDGE ? bridgeColumn(sl, s, r, x, z) : WaterCity.walkColumn(sl, s, x, z);
                placed += n;
                // som de tábua de vez em quando (baixo), só enquanto a passarela cresce de verdade
                if (n > 0 && blocks < 1000 && sl.random.nextInt(6) == 0) {
                    sl.playSound(null, x + 0.5, WaterCity.deckY(s) + 1, z + 0.5, net.minecraft.sounds.SoundEvents.WOOD_PLACE,
                            net.minecraft.sounds.SoundSource.BLOCKS, 0.5F, 0.8F + sl.random.nextFloat() * 0.3F);
                }
            }
            if (p.col >= total) {
                p.rect++;
                p.col = 0;
            }
        }
        if (p.rect >= p.rects.size()) {
            if (p.skipped) {
                restart(p);
                p.wait = RETRY_WAIT;
            } else {
                p.laid = true;
                p.verifyAt = sl.getGameTime() + VERIFY_EVERY;
            }
        }
        return placed;
    }

    /** Bloco de ponte de algum estilo (escada, tábua)? Só esses são trocados quando o estilo muda. */
    private static boolean bridgeBlock(BlockState st) {
        for (SettlementStyle style : SettlementStyle.values()) {
            WaterCity.Pal pal = WaterCity.pal(style);
            if (st.is(pal.stairs()) || st.is(pal.deck().getBlock())) return true;
        }
        return false;
    }

    private static int putBridge(ServerLevel sl, BlockPos pos, BlockState want) {
        BlockState cur = sl.getBlockState(pos);
        if (cur == want) return 0;
        if (!(cur.isAir() || cur.canBeReplaced() && cur.getFluidState().isEmpty() || bridgeBlock(cur))) return 0;
        sl.setBlock(pos, want, 2 | 16);
        return 1;
    }

    /**
     * Uma coluna da ponte em arco (5 blocos de comprimento sobre o canal de 3): sobe dois degraus, um bloco no alto,
     * desce dois degraus. Debaixo dos 3 blocos do meio ficam 2 blocos de ar sobre a água: o barco passa.
     */
    private static int bridgeColumn(ServerLevel sl, Settlement s, int[] r, int x, int z) {
        Direction out = Direction.values()[r[5]];
        int deck = WaterCity.deckY(s);
        // posição ao longo da ponte, a partir do lado da praça (0..4)
        int t = out.getStepX() != 0 ? (out.getStepX() > 0 ? x - r[0] : r[2] - x) : (out.getStepZ() > 0 ? z - r[1] : r[3] - z);
        WaterCity.Pal pal = WaterCity.pal(s.style);
        BlockState up = pal.stairs().defaultBlockState().setValue(StairBlock.FACING, out);
        BlockState down = pal.stairs().defaultBlockState().setValue(StairBlock.FACING, out.getOpposite());
        int n = 0;
        switch (t) {
            case 0, 4 -> {
                n += WaterCity.walkColumn(sl, s, x, z);
                n += putBridge(sl, new BlockPos(x, deck + 1, z), t == 0 ? up : down);
            }
            case 1 -> n += putBridge(sl, new BlockPos(x, deck + 2, z), up);
            case 3 -> n += putBridge(sl, new BlockPos(x, deck + 2, z), down);
            default -> n += putBridge(sl, new BlockPos(x, deck + 2, z), pal.deck());
        }
        return n;
    }

    // ---- enfeites
    /** Enfeite nosso (para tirar): só os blocos que a planta usa — nada mais é tocado. */
    private static boolean decorBlock(BlockState st) {
        Block b = st.getBlock();
        return b == Blocks.PRISMARINE_BRICKS || b == Blocks.SEA_LANTERN || b == Blocks.WATER
                || b == Blocks.LANTERN || b == Blocks.SOUL_LANTERN || b == Blocks.LILY_PAD || b == Blocks.BARREL || b == Blocks.COMPOSTER
                || b == Blocks.HAY_BLOCK || b == Blocks.STRIPPED_MANGROVE_LOG || b == Blocks.MANGROVE_PLANKS || b == Blocks.DARK_OAK_PLANKS
                || b == Blocks.DARK_OAK_SLAB || b == Blocks.SPRUCE_PLANKS || b == Blocks.DARK_OAK_FENCE || b == Blocks.SPRUCE_FENCE
                || b == Blocks.RED_WOOL || b == Blocks.WHITE_WOOL || b instanceof net.minecraft.world.level.block.FenceBlock
                || com.renanpontes.isekaimahou.feature.settlements.build.TierLook.isLampPart(st)
                || com.renanpontes.isekaimahou.feature.settlements.build.TierLook.isLampLight(st);
    }

    private static void removeDecor(ServerLevel sl, Settlement s, long key) {
        BlockPos pos = BlockPos.of(key);
        if (!sl.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return;
        if (!decorBlock(sl.getBlockState(pos))) return;
        // o que ficava dentro d'água devolve a água
        sl.setBlock(pos, (pos.getY() <= WaterCity.seaY(s) ? Blocks.WATER : Blocks.AIR).defaultBlockState(), 3);
    }

    /**
     * Enfeites: primeiro tira os anotados que saíram da planta (de cima para baixo), depois põe os que faltam (de
     * baixo para cima), {@code max} por tick. Só entra em ar/água; cada bloco posto é anotado em {@code Decor}.
     */
    private static void decorate(ServerLevel sl, Settlement s, Plan p, int max) {
        long[] old = s.waterData.getLongArray("Decor");
        if (p.decorIdx == 0 && old.length > 0) {
            LongArrayList keep = new LongArrayList();
            for (int i = old.length - 1; i >= 0; i--) {
                if (p.decor.containsKey(old[i])) keep.add(old[i]);
                else removeDecor(sl, s, old[i]);
            }
            s.waterData.putLongArray("Decor", keep.toLongArray());
        }
        LongArrayList tracked = new LongArrayList(s.waterData.getLongArray("Decor"));
        Set<Long> have = new HashSet<>(tracked);
        List<Map.Entry<Long, BlockState>> all = new ArrayList<>(p.decor.entrySet());
        int done = 0;
        int sea = WaterCity.seaY(s);
        while (p.decorIdx < all.size() && done < max) {
            Map.Entry<Long, BlockState> e = all.get(p.decorIdx++);
            BlockPos pos = BlockPos.of(e.getKey());
            if (!sl.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) continue;
            BlockState cur = sl.getBlockState(pos), want = e.getValue();
            if (cur.is(want.getBlock()) && (!want.is(Blocks.WATER) || cur.getFluidState().isSource())) {
                if (have.add(e.getKey())) tracked.add((long) e.getKey());
                continue;
            }
            boolean free = pos.getY() <= sea ? WaterCity.open(cur) : cur.isAir() || cur.canBeReplaced() && cur.getFluidState().isEmpty();
            // enfeite nosso de outro estilo no mesmo lugar (a cidade foi reformada): troca
            if (!free && have.contains(e.getKey()) && decorBlock(cur)) free = true;
            if (!free || !supported(sl, pos, want)) continue;
            // (a água do chafariz entra com aviso aos vizinhos, para começar a cair)
            sl.setBlock(pos, Block.updateFromNeighbourShapes(want, sl, pos), want.is(Blocks.WATER) ? 3 : 2 | 16);
            if (have.add(e.getKey())) tracked.add((long) e.getKey());
            done++;
        }
        s.waterData.putLongArray("Decor", tracked.toLongArray());
        if (p.decorIdx >= all.size()) {
            p.decorated = true;
            boats(sl, s, p, false);
        }
    }

    /** O enfeite tem onde se apoiar? (vitória-régia só sobre água parada; poste/barril só sobre piso; lanterna sobre o poste) */
    private static boolean supported(ServerLevel sl, BlockPos pos, BlockState want) {
        BlockState below = sl.getBlockState(pos.below());
        if (want.is(Blocks.LILY_PAD)) return below.is(Blocks.WATER) && below.getFluidState().isSource();
        if (want.is(Blocks.WATER)) return below.is(Blocks.SEA_LANTERN);
        Block b = want.getBlock();
        if (b == Blocks.LANTERN || b == Blocks.SOUL_LANTERN || b == Blocks.BARREL || b == Blocks.COMPOSTER || b == Blocks.HAY_BLOCK
                || b instanceof net.minecraft.world.level.block.FenceBlock
                || com.renanpontes.isekaimahou.feature.settlements.build.TierLook.isLampPart(want)
                || com.renanpontes.isekaimahou.feature.settlements.build.TierLook.isLampLight(want)) {
            return !below.isAir() && below.getFluidState().isEmpty();
        }
        return true;
    }

    // ---- barcos
    /**
     * Barcos do cais: 2 (3 a partir do Burgo). Se alguém levou ou quebrou, um novo aparece devagar — no máximo um por
     * vez, com {@link #BOAT_WAIT} ticks entre um e outro. {@code now} (testes) repõe todos já.
     */
    private static int boats(ServerLevel sl, Settlement s, Plan p, boolean now) {
        if (p.moorings.isEmpty()) return 0;
        BlockPos first = p.moorings.get(0);
        if (!sl.isLoaded(first)) return 0;
        int want = s.level >= 3 ? 3 : 2;
        AABB box = new AABB(first).inflate(20, 6, 20);
        List<Boat> list = sl.getEntitiesOfClass(Boat.class, box, b -> b.getTags().contains(BOAT_TAG));
        long time = sl.getGameTime(), last = s.waterData.getLong("BoatAt");
        boolean firstTime = !s.waterData.contains("BoatAt");
        int made = 0;
        for (int i = 0; i < p.moorings.size() && list.size() + made < want; i++) {
            if (!now && !firstTime && (made > 0 || time - last < BOAT_WAIT)) break;
            BlockPos m = p.moorings.get(i);
            if (!sl.getBlockState(m.below()).is(Blocks.WATER) || !sl.getBlockState(m).isAir()) continue;
            if (!sl.getEntitiesOfClass(Boat.class, new AABB(m).inflate(1.2)).isEmpty()) continue;
            Boat boat = EntityType.BOAT.create(sl);
            if (boat == null) break;
            boat.setVariant(WaterCity.pal(s.style).boat());
            boat.moveTo(m.getX() + 0.5, m.getY() - 0.1, m.getZ() + 0.5, WaterCity.front(s).toYRot(), 0F);
            boat.addTag(BOAT_TAG);
            sl.addFreshEntity(boat);
            made++;
        }
        if (made > 0 || firstTime) s.waterData.putLong("BoatAt", time);
        return made;
    }

    // =====================================================================================
    //  Comandos de teste / reforma
    // =====================================================================================
    /** Termina AGORA passarelas, pontes, cais, enfeites e barcos (só em chunks carregados). Devolve os blocos postos. */
    public static int finishNow(ServerLevel sl, Settlement s) {
        if (!s.water) return 0;
        Plan p = current(s, true);
        restart(p);
        int n = lay(sl, s, p, Integer.MAX_VALUE, Integer.MAX_VALUE);
        p.wait = 0;
        p.decorIdx = 0;
        decorate(sl, s, p, Integer.MAX_VALUE);
        boats(sl, s, p, true);
        return n;
    }

    /** Tira os enfeites anotados e refaz a planta do zero (teste; também depois de trocar o estilo). */
    public static void redo(ServerLevel sl, Settlement s) {
        if (!s.water) return;
        long[] old = s.waterData.getLongArray("Decor");
        for (int i = old.length - 1; i >= 0; i--) removeDecor(sl, s, old[i]);
        s.waterData.remove("Decor");
        PLANS.remove(s.id);
    }

    /** Linha de estado para {@code /mahou city water}. */
    static Component debug(ServerLevel sl, Settlement s) {
        Component name = SettlementService.cityName(sl.getServer(), s);
        if (!s.water) return SettlementsLang.tr("cmd.water.none", name);
        Plan p = current(s, true);
        int cols = 0;
        for (int[] r : p.rects) cols += (r[2] - r[0] + 1) * (r[3] - r[1] + 1);
        int boats = p.moorings.isEmpty() ? 0 : sl.getEntitiesOfClass(Boat.class, new AABB(p.moorings.get(0)).inflate(20, 6, 20),
                b -> b.getTags().contains(BOAT_TAG)).size();
        return SettlementsLang.tr("cmd.water.state", name, WaterCity.seaY(s), p.rects.size(), cols, p.laid ? 100 : p.rects.isEmpty() ? 0
                : p.rect * 100 / p.rects.size(), s.waterData.getLongArray("Decor").length, p.decor.size(), boats);
    }

    private WaterWorks() {}
}
