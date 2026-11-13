package com.renanpontes.isekaimahou.feature.settlements.build;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.settlements.SettlementStyle;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * MURALHA POR DEGRAU (onda 5): "paliçada → pedra → fortificada com torres e portão maior". As três peças da muralha
 * (trecho 9×3, portão 7×3, torre 5×5 — a MESMA planta dos moldes .nbt antigos, então o traçado da cidade não muda) são
 * geradas por código em 4 graus, com a paleta do estilo ({@link TierLook.WallPal}):
 * <ol>
 *   <li><b>Paliçada</b> — troncos em pé com pontas, torre de madeira com telhadinho, portão de 3×3;</li>
 *   <li><b>Pedra</b> — muro cheio de 6 de altura com ameias, torre de 9 com seteiras, portão de 3×4;</li>
 *   <li><b>Fortificada</b> — muro de 8 com faixa de destaque, torres de 11 com telhado, portão de 5×5;</li>
 *   <li><b>Real</b> — a fortificada 1 bloco mais alta, com os detalhes ricos do estilo.</li>
 * </ol>
 * Ids dos moldes: {@code settlements_wall/<estilo>.<grau>.<peça>}. Para REFORMAR uma muralha pronta existe o molde de
 * TROCA {@code settlements_swap/<estilo velho>.<grau velho>.<estilo novo>.<grau novo>.<peça>} (grau 0 = o .nbt antigo):
 * primeiro uma lista de "tirar" (um {@code structure_void} por bloco do molde velho, com o bloco esperado) e depois os
 * blocos do molde novo. A obra só tira o que ainda for o bloco que os construtores puseram e só põe onde está livre —
 * o que o jogador pendurou no muro fica.
 */
public final class TierWalls {
    public static final String PREFIX = "settlements_wall/";
    public static final String SWAP = "settlements_swap/";
    public static final String TOWER = "wall_tower", SEGMENT = "wall_segment", GATE = "wall_gate";
    /** Chave do NBT de uma entrada "tirar": id do bloco que tem de estar lá. */
    public static final String OLD = "Old";
    /**
     * Onda 8 (city_looks, "RENOVATION tem que renovar o muro tbm"): entrada "varrer a coluna" de um molde de troca — o
     * {@link #OLD} vale {@link #SWEEP} e {@link #SWEEP_H} diz até que altura. A obra tira dali TODO bloco de muralha
     * ({@link #isWallBlock}), seja qual for o estilo/grau em que a peça estava: nada de muro velho sobrando.
     */
    public static final String SWEEP = "*", SWEEP_H = "H";
    /** Estilo velho "desconhecido" num id de troca. */
    public static final String ANY = "any";
    /** Altura varrida acima do chão da peça. */
    private static final int SWEEP_TOP = 26;

    private static final Map<String, Templates.Tpl> CACHE = new HashMap<>();

    public static ResourceLocation id(SettlementStyle style, int wallTier, String piece) {
        return IsekaiMahou.id(PREFIX + style.id + "." + Math.max(1, Math.min(TierLook.WALL_MAX, wallTier)) + "." + piece);
    }

    /** Molde da peça num grau; grau 0 = o molde .nbt antigo do estilo. */
    public static ResourceLocation any(SettlementStyle style, int wallTier, String piece) {
        return wallTier <= 0 ? Blueprint.template(style, piece) : id(style, wallTier, piece);
    }

    public static ResourceLocation swapId(SettlementStyle oldStyle, int oldTier, SettlementStyle newStyle, int newTier, String piece) {
        return IsekaiMahou.id(SWAP + oldStyle.id + "." + Math.max(0, oldTier) + "." + newStyle.id + "." + newTier + "." + piece);
    }

    public static boolean isSwap(ResourceLocation id) {
        return id.getPath().startsWith(SWAP);
    }

    /** Grau de muralha que o molde constrói (0 = molde .nbt antigo). */
    public static int tierOf(ResourceLocation id) {
        String path = id.getPath();
        try {
            if (path.startsWith(PREFIX)) return Integer.parseInt(path.substring(PREFIX.length()).split("\\.")[1]);
            if (path.startsWith(SWAP)) return Integer.parseInt(path.substring(SWAP.length()).split("\\.")[3]);
        } catch (RuntimeException e) {
            // id estranho: trata como molde antigo
        }
        return 0;
    }

    /** Vão do portão do molde: {x0, x1, altura} (a grade fica em z = 1, y de 1 até a altura). */
    public static int[] opening(ResourceLocation template, SettlementStyle style) {
        int wt = tierOf(template);
        if (wt <= 0) return new int[] {2, 4, style.gateHeight};
        return gate(wt);
    }

