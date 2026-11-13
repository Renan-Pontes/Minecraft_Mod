package com.renanpontes.isekaimahou.feature.settlements.build;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.renanpontes.isekaimahou.feature.settlements.SettlementStyle;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A CARA de cada estilo em cada DEGRAU da cidade (onda 5 — "uma mudança de lvl mais significativa").
 * <p><b>É aqui que um estilo novo se pluga</b>: monte um {@link Look} (ruas, postes, monumento da praça, acabamento das
 * fachadas e as 4 paletas de muralha) e chame {@link #register}. Tudo o que muda com o degrau — {@code CityTerrain}
 * (rua e postes das obras novas), {@code CityRenewal} (a reforma da cidade inteira) e {@link TierWalls} (muralha,
 * torres e portão) — lê daqui e de mais nenhum lugar. Estilo sem aparência registrada usa a do Reino (nunca quebra).
 * <p>Índices: os vetores "por degrau" têm 6 posições e usam 1..5 (Acampamento, Vila, Burgo, Cidade, Capital); a
 * muralha tem 4 graus ({@link #wallTier}: paliçada, pedra, fortificada, real).
 */
public final class TierLook {
    public static final int MAX = 5;
    /** Graus de muralha: 1 paliçada, 2 pedra, 3 fortificada (torres com telhado, portão maior), 4 real. */
    public static final int WALL_MAX = 4;

    /** Paleta de UM grau de muralha (as formas são as mesmas para todo estilo: veja {@link TierWalls}). */
    public static final class WallPal {
        /** Corpo do muro (sorteado por posição). Na paliçada é o tronco em pé. */
        public BlockState[] body;
        /** Fiada de baixo. */
        public BlockState base;
        /** Faixa/cantos de destaque. */
        public BlockState trim;
        /** Ameia cheia e a meia-laje entre ameias. */
        public BlockState merlon, merlonSlab;
        /** Tábua (plataformas da paliçada) e cerca (guarda-corpo, postes do telhado). */
        public BlockState plank, fence;
        /** Telhado das torres (grau 3+): escada e bloco cheio. */
        public Block roofStairs;
        public BlockState roofFull;
        /** Luz em cima do muro e o detalhe rico do grau 4 (null = sem). */
        public BlockState light, rich;
        /** Estandarte em pé no topo do portão. */
        public Block banner;
        /** Portão em forma de TORII (Oriental): pilares e travessa de destaque, telhadinho curvo, sem estandartes. */
        public boolean torii;
        /** Torres com AGULHA no telhado (Sombrio). */
        public boolean spire;
        /** Folhas brotando do muro (Élfico): folha a pôr entre as ameias, ou null. */
        public BlockState overgrowth;
    }

    /** Tudo o que um estilo mostra em cada degrau. Campos públicos: monte e registre. */
    public static final class Look {
        /** Piso das ruas por degrau (sorteado por posição): terra → cascalho → pedra → calçamento → pavimento nobre. */
        public final BlockState[][] street = new BlockState[MAX + 1][];
        /** Poste por degrau, de baixo para cima (o último bloco é a luz). */
        public final BlockState[][] lamp = new BlockState[MAX + 1][];
        /** Meio-fio da praça por degrau (borda do lote do centro). */
        public final BlockState[] plazaEdge = new BlockState[MAX + 1];
        /** Monumento do coração: pilar, capitel e luz por degrau (altura em {@link #pillarHeight}). */
        public final BlockState[] pillar = new BlockState[MAX + 1], cap = new BlockState[MAX + 1], light = new BlockState[MAX + 1];
        /** Fonte da praça (degrau 3+): borda (escada) e coluna do meio (muro; fica com água). */
        public Block fountainRim, fountainPost;
        /** Fachadas e jardins: sebe sob as janelas, flores do quintal, vasos, estandarte de parede e capacho. */
        public BlockState hedge;
        public BlockState[] flowers, pots;
        public Block wallBanner, banner, carpet;
        /** Muralha por grau (1..4). */
        public final WallPal[] wall = new WallPal[WALL_MAX + 1];
    }

    private static final Map<SettlementStyle, Look> LOOKS = new EnumMap<>(SettlementStyle.class);
    private static Set<Block> streets, lampParts, furniture;

    /** Registra (ou troca) a aparência de um estilo. */
    public static synchronized void register(SettlementStyle style, Look look) {
        LOOKS.put(style, look);
        streets = null;
        lampParts = null;
        furniture = null;
        TierWalls.clear();
    }

    public static synchronized Look of(SettlementStyle style) {
        Look l = LOOKS.get(style);
        return l != null ? l : LOOKS.get(SettlementStyle.KINGDOM);
    }

    public static int clamp(int tier) {
        return Math.max(1, Math.min(MAX, tier));
    }

    /** Grau da muralha de uma cidade no degrau {@code tier}: Vila = paliçada, Burgo = pedra, Cidade = fortificada, Capital = real. */
    public static int wallTier(int tier) {
        return Math.max(1, Math.min(WALL_MAX, clamp(tier) - 1));
    }

    /** Altura dos 4 pilares do monumento do coração em cada degrau (0 = ainda não tem). */
    public static int pillarHeight(int tier) {
        return new int[] {0, 0, 1, 3, 4, 5}[clamp(tier)];
    }

    // =====================================================================================
    //  Consultas usadas pelas obras
    // =====================================================================================
    public static BlockState street(SettlementStyle style, int tier, int hash) {
        BlockState[] p = of(style).street[clamp(tier)];
        return p[Math.floorMod(hash, p.length)];
    }

    /**
     * Onda 8 (city_looks): o DESENHO da rua por degrau. A rua tem 3 blocos: a faixa do meio (anel 8 do lote) e uma
     * beira de cada lado (anel 7). Acampamento = trilha de terra com as beiras de terra batida; Vila = cascalho com
     * meio-fio; Burgo = pedra com calçada; Cidade = avenida com calçada polida (as árvores vêm do {@code CityScenery});
     * Capital = pavimento nobre com uma pedra de mosaico a cada 4 no meio da rua. Só usa blocos que já são "piso de rua".
     *
     * @param ring anel do lote (7 = beira, 8 = meio; menor = calçada da porta)
     * @param a    deslocamento x no lote
     * @param b    deslocamento z no lote
     */
    public static BlockState pave(SettlementStyle style, int tier, int ring, int a, int b, int hash) {
        int t = clamp(tier);
        Look l = of(style);
        if (ring == 7) {
            if (t == 1) return (style == SettlementStyle.DESERT ? Blocks.SANDSTONE : Blocks.COARSE_DIRT).defaultBlockState();
            return l.plazaEdge[Math.min(t, 4)];
        }
        if (ring >= 8 && t >= 5 && Math.floorMod(a + b, 4) == 0) return l.plazaEdge[5];
        return street(style, t, hash);
    }

    public static BlockState[] lamp(SettlementStyle style, int tier) {
        return of(style).lamp[clamp(tier)];
    }

    public static WallPal wall(SettlementStyle style, int wallTier) {
        return of(style).wall[Math.max(1, Math.min(WALL_MAX, wallTier))];
    }

    /** O bloco é piso de rua de algum estilo/degrau? (a reforma só repavimenta o que já é rua) */
    public static synchronized boolean isStreet(BlockState st) {
        if (streets == null) {
            Set<Block> set = new HashSet<>(Set.of(Blocks.DIRT_PATH, Blocks.COARSE_DIRT, Blocks.GRAVEL));
            for (Look l : LOOKS.values()) {
                for (BlockState[] row : l.street) add(set, row);
                add(set, l.plazaEdge);
            }
            streets = set;
        }
        return streets.contains(st.getBlock());
    }

    /** O bloco é peça de poste de algum estilo/degrau? (poste antigo é trocado; outra coisa no lugar fica) */
    public static synchronized boolean isLampPart(BlockState st) {
        if (lampParts == null) {
            Set<Block> set = new HashSet<>(Set.of(Blocks.OAK_FENCE, Blocks.SPRUCE_FENCE, Blocks.SANDSTONE_WALL, Blocks.LANTERN,
                    Blocks.TORCH));
            for (Look l : LOOKS.values()) for (BlockState[] row : l.lamp) add(set, row);
            lampParts = set;
        }
        return lampParts.contains(st.getBlock());
    }

    public static boolean isLampLight(BlockState st) {
        return st.is(Blocks.LANTERN) || st.is(Blocks.TORCH) || st.is(Blocks.SOUL_LANTERN) || st.is(Blocks.END_ROD)
                || st.is(Blocks.SEA_LANTERN) || st.is(Blocks.GLOWSTONE) || st.is(Blocks.SOUL_TORCH) || st.is(Blocks.SHROOMLIGHT)
                || st.is(Blocks.OCHRE_FROGLIGHT) || st.is(Blocks.VERDANT_FROGLIGHT) || st.is(Blocks.PEARLESCENT_FROGLIGHT);
    }

    /** O bloco é enfeite que a reforma coloca (monumento, fonte, sebe, vaso, estandarte...)? Só estes saem na troca de degrau. */
    public static synchronized boolean isFurniture(BlockState st) {
        if (furniture == null) {
            Set<Block> set = new HashSet<>(Set.of(Blocks.LANTERN, Blocks.SEA_LANTERN, Blocks.TORCH));
            for (Look l : LOOKS.values()) {
                add(set, l.pillar);
                add(set, l.cap);
                add(set, l.light);
                add(set, l.flowers);
                add(set, l.pots);
                if (l.hedge != null) set.add(l.hedge.getBlock());
                for (Block b : new Block[] {l.fountainRim, l.fountainPost, l.wallBanner, l.banner, l.carpet}) if (b != null) set.add(b);
            }
            furniture = set;
        }
        // onda 8 (city_looks): mobília da rua e da praça (CityScenery) — lã das barracas e bancas, árvores, bancos,
        // estandartes, lajes das arcadas... Isto só vale para posições ANOTADAS pela reforma (Settlement.tierBlocks).
        return furniture.contains(st.getBlock()) || st.is(net.minecraft.tags.BlockTags.WOOL) || st.is(net.minecraft.tags.BlockTags.LOGS)
                || st.is(net.minecraft.tags.BlockTags.LEAVES) || st.is(net.minecraft.tags.BlockTags.BANNERS)
                || st.is(net.minecraft.tags.BlockTags.STAIRS) || st.is(net.minecraft.tags.BlockTags.SLABS)
                || st.is(net.minecraft.tags.BlockTags.FENCES) || st.is(net.minecraft.tags.BlockTags.WALLS)
                || st.is(net.minecraft.tags.BlockTags.FLOWER_POTS) || st.is(Blocks.CAMPFIRE) || st.is(Blocks.SOUL_CAMPFIRE)
                || st.is(Blocks.BARREL) || st.is(Blocks.COMPOSTER) || st.is(Blocks.HAY_BLOCK) || st.is(Blocks.MELON)
                || st.is(Blocks.WATER_CAULDRON) || isLampLight(st) || isStreetAccent(st);
    }

    /** Blocos de pedestal/capitel que também são piso de rua (o pedestal da estátua da praça). */
    private static boolean isStreetAccent(BlockState st) {
        for (Look l : LOOKS.values()) {
            for (BlockState b : l.plazaEdge) if (b != null && st.is(b.getBlock())) return true;
        }
        return false;
    }

    private static void add(Set<Block> set, BlockState[] row) {
        if (row == null) return;
        for (BlockState st : row) if (st != null) set.add(st.getBlock());
    }

    // =====================================================================================
    //  Os 3 estilos de origem e os 4 da onda 5 (Oriental, Élfico, Sombrio, Anão)
    // =====================================================================================
    private static BlockState s(Block b) {
        return b.defaultBlockState();
    }

    /** Lista com pesos: {@code mix(A, 3, B, 1)} = A três vezes mais comum que B. */
    public static BlockState[] mix(Object... pairs) {
        java.util.List<BlockState> out = new java.util.ArrayList<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            for (int k = 0; k < (Integer) pairs[i + 1]; k++) out.add(((Block) pairs[i]).defaultBlockState());
        }
        return out.toArray(new BlockState[0]);
    }

    /** Coluna de poste: blocos de baixo para cima. */
    public static BlockState[] col(Block... blocks) {
        BlockState[] out = new BlockState[blocks.length];
        for (int i = 0; i < blocks.length; i++) out[i] = blocks[i].defaultBlockState();
        return out;
    }

    public static BlockState leaves(Block b) {
        BlockState st = b.defaultBlockState();
        return st.hasProperty(LeavesBlock.PERSISTENT) ? st.setValue(LeavesBlock.PERSISTENT, true) : st;
    }

    /**
     * Paleta de um grau de muralha. {@code body} com pesos ({@link #mix}); o resto são blocos simples.
     */
    public static WallPal wallPal(BlockState[] body, Block base, Block trim, Block merlon, Block merlonSlab, Block plank, Block fence,
                                  Block roofStairs, Block roofFull, Block light, Block rich, Block banner) {
        WallPal w = new WallPal();
        w.body = body;
        w.base = s(base);
        w.trim = s(trim);
        w.merlon = s(merlon);
        w.merlonSlab = s(merlonSlab);
        w.plank = s(plank);
        w.fence = s(fence);
        w.roofStairs = roofStairs;
        w.roofFull = s(roofFull);
        w.light = s(light);
        w.rich = rich == null ? null : s(rich);
        w.banner = banner;
        return w;
    }

    static {
        // ---------------------------------------------------------------- Reino
        Look k = new Look();
        k.street[1] = mix(Blocks.DIRT_PATH, 5, Blocks.COARSE_DIRT, 1);
        k.street[2] = mix(Blocks.GRAVEL, 3, Blocks.DIRT_PATH, 2, Blocks.COARSE_DIRT, 1);
        k.street[3] = mix(Blocks.COBBLESTONE, 4, Blocks.ANDESITE, 1, Blocks.MOSSY_COBBLESTONE, 1);
        k.street[4] = mix(Blocks.STONE_BRICKS, 4, Blocks.COBBLESTONE, 1, Blocks.ANDESITE, 1);
        k.street[5] = mix(Blocks.POLISHED_ANDESITE, 4, Blocks.STONE_BRICKS, 2, Blocks.POLISHED_DIORITE, 1);
        k.lamp[1] = col(Blocks.OAK_FENCE, Blocks.TORCH);
        k.lamp[2] = col(Blocks.OAK_FENCE, Blocks.OAK_FENCE, Blocks.LANTERN);
        k.lamp[3] = col(Blocks.COBBLESTONE_WALL, Blocks.OAK_FENCE, Blocks.OAK_FENCE, Blocks.LANTERN);
        k.lamp[4] = col(Blocks.STONE_BRICK_WALL, Blocks.STONE_BRICK_WALL, Blocks.OAK_FENCE, Blocks.LANTERN);
        k.lamp[5] = col(Blocks.CHISELED_STONE_BRICKS, Blocks.STONE_BRICK_WALL, Blocks.STONE_BRICK_WALL, Blocks.LANTERN);
        fill(k.plazaEdge, Blocks.DIRT_PATH, Blocks.COBBLESTONE, Blocks.STONE_BRICKS, Blocks.POLISHED_ANDESITE, Blocks.CHISELED_STONE_BRICKS);
        fill(k.pillar, Blocks.STONE_BRICK_WALL, Blocks.STONE_BRICK_WALL, Blocks.STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.POLISHED_ANDESITE);
        fill(k.cap, Blocks.STONE_BRICK_WALL, Blocks.STONE_BRICK_WALL, Blocks.CHISELED_STONE_BRICKS, Blocks.CHISELED_STONE_BRICKS, Blocks.GOLD_BLOCK);
        fill(k.light, Blocks.LANTERN, Blocks.LANTERN, Blocks.LANTERN, Blocks.LANTERN, Blocks.SEA_LANTERN);
        k.fountainRim = Blocks.STONE_BRICK_STAIRS;
        k.fountainPost = Blocks.STONE_BRICK_WALL;
        k.hedge = leaves(Blocks.FLOWERING_AZALEA_LEAVES);
        k.flowers = col(Blocks.POPPY, Blocks.CORNFLOWER, Blocks.DANDELION, Blocks.OXEYE_DAISY);
        k.pots = col(Blocks.POTTED_RED_TULIP, Blocks.POTTED_CORNFLOWER, Blocks.POTTED_OXEYE_DAISY);
        k.wallBanner = Blocks.BLUE_WALL_BANNER;
        k.banner = Blocks.BLUE_BANNER;
        k.carpet = Blocks.BLUE_CARPET;
        k.wall[1] = wallPal(mix(Blocks.OAK_LOG, 3, Blocks.SPRUCE_LOG, 1), Blocks.COBBLESTONE, Blocks.STRIPPED_OAK_LOG, Blocks.OAK_LOG,
                Blocks.OAK_SLAB, Blocks.OAK_PLANKS, Blocks.OAK_FENCE, Blocks.OAK_STAIRS, Blocks.OAK_PLANKS, Blocks.LANTERN, null, Blocks.BLUE_BANNER);
        k.wall[2] = wallPal(mix(Blocks.COBBLESTONE, 3, Blocks.STONE_BRICKS, 2, Blocks.MOSSY_COBBLESTONE, 1), Blocks.COBBLESTONE,
                Blocks.STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.STONE_BRICK_SLAB, Blocks.SPRUCE_PLANKS, Blocks.OAK_FENCE,
                Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_PLANKS, Blocks.LANTERN, null, Blocks.BLUE_BANNER);
        k.wall[3] = wallPal(mix(Blocks.STONE_BRICKS, 5, Blocks.CRACKED_STONE_BRICKS, 1, Blocks.COBBLESTONE, 1), Blocks.POLISHED_ANDESITE,
                Blocks.CHISELED_STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.STONE_BRICK_SLAB, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_FENCE,
                Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_PLANKS, Blocks.LANTERN, null, Blocks.BLUE_BANNER);
        k.wall[4] = wallPal(mix(Blocks.STONE_BRICKS, 6, Blocks.POLISHED_ANDESITE, 1), Blocks.POLISHED_ANDESITE,
                Blocks.CHISELED_STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.STONE_BRICK_SLAB, Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_FENCE,
                Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILES, Blocks.LANTERN, Blocks.GOLD_BLOCK, Blocks.BLUE_BANNER);
        register(SettlementStyle.KINGDOM, k);

        // ---------------------------------------------------------------- Desértico
        Look d = new Look();
        d.street[1] = mix(Blocks.SANDSTONE, 2, Blocks.SMOOTH_SANDSTONE, 1);
        d.street[2] = mix(Blocks.SMOOTH_SANDSTONE, 4, Blocks.SANDSTONE, 1);
        d.street[3] = mix(Blocks.SMOOTH_SANDSTONE, 3, Blocks.CUT_SANDSTONE, 1);
        d.street[4] = mix(Blocks.CUT_SANDSTONE, 4, Blocks.SMOOTH_SANDSTONE, 1, Blocks.ORANGE_TERRACOTTA, 1);
        d.street[5] = mix(Blocks.CUT_SANDSTONE, 4, Blocks.CUT_RED_SANDSTONE, 2, Blocks.CHISELED_SANDSTONE, 1);
        d.lamp[1] = col(Blocks.SANDSTONE_WALL, Blocks.TORCH);
        d.lamp[2] = col(Blocks.SANDSTONE_WALL, Blocks.SANDSTONE_WALL, Blocks.LANTERN);
        d.lamp[3] = col(Blocks.SANDSTONE_WALL, Blocks.SANDSTONE_WALL, Blocks.SANDSTONE_WALL, Blocks.LANTERN);
        d.lamp[4] = col(Blocks.CUT_SANDSTONE, Blocks.SANDSTONE_WALL, Blocks.SANDSTONE_WALL, Blocks.LANTERN);
        d.lamp[5] = col(Blocks.CHISELED_SANDSTONE, Blocks.RED_SANDSTONE_WALL, Blocks.RED_SANDSTONE_WALL, Blocks.LANTERN);
        fill(d.plazaEdge, Blocks.SANDSTONE, Blocks.SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.ORANGE_TERRACOTTA, Blocks.CHISELED_SANDSTONE);
        fill(d.pillar, Blocks.SANDSTONE_WALL, Blocks.SANDSTONE_WALL, Blocks.CUT_SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.CUT_SANDSTONE);
        fill(d.cap, Blocks.SANDSTONE_WALL, Blocks.SANDSTONE_WALL, Blocks.CHISELED_SANDSTONE, Blocks.CHISELED_SANDSTONE, Blocks.GOLD_BLOCK);
        fill(d.light, Blocks.LANTERN, Blocks.LANTERN, Blocks.LANTERN, Blocks.LANTERN, Blocks.SEA_LANTERN);
        d.fountainRim = Blocks.SANDSTONE_STAIRS;
        d.fountainPost = Blocks.SANDSTONE_WALL;
        d.hedge = s(Blocks.POTTED_CACTUS);
        d.flowers = col(Blocks.DEAD_BUSH);
        d.pots = col(Blocks.POTTED_CACTUS, Blocks.POTTED_ORANGE_TULIP, Blocks.POTTED_DEAD_BUSH);
        d.wallBanner = Blocks.ORANGE_WALL_BANNER;
        d.banner = Blocks.ORANGE_BANNER;
        d.carpet = Blocks.ORANGE_CARPET;
        d.wall[1] = wallPal(mix(Blocks.ACACIA_LOG, 3, Blocks.STRIPPED_ACACIA_LOG, 1), Blocks.SANDSTONE, Blocks.STRIPPED_ACACIA_LOG,
                Blocks.ACACIA_LOG, Blocks.ACACIA_SLAB, Blocks.ACACIA_PLANKS, Blocks.ACACIA_FENCE, Blocks.ACACIA_STAIRS, Blocks.ACACIA_PLANKS,
                Blocks.LANTERN, null, Blocks.ORANGE_BANNER);
        d.wall[2] = wallPal(mix(Blocks.SANDSTONE, 3, Blocks.SMOOTH_SANDSTONE, 2), Blocks.CUT_SANDSTONE, Blocks.CHISELED_SANDSTONE,
                Blocks.CUT_SANDSTONE, Blocks.CUT_SANDSTONE_SLAB, Blocks.ACACIA_PLANKS, Blocks.ACACIA_FENCE, Blocks.SMOOTH_SANDSTONE_STAIRS,
                Blocks.SMOOTH_SANDSTONE, Blocks.LANTERN, null, Blocks.ORANGE_BANNER);
        d.wall[3] = wallPal(mix(Blocks.SMOOTH_SANDSTONE, 4, Blocks.CUT_SANDSTONE, 2), Blocks.CUT_SANDSTONE, Blocks.ORANGE_TERRACOTTA,
                Blocks.CUT_SANDSTONE, Blocks.CUT_SANDSTONE_SLAB, Blocks.ACACIA_PLANKS, Blocks.SANDSTONE_WALL, Blocks.SMOOTH_SANDSTONE_STAIRS,
                Blocks.CYAN_TERRACOTTA, Blocks.LANTERN, null, Blocks.ORANGE_BANNER);
        d.wall[4] = wallPal(mix(Blocks.CUT_SANDSTONE, 4, Blocks.SMOOTH_SANDSTONE, 2), Blocks.CUT_RED_SANDSTONE, Blocks.CHISELED_SANDSTONE,
                Blocks.CUT_SANDSTONE, Blocks.CUT_SANDSTONE_SLAB, Blocks.ACACIA_PLANKS, Blocks.SANDSTONE_WALL, Blocks.SMOOTH_SANDSTONE_STAIRS,
                Blocks.CYAN_TERRACOTTA, Blocks.LANTERN, Blocks.GOLD_BLOCK, Blocks.ORANGE_BANNER);
        register(SettlementStyle.DESERT, d);

        // ---------------------------------------------------------------- Nórdico
        Look n = new Look();
        n.street[1] = mix(Blocks.COARSE_DIRT, 3, Blocks.GRAVEL, 1);
        n.street[2] = mix(Blocks.GRAVEL, 3, Blocks.COARSE_DIRT, 1, Blocks.COBBLESTONE, 1);
        n.street[3] = mix(Blocks.COBBLESTONE, 3, Blocks.MOSSY_COBBLESTONE, 1, Blocks.GRAVEL, 1);
        n.street[4] = mix(Blocks.COBBLESTONE, 2, Blocks.STONE_BRICKS, 3, Blocks.MOSSY_COBBLESTONE, 1);
        n.street[5] = mix(Blocks.STONE_BRICKS, 3, Blocks.POLISHED_ANDESITE, 2, Blocks.COBBLED_DEEPSLATE, 1);
        n.lamp[1] = col(Blocks.SPRUCE_FENCE, Blocks.TORCH);
        n.lamp[2] = col(Blocks.SPRUCE_FENCE, Blocks.SPRUCE_FENCE, Blocks.LANTERN);
        n.lamp[3] = col(Blocks.COBBLESTONE_WALL, Blocks.SPRUCE_FENCE, Blocks.SPRUCE_FENCE, Blocks.LANTERN);
        n.lamp[4] = col(Blocks.COBBLESTONE_WALL, Blocks.COBBLESTONE_WALL, Blocks.SPRUCE_FENCE, Blocks.LANTERN);
        n.lamp[5] = col(Blocks.POLISHED_ANDESITE, Blocks.COBBLESTONE_WALL, Blocks.SPRUCE_FENCE, Blocks.LANTERN);
        fill(n.plazaEdge, Blocks.COARSE_DIRT, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.STONE_BRICKS, Blocks.POLISHED_ANDESITE);
        fill(n.pillar, Blocks.COBBLESTONE_WALL, Blocks.COBBLESTONE_WALL, Blocks.STRIPPED_SPRUCE_LOG, Blocks.STRIPPED_SPRUCE_LOG, Blocks.DARK_OAK_LOG);
        fill(n.cap, Blocks.COBBLESTONE_WALL, Blocks.COBBLESTONE_WALL, Blocks.POLISHED_ANDESITE, Blocks.POLISHED_ANDESITE, Blocks.WAXED_OXIDIZED_COPPER);
        fill(n.light, Blocks.LANTERN, Blocks.LANTERN, Blocks.LANTERN, Blocks.LANTERN, Blocks.SEA_LANTERN);
        n.fountainRim = Blocks.COBBLESTONE_STAIRS;
        n.fountainPost = Blocks.COBBLESTONE_WALL;
        n.hedge = leaves(Blocks.SPRUCE_LEAVES);
        n.flowers = col(Blocks.FERN, Blocks.LILY_OF_THE_VALLEY, Blocks.FERN, Blocks.AZURE_BLUET);
        n.pots = col(Blocks.POTTED_FERN, Blocks.POTTED_BLUE_ORCHID, Blocks.POTTED_SPRUCE_SAPLING);
        n.wallBanner = Blocks.CYAN_WALL_BANNER;
        n.banner = Blocks.CYAN_BANNER;
        n.carpet = Blocks.CYAN_CARPET;
        n.wall[1] = wallPal(mix(Blocks.SPRUCE_LOG, 4, Blocks.DARK_OAK_LOG, 1), Blocks.COBBLESTONE, Blocks.STRIPPED_SPRUCE_LOG,
                Blocks.SPRUCE_LOG, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_FENCE, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_PLANKS,
                Blocks.LANTERN, null, Blocks.CYAN_BANNER);
        n.wall[2] = wallPal(mix(Blocks.COBBLESTONE, 4, Blocks.MOSSY_COBBLESTONE, 1, Blocks.ANDESITE, 1), Blocks.COBBLESTONE,
                Blocks.SPRUCE_LOG, Blocks.COBBLESTONE, Blocks.COBBLESTONE_SLAB, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_FENCE,
                Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_PLANKS, Blocks.LANTERN, null, Blocks.CYAN_BANNER);
        n.wall[3] = wallPal(mix(Blocks.STONE_BRICKS, 3, Blocks.COBBLESTONE, 2, Blocks.MOSSY_STONE_BRICKS, 1), Blocks.COBBLESTONE,
                Blocks.DARK_OAK_LOG, Blocks.STONE_BRICKS, Blocks.STONE_BRICK_SLAB, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_FENCE,
                Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_PLANKS, Blocks.LANTERN, null, Blocks.CYAN_BANNER);
        n.wall[4] = wallPal(mix(Blocks.STONE_BRICKS, 4, Blocks.DEEPSLATE_BRICKS, 2), Blocks.POLISHED_ANDESITE, Blocks.DARK_OAK_LOG,
                Blocks.STONE_BRICKS, Blocks.STONE_BRICK_SLAB, Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_FENCE, Blocks.DARK_OAK_STAIRS,
                Blocks.DARK_OAK_PLANKS, Blocks.LANTERN, Blocks.WAXED_OXIDIZED_COPPER, Blocks.CYAN_BANNER);
        register(SettlementStyle.NORDIC, n);

        // ---------------------------------------------------------------- Oriental (onda 5)
        // madeira escura e vermelho, pedra clara, telha cinza, cerejeiras; portão TORII em toda muralha
        Look o = new Look();
        o.street[1] = mix(Blocks.DIRT_PATH, 5, Blocks.COARSE_DIRT, 1);
        o.street[2] = mix(Blocks.GRAVEL, 4, Blocks.COARSE_DIRT, 1, Blocks.DIRT_PATH, 1);
        o.street[3] = mix(Blocks.ANDESITE, 3, Blocks.COBBLESTONE, 2, Blocks.GRAVEL, 1);
        o.street[4] = mix(Blocks.POLISHED_ANDESITE, 4, Blocks.ANDESITE, 1, Blocks.STONE_BRICKS, 1);
        o.street[5] = mix(Blocks.POLISHED_ANDESITE, 3, Blocks.SMOOTH_STONE, 2, Blocks.POLISHED_GRANITE, 1);
        o.lamp[1] = col(Blocks.DARK_OAK_FENCE, Blocks.TORCH);
        o.lamp[2] = col(Blocks.DARK_OAK_FENCE, Blocks.DARK_OAK_FENCE, Blocks.LANTERN);
        o.lamp[3] = col(Blocks.ANDESITE_WALL, Blocks.DARK_OAK_FENCE, Blocks.DARK_OAK_FENCE, Blocks.LANTERN);
        o.lamp[4] = col(Blocks.POLISHED_ANDESITE, Blocks.ANDESITE_WALL, Blocks.MANGROVE_FENCE, Blocks.LANTERN);
        o.lamp[5] = col(Blocks.POLISHED_ANDESITE, Blocks.MANGROVE_FENCE, Blocks.MANGROVE_FENCE, Blocks.OCHRE_FROGLIGHT);
        fill(o.plazaEdge, Blocks.DIRT_PATH, Blocks.COBBLESTONE, Blocks.POLISHED_ANDESITE, Blocks.POLISHED_GRANITE, Blocks.SMOOTH_RED_SANDSTONE);
        fill(o.pillar, Blocks.MANGROVE_FENCE, Blocks.MANGROVE_FENCE, Blocks.STRIPPED_MANGROVE_LOG, Blocks.STRIPPED_MANGROVE_LOG, Blocks.STRIPPED_MANGROVE_LOG);
        fill(o.cap, Blocks.MANGROVE_FENCE, Blocks.MANGROVE_FENCE, Blocks.DARK_OAK_SLAB, Blocks.DEEPSLATE_TILE_SLAB, Blocks.GOLD_BLOCK);
        fill(o.light, Blocks.LANTERN, Blocks.LANTERN, Blocks.LANTERN, Blocks.LANTERN, Blocks.SHROOMLIGHT);
        o.fountainRim = Blocks.POLISHED_ANDESITE_STAIRS;
        o.fountainPost = Blocks.ANDESITE_WALL;
        o.hedge = leaves(Blocks.CHERRY_LEAVES);
        o.flowers = col(Blocks.PINK_TULIP, Blocks.ALLIUM, Blocks.PINK_PETALS, Blocks.AZURE_BLUET);
        o.pots = col(Blocks.POTTED_CHERRY_SAPLING, Blocks.POTTED_BAMBOO, Blocks.POTTED_PINK_TULIP, Blocks.POTTED_AZALEA);
        o.wallBanner = Blocks.RED_WALL_BANNER;
        o.banner = Blocks.RED_BANNER;
        o.carpet = Blocks.RED_CARPET;
        o.wall[1] = wallPal(mix(Blocks.DARK_OAK_LOG, 3, Blocks.SPRUCE_LOG, 1), Blocks.COBBLESTONE, Blocks.STRIPPED_MANGROVE_LOG,
                Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_FENCE, Blocks.DARK_OAK_STAIRS,
                Blocks.DARK_OAK_PLANKS, Blocks.LANTERN, null, Blocks.RED_BANNER);
        o.wall[2] = wallPal(mix(Blocks.ANDESITE, 3, Blocks.COBBLESTONE, 2, Blocks.STONE, 1), Blocks.COBBLESTONE,
                Blocks.STRIPPED_MANGROVE_LOG, Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_TILE_SLAB, Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_FENCE,
                Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_PLANKS, Blocks.LANTERN, null, Blocks.RED_BANNER);
        o.wall[3] = wallPal(mix(Blocks.WHITE_TERRACOTTA, 5, Blocks.CALCITE, 1), Blocks.POLISHED_ANDESITE,
                Blocks.STRIPPED_MANGROVE_LOG, Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_TILE_SLAB, Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_FENCE,
                Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILES, Blocks.LANTERN, null, Blocks.RED_BANNER);
        o.wall[4] = wallPal(mix(Blocks.WHITE_TERRACOTTA, 4, Blocks.SMOOTH_QUARTZ, 2), Blocks.POLISHED_DEEPSLATE,
                Blocks.STRIPPED_MANGROVE_LOG, Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_TILE_SLAB, Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_FENCE,
                Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILES, Blocks.LANTERN, Blocks.GOLD_BLOCK, Blocks.RED_BANNER);
        for (int i = 1; i <= WALL_MAX; i++) o.wall[i].torii = true;
        register(SettlementStyle.ORIENTAL, o);

        // ---------------------------------------------------------------- Élfico (onda 5)
        // madeira clara, pedra com musgo, cobre esverdeado, luzes suaves (lumes); folhas brotando do muro
        Look e = new Look();
        e.street[1] = mix(Blocks.DIRT_PATH, 5, Blocks.ROOTED_DIRT, 1);
        e.street[2] = mix(Blocks.DIRT_PATH, 3, Blocks.GRAVEL, 2, Blocks.COARSE_DIRT, 1);
        e.street[3] = mix(Blocks.MOSSY_COBBLESTONE, 3, Blocks.COBBLESTONE, 2, Blocks.ANDESITE, 1);
        e.street[4] = mix(Blocks.MOSSY_STONE_BRICKS, 3, Blocks.STONE_BRICKS, 3, Blocks.MOSSY_COBBLESTONE, 1);
        e.street[5] = mix(Blocks.STONE_BRICKS, 3, Blocks.MOSSY_STONE_BRICKS, 2, Blocks.POLISHED_DIORITE, 2);
        e.lamp[1] = col(Blocks.BIRCH_FENCE, Blocks.TORCH);
        e.lamp[2] = col(Blocks.BIRCH_FENCE, Blocks.BIRCH_FENCE, Blocks.LANTERN);
        e.lamp[3] = col(Blocks.MOSSY_COBBLESTONE_WALL, Blocks.BIRCH_FENCE, Blocks.BIRCH_FENCE, Blocks.VERDANT_FROGLIGHT);
        e.lamp[4] = col(Blocks.MOSSY_STONE_BRICK_WALL, Blocks.BIRCH_FENCE, Blocks.BIRCH_FENCE, Blocks.VERDANT_FROGLIGHT);
        e.lamp[5] = col(Blocks.CHISELED_STONE_BRICKS, Blocks.MOSSY_STONE_BRICK_WALL, Blocks.BIRCH_FENCE, Blocks.PEARLESCENT_FROGLIGHT);
        fill(e.plazaEdge, Blocks.DIRT_PATH, Blocks.MOSSY_COBBLESTONE, Blocks.MOSSY_STONE_BRICKS, Blocks.POLISHED_DIORITE, Blocks.CHISELED_STONE_BRICKS);
        fill(e.pillar, Blocks.BIRCH_FENCE, Blocks.BIRCH_FENCE, Blocks.STRIPPED_BIRCH_LOG, Blocks.STRIPPED_BIRCH_LOG, Blocks.QUARTZ_PILLAR);
        fill(e.cap, Blocks.BIRCH_FENCE, Blocks.BIRCH_FENCE, Blocks.MOSSY_STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.EMERALD_BLOCK);
        fill(e.light, Blocks.LANTERN, Blocks.LANTERN, Blocks.VERDANT_FROGLIGHT, Blocks.VERDANT_FROGLIGHT, Blocks.PEARLESCENT_FROGLIGHT);
        e.fountainRim = Blocks.MOSSY_STONE_BRICK_STAIRS;
        e.fountainPost = Blocks.MOSSY_STONE_BRICK_WALL;
        e.hedge = leaves(Blocks.AZALEA_LEAVES);
        e.flowers = col(Blocks.LILY_OF_THE_VALLEY, Blocks.AZURE_BLUET, Blocks.FERN, Blocks.BLUE_ORCHID);
        e.pots = col(Blocks.POTTED_AZALEA, Blocks.POTTED_FLOWERING_AZALEA, Blocks.POTTED_LILY_OF_THE_VALLEY, Blocks.POTTED_BIRCH_SAPLING);
        e.wallBanner = Blocks.LIME_WALL_BANNER;
        e.banner = Blocks.LIME_BANNER;
        e.carpet = Blocks.LIME_CARPET;
        e.wall[1] = wallPal(mix(Blocks.BIRCH_LOG, 3, Blocks.OAK_LOG, 1), Blocks.MOSSY_COBBLESTONE, Blocks.STRIPPED_BIRCH_LOG,
                Blocks.BIRCH_LOG, Blocks.BIRCH_SLAB, Blocks.BIRCH_PLANKS, Blocks.BIRCH_FENCE, Blocks.BIRCH_STAIRS, Blocks.BIRCH_PLANKS,
                Blocks.LANTERN, null, Blocks.LIME_BANNER);
        e.wall[2] = wallPal(mix(Blocks.MOSSY_COBBLESTONE, 3, Blocks.COBBLESTONE, 2, Blocks.MOSSY_STONE_BRICKS, 1), Blocks.MOSSY_COBBLESTONE,
                Blocks.STRIPPED_BIRCH_LOG, Blocks.MOSSY_STONE_BRICKS, Blocks.MOSSY_STONE_BRICK_SLAB, Blocks.BIRCH_PLANKS, Blocks.BIRCH_FENCE,
                Blocks.BIRCH_STAIRS, Blocks.BIRCH_PLANKS, Blocks.LANTERN, null, Blocks.LIME_BANNER);
        e.wall[3] = wallPal(mix(Blocks.STONE_BRICKS, 3, Blocks.MOSSY_STONE_BRICKS, 3), Blocks.POLISHED_DIORITE,
                Blocks.STRIPPED_BIRCH_LOG, Blocks.STONE_BRICKS, Blocks.STONE_BRICK_SLAB, Blocks.BIRCH_PLANKS, Blocks.BIRCH_FENCE,
                Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS, Blocks.WAXED_OXIDIZED_CUT_COPPER, Blocks.VERDANT_FROGLIGHT, null, Blocks.LIME_BANNER);
        e.wall[4] = wallPal(mix(Blocks.STONE_BRICKS, 3, Blocks.MOSSY_STONE_BRICKS, 2, Blocks.POLISHED_DIORITE, 2), Blocks.POLISHED_DIORITE,
                Blocks.QUARTZ_PILLAR, Blocks.STONE_BRICKS, Blocks.STONE_BRICK_SLAB, Blocks.BIRCH_PLANKS, Blocks.BIRCH_FENCE,
                Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS, Blocks.WAXED_OXIDIZED_CUT_COPPER, Blocks.VERDANT_FROGLIGHT, Blocks.EMERALD_BLOCK,
                Blocks.LIME_BANNER);
        for (int i = 2; i <= WALL_MAX; i++) e.wall[i].overgrowth = leaves(i >= 4 ? Blocks.FLOWERING_AZALEA_LEAVES : Blocks.AZALEA_LEAVES);
        register(SettlementStyle.ELVEN, e);

        // ---------------------------------------------------------------- Sombrio / Gótico (onda 5)
        // ardósia e pedra-negra, lanternas de alma, torres com agulha
        Look gt = new Look();
        gt.street[1] = mix(Blocks.COARSE_DIRT, 3, Blocks.GRAVEL, 1);
        gt.street[2] = mix(Blocks.GRAVEL, 3, Blocks.COARSE_DIRT, 1, Blocks.COBBLED_DEEPSLATE, 1);
        gt.street[3] = mix(Blocks.COBBLED_DEEPSLATE, 4, Blocks.COBBLESTONE, 1, Blocks.GRAVEL, 1);
        gt.street[4] = mix(Blocks.DEEPSLATE_BRICKS, 3, Blocks.COBBLED_DEEPSLATE, 2, Blocks.CRACKED_DEEPSLATE_BRICKS, 1);
        gt.street[5] = mix(Blocks.POLISHED_DEEPSLATE, 3, Blocks.DEEPSLATE_TILES, 2, Blocks.POLISHED_BLACKSTONE, 1);
        gt.lamp[1] = col(Blocks.DARK_OAK_FENCE, Blocks.SOUL_TORCH);
        gt.lamp[2] = col(Blocks.DARK_OAK_FENCE, Blocks.DARK_OAK_FENCE, Blocks.SOUL_LANTERN);
        gt.lamp[3] = col(Blocks.COBBLED_DEEPSLATE_WALL, Blocks.DARK_OAK_FENCE, Blocks.DARK_OAK_FENCE, Blocks.SOUL_LANTERN);
        gt.lamp[4] = col(Blocks.DEEPSLATE_BRICK_WALL, Blocks.DEEPSLATE_BRICK_WALL, Blocks.DARK_OAK_FENCE, Blocks.SOUL_LANTERN);
        gt.lamp[5] = col(Blocks.CHISELED_DEEPSLATE, Blocks.POLISHED_BLACKSTONE_WALL, Blocks.POLISHED_BLACKSTONE_WALL, Blocks.SOUL_LANTERN);
        fill(gt.plazaEdge, Blocks.COARSE_DIRT, Blocks.COBBLED_DEEPSLATE, Blocks.DEEPSLATE_BRICKS, Blocks.POLISHED_DEEPSLATE, Blocks.CHISELED_DEEPSLATE);
        fill(gt.pillar, Blocks.COBBLED_DEEPSLATE_WALL, Blocks.COBBLED_DEEPSLATE_WALL, Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_BRICKS, Blocks.POLISHED_BLACKSTONE_BRICKS);
        fill(gt.cap, Blocks.COBBLED_DEEPSLATE_WALL, Blocks.COBBLED_DEEPSLATE_WALL, Blocks.CHISELED_DEEPSLATE, Blocks.CHISELED_DEEPSLATE, Blocks.AMETHYST_BLOCK);
        fill(gt.light, Blocks.SOUL_LANTERN, Blocks.SOUL_LANTERN, Blocks.SOUL_LANTERN, Blocks.SOUL_LANTERN, Blocks.SOUL_LANTERN);
        gt.fountainRim = Blocks.DEEPSLATE_BRICK_STAIRS;
        gt.fountainPost = Blocks.DEEPSLATE_BRICK_WALL;
        gt.hedge = leaves(Blocks.DARK_OAK_LEAVES);
        gt.flowers = col(Blocks.FERN, Blocks.ALLIUM, Blocks.FERN, Blocks.LILY_OF_THE_VALLEY);
        gt.pots = col(Blocks.POTTED_WITHER_ROSE, Blocks.POTTED_ALLIUM, Blocks.POTTED_DEAD_BUSH);
        gt.wallBanner = Blocks.PURPLE_WALL_BANNER;
        gt.banner = Blocks.PURPLE_BANNER;
        gt.carpet = Blocks.PURPLE_CARPET;
        gt.wall[1] = wallPal(mix(Blocks.DARK_OAK_LOG, 3, Blocks.SPRUCE_LOG, 1), Blocks.COBBLED_DEEPSLATE, Blocks.STRIPPED_DARK_OAK_LOG,
                Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_FENCE, Blocks.DARK_OAK_STAIRS,
                Blocks.DARK_OAK_PLANKS, Blocks.SOUL_LANTERN, null, Blocks.PURPLE_BANNER);
        gt.wall[2] = wallPal(mix(Blocks.COBBLED_DEEPSLATE, 4, Blocks.DEEPSLATE_BRICKS, 2), Blocks.COBBLED_DEEPSLATE,
                Blocks.POLISHED_DEEPSLATE, Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_BRICK_SLAB, Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_FENCE,
                Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_PLANKS, Blocks.SOUL_LANTERN, null, Blocks.PURPLE_BANNER);
        gt.wall[3] = wallPal(mix(Blocks.DEEPSLATE_BRICKS, 5, Blocks.CRACKED_DEEPSLATE_BRICKS, 1, Blocks.COBBLED_DEEPSLATE, 1),
                Blocks.POLISHED_DEEPSLATE, Blocks.CHISELED_DEEPSLATE, Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_BRICK_SLAB,
                Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_FENCE, Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, Blocks.POLISHED_BLACKSTONE_BRICKS,
                Blocks.SOUL_LANTERN, null, Blocks.PURPLE_BANNER);
        gt.wall[4] = wallPal(mix(Blocks.DEEPSLATE_BRICKS, 4, Blocks.DEEPSLATE_TILES, 2, Blocks.POLISHED_BLACKSTONE_BRICKS, 1),
                Blocks.POLISHED_BLACKSTONE, Blocks.CHISELED_DEEPSLATE, Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_BRICK_SLAB,
                Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_FENCE, Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, Blocks.POLISHED_BLACKSTONE_BRICKS,
                Blocks.SOUL_LANTERN, Blocks.AMETHYST_BLOCK, Blocks.PURPLE_BANNER);
        for (int i = 1; i <= WALL_MAX; i++) gt.wall[i].spire = true;
        register(SettlementStyle.GOTHIC, gt);

        // ---------------------------------------------------------------- Anão (onda 5)
        // pedra talhada, ardósia e cobre; nem a "paliçada" é de madeira (muro de pedra solta)
        Look dw = new Look();
        dw.street[1] = mix(Blocks.GRAVEL, 3, Blocks.COARSE_DIRT, 2);
        dw.street[2] = mix(Blocks.COBBLESTONE, 3, Blocks.GRAVEL, 2);
        dw.street[3] = mix(Blocks.COBBLESTONE, 3, Blocks.ANDESITE, 2, Blocks.STONE_BRICKS, 1);
        dw.street[4] = mix(Blocks.STONE_BRICKS, 3, Blocks.DEEPSLATE_BRICKS, 2, Blocks.ANDESITE, 1);
        dw.street[5] = mix(Blocks.POLISHED_DEEPSLATE, 3, Blocks.SMOOTH_STONE, 2, Blocks.WAXED_CUT_COPPER, 1);
        dw.lamp[1] = col(Blocks.COBBLESTONE_WALL, Blocks.TORCH);
        dw.lamp[2] = col(Blocks.COBBLESTONE_WALL, Blocks.COBBLESTONE_WALL, Blocks.LANTERN);
        dw.lamp[3] = col(Blocks.COBBLESTONE_WALL, Blocks.COBBLESTONE_WALL, Blocks.COBBLESTONE_WALL, Blocks.LANTERN);
        dw.lamp[4] = col(Blocks.POLISHED_DEEPSLATE, Blocks.POLISHED_DEEPSLATE_WALL, Blocks.POLISHED_DEEPSLATE_WALL, Blocks.LANTERN);
        dw.lamp[5] = col(Blocks.WAXED_CUT_COPPER, Blocks.POLISHED_DEEPSLATE_WALL, Blocks.POLISHED_DEEPSLATE_WALL, Blocks.LANTERN);
        fill(dw.plazaEdge, Blocks.GRAVEL, Blocks.COBBLESTONE, Blocks.STONE_BRICKS, Blocks.POLISHED_DEEPSLATE, Blocks.WAXED_CUT_COPPER);
        fill(dw.pillar, Blocks.COBBLESTONE_WALL, Blocks.COBBLESTONE_WALL, Blocks.STONE_BRICKS, Blocks.POLISHED_DEEPSLATE, Blocks.POLISHED_DEEPSLATE);
        fill(dw.cap, Blocks.COBBLESTONE_WALL, Blocks.COBBLESTONE_WALL, Blocks.CHISELED_STONE_BRICKS, Blocks.WAXED_COPPER_BLOCK, Blocks.WAXED_CHISELED_COPPER);
        fill(dw.light, Blocks.LANTERN, Blocks.LANTERN, Blocks.LANTERN, Blocks.LANTERN, Blocks.OCHRE_FROGLIGHT);
        dw.fountainRim = Blocks.POLISHED_DEEPSLATE_STAIRS;
        dw.fountainPost = Blocks.POLISHED_DEEPSLATE_WALL;
        dw.hedge = leaves(Blocks.SPRUCE_LEAVES);
        dw.flowers = col(Blocks.FERN, Blocks.DANDELION, Blocks.FERN, Blocks.POPPY);
        dw.pots = col(Blocks.POTTED_BROWN_MUSHROOM, Blocks.POTTED_RED_MUSHROOM, Blocks.POTTED_FERN);
        dw.wallBanner = Blocks.YELLOW_WALL_BANNER;
        dw.banner = Blocks.YELLOW_BANNER;
        dw.carpet = Blocks.YELLOW_CARPET;
        dw.wall[1] = wallPal(mix(Blocks.COBBLESTONE, 3, Blocks.ANDESITE, 1, Blocks.MOSSY_COBBLESTONE, 1), Blocks.COBBLESTONE,
                Blocks.STRIPPED_SPRUCE_LOG, Blocks.COBBLESTONE, Blocks.COBBLESTONE_SLAB, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_FENCE,
                Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_PLANKS, Blocks.LANTERN, null, Blocks.YELLOW_BANNER);
        dw.wall[2] = wallPal(mix(Blocks.STONE_BRICKS, 3, Blocks.COBBLESTONE, 2, Blocks.ANDESITE, 1), Blocks.COBBLED_DEEPSLATE,
                Blocks.POLISHED_DEEPSLATE, Blocks.STONE_BRICKS, Blocks.STONE_BRICK_SLAB, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_FENCE,
                Blocks.COBBLED_DEEPSLATE_STAIRS, Blocks.COBBLED_DEEPSLATE, Blocks.LANTERN, null, Blocks.YELLOW_BANNER);
        dw.wall[3] = wallPal(mix(Blocks.DEEPSLATE_BRICKS, 3, Blocks.STONE_BRICKS, 3, Blocks.CRACKED_STONE_BRICKS, 1), Blocks.POLISHED_DEEPSLATE,
                Blocks.WAXED_CUT_COPPER, Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_BRICK_SLAB, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_FENCE,
                Blocks.WAXED_CUT_COPPER_STAIRS, Blocks.WAXED_CUT_COPPER, Blocks.LANTERN, null, Blocks.YELLOW_BANNER);
        dw.wall[4] = wallPal(mix(Blocks.DEEPSLATE_BRICKS, 4, Blocks.POLISHED_DEEPSLATE, 2, Blocks.STONE_BRICKS, 1), Blocks.POLISHED_DEEPSLATE,
                Blocks.WAXED_CHISELED_COPPER, Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_BRICK_SLAB, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_FENCE,
                Blocks.WAXED_CUT_COPPER_STAIRS, Blocks.WAXED_CUT_COPPER, Blocks.LANTERN, Blocks.GOLD_BLOCK, Blocks.YELLOW_BANNER);
        register(SettlementStyle.DWARVEN, dw);
    }

    /** Preenche um vetor "por degrau" (1..5) com um bloco por degrau. */
    public static void fill(BlockState[] row, Block t1, Block t2, Block t3, Block t4, Block t5) {
        Block[] b = {t1, t2, t3, t4, t5};
        for (int i = 0; i < MAX; i++) row[i + 1] = b[i].defaultBlockState();
    }

    private TierLook() {}
}
