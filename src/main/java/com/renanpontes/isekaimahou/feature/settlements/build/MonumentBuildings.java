package com.renanpontes.isekaimahou.feature.settlements.build;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.settlements.SettlementStyle;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * MONUMENTOS NOVOS da onda 6 ("começar a construir monumentos com buffs, templos e etc..."), todos procedurais e em 3
 * níveis (cada nível muda o visual):
 * <ul>
 *   <li>para qualquer estilo: Obelisco das Eras, Colosso do Guardião, Grande Farol e Jardim Suspenso (na pedra e na
 *       madeira do estilo);</li>
 *   <li>uma maravilha para cada estilo que não tinha: Salão dos Heróis (Nórdico), Grande Pagode (Oriental),
 *       Árvore-Mãe (Élfico), Catedral das Sombras (Sombrio) e Grande Forja (Anão).</li>
 * </ul>
 * Mesmas convenções do {@link ProcBuildings} (que chama esta classe): y=0 é o chão cheio, a entrada fica no lado +Z,
 * planta de no máximo 13×13 e altura de no máximo 24; só blocos vanilla, sem lava nem água. Os bônus ficam em
 * {@code CityMonuments}.
 */
public final class MonumentBuildings {
    /** Monumentos de todos os estilos e a maravilha de cada estilo. */
    public static final List<String> COMMON = List.of("eras_obelisk", "guardian_colossus", "great_lighthouse", "hanging_garden");
    public static final List<String> WONDERS = List.of("mead_hall", "grand_pagoda", "world_tree", "grand_cathedral", "great_forge");
    private static final int MAX_Y = 24;

    public static boolean handles(@Nullable String key) {
        return key != null && (COMMON.contains(key) || WONDERS.contains(key));
    }

    /** Estilo dono de uma maravilha (null = monumento comum). */
    @Nullable
    public static SettlementStyle wonderStyle(String key) {
        return switch (key) {
            case "mead_hall" -> SettlementStyle.NORDIC;
            case "grand_pagoda" -> SettlementStyle.ORIENTAL;
            case "world_tree" -> SettlementStyle.ELVEN;
            case "grand_cathedral" -> SettlementStyle.GOTHIC;
            case "great_forge" -> SettlementStyle.DWARVEN;
            default -> null;
        };
    }

    /** Plantas novas do catálogo de um estilo (chamado pelo {@link Blueprints}). */
    public static List<Blueprint> blueprints(SettlementStyle s) {
        List<Blueprint> l = new ArrayList<>();
        l.add(new Blueprint("hanging_garden", s, 2, 6, 1, Blueprint.Effects.none()));
        l.add(new Blueprint("eras_obelisk", s, 3, 8, 1, Blueprint.Effects.none()));
        l.add(new Blueprint("great_lighthouse", s, 3, 8, 1, Blueprint.Effects.none()));
        l.add(new Blueprint("guardian_colossus", s, 4, 10, 1, Blueprint.Effects.none()));
        for (String w : WONDERS) {
            if (wonderStyle(w) == s) l.add(new Blueprint(w, s, 4, 15, 1, new Blueprint.Effects(0, 0, 0, 0, 0, 0, 0, 0, false, w)));
        }
        return l;
    }

    /** Molde do monumento (nível 1..3; acima disso repete o 3). null = chave desconhecida. */
    @Nullable
    static Templates.Tpl make(String key, SettlementStyle st, int level) {
        return make(key, st, level, false);
    }

    /**
     * Onda 8 (city_looks): {@code look} = visual novo — a Árvore-Mãe nasce maior (o nível 1 era pequeno demais) e cresce
     * mais a cada nível. Os outros monumentos já tinham 3 níveis bem diferentes e ficam como são.
     */
    @Nullable
    static Templates.Tpl make(String key, SettlementStyle st, int level, boolean look) {
        int lv = Math.max(1, Math.min(3, level));
        if (look && "world_tree".equals(key)) return worldTree(lv, true);
        return switch (key) {
            case "eras_obelisk" -> erasObelisk(st, lv);
            case "guardian_colossus" -> colossus(st, lv);
            case "great_lighthouse" -> lighthouse(st, lv);
            case "hanging_garden" -> garden(st, lv);
            case "mead_hall" -> meadHall(lv);
            case "grand_pagoda" -> pagoda(lv);
            case "world_tree" -> worldTree(lv, false);
            case "grand_cathedral" -> cathedral(lv);
            case "great_forge" -> forge(lv);
            default -> null;
        };
    }

    // =====================================================================================================
    // Grade e ajudantes
    // =====================================================================================================
    private static final class G {
        final int sx, sz;
        final Map<BlockPos, BlockState> m = new HashMap<>();

        G(int sx, int sz) {
            this.sx = sx;
            this.sz = sz;
        }

        void set(int x, int y, int z, @Nullable BlockState st) {
            if (x < 0 || z < 0 || x >= sx || z >= sz || y < 0 || y >= MAX_Y) return;
            BlockPos p = new BlockPos(x, y, z);
            if (st == null || st.isAir()) m.remove(p);
            else m.put(p, st);
        }

        void fill(int x0, int y0, int z0, int x1, int y1, int z1, @Nullable BlockState st) {
            for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
                for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
                    for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) set(x, y, z, st);
        }