    private static int[] gate(int wt) {
        return wt >= 3 ? new int[] {1, 5, 5} : wt == 2 ? new int[] {2, 4, 4} : new int[] {2, 4, 3};
    }

    // =====================================================================================
    //  Carga (chamada por Templates.load)
    // =====================================================================================
    @Nullable
    static Templates.Tpl load(ServerLevel level, ResourceLocation id) {
        String path = id.getPath();
        if (path.startsWith(PREFIX)) {
            String[] p = path.substring(PREFIX.length()).split("\\.");
            if (p.length < 3) return null;
            SettlementStyle st = SettlementStyle.byId(p[0]);
            return build(st == null ? SettlementStyle.KINGDOM : st, Integer.parseInt(p[1]), p[2]);
        }
        String[] p = path.substring(SWAP.length()).split("\\.");
        if (p.length < 5) return null;
        SettlementStyle os = SettlementStyle.byId(p[0]), ns = SettlementStyle.byId(p[2]);
        if (ns == null) ns = SettlementStyle.KINGDOM;
        // onda 8: estilo velho desconhecido ("any") = sem molde velho — a troca varre a peça e ergue a nova do zero
        Templates.Tpl old = os == null ? null : Templates.get(level, any(os, Integer.parseInt(p[1]), p[4]));
        Templates.Tpl neu = Templates.get(level, id(ns, Integer.parseInt(p[3]), p[4]));
        if (neu == null) return null;
        return swap(old, neu);
    }

    /** Estilo que o molde CONSTRÓI (peça nova de um molde de troca), ou null (molde .nbt antigo). */
    @Nullable
    public static SettlementStyle styleOf(ResourceLocation id) {
        String path = id.getPath();
        if (path.startsWith(PREFIX)) return SettlementStyle.byId(path.substring(PREFIX.length()).split("\\.")[0]);
        if (path.startsWith(SWAP)) {
            String[] p = path.substring(SWAP.length()).split("\\.");
            return p.length < 5 ? null : SettlementStyle.byId(p[2]);
        }
        return null;
    }

    /** Molde de troca "do zero": não se sabe como é a peça velha — varre o que for bloco de muralha e ergue a nova. */
    public static ResourceLocation scratchId(SettlementStyle newStyle, int newTier, String piece) {
        return IsekaiMahou.id(SWAP + ANY + ".0." + newStyle.id + "." + newTier + "." + piece);
    }

