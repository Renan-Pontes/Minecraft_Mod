package com.renanpontes.isekaimahou.feature.settlements.build;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.settlements.CityMonuments;
import com.renanpontes.isekaimahou.feature.settlements.SettlementStyle;
import com.renanpontes.isekaimahou.feature.settlements.build.ProcBuildings.G;
import com.renanpontes.isekaimahou.feature.settlements.build.ProcBuildings.P;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * VISUAL NOVO dos prédios (onda 8, city_looks) — pedido do dono: "tem que ter mudanças mais significativas visuais do
 * lvl 1 pro 2... até o 5... tem que ser bem diferente".
 * <p>Motor procedural com parâmetros POR NÍVEL e POR ESTILO: do 1 ao 5 mudam a silhueta (área ocupada do lote,
 * andares, torre/chaminé/anexos), os materiais ({@link #mat}: madeira crua → madeira + pedra → pedra lavrada →
 * reboco/vitral → nobre do estilo), o telhado, as janelas, o quintal ({@link #yard}: tocos → cerca → muro baixo →
 * jardim murado → pátio calçado) e os detalhes de função de cada prédio.
 * <ul>
 *   <li>plantas que eram molde .nbt (casas, fazenda, mercado, quartel, ferraria, templo, prefeitura, castelo e as
 *       maravilhas do deserto...) ganham gerador próprio aqui e em {@link LookWorks};</li>
 *   <li>as plantas que já eram procedurais ({@link ProcBuildings}) entram num lote 13×13 com o quintal do nível
 *       ({@link #wrap}); os monumentos (3 níveis) usam os visuais 1, 3 e 5 — o mais rico volta a ser alcançável.</li>
 * </ul>
 * Todo molde tem 13×13 com o chão (y=0) CHEIO — serve igual em chão natural, Ilha Celeste ou deque sobre a água — e a
 * entrada no meio do lado +Z. Ids: {@code settlements_look/<estilo>/<chave>/<nível>/<variante>}.
 * <p>Prédios de mundos antigos continuam com o molde em que foram erguidos ({@code Settlement.bLooks}): só ganham este
 * visual numa obra (melhoria, reforma de estilo ou "Refazer no visual novo").
 */
public final class LookBuildings {
    public static final String PREFIX = "settlements_look/";
    /** Lado do molde e o meio dele. */
    static final int W = 13, C = 6;

    /** Plantas com gerador próprio no visual novo. */
    public static final List<String> OWN = List.of("house_small", "house_large", "tavern", "builders_hall", "town_hall", "farm",
            "market", "barracks", "watchtower", "temple", "smithy", "stable", "well", "garden", "castle", "obelisk", "pyramid", "sphinx");

    private static final Map<String, Templates.Tpl> CACHE = new HashMap<>();

    public static boolean supports(@Nullable String key) {
        return key != null && !"pad".equals(key) && (OWN.contains(key) || ProcBuildings.handles(key));
    }

    public static ResourceLocation id(SettlementStyle style, String key, int level, int variant) {
        return IsekaiMahou.id(PREFIX + style.id + "/" + key + "/" + Math.max(1, Math.min(5, level)) + "/" + Math.max(0, variant));
    }

    public static boolean isLook(@Nullable ResourceLocation id) {
        return id != null && id.getPath().startsWith(PREFIX);
    }

    /** Carga por id (chamada por {@code Templates.load}). */
    @Nullable
    static Templates.Tpl load(ResourceLocation id) {
        String[] p = id.getPath().substring(PREFIX.length()).split("/");
        if (p.length < 4) return null;
        SettlementStyle st = SettlementStyle.byId(p[0]);
        try {
            return build(p[1], st == null ? SettlementStyle.KINGDOM : st, Integer.parseInt(p[2]), Integer.parseInt(p[3]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Molde antigo equivalente a um id do visual novo (rede de segurança se o gerador falhar), ou null. */
    @Nullable
    static ResourceLocation oldId(ResourceLocation id) {
        try {
            String[] p = id.getPath().substring(PREFIX.length()).split("/");
            SettlementStyle st = SettlementStyle.byId(p[0]);
            Blueprint bp = Blueprints.get(st == null ? SettlementStyle.KINGDOM : st, p[1]);
            return bp == null ? null : bp.templateOld(Integer.parseInt(p[3]), Integer.parseInt(p[2]));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Nível VISUAL de um prédio: os monumentos têm 3 níveis e usam os visuais 1, 3 e 5 (cada um bem maior que o outro). */
    public static int visual(String key, int level) {
        int lv = Math.max(1, Math.min(5, level));
        return CityMonuments.maxLevel(key) <= CityMonuments.MAX_LEVEL ? new int[] {1, 3, 5, 5, 5}[lv - 1] : lv;
    }

    /** Molde do prédio no visual novo; null = chave que este motor não conhece. */
    @Nullable
    public static Templates.Tpl build(String key, SettlementStyle style, int level, int variant) {
        if (!supports(key)) return null;
        SettlementStyle st = style == null ? SettlementStyle.KINGDOM : style;
        int lv = Math.max(1, Math.min(5, level));
        String ck = st.id + "/" + key + "/" + lv + "/" + variant;
        synchronized (CACHE) {
            Templates.Tpl hit = CACHE.get(ck);
            if (hit != null) return hit;
        }
        Templates.Tpl t;
        try {
            t = make(key, st, lv, variant);
        } catch (RuntimeException e) {
            // nunca derruba o servidor por causa de um molde: cai no visual antigo da planta
            IsekaiMahou.LOGGER.error("[settlements] falha ao gerar o visual novo de {}", ck, e);
            t = null;
        }
        if (t == null || t.entries().isEmpty()) return null;
        t = ProcBuildings.relight(t, st);
        synchronized (CACHE) {
            CACHE.put(ck, t);
        }
        return t;
    }

    @Nullable
    private static Templates.Tpl make(String key, SettlementStyle st, int lv, int variant) {
        int vis = visual(key, lv);
        Templates.Tpl t = switch (key) {
            case "house_small" -> house(st, lv, variant, false);
            case "house_large" -> house(st, lv, variant, true);
            case "tavern" -> tavern(st, lv);
            case "builders_hall" -> buildersHall(st, lv);
            case "town_hall" -> townHall(st, lv);
            case "farm" -> LookWorks.farm(st, lv);
            case "market" -> LookWorks.market(st, lv);
            case "barracks" -> LookWorks.barracks(st, lv);
            case "watchtower" -> LookWorks.watchtower(st, lv);
            case "temple" -> LookWorks.temple(st, vis);
            case "smithy" -> LookWorks.smithy(st, lv);
            case "stable" -> LookWorks.stable(st, lv);
            case "well" -> LookWorks.well(st, lv);
            case "garden" -> LookWorks.garden(st, lv);
            case "castle" -> LookWorks.castle(st, vis);
            case "obelisk" -> LookWorks.obelisk(st, vis);
            case "pyramid" -> LookWorks.pyramid(st, vis);
            case "sphinx" -> LookWorks.sphinx(st, vis);
            default -> null;
        };
        if (t != null) return t;
        // monumentos da onda 6: já têm 3 níveis próprios (a Árvore-Mãe nasce maior no visual novo)
        if (MonumentBuildings.handles(key)) return MonumentBuildings.make(key, st, lv, true);
        // plantas procedurais antigas: o prédio do nível visual dentro do lote, com o quintal do nível
        Templates.Tpl base = ProcBuildings.build(key, st, vis);
        return base == null ? null : wrap(base, st, vis);
    }

    // =====================================================================================================
    // Linha do planejador: o que muda no visual de cada nível
    // =====================================================================================================

    /** Grupo de textos de uma planta ("house", "farm", "smithy"... ou os genéricos "proc" e "monument"). */
    public static String group(String key) {
        return switch (key) {
            case "house_small", "house_large" -> "house";
            case "tavern", "builders_hall", "town_hall", "farm", "market", "barracks", "watchtower", "smithy", "stable", "well",
                 "garden" -> key;
            default -> CityMonuments.maxLevel(key) <= CityMonuments.MAX_LEVEL ? "monument" : "proc";
        };
    }

    /** Chave de tradução (sem o prefixo do módulo) da linha "o que este nível tem de diferente". */
    public static String lineKey(String key, int level) {
        String g = group(key);
        int max = "monument".equals(g) ? CityMonuments.MAX_LEVEL : 5;
        return "looks." + g + "." + Math.max(1, Math.min(max, level));
    }

    // =====================================================================================================
    // Materiais por estilo e nível
    // =====================================================================================================

    /** Materiais de um prédio no visual novo: a paleta do estilo com as trocas do nível. */
    static final class M {
        final SettlementStyle st;
        final int lv;
        final P p;
        final TierLook.Look look;
        /** Parede do térreo, parede dos andares de cima, chão do quintal, caminho, calçamento nobre e muro baixo. */
        BlockState ground, upper, soil, path, paving, lowWall;

        M(SettlementStyle st, int lv, P p) {
            this.st = st;
            this.lv = lv;
            this.p = p;
            this.look = TierLook.of(st);
        }
    }

    private static Block[] b(Block... blocks) {
        return blocks;
    }

    /** Telhado {escada, laje, bloco cheio} do estilo no nível. */
    private static Block[] roofOf(SettlementStyle st, int lv) {
        return switch (st) {
            case DESERT -> lv == 1 ? b(Blocks.MUD_BRICK_STAIRS, Blocks.MUD_BRICK_SLAB, Blocks.MUD_BRICKS)
                    : lv == 2 ? b(Blocks.SANDSTONE_STAIRS, Blocks.SANDSTONE_SLAB, Blocks.SANDSTONE)
                    : lv <= 4 ? b(Blocks.SMOOTH_SANDSTONE_STAIRS, Blocks.SMOOTH_SANDSTONE_SLAB, Blocks.SMOOTH_SANDSTONE)
                    : b(Blocks.SMOOTH_SANDSTONE_STAIRS, Blocks.CUT_SANDSTONE_SLAB, Blocks.CUT_SANDSTONE);
            case NORDIC -> lv <= 2 ? b(Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_PLANKS)
                    : lv == 3 ? b(Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_PLANKS)
                    : lv == 4 ? b(Blocks.COBBLED_DEEPSLATE_STAIRS, Blocks.COBBLED_DEEPSLATE_SLAB, Blocks.COBBLED_DEEPSLATE)
                    : b(Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB, Blocks.DEEPSLATE_TILES);
            case ORIENTAL -> lv <= 2 ? b(Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_PLANKS)
                    : lv == 3 ? b(Blocks.MANGROVE_STAIRS, Blocks.MANGROVE_SLAB, Blocks.MANGROVE_PLANKS)
                    : lv == 4 ? b(Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB, Blocks.DEEPSLATE_TILES)
                    : b(Blocks.DARK_PRISMARINE_STAIRS, Blocks.DARK_PRISMARINE_SLAB, Blocks.DARK_PRISMARINE);
            case ELVEN -> lv == 1 ? b(Blocks.BIRCH_STAIRS, Blocks.BIRCH_SLAB, Blocks.BIRCH_PLANKS)
                    : lv == 2 ? b(Blocks.WARPED_STAIRS, Blocks.WARPED_SLAB, Blocks.WARPED_PLANKS)
                    : lv == 3 ? b(Blocks.WAXED_WEATHERED_CUT_COPPER_STAIRS, Blocks.WAXED_WEATHERED_CUT_COPPER_SLAB, Blocks.WAXED_WEATHERED_CUT_COPPER)
                    : lv == 4 ? b(Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS, Blocks.WAXED_OXIDIZED_CUT_COPPER_SLAB, Blocks.WAXED_OXIDIZED_CUT_COPPER)
                    : b(Blocks.PRISMARINE_BRICK_STAIRS, Blocks.PRISMARINE_BRICK_SLAB, Blocks.PRISMARINE_BRICKS);
            case GOTHIC -> lv == 1 ? b(Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_PLANKS)
                    : lv == 2 ? b(Blocks.COBBLED_DEEPSLATE_STAIRS, Blocks.COBBLED_DEEPSLATE_SLAB, Blocks.COBBLED_DEEPSLATE)
                    : lv == 3 ? b(Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB, Blocks.DEEPSLATE_TILES)
                    : lv == 4 ? b(Blocks.CRIMSON_STAIRS, Blocks.CRIMSON_SLAB, Blocks.CRIMSON_PLANKS)
                    : b(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, Blocks.POLISHED_BLACKSTONE_BRICK_SLAB, Blocks.POLISHED_BLACKSTONE_BRICKS);
            case DWARVEN -> lv == 1 ? b(Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_PLANKS)
                    : lv == 2 ? b(Blocks.COBBLED_DEEPSLATE_STAIRS, Blocks.COBBLED_DEEPSLATE_SLAB, Blocks.COBBLED_DEEPSLATE)
                    : lv == 3 ? b(Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB, Blocks.DEEPSLATE_TILES)
                    : lv == 4 ? b(Blocks.WAXED_EXPOSED_CUT_COPPER_STAIRS, Blocks.WAXED_EXPOSED_CUT_COPPER_SLAB, Blocks.WAXED_EXPOSED_CUT_COPPER)
                    : b(Blocks.WAXED_CUT_COPPER_STAIRS, Blocks.WAXED_CUT_COPPER_SLAB, Blocks.WAXED_CUT_COPPER);
            default -> lv == 1 ? b(Blocks.OAK_STAIRS, Blocks.OAK_SLAB, Blocks.OAK_PLANKS)
                    : lv == 2 ? b(Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_PLANKS)
                    : lv == 3 ? b(Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_PLANKS)
                    : lv == 4 ? b(Blocks.BRICK_STAIRS, Blocks.BRICK_SLAB, Blocks.BRICKS)
                    : b(Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB, Blocks.DEEPSLATE_TILES);
        };
    }

    /**
     * Materiais do nível: 1 madeira crua (troncos, janela sem vidro, palha), 2 madeira sobre base de pedra, 3 pedra
     * lavrada, 4 pedra com o andar de cima rebocado e vitrais, 5 o material nobre do estilo.
     */
    static M mat(SettlementStyle st, int level) {
        int lv = Math.max(1, Math.min(5, level));
        P p = ProcBuildings.pal(st, lv);
        M m = new M(st, lv, p);
        Block[] g, u;
        switch (st) {
            case DESERT -> {
                g = b(Blocks.MUD_BRICKS, Blocks.SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.CUT_RED_SANDSTONE);
                u = b(Blocks.MUD_BRICKS, Blocks.SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.WHITE_TERRACOTTA, Blocks.CUT_SANDSTONE);
            }
            case NORDIC -> {
                g = b(Blocks.SPRUCE_LOG, Blocks.COBBLESTONE, Blocks.STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.DEEPSLATE_BRICKS);
                u = b(Blocks.SPRUCE_LOG, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_PLANKS, Blocks.STRIPPED_SPRUCE_WOOD);
            }
            case ORIENTAL -> {
                g = b(Blocks.DARK_OAK_LOG, Blocks.COBBLESTONE, Blocks.ANDESITE, Blocks.POLISHED_ANDESITE, Blocks.POLISHED_ANDESITE);
                u = b(Blocks.DARK_OAK_LOG, Blocks.SPRUCE_PLANKS, Blocks.WHITE_TERRACOTTA, Blocks.WHITE_TERRACOTTA, Blocks.SMOOTH_QUARTZ);
            }
            case ELVEN -> {
                g = b(Blocks.BIRCH_LOG, Blocks.MOSSY_COBBLESTONE, Blocks.MOSSY_STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.QUARTZ_BRICKS);
                u = b(Blocks.BIRCH_LOG, Blocks.BIRCH_PLANKS, Blocks.STRIPPED_BIRCH_WOOD, Blocks.CALCITE, Blocks.QUARTZ_BRICKS);
            }
            case GOTHIC -> {
                g = b(Blocks.DARK_OAK_LOG, Blocks.COBBLED_DEEPSLATE, Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_BRICKS, Blocks.POLISHED_BLACKSTONE_BRICKS);
                u = b(Blocks.DARK_OAK_LOG, Blocks.SPRUCE_PLANKS, Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_TILES, Blocks.POLISHED_BLACKSTONE_BRICKS);
            }
            case DWARVEN -> {
                g = b(Blocks.COBBLESTONE, Blocks.COBBLED_DEEPSLATE, Blocks.STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.DEEPSLATE_BRICKS);
                u = b(Blocks.COBBLESTONE, Blocks.STONE, Blocks.STONE_BRICKS, Blocks.POLISHED_ANDESITE, Blocks.DEEPSLATE_BRICKS);
            }
            default -> {
                g = b(Blocks.OAK_LOG, Blocks.COBBLESTONE, Blocks.STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.POLISHED_DIORITE);
                u = b(Blocks.OAK_LOG, Blocks.OAK_PLANKS, Blocks.STONE_BRICKS, Blocks.WHITE_TERRACOTTA, Blocks.POLISHED_DIORITE);
            }
        }
        m.ground = s(g[lv - 1]);
        m.upper = s(u[lv - 1]);
        Block[] r = roofOf(st, lv);
        p.roofStair = r[0];
        p.roofSlab = r[1];
        p.roofFull = s(r[2]);
        if (lv == 1) {
            // madeira crua: sem vidro (grade de cerca), chão batido e cumeeira de palha
            p.glass = st == SettlementStyle.DWARVEN ? Blocks.IRON_BARS : p.fence;
            p.pillar = s(p.log);
            p.beam = s(p.log);
            p.floor = s(st == SettlementStyle.DESERT ? Blocks.PACKED_MUD : st == SettlementStyle.DWARVEN ? Blocks.COBBLESTONE : Blocks.COARSE_DIRT);
            if (!p.flat) p.roofFull = s(Blocks.HAY_BLOCK);
        } else if (lv == 4) {
            // vitrais do estilo já no nível 4 (o 5 muda o material inteiro)
            p.glass = ProcBuildings.pal(st, 5).glass;
        }
        p.wall = m.upper;
        p.gable = m.upper;
        boolean desert = st == SettlementStyle.DESERT;
        m.soil = s(desert ? Blocks.SAND : Blocks.GRASS_BLOCK);
        m.path = lv == 1 ? s(desert ? Blocks.SANDSTONE : Blocks.COARSE_DIRT) : lv == 2 ? s(desert ? Blocks.SMOOTH_SANDSTONE : Blocks.GRAVEL) : p.base;
        m.paving = s(switch (st) {
            case DESERT -> Blocks.CUT_SANDSTONE;
            case NORDIC -> Blocks.STONE_BRICKS;
            case ORIENTAL, DWARVEN -> Blocks.SMOOTH_STONE;
            case ELVEN -> Blocks.POLISHED_DIORITE;
            case GOTHIC -> Blocks.POLISHED_DEEPSLATE;
            default -> Blocks.POLISHED_ANDESITE;
        });
        m.lowWall = lv >= 4 && m.look.fountainPost != null ? s(m.look.fountainPost) : m.look.lamp[3][0];
        return m;
    }

    // =====================================================================================================
    // Ajudantes de estado e peças comuns
    // =====================================================================================================

    static BlockState s(Block b) {
        return ProcBuildings.s(b);
    }

    static BlockState slabLow(Block b) {
        return ProcBuildings.slab(b, SlabType.BOTTOM);
    }

    static BlockState slabHigh(Block b) {
        return ProcBuildings.slab(b, SlabType.TOP);
    }

    static BlockState logY(BlockState st) {
        return ProcBuildings.axis(st, Direction.Axis.Y);
    }

    static boolean gate(int x, int z) {
        return z == W - 1 && Math.abs(x - C) <= 1;
    }

    /**
     * Chão do lote inteiro e o CERCADO do nível: 1 tocos nos cantos, 2 cerca de madeira, 3 muro baixo com lanternas
     * no portão, 4 o mesmo muro (o jardim vem em {@link #garnish}), 5 pátio calçado com pilares, luzes e estandartes.
     * O caminho vai da porta ({@code doorZ}) ao portão, no meio do lado +Z.
     */
    static void yard(G g, M m, int doorZ) {
        P p = m.p;
        int lv = m.lv;
        g.fill(0, 0, 0, W - 1, 0, W - 1, lv >= 5 ? m.paving : m.soil);
        int half = lv >= 4 ? 1 : 0;
        for (int z = Math.max(0, doorZ + 1); z < W; z++) {
            for (int x = C - half; x <= C + half; x++) g.set(x, 0, z, lv >= 5 ? p.base : m.path);
        }
        if (lv == 1) {
            for (int[] c : new int[][] {{0, 0}, {W - 1, 0}, {0, W - 1}, {W - 1, W - 1}}) g.set(c[0], 1, c[1], s(p.fence));
            return;
        }
        for (int x = 0; x < W; x++) {
            for (int z = 0; z < W; z++) {
                if (x != 0 && z != 0 && x != W - 1 && z != W - 1 || gate(x, z)) continue;
                boolean post = lv >= 5 && (x % 3 == 0 && (z == 0 || z == W - 1) || z % 3 == 0 && (x == 0 || x == W - 1));
                if (lv == 2) {
                    g.set(x, 1, z, s(p.fence));
                } else if (post) {
                    g.set(x, 1, z, m.ground);
                    g.set(x, 2, z, slabLow(p.roofSlab));
                } else {
                    g.set(x, 1, z, m.lowWall);
                }
            }
        }
        if (lv >= 3) {
            // pilares do portão com luz (e estandartes no pátio nobre)
            for (int x : new int[] {C - 2, C + 2}) {
                int h = lv >= 5 ? 3 : lv >= 4 ? 2 : 1;
                for (int y = 1; y <= h; y++) g.set(x, y, W - 1, y == h && lv >= 5 ? p.trim : m.ground);
                g.set(x, h + 1, W - 1, ProcBuildings.lantern(Blocks.LANTERN));
            }
            if (lv >= 5) {
                g.set(0, 3, W - 1, s(p.banner));
                g.set(W - 1, 3, W - 1, s(p.banner));
                g.set(0, 2, W - 1, m.ground);
                g.set(W - 1, 2, W - 1, m.ground);
            }
        }
    }

    /**
     * Enfeita o que SOBROU do quintal depois do prédio: mato (1), flores (2–3), sebes junto ao muro e canteiros (4),
     * topiaria nos cantos e vasos (5). Só onde o chão ainda é o do quintal e o bloco de cima está vazio.
     */
    static void garnish(G g, M m) {
        int lv = m.lv;
        boolean sand = m.soil.is(Blocks.SAND);
        BlockState[] flowers = m.look.flowers, pots = m.look.pots;
        for (int x = 1; x < W - 1; x++) {
            for (int z = 1; z < W - 1; z++) {
                BlockState floor = g.get(x, 0, z);
                if (g.get(x, 1, z) != null || floor == null) continue;
                int h = ProcBuildings.hash(x, lv, z, 31 + m.st.ordinal());
                boolean edge = x == 1 || x == W - 2 || z == 1;
                if (lv >= 5) {
                    if (floor != m.paving) continue;
                    boolean corner = (x == 1 || x == W - 2) && (z == 1 || z == W - 2);
                    if (corner) {
                        g.set(x, 1, z, ProcBuildings.leaves(m.p.leaves));
                        g.set(x, 2, z, ProcBuildings.leaves(m.p.leaves));
                    } else if (edge && h % 4 == 0) {
                        g.set(x, 1, z, pots[h / 7 % pots.length]);
                    }
                    continue;
                }
                if (floor != m.soil) continue;
                if (lv == 4 && edge && (x + z) % 2 == 0 && m.look.hedge != null) {
                    g.set(x, 1, z, m.look.hedge);
                } else if (h % (lv == 1 ? 11 : lv == 2 ? 8 : 5) == 0) {
                    BlockState plant = lv == 1 ? s(sand ? Blocks.DEAD_BUSH : Blocks.SHORT_GRASS) : flowers[h / 13 % flowers.length];
                    // flor que não vive na areia vira arbusto seco (deserto)
                    if (sand && !plant.is(Blocks.DEAD_BUSH) && !plant.is(Blocks.CACTUS)) plant = s(Blocks.DEAD_BUSH);
                    g.set(x, 1, z, plant);
                }
            }
        }
    }

    /**
     * Corpo de um prédio retangular: piso, paredes (térreo no material de baixo, andares de cima no de cima; no nível 2
     * só a base de pedra), vigas e pisos de cada andar, janelas do nível, pilares, porta no meio do lado +Z, escada de
     * mão e luzes. Devolve a altura do topo das paredes.
     */
    static int body(G g, M m, int x0, int z0, int x1, int z1, int storeys, boolean door) {
        P p = m.p;
        int lv = m.lv, sh = ProcBuildings.sh(lv), H = sh * storeys;
        int cx = (x0 + x1) / 2, cz = (z0 + z1) / 2;
        g.fill(x0, 0, z0, x1, 0, z1, p.floor);
        g.fill(x0 + 1, 1, z0 + 1, x1 - 1, H - 1, z1 - 1, null);
        int stone = lv == 2 ? 2 : lv >= 3 ? sh - 1 : 0;
        for (int y = 1; y <= H; y++) g.ring(x0, z0, x1, z1, y, lv >= 3 && y == 1 ? p.base : y <= stone ? m.ground : m.upper);
        for (int k = 1; k <= storeys; k++) {
            int y = sh * k;
            for (int x = x0; x <= x1; x++) {
                g.set(x, y, z0, ProcBuildings.axis(p.beam, Direction.Axis.X));
                g.set(x, y, z1, ProcBuildings.axis(p.beam, Direction.Axis.X));
            }
            for (int z = z0; z <= z1; z++) {
                g.set(x0, y, z, ProcBuildings.axis(p.beam, Direction.Axis.Z));
                g.set(x1, y, z, ProcBuildings.axis(p.beam, Direction.Axis.Z));
            }
            g.fill(x0 + 1, y, z0 + 1, x1 - 1, y, z1 - 1, p.plank);
        }
        // janelas: 1 = vãos com grade; 2 = uma fiada de vidro; 3+ = duas fiadas (vitral no 4+)
        for (int k = 0; k < storeys; k++) {
            int y0 = sh * k;
            for (int yw = y0 + 2; yw <= y0 + (lv >= 3 ? 3 : 2); yw++) {
                for (int x = x0 + 2; x <= x1 - 2; x++) {
                    int dx = Math.abs(x - cx);
                    boolean win = lv == 1 ? dx == 2 : dx % 3 == 2 || dx == 0 && k > 0;
                    if (!win) continue;
                    g.set(x, yw, z0, s(p.glass));
                    if (!(k == 0 && door && dx <= 1)) g.set(x, yw, z1, s(p.glass));
                }
                for (int z = z0 + 2; z <= z1 - 2; z++) {
                    int dz = Math.abs(z - cz);
                    if (lv == 1 ? dz == 0 : dz % 3 == (z1 - z0 >= 7 ? 2 : 0)) {
                        g.set(x0, yw, z, s(p.glass));
                        g.set(x1, yw, z, s(p.glass));
                    }
                }
            }
        }
        BlockState pl = logY(p.pillar);
        for (int y = 1; y <= H; y++) {
            g.set(x0, y, z0, pl);
            g.set(x1, y, z0, pl);
            g.set(x0, y, z1, pl);
            g.set(x1, y, z1, pl);
            if (lv >= 4 && x1 - x0 >= 8) {
                g.set(x0, y, cz, pl);
                g.set(x1, y, cz, pl);
                g.set(cx, y, z0, pl);
            }
        }
        if (door) {
            ProcBuildings.door(g, cx, 1, z1, p.door);
            if (lv >= 3) g.set(cx, 3, z1, p.trim);
        }
        if (storeys > 1) {
            for (int y = 1; y <= sh * (storeys - 1); y++) g.set(x0 + 1, y, z0 + 1, ProcBuildings.ladder(Direction.SOUTH));
        }
        for (int k = 0; k < storeys; k++) ProcBuildings.lights(g, p, x0, z0, x1, z1, sh * k);
        if (lv >= 5) {
            g.set(x0, H, z1, p.rich);
            g.set(x1, H, z1, p.rich);
        }
        return H;
    }

    /** Andares que cabem na altura do molde com o telhado do estilo (telhado íngreme e planta larga pedem menos). */
    static int fit(M m, int storeys, int width) {
        int roof = m.p.flat ? 5 : (width / 2) * (m.p.steep ? 2 : 1) + 4;
        int n = storeys;
        while (n > 1 && ProcBuildings.sh(m.lv) * n + roof > 24) n--;
        return n;
    }

    /** Telhado em pirâmide (escadas viradas para o centro) a partir do beiral em y. Devolve o y do topo. */
    static int pyramid(G g, P p, int x0, int z0, int x1, int z1, int y) {
        while (x0 < x1 && z0 < z1) {
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    if (x != x0 && x != x1 && z != z0 && z != z1) continue;
                    Direction f = z == z0 ? Direction.SOUTH : z == z1 ? Direction.NORTH : x == x0 ? Direction.EAST : Direction.WEST;
                    g.set(x, y, z, ProcBuildings.stair(p.roofStair, f));
                }
            }
            x0++;
            z0++;
            x1--;
            z1--;
            y++;
        }
        g.fill(x0, y, z0, Math.max(x0, x1), y, Math.max(z0, z1), p.roofFull);
        return y;
    }

    /**
     * Torre quadrada oca (lado n) de y0 até h, no material do térreo, com seteiras; {@code roofed} = telhado em
     * pirâmide com ponta, senão ameias. Devolve o y mais alto usado.
     */
    static int turret(G g, M m, int x0, int z0, int n, int y0, int h, boolean roofed) {
        P p = m.p;
        int x1 = x0 + n - 1, z1 = z0 + n - 1, cx = x0 + n / 2, cz = z0 + n / 2;
        for (int y = y0; y <= h; y++) {
            g.ring(x0, z0, x1, z1, y, m.ground);
            if (n >= 3) g.fill(x0 + 1, y, z0 + 1, x1 - 1, y, z1 - 1, null);
        }
        if (n >= 3) {
            g.fill(x0 + 1, h, z0 + 1, x1 - 1, h, z1 - 1, p.plank);
            BlockState slit = s(m.lv >= 4 ? p.glass : Blocks.IRON_BARS);
            for (int y = h - 2; y >= y0 + 2; y -= 4) {
                g.set(cx, y, z0, slit);
                g.set(cx, y, z1, slit);
                g.set(x0, y, cz, slit);
                g.set(x1, y, cz, slit);
            }
        }
        if (!roofed) {
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    if (x != x0 && x != x1 && z != z0 && z != z1) continue;
                    g.set(x, h + 1, z, (x + z) % 2 == 0 ? m.ground : slabLow(p.roofSlab));
                }
            }
            return h + 1;
        }
        int top = pyramid(g, p, x0, z0, x1, z1, h + 1);
        g.set(cx, top + 1, cz, s(p.fence));
        g.set(cx, top + 2, cz, m.lv >= 5 ? p.rich : s(Blocks.LIGHTNING_ROD));
        return top + 2;
    }

    /** Alpendre na frente da porta; com {@code balcony} vira sacada com guarda-corpo na altura do andar de cima. */
    static void porch(G g, M m, int cx, int z, boolean balcony) {
        P p = m.p;
        int top = balcony ? ProcBuildings.sh(m.lv) : 3;
        for (int x : new int[] {cx - 1, cx + 1}) {
            for (int y = 1; y < top; y++) g.set(x, y, z, m.lv >= 5 ? logY(p.pillar) : s(p.fence));
        }
        for (int x = cx - 1; x <= cx + 1; x++) {
            g.set(x, top, z, balcony ? p.plank : slabLow(p.roofSlab));
            if (balcony) g.set(x, top + 1, z, s(p.fence));
        }
    }

    /** Mesa de quintal: cerca com tapete em cima. */
    static void table(G g, M m, int x, int z) {
        g.set(x, 1, z, s(m.p.fence));
        g.set(x, 2, z, s(m.p.carpet));
    }

    /** Poste de luz do quintal (cerca + lanterna). */
    static void lamp(G g, M m, int x, int z, int h) {
        for (int y = 1; y <= h; y++) g.set(x, y, z, s(m.p.fence));
        g.set(x, h + 1, z, ProcBuildings.lantern(Blocks.LANTERN));
    }

    /** Espelha o molde no eixo X (variantes das casas: a chaminé e os anexos trocam de lado). */
    static Templates.Tpl mirror(Templates.Tpl t) {
        List<Templates.Entry> out = new ArrayList<>(t.entries().size());
        for (Templates.Entry e : t.entries()) {
            BlockPos q = e.pos();
            out.add(new Templates.Entry(new BlockPos(t.sx() - 1 - q.getX(), q.getY(), q.getZ()), e.state().mirror(Mirror.FRONT_BACK), e.nbt()));
        }
        return Templates.fromEntries(t.sx(), t.sy(), t.sz(), out);
    }

    /**
     * Planta procedural antiga dentro do lote 13×13 do visual novo: o quintal e o cercado do nível em volta do prédio
     * (plantas de 13 de lado já ocupam o lote inteiro e ficam como são).
     */
    static Templates.Tpl wrap(Templates.Tpl base, SettlementStyle st, int lv) {
        if (base.sx() > W - 2 || base.sz() > W - 2) return base;
        M m = mat(st, lv);
        G g = new G(W, W);
        int ox = (W - base.sx()) / 2, oz = W - 1 - base.sz();
        yard(g, m, oz + base.sz() - 1);
        for (Templates.Entry e : base.entries()) g.set(ox + e.pos().getX(), e.pos().getY(), oz + e.pos().getZ(), e.state());
        garnish(g, m);
        return g.tpl();
    }

    // =====================================================================================================
    // Casas, taverna, casa dos construtores e prefeitura
    // =====================================================================================================

    /**
     * Casa: 1 choupana de troncos; 2 chalé com base de pedra, vidro e chaminé; 3 sobrado de pedra; 4 sobrado maior com
     * o andar de cima rebocado, vitrais e sacada; 5 casarão nobre de três andares com torreão. A casa grande é sempre um
     * degrau maior que a pequena.
     */
    private static Templates.Tpl house(SettlementStyle st, int lv, int variant, boolean large) {
        M m = mat(st, lv);
        P p = m.p;
        int[][] fp = large
                ? new int[][] {{3, 4, 9, 10}, {2, 4, 10, 10}, {2, 2, 10, 10}, {1, 2, 11, 10}, {1, 1, 11, 10}}
                : new int[][] {{4, 5, 8, 9}, {3, 4, 9, 9}, {3, 3, 9, 9}, {2, 3, 10, 9}, {2, 2, 10, 10}};
        int[] f = fp[lv - 1];
        int x0 = f[0], z0 = f[1], x1 = f[2], z1 = f[3], cx = (x0 + x1) / 2;
        int storeys = fit(m, (large ? new int[] {1, 2, 2, 3, 3} : new int[] {1, 1, 2, 2, 3})[lv - 1], x1 - x0 + 1);
        G g = new G(W, W);
        yard(g, m, z1);
        int H = body(g, m, x0, z0, x1, z1, storeys, true);
        ProcBuildings.roof(g, p, x0, z0, x1, z1, H);
        if (lv >= 4) porch(g, m, cx, z1 + 1, storeys > 1);
        if (lv >= 5) turret(g, m, x0, z1 - 2, 3, H + 1, H + 4, true);
        // por dentro: camas (uma por andar; duas na casa grande), mesa e tapete
        int sh = ProcBuildings.sh(lv);
        for (int k = 0; k < storeys; k++) {
            int y = sh * k + 1;
            ProcBuildings.bed(g, x1 - 1, y, z0 + 1, Direction.SOUTH, p.bed);
            if (large && x1 - x0 >= 6) ProcBuildings.bed(g, x1 - 2, y, z0 + 1, Direction.SOUTH, p.bed);
            if (lv >= 3) g.set(cx, y, (z0 + z1) / 2, s(p.carpet));
        }
        g.set(x0 + 1, 1, z1 - 1, s(lv >= 2 ? Blocks.CRAFTING_TABLE : Blocks.HAY_BLOCK));
        if (lv >= 4) g.set(x1 - 1, 1, z1 - 1, s(Blocks.BOOKSHELF));
        // quintal: pilha de lenha (1–2), banco (3+)
        if (lv <= 2) {
            g.set(x1 + 1, 1, z0 + 1, ProcBuildings.axis(s(p.log), Direction.Axis.Z));
            g.set(x1 + 1, 1, z0 + 2, ProcBuildings.axis(s(p.log), Direction.Axis.Z));
        } else if (z1 + 1 < W - 1) {
            g.set(x0 + 1, 1, z1 + 1, ProcBuildings.stair(p.stairs, Direction.NORTH));
        }
        garnish(g, m);
        Templates.Tpl t = g.tpl();
        return variant % 2 == 1 ? mirror(t) : t;
    }

    /**
     * Taverna: 1 barraca de troncos com mesas ao ar livre; 2 casa de madeira com barris; 3 estalagem de dois andares
     * com placa; 4 estalagem com sacada e varanda de mesas; 5 hospedaria nobre de três andares com torreão.
     */
    private static Templates.Tpl tavern(SettlementStyle st, int lv) {
        M m = mat(st, lv);
        P p = m.p;
        int[] f = new int[][] {{3, 3, 9, 8}, {2, 3, 10, 8}, {2, 2, 10, 8}, {1, 2, 11, 8}, {1, 1, 11, 8}}[lv - 1];
        int x0 = f[0], z0 = f[1], x1 = f[2], z1 = f[3], cx = (x0 + x1) / 2;
        int storeys = fit(m, new int[] {1, 1, 2, 2, 3}[lv - 1], x1 - x0 + 1);
        G g = new G(W, W);
        yard(g, m, z1);
        int H = body(g, m, x0, z0, x1, z1, storeys, true);
        ProcBuildings.roof(g, p, x0, z0, x1, z1, H);
        // balcão e barris por dentro
        for (int x = x0 + 2; x <= x1 - 2; x++) g.set(x, 1, z0 + 2, x % 2 == 0 ? p.plank : slabHigh(p.slab));
        g.set(x0 + 1, 1, z0 + 1, s(Blocks.BARREL));
        if (lv >= 2) g.set(x0 + 1, 2, z0 + 1, s(Blocks.BARREL));
        // mesas na frente: mais a cada nível; placa com lanterna do 3 em diante
        int tables = Math.min(4, lv);
        int[][] spots = {{x0 + 1, z1 + 2}, {x1 - 1, z1 + 2}, {x0 + 1, z1 + 3}, {x1 - 1, z1 + 3}};
        for (int i = 0; i < tables; i++) {
            if (spots[i][1] >= W - 1) continue;
            table(g, m, spots[i][0], spots[i][1]);
            if (lv >= 2) g.set(spots[i][0] + (i % 2 == 0 ? 1 : -1), 1, spots[i][1], ProcBuildings.stair(p.stairs, i % 2 == 0 ? Direction.WEST : Direction.EAST));
        }
        if (lv >= 3) {
            g.set(cx + 2, 3, z1 + 1, s(p.fence));
            g.set(cx + 2, 2, z1 + 1, ProcBuildings.hanging(Blocks.LANTERN));
            g.set(cx - 2, 3, z1 + 1, ProcBuildings.wallBanner(p.wallBanner, Direction.SOUTH));
        }
        if (lv >= 4) porch(g, m, cx, z1 + 1, storeys > 1);
        if (lv >= 5) turret(g, m, x1 - 2, z1 - 2, 3, H + 1, H + 4, true);
        garnish(g, m);
        return g.tpl();
    }

    /** Guindaste de obra: mastro, braço e a carga pendurada na corrente. */
    static void crane(G g, M m, int x, int z, int h) {
        P p = m.p;
        for (int y = 1; y <= h; y++) g.set(x, y, z, y <= 1 ? logY(s(p.log)) : s(p.fence));
        for (int dz = 1; dz <= 2; dz++) g.set(x, h, z + dz, s(p.fence));
        g.set(x, h - 1, z + 2, s(Blocks.CHAIN));
        g.set(x, h - 2, z + 2, m.lv >= 3 ? m.ground : p.plank);
    }

    /**
     * Casa dos Construtores: 1 telheiro com bancada e pilha de troncos; 2 galpão de madeira; 3 oficina de pedra com
     * guindaste; 4 dois andares com pátio de materiais; 5 sede da guilda com torre e guindaste alto.
     */
    private static Templates.Tpl buildersHall(SettlementStyle st, int lv) {
        M m = mat(st, lv);
        P p = m.p;
        G g = new G(W, W);
        if (lv == 1) {
            yard(g, m, 6);
            for (int[] c : new int[][] {{4, 4}, {8, 4}, {4, 6}, {8, 6}}) {
                g.set(c[0], 1, c[1], logY(s(p.log)));
                g.set(c[0], 2, c[1], logY(s(p.log)));
            }
            g.fill(4, 3, 4, 8, 3, 6, slabLow(p.roofSlab));
            g.fill(5, 0, 4, 7, 0, 6, p.floor);
            g.set(5, 1, 4, s(Blocks.CRAFTING_TABLE));
            g.set(7, 1, 4, s(Blocks.STONECUTTER));
        } else {
            int[] f = new int[][] {null, {3, 3, 9, 8}, {2, 2, 10, 8}, {2, 2, 10, 8}, {1, 1, 11, 8}}[lv - 1];
            int storeys = fit(m, new int[] {1, 1, 1, 2, 2}[lv - 1], f[2] - f[0] + 1);
            yard(g, m, f[3]);
            int H = body(g, m, f[0], f[1], f[2], f[3], storeys, true);
            ProcBuildings.roof(g, p, f[0], f[1], f[2], f[3], H);
            g.set(f[0] + 1, 1, f[3] - 1, s(Blocks.CRAFTING_TABLE));
            g.set(f[2] - 1, 1, f[3] - 1, s(Blocks.STONECUTTER));
            if (lv >= 3) crane(g, m, f[2] - 1, f[3] + 1, lv >= 5 ? 7 : 5);
            if (lv >= 5) turret(g, m, f[0], f[3] - 2, 3, H + 1, H + 5, true);
        }
        // pilhas de material no pátio: troncos, pedra e tábuas (mais a cada nível)
        int piles = Math.min(4, lv + 1);
        int[][] at = {{1, 9}, {2, 9}, {1, 10}, {2, 10}};
        for (int i = 0; i < piles; i++) {
            g.set(at[i][0], 1, at[i][1], i % 2 == 0 ? ProcBuildings.axis(s(p.log), Direction.Axis.X) : lv >= 3 ? m.ground : p.plank);
            if (lv >= 4 && i < 2) g.set(at[i][0], 2, at[i][1], slabLow(p.slab));
        }
        garnish(g, m);
        return g.tpl();
    }

    /**
     * Prefeitura: 1 salão de madeira com sino; 2 salão com base de pedra; 3 paço de dois andares com torre do sino;
     * 4 paço largo com colunata e vitrais; 5 palácio de três andares com torre alta e estandartes.
     */
    private static Templates.Tpl townHall(SettlementStyle st, int lv) {
        M m = mat(st, lv);
        P p = m.p;
        int[] f = new int[][] {{3, 3, 9, 8}, {2, 3, 10, 8}, {2, 2, 10, 8}, {1, 2, 11, 8}, {1, 1, 11, 8}}[lv - 1];
        int x0 = f[0], z0 = f[1], x1 = f[2], z1 = f[3], cx = (x0 + x1) / 2;
        int storeys = fit(m, new int[] {1, 1, 2, 2, 3}[lv - 1], x1 - x0 + 1);
        G g = new G(W, W);
        yard(g, m, z1);
        int H = body(g, m, x0, z0, x1, z1, storeys, true);
        ProcBuildings.roof(g, p, x0, z0, x1, z1, H);
        if (lv <= 2) {
            // sino num poste ao lado da porta
            g.set(cx + 2, 1, z1 + 2, s(p.fence));
            g.set(cx + 2, 2, z1 + 2, s(p.fence));
            g.set(cx + 2, 3, z1 + 2, ProcBuildings.bell(Direction.SOUTH));
        } else {
            // torre do sino no meio da frente, mais alta a cada nível
            int top = Math.min(20, H + (lv >= 5 ? 6 : 4));
            turret(g, m, cx - 1, z1 - 2, 3, H + 1, top, true);
            g.set(cx, top - 1, z1 - 1, ProcBuildings.bell(Direction.SOUTH));
        }
        if (lv >= 4) {
            // colunata: pilares na frente segurando o beiral
            for (int x = x0; x <= x1; x += 2) {
                for (int y = 1; y <= 3; y++) g.set(x, y, z1 + 1, logY(p.pillar));
                g.set(x, 4, z1 + 1, slabLow(p.roofSlab));
            }
            for (int x = x0 + 1; x < x1; x += 2) g.set(x, 4, z1 + 1, slabLow(p.roofSlab));
        }
        if (lv >= 3) {
            g.set(cx - 2, 3, z1 + 1, ProcBuildings.wallBanner(p.wallBanner, Direction.SOUTH));
            g.set(cx + 2, 3, z1 + 1, ProcBuildings.wallBanner(p.wallBanner, Direction.SOUTH));
        }
        // por dentro: mesa do conselho e estantes
        g.set(cx, 1, z0 + 2, s(Blocks.LECTERN));
        if (lv >= 2) g.fill(x0 + 1, 1, z0 + 1, x0 + 2, lv >= 3 ? 2 : 1, z0 + 1, s(Blocks.BOOKSHELF));
        garnish(g, m);
        return g.tpl();
    }

    /**
     * Quantas fileiras do molde, a partir da frente (+Z), estão livres acima do chão nas 3 colunas do meio: o caminho
     * do portão até a porta. Só para moldes de lote inteiro (13×13); nos outros é 0.
     */
    static int frontClear(Templates.Tpl tpl) {
        if (tpl.sx() != W || tpl.sz() != W) return 0;
        boolean[] busy = new boolean[W];
        for (Templates.Entry e : tpl.entries()) {
            BlockPos q = e.pos();
            if (q.getY() >= 1 && q.getY() <= 5 && Math.abs(q.getX() - C) <= 1) busy[q.getZ()] = true;
        }
        int n = 0;
        while (n < W && !busy[W - 1 - n]) n++;
        return Math.min(5, n);
    }

    /** Esquece os moldes gerados (trocou a paleta de um estilo). */
    public static void clear() {
        synchronized (CACHE) {
            CACHE.clear();
        }
    }

    static BlockState stairUp(Block b, Direction f) {
        return ProcBuildings.stair(b, f, Half.TOP);
    }

    private LookBuildings() {}
}