        /** Contorno de um retângulo na altura y. */
        void ring(int x0, int z0, int x1, int z1, int y, @Nullable BlockState st) {
            for (int x = x0; x <= x1; x++) {
                set(x, y, z0, st);
                set(x, y, z1, st);
            }
            for (int z = z0; z <= z1; z++) {
                set(x0, y, z, st);
                set(x1, y, z, st);
            }
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

    private static BlockState s(Block b) {
        return b.defaultBlockState();
    }

    /** Escada com o lado ALTO virado para {@code f} (sobe-se andando para {@code f}). */
    private static BlockState stair(Block b, Direction f) {
        return b.defaultBlockState().setValue(StairBlock.FACING, f).setValue(StairBlock.HALF, Half.BOTTOM);
    }

    private static BlockState slab(Block b, boolean top) {
        return b.defaultBlockState().setValue(SlabBlock.TYPE, top ? SlabType.TOP : SlabType.BOTTOM);
    }

    private static BlockState leaves(Block b) {
        return b.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
    }

    private static BlockState log(Block b, Direction.Axis a) {
        BlockState st = b.defaultBlockState();
        return st.hasProperty(RotatedPillarBlock.AXIS) ? st.setValue(RotatedPillarBlock.AXIS, a) : st;
    }

    private static BlockState hanging(Block lantern) {
        return lantern.defaultBlockState().setValue(LanternBlock.HANGING, true);
    }

    /** Escada da borda de um quadrado de meio-lado r, virada para o centro (cantos: null = use bloco cheio). */
    @Nullable
    private static Direction inward(int dx, int dz, int r) {
        boolean ex = Math.abs(dx) == r, ez = Math.abs(dz) == r;
        if (ex && ez) return null;
        if (ez) return dz < 0 ? Direction.SOUTH : Direction.NORTH;
        if (ex) return dx < 0 ? Direction.EAST : Direction.WEST;
        return null;
    }

    private static boolean oct(int dx, int dz, int r) {
        int a = Math.abs(dx), b = Math.abs(dz);
        return Math.max(a, b) <= r && a + b <= r + (r + 1) / 2;
    }

    private static boolean octShell(int dx, int dz, int r) {
        return oct(dx, dz, r) && !oct(dx, dz, r - 1);
    }

    private static int hash(int x, int y, int z) {
        int h = x * 73856093 ^ y * 19349663 ^ z * 83492791;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return h & 0x7fffffff;
    }

    /** Pedra, madeira e luz de um estilo (o nível 3 troca a pedra bruta pela lavrada). */
    private record MP(Block stone, Block stairs, Block slab, Block wall, Block pillar, Block accent, Block light, Block fence,
                      Block leaves, Block glass, Block rich, Block soil) {}

    private static MP pal(SettlementStyle st, int lv) {
        boolean fine = lv >= 2;
        return switch (st) {
            case DESERT -> new MP(fine ? Blocks.CUT_SANDSTONE : Blocks.SANDSTONE, Blocks.SANDSTONE_STAIRS, Blocks.SANDSTONE_SLAB,
                    Blocks.SANDSTONE_WALL, Blocks.SMOOTH_SANDSTONE, Blocks.CHISELED_SANDSTONE, Blocks.GLOWSTONE, Blocks.ACACIA_FENCE,
                    Blocks.ACACIA_LEAVES, Blocks.ORANGE_STAINED_GLASS, Blocks.GOLD_BLOCK, Blocks.GRASS_BLOCK);
            case NORDIC -> new MP(fine ? Blocks.STONE_BRICKS : Blocks.COBBLESTONE, fine ? Blocks.STONE_BRICK_STAIRS : Blocks.COBBLESTONE_STAIRS,
                    fine ? Blocks.STONE_BRICK_SLAB : Blocks.COBBLESTONE_SLAB, Blocks.COBBLESTONE_WALL, Blocks.POLISHED_ANDESITE,
                    Blocks.CHISELED_STONE_BRICKS, Blocks.SEA_LANTERN, Blocks.SPRUCE_FENCE, Blocks.SPRUCE_LEAVES,
                    Blocks.LIGHT_BLUE_STAINED_GLASS, Blocks.IRON_BLOCK, Blocks.GRASS_BLOCK);
            case ORIENTAL -> new MP(fine ? Blocks.POLISHED_ANDESITE : Blocks.ANDESITE, fine ? Blocks.POLISHED_ANDESITE_STAIRS : Blocks.ANDESITE_STAIRS,
                    fine ? Blocks.POLISHED_ANDESITE_SLAB : Blocks.ANDESITE_SLAB, Blocks.ANDESITE_WALL, Blocks.SMOOTH_STONE,
                    Blocks.RED_TERRACOTTA, Blocks.SHROOMLIGHT, Blocks.DARK_OAK_FENCE, Blocks.CHERRY_LEAVES, Blocks.RED_STAINED_GLASS,
                    Blocks.GOLD_BLOCK, Blocks.GRASS_BLOCK);
            case ELVEN -> new MP(fine ? Blocks.MOSSY_STONE_BRICKS : Blocks.MOSSY_COBBLESTONE,
                    fine ? Blocks.MOSSY_STONE_BRICK_STAIRS : Blocks.MOSSY_COBBLESTONE_STAIRS,
                    fine ? Blocks.MOSSY_STONE_BRICK_SLAB : Blocks.MOSSY_COBBLESTONE_SLAB, Blocks.MOSSY_COBBLESTONE_WALL, Blocks.SMOOTH_QUARTZ,
                    Blocks.CHISELED_QUARTZ_BLOCK, Blocks.PEARLESCENT_FROGLIGHT, Blocks.BIRCH_FENCE, Blocks.FLOWERING_AZALEA_LEAVES,
                    Blocks.LIME_STAINED_GLASS, Blocks.EMERALD_BLOCK, Blocks.MOSS_BLOCK);
            case GOTHIC -> new MP(fine ? Blocks.DEEPSLATE_BRICKS : Blocks.COBBLED_DEEPSLATE,
                    fine ? Blocks.DEEPSLATE_BRICK_STAIRS : Blocks.COBBLED_DEEPSLATE_STAIRS,
                    fine ? Blocks.DEEPSLATE_BRICK_SLAB : Blocks.COBBLED_DEEPSLATE_SLAB, Blocks.DEEPSLATE_BRICK_WALL, Blocks.POLISHED_BLACKSTONE,
                    Blocks.CHISELED_DEEPSLATE, Blocks.CRYING_OBSIDIAN, Blocks.DARK_OAK_FENCE, Blocks.DARK_OAK_LEAVES,
                    Blocks.PURPLE_STAINED_GLASS, Blocks.AMETHYST_BLOCK, Blocks.GRASS_BLOCK);
            case DWARVEN -> new MP(fine ? Blocks.POLISHED_DEEPSLATE : Blocks.COBBLED_DEEPSLATE,
                    fine ? Blocks.POLISHED_DEEPSLATE_STAIRS : Blocks.COBBLED_DEEPSLATE_STAIRS,
                    fine ? Blocks.POLISHED_DEEPSLATE_SLAB : Blocks.COBBLED_DEEPSLATE_SLAB, Blocks.POLISHED_DEEPSLATE_WALL, Blocks.DEEPSLATE_TILES,
                    Blocks.WAXED_CUT_COPPER, Blocks.GLOWSTONE, Blocks.SPRUCE_FENCE, Blocks.SPRUCE_LEAVES, Blocks.ORANGE_STAINED_GLASS,
                    Blocks.GOLD_BLOCK, Blocks.GRASS_BLOCK);
            default -> new MP(fine ? Blocks.STONE_BRICKS : Blocks.COBBLESTONE, fine ? Blocks.STONE_BRICK_STAIRS : Blocks.COBBLESTONE_STAIRS,
                    fine ? Blocks.STONE_BRICK_SLAB : Blocks.COBBLESTONE_SLAB, Blocks.STONE_BRICK_WALL, Blocks.POLISHED_ANDESITE,
                    Blocks.CHISELED_STONE_BRICKS, Blocks.SEA_LANTERN, Blocks.OAK_FENCE, Blocks.OAK_LEAVES, Blocks.LIGHT_BLUE_STAINED_GLASS,
                    Blocks.GOLD_BLOCK, Blocks.GRASS_BLOCK);
        };
    }

    /** Poste de luz baixo: pedra, mureta e lanterna. */
    private static void lampPost(G g, MP p, int x, int z) {
        g.set(x, 1, z, s(p.stone));
        g.set(x, 2, z, s(p.wall));
        g.set(x, 3, z, s(Blocks.LANTERN));
    }

    // =====================================================================================================
    // Monumentos de todos os estilos
    // =====================================================================================================

    /** Obelisco das Eras: base em degraus, pedestal e uma agulha alta com faixas lavradas; ponta de ouro e luz no 3. */
    private static Templates.Tpl erasObelisk(SettlementStyle st, int lv) {
        MP p = pal(st, lv);
        int w = lv == 1 ? 7 : 9, c = w / 2;
        G g = new G(w, w);
        g.fill(0, 0, 0, w - 1, 0, w - 1, s(p.stone));
        if (lv >= 3) {
            // cruz lavrada no piso, do centro às bordas
            for (int i = 0; i < w; i++) {
                g.set(c, 0, i, s(p.accent));
                g.set(i, 0, c, s(p.accent));
            }
        }
        int tiers = lv == 1 ? 1 : 2;
        for (int t = 1; t <= tiers; t++) {
            int r = 2 + (tiers - t);
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    Direction f = inward(dx, dz, r);
                    g.set(c + dx, t, c + dz, f != null ? stair(p.stairs, f) : s(p.stone));
                }
            }
        }
        int y = tiers + 1;
        int plinth = lv >= 2 ? 2 : 1;
        g.fill(c - 1, y, c - 1, c + 1, y + plinth - 1, c + 1, s(p.pillar));
        if (lv >= 3) g.ring(c - 1, c - 1, c + 1, c + 1, y + plinth - 1, s(p.accent));
        y += plinth;
        int h = 4 + 3 * lv;
        for (int i = 0; i < h; i++) g.set(c, y + i, c, s(lv >= 2 && i % 3 == 2 ? p.accent : p.pillar));
        // aletas na base da agulha (2+), mais altas no 3
        if (lv >= 2) {
            for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                for (int i = 0; i < lv; i++) g.set(c + d[0], y + i, c + d[1], s(p.wall));
            }
        }
        g.set(c, y + h, c, s(lv >= 2 ? p.rich : p.accent));
        if (lv >= 3) g.set(c, y + h + 1, c, s(Blocks.END_ROD));
        if (lv >= 2) {
            for (int[] cc : new int[][] {{0, 0}, {w - 1, 0}, {0, w - 1}, {w - 1, w - 1}}) lampPost(g, p, cc[0], cc[1]);
        }
        return g.tpl();
    }

    /** Colosso do Guardião: estátua gigante de pé sobre um pedestal; escudo e espada no 2, elmo com crista e olhos de luz no 3. */
    private static Templates.Tpl colossus(SettlementStyle st, int lv) {
        MP p = pal(st, lv);
        int w = lv == 1 ? 9 : 11, c = w / 2;
        G g = new G(w, w);
        g.fill(0, 0, 0, w - 1, 0, w - 1, s(p.stone));
        int ph = lv == 1 ? 1 : 2;
        int rx = lv == 1 ? 3 : 4, rz = lv == 1 ? 2 : 3;
        g.fill(c - rx, 1, c - rz, c + rx, ph, c + rz, s(p.stone));
        if (lv >= 2) g.ring(c - rx, c - rz, c + rx, c + rz, ph, s(p.accent));
        BlockState body = s(lv == 1 ? p.stone : p.pillar);
        BlockState trim = s(lv >= 3 ? p.rich : p.accent);
        int y0 = ph + 1, legH = 3 + lv, torsoH = 3 + lv;
        // pernas (2×2 cada), cinto, tronco
        g.fill(c - 2, y0, c - 1, c - 1, y0 + legH - 1, c, body);
        g.fill(c + 1, y0, c - 1, c + 2, y0 + legH - 1, c, body);
        g.fill(c - 2, y0 + legH, c - 1, c + 2, y0 + legH, c, trim);
        g.fill(c - 2, y0 + legH + 1, c - 1, c + 2, y0 + legH + torsoH, c, body);
        int ys = y0 + legH + torsoH;
        // ombreiras e braços
        g.fill(c - 3, ys, c - 1, c - 3, ys, c, s(p.accent));
        g.fill(c + 3, ys, c - 1, c + 3, ys, c, s(p.accent));
        int arm = torsoH - 1;
        g.fill(c - 3, ys - arm, c, c - 3, ys - 1, c, body);
        g.fill(c + 3, ys - arm, c, c + 3, ys - 1, c, body);
        if (lv >= 2) {
            // escudo no braço esquerdo e espada fincada no pedestal sob a mão direita
            g.fill(c - 4, ys - arm, c + 1, c - 2, ys - arm + 3, c + 1, s(p.accent));
            g.set(c - 3, ys - arm + 2, c + 1, s(p.rich));
            for (int y = y0; y < ys - arm; y++) g.set(c + 3, y, c + 1, s(Blocks.IRON_BARS));
            g.set(c + 3, ys - arm, c + 1, body);
        }
        // cabeça 3×3×3 (elmo), olhos para +Z
        g.fill(c - 1, ys + 1, c - 1, c + 1, ys + 3, c + 1, body);
        BlockState eye = s(lv >= 3 ? p.light : p.accent);
        g.set(c - 1, ys + 2, c + 1, eye);
        g.set(c + 1, ys + 2, c + 1, eye);
        if (lv >= 2) g.fill(c, ys + 4, c - 1, c, ys + 4, c + 1, trim);
        if (lv >= 3) {
            for (int[] cc : new int[][] {{0, 0}, {w - 1, 0}, {0, w - 1}, {w - 1, w - 1}}) lampPost(g, p, cc[0], cc[1]);
        }
        return g.tpl();
    }

    /** Grande Farol: torre octogonal listrada, sacada com grade e a sala da luz no topo (dá para subir pela escada de mão). */
    private static Templates.Tpl lighthouse(SettlementStyle st, int lv) {
        MP p = pal(st, lv);
        int w = lv == 3 ? 11 : 9, c = w / 2;
        int h = 6 + 3 * lv;
        G g = new G(w, w);
        g.fill(0, 0, 0, w - 1, 0, w - 1, s(p.stone));
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                // base mais larga no 3
                if (lv >= 3 && octShell(dx, dz, 4)) g.fill(c + dx, 1, c + dz, c + dx, 2, c + dz, s(p.stone));
                if (octShell(dx, dz, 3)) {
                    for (int y = 1; y <= h; y++) g.set(c + dx, y, c + dz, s(lv >= 2 && (y / 2) % 2 == 1 ? p.pillar : p.stone));
                }
                // piso da sacada e grade
                if (oct(dx, dz, 4)) g.set(c + dx, h + 1, c + dz, s(p.stone));
                if (octShell(dx, dz, 4)) g.set(c + dx, h + 2, c + dz, s(p.fence));
                // sala da luz (vidro) e o telhado dela
                if (octShell(dx, dz, 2)) g.fill(c + dx, h + 2, c + dz, c + dx, h + 4, c + dz, s(p.glass));
                if (oct(dx, dz, 2)) g.set(c + dx, h + 5, c + dz, s(p.stone));
                if (oct(dx, dz, 1)) g.set(c + dx, h + 6, c + dz, slab(p.slab, false));
            }
        }
        // porta (lado +Z) e janelas a leste e oeste
        g.fill(c, 1, c + 3, c, 2, c + 3, null);
        g.fill(c, 1, c + 4, c, 2, c + 4, null);
        for (int y = 4; y < h; y += 4) {
            g.set(c - 3, y, c, s(p.glass));
            g.set(c + 3, y, c, s(p.glass));
        }
        // escada de mão na parede norte, saindo por um vão do piso e da sala da luz
        BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH);
        for (int y = 1; y <= h + 1; y++) g.set(c, y, c - 2, ladder);
        g.fill(c, h + 2, c - 2, c, h + 3, c - 2, null);
        // a luz
        g.set(c, h + 4, c, s(p.light));
        if (lv >= 2) g.set(c, h + 3, c, s(p.light));
        g.set(c, h + 6, c, s(lv >= 3 ? p.rich : p.accent));
        if (lv >= 3) g.set(c, h + 7, c, s(Blocks.END_ROD));
        return g.tpl();
    }

    /** Jardim Suspenso: três terraços com canteiros, arbustos e flores, escadaria na frente e uma árvore no topo. */
    private static Templates.Tpl garden(SettlementStyle st, int lv) {
        MP p = pal(st, lv);
        int w = lv == 1 ? 11 : 13, c = w / 2;
        int sw = lv == 1 ? 0 : 1;   // meia-largura da escadaria
        G g = new G(w, w);
        g.fill(0, 0, 0, w - 1, 0, w - 1, s(p.stone));
        Block[] flowers = {Blocks.POPPY, Blocks.DANDELION, Blocks.AZURE_BLUET, Blocks.OXEYE_DAISY, Blocks.CORNFLOWER, Blocks.ALLIUM};
        for (int k = 0; k < 3; k++) {
            int r = c - 2 * k, y1 = 1 + 2 * k, y2 = y1 + 1;
            g.fill(c - r, y1, c - r, c + r, y2, c + r, s(p.stone));
            boolean top = k == 2;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    int m = Math.max(Math.abs(dx), Math.abs(dz));
                    boolean corner = Math.abs(dx) == r && Math.abs(dz) == r;
                    if (m == r && !top) {
                        // borda do terraço: pedra lavrada nos cantos, com luz por cima (2+)
                        if (corner) {
                            g.set(c + dx, y2, c + dz, s(p.accent));
                            if (lv >= 2) g.set(c + dx, y2 + 1, c + dz, s(p.light));
                        }
                        continue;
                    }
                    if (!top && m < r - 1) continue;   // coberto pelo terraço de cima
                    g.set(c + dx, y2, c + dz, s(p.soil));
                    boolean stairs = dz > 0 && Math.abs(dx) <= sw;
                    if (stairs || top && dx == 0 && dz == 0) continue;
                    int hsh = hash(dx, k, dz);
                    if (hsh % 3 == 0) g.set(c + dx, y2 + 1, c + dz, leaves(p.leaves));
                    else if (hsh % 3 == 1 || lv >= 3) g.set(c + dx, y2 + 1, c + dz, s(flowers[hsh / 7 % flowers.length]));
                }
            }
        }
        // escadaria central no lado +Z: um degrau por bloco, cortando os terraços
        for (int j = 0; j < 6; j++) {
            int y = 1 + j, z = w - 1 - j;
            if (z <= c) break;   // o centro é da árvore
            for (int dx = -sw; dx <= sw; dx++) {
                g.set(c + dx, y, z, stair(p.stairs, Direction.NORTH));
                g.fill(c + dx, y + 1, z, c + dx, y + 4, z, null);
            }
        }
        // árvore no topo
        int yt = 7;
        Block trunk = switch (st) {
            case DESERT -> Blocks.ACACIA_LOG;
            case NORDIC, DWARVEN -> Blocks.SPRUCE_LOG;
            case ORIENTAL -> Blocks.CHERRY_LOG;
            case ELVEN -> Blocks.BIRCH_LOG;
            case GOTHIC -> Blocks.DARK_OAK_LOG;
            default -> Blocks.OAK_LOG;
        };
        int th = 1 + lv;
        for (int i = 0; i < th; i++) g.set(c, yt + i, c, log(trunk, Direction.Axis.Y));
        int cr = lv >= 2 ? 2 : 1;
        for (int dx = -cr; dx <= cr; dx++) {
            for (int dz = -cr; dz <= cr; dz++) {
                for (int dy = 0; dy <= cr; dy++) {
                    if (Math.abs(dx) + Math.abs(dz) + dy > cr + 1) continue;
                    if (dx == 0 && dz == 0 && dy == 0) continue;
                    g.set(c + dx, yt + th - 1 + dy, c + dz, leaves(p.leaves));
                }
            }
        }
        if (lv >= 3) {
            // luzes escondidas na copa
            g.set(c + 1, yt + th - 1, c, s(p.light));
            g.set(c - 1, yt + th - 1, c, s(p.light));
        }
        return g.tpl();
    }

    // =====================================================================================================
    // Maravilhas dos estilos
    // =====================================================================================================

    /** Telhado de duas águas ao longo de Z entre x0..x1 (com beiral), começando na altura y; devolve a altura da cumeeira. */
    private static int gable(G g, int x0, int x1, int z0, int z1, int y, Block stairs, BlockState ridge, BlockState fill) {
        int left = x0, right = x1;
        while (left < right) {
            for (int z = z0; z <= z1; z++) {
                g.set(left, y, z, stair(stairs, Direction.EAST));
                g.set(right, y, z, stair(stairs, Direction.WEST));
            }
            // empenas fechadas
            for (int x = left + 1; x < right; x++) {
                g.set(x, y, z0 + 1, fill);
                g.set(x, y, z1 - 1, fill);
            }
            left++;
            right--;
            y++;
        }
        if (left == right) for (int z = z0; z <= z1; z++) g.set(left, y, z, ridge);
        return y;
    }

    /** Salão dos Heróis (Nórdico): casa longa de troncos com telhado alto, fogueira no meio, mesas e o trono do jarl. */
    private static Templates.Tpl meadHall(int lv) {
        int w = lv >= 3 ? 13 : 11, d = 13, c = w / 2;
        G g = new G(w, d);
        g.fill(0, 0, 0, w - 1, 0, d - 1, s(lv >= 2 ? Blocks.STONE_BRICKS : Blocks.COBBLESTONE));
        int x0 = 1, x1 = w - 2, z0 = 1, z1 = d - 2;
        g.fill(x0 + 1, 0, z0 + 1, x1 - 1, 0, z1 - 1, s(Blocks.SPRUCE_PLANKS));
        int wh = lv == 1 ? 3 : 4;
        for (int y = 1; y <= wh; y++) {
            g.ring(x0, z0, x1, z1, y, s(y == 1 ? Blocks.COBBLESTONE : Blocks.SPRUCE_PLANKS));
            // esteios de tronco nos cantos e ao longo das paredes
            for (int z = z0; z <= z1; z += 2) {
                if ((z - z0) % 4 != 0 && z != z1) continue;
                g.set(x0, y, z, log(Blocks.SPRUCE_LOG, Direction.Axis.Y));
                g.set(x1, y, z, log(Blocks.SPRUCE_LOG, Direction.Axis.Y));
            }
            g.set(x0, y, z1, log(Blocks.SPRUCE_LOG, Direction.Axis.Y));
            g.set(x1, y, z1, log(Blocks.SPRUCE_LOG, Direction.Axis.Y));
        }
        int top = gable(g, x0 - 1, x1 + 1, z0 - 1, z1 + 1, wh + 1, Blocks.DARK_OAK_STAIRS, s(Blocks.DARK_OAK_PLANKS), s(Blocks.SPRUCE_PLANKS));
        // portão na frente
        int dh = lv == 1 ? 2 : 3, dw = lv == 1 ? 0 : 1;
        g.fill(c - dw, 1, z1, c + dw, dh, z1, null);
        // fogueira central sobre pedra, mesas e bancos dos dois lados
        int cz = d / 2;
        g.set(c, 0, cz, s(Blocks.COBBLESTONE));
        g.set(c, 1, cz, s(Blocks.CAMPFIRE));
        for (int z = z0 + 2; z <= z1 - 2; z++) {
            if (Math.abs(z - cz) > 3) continue;
            g.set(c - 2, 1, z, slab(Blocks.SPRUCE_SLAB, true));
            g.set(c + 2, 1, z, slab(Blocks.SPRUCE_SLAB, true));
        }
        g.set(c - 2, 2, cz, s(Blocks.LANTERN));
        g.set(c + 2, 2, cz, s(Blocks.LANTERN));
        // trono no fundo
        g.set(c, 1, z0 + 1, stair(Blocks.DARK_OAK_STAIRS, Direction.NORTH));
        if (lv >= 2) {
            g.set(c - 1, 1, z0 + 1, s(Blocks.IRON_BLOCK));
            g.set(c + 1, 1, z0 + 1, s(Blocks.IRON_BLOCK));
            g.set(c - 1, 2, z0 + 1, s(Blocks.LANTERN));
            g.set(c + 1, 2, z0 + 1, s(Blocks.LANTERN));
        }
        if (lv >= 3) {
            // cabeças de dragão nas pontas da cumeeira e ouro no trono
            g.set(c, top + 1, 0, stair(Blocks.DARK_OAK_STAIRS, Direction.SOUTH));
            g.set(c, top + 1, d - 1, stair(Blocks.DARK_OAK_STAIRS, Direction.NORTH));
            g.set(c, 2, z0 + 1, s(Blocks.GOLD_BLOCK));
            g.set(c, 1, z0 + 2, stair(Blocks.DARK_OAK_STAIRS, Direction.NORTH));
            g.set(c, 1, z0 + 1, s(Blocks.DARK_OAK_PLANKS));
        }
        return g.tpl();
    }

    /** Grande Pagode (Oriental): torre de 3, 4 ou 5 andares, cada um com o seu beiral, pilares vermelhos e agulha dourada. */
    private static Templates.Tpl pagoda(int lv) {
        int w = lv >= 3 ? 13 : 11, c = w / 2;
        int[] radii = lv == 1 ? new int[] {3, 2, 1} : lv == 2 ? new int[] {3, 2, 2, 1} : new int[] {4, 3, 3, 2, 1};
        G g = new G(w, w);
        g.fill(0, 0, 0, w - 1, 0, w - 1, s(Blocks.POLISHED_ANDESITE));
        int pr = radii[0] + 1;
        g.fill(c - pr, 1, c - pr, c + pr, 1, c + pr, s(Blocks.SMOOTH_STONE));
        g.set(c, 1, c + pr + 1, stair(Blocks.POLISHED_ANDESITE_STAIRS, Direction.NORTH));
        g.set(c, 1, c, s(Blocks.SHROOMLIGHT));
        BlockState pillar = log(Blocks.STRIPPED_MANGROVE_LOG, Direction.Axis.Y);
        for (int k = 0; k < radii.length; k++) {
            int r = radii[k], yb = 2 + 4 * k;
            for (int y = yb; y <= yb + 2; y++) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                        boolean corner = Math.abs(dx) == r && Math.abs(dz) == r;
                        boolean mid = dx == 0 || dz == 0;
                        BlockState b = corner ? pillar : mid && y == yb + 1 && r > 1 ? s(Blocks.WHITE_STAINED_GLASS) : s(Blocks.WHITE_TERRACOTTA);
                        g.set(c + dx, y, c + dz, b);
                    }
                }
            }
            // beiral: forro de tábuas, escadas viradas para dentro e pontas nos cantos
            int e = r + 1, ye = yb + 3;
            g.fill(c - r, ye, c - r, c + r, ye, c + r, s(Blocks.DARK_OAK_PLANKS));
            for (int dx = -e; dx <= e; dx++) {
                for (int dz = -e; dz <= e; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != e) continue;
                    Direction f = inward(dx, dz, e);
                    g.set(c + dx, ye, c + dz, f != null ? stair(Blocks.DEEPSLATE_TILE_STAIRS, f) : slab(Blocks.DEEPSLATE_TILE_SLAB, false));
                    if (f == null && lv >= 2) g.set(c + dx, ye - 1, c + dz, hanging(Blocks.LANTERN));
                }
            }
        }
        // porta do térreo
        g.fill(c, 2, c + radii[0], c, 3, c + radii[0], null);
        int yt = 2 + 4 * radii.length;
        // agulha (no 3 a torre chega ao limite de altura: ouro e ponta de luz)
        g.set(c, yt, c, s(Blocks.GOLD_BLOCK));
        g.set(c, yt + 1, c, s(lv >= 3 ? Blocks.END_ROD : Blocks.IRON_BARS));
        return g.tpl();
    }

    /** Árvore-Mãe (Élfico): tronco gigante com raízes, copa larga cheia de luzes e um nicho iluminado no tronco. */
    private static Templates.Tpl worldTree(int lv, boolean big) {
        int w = lv == 1 && !big ? 11 : 13, c = w / 2;
        // visual novo: copa de 5/6/6 de raio e tronco de 10/13/16 (antes 4/5/6 e 8/10/12)
        int r = big ? Math.min(6, 4 + lv) : 3 + lv, th = big ? 7 + 3 * lv : 6 + 2 * lv;
        G g = new G(w, w);
        g.fill(0, 0, 0, w - 1, 0, w - 1, s(Blocks.MOSS_BLOCK));
        for (int z = c + 2; z < w; z++) g.set(c, 0, z, s(Blocks.MOSSY_STONE_BRICKS));
        Block[] flowers = {Blocks.AZURE_BLUET, Blocks.OXEYE_DAISY, Blocks.LILY_OF_THE_VALLEY, Blocks.BLUE_ORCHID};
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < w; z++) {
                int hsh = hash(x, 7, z);
                if (hsh % 9 == 0 && Math.max(Math.abs(x - c), Math.abs(z - c)) >= 3 && !(x == c && z > c)) {
                    g.set(x, 1, z, s(flowers[hsh / 11 % flowers.length]));
                }
            }
        }
        // copa: elipsoide de folhas, com falhas na borda e luzes por dentro
        int yc = th + 1;
        double ry = r * 0.7;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -(int) ry; dy <= (int) ry + 1; dy++) {
                    double q = (dx * dx + dz * dz) / (double) (r * r) + dy * dy / (ry * ry);
                    if (q > 1.0) continue;
                    int hsh = hash(dx, dy, dz);
                    if (q > 0.75 && hsh % 5 == 0) continue;
                    boolean lit = q > 0.2 && q < 0.6 && hsh % (lv >= 2 ? 17 : 29) == 0;
                    g.set(c + dx, yc + dy, c + dz, lit ? s(Blocks.PEARLESCENT_FROGLIGHT) : leaves(hsh % 4 == 0 ? Blocks.FLOWERING_AZALEA_LEAVES : Blocks.AZALEA_LEAVES));
                }
            }
        }
        // tronco (em cruz no 1, 3×3 depois), raízes e galhos
        for (int y = 1; y <= th; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (lv == 1 && !big && dx != 0 && dz != 0) continue;
                    g.set(c + dx, y, c + dz, log(Blocks.OAK_LOG, Direction.Axis.Y));
                }
            }
        }
        for (int[] dd : new int[][] {{2, 0}, {-2, 0}, {0, -2}, {2, 2}, {-2, 2}, {2, -2}, {-2, -2}}) {
            if (lv == 1 && !big && dd[0] != 0 && dd[1] != 0) continue;
            g.set(c + dd[0], 1, c + dd[1], log(Blocks.OAK_LOG, Direction.Axis.Y));
        }
        for (int i = 2; i <= r - 2; i++) {
            g.set(c + i, yc - 1, c, log(Blocks.OAK_LOG, Direction.Axis.X));
            g.set(c - i, yc - 1, c, log(Blocks.OAK_LOG, Direction.Axis.X));
            g.set(c, yc - 1, c + i, log(Blocks.OAK_LOG, Direction.Axis.Z));
            g.set(c, yc - 1, c - i, log(Blocks.OAK_LOG, Direction.Axis.Z));
        }
        if (lv >= 2) {
            // nicho no tronco, virado para a entrada
            g.fill(c, 1, c, c, 2, c + 1, null);
            g.set(c, 3, c, s(Blocks.PEARLESCENT_FROGLIGHT));
            g.set(c, 0, c, s(Blocks.CHISELED_QUARTZ_BLOCK));
        }
        if (big && lv >= 2) {
            // visual novo: uma saia de folhas mais baixa (copa em dois andares) e lanternas penduradas nos galhos
            int ys = yc - (int) ry - 2;
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    int d2 = dx * dx + dz * dz;
                    if (d2 < 3 || d2 > 10 || hash(dx, 3, dz) % 4 == 0) continue;
                    g.set(c + dx, ys, c + dz, leaves(Blocks.AZALEA_LEAVES));
                    if (lv >= 3 && d2 >= 8 && hash(dx, 5, dz) % 3 == 0) g.set(c + dx, ys - 1, c + dz, hanging(Blocks.LANTERN));
                }
            }
        }
        if (lv >= 3) {
            for (int[] cc : new int[][] {{c - 4, c + 4}, {c + 4, c + 4}, {c - 4, c - 4}, {c + 4, c - 4}}) {
                g.set(cc[0], 1, cc[1], s(Blocks.MOSSY_STONE_BRICKS));
                g.set(cc[0], 2, cc[1], s(Blocks.MOSSY_STONE_BRICK_WALL));
                g.set(cc[0], 3, cc[1], s(Blocks.LANTERN));
            }
        }
        return g.tpl();
    }

    /** Catedral das Sombras (Sombrio): nave de pedra escura com vitrais roxos e a torre da frente com agulha. */
    private static Templates.Tpl cathedral(int lv) {
        int w = 11, d = 13, c = w / 2;
        G g = new G(w, d);
        g.fill(0, 0, 0, w - 1, 0, d - 1, s(Blocks.POLISHED_BLACKSTONE));
        BlockState brick = s(Blocks.DEEPSLATE_BRICKS), dark = s(Blocks.POLISHED_BLACKSTONE_BRICKS);
        BlockState glass = s(Blocks.PURPLE_STAINED_GLASS);
        int x0 = 2, x1 = w - 3, zn = 8;
        int wh = 4 + lv;
        for (int y = 1; y <= wh; y++) g.ring(x0, 0, x1, zn, y, brick);
        // vitrais altos nas laterais e contrafortes (2+)
        for (int z = 2; z <= 6; z += 2) {
            g.fill(x0, 2, z, x0, wh - 1, z, glass);
            g.fill(x1, 2, z, x1, wh - 1, z, glass);
        }
        if (lv >= 2) {
            for (int z = 1; z <= 7; z += 2) {
                g.fill(x0 - 1, 1, z, x0 - 1, 3, z, dark);
                g.fill(x1 + 1, 1, z, x1 + 1, 3, z, dark);
                g.set(x0 - 1, 4, z, stair(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.EAST));
                g.set(x1 + 1, 4, z, stair(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.WEST));
            }
        }
        gable(g, x0 - 1, x1 + 1, -1, zn + 1, wh + 1, Blocks.DEEPSLATE_TILE_STAIRS, s(Blocks.DEEPSLATE_TILES), brick);
        g.set(c, wh + 2, 0, glass);
        // torre da frente (5×5), colada na nave
        int th = 8 + 2 * lv;
        for (int y = 1; y <= th; y++) {
            g.ring(c - 2, zn, c + 2, d - 1, y, brick);
            for (int[] cc : new int[][] {{c - 2, zn}, {c + 2, zn}, {c - 2, d - 1}, {c + 2, d - 1}}) g.set(cc[0], y, cc[1], dark);
            // por dentro a torre é oca (o telhado da nave não atravessa)
            g.fill(c - 1, y, zn + 1, c + 1, y, d - 2, null);
        }
        // portal, arco para a nave, rosácea e vãos do sino
        g.fill(c, 1, d - 1, c, 3, d - 1, null);
        g.fill(c - 1, 1, zn, c + 1, 3, zn, null);
        g.fill(c - 1, 5, d - 1, c + 1, 7, d - 1, glass);
        g.set(c, 6, d - 1, s(Blocks.AMETHYST_BLOCK));
        g.fill(c, th - 2, d - 1, c, th - 1, d - 1, null);
        g.fill(c - 2, th - 2, zn + 2, c - 2, th - 1, zn + 2, null);
        g.fill(c + 2, th - 2, zn + 2, c + 2, th - 1, zn + 2, null);
        // agulha
        int tz = zn + 2, y = th + 1;
        for (int r = 2; r >= 1; r--) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    Direction f = Math.max(Math.abs(dx), Math.abs(dz)) == r ? inward(dx, dz, r) : null;
                    boolean edge = Math.max(Math.abs(dx), Math.abs(dz)) == r;
                    g.set(c + dx, y, tz + dz, edge ? (f != null ? stair(Blocks.DEEPSLATE_TILE_STAIRS, f) : slab(Blocks.DEEPSLATE_TILE_SLAB, false)) : s(Blocks.DEEPSLATE_TILES));
                }
            }
            y++;
        }
        g.set(c, y, tz, s(Blocks.DEEPSLATE_TILES));
        for (int i = 1; i <= lv; i++) g.set(c, y + i, tz, s(Blocks.DEEPSLATE_BRICK_WALL));
        g.set(c, y + lv + 1, tz, s(lv >= 3 ? Blocks.END_ROD : Blocks.IRON_BARS));
        if (lv >= 2) {
            // pináculos nos cantos da torre
            for (int[] cc : new int[][] {{c - 2, zn}, {c + 2, zn}, {c - 2, d - 1}, {c + 2, d - 1}}) {
                g.set(cc[0], th + 1, cc[1], dark);
                g.set(cc[0], th + 2, cc[1], s(Blocks.DEEPSLATE_BRICK_WALL));
                if (lv >= 3) g.set(cc[0], th + 3, cc[1], s(Blocks.LANTERN));
            }
        }
        // altar, bancos e luzes
        g.set(c, 1, 1, s(lv >= 3 ? Blocks.AMETHYST_BLOCK : Blocks.CHISELED_DEEPSLATE));
        g.set(c, 2, 1, s(Blocks.LANTERN));
        for (int z = 3; z <= 6; z += 2) {
            for (int x = x0 + 1; x < x1; x++) if (x != c) g.set(x, 1, z, stair(Blocks.DARK_OAK_STAIRS, Direction.SOUTH));
        }
        g.set(c - 1, 1, d - 2, s(Blocks.LANTERN));
        g.set(c + 1, 1, d - 2, s(Blocks.LANTERN));
        return g.tpl();
    }

    /** Grande Forja (Anão): salão de pedra com faixas de cobre, chaminés nos cantos e a fornalha central com brasas. */
    private static Templates.Tpl forge(int lv) {
        int w = lv == 1 ? 11 : 13, c = w / 2;
        G g = new G(w, w);
        BlockState stone = s(Blocks.POLISHED_DEEPSLATE), tile = s(Blocks.DEEPSLATE_TILES), copper = s(Blocks.WAXED_CUT_COPPER);
        g.fill(0, 0, 0, w - 1, 0, w - 1, stone);
        int x0 = 1, x1 = w - 2;
        int wh = lv == 1 ? 4 : 5;
        for (int y = 1; y <= wh; y++) g.ring(x0, x0, x1, x1, y, y == 3 ? copper : stone);
        // ameias e teto (com o vão da chaminé no meio)
        for (int i = x0; i <= x1; i += 2) {
            g.set(i, wh + 1, x0, stone);
            g.set(i, wh + 1, x1, stone);
            g.set(x0, wh + 1, i, stone);
            g.set(x1, wh + 1, i, stone);
        }
        g.fill(x0 + 1, wh, x0 + 1, x1 - 1, wh, x1 - 1, tile);
        // chaminés dos cantos, com fumaça
        for (int[] cc : new int[][] {{x0, x0}, {x1, x0}, {x0, x1}, {x1, x1}}) {
            g.fill(cc[0], 1, cc[1], cc[0], wh + 2, cc[1], tile);
            g.set(cc[0], wh + 3, cc[1], s(Blocks.CAMPFIRE));
        }
        // fornalha central: caixa 5×5, brasas no fundo, grade na frente e chaminé alta
        int ch = wh + 2 + lv;
        for (int y = 1; y <= ch; y++) {
            g.ring(c - 2, c - 2, c + 2, c + 2, y, y == 3 && lv >= 2 ? copper : s(Blocks.DEEPSLATE_BRICKS));
            g.fill(c - 1, y, c - 1, c + 1, y, c + 1, null);
        }
        g.fill(c - 1, 0, c - 1, c + 1, 0, c + 1, s(Blocks.MAGMA_BLOCK));
        g.set(c, 1, c, s(Blocks.CAMPFIRE));
        g.fill(c - 1, 1, c + 2, c + 1, 2, c + 2, s(Blocks.IRON_BARS));
        // portão
        g.fill(c - 1, 1, x1, c + 1, 3, x1, null);
        if (lv >= 2) {
            g.fill(c - 2, 1, x1, c - 2, 4, x1, copper);
            g.fill(c + 2, 1, x1, c + 2, 4, x1, copper);
            g.fill(c - 1, 4, x1, c + 1, 4, x1, copper);
        }
        // bigornas e luzes
        g.set(x0 + 1, 1, c + 1, s(Blocks.ANVIL));
        g.set(x1 - 1, 1, c + 1, s(Blocks.ANVIL));
        g.set(x0 + 1, 1, x0 + 1, s(Blocks.GLOWSTONE));
        g.set(x1 - 1, 1, x0 + 1, s(Blocks.GLOWSTONE));
        g.set(x0 + 1, 1, x1 - 1, s(Blocks.LANTERN));
        g.set(x1 - 1, 1, x1 - 1, s(Blocks.LANTERN));
        if (lv >= 3) {
            // martelo de ouro sobre o portão
            g.fill(c - 1, wh + 1, x1, c + 1, wh + 2, x1, s(Blocks.GOLD_BLOCK));
            g.set(c, wh, x1, s(Blocks.GOLD_BLOCK));
        }
        return g.tpl();
    }

    private MonumentBuildings() {}
}