    /** Molde velho da peça de um molde de troca (para achar a altura do muro que já existe), ou null. */
    @Nullable
    public static Templates.Tpl oldOf(ServerLevel level, ResourceLocation swap) {
        if (!isSwap(swap)) return null;
        String[] p = swap.getPath().substring(SWAP.length()).split("\\.");
        if (p.length < 5) return null;
        SettlementStyle os = SettlementStyle.byId(p[0]);
        try {
            return Templates.get(level, any(os == null ? SettlementStyle.KINGDOM : os, Integer.parseInt(p[1]), p[4]));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Lista de troca: tira o velho de cima para baixo (só o que o novo não repete), depois põe o novo. */
    private static Templates.Tpl swap(@Nullable Templates.Tpl old, Templates.Tpl neu) {
        Map<BlockPos, BlockState> want = new HashMap<>();
        for (Templates.Entry e : neu.entries()) want.put(e.pos(), e.state());
        int sx = old == null ? neu.sx() : Math.max(old.sx(), neu.sx()), sz = old == null ? neu.sz() : Math.max(old.sz(), neu.sz());
        List<Templates.Entry> out = new ArrayList<>((old == null ? 0 : old.entries().size()) + neu.entries().size() + sx * sz);
        // 1) varredura: uma entrada por coluna da peça tira todo bloco de muralha acima do chão (qualquer estilo/grau)
        for (int x = 0; x < sx; x++) {
            for (int z = 0; z < sz; z++) {
                CompoundTag tag = new CompoundTag();
                tag.putString(OLD, SWEEP);
                tag.putInt(SWEEP_H, SWEEP_TOP);
                out.add(new Templates.Entry(new BlockPos(x, 1, z), Blocks.STRUCTURE_VOID.defaultBlockState(), tag));
            }
        }
        // 2) o que o molde velho tinha e NÃO é bloco de muralha (tochas, enfeites dos moldes .nbt): só sai se ainda for ele
        if (old != null) {
            for (int i = old.entries().size() - 1; i >= 0; i--) {
                Templates.Entry e = old.entries().get(i);
                if (e.state() == want.get(e.pos()) || e.pos().getY() >= 1 && isWallBlock(e.state())) continue;
                CompoundTag tag = new CompoundTag();
                tag.putString(OLD, BuiltInRegistries.BLOCK.getKey(e.state().getBlock()).toString());
                out.add(new Templates.Entry(e.pos(), Blocks.STRUCTURE_VOID.defaultBlockState(), tag));
            }
        }
        // 3) a peça nova
        out.addAll(neu.entries());
        return new Templates.Tpl(sx, old == null ? neu.sy() : Math.max(old.sy(), neu.sy()), sz, List.copyOf(out));
    }

    private static java.util.Set<net.minecraft.world.level.block.Block> wallBlocks;

    /**
     * O bloco é material de muralha de algum estilo/grau (corpo, base, ameias, tábuas, cercas, telhado, luz, estandarte,
     * grade do portão)? Dentro da faixa da muralha, é isso que uma reforma pode tirar ou cobrir — o que o jogador
     * pendurou de outro material fica.
     */
    public static synchronized boolean isWallBlock(BlockState st) {
        if (wallBlocks == null) {
            java.util.Set<net.minecraft.world.level.block.Block> set = new java.util.HashSet<>();
            set.add(Blocks.IRON_BARS);
            for (SettlementStyle style : SettlementStyle.values()) {
                set.add(style.portcullis().getBlock());
                for (int wt = 1; wt <= TierLook.WALL_MAX; wt++) {
                    TierLook.WallPal p = TierLook.wall(style, wt);
                    for (BlockState b : p.body) set.add(b.getBlock());
                    for (BlockState b : new BlockState[] {p.base, p.trim, p.merlon, p.merlonSlab, p.plank, p.fence, p.roofFull, p.light, p.rich, p.overgrowth}) {
                        if (b != null) set.add(b.getBlock());
                    }
                    set.add(p.roofStairs);
                    set.add(p.banner);
                }
            }
            set.remove(Blocks.AIR);
            wallBlocks = set;
        }
        return wallBlocks.contains(st.getBlock());
    }

    /** Varre uma coluna da peça (de {@code from} para cima): tira os blocos de muralha. Devolve quantos tirou. */
    public static int sweep(ServerLevel sl, BlockPos from, int height) {
        int n = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int y = Math.min(height, SWEEP_TOP + 4) - 1; y >= 0; y--) {
            BlockState cur = sl.getBlockState(m.set(from.getX(), from.getY() + y, from.getZ()));
            if (cur.isAir() || !isWallBlock(cur)) continue;
            sl.setBlock(m, Blocks.AIR.defaultBlockState(), 2 | 16);
            n++;
        }
        return n;
    }

    // =====================================================================================
    //  Onde está (e como é) a peça velha — onda 8: a reforma nunca pula peça em silêncio
    // =====================================================================================
    /** Resultado de {@link #resolveSwap}: terreno não carregado, chão resolvido, peça já no visual novo, molde trocado. */
    public static final int SWAP_WAIT = 0, SWAP_READY = 1, SWAP_DONE = 2, SWAP_REPLACED = 3;

    /**
     * Resolve a peça {@code idx} de uma reforma de muralha:
     * <ol>
     *   <li>a peça já é a nova (muralha erguida metade num estilo, metade no outro) → {@link #SWAP_DONE};</li>
     *   <li>o molde velho anotado bate com o mundo → usa a altura dele ({@link #SWAP_READY});</li>
     *   <li>não bate → procura qual estilo/grau está de pé ali e troca o molde da peça pelo de troca certo
     *       ({@link #SWAP_REPLACED});</li>
     *   <li>nada bate (peça desmontada ou muito mexida) → reconstrói a peça DO ZERO no lugar dela, na altura do terreno
     *       ao lado ({@link #SWAP_REPLACED}, molde {@link #scratchId}).</li>
     * </ol>
     *
     * @param flatGround cidade em chão plano feito pelo mod (Ilha Celeste, deque): a altura é sempre a do piso da praça
     */
    public static int resolveSwap(ServerLevel sl, Project p, int idx, int ground, boolean flatGround) {
        Project.Piece piece = p.pieces.get(idx);
        if (!sl.hasChunk(piece.cx >> 4, piece.cz >> 4)) return SWAP_WAIT;
        if (!isSwap(piece.template)) {
            // obra de reforma com molde comum (save editado): ergue no terreno, como uma peça nova
            piece.baseY = flatGround ? ground : neighbourBase(sl, p, idx, ground);
            return SWAP_READY;
        }
        String[] t = piece.template.getPath().substring(SWAP.length()).split("\\.");
        if (t.length < 5) return SWAP_DONE;
        SettlementStyle os = SettlementStyle.byId(t[0]), ns = SettlementStyle.byId(t[2]);
        if (ns == null) ns = SettlementStyle.KINGDOM;
        int ot, nt;
        try {
            ot = Integer.parseInt(t[1]);
            nt = Integer.parseInt(t[3]);
        } catch (NumberFormatException e) {
            return SWAP_DONE;
        }
        String name = t[4];
        // molde "do zero" já escolhido numa passada anterior (a altura veio junto): nada a procurar
        if (ANY.equals(t[0])) {
            piece.baseY = flatGround ? ground : neighbourBase(sl, p, idx, ground);
            return SWAP_READY;
        }
        Templates.Tpl neu = build(ns, nt, name);
        // 1) já é a peça nova?
        int[] m = match(sl, neu, piece, ground);
        if (m[1] * 5 >= m[2] * 4 && same(sl, neu, piece, m[0]) >= 0.92F) return SWAP_DONE;
        // 2) o molde velho anotado
        Templates.Tpl old = os == null ? null : Templates.get(sl, any(os, ot, name));
        if (old != null) {
            m = match(sl, old, piece, ground);
            if (m[1] >= Math.max(5, m[2] / 3)) {
                piece.baseY = m[0];
                return SWAP_READY;
            }
        }
        // 3) qual estilo/grau está de pé ali?
        SettlementStyle bestStyle = null;
        int bestTier = 0, bestY = Integer.MIN_VALUE;
        float best = 0.5F;
        for (SettlementStyle st : SettlementStyle.values()) {
            for (int wt = 0; wt <= TierLook.WALL_MAX; wt++) {
                if (st == os && wt == ot || st == ns && wt == nt) continue;
                Templates.Tpl cand = Templates.get(sl, any(st, wt, name));
                if (cand == null) continue;
                m = match(sl, cand, piece, ground);
                float ratio = m[2] == 0 ? 0F : m[1] / (float) m[2];
                if (m[1] >= 5 && ratio > best) {
                    best = ratio;
                    bestStyle = st;
                    bestTier = wt;
                    bestY = m[0];
                }
            }
        }
        int y;
        ResourceLocation id;
        if (bestStyle != null) {
            y = bestY;
            id = swapId(bestStyle, bestTier, ns, nt, name);
        } else {
            // 4) do zero: mesma altura da peça vizinha já resolvida, senão o terreno ao lado da faixa da muralha
            y = flatGround ? ground : neighbourBase(sl, p, idx, ground);
            id = scratchId(ns, nt, name);
        }
        p.pieces.set(idx, new Project.Piece(id, piece.cx, piece.cz, piece.rotation, piece.kind, y));
        return SWAP_REPLACED;
    }

    /** Fração dos blocos do molde que estão no mundo com a peça na altura y (confere TODOS, não uma amostra). */
    private static float same(ServerLevel sl, Templates.Tpl tpl, Project.Piece piece, int y) {
        int w = tpl.width(piece.rotation), d = tpl.depth(piece.rotation);
        int x0 = piece.cx - w / 2, z0 = piece.cz - d / 2, hit = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (Templates.Entry e : tpl.entries()) {
            BlockPos r = tpl.rotate(e.pos().getX(), e.pos().getY(), e.pos().getZ(), piece.rotation);
            if (sl.getBlockState(m.set(x0 + r.getX(), y + r.getY(), z0 + r.getZ())).is(e.state().getBlock())) hit++;
        }
        return tpl.entries().isEmpty() ? 0F : hit / (float) tpl.entries().size();
    }

    private static int neighbourBase(ServerLevel sl, Project p, int idx, int ground) {
        Project.Piece piece = p.pieces.get(idx);
        int best = Integer.MAX_VALUE, y = Integer.MIN_VALUE;
        for (int i = 0; i < p.pieces.size(); i++) {
            Project.Piece o = p.pieces.get(i);
            if (i == idx || !o.resolved()) continue;
            int d = Math.abs(o.cx - piece.cx) + Math.abs(o.cz - piece.cz);
            if (d <= 12 && d < best) {
                best = d;
                y = o.baseY;
            }
        }
        if (y != Integer.MIN_VALUE) return y;
        // terreno dos dois lados da faixa (fora da planta da peça): mediana, a no máximo 10 do piso da praça
        int[][] pts = {{4, 4}, {-4, 4}, {4, -4}, {-4, -4}, {0, 4}, {0, -4}, {4, 0}, {-4, 0}};
        int[] hs = new int[pts.length];
        int n = 0;
        for (int[] o : pts) {
            int x = piece.cx + o[0], z = piece.cz + o[1];
            if (!sl.hasChunk(x >> 4, z >> 4)) continue;
            hs[n++] = sl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        }
        if (n == 0) return ground;
        java.util.Arrays.sort(hs, 0, n);
        return Math.max(ground - 10, Math.min(ground + 10, hs[n / 2]));
    }

    /**
     * Altura (camada y=0 do molde) em que o muro VELHO desta peça está de pé: compara uma amostra do molde velho com o
     * mundo, de {@code around-14} a {@code around+14}. {@link Integer#MIN_VALUE} = não achou (muro desmontado/alterado
     * demais): a reforma pula a peça em vez de adivinhar.
     */
    public static int findBase(ServerLevel sl, Templates.Tpl old, Project.Piece piece, int around) {
        int[] m = match(sl, old, piece, around);
        return m[1] >= Math.max(5, m[2] / 3) ? m[0] : Integer.MIN_VALUE;
    }

    /** Compara uma amostra do molde com o mundo: {melhor altura, blocos que bateram, tamanho da amostra}. */
    public static int[] match(ServerLevel sl, Templates.Tpl old, Project.Piece piece, int around) {
        int w = old.width(piece.rotation), d = old.depth(piece.rotation);
        int x0 = piece.cx - w / 2, z0 = piece.cz - d / 2;
        List<Templates.Entry> all = old.entries();
        int stride = Math.max(1, all.size() / 48);
        int best = 0, bestY = Integer.MIN_VALUE, samples = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int y = around - 14; y <= around + 14; y++) {
            int hit = 0;
            samples = 0;
            for (int i = 0; i < all.size(); i += stride) {
                Templates.Entry e = all.get(i);
                BlockPos r = old.rotate(e.pos().getX(), e.pos().getY(), e.pos().getZ(), piece.rotation);
                samples++;
                if (sl.getBlockState(m.set(x0 + r.getX(), y + r.getY(), z0 + r.getZ())).is(e.state().getBlock())) hit++;
            }
            if (hit > best) {
                best = hit;
                bestY = y;
            }
        }
        return new int[] {bestY, best, samples};
    }

