package com.renanpontes.isekaimahou.feature.settlements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.settlements.build.Blueprint;
import com.renanpontes.isekaimahou.feature.settlements.build.CityLayout;
import com.renanpontes.isekaimahou.feature.settlements.build.CityTerrain;
import com.renanpontes.isekaimahou.feature.settlements.build.ConstructionEngine;
import com.renanpontes.isekaimahou.feature.settlements.build.Project;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Onda 9 (isles) — a Ilha Celeste vira um <b>ARQUIPÉLAGO</b>. Pedido do dono (2026-11-13): <i>"a cidade voadora tem que
 * ser bem mais alta... e ter um local pra subir no meio que nem as sky island... e ir criando ilhas com pontes... tem
 * que ir expandindo conforme a necessidade dele"</i>. Só vale para cidade do céu fundada a partir desta onda
 * ({@code s.sky && s.isles}); a Ilha Celeste antiga (um bloco só, {@link SkyCity}) continua exatamente como era.
 * <ul>
 *   <li><b>Bem mais alta</b>: o Coração nasce em {@link #ALTITUDE} (ou {@link #CLEARANCE} acima do morro mais alto por
 *       perto), acima das nuvens, nunca acima de {@link #MAX_Y} (sobra lugar para as torres abaixo do limite do mundo).</li>
 *   <li><b>Ilha do meio</b> ({@link #CORE}): a praça e o <b>poço da Corrente Ascendente</b> — um buraco 3×3 que atravessa
 *       a ilha ao lado da praça ({@link #shaft}). Quem está no marco do chão sobe por dentro dele e é posto ao lado
 *       ({@link #landing}); agachado, desce pelo mesmo buraco ({@link SkyAccess}).</li>
 *   <li><b>Uma ilha por lote</b> ({@link #LOT}), criada só quando a cidade PRECISA do lote: qualquer obra na fila
 *       (construir, aplainar, melhorar, mudar de estilo...) num terreno sem ilha faz a ilha nascer ({@link #sync}). Os
 *       terrenos continuam sendo a planta de sempre (painel, níveis que liberam, expansão), só que espaçados
 *       ({@code CityLayout.ISLE_PITCH}): entre uma ilha e outra fica o vazio.</li>
 *   <li><b>Pontes</b>: cada ilha se liga à ilha do terreno de dentro (o mesmo "vizinho de dentro" das ruas,
 *       {@link CityTerrain#inward}); se ali ainda não há lote, nasce uma <b>ilhota de passagem</b> ({@link #STEP}) e a
 *       corrente continua até a praça. Ponte de 5 de largura com guarda-corpo, no piso do degrau da cidade (tábuas no
 *       começo, calçamento do estilo depois) e lanternas nas pontas; refeita quando o estilo ou o degrau mudam.</li>
 *   <li><b>Muralhas</b>: um muro em anel boiando no vazio não faz sentido — no arquipélago a muralha k são as 4 torres e
 *       os 4 portões dela, cada um na sua <b>ilhota de vigia</b> ({@link #POST}); os portões ficam em cima das pontes dos
 *       eixos. Ela conta como muralha para tudo (degrau, segurança, torres que atiram).</li>
 * </ul>
 * Nada é destruído: a terra só entra em ar/fluido/mato solto; ilha nunca some (cidade é sagrada). Tudo aos poucos, com o
 * orçamento de blocos das obras, só em chunk carregado, com o andamento salvo em {@code Settlement.isleData}.
 */
public final class SkyIsles {
    // ---- altura (só cidades novas: a altura de uma cidade já fundada nunca muda)
    /** Altitude do Coração de uma Ilha Celeste nova (acima das nuvens, que ficam em ~192). */
    public static final int ALTITUDE = 220;
    /** ...ou este tanto acima do terreno mais alto por perto, se o lugar for montanhoso. */
    public static final int CLEARANCE = 100;
    /** Teto: o Coração nunca passa daqui (prédio/torre de até ~56 blocos ainda cabe abaixo do limite do mundo, 320). */
    public static final int MAX_Y = 268; // era 256: montanhas altas recusavam a cidade (mundo vai até 320; sobra ~50 p/ torres)
    /** Folga mínima entre o terreno mais alto e o Coração; menos que isso = lugar alto demais para fundar. */
    public static final int MIN_GAP = 32; // era 48 (fundar mais fácil perto de montanhas)

    // ---- tipos de ilha
    public static final int CORE = 0, LOT = 1, STEP = 2, POST = 3;
    /** Meia-largura, raio do canto arredondado, quanto a borda estufa e a fundura da barriga, por tipo. */
    private static final int[] RADIUS = {14, 10, 3, 6};
    private static final int[] CORNER = {5, 4, 2, 2};
    private static final double[] BULGE = {2.5, 2.0, 1.0, 1.5};
    private static final int[] DEPTH = {24, 14, 5, 9};
    /** Poço da Corrente Ascendente na ilha do meio: a partir do coração, no lado da frente da praça e um pouco de lado. */
    public static final int SHAFT_ALONG = 11, SHAFT_ACROSS = 5;
    /** Onde quem sobe é posto (pés): ao lado do poço, do lado da rua principal. */
    private static final int LAND_ACROSS = 2;

    // ---- ritmo
    private static final int BLOCKS_PER_TICK = 320, COLUMNS_PER_TICK = 900, BRIDGE_PER_TICK = 24;
    private static final int RETRY_WAIT = 200, VERIFY_EVERY = 6000;
    private static final int PITCH = CityLayout.ISLE_PITCH;

    /** Uma ilha: tipo, centro (dx, dz a partir do coração), terreno dono (-1 = nenhum) e se a terra já está toda lá. */
    static final class Isle {
        int kind, dx, dz, cell;
        boolean done;
        /** Só em memória: tempo do jogo antes do qual a geração não volta nesta ilha (chunk descarregado). */
        long retryAt;

        Isle(int kind, int dx, int dz, int cell, boolean done) {
            this.kind = kind;
            this.dx = dx;
            this.dz = dz;
            this.cell = cell;
            this.done = done;
        }
    }

    /** Estado de uma cidade (em memória; a lista de ilhas, o cursor e os blocos das pontes vão para o NBT dela). */
    private static final class State {
        final List<Isle> list = new ArrayList<>();
        final Map<Long, Isle> byPos = new HashMap<>();
        final List<Isle> posts = new ArrayList<>();
        Isle gen;
        int ring, idx;
        boolean skipped;
        // pontes
        long sig = Long.MIN_VALUE;
        final LinkedHashMap<Long, BlockState> bridge = new LinkedHashMap<>();
        final List<Long> keys = new ArrayList<>();
        final LongOpenHashSet tracked = new LongOpenHashSet();
        int bIdx, bSinceSave;
        boolean bLaid, bSwept;
        long bVerify;

        void add(Isle is) {
            list.add(is);
            if (is.kind == POST) posts.add(is);
            else byPos.put(key(is.dx, is.dz), is);
        }
    }

    private static final Map<Settlement, State> STATES = new WeakHashMap<>();

    public static void clear() {
        STATES.clear();
    }

    private static long key(int dx, int dz) {
        return (long) dx << 32 | dz & 0xFFFFFFFFL;
    }

    // =====================================================================================
    //  Altura
    // =====================================================================================
    /** Altura do Coração de uma ilha nova sobre um lugar cujo terreno mais alto por perto é {@code highest}; -1 = alto demais. */
    public static int heartY(int highest) {
        int y = Math.min(MAX_Y, Math.max(ALTITUDE, highest + CLEARANCE));
        return y - highest < MIN_GAP ? -1 : y;
    }

    // =====================================================================================
    //  Forma (conta pura: mesma resposta no servidor e no cliente)
    // =====================================================================================
    /** Lado da frente da praça (para onde ficam o poço da corrente e a rua principal). */
    public static Direction front(Settlement s) {
        return SkyCity.pierSide(s);
    }

    private static double sdf(int r, int corner, double x, double z) {
        double qx = Math.abs(x) - (r - corner), qz = Math.abs(z) - (r - corner);
        return Math.hypot(Math.max(qx, 0), Math.max(qz, 0)) + Math.min(Math.max(qx, qz), 0) - corner;
    }

    private static int seedOf(BlockPos heart, int cdx, int cdz) {
        return SkyCity.seed(heart) + cdx * 7919 + cdz * 104729;
    }

    /** O deslocamento (dx, dz a partir do coração) cai no poço 3×3 da Corrente Ascendente? */
    public static boolean inShaft(Direction front, int dx, int dz) {
        int along = dx * front.getStepX() + dz * front.getStepZ();
        int across = -dx * front.getStepZ() + dz * front.getStepX();
        return Math.abs(along - SHAFT_ALONG) <= 1 && Math.abs(across - SHAFT_ACROSS) <= 1;
    }

    private static int offX(Direction front, int along, int across) {
        return front.getStepX() * along - front.getStepZ() * across;
    }

    private static int offZ(Direction front, int along, int across) {
        return front.getStepZ() * along + front.getStepX() * across;
    }

    /**
     * Blocos de terra da coluna (lx, lz a partir do centro da ilha) de uma ilha do tipo {@code kind} com centro em
     * (cdx, cdz) a partir do coração; 0 = fora. Quadrado de cantos redondos com a borda estufada por ruído, barriga em
     * cone e uma ponta de pedra pendurada perto do meio.
     */
    public static int thickness(BlockPos heart, Direction front, int kind, int cdx, int cdz, int lx, int lz) {
        int r = RADIUS[kind];
        double d = sdf(r, CORNER[kind], lx, lz);
        if (d > BULGE[kind]) return 0;
        int seed = seedOf(heart, cdx, cdz);
        double edge = BULGE[kind] * (0.6 * SkyCity.noise(lx / 6.0, lz / 6.0, seed) + 0.4 * SkyCity.noise(lx / 2.5, lz / 2.5, seed + 1));
        double e = edge - d;
        if (e < 0) return 0;
        if (kind == CORE && inShaft(front, cdx + lx, cdz + lz)) return 0;
        double rn = Math.min(1.0, Math.hypot(lx, lz) / (r + BULGE[kind]));
        double full = 2 + DEPTH[kind] * Math.pow(1 - rn, 1.25) + 3 * SkyCity.noise(lx / 4.0, lz / 4.0, seed + 2);
        int h = SkyCity.hash(cdx, cdz, seed + 3);
        double px = ((h & 7) - 3.5) * r / 10.0, pz = ((h >> 3 & 7) - 3.5) * r / 10.0;
        double sd = Math.hypot(lx - px, lz - pz), sr = 1.5 + r / 6.0;
        if (sd < sr) full += (4 + (h >> 6 & 7) + DEPTH[kind] * 0.3) * Math.pow(1 - sd / sr, 1.4);
        double taper = 1.5 + e * 1.6 + SkyCity.noise(lx / 3.0, lz / 3.0, seed + 4);
        return Mth.clamp((int) Math.min(full, taper), 1, DEPTH[kind] + 16);
    }

    /** A coluna (dx, dz a partir do coração) faz parte da ilha do MEIO de uma cidade nova com esta frente? (prévia) */
    public static boolean inCore(BlockPos heart, Direction front, int dx, int dz) {
        return thickness(heart, front, CORE, 0, 0, dx, dz) > 0;
    }

    /** Até onde (num eixo) a ilha do meio pode ir a partir do coração. */
    public static int coreReach() {
        return RADIUS[CORE] + 3;
    }

    // =====================================================================================
    //  Estado
    // =====================================================================================
    private static State state(Settlement s) {
        State st = STATES.get(s);
        if (st == null) {
            st = new State();
            ListTag l = s.isleData.getList("L", Tag.TAG_INT_ARRAY);
            for (int i = 0; i < l.size(); i++) {
                int[] a = l.getIntArray(i);
                if (a.length < 5 || a[0] < CORE || a[0] > POST) continue;
                st.add(new Isle(a[0], a[1], a[2], a[3], a[4] != 0));
            }
            for (long p : s.isleData.getLongArray("B")) st.tracked.add(p);
            int g = s.isleData.contains("G") ? s.isleData.getInt("G") : -1;
            if (g >= 0 && g < st.list.size() && !st.list.get(g).done) {
                st.gen = st.list.get(g);
                st.ring = Math.max(0, s.isleData.getInt("Ring"));
                st.idx = Math.max(0, s.isleData.getInt("Idx"));
            }
            STATES.put(s, st);
        }
        return st;
    }

    private static void save(Settlement s, State st) {
        ListTag l = new ListTag();
        for (Isle is : st.list) l.add(new IntArrayTag(new int[] {is.kind, is.dx, is.dz, is.cell, is.done ? 1 : 0}));
        s.isleData.put("L", l);
        cursor(s, st);
    }

    private static void cursor(Settlement s, State st) {
        s.isleData.putInt("G", st.gen == null ? -1 : st.list.indexOf(st.gen));
        s.isleData.putInt("Ring", st.ring);
        s.isleData.putInt("Idx", st.idx);
    }

    private static void dirty(ServerLevel sl) {
        SettlementData.get(sl.getServer()).setDirty();
    }

    // =====================================================================================
    //  Consultas (para SkyCity, SkyAccess e as obras)
    // =====================================================================================
    private static boolean on(Settlement s) {
        return s.sky && s.isles;
    }

    /** Espessura da terra na coluna (dx, dz a partir do coração), somando as ilhas que se encostam ali; 0 = vazio. */
    private static int thickAt(Settlement s, State st, int dx, int dz, boolean needDone) {
        return thickAt(s, st, dx, dz, needDone, true);
    }

    private static int thickAt(Settlement s, State st, int dx, int dz, boolean needDone, boolean posts) {
        Direction front = front(s);
        int best = 0;
        int i = Math.round(dx / (float) PITCH), j = Math.round(dz / (float) PITCH);
        best = Math.max(best, thickOf(s, front, st.byPos.get(key(i * PITCH, j * PITCH)), dx, dz, needDone));
        if (Math.abs(dx) <= coreReach() && Math.abs(dz) <= coreReach() && (i != 0 || j != 0)) {
            best = Math.max(best, thickOf(s, front, st.byPos.get(key(0, 0)), dx, dz, needDone));
        }
        if (posts) for (Isle p : st.posts) best = Math.max(best, thickOf(s, front, p, dx, dz, needDone));
        return best;
    }

    private static int thickOf(Settlement s, Direction front, @Nullable Isle is, int dx, int dz, boolean needDone) {
        if (is == null || needDone && !is.done) return 0;
        int lx = dx - is.dx, lz = dz - is.dz, r = RADIUS[is.kind] + 3;
        if (Math.abs(lx) > r || Math.abs(lz) > r) return 0;
        return thickness(s.heart, front, is.kind, is.dx, is.dz, lx, lz);
    }

    /** Já existe terra pronta na coluna (x, z) do mundo? */
    public static boolean hasLand(Settlement s, int x, int z) {
        return on(s) && thickAt(s, state(s), x - s.heart.getX(), z - s.heart.getZ(), true) > 0;
    }

    /**
     * Terra pronta da CIDADE na coluna (x, z): a ilha do meio, as de lote e as de passagem — todas ligadas por ponte. As
     * ilhotas de vigia das muralhas ficam de fora (ninguém chega nelas andando): pontos de chegada e de invasão usam esta.
     */
    public static boolean hasTownLand(Settlement s, int x, int z) {
        return on(s) && thickAt(s, state(s), x - s.heart.getX(), z - s.heart.getZ(), true, false) > 0;
    }

    /** A coluna (x, z) é de alguma ilha planejada (pronta ou se formando)? */
    public static boolean inFootprint(Settlement s, int x, int z) {
        return on(s) && thickAt(s, state(s), x - s.heart.getX(), z - s.heart.getZ(), false) > 0;
    }

    /** Bloco mais baixo da terra pronta na coluna, ou {@link Integer#MAX_VALUE} sem terra. */
    public static int floorY(Settlement s, int x, int z) {
        int t = on(s) ? thickAt(s, state(s), x - s.heart.getX(), z - s.heart.getZ(), true) : 0;
        return t <= 0 ? Integer.MAX_VALUE : SkyCity.topY(s) - t + 1;
    }

    /** A ilha do meio já está pronta (praça, poço da corrente)? */
    public static boolean coreDone(Settlement s) {
        if (!on(s)) return false;
        Isle core = state(s).byPos.get(key(0, 0));
        return core != null && core.done;
    }

    /** Alguma ilha ainda se formando? */
    public static boolean generating(Settlement s) {
        if (!on(s)) return false;
        for (Isle is : state(s).list) if (!is.done) return true;
        return false;
    }

    /** 0..99 enquanto alguma ilha se forma; 100 = tudo pronto. */
    public static int percent(Settlement s) {
        State st = state(s);
        int all = 0, done = 0;
        for (Isle is : st.list) {
            int w = (RADIUS[is.kind] + 1) * (RADIUS[is.kind] + 1);
            all += w;
            if (is.done) done += w;
        }
        return all == 0 ? 0 : done >= all ? 100 : Math.min(99, done * 100 / all);
    }

    /** Centro do poço da Corrente Ascendente (x/z do mundo, na altura do topo da ilha). */
    public static BlockPos shaft(Settlement s) {
        Direction f = front(s);
        return s.heart.offset(offX(f, SHAFT_ALONG, SHAFT_ACROSS), -1, offZ(f, SHAFT_ALONG, SHAFT_ACROSS));
    }

    /** Onde quem sobe pela corrente é posto (pés), ao lado do poço; null = a ilha do meio ainda não está pronta. */
    @Nullable
    public static BlockPos landing(Settlement s) {
        if (!coreDone(s)) return null;
        Direction f = front(s);
        return s.heart.offset(offX(f, SHAFT_ALONG, LAND_ACROSS), 0, offZ(f, SHAFT_ALONG, LAND_ACROSS));
    }

    /** Volta (5×5 menos a boca) do poço, para o guarda-corpo: {dx, dz} a partir do coração, e se é canto. */
    static List<int[]> shaftRing(Settlement s) {
        Direction f = front(s);
        List<int[]> out = new ArrayList<>();
        for (int a = -2; a <= 2; a++) {
            for (int c = -2; c <= 2; c++) {
                if (Math.abs(a) != 2 && Math.abs(c) != 2) continue;
                // a boca do poço fica do lado da rua principal (onde se é posto ao chegar)
                if (c == -2 && Math.abs(a) <= 1) continue;
                boolean corner = Math.abs(a) == 2 && Math.abs(c) == 2;
                out.add(new int[] {offX(f, SHAFT_ALONG + a, SHAFT_ACROSS + c), offZ(f, SHAFT_ALONG + a, SHAFT_ACROSS + c), corner ? 1 : 0});
            }
        }
        return out;
    }

    /** Quantas ilhas (todas / prontas) e quantos blocos de ponte a cidade tem — para o comando de estado. */
    static int[] counts(Settlement s) {
        State st = state(s);
        int done = 0, lots = 0, steps = 0, posts = 0;
        for (Isle is : st.list) {
            if (is.done) done++;
            if (is.kind == LOT) lots++;
            else if (is.kind == STEP) steps++;
            else if (is.kind == POST) posts++;
        }
        return new int[] {st.list.size(), done, lots, steps, posts, st.tracked.size(), st.bridge.size()};
    }

    // =====================================================================================
    //  Fundação e "a cidade precisa de terra"
    // =====================================================================================
    /** A cidade nasceu: a ilha do meio entra na fila (o fundador sobe e os construtores chegam quando ela ficar pronta). */
    static void founded(ServerLevel sl, Settlement s) {
        State st = state(s);
        if (st.byPos.get(key(0, 0)) == null) st.add(new Isle(CORE, 0, 0, CityLayout.CENTER_CELL, false));
        save(s, st);
        dirty(sl);
    }

    /**
     * Confere de que ilhas a cidade precisa AGORA e põe na fila as que faltam: a do meio; uma por terreno com prédio,
     * altura aplainada ou obra na fila; as ilhotas de passagem até a praça; e as ilhotas de vigia das muralhas (prontas
     * ou em obra). Ilha nunca é tirada. Devolve true se alguma nasceu.
     */
    static boolean sync(ServerLevel sl, Settlement s) {
        if (!on(s)) return false;
        State st = state(s);
        boolean changed = false;
        if (st.byPos.get(key(0, 0)) == null) {
            st.add(new Isle(CORE, 0, 0, CityLayout.CENTER_CELL, false));
            changed = true;
        }
        Set<Integer> cells = new LinkedHashSet<>();
        for (Project p : s.projects) if (p.cell >= 0) cells.add(p.cell);
        cells.addAll(s.buildings.keySet());
        cells.addAll(s.plotBase.keySet());
        for (int c : cells) changed |= want(s, st, c);
        // muralhas: as torres e os portões ficam em ilhotas próprias (no céu não há trecho de muro)
        Set<Integer> walls = new LinkedHashSet<>(s.walls);
        for (Project p : s.projects) if (p.cell < 0 && Blueprint.WALL.equals(p.key)) walls.add(CityLayout.wallOf(p));
        for (int k : walls) {
            for (Project.Piece piece : CityLayout.wallPieces(s, k, 1)) {
                if (piece.kind != Project.Piece.TOWER && piece.kind != Project.Piece.GATE) continue;
                int dx = piece.cx - s.heart.getX(), dz = piece.cz - s.heart.getZ();
                boolean have = false;
                for (Isle p : st.posts) {
                    if (p.dx == dx && p.dz == dz) {
                        have = true;
                        break;
                    }
                }
                if (!have) {
                    st.add(new Isle(POST, dx, dz, -1, false));
                    changed = true;
                }
            }
        }
        if (changed) {
            save(s, st);
            dirty(sl);
        }
        return changed;
    }

    /** Garante a ilha do terreno {@code cell} (e as ilhotas de passagem até a praça). */
    private static boolean want(Settlement s, State st, int cell) {
        if (cell == CityLayout.CENTER_CELL || !CityLayout.isIsle(cell)) return false;
        boolean changed = put(st, cell, LOT);
        int c = cell;
        for (int guard = 0; guard < 2 * CityLayout.MAX_RADIUS + 2; guard++) {
            c = CityTerrain.inward(c);
            if (c == CityLayout.CENTER_CELL || !CityLayout.isIsle(c)) break;
            changed |= put(st, c, STEP);
        }
        return changed;
    }

    private static boolean put(State st, int cell, int kind) {
        int dx = CityLayout.offsetX(cell), dz = CityLayout.offsetZ(cell);
        Isle is = st.byPos.get(key(dx, dz));
        if (is == null) {
            st.add(new Isle(kind, dx, dz, cell, false));
            return true;
        }
        if (kind == LOT && is.kind == STEP) {
            // a ilhota de passagem vira ilha de lote: a terra cresce em volta dela (só no ar)
            is.kind = LOT;
            is.done = false;
            return true;
        }
        return false;
    }

    /** Teste: cria já a ilha do terreno (i, j) — sem obra nenhuma — e o caminho de ilhotas até a praça. */
    static boolean force(ServerLevel sl, Settlement s, int i, int j) {
        if (!on(s) || Math.max(Math.abs(i), Math.abs(j)) > CityLayout.MAX_RADIUS || i == 0 && j == 0) return false;
        State st = state(s);
        boolean changed = want(s, st, CityLayout.isleCell(i, j));
        if (changed) {
            save(s, st);
            dirty(sl);
        }
        return changed;
    }

    // =====================================================================================
    //  Tick: ilhas que faltam, geração aos poucos, pontes
    // =====================================================================================
    static void tick(ServerLevel sl, Settlement s, int age) {
        if (!on(s)) return;
        State st = state(s);
        if (age % 20 == 5) sync(sl, s);
        int budget = ConstructionEngine.claim(sl, BLOCKS_PER_TICK);
        if (budget > 0) generate(sl, s, st, budget, COLUMNS_PER_TICK, true);
        // as pontes saem depois da terra que elas ligam (a ilha do meio pronta antes de tudo)
        if (coreDone(s)) {
            int b = ConstructionEngine.claim(sl, BRIDGE_PER_TICK);
            if (b > 0) bridges(sl, s, st, b);
        }
    }

    /** Próxima ilha a formar (na ordem em que nasceram), pulando as que esperam chunk carregado. */
    @Nullable
    private static Isle next(State st, long now) {
        for (Isle is : st.list) if (!is.done && is.retryAt <= now) return is;
        return null;
    }

    /** Posição (lx, lz) da coluna {@code idx} do anel {@code ring} em volta do centro da ilha (a ilha cresce do meio para fora). */
    private static int[] ringPos(int ring, int idx) {
        if (ring == 0) return new int[] {0, 0};
        int count = 8 * ring;
        int i = (int) ((long) idx * (2 * ring + 1) % count);
        int side = i / (2 * ring), off = i % (2 * ring);
        return switch (side) {
            case 0 -> new int[] {-ring + off, -ring};
            case 1 -> new int[] {ring, -ring + off};
            case 2 -> new int[] {ring - off, ring};
            default -> new int[] {-ring, ring - off};
        };
    }

    /** Avança a geração: até {@code blocks} blocos e {@code columns} colunas. Devolve os blocos postos. */
    private static int generate(ServerLevel sl, Settlement s, State st, int blocks, int columns, boolean fx) {
        long now = sl.getGameTime();
        int placed = 0, seen = 0;
        Direction front = front(s);
        while (placed < blocks && seen < columns) {
            if (st.gen == null || st.gen.done) {
                st.gen = next(st, now);
                st.ring = st.idx = 0;
                st.skipped = false;
                if (st.gen == null) break;
            }
            Isle is = st.gen;
            int end = RADIUS[is.kind] + 3;
            while (st.ring <= end && placed < blocks && seen < columns) {
                int count = st.ring == 0 ? 1 : 8 * st.ring;
                if (st.idx >= count) {
                    st.ring++;
                    st.idx = 0;
                    continue;
                }
                int[] o = ringPos(st.ring, st.idx++);
                seen++;
                int t = thickness(s.heart, front, is.kind, is.dx, is.dz, o[0], o[1]);
                if (t <= 0) continue;
                int x = s.heart.getX() + is.dx + o[0], z = s.heart.getZ() + is.dz + o[1];
                if (!sl.hasChunk(x >> 4, z >> 4)) {
                    st.skipped = true;
                    continue;
                }
                int n = SkyCity.column(sl, s, x, z, 0, t);
                placed += n;
                if (n > 0) {
                    underside(sl, s, x, z, t);
                    if (fx) SkyCity.formFx(sl, s, x, z);
                }
            }
            if (st.ring > end) {
                if (st.skipped) {
                    // coluna em chunk descarregado: esta ilha espera e a geração segue com as outras
                    is.retryAt = now + RETRY_WAIT;
                } else {
                    is.done = true;
                    formed(sl, s, is);
                }
                st.gen = null;
                save(s, st);
            }
        }
        cursor(s, st);
        return placed;
    }

    /** Enfeite debaixo da ilha: raízes penduradas em algumas colunas da barriga (nada no deserto). */
    private static void underside(ServerLevel sl, Settlement s, int x, int z, int t) {
        if (t < 5 || s.style == SettlementStyle.DESERT || (SkyCity.hash(x, z, 911) & 7) != 0) return;
        BlockPos p = new BlockPos(x, SkyCity.topY(s) - t, z);
        if (sl.getBlockState(p).isAir() && sl.getBlockState(p.above()).isFaceSturdy(sl, p.above(), Direction.DOWN)) {
            sl.setBlock(p, Blocks.HANGING_ROOTS.defaultBlockState(), 2 | 16);
        }
    }

    /** Uma ilha ficou pronta: a do meio leva o fundador para cima e traz os construtores; as outras só soam. */
    private static void formed(ServerLevel sl, Settlement s, Isle is) {
        BlockPos at = s.heart.offset(is.dx, 0, is.dz);
        sl.playSound(null, at, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.4F, is.kind == CORE ? 0.8F : 1.1F);
        if (is.kind == CORE && !s.isleData.getBoolean("Pop")) {
            s.isleData.putBoolean("Pop", true);
            SkyCity.coreReady(sl, s);
            CityPace.diary(sl.getServer(), s, SettlementsLang.tr("isle.diary.core"));
        } else if (is.kind == LOT) {
            MinecraftServer server = sl.getServer();
            for (var g : CityEconomy.governors(server, s)) {
                g.displayClientMessage(SettlementsLang.tr("isle.formed", SettlementService.cityName(server, s)).withColor(s.style.color), true);
            }
        }
        SettlementService.refreshViewers(sl.getServer(), s);
    }

    // ---- pontes
    private static long bridgeSig(Settlement s, State st) {
        long h = s.style.ordinal() * 131L + s.level * 1009L + s.plazaRot * 7919L + st.list.size() * 0x9E3779B97F4A7C15L;
        for (Isle is : st.list) h = h * 31 + is.kind * 1000003L + is.dx * 8191L + is.dz;
        return h;
    }

    /** Planta das pontes: de cada ilha de lote/passagem até a ilha do terreno de dentro, só no vão (onde não há terra). */
    private static void planBridges(Settlement s, State st) {
        st.bridge.clear();
        int top = SkyCity.topY(s), hx = s.heart.getX(), hz = s.heart.getZ();
        BlockState rail = SkyAccess.bridgeRail(s.style), lamp = SkyAccess.bridgeLamp(s.style);
        for (Isle is : st.list) {
            if (is.kind != LOT && is.kind != STEP) continue;
            int i = Math.round(is.dx / (float) PITCH), j = Math.round(is.dz / (float) PITCH);
            int parent = CityTerrain.inward(CityLayout.isleCell(i, j));
            int ax = Integer.signum(CityLayout.cellI(parent) - i), az = Integer.signum(CityLayout.cellJ(parent) - j);
            if (ax == 0 && az == 0) continue;
            int px = -az, pz = ax;
            int first = -1, last = -1;
            for (int t = 1; t < PITCH; t++) {
                int cx = is.dx + ax * t, cz = is.dz + az * t;
                boolean row = false;
                for (int w = -2; w <= 2; w++) {
                    int dx = cx + px * w, dz = cz + pz * w;
                    if (thickAt(s, st, dx, dz, false) > 0) continue;
                    int x = hx + dx, z = hz + dz;
                    st.bridge.put(BlockPos.asLong(x, top, z), WaterCity.walk(s.style, s.level, WaterCity.hash(x, z)));
                    if (Math.abs(w) == 2) st.bridge.put(BlockPos.asLong(x, top + 1, z), rail);
                    row = true;
                }
                if (row) {
                    if (first < 0) first = t;
                    last = t;
                }
            }
            if (first < 0) continue;
            // lanternas nas duas pontas (e no meio das pontes compridas, a partir do Burgo)
            List<Integer> lamps = new ArrayList<>(List.of(first, last));
            if (s.level >= 3 && last - first >= 8) lamps.add((first + last) / 2);
            for (int t : lamps) {
                for (int w = -2; w <= 2; w += 4) {
                    int x = hx + is.dx + ax * t + px * w, z = hz + is.dz + az * t + pz * w;
                    if (st.bridge.get(BlockPos.asLong(x, top + 1, z)) == rail) st.bridge.put(BlockPos.asLong(x, top + 2, z), lamp);
                }
            }
        }
        st.keys.clear();
        st.keys.addAll(st.bridge.keySet());
    }

    /** Bloco de ponte nosso (piso de algum degrau/estilo, guarda-corpo ou luz)? Só esses são trocados ou tirados. */
    private static boolean ours(BlockState st) {
        return WaterCity.isFloor(st) || SkyAccess.ownBlock(st);
    }

    /** Pontes: refaz a planta quando a cidade muda, tira o que saiu dela e põe o que falta ({@code budget} blocos). */
    private static int bridges(ServerLevel sl, Settlement s, State st, int budget) {
        long sig = bridgeSig(s, st);
        if (sig != st.sig) {
            st.sig = sig;
            planBridges(s, st);
            st.bIdx = 0;
            st.bLaid = false;
            st.bSwept = false;
        }
        if (st.bLaid) {
            if (sl.getGameTime() < st.bVerify) return 0;
            // de tempos em tempos confere tudo de novo (repõe tábua que sumiu)
            st.bIdx = 0;
            st.bLaid = false;
        }
        int n = 0;
        if (!st.bSwept) {
            n += sweep(sl, s, st);
            st.bSwept = true;
        }
        boolean skipped = false;
        while (st.bIdx < st.keys.size() && n < budget) {
            long k = st.keys.get(st.bIdx++);
            BlockPos p = BlockPos.of(k);
            if (!sl.hasChunk(p.getX() >> 4, p.getZ() >> 4)) {
                skipped = true;
                continue;
            }
            BlockState want = st.bridge.get(k), cur = sl.getBlockState(p);
            if (cur.is(want.getBlock())) {
                st.tracked.add(k);
                continue;
            }
            boolean free = cur.isAir() || cur.canBeReplaced() && cur.getFluidState().isEmpty();
            // piso/guarda-corpo nosso de outro estilo ou degrau: troca
            if (!free && !(st.tracked.contains(k) && ours(cur))) continue;
            sl.setBlock(p, Block.updateFromNeighbourShapes(want, sl, p), 3);
            st.tracked.add(k);
            n++;
            if (++st.bSinceSave >= 64) saveBridges(s, st);
        }
        if (st.bIdx >= st.keys.size()) {
            saveBridges(s, st);
            st.bLaid = true;
            st.bVerify = sl.getGameTime() + (skipped ? RETRY_WAIT : VERIFY_EVERY);
        }
        return n;
    }

    private static void saveBridges(Settlement s, State st) {
        st.bSinceSave = 0;
        s.isleData.putLongArray("B", st.tracked.toLongArray());
    }

    /**
     * Tira os blocos de ponte anotados que saíram da planta (uma ilhota virou ilha de lote e cobriu a ponte velha, o
     * estilo mudou...). Onde agora é terra, o piso volta a ser o topo da ilha. Chunk descarregado: fica para depois.
     */
    private static int sweep(ServerLevel sl, Settlement s, State st) {
        int n = 0, top = SkyCity.topY(s);
        List<Long> gone = new ArrayList<>();
        for (long k : st.tracked) if (!st.bridge.containsKey(k)) gone.add(k);
        // de cima para baixo: lanterna, guarda-corpo, piso
        gone.sort(java.util.Comparator.comparingInt((Long k) -> -BlockPos.getY(k)));
        SkyCity.Palette pal = SkyCity.palette(s.style);
        for (long k : gone) {
            BlockPos p = BlockPos.of(k);
            if (!sl.hasChunk(p.getX() >> 4, p.getZ() >> 4)) continue;
            st.tracked.remove(k);
            BlockState cur = sl.getBlockState(p);
            if (!ours(cur)) continue;
            boolean land = p.getY() == top && thickAt(s, st, p.getX() - s.heart.getX(), p.getZ() - s.heart.getZ(), false) > 0;
            sl.setBlock(p, land ? pal.top() : Blocks.AIR.defaultBlockState(), 3);
            n++;
        }
        saveBridges(s, st);
        return n;
    }

    // =====================================================================================
    //  Comandos de teste
    // =====================================================================================
    /** Termina AGORA todas as ilhas e pontes (só em chunk carregado). Devolve os blocos postos. */
    static int finishNow(ServerLevel sl, Settlement s) {
        if (!on(s)) return 0;
        sync(sl, s);
        State st = state(s);
        for (Isle is : st.list) is.retryAt = 0;
        int n = generate(sl, s, st, Integer.MAX_VALUE, Integer.MAX_VALUE, false);
        if (coreDone(s)) {
            st.sig = Long.MIN_VALUE;
            n += bridges(sl, s, st, Integer.MAX_VALUE);
        }
        return n;
    }

    /** Refaz a passada de todas as ilhas (completa buracos) e confere as pontes de novo. */
    static void regenerate(ServerLevel sl, Settlement s) {
        if (!on(s)) return;
        State st = state(s);
        for (Isle is : st.list) {
            is.done = false;
            is.retryAt = 0;
        }
        // a ilha do meio já existe: ninguém é levado de novo para a praça
        s.isleData.putBoolean("Pop", true);
        st.gen = null;
        st.sig = Long.MIN_VALUE;
        save(s, st);
        dirty(sl);
    }

    /** Linha de estado para {@code /mahou city sky} numa cidade de ilhas. */
    static Component debug(MinecraftServer server, Settlement s) {
        int[] c = counts(s);
        return SettlementsLang.tr("cmd.isle.state", SettlementService.cityName(server, s), s.heart.getY(), c[0], c[1], c[2], c[3], c[4],
                c[5], percent(s));
    }

    private SkyIsles() {}
}