    // =====================================================================================
    //  Geração
    // =====================================================================================
    public static Templates.Tpl build(SettlementStyle style, int wallTier, String piece) {
        int wt = Math.max(1, Math.min(TierLook.WALL_MAX, wallTier));
        String key = style.id + "." + wt + "." + piece;
        synchronized (CACHE) {
            Templates.Tpl hit = CACHE.get(key);
            if (hit != null) return hit;
        }
        TierLook.WallPal p = TierLook.wall(style, wt);
        G g = switch (piece) {
            case TOWER -> tower(p, wt);
            case GATE -> gatehouse(p, wt);
            default -> segment(p, wt);
        };
        Templates.Tpl t = g.tpl();
        synchronized (CACHE) {
            CACHE.put(key, t);
        }
        return t;
    }

    /** Esquece os moldes gerados (um estilo trocou a paleta com {@link TierLook#register}). */
    public static void clear() {
        synchronized (CACHE) {
            CACHE.clear();
        }
    }

    /** Grade de montagem (o último bloco posto numa posição vence). */
    private static final class G {
        final int sx, sz;
        final Map<BlockPos, BlockState> m = new LinkedHashMap<>();

        G(int sx, int sz) {
            this.sx = sx;
            this.sz = sz;
        }

        void set(int x, int y, int z, @Nullable BlockState st) {
            if (x < 0 || z < 0 || x >= sx || z >= sz || y < 0) return;
            BlockPos pos = new BlockPos(x, y, z);
            if (st == null) m.remove(pos);
            else m.put(pos, st);
        }

        boolean edge(int x, int z) {
            return x == 0 || z == 0 || x == sx - 1 || z == sz - 1;
        }

        boolean corner(int x, int z) {
            return (x == 0 || x == sx - 1) && (z == 0 || z == sz - 1);
        }

        Templates.Tpl tpl() {
            int maxY = 0;
            List<Templates.Entry> list = new ArrayList<>(m.size());
            for (Map.Entry<BlockPos, BlockState> e : m.entrySet()) {
                maxY = Math.max(maxY, e.getKey().getY());
                list.add(new Templates.Entry(e.getKey(), e.getValue(), null));
            }
            return Templates.fromEntries(sx, maxY + 1, sz, list);
        }
    }

    private static BlockState body(TierLook.WallPal p, int x, int y, int z) {
        int h = x * 73856093 ^ y * 19349663 ^ z * 83492791;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return p.body[Math.floorMod(h, p.body.length)];
    }

    private static BlockState axisX(BlockState st) {
        return st.hasProperty(BlockStateProperties.AXIS) ? st.setValue(BlockStateProperties.AXIS, Direction.Axis.X) : st;
    }

    private static BlockState light(TierLook.WallPal p, boolean hanging) {
        return p.light.hasProperty(LanternBlock.HANGING) ? p.light.setValue(LanternBlock.HANGING, hanging) : p.light;
    }

    /** Altura do muro (topo do corpo) por grau. */
    private static int wallHeight(int wt) {
        // onda 8 (city_looks): o muro real ficou 1 bloco mais alto que antes (cada grau bem diferente do anterior)
        return new int[] {0, 4, 5, 7, 9}[wt];
    }

    /** Trecho de 9 (x) por 3 (z). */
    private static G segment(TierLook.WallPal p, int wt) {
        G g = new G(9, 3);
        if (wt == 1) {
            for (int x = 0; x < 9; x++) {
                g.set(x, 0, 0, p.base);
                g.set(x, 0, 2, p.base);
                // troncos em pé com as pontas desencontradas
                int top = x % 2 == 0 ? 5 : 4;
                for (int y = 0; y <= top; y++) g.set(x, y, 1, body(p, x, 0, 1));
            }
            // escoras dos dois lados e uma lanterna no meio
            for (int x : new int[] {1, 4, 7}) {
                g.set(x, 1, 0, p.fence);
                g.set(x, 1, 2, p.fence);
            }
            g.set(4, 2, 0, light(p, false));
            g.set(4, 2, 2, light(p, false));
            return g;
        }
        int h = wallHeight(wt);
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 3; z++) {
                for (int y = 0; y <= h; y++) {
                    boolean face = z != 1;
                    BlockState st = y <= (wt >= 3 ? 1 : 0) ? p.base : wt >= 3 && face && y == h - 1 ? p.trim : body(p, x, y, z);
                    // onda 8: muro real com contrafortes (colunas de destaque a cada 4 blocos nas duas faces)
                    if (wt >= 4 && face && x % 4 == 0 && y >= 2) st = p.trim;
                    g.set(x, y, z, st);
                }
                if (z != 1) {
                    // ameias: grau 2 alterna cheia/laje; grau 3+ tem ameias de 2 com vão de laje
                    boolean full = wt >= 3 ? x % 3 != 1 : x % 2 == 0;
                    g.set(x, h + 1, z, full ? p.merlon : p.merlonSlab);
                }
            }
        }
        if (p.overgrowth != null) {
            // Élfico: a floresta toma o muro — folhas nas ameias baixas, de um lado e do outro (desencontradas)
            for (int x = 1; x < 9; x += 3) {
                g.set(x, h + 1, 0, p.overgrowth);
                g.set(Math.min(8, x + 1), h + 1, 2, p.overgrowth);
            }
        }
        if (wt >= 3) {
            g.set(1, h + 1, 1, light(p, false));
            g.set(7, h + 1, 1, light(p, false));
            if (p.rich != null) {
                g.set(4, h - 1, 0, p.rich);
                g.set(4, h - 1, 2, p.rich);
            }
        } else {
            g.set(4, h + 1, 1, light(p, false));
        }
        return g;
    }

    /** Torre de canto 5×5. */
    private static G tower(TierLook.WallPal p, int wt) {
        G g = new G(5, 5);
        if (wt == 1) {
            for (int x = 0; x < 5; x++) {
                for (int z = 0; z < 5; z++) {
                    g.set(x, 0, z, p.base);
                    g.set(x, 5, z, p.plank);
                    if (!g.edge(x, z)) continue;
                    if (g.corner(x, z)) {
                        for (int y = 1; y <= 7; y++) g.set(x, y, z, body(p, x, 0, z));
                    } else {
                        for (int y = 1; y <= 4; y++) g.set(x, y, z, p.plank);
                        g.set(x, 6, z, p.fence);
                    }
                }
            }
            for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) g.set(x, 8, z, p.merlonSlab);
            g.set(2, 6, 2, light(p, false));
            if (p.spire) {
                g.set(2, 9, 2, p.fence);
                g.set(2, 10, 2, Blocks.IRON_BARS.defaultBlockState());
            }
            return g;
        }
        int h = wt == 2 ? 8 : wt == 3 ? 11 : 13;
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                boolean edge = g.edge(x, z), corner = g.corner(x, z);
                g.set(x, 0, z, p.base);
                g.set(x, h, z, edge ? body(p, x, h, z) : p.plank);
                if (!edge) continue;
                for (int y = 1; y < h; y++) {
                    g.set(x, y, z, wt >= 3 && y == 1 ? p.base : corner && y % 3 == 0 ? p.trim : body(p, x, y, z));
                }
                if (wt == 2) {
                    if ((x + z) % 2 == 0) g.set(x, h + 1, z, p.merlon);
                } else if (corner) {
                    // postes de canto seguram o telhado
                    g.set(x, h + 1, z, p.fence);
                    g.set(x, h + 2, z, p.fence);
                } else if ((x + z) % 2 == 1) {
                    g.set(x, h + 1, z, p.merlon);
                }
            }
        }
        // seteiras no meio de cada lado
        int slit = h - 3;
        BlockState bars = Blocks.IRON_BARS.defaultBlockState();
        g.set(2, slit, 0, bars);
        g.set(2, slit, 4, bars);
        g.set(0, slit, 2, bars);
        g.set(4, slit, 2, bars);
        if (wt >= 4) {
            // onda 8: torre real — segunda fiada de seteiras, mais baixa
            g.set(2, slit - 4, 0, bars);
            g.set(2, slit - 4, 4, bars);
            g.set(0, slit - 4, 2, bars);
            g.set(4, slit - 4, 2, bars);
        }
        if (wt == 2) {
            g.set(2, h + 1, 2, light(p, false));
            return g;
        }
        // telhado em pirâmide: anel 5×5, anel 3×3 e o bloco do topo (lanterna pendurada por baixo)
        roofRing(g, p, 0, 4, h + 3);
        roofRing(g, p, 1, 3, h + 4);
        g.set(2, h + 5, 2, p.roofFull);
        g.set(2, h + 4, 2, light(p, true));
        if (p.rich != null) g.set(2, h + 6, 2, p.rich);
        if (wt >= 4 && !p.spire) {
            // onda 8: mastro com o estandarte da cidade no alto da torre real
            int y = h + (p.rich != null ? 7 : 6);
            g.set(2, y, 2, p.fence);
            g.set(2, y + 1, 2, p.banner.defaultBlockState());
        }
        if (p.spire) {
            // Sombrio: agulha — o telhado continua subindo fino até a ponta de ferro
            int y = h + (p.rich != null ? 7 : 6);
            g.set(2, y, 2, p.roofFull);
            g.set(2, y + 1, 2, p.fence);
            g.set(2, y + 2, 2, Blocks.IRON_BARS.defaultBlockState());
            g.set(2, y + 3, 2, Blocks.IRON_BARS.defaultBlockState());
        }
        if (p.overgrowth != null) {
            for (int[] c : new int[][] {{0, 2}, {4, 2}, {2, 0}, {2, 4}}) g.set(c[0], h + 1, c[1], p.overgrowth);
        }
        return g;
    }

    /** Anel de escadas de telhado subindo para o centro (os cantos se ajeitam sozinhos no acabamento da obra). */
    private static void roofRing(G g, TierLook.WallPal p, int a, int b, int y) {
        for (int x = a; x <= b; x++) {
            for (int z = a; z <= b; z++) {
                if (x != a && x != b && z != a && z != b) continue;
                Direction f = z == a ? Direction.SOUTH : z == b ? Direction.NORTH : x == a ? Direction.EAST : Direction.WEST;
                g.set(x, y, z, p.roofStairs.defaultBlockState().setValue(StairBlock.FACING, f));
            }
        }
    }

    /** Portão 7 (x) por 3 (z); o vão fica livre de y=1 até a altura ({@link #gate}). */
    private static G gatehouse(TierLook.WallPal p, int wt) {
        G g = new G(7, 3);
        int[] o = gate(wt);
        int x0 = o[0], x1 = o[1], gh = o[2];
        if (wt == 1) {
            for (int x = 0; x < 7; x++) {
                for (int z = 0; z < 3; z++) {
                    g.set(x, 0, z, p.base);
                    if (x < x0 || x > x1) {
                        for (int y = 1; y <= 6; y++) g.set(x, y, z, x == 0 || x == 6 || z == 1 ? body(p, x, 0, z) : p.plank);
                    } else {
                        g.set(x, gh + 1, z, axisX(p.trim));
                        g.set(x, gh + 2, z, p.plank);
                        g.set(x, 6, z, p.plank);
                    }
                    if (z != 1) g.set(x, 7, z, p.fence);
                }
            }
            if (p.torii) {
                torii(g, p, gh, 6, 8);
                g.set(3, 7, 1, light(p, false));
                return g;
            }
            g.set(1, 7, 1, p.banner.defaultBlockState());
            g.set(5, 7, 1, p.banner.defaultBlockState());
            g.set(3, 7, 1, light(p, false));
            return g;
        }
        int h = wt == 2 ? 8 : wt == 3 ? 10 : 12;
        for (int x = 0; x < 7; x++) {
            for (int z = 0; z < 3; z++) {
                for (int y = 0; y <= h; y++) {
                    if (y >= 1 && y <= gh && x >= x0 && x <= x1) continue;
                    boolean side = x == 0 || x == 6;
                    g.set(x, y, z, y == 0 ? p.base : side && y % 3 == 0 ? p.trim : wt >= 3 && z != 1 && y == h - 1 ? p.trim : body(p, x, y, z));
                }
                if (z != 1) g.set(x, h + 1, z, x % 2 == 0 ? p.merlon : p.merlonSlab);
            }
        }
        // pedra-chave em cima do vão, dos dois lados
        BlockState key = p.rich != null ? p.rich : p.trim;
        g.set(3, gh + 1, 0, key);
        g.set(3, gh + 1, 2, key);
        // onda 8 (city_looks): portões cada vez mais imponentes — torreões nas duas pontas (mais altos a cada grau, com
        // luz no topo), cantos do vão em arco (grau 3+) e uma luz pendurada no meio da passagem (grau 4)
        int up = p.torii ? 0 : wt - 1;
        for (int x : new int[] {0, 6}) {
            if (up == 0) break;
            for (int z = 0; z < 3; z++) {
                for (int y = h + 1; y <= h + up; y++) g.set(x, y, z, z == 1 ? body(p, x, y, z) : y == h + up ? p.merlon : p.trim);
            }
            g.set(x, h + up + 1, 1, light(p, false));
        }
        if (wt >= 3 && !p.torii && p.merlonSlab.hasProperty(SlabBlock.TYPE)) {
            BlockState arch = p.merlonSlab.setValue(SlabBlock.TYPE, SlabType.TOP);
            // só nas duas faces: o plano do meio (z = 1) é o da grade do portão
            for (int z = 0; z < 3; z += 2) {
                g.set(x0, gh, z, arch);
                g.set(x1, gh, z, arch);
            }
        }
        if (wt >= 4 && !p.torii) {
            g.set(3, gh, 0, light(p, true));
            g.set(3, gh, 2, light(p, true));
        }
        if (p.torii) {
            torii(g, p, gh, h, h + 2);
            g.set(3, gh + 1, 0, key);
            g.set(3, gh + 1, 2, key);
            g.set(1, h + 1, 1, light(p, false));
            g.set(5, h + 1, 1, light(p, false));
            return g;
        }
        g.set(1, h + 1, 1, p.banner.defaultBlockState());
        g.set(5, h + 1, 1, p.banner.defaultBlockState());
        g.set(3, h + 1, 1, light(p, false));
        if (p.spire) {
            // Sombrio: frontão pontudo no meio do portão, com ponta de ferro
            g.set(3, h + 1, 0, p.merlon);
            g.set(3, h + 1, 2, p.merlon);
            g.set(3, h + 2, 0, p.fence);
            g.set(3, h + 2, 2, p.fence);
            g.set(0, h + 2, 1, Blocks.IRON_BARS.defaultBlockState());
            g.set(6, h + 2, 1, Blocks.IRON_BARS.defaultBlockState());
        }
        return g;
    }

    /**
     * TORII (Oriental): os pilares das pontas e a travessa sobre o vão ficam na cor de destaque (vermelho) nas duas faces,
     * e por cima do portão vai o telhadinho curvo — lajes no meio e as pontas meio bloco mais altas.
     *
     * @param gh   altura do vão
     * @param top  última fiada cheia do portão
     * @param roof altura do telhadinho
     */
    private static void torii(G g, TierLook.WallPal p, int gh, int top, int roof) {
        for (int z : new int[] {0, 2}) {
            for (int y = 1; y <= top; y++) {
                g.set(0, y, z, p.trim);
                g.set(6, y, z, p.trim);
            }
            for (int x = 0; x < 7; x++) g.set(x, gh + 1, z, axisX(p.trim));
        }
        BlockState low = p.merlonSlab.hasProperty(SlabBlock.TYPE) ? p.merlonSlab.setValue(SlabBlock.TYPE, SlabType.BOTTOM) : p.merlonSlab;
        BlockState high = p.merlonSlab.hasProperty(SlabBlock.TYPE) ? p.merlonSlab.setValue(SlabBlock.TYPE, SlabType.TOP) : p.merlonSlab;
        for (int x = 0; x < 7; x++) {
            for (int z = 0; z < 3; z++) g.set(x, roof, z, x == 0 || x == 6 ? high : low);
        }
    }

    private TierWalls() {}
}
