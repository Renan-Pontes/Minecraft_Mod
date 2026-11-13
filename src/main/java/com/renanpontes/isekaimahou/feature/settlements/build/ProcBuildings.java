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
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.GrindstoneBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Prédios PROCEDURAIS das cidades do jogador (sem .nbt): celeiro, biblioteca, torre de magia, guilda, arena,
 * curandeiro, porto, mina, serraria, oficina, estátua, arco, os 7 templos elementais e a base de terraplenagem.
 * <p>
 * Convenções (iguais às dos moldes .nbt, ver tools/gen/settlements_build.py):
 * <ul>
 *   <li>coordenadas locais x em [0,sx), y em [0,sy), z em [0,sz); y=0 é o CHÃO (cheio em toda a planta);</li>
 *   <li>a ENTRADA fica no lado +Z: porta/abertura em z=sz-1, centrada em x=sx/2 (o lado de fora fica livre para os
 *       degraus que a obra põe);</li>
 *   <li>planta de no máximo 13 x 13 (só a base "pad" tem 15 x 15) e altura de no máximo 24;</li>
 *   <li>o prédio CRESCE com o nível (1..5): planta maior, mais andares, materiais mais ricos e mais enfeites.</li>
 * </ul>
 * Só blocos vanilla; nada de entidades, lava ou fogo solto (só fogueiras acesas e magma); água só em tanques
 * fechados (templo da água e porto). Os moldes ficam em cache por chave+estilo+nível.
 */
public final class ProcBuildings {
    /** Chaves que esta classe sabe construir. */
    public static final List<String> KEYS = List.of("granary", "library", "magic_tower", "guild", "arena", "hospital",
            "port", "mine", "sawmill", "workshop", "statue", "arch", "temple_fire", "temple_water", "temple_earth",
            "temple_wind", "temple_light", "temple_shadow", "temple_arcane", "pad",
            // v1.5 (city_arcana): prédios mágicos da cidade
            "arcane_ward", "null_obelisk", "mana_spire", "arcane_beacon",
            // v1.0 (city_life): defesa de cerco
            "trap_field",
            // onda 10 (vehicles_mechanic): Oficina Mecânica
            "garage");

    /** Altura máxima de um molde procedural (blocos acima disso são ignorados). */
    private static final int MAX_Y = 24;

    private static final Map<String, Templates.Tpl> CACHE = new HashMap<>();

    public static boolean handles(String key) {
        // onda 6 (city_monuments): os monumentos novos são gerados por MonumentBuildings
        return key != null && (KEYS.contains(key) || MonumentBuildings.handles(key));
    }

    /** Molde do prédio no nível 1..5 (fora disso é limitado). Nunca nulo para uma chave de {@link #KEYS}. */
    @Nullable
    public static Templates.Tpl build(String key, SettlementStyle style, int level) {
        if (!handles(key)) return null;
        SettlementStyle st = style == null ? SettlementStyle.KINGDOM : style;
        int lv = Math.max(1, Math.min(5, level));
        String ck = key + "/" + st.id + "/" + lv;
        synchronized (CACHE) {
            Templates.Tpl t = CACHE.get(ck);
            if (t != null) return t;
        }
        Templates.Tpl t;
        try {
            t = make(key, st, lv);
        } catch (RuntimeException e) {
            // nunca derruba o servidor por causa de um molde: vira um piso simples
            IsekaiMahou.LOGGER.error("[settlements] falha ao gerar o prédio procedural {}", ck, e);
            t = null;
        }
        if (t == null || t.entries().isEmpty()) t = fallback(st);
        t = relight(t, st);
        synchronized (CACHE) {
            CACHE.put(ck, t);
        }
        return t;
    }

    private static Templates.Tpl make(String key, SettlementStyle st, int lv) {
        return switch (key) {
            case "granary" -> granary(st, lv);
            case "library" -> library(st, lv);
            case "magic_tower" -> magicTower(st, lv);
            case "guild" -> guild(st, lv);
            case "arena" -> arena(st, lv);
            case "hospital" -> hospital(st, lv);
            case "port" -> port(st, lv);
            case "mine" -> mine(st, lv);
            case "sawmill" -> sawmill(st, lv);
            case "workshop" -> workshop(st, lv);
            case "statue" -> statue(st, lv);
            case "arch" -> arch(st, lv);
            case "pad" -> pad(st);
            case "arcane_ward" -> arcaneWard(st, lv);
            case "null_obelisk" -> nullObelisk(st, lv);
            case "mana_spire" -> manaSpire(st, lv);
            case "arcane_beacon" -> arcaneBeacon(st, lv);
            case "trap_field" -> trapField(st, lv);
            case "garage" -> garage(st, lv);
            default -> MonumentBuildings.handles(key) ? MonumentBuildings.make(key, st, lv)
                    : key.startsWith("temple_") ? temple(key.substring(7), st, lv) : null;
        };
    }

    /**
     * Luzes do estilo (onda 5): no Sombrio todo fogo é de ALMA — lanternas, tochas e fogueiras do molde viram as de
     * alma (mesma posição e orientação). Os outros estilos ficam como saíram.
     */
    static Templates.Tpl relight(Templates.Tpl t, SettlementStyle st) {
        if (st != SettlementStyle.GOTHIC) return t;
        List<Templates.Entry> out = new ArrayList<>(t.entries().size());
        for (Templates.Entry e : t.entries()) out.add(new Templates.Entry(e.pos(), soul(e.state()), e.nbt()));
        return new Templates.Tpl(t.sx(), t.sy(), t.sz(), List.copyOf(out));
    }

    static BlockState soul(BlockState st) {
        Block to = st.is(Blocks.LANTERN) ? Blocks.SOUL_LANTERN : st.is(Blocks.TORCH) ? Blocks.SOUL_TORCH
                : st.is(Blocks.WALL_TORCH) ? Blocks.SOUL_WALL_TORCH : st.is(Blocks.CAMPFIRE) ? Blocks.SOUL_CAMPFIRE : null;
        return to == null ? st : to.withPropertiesOf(st);
    }

    /**
     * Pedra de cantaria dos estilos novos: {bloco, escada, laje} (pedestais, arcos, torre de magia). null = estilo de
     * origem (cada prédio já tem a sua).
     */
    @Nullable
    private static Block[] masonry(SettlementStyle st, int lv) {
        return switch (st) {
            case ORIENTAL -> lv >= 3 ? new Block[] {Blocks.POLISHED_ANDESITE, Blocks.POLISHED_ANDESITE_STAIRS, Blocks.POLISHED_ANDESITE_SLAB}
                    : new Block[] {Blocks.ANDESITE, Blocks.ANDESITE_STAIRS, Blocks.ANDESITE_SLAB};
            case ELVEN -> lv >= 3 ? new Block[] {Blocks.MOSSY_STONE_BRICKS, Blocks.MOSSY_STONE_BRICK_STAIRS, Blocks.MOSSY_STONE_BRICK_SLAB}
                    : new Block[] {Blocks.MOSSY_COBBLESTONE, Blocks.MOSSY_COBBLESTONE_STAIRS, Blocks.MOSSY_COBBLESTONE_SLAB};
            case GOTHIC -> lv >= 3 ? new Block[] {Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.DEEPSLATE_BRICK_SLAB}
                    : new Block[] {Blocks.COBBLED_DEEPSLATE, Blocks.COBBLED_DEEPSLATE_STAIRS, Blocks.COBBLED_DEEPSLATE_SLAB};
            case DWARVEN -> lv >= 3 ? new Block[] {Blocks.POLISHED_DEEPSLATE, Blocks.POLISHED_DEEPSLATE_STAIRS, Blocks.POLISHED_DEEPSLATE_SLAB}
                    : new Block[] {Blocks.COBBLED_DEEPSLATE, Blocks.COBBLED_DEEPSLATE_STAIRS, Blocks.COBBLED_DEEPSLATE_SLAB};
            default -> null;
        };
    }

    /** Piso 9 x 9 do estilo (só se algo der muito errado na geração). */
    private static Templates.Tpl fallback(SettlementStyle st) {
        G g = new G(9, 9);
        g.fill(0, 0, 0, 8, 0, 8, st.foundation());
        return g.tpl();
    }

    // =====================================================================================================
    // Enfeites em volta (qualquer prédio, inclusive os de .nbt)
    // =====================================================================================================

    /**
     * Anel de enfeites na faixa de 1 bloco em volta da planta (x em -1/sx ou z em -1/sz, y >= 1), conforme o nível
     * do prédio: 2 = postes de lanterna nos cantos; 3 = + sebes/vasos nas laterais; 4 = + estandartes do estilo;
     * 5 = + base de ouro/esmeralda nos postes e mais luzes. Nada na frente da entrada (z=sz, |x - sx/2| <= 2).
     */
    public static List<Templates.Entry> ornaments(SettlementStyle style, int level, int sx, int sz) {
        if (level <= 1 || sx <= 0 || sz <= 0) return List.of();
        SettlementStyle st = style == null ? SettlementStyle.KINGDOM : style;
        int lv = Math.min(5, level);
        P p = pal(st, Math.max(3, lv));
        Map<BlockPos, BlockState> m = new LinkedHashMap<>();
        int cx = sx / 2;
        // 3+: sebes (ou vasos de cacto no deserto) intercaladas com vasos de flor nas laterais e no fundo
        if (lv >= 3) {
            BlockState hedge = st == SettlementStyle.DESERT ? s(Blocks.POTTED_CACTUS) : leaves(p.leaves);
            BlockState[] pots = st == SettlementStyle.DESERT
                    ? new BlockState[] {s(Blocks.POTTED_DEAD_BUSH), s(Blocks.POTTED_ORANGE_TULIP)}
                    : st == SettlementStyle.ORIENTAL
                    ? new BlockState[] {s(Blocks.POTTED_BAMBOO), s(Blocks.POTTED_CHERRY_SAPLING)}
                    : st == SettlementStyle.ELVEN
                    ? new BlockState[] {s(Blocks.POTTED_AZALEA), s(Blocks.POTTED_LILY_OF_THE_VALLEY)}
                    : st == SettlementStyle.GOTHIC
                    ? new BlockState[] {s(Blocks.POTTED_WITHER_ROSE), s(Blocks.POTTED_ALLIUM)}
                    : st == SettlementStyle.DWARVEN
                    ? new BlockState[] {s(Blocks.POTTED_BROWN_MUSHROOM), s(Blocks.POTTED_RED_MUSHROOM)}
                    : st == SettlementStyle.NORDIC
                    ? new BlockState[] {s(Blocks.POTTED_FERN), s(Blocks.POTTED_BLUE_ORCHID)}
                    : new BlockState[] {s(Blocks.POTTED_POPPY), s(Blocks.POTTED_CORNFLOWER)};
            int i = 0;
            for (int z = 1; z < sz - 1; z += 2) {
                BlockState b = (i++ % 2 == 0) ? hedge : pots[(z / 2) % 2];
                orn(m, sx, sz, -1, 1, z, b);
                orn(m, sx, sz, sx, 1, z, b);
            }
            for (int x = 1; x < sx - 1; x += 2) {
                orn(m, sx, sz, x, 1, -1, (x / 2) % 2 == 0 ? hedge : pots[0]);
            }
        }
        // 4+: estandartes da cor do estilo no meio das laterais e do fundo
        if (lv >= 4) {
            int[][] spots = {{-1, sz / 2}, {sx, sz / 2}, {cx, -1}};
            for (int[] sp : spots) {
                orn(m, sx, sz, sp[0], 1, sp[1], p.trim);
                orn(m, sx, sz, sp[0], 2, sp[1], s(p.banner));
            }
        }
        // 2+: postes de lanterna nos 4 cantos (5: base de ouro/esmeralda/cobre)
        int[][] corners = {{-1, -1}, {sx, -1}, {-1, sz}, {sx, sz}};
        for (int[] c : corners) {
            int y = 1;
            if (lv >= 5) orn(m, sx, sz, c[0], y++, c[1], p.rich);
            orn(m, sx, sz, c[0], y++, c[1], s(p.fence));
            orn(m, sx, sz, c[0], y++, c[1], s(p.fence));
            orn(m, sx, sz, c[0], y, c[1], lv >= 5 && (c[1] == -1) ? lantern(Blocks.SOUL_LANTERN) : lantern(Blocks.LANTERN));
        }
        // 5: mais luzes — postes extras a 1/4 e 3/4 das laterais, com bastão do fim no topo
        if (lv >= 5) {
            int[] zs = {Math.max(1, sz / 4), Math.min(sz - 2, (3 * sz) / 4)};
            for (int z : zs) {
                for (int x : new int[] {-1, sx}) {
                    orn(m, sx, sz, x, 1, z, p.rich);
                    orn(m, sx, sz, x, 2, z, s(p.fence));
                    orn(m, sx, sz, x, 3, z, s(Blocks.END_ROD));
                }
            }
        }
        List<Templates.Entry> out = new ArrayList<>(m.size());
        boolean soul = st == SettlementStyle.GOTHIC;
        for (Map.Entry<BlockPos, BlockState> e : m.entrySet()) {
            out.add(new Templates.Entry(e.getKey(), soul ? soul(e.getValue()) : e.getValue(), null));
        }
        return out;
    }

    /** Um bloco do anel de enfeites (só na faixa em volta, nunca na frente da porta). */
    private static void orn(Map<BlockPos, BlockState> m, int sx, int sz, int x, int y, int z, BlockState st) {
        if (y < 1 || x < -1 || x > sx || z < -1 || z > sz) return;
        boolean ring = x == -1 || x == sx || z == -1 || z == sz;
        if (!ring) return;
        if (z == sz && Math.abs(x - sx / 2) <= 2) return;
        m.put(new BlockPos(x, y, z), st);
    }

    // =====================================================================================================
    // Grade de blocos e ajudantes de estado
    // =====================================================================================================

    /** Grade do molde em montagem (o último bloco posto numa posição vence; null/ar apaga). */
    static final class G {
        final int sx, sz;
        final Map<BlockPos, BlockState> m = new HashMap<>();

        G(int sx, int sz) {
            this.sx = sx;
            this.sz = sz;
        }

        boolean in(int x, int y, int z) {
            return x >= 0 && z >= 0 && x < sx && z < sz && y >= 0 && y < MAX_Y;
        }

        void set(int x, int y, int z, @Nullable BlockState st) {
            if (!in(x, y, z)) return;
            BlockPos p = new BlockPos(x, y, z);
            if (st == null || st.isAir()) m.remove(p);
            else m.put(p, st);
        }

        void clear(int x, int y, int z) {
            set(x, y, z, null);
        }

        @Nullable
        BlockState get(int x, int y, int z) {
            return m.get(new BlockPos(x, y, z));
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

    static BlockState s(Block b) {
        return b.defaultBlockState();
    }

    static BlockState stair(Block b, Direction f, Half h) {
        return b.defaultBlockState().setValue(StairBlock.FACING, f).setValue(StairBlock.HALF, h);
    }

    static BlockState stair(Block b, Direction f) {
        return stair(b, f, Half.BOTTOM);
    }

    static BlockState slab(Block b, SlabType t) {
        return b.defaultBlockState().setValue(SlabBlock.TYPE, t);
    }

    /** Tronco/pilar no eixo pedido (blocos sem eixo passam direto). */
    static BlockState axis(BlockState st, Direction.Axis a) {
        return st.hasProperty(BlockStateProperties.AXIS) ? st.setValue(BlockStateProperties.AXIS, a) : st;
    }

    /** Vira o bloco para a direção (horizontal ou qualquer, conforme o bloco). */
    static BlockState face(BlockState st, Direction f) {
        if (st.hasProperty(BlockStateProperties.HORIZONTAL_FACING) && f.getAxis().isHorizontal())
            return st.setValue(BlockStateProperties.HORIZONTAL_FACING, f);
        if (st.hasProperty(BlockStateProperties.FACING)) return st.setValue(BlockStateProperties.FACING, f);
        return st;
    }

    static BlockState face(Block b, Direction f) {
        return face(s(b), f);
    }

    static BlockState lantern(Block b) {
        return b.defaultBlockState().setValue(LanternBlock.HANGING, false);
    }

    static BlockState hanging(Block b) {
        return b.defaultBlockState().setValue(LanternBlock.HANGING, true);
    }

    static BlockState torch(Direction f) {
        return Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, f);
    }

    static BlockState leaves(Block b) {
        BlockState st = b.defaultBlockState();
        return st.hasProperty(LeavesBlock.PERSISTENT) ? st.setValue(LeavesBlock.PERSISTENT, true) : st;
    }

    static BlockState wallBanner(Block b, Direction f) {
        return b.defaultBlockState().setValue(WallBannerBlock.FACING, f);
    }

    static BlockState grindstone(Direction f) {
        return Blocks.GRINDSTONE.defaultBlockState().setValue(GrindstoneBlock.FACE, AttachFace.FLOOR)
                .setValue(GrindstoneBlock.FACING, f);
    }

    static BlockState bell(Direction f) {
        return Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR).setValue(BellBlock.FACING, f);
    }

    static BlockState waterCauldron() {
        return Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3);
    }

    static BlockState ladder(Direction f) {
        return Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, f);
    }

    /** Porta de 2 metades olhando para o sul (+Z). */
    static void door(G g, int x, int y, int z, Block door) {
        BlockState d = door.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH);
        g.set(x, y, z, d.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        g.set(x, y + 1, z, d.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    /** Cama: pé em (x, y, z), cabeceira um bloco na direção f. */
    static void bed(G g, int x, int y, int z, Direction f, Block bed) {
        BlockState b = bed.defaultBlockState().setValue(BedBlock.FACING, f);
        g.set(x, y, z, b.setValue(BedBlock.PART, BedPart.FOOT));
        g.set(x + f.getStepX(), y, z + f.getStepZ(), b.setValue(BedBlock.PART, BedPart.HEAD));
    }

    /** Lustre: corrente presa no teto (yCeil) e lanterna pendurada logo abaixo. */
    static void chandelier(G g, int x, int yCeil, int z, Block lantern) {
        g.set(x, yCeil - 1, z, s(Blocks.CHAIN));
        g.set(x, yCeil - 2, z, hanging(lantern));
    }

    static boolean pane(@Nullable BlockState st) {
        return st != null && (st.is(Blocks.GLASS_PANE) || st.getBlock().getDescriptionId().endsWith("glass_pane"));
    }

    /** Ruído determinístico (mesmo molde sempre igual). */
    static int hash(int x, int y, int z, int salt) {
        int h = x * 73856093 ^ y * 19349663 ^ z * 83492791 ^ salt * 0x9E3779B1;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return h & 0x7fffffff;
    }

    /** Lado da planta por nível (ímpar: a porta fica no meio). */
    static int size(int lv) {
        return new int[] {9, 9, 11, 11, 13}[lv - 1];
    }

    /** Altura de um andar (piso a piso): 4 nos níveis baixos, 5 a partir do 3. */
    static int sh(int lv) {
        return lv >= 3 ? 5 : 4;
    }

    // =====================================================================================================
    // Paletas dos estilos
    // =====================================================================================================

    /** Paleta de um estilo num nível (campos mutáveis: alguns prédios trocam peças, ex.: o curandeiro branco). */
    static final class P {
        SettlementStyle style;
        int lv;
        BlockState floor, base, wall, pillar, beam, trim, plank, roofFull, gable, accent, wool, rich;
        Block roofStair, roofSlab, slab, stairs, fence, door, glass, banner, wallBanner, carpet, bed, log, slog, leaves;
        boolean flat, steep;
        /** onda 5: beiral curvo (Oriental), pináculos (Sombrio), folhas no telhado (Élfico). */
        boolean curved, spire, leafy;
    }

    static P pal(SettlementStyle style, int lv) {
        P p = new P();
        p.style = style;
        p.lv = lv;
        switch (style) {
            case DESERT -> {
                p.floor = s(lv >= 3 ? Blocks.CUT_SANDSTONE : Blocks.SMOOTH_SANDSTONE);
                p.base = s(lv >= 3 ? Blocks.CUT_SANDSTONE : Blocks.SANDSTONE);
                p.wall = s(lv >= 3 ? Blocks.SMOOTH_SANDSTONE : Blocks.SANDSTONE);
                p.pillar = s(lv >= 3 ? Blocks.CUT_SANDSTONE : Blocks.STRIPPED_ACACIA_LOG);
                p.beam = s(lv >= 3 ? Blocks.ORANGE_TERRACOTTA : Blocks.STRIPPED_ACACIA_LOG);
                p.trim = s(Blocks.CHISELED_SANDSTONE);
                p.plank = s(Blocks.ACACIA_PLANKS);
                p.roofStair = Blocks.SMOOTH_SANDSTONE_STAIRS;
                p.roofSlab = lv >= 3 ? Blocks.CUT_SANDSTONE_SLAB : Blocks.SMOOTH_SANDSTONE_SLAB;
                p.roofFull = s(Blocks.SMOOTH_SANDSTONE);
                p.slab = Blocks.ACACIA_SLAB;
                p.stairs = Blocks.ACACIA_STAIRS;
                p.fence = Blocks.ACACIA_FENCE;
                p.door = Blocks.ACACIA_DOOR;
                p.glass = lv >= 5 ? Blocks.ORANGE_STAINED_GLASS_PANE : Blocks.GLASS_PANE;
                p.banner = Blocks.ORANGE_BANNER;
                p.wallBanner = Blocks.ORANGE_WALL_BANNER;
                p.carpet = Blocks.ORANGE_CARPET;
                p.bed = Blocks.ORANGE_BED;
                p.log = Blocks.ACACIA_LOG;
                p.slog = Blocks.STRIPPED_ACACIA_LOG;
                p.leaves = Blocks.ACACIA_LEAVES;
                p.accent = s(lv >= 4 ? Blocks.WHITE_TERRACOTTA : Blocks.ORANGE_TERRACOTTA);
                p.wool = s(Blocks.ORANGE_WOOL);
                p.rich = s(Blocks.GOLD_BLOCK);
                p.flat = true;
            }
            case ORIENTAL -> {
                // madeira escura e vermelho: reboco branco entre pilares, telha de ardósia, janelas de "papel"
                p.floor = s(lv >= 3 ? Blocks.DARK_OAK_PLANKS : Blocks.SPRUCE_PLANKS);
                p.base = s(lv >= 5 ? Blocks.POLISHED_ANDESITE : lv >= 3 ? Blocks.ANDESITE : Blocks.COBBLESTONE);
                p.wall = s(lv >= 3 ? Blocks.WHITE_TERRACOTTA : Blocks.SPRUCE_PLANKS);
                p.pillar = s(lv >= 4 ? Blocks.STRIPPED_MANGROVE_LOG : Blocks.DARK_OAK_LOG);
                p.beam = s(lv >= 3 ? Blocks.STRIPPED_DARK_OAK_LOG : Blocks.DARK_OAK_LOG);
                p.trim = s(lv >= 3 ? Blocks.MANGROVE_PLANKS : Blocks.DARK_OAK_PLANKS);
                p.plank = s(Blocks.DARK_OAK_PLANKS);
                if (lv >= 3) {
                    p.roofStair = Blocks.DEEPSLATE_TILE_STAIRS;
                    p.roofSlab = Blocks.DEEPSLATE_TILE_SLAB;
                    p.roofFull = s(Blocks.DEEPSLATE_TILES);
                } else {
                    p.roofStair = Blocks.DARK_OAK_STAIRS;
                    p.roofSlab = Blocks.DARK_OAK_SLAB;
                    p.roofFull = s(Blocks.DARK_OAK_PLANKS);
                }
                p.slab = Blocks.DARK_OAK_SLAB;
                p.stairs = Blocks.DARK_OAK_STAIRS;
                p.fence = lv >= 3 ? Blocks.MANGROVE_FENCE : Blocks.DARK_OAK_FENCE;
                p.door = lv >= 3 ? Blocks.MANGROVE_DOOR : Blocks.DARK_OAK_DOOR;
                p.glass = lv >= 5 ? Blocks.RED_STAINED_GLASS_PANE : Blocks.WHITE_STAINED_GLASS_PANE;
                p.banner = Blocks.RED_BANNER;
                p.wallBanner = Blocks.RED_WALL_BANNER;
                p.carpet = Blocks.RED_CARPET;
                p.bed = Blocks.RED_BED;
                p.log = Blocks.CHERRY_LOG;
                p.slog = Blocks.STRIPPED_CHERRY_LOG;
                p.leaves = Blocks.CHERRY_LEAVES;
                p.accent = s(Blocks.RED_TERRACOTTA);
                p.wool = s(Blocks.RED_WOOL);
                p.rich = s(Blocks.GOLD_BLOCK);
                p.curved = true;
            }
            case ELVEN -> {
                // madeira clara, pedra com musgo, telhado verde-azulado (madeira distorcida → cobre oxidado) com folhas
                p.floor = s(Blocks.BIRCH_PLANKS);
                p.base = s(lv >= 3 ? Blocks.MOSSY_STONE_BRICKS : Blocks.MOSSY_COBBLESTONE);
                p.wall = s(lv >= 5 ? Blocks.QUARTZ_BRICKS : lv >= 3 ? Blocks.STRIPPED_BIRCH_WOOD : Blocks.BIRCH_PLANKS);
                p.pillar = s(lv >= 3 ? Blocks.STRIPPED_OAK_LOG : Blocks.OAK_LOG);
                p.beam = p.pillar;
                p.trim = s(lv >= 3 ? Blocks.MOSSY_STONE_BRICKS : Blocks.OAK_PLANKS);
                p.plank = s(Blocks.BIRCH_PLANKS);
                if (lv >= 3) {
                    p.roofStair = Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS;
                    p.roofSlab = Blocks.WAXED_OXIDIZED_CUT_COPPER_SLAB;
                    p.roofFull = s(Blocks.WAXED_OXIDIZED_CUT_COPPER);
                } else {
                    p.roofStair = Blocks.WARPED_STAIRS;
                    p.roofSlab = Blocks.WARPED_SLAB;
                    p.roofFull = s(Blocks.WARPED_PLANKS);
                }
                p.slab = Blocks.BIRCH_SLAB;
                p.stairs = Blocks.BIRCH_STAIRS;
                p.fence = Blocks.BIRCH_FENCE;
                p.door = Blocks.BIRCH_DOOR;
                p.glass = lv >= 5 ? Blocks.LIME_STAINED_GLASS_PANE : Blocks.GLASS_PANE;
                p.banner = Blocks.LIME_BANNER;
                p.wallBanner = Blocks.LIME_WALL_BANNER;
                p.carpet = Blocks.LIME_CARPET;
                p.bed = Blocks.LIME_BED;
                p.log = Blocks.BIRCH_LOG;
                p.slog = Blocks.STRIPPED_BIRCH_LOG;
                p.leaves = lv >= 4 ? Blocks.FLOWERING_AZALEA_LEAVES : Blocks.AZALEA_LEAVES;
                p.accent = s(Blocks.MOSS_BLOCK);
                p.wool = s(Blocks.LIME_WOOL);
                p.rich = s(Blocks.EMERALD_BLOCK);
                p.leafy = true;
            }
            case GOTHIC -> {
                // pedra escura, telhado íngreme carmesim, vitrais roxos; as luzes viram de alma em relight()
                p.floor = s(lv >= 3 ? Blocks.DARK_OAK_PLANKS : Blocks.SPRUCE_PLANKS);
                p.base = s(lv >= 3 ? Blocks.POLISHED_DEEPSLATE : Blocks.COBBLED_DEEPSLATE);
                p.wall = s(lv >= 5 ? Blocks.POLISHED_BLACKSTONE_BRICKS : lv >= 3 ? Blocks.DEEPSLATE_BRICKS : Blocks.SPRUCE_PLANKS);
                p.pillar = s(lv >= 3 ? Blocks.POLISHED_BASALT : Blocks.DARK_OAK_LOG);
                p.beam = s(lv >= 3 ? Blocks.POLISHED_DEEPSLATE : Blocks.DARK_OAK_LOG);
                p.trim = s(lv >= 3 ? Blocks.CHISELED_DEEPSLATE : Blocks.STRIPPED_DARK_OAK_WOOD);
                p.plank = s(Blocks.DARK_OAK_PLANKS);
                if (lv >= 3) {
                    p.roofStair = Blocks.CRIMSON_STAIRS;
                    p.roofSlab = Blocks.CRIMSON_SLAB;
                    p.roofFull = s(Blocks.CRIMSON_PLANKS);
                } else {
                    p.roofStair = Blocks.DEEPSLATE_TILE_STAIRS;
                    p.roofSlab = Blocks.DEEPSLATE_TILE_SLAB;
                    p.roofFull = s(Blocks.DEEPSLATE_TILES);
                }
                p.slab = Blocks.DARK_OAK_SLAB;
                p.stairs = Blocks.DARK_OAK_STAIRS;
                p.fence = Blocks.DARK_OAK_FENCE;
                p.door = Blocks.DARK_OAK_DOOR;
                p.glass = lv >= 3 ? Blocks.PURPLE_STAINED_GLASS_PANE : Blocks.GLASS_PANE;
                p.banner = Blocks.PURPLE_BANNER;
                p.wallBanner = Blocks.PURPLE_WALL_BANNER;
                p.carpet = Blocks.PURPLE_CARPET;
                p.bed = Blocks.PURPLE_BED;
                p.log = Blocks.DARK_OAK_LOG;
                p.slog = Blocks.STRIPPED_DARK_OAK_LOG;
                p.leaves = Blocks.DARK_OAK_LEAVES;
                p.accent = s(Blocks.PURPLE_TERRACOTTA);
                p.wool = s(Blocks.PURPLE_WOOL);
                p.rich = s(Blocks.AMETHYST_BLOCK);
                p.steep = true;
                p.spire = true;
            }
            case DWARVEN -> {
                // pedra talhada com faixas de cobre, piso de pedra, telhado de cobre; madeira só no que precisa
                p.floor = s(lv >= 5 ? Blocks.POLISHED_DEEPSLATE : lv >= 3 ? Blocks.POLISHED_ANDESITE : Blocks.SMOOTH_STONE);
                p.base = s(lv >= 3 ? Blocks.POLISHED_DEEPSLATE : Blocks.COBBLED_DEEPSLATE);
                p.wall = s(lv >= 5 ? Blocks.DEEPSLATE_BRICKS : lv >= 3 ? Blocks.STONE_BRICKS : Blocks.COBBLESTONE);
                p.pillar = s(lv >= 3 ? Blocks.POLISHED_DEEPSLATE : Blocks.SPRUCE_LOG);
                p.beam = s(lv >= 3 ? Blocks.WAXED_CUT_COPPER : Blocks.SPRUCE_LOG);
                p.trim = s(lv >= 3 ? Blocks.WAXED_CHISELED_COPPER : Blocks.CHISELED_STONE_BRICKS);
                p.plank = s(Blocks.SPRUCE_PLANKS);
                if (lv >= 3) {
                    p.roofStair = Blocks.WAXED_CUT_COPPER_STAIRS;
                    p.roofSlab = Blocks.WAXED_CUT_COPPER_SLAB;
                    p.roofFull = s(Blocks.WAXED_CUT_COPPER);
                } else {
                    p.roofStair = Blocks.COBBLED_DEEPSLATE_STAIRS;
                    p.roofSlab = Blocks.COBBLED_DEEPSLATE_SLAB;
                    p.roofFull = s(Blocks.COBBLED_DEEPSLATE);
                }
                p.slab = Blocks.SPRUCE_SLAB;
                p.stairs = Blocks.SPRUCE_STAIRS;
                p.fence = Blocks.SPRUCE_FENCE;
                p.door = lv >= 5 ? Blocks.DARK_OAK_DOOR : Blocks.SPRUCE_DOOR;
                p.glass = lv >= 5 ? Blocks.ORANGE_STAINED_GLASS_PANE : Blocks.GLASS_PANE;
                p.banner = Blocks.YELLOW_BANNER;
                p.wallBanner = Blocks.YELLOW_WALL_BANNER;
                p.carpet = Blocks.YELLOW_CARPET;
                p.bed = Blocks.BROWN_BED;
                p.log = Blocks.SPRUCE_LOG;
                p.slog = Blocks.STRIPPED_SPRUCE_LOG;
                p.leaves = Blocks.SPRUCE_LEAVES;
                p.accent = s(Blocks.WAXED_COPPER_BLOCK);
                p.wool = s(Blocks.BROWN_WOOL);
                p.rich = s(Blocks.GOLD_BLOCK);
            }
            case NORDIC -> {
                p.floor = s(Blocks.SPRUCE_PLANKS);
                p.base = s(lv >= 3 ? Blocks.MOSSY_COBBLESTONE : Blocks.COBBLESTONE);
                p.wall = s(lv >= 5 ? Blocks.STRIPPED_SPRUCE_WOOD : Blocks.SPRUCE_PLANKS);
                p.pillar = s(lv >= 3 ? Blocks.DARK_OAK_LOG : Blocks.SPRUCE_LOG);
                p.beam = p.pillar;
                p.trim = s(lv >= 3 ? Blocks.POLISHED_ANDESITE : Blocks.STRIPPED_SPRUCE_WOOD);
                p.plank = s(Blocks.SPRUCE_PLANKS);
                p.roofStair = Blocks.DARK_OAK_STAIRS;
                p.roofSlab = Blocks.DARK_OAK_SLAB;
                p.roofFull = s(Blocks.DARK_OAK_PLANKS);
                p.slab = Blocks.SPRUCE_SLAB;
                p.stairs = Blocks.SPRUCE_STAIRS;
                p.fence = Blocks.SPRUCE_FENCE;
                p.door = Blocks.SPRUCE_DOOR;
                p.glass = lv >= 5 ? Blocks.CYAN_STAINED_GLASS_PANE : Blocks.GLASS_PANE;
                p.banner = Blocks.CYAN_BANNER;
                p.wallBanner = Blocks.CYAN_WALL_BANNER;
                p.carpet = Blocks.CYAN_CARPET;
                p.bed = Blocks.CYAN_BED;
                p.log = Blocks.SPRUCE_LOG;
                p.slog = Blocks.STRIPPED_SPRUCE_LOG;
                p.leaves = Blocks.SPRUCE_LEAVES;
                p.accent = s(Blocks.CYAN_WOOL);
                p.wool = s(Blocks.WHITE_WOOL);
                p.rich = s(Blocks.WAXED_OXIDIZED_COPPER);
                p.steep = true;
            }
            default -> {
                p.floor = s(lv >= 5 ? Blocks.DARK_OAK_PLANKS : lv >= 3 ? Blocks.SPRUCE_PLANKS : Blocks.OAK_PLANKS);
                p.base = s(lv >= 5 ? Blocks.POLISHED_ANDESITE : Blocks.COBBLESTONE);
                p.wall = s(lv >= 3 ? Blocks.STONE_BRICKS : Blocks.OAK_PLANKS);
                p.pillar = s(lv >= 3 ? Blocks.SPRUCE_LOG : Blocks.OAK_LOG);
                p.beam = p.pillar;
                p.trim = s(lv >= 3 ? Blocks.CHISELED_STONE_BRICKS : Blocks.SPRUCE_PLANKS);
                p.plank = s(Blocks.SPRUCE_PLANKS);
                if (lv >= 5) {
                    p.roofStair = Blocks.DEEPSLATE_TILE_STAIRS;
                    p.roofSlab = Blocks.DEEPSLATE_TILE_SLAB;
                    p.roofFull = s(Blocks.DEEPSLATE_TILES);
                } else if (lv >= 3) {
                    p.roofStair = Blocks.DARK_OAK_STAIRS;
                    p.roofSlab = Blocks.DARK_OAK_SLAB;
                    p.roofFull = s(Blocks.DARK_OAK_PLANKS);
                } else {
                    p.roofStair = Blocks.SPRUCE_STAIRS;
                    p.roofSlab = Blocks.SPRUCE_SLAB;
                    p.roofFull = s(Blocks.SPRUCE_PLANKS);
                }
                p.slab = Blocks.SPRUCE_SLAB;
                p.stairs = Blocks.SPRUCE_STAIRS;
                p.fence = Blocks.OAK_FENCE;
                p.door = lv >= 5 ? Blocks.DARK_OAK_DOOR : Blocks.SPRUCE_DOOR;
                p.glass = Blocks.GLASS_PANE;
                p.banner = Blocks.BLUE_BANNER;
                p.wallBanner = Blocks.BLUE_WALL_BANNER;
                p.carpet = Blocks.BLUE_CARPET;
                p.bed = Blocks.BLUE_BED;
                p.log = Blocks.OAK_LOG;
                p.slog = Blocks.STRIPPED_OAK_LOG;
                p.leaves = Blocks.OAK_LEAVES;
                p.accent = s(Blocks.BLUE_TERRACOTTA);
                p.wool = s(Blocks.BLUE_WOOL);
                p.rich = s(Blocks.GOLD_BLOCK);
            }
        }
        p.gable = p.wall;
        return p;
    }

    // =====================================================================================================
    // Peças de construção: casca (paredes + andares), telhados, luzes
    // =====================================================================================================

    /**
     * Casca de um prédio retangular [x0..x1] x [z0..z1]: chão, base, paredes com pilares nos cantos, vigas em cada
     * andar, pisos internos, janelas, porta no meio do lado +Z e escada de mão no canto de trás (x0+1, z0+1).
     * Devolve a altura do topo das paredes (forro em y = H).
     */
    static int shell(G g, P p, int x0, int z0, int x1, int z1, int storeys) {
        int sh = sh(p.lv);
        int H = sh * storeys;
        int cx = (x0 + x1) / 2, cz = (z0 + z1) / 2;
        g.fill(x0, 0, z0, x1, 0, z1, p.floor);
        for (int y = 1; y <= H; y++) g.ring(x0, z0, x1, z1, y, y == 1 ? p.base : p.wall);
        // vigas em cada andar (troncos deitados no sentido da parede) e pisos internos / forro
        for (int k = 1; k <= storeys; k++) {
            int y = sh * k;
            for (int x = x0; x <= x1; x++) {
                g.set(x, y, z0, axis(p.beam, Direction.Axis.X));
                g.set(x, y, z1, axis(p.beam, Direction.Axis.X));
            }
            for (int z = z0; z <= z1; z++) {
                g.set(x0, y, z, axis(p.beam, Direction.Axis.Z));
                g.set(x1, y, z, axis(p.beam, Direction.Axis.Z));
            }
            g.fill(x0 + 1, y, z0 + 1, x1 - 1, y, z1 - 1, p.plank);
        }
        // janelas (vidro), simétricas em volta do meio de cada parede
        for (int k = 0; k < storeys; k++) {
            int y0 = sh * k;
            for (int yw = y0 + 2; yw <= y0 + (sh >= 5 ? 3 : 2); yw++) {
                for (int x = x0 + 2; x <= x1 - 2; x++) {
                    int dx = Math.abs(x - cx);
                    boolean win = dx % 3 == 2;
                    if (win || (dx == 0 && p.lv >= 3)) g.set(x, yw, z0, s(p.glass));
                    if (win && !(k == 0 && dx <= 1)) g.set(x, yw, z1, s(p.glass));
                }
                for (int z = z0 + 2; z <= z1 - 2; z++) {
                    if (Math.abs(z - cz) % 3 == 2) {
                        g.set(x0, yw, z, s(p.glass));
                        g.set(x1, yw, z, s(p.glass));
                    }
                }
            }
        }
        // pilares: cantos sempre; no nível 4+ também no meio das laterais e do fundo
        for (int y = 1; y <= H; y++) {
            BlockState pl = axis(p.pillar, Direction.Axis.Y);
            g.set(x0, y, z0, pl);
            g.set(x1, y, z0, pl);
            g.set(x0, y, z1, pl);
            g.set(x1, y, z1, pl);
            if (p.lv >= 4 && x1 - x0 >= 8) {
                g.set(x0, y, cz, pl);
                g.set(x1, y, cz, pl);
                g.set(cx, y, z0, pl);
            }
        }
        // porta (e verga decorada no nível 3+)
        door(g, cx, 1, z1, p.door);
        if (p.lv >= 3 && sh >= 5) g.set(cx, 3, z1, p.trim);
        if (p.lv >= 5) {
            g.set(x0, H, z1, p.rich);
            g.set(x1, H, z1, p.rich);
        }
        // escada de mão até o último andar
        if (storeys > 1) {
            for (int y = 1; y <= sh * (storeys - 1); y++) g.set(x0 + 1, y, z0 + 1, ladder(Direction.SOUTH));
        }
        return H;
    }

    /**
     * Telhado sobre a casca: plano com parapeito ameado (deserto), de duas águas com cumeeira no sentido Z (reino) ou
     * de duas águas íngreme (nórdico). As empenas (triângulos) ficam na frente e no fundo.
     */
    static void roof(G g, P p, int x0, int z0, int x1, int z1, int H) {
        int cx = (x0 + x1) / 2, cz = (z0 + z1) / 2;
        if (p.flat) {
            g.fill(x0 + 1, H, z0 + 1, x1 - 1, H, z1 - 1, p.roofFull);
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    if (x != x0 && x != x1 && z != z0 && z != z1) continue;
                    g.set(x, H + 1, z, (x + z) % 2 == 0 ? p.wall : slab(p.roofSlab, SlabType.BOTTOM));
                }
            }
            if (p.lv >= 3) {
                int[][] cs = {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}};
                for (int[] c : cs) {
                    g.set(c[0], H + 1, c[1], p.trim);
                    g.set(c[0], H + 2, c[1], p.lv >= 5 ? p.rich : slab(p.roofSlab, SlabType.BOTTOM));
                }
            }
            // cúpula no meio (4+), com remate de ouro no 5
            if (p.lv >= 4 && x1 - x0 >= 6 && z1 - z0 >= 6) {
                g.fill(cx - 1, H + 1, cz - 1, cx + 1, H + 1, cz + 1, p.wall);
                g.fill(cx - 1, H + 2, cz - 1, cx + 1, H + 2, cz + 1, p.accent);
                g.set(cx, H + 3, cz, p.lv >= 5 ? p.rich : p.trim);
                if (p.lv >= 5) g.set(cx, H + 4, cz, s(Blocks.LIGHTNING_ROD));
            }
            // v1.0: pérgola de sombra num canto do terraço (níveis sem cúpula) — o canto muda de prédio para prédio
            if (p.lv >= 2 && p.lv < 4 && x1 - x0 >= 7 && z1 - z0 >= 7) {
                boolean left = hash(x1 - x0, H, z1 - z0, p.lv) % 2 == 0;
                int ax = left ? x0 + 1 : x1 - 3, az = z0 + 1;
                int[][] posts = {{ax, az}, {ax + 2, az}, {ax, az + 2}, {ax + 2, az + 2}};
                for (int[] c : posts) {
                    g.set(c[0], H + 1, c[1], s(p.fence));
                    g.set(c[0], H + 2, c[1], s(p.fence));
                }
                g.fill(ax, H + 3, az, ax + 2, H + 3, az + 2, slab(p.roofSlab, SlabType.BOTTOM));
                g.set(ax + 1, H + 1, az + 1, s(p.carpet));
            }
            return;
        }
        int r = p.steep ? 2 : 1;
        int ridgeY = H + 1;
        for (int i = 0; ; i++) {
            int xa = x0 + i, xb = x1 - i;
            if (xa > xb) break;
            int yb = H + 1 + i * r;
            for (int z = z0; z <= z1; z++) {
                if (xa == xb) {
                    g.set(xa, yb, z, p.roofFull);
                    g.set(xa, yb + 1, z, slab(p.roofSlab, SlabType.BOTTOM));
                    ridgeY = yb;
                } else if (p.steep) {
                    g.set(xa, yb, z, p.roofFull);
                    g.set(xb, yb, z, p.roofFull);
                    g.set(xa, yb + 1, z, stair(p.roofStair, Direction.EAST));
                    g.set(xb, yb + 1, z, stair(p.roofStair, Direction.WEST));
                } else {
                    g.set(xa, yb, z, stair(p.roofStair, Direction.EAST));
                    g.set(xb, yb, z, stair(p.roofStair, Direction.WEST));
                }
            }
            // empenas (frente e fundo)
            for (int x = xa + 1; x <= xb - 1; x++) {
                for (int yy = yb; yy < yb + r; yy++) {
                    g.set(x, yy, z0, p.gable);
                    g.set(x, yy, z1, p.gable);
                }
            }
            if (xa == xb) break;
        }
        if (p.curved && x1 - x0 >= 4) {
            // Oriental: o beiral abre e as pontas sobem (laje baixa ao longo da beira, laje alta nos 4 cantos) e as
            // pontas da cumeeira viram para cima
            for (int z = z0; z <= z1; z++) {
                boolean tip = z == z0 || z == z1;
                BlockState lip = slab(p.roofSlab, tip ? SlabType.TOP : SlabType.BOTTOM);
                g.set(x0, H + 1, z, lip);
                g.set(x1, H + 1, z, lip);
            }
            if ((x1 - x0) % 2 == 0) {
                g.set(cx, ridgeY + 1, z0, stair(p.roofStair, Direction.NORTH));
                g.set(cx, ridgeY + 1, z1, stair(p.roofStair, Direction.SOUTH));
            }
        }
        if (p.spire && x1 - x0 >= 4) {
            // Sombrio: pináculos nos 4 cantos e agulhas de ferro nas pontas da cumeeira
            BlockState pin = s(Blocks.DEEPSLATE_BRICK_WALL);
            int[][] cs = {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}};
            for (int[] c : cs) {
                g.set(c[0], H + 1, c[1], p.base);
                g.set(c[0], H + 2, c[1], pin);
                g.set(c[0], H + 3, c[1], pin);
                if (p.lv >= 3) g.set(c[0], H + 4, c[1], s(Blocks.IRON_BARS));
            }
            if ((x1 - x0) % 2 == 0) {
                for (int z : new int[] {z0, z1}) {
                    g.set(cx, ridgeY + 1, z, p.roofFull);
                    g.set(cx, ridgeY + 2, z, s(Blocks.IRON_BARS));
                    if (p.lv >= 4) g.set(cx, ridgeY + 3, z, s(Blocks.IRON_BARS));
                }
            }
        }
        if (p.leafy) {
            // Élfico: a copa toma o telhado — tufos de folhas sorteados sobre as águas (mais perto da beira)
            BlockState leaf = leaves(p.leaves);
            for (int i = 0; x0 + i < x1 - i; i++) {
                int yb = H + 1 + i * r;
                for (int z = z0; z <= z1; z++) {
                    int chance = i == 0 ? 3 : 6;
                    if (hash(x0 + i, yb, z, 91 + p.lv) % chance == 0) g.set(x0 + i, yb + 1, z, leaf);
                    if (hash(x1 - i, yb, z, 57 + p.lv) % chance == 0) g.set(x1 - i, yb + 1, z, leaf);
                }
            }
        }
        if (p.lv >= 2 && x1 - x0 >= 4) {
            g.set(cx, H + 2, z0, s(p.glass));
            g.set(cx, H + 2, z1, s(p.glass));
        }
        if (p.lv >= 4) {
            g.set(cx, H + 3, z1, p.accent);
            g.set(cx, H + 3, z0, p.accent);
        }
        if (p.lv >= 5) {
            g.set(cx, ridgeY, z0, p.rich);
            g.set(cx, ridgeY, z1, p.rich);
        }
        // v1.0: chaminé de pedra soltando fumaça, de um lado ou do outro (cada prédio sai um pouco diferente)
        if (x1 - x0 >= 6 && z1 - z0 >= 5) {
            boolean left = hash(x1 - x0, H, z1 - z0, p.lv + 7 * p.style.ordinal()) % 2 == 0;
            int hx = left ? x0 + 2 : x1 - 2, hz = z0 + 2;
            int top = H + 2 + 2 * r + (p.steep ? 1 : 0);
            for (int y = H + 1; y <= top; y++) g.set(hx, y, hz, p.base);
            g.set(hx, top + 1, hz, s(Blocks.CAMPFIRE));
        }
    }

    /** Luzes de um andar: tochas nas laterais (nível 1) ou lanternas/lustres pendurados no forro (2+). */
    static void lights(G g, P p, int x0, int z0, int x1, int z1, int y0) {
        int sh = sh(p.lv);
        int cx = (x0 + x1) / 2, cz = (z0 + z1) / 2;
        if (p.lv <= 1) {
            g.set(x0 + 1, y0 + 3, cz, torch(Direction.EAST));
            g.set(x1 - 1, y0 + 3, cz, torch(Direction.WEST));
            return;
        }
        List<int[]> spots = new ArrayList<>();
        spots.add(new int[] {cx, cz});
        if (p.lv >= 3 && x1 - x0 >= 10) {
            spots.add(new int[] {x0 + 3, z0 + 3});
            spots.add(new int[] {x1 - 3, z0 + 3});
            spots.add(new int[] {x0 + 3, z1 - 3});
            spots.add(new int[] {x1 - 3, z1 - 3});
        }
        for (int[] sp : spots) {
            if (sh >= 5) chandelier(g, sp[0], y0 + sh, sp[1], Blocks.LANTERN);
            else g.set(sp[0], y0 + sh - 1, sp[1], hanging(Blocks.LANTERN));
        }
    }

    // =====================================================================================================
    // Prédios com casca (celeiro, biblioteca, guilda, curandeiro, oficina) e a serraria aberta
    // =====================================================================================================

    /** Celeiro: fardos de feno, barris, composteiras e mezanino (2+); no nível 5 ganha um silo nos fundos. */
    private static Templates.Tpl granary(SettlementStyle st, int lv) {
        P p = pal(st, lv);
        int w = size(lv), sh = sh(lv), cx = w / 2;
        G g = new G(w, w);
        int z0 = lv >= 5 ? 4 : 0;
        int storeys = lv >= 2 ? 2 : 1;
        g.fill(0, 0, 0, w - 1, 0, w - 1, p.floor);
        int H = shell(g, p, 0, z0, w - 1, w - 1, storeys);
        roof(g, p, 0, z0, w - 1, w - 1, H);
        BlockState hay = s(Blocks.HAY_BLOCK);
        BlockState barrel = face(Blocks.BARREL, Direction.UP);
        if (storeys > 1) {
            int zl = (z0 + w - 1) / 2; // borda do mezanino
            // mezanino só na metade de trás: a frente fica com pé-direito duplo
            for (int x = 1; x <= w - 2; x++) for (int z = zl + 1; z <= w - 2; z++) g.clear(x, sh, z);
            for (int x = 2; x <= w - 2; x++) g.set(x, sh + 1, zl, s(p.fence));
            for (int x = 2; x <= w - 3; x++) {
                for (int z = z0 + 1; z < zl; z++) {
                    int hh = hash(x, 0, z, 7) % 3;
                    if (hh > 0) g.set(x, sh + 1, z, axis(hay, hh == 1 ? Direction.Axis.X : Direction.Axis.Z));
                    if (hh == 2 && z < zl - 1) g.set(x, sh + 2, z, hay);
                }
            }
            lights(g, p, 0, z0, w - 1, zl, sh);
            // luz embaixo do mezanino e lanterna comprida pendurada na parte aberta
            g.set(cx, sh - 1, z0 + 2, hanging(Blocks.LANTERN));
            for (int y = sh + 1; y <= H - 1; y++) g.set(cx, y, zl + 2, s(Blocks.CHAIN));
            g.set(cx, sh, zl + 2, hanging(Blocks.LANTERN));
        } else {
            lights(g, p, 0, z0, w - 1, w - 1, 0);
        }
        // fardos nas laterais do térreo
        for (int z = z0 + 2; z <= w - 4; z++) {
            g.set(1, 1, z, hay);
            g.set(w - 2, 1, z, hay);
            if (hash(1, 2, z, 3) % 2 == 0) g.set(1, 2, z, axis(hay, Direction.Axis.X));
            if (hash(w, 2, z, 3) % 2 == 0) g.set(w - 2, 2, z, axis(hay, Direction.Axis.X));
        }
        // barris no fundo, baú, composteiras perto da porta
        g.set(w - 2, 1, z0 + 1, barrel);
        g.set(w - 3, 1, z0 + 1, barrel);
        g.set(w - 2, 2, z0 + 1, barrel);
        if (lv >= 3) g.set(cx - 2, 1, z0 + 1, face(Blocks.CHEST, Direction.SOUTH));
        g.set(1, 1, w - 2, s(Blocks.COMPOSTER));
        g.set(w - 2, 1, w - 2, s(Blocks.COMPOSTER));
        if (lv >= 4) {
            g.set(w - 3, 1, w - 2, s(Blocks.COMPOSTER));
            g.set(2, 1, w - 2, barrel);
        }
        if (lv >= 5) {
            // silo redondo atrás (centro em x=cx, z=2), com cúpula e para-raios de cobre
            int sc = 2, top = H + 4;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    int d2 = dx * dx + dz * dz, x = cx + dx, z = sc + dz;
                    if (d2 >= 4 && d2 <= 5) {
                        for (int y = 1; y <= top; y++) g.set(x, y, z, y % 4 == 0 ? p.accent : p.wall);
                        g.set(x, top + 1, z, slab(p.roofSlab, SlabType.BOTTOM));
                    } else if (d2 <= 2) {
                        g.set(x, top + 1, z, p.roofFull);
                        if (dz < 0) g.set(x, 1, z, hay);
                    }
                }
            }
            g.set(cx, top + 2, sc, s(Blocks.WAXED_COPPER_BLOCK));
            g.set(cx, top + 3, sc, s(Blocks.LIGHTNING_ROD));
            g.clear(cx, 1, z0);
            g.clear(cx, 2, z0);
            // pátio dos lados do silo
            g.set(1, 1, 1, hay);
            g.set(1, 2, 1, hay);
            g.set(2, 1, 1, axis(hay, Direction.Axis.X));
            g.set(1, 1, 2, s(Blocks.COMPOSTER));
            g.set(w - 2, 1, 1, s(Blocks.COMPOSTER));
            g.set(w - 3, 1, 1, barrel);
            g.set(w - 2, 1, 2, barrel);
        }
        return g.tpl();
    }

    /** Biblioteca: paredes de estantes, atris, mesas de leitura com cadeiras, lustres; 2 andares no nível 4+. */
    private static Templates.Tpl library(SettlementStyle st, int lv) {
        P p = pal(st, lv);
        int w = size(lv), sh = sh(lv), cx = w / 2;
        G g = new G(w, w);
        int storeys = lv >= 4 ? 2 : 1;
        int H = shell(g, p, 0, 0, w - 1, w - 1, storeys);
        roof(g, p, 0, 0, w - 1, w - 1, H);
        for (int k = 0; k < storeys; k++) {
            int y0 = k * sh;
            lights(g, p, 0, 0, w - 1, w - 1, y0);
            // estantes encostadas nas paredes (onde não há janela)
            for (int y = y0 + 1; y <= y0 + sh - 2; y++) {
                for (int z = 2; z <= w - 3; z++) {
                    if (!pane(g.get(0, y, z))) g.set(1, y, z, s(Blocks.BOOKSHELF));
                    if (!pane(g.get(w - 1, y, z))) g.set(w - 2, y, z, s(Blocks.BOOKSHELF));
                }
                for (int x = 2; x <= w - 3; x++) if (!pane(g.get(x, y, 0))) g.set(x, y, 1, s(Blocks.BOOKSHELF));
            }
            // mesas de leitura (laje alta) com cadeiras (escadas) dos dois lados, corredor no meio
            for (int zt = 4; zt + 1 <= w - 4; zt += 3) {
                for (int x : new int[] {cx - 2, cx - 1, cx + 1, cx + 2}) {
                    g.set(x, y0 + 1, zt, slab(p.slab, SlabType.TOP));
                    g.set(x, y0 + 1, zt - 1, stair(p.stairs, Direction.NORTH));
                    g.set(x, y0 + 1, zt + 1, stair(p.stairs, Direction.SOUTH));
                }
                if (lv >= 3) {
                    g.set(cx - 2, y0 + 2, zt, lantern(Blocks.LANTERN));
                    g.set(cx + 2, y0 + 2, zt, lantern(Blocks.LANTERN));
                }
            }
            if (k == 0) {
                g.set(cx, 1, 2, face(Blocks.LECTERN, Direction.SOUTH));
                if (lv >= 3) {
                    g.set(cx - 2, 1, 2, face(Blocks.LECTERN, Direction.SOUTH));
                    g.set(cx + 2, 1, 2, face(Blocks.LECTERN, Direction.SOUTH));
                    for (int z = 3; z <= w - 2; z++) g.set(cx, 1, z, s(p.carpet));
                }
            } else {
                g.set(cx, y0 + 1, 2, lv >= 5 ? s(Blocks.ENCHANTING_TABLE) : face(Blocks.LECTERN, Direction.SOUTH));
                for (int z = 3; z <= w - 2; z++) g.set(cx, y0 + 1, z, s(Blocks.RED_CARPET));
            }
        }
        if (lv >= 5) {
            // estantes com acabamento de ouro no topo das colunas da frente
            g.set(1, sh - 1, w - 2, p.rich);
            g.set(w - 2, sh - 1, w - 2, p.rich);
        }
        return g.tpl();
    }

    /** Guilda: salão com mesa comprida, mural de avisos, estandartes, sino; dormitório em cima no nível 4+. */
    private static Templates.Tpl guild(SettlementStyle st, int lv) {
        P p = pal(st, lv);
        int w = size(lv), sh = sh(lv), cx = w / 2, cz = w / 2;
        G g = new G(w, w);
        int storeys = lv >= 4 ? 2 : 1;
        int H = shell(g, p, 0, 0, w - 1, w - 1, storeys);
        roof(g, p, 0, 0, w - 1, w - 1, H);
        lights(g, p, 0, 0, w - 1, w - 1, 0);
        // mesa comprida com cadeiras e a cadeira do mestre na cabeceira
        for (int z = 3; z <= w - 4; z++) {
            g.set(cx, 1, z, slab(p.slab, SlabType.TOP));
            g.set(cx - 1, 1, z, stair(p.stairs, Direction.WEST));
            g.set(cx + 1, 1, z, stair(p.stairs, Direction.EAST));
        }
        g.set(cx, 1, 2, stair(p.stairs, Direction.NORTH));
        if (lv >= 3) {
            g.set(cx, 2, 3, lantern(Blocks.LANTERN));
            g.set(cx, 2, w - 4, lantern(Blocks.LANTERN));
        }
        // mural de avisos (tábuas de abeto) no fundo, com tochas e o estandarte da guilda
        for (int x = cx - 2; x <= cx + 2; x++) for (int y = 2; y <= 3; y++) g.set(x, y, 1, s(Blocks.SPRUCE_PLANKS));
        g.set(cx - 2, 3, 2, torch(Direction.SOUTH));
        g.set(cx + 2, 3, 2, torch(Direction.SOUTH));
        if (lv >= 2) g.set(cx, 3, 2, wallBanner(p.wallBanner, Direction.SOUTH));
        if (lv >= 3) {
            for (int y = 1; y <= 3; y++) {
                g.set(cx - 3, y, 1, p.trim);
                g.set(cx + 3, y, 1, p.trim);
            }
        }
        if (lv >= 5) {
            g.set(cx - 3, 4, 1, p.rich);
            g.set(cx + 3, 4, 1, p.rich);
        }
        // estandartes nas paredes laterais
        if (lv >= 2) {
            for (int z : new int[] {cz - 3, cz, cz + 3}) {
                if (z < 2 || z > w - 3) continue;
                if (!pane(g.get(0, sh - 1, z))) g.set(1, sh - 1, z, wallBanner(p.wallBanner, Direction.EAST));
                if (!pane(g.get(w - 1, sh - 1, z))) g.set(w - 2, sh - 1, z, wallBanner(p.wallBanner, Direction.WEST));
            }
        }
        // sino perto da porta, balcão com baú e barris
        g.set(w - 2, 1, w - 2, p.trim);
        g.set(w - 2, 2, w - 2, bell(Direction.SOUTH));
        g.set(1, 1, w - 2, face(Blocks.CHEST, Direction.EAST));
        g.set(1, 1, w - 3, face(Blocks.BARREL, Direction.UP));
        if (lv >= 3) g.set(1, 2, w - 3, face(Blocks.BARREL, Direction.UP));
        // dormitório dos aventureiros
        if (storeys > 1) {
            int y0 = sh;
            lights(g, p, 0, 0, w - 1, w - 1, y0);
            for (int z = 3; z <= w - 3; z += 2) {
                bed(g, 2, y0 + 1, z, Direction.WEST, p.bed);
                bed(g, w - 3, y0 + 1, z, Direction.EAST, p.bed);
            }
            g.set(cx, y0 + 1, 1, face(Blocks.CHEST, Direction.SOUTH));
            g.set(cx + 1, y0 + 1, 1, face(Blocks.BARREL, Direction.UP));
        }
        return g.tpl();
    }

    /** Curandeiro: clínica branca com camas, suportes de poção, caldeirões e vasos; cruz vermelha no chão. */
    private static Templates.Tpl hospital(SettlementStyle st, int lv) {
        P p = pal(st, lv);
        p.wall = s(lv <= 2 ? Blocks.WHITE_TERRACOTTA : lv <= 4 ? Blocks.WHITE_CONCRETE : Blocks.QUARTZ_BLOCK);
        p.gable = p.wall;
        if (lv >= 3) p.floor = s(lv >= 5 ? Blocks.SMOOTH_QUARTZ : Blocks.POLISHED_DIORITE);
        if (lv >= 5) p.pillar = s(Blocks.QUARTZ_PILLAR);
        int w = size(lv), sh = sh(lv), cx = w / 2, cz = w / 2;
        G g = new G(w, w);
        int storeys = lv >= 5 ? 2 : 1;
        int H = shell(g, p, 0, 0, w - 1, w - 1, storeys);
        roof(g, p, 0, 0, w - 1, w - 1, H);
        int beds = new int[] {2, 3, 4, 5, 8}[lv - 1];
        BlockState[] flowers = {s(Blocks.POTTED_RED_TULIP), s(Blocks.POTTED_ALLIUM), s(Blocks.POTTED_FERN),
                s(Blocks.POTTED_OXEYE_DAISY)};
        for (int k = 0; k < storeys; k++) {
            int y0 = k * sh;
            lights(g, p, 0, 0, w - 1, w - 1, y0);
            // camas encostadas nas paredes laterais, criado-mudo com vaso entre elas
            for (int z = 2; z <= w - 4 && beds > 0; z += 2) {
                for (int side = 0; side < 2 && beds > 0; side++, beds--) {
                    int x = side == 0 ? 1 : w - 2;
                    if (side == 0) bed(g, 2, y0 + 1, z, Direction.WEST, Blocks.WHITE_BED);
                    else bed(g, w - 3, y0 + 1, z, Direction.EAST, Blocks.WHITE_BED);
                    if (lv >= 2 && z + 1 <= w - 3) {
                        g.set(x, y0 + 1, z + 1, slab(p.slab, SlabType.TOP));
                        g.set(x, y0 + 2, z + 1, flowers[(z + side) % flowers.length]);
                    }
                }
            }
            // bancada de poções no fundo
            g.set(cx, y0 + 1, 1, waterCauldron());
            g.set(cx - 1, y0 + 1, 1, s(Blocks.BREWING_STAND));
            g.set(cx + 1, y0 + 1, 1, s(Blocks.BREWING_STAND));
            if (lv >= 3) {
                g.set(cx - 2, y0 + 1, 1, face(Blocks.CHEST, Direction.SOUTH));
                g.set(cx + 2, y0 + 1, 1, waterCauldron());
            }
            if (lv >= 4 && cx - 3 >= 2) {
                g.set(cx - 3, y0 + 1, 1, s(Blocks.BREWING_STAND));
                g.set(cx + 3, y0 + 1, 1, face(Blocks.BARREL, Direction.UP));
            }
        }
        // cruz vermelha no chão (3+) e vasos ao lado da porta
        if (lv >= 3) {
            g.set(cx, 1, cz, s(Blocks.RED_CARPET));
            g.set(cx - 1, 1, cz, s(Blocks.RED_CARPET));
            g.set(cx + 1, 1, cz, s(Blocks.RED_CARPET));
            g.set(cx, 1, cz - 1, s(Blocks.RED_CARPET));
            g.set(cx, 1, cz + 1, s(Blocks.RED_CARPET));
        }
        g.set(cx - 2, 1, w - 2, s(Blocks.POTTED_POPPY));
        g.set(cx + 2, 1, w - 2, s(Blocks.POTTED_AZURE_BLUET));
        return g.tpl();
    }

    /** Oficina: bancadas de todos os ofícios nas paredes (mais a cada nível), mesa no meio, depósito em cima. */
    private static Templates.Tpl workshop(SettlementStyle st, int lv) {
        P p = pal(st, lv);
        int w = size(lv), sh = sh(lv), cx = w / 2, cz = w / 2;
        G g = new G(w, w);
        int storeys = lv >= 4 ? 2 : 1;
        int H = shell(g, p, 0, 0, w - 1, w - 1, storeys);
        roof(g, p, 0, 0, w - 1, w - 1, H);
        lights(g, p, 0, 0, w - 1, w - 1, 0);
        Block[] list = {Blocks.CRAFTING_TABLE, Blocks.FURNACE, Blocks.ANVIL, Blocks.SMITHING_TABLE, Blocks.GRINDSTONE,
                Blocks.LOOM, Blocks.CARTOGRAPHY_TABLE, Blocks.FLETCHING_TABLE, Blocks.BLAST_FURNACE, Blocks.STONECUTTER,
                Blocks.SMOKER, Blocks.BARREL, Blocks.CHEST};
        int count = Math.min(list.length, 3 + 2 * lv);
        List<int[]> slots = new ArrayList<>(); // x, z, direção (0=sul, 1=oeste, 2=leste)
        for (int x = 2; x <= w - 3; x++) slots.add(new int[] {x, 1, 0});
        for (int z = 2; z <= w - 4; z++) slots.add(new int[] {w - 2, z, 1});
        for (int z = 2; z <= w - 4; z++) slots.add(new int[] {1, z, 2});
        for (int i = 0; i < count && i < slots.size(); i++) {
            int[] sl = slots.get(i);
            Direction f = sl[2] == 0 ? Direction.SOUTH : sl[2] == 1 ? Direction.WEST : Direction.EAST;
            Block b = list[i];
            BlockState bs = b == Blocks.GRINDSTONE ? grindstone(f) : b == Blocks.BARREL ? face(b, Direction.UP) : face(b, f);
            g.set(sl[0], 1, sl[1], bs);
        }
        if (lv >= 3) {
            for (int x = cx - 1; x <= cx + 1; x++) g.set(x, 1, cz, slab(p.slab, SlabType.TOP));
            g.set(cx - 1, 2, cz, lantern(Blocks.LANTERN));
        }
        if (lv >= 5) {
            g.set(cx + 1, 1, cz, s(Blocks.CRAFTER));
            g.set(cx - 1, 1, cz - 2, s(Blocks.WAXED_CUT_COPPER));
            g.set(cx + 1, 1, cz - 2, s(Blocks.WAXED_CUT_COPPER));
        }
        if (storeys > 1) {
            int y0 = sh;
            lights(g, p, 0, 0, w - 1, w - 1, y0);
            for (int x = 2; x <= w - 3; x++) g.set(x, y0 + 1, 1, face(Blocks.BARREL, Direction.UP));
            for (int z = 3; z <= w - 4; z += 2) {
                g.set(1, y0 + 1, z, face(Blocks.CHEST, Direction.EAST));
                g.set(w - 2, y0 + 1, z, face(Blocks.CHEST, Direction.WEST));
            }
        }
        return g.tpl();
    }

    /**
     * Onda 10 — Oficina Mecânica (garagem dos veículos): portão largo de 3 blocos na frente, baia com elevador de
     * placas de ferro no meio, parede de ferramentas e, a cada nível, mais oficina: correntes e lanterna sobre a baia (2),
     * guincho de corrente e detalhes de cobre (3), segundo andar de peças e o pilar de recarga de mana com ametista (4),
     * para-raios no telhado, cobre encerado e o autofabricador (5).
     */
    private static Templates.Tpl garage(SettlementStyle st, int lv) {
        P p = pal(st, lv);
        int w = size(lv), sh = sh(lv), cx = w / 2, cz = w / 2;
        G g = new G(w, w);
        int storeys = lv >= 4 ? 2 : 1;
        int H = shell(g, p, 0, 0, w - 1, w - 1, storeys);
        roof(g, p, 0, 0, w - 1, w - 1, H);
        // portão largo (3 de largura, 3 de altura): os veículos entram e saem pela frente
        for (int x = cx - 1; x <= cx + 1; x++) {
            for (int y = 1; y <= 3; y++) g.clear(x, y, w - 1);
        }
        g.set(cx - 2, 3, w - 1, axis(p.pillar, Direction.Axis.Y));
        g.set(cx + 2, 3, w - 1, axis(p.pillar, Direction.Axis.Y));
        // baia: elevador de placas de ferro no meio, faixa de laje em volta
        for (int x = cx - 1; x <= cx + 1; x++) {
            for (int z = cz - 1; z <= cz + 1; z++) g.set(x, 1, z, s(Blocks.IRON_TRAPDOOR));
        }
        g.set(cx, 0, cz, s(lv >= 3 ? Blocks.CUT_COPPER : Blocks.SMOOTH_STONE));
        // parede de ferramentas no fundo (mais a cada nível)
        Block[] tools = {Blocks.CRAFTING_TABLE, Blocks.ANVIL, Blocks.BARREL, Blocks.SMITHING_TABLE, Blocks.GRINDSTONE,
                Blocks.BLAST_FURNACE, Blocks.CHEST, Blocks.STONECUTTER, Blocks.BARREL};
        int count = Math.min(tools.length, 3 + lv);
        int placed = 0;
        for (int x = 1; x <= w - 2 && placed < count; x++) {
            if (x == cx) continue;   // o meio do fundo fica livre (pilar de recarga no nível 4+)
            Block b = tools[placed++];
            BlockState bs = b == Blocks.GRINDSTONE ? grindstone(Direction.SOUTH) : b == Blocks.BARREL ? face(b, Direction.UP)
                    : face(b, Direction.SOUTH);
            g.set(x, 1, 1, bs);
        }
        // pneus/rodas de reserva: barris deitados nas laterais, perto do portão
        g.set(1, 1, w - 3, face(Blocks.BARREL, Direction.EAST));
        g.set(w - 2, 1, w - 3, face(Blocks.BARREL, Direction.WEST));
        lights(g, p, 0, 0, w - 1, w - 1, 0);
        if (lv >= 2) {
            // correntes e lanterna penduradas sobre a baia
            g.set(cx - 1, sh - 1, cz, s(Blocks.CHAIN));
            g.set(cx + 1, sh - 1, cz, s(Blocks.CHAIN));
            g.set(cx - 1, sh - 2, cz, hanging(Blocks.LANTERN));
        }
        if (lv >= 3) {
            // guincho: corrente descendo do forro com um gancho de ferro, detalhes de cobre ao lado do portão
            for (int y = sh - 1; y >= 3; y--) g.set(cx + 1, y, cz - 1, s(Blocks.CHAIN));
            g.set(cx + 1, 2, cz - 1, s(Blocks.IRON_BARS));
            g.set(cx - 2, 1, w - 2, s(Blocks.WAXED_CUT_COPPER));
            g.set(cx + 2, 1, w - 2, s(Blocks.WAXED_CUT_COPPER));
        }
        if (lv >= 4) {
            // pilar de recarga de mana: bloco de ametista com uma haste brilhando em cima
            g.set(cx, 1, 1, s(Blocks.AMETHYST_BLOCK));
            g.set(cx, 2, 1, face(Blocks.END_ROD, Direction.UP));
            // andar de cima: depósito de peças
            int y0 = sh;
            lights(g, p, 0, 0, w - 1, w - 1, y0);
            for (int x = 2; x <= w - 3; x++) g.set(x, y0 + 1, 1, face(Blocks.BARREL, Direction.UP));
            for (int z = 3; z <= w - 4; z += 2) {
                g.set(1, y0 + 1, z, face(Blocks.CHEST, Direction.EAST));
                g.set(w - 2, y0 + 1, z, face(Blocks.CHEST, Direction.WEST));
            }
        }
        if (lv >= 5) {
            g.set(cx - 2, 1, cz + 2, s(Blocks.CRAFTER));
            g.set(cx + 2, 1, cz - 2, s(Blocks.WAXED_COPPER_GRATE));
            g.set(cx - 2, 1, cz - 2, s(Blocks.WAXED_COPPER_GRATE));
            // para-raios no topo do telhado (acima da cumeeira)
            int top = 0;
            for (int y = H; y < 24; y++) if (g.get(cx, y, cz) != null) top = y;
            g.set(cx, top + 1, cz, s(Blocks.LIGHTNING_ROD));
        }
        return g.tpl();
    }

    /** Serraria: galpão aberto (postes + telhado), pilhas de toras normais e descascadas, linha de serra no meio. */
    private static Templates.Tpl sawmill(SettlementStyle st, int lv) {
        P p = pal(st, lv);
        int w = size(lv), cx = w / 2;
        G g = new G(w, w);
        int H = sh(lv) + (lv >= 4 ? 1 : 0);
        g.fill(0, 0, 0, w - 1, 0, w - 1, p.floor);
        // postes simétricos (cantos e a cada 4 a partir do meio)
        List<Integer> ps = new ArrayList<>();
        ps.add(0);
        ps.add(w - 1);
        for (int k = -2; k <= 2; k++) {
            int v = cx + 4 * k;
            if (v >= 2 && v <= w - 3 && !ps.contains(v)) ps.add(v);
        }
        BlockState post = axis(p.pillar, Direction.Axis.Y);
        for (int v : ps) {
            for (int y = 1; y < H; y++) {
                g.set(v, y, 0, post);
                if (Math.abs(v - cx) > 1) g.set(v, y, w - 1, post);
                g.set(0, y, v, post);
                g.set(w - 1, y, v, post);
            }
        }
        // vigas, forro e telhado
        for (int x = 0; x < w; x++) {
            g.set(x, H, 0, axis(p.beam, Direction.Axis.X));
            g.set(x, H, w - 1, axis(p.beam, Direction.Axis.X));
        }
        for (int z = 0; z < w; z++) {
            g.set(0, H, z, axis(p.beam, Direction.Axis.Z));
            g.set(w - 1, H, z, axis(p.beam, Direction.Axis.Z));
        }
        g.fill(1, H, 1, w - 2, H, w - 2, p.plank);
        roof(g, p, 0, 0, w - 1, w - 1, H);
        // meia parede de tábuas no fundo; cerquinha nas laterais (2+)
        for (int x = 1; x <= w - 2; x++) {
            if (ps.contains(x)) continue;
            g.set(x, 1, 0, p.base);
            g.set(x, 2, 0, p.wall);
        }
        if (lv >= 2) {
            for (int z = 1; z <= w - 2; z++) {
                if (ps.contains(z)) continue;
                g.set(0, 1, z, s(p.fence));
                g.set(w - 1, 1, z, s(p.fence));
            }
        }
        // pilhas de toras (pirâmides): normais a oeste, descascadas a leste
        BlockState lg = axis(s(p.log), Direction.Axis.Z), sl = axis(s(p.slog), Direction.Axis.Z);
        for (int z = 2; z <= w - 5; z++) {
            g.set(1, 1, z, lg);
            g.set(2, 1, z, lg);
            g.set(3, 1, z, lg);
            g.set(1, 2, z, lg);
            g.set(2, 2, z, lg);
            g.set(1, 3, z, lg);
            g.set(w - 2, 1, z, sl);
            g.set(w - 3, 1, z, sl);
            g.set(w - 4, 1, z, sl);
            g.set(w - 2, 2, z, sl);
            g.set(w - 3, 2, z, sl);
            if (lv >= 3) g.set(w - 2, 3, z, sl);
        }
        // linha da serra no meio: tora -> tora descascada -> cortador -> tábuas
        g.set(cx, 1, 1, lg);
        g.set(cx, 1, 2, sl);
        g.set(cx, 1, 3, face(Blocks.STONECUTTER, Direction.SOUTH));
        g.set(cx, 1, 4, slab(p.slab, SlabType.BOTTOM));
        if (lv >= 3) g.set(cx, 2, 1, lg);
        // canto da frente: bancada, rebolo, composteira (serragem), barril
        g.set(1, 1, w - 2, s(Blocks.CRAFTING_TABLE));
        g.set(w - 2, 1, w - 2, grindstone(Direction.NORTH));
        if (lv >= 2) g.set(1, 1, w - 3, s(Blocks.COMPOSTER));
        if (lv >= 3) {
            g.set(w - 2, 1, w - 3, face(Blocks.BARREL, Direction.UP));
            g.set(1, 1, 1, face(Blocks.BARREL, Direction.UP));
            g.set(w - 2, 1, 1, face(Blocks.CHEST, Direction.SOUTH));
        }
        if (lv >= 4) g.set(2, 1, w - 2, s(Blocks.SMITHING_TABLE));
        // luzes penduradas no forro
        g.set(cx, H - 1, w / 2, hanging(Blocks.LANTERN));
        if (lv >= 3) {
            g.set(cx, H - 1, 2, hanging(Blocks.LANTERN));
            g.set(cx, H - 1, w - 3, hanging(Blocks.LANTERN));
        }
        if (lv >= 5) {
            for (int v : ps) {
                g.set(v, 1, 0, p.rich);
                g.set(0, 1, v, p.rich);
                g.set(w - 1, 1, v, p.rich);
            }
        }
        return g.tpl();
    }

    // =====================================================================================================
    // Torre de magia, arena, porto, mina
    // =====================================================================================================

    /** Dentro do octógono de "raio" r (centro 0,0)? */
    private static boolean oct(int dx, int dz, int r) {
        int ax = Math.abs(dx), az = Math.abs(dz);
        return ax <= r && az <= r && ax + az <= r + r / 2;
    }

    /** Torre de magia (academia): anel octogonal, andares com escada de mão, topo com mesa de encantamento. */
    private static Templates.Tpl magicTower(SettlementStyle st, int lv) {
        P p = pal(st, lv);
        int w = new int[] {9, 9, 9, 11, 13}[lv - 1];
        int n = lv == 1 ? 2 : 3;
        int sh = sh(lv);
        int c = w / 2, R = c;
        int H = n * sh;
        G g = new G(w, w);
        BlockState wall = p.flat ? p.wall : st == SettlementStyle.NORDIC ? s(Blocks.COBBLESTONE) : s(Blocks.STONE_BRICKS);
        if (lv >= 4 && st == SettlementStyle.NORDIC) wall = s(Blocks.STONE_BRICKS);
        Block[] cut = masonry(st, lv);
        if (cut != null) wall = s(cut[0]);
        Block glass = lv >= 3 ? Blocks.PURPLE_STAINED_GLASS_PANE : Blocks.GLASS_PANE;
        g.fill(0, 0, 0, w - 1, 0, w - 1, p.base);
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < w; z++) {
                int dx = x - c, dz = z - c;
                if (!oct(dx, dz, R)) continue;
                boolean ring = !oct(dx, dz, R - 1);
                if (ring) {
                    for (int y = 1; y <= H; y++) {
                        BlockState b = y == 1 ? p.base : wall;
                        if (y % sh == 0 && lv >= 3) b = p.trim;
                        g.set(x, y, z, b);
                    }
                } else {
                    g.set(x, 0, z, lv >= 3 ? s(Blocks.POLISHED_DEEPSLATE) : p.floor);
                    for (int k = 1; k <= n; k++) g.set(x, k * sh, z, k == n ? p.plank : p.floor);
                }
            }
        }
        // janelas nos 4 pontos cardeais de cada andar (menos a porta na frente do térreo)
        for (int k = 0; k < n; k++) {
            int y0 = k * sh;
            for (int yw = y0 + 2; yw <= y0 + (sh >= 5 ? 3 : 2); yw++) {
                g.set(c, yw, 0, s(glass));
                g.set(0, yw, c, s(glass));
                g.set(w - 1, yw, c, s(glass));
                if (k > 0) g.set(c, yw, w - 1, s(glass));
            }
        }
        door(g, c, 1, w - 1, p.door);
        if (sh >= 5) g.set(c, 3, w - 1, p.trim);
        // escada de mão encostada na parede norte, ao lado da janela
        int lx = c - 1;
        for (int y = 1; y <= (n - 1) * sh; y++) g.set(lx, y, 1, ladder(Direction.SOUTH));
        // térreo: estantes na volta, atril, baú
        for (int x = 1; x < w - 1; x++) {
            for (int z = 1; z < w - 1; z++) {
                int dx = x - c, dz = z - c;
                if (!oct(dx, dz, R - 1) || oct(dx, dz, R - 2)) continue;
                if (z >= w - 3 && Math.abs(dx) <= 1) continue; // passagem da porta
                if (z == 1 && Math.abs(x - lx) <= 0) continue; // escada
                g.set(x, 1, z, s(Blocks.BOOKSHELF));
                if (lv >= 2 && hash(x, 2, z, 5) % 2 == 0) g.set(x, 2, z, s(Blocks.BOOKSHELF));
            }
        }
        g.set(c, 1, c, face(Blocks.LECTERN, Direction.SOUTH));
        g.set(c, sh - 1, c, hanging(Blocks.LANTERN));
        // andares do meio: laboratório de poções / quarto do mago
        for (int k = 1; k <= n - 2; k++) {
            int y0 = k * sh;
            g.set(c - 1, y0 + 1, c - 1, s(Blocks.BREWING_STAND));
            g.set(c + 1, y0 + 1, c - 1, waterCauldron());
            g.set(c + 1, y0 + 1, c + 1, face(Blocks.CHEST, Direction.WEST));
            bed(g, c - 1, y0 + 1, c + 1, Direction.WEST, Blocks.PURPLE_BED);
            g.set(c, y0 + sh - 1, c, hanging(Blocks.SOUL_LANTERN));
        }
        // último andar: mesa de encantamento no meio, estantes a 2 blocos, ametistas
        int yt = (n - 1) * sh;
        g.set(c, yt + 1, c, s(Blocks.ENCHANTING_TABLE));
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != 2) continue;
                if (dz == -2 && dx == 0) continue; // vão para entrar no círculo (do lado da escada)
                g.set(c + dx, yt + 1, c + dz, s(Blocks.BOOKSHELF));
                if (lv >= 3 && (Math.abs(dx) + Math.abs(dz)) % 2 == 1) g.set(c + dx, yt + 2, c + dz, s(Blocks.BOOKSHELF));
            }
        }
        if (lv >= 2) {
            for (int[] d : new int[][] {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
                g.set(c + d[0], yt + 2, c + d[1], s(lv >= 4 ? Blocks.AMETHYST_CLUSTER : Blocks.AMETHYST_BLOCK));
            }
        }
        if (lv >= 3) {
            g.set(c, yt + sh - 1, c, s(Blocks.CHAIN));
            g.set(c, yt + sh - 2, c, hanging(Blocks.SOUL_LANTERN));
            for (int x = 1; x < w - 1; x++) {
                for (int z = 1; z < w - 1; z++) {
                    int dx = x - c, dz = z - c;
                    if (!oct(dx, dz, R - 1) || oct(dx, dz, R - 2) || (dz < 0 && Math.abs(dx) <= 1)) continue;
                    if (hash(x, yt, z, 9) % 3 == 0) {
                        g.set(x, yt + 1, z, s(Blocks.AMETHYST_BLOCK));
                        g.set(x, yt + 2, z, s(Blocks.AMETHYST_CLUSTER));
                    } else if (hash(x, yt, z, 9) % 3 == 1) {
                        g.set(x, yt + 1, z, s(Blocks.PURPUR_PILLAR));
                    }
                }
            }
        } else {
            g.set(c, yt + sh - 1, c, hanging(Blocks.LANTERN));
        }
        // telhado cônico em degraus (anéis do octógono) e pináculo
        BlockState rf = lv >= 3 ? s(Blocks.PURPUR_BLOCK) : p.roofFull;
        int top = H;
        for (int i = 0; i <= R; i++) {
            int y = H + 1 + i;
            top = y;
            for (int x = 0; x < w; x++) {
                for (int z = 0; z < w; z++) {
                    int dx = x - c, dz = z - c;
                    if (!oct(dx, dz, R - i)) continue;
                    if (i < R && oct(dx, dz, R - i - 1)) continue;
                    g.set(x, y, z, i == R ? (lv >= 5 ? s(Blocks.AMETHYST_BLOCK) : rf) : rf);
                }
            }
            if (i == 0 && lv >= 2) {
                // beiral: lajes por fora do primeiro anel não cabem na planta; põe acento no anel
                for (int x = 0; x < w; x++) for (int z = 0; z < w; z++) {
                    int dx = x - c, dz = z - c;
                    if (oct(dx, dz, R) && !oct(dx, dz, R - 1) && (dx == 0 || dz == 0)) g.set(x, y, z, p.trim);
                }
            }
        }
        g.set(c, top + 1, c, lv >= 4 ? s(Blocks.END_ROD) : s(Blocks.LIGHTNING_ROD));
        if (lv >= 5) {
            g.set(c, H, 0, p.rich);
            g.set(0, H, c, p.rich);
            g.set(w - 1, H, c, p.rich);
            g.set(c, H, w - 1, p.rich);
        }
        return g.tpl();
    }

    /** Arena: campo redondo de areia/terra grossa, barreira, arquibancadas nos cantos, braseiros, alvos. */
    private static Templates.Tpl arena(SettlementStyle st, int lv) {
        P p = pal(st, lv);
        int w = new int[] {11, 11, 13, 13, 13}[lv - 1];
        int c = w / 2;
        double rf = c - 1.5;
        int tiers = lv >= 4 ? 3 : 2;
        G g = new G(w, w);
        Block stairB = st == SettlementStyle.DESERT ? Blocks.SANDSTONE_STAIRS
                : lv >= 3 ? Blocks.STONE_BRICK_STAIRS : p.stairs;
        BlockState seatBase = lv >= 3 && st != SettlementStyle.DESERT ? s(Blocks.STONE_BRICKS) : p.base;
        BlockState barrier = lv >= 3 ? s(st == SettlementStyle.DESERT ? Blocks.SANDSTONE_WALL : Blocks.STONE_BRICK_WALL)
                : s(p.fence);
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < w; z++) {
                int dx = x - c, dz = z - c;
                double d = Math.sqrt(dx * dx + dz * dz);
                boolean edge = x == 0 || z == 0 || x == w - 1 || z == w - 1;
                boolean gate = Math.abs(dx) <= 1 && dz > 0;
                if (d <= rf) {
                    int hh = hash(x, 0, z, 11) % 4;
                    BlockState f = st == SettlementStyle.DESERT ? s(hh == 0 ? Blocks.SMOOTH_SANDSTONE : Blocks.SAND)
                            : s(hh == 0 ? Blocks.SAND : Blocks.COARSE_DIRT);
                    g.set(x, 0, z, f);
                    continue;
                }
                g.set(x, 0, z, p.base);
                if (gate) {
                    g.set(x, 0, z, s(Blocks.DIRT_PATH));
                    if (lv >= 3 && Math.abs(dx) == 1 && !edge) g.set(x, 0, z, p.base);
                    continue;
                }
                if (d <= rf + 1) {
                    g.set(x, 1, z, barrier);
                    if (lv >= 4) g.set(x, 1, z, seatBase);
                    if (lv >= 4) g.set(x, 2, z, barrier);
                    continue;
                }
                if (edge) {
                    for (int y = 1; y <= tiers + 1; y++) g.set(x, y, z, y == 1 ? p.base : p.wall);
                    continue;
                }
                int t = Math.min(tiers, (int) Math.ceil(d - (rf + 1)));
                for (int y = 1; y < t; y++) g.set(x, y, z, seatBase);
                Direction out = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST)
                        : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
                g.set(x, t, z, stair(stairB, out));
            }
        }
        // portão: pilares e lintel na entrada (3+), placas de alvo no fundo
        if (lv >= 3) {
            for (int y = 1; y <= tiers + 2; y++) {
                g.set(c - 2, y, w - 1, axis(p.pillar, Direction.Axis.Y));
                g.set(c + 2, y, w - 1, axis(p.pillar, Direction.Axis.Y));
            }
            for (int x = c - 2; x <= c + 2; x++) g.set(x, tiers + 3, w - 1, p.trim);
            g.set(c, tiers + 2, w - 1, hanging(Blocks.LANTERN));
        }
        int zb = (int) Math.floor(c - rf - 0.5);
        g.set(c, 1, zb, s(Blocks.TARGET));
        g.set(c, 2, zb, s(Blocks.TARGET));
        g.set(c - 2, 1, zb + 1, s(Blocks.HAY_BLOCK));
        g.set(c + 2, 1, zb + 1, s(Blocks.HAY_BLOCK));
        g.set(c - 2, 2, zb + 1, s(Blocks.TARGET));
        g.set(c + 2, 2, zb + 1, s(Blocks.TARGET));
        // braseiros (fogueiras) e estandartes no alto do muro
        int wy = tiers + 2;
        int[][] corners = {{0, 0}, {w - 1, 0}, {0, w - 1}, {w - 1, w - 1}};
        for (int[] cc : corners) {
            g.set(cc[0], wy - 1, cc[1], lv >= 5 ? p.rich : p.trim);
            g.set(cc[0], wy, cc[1], s(Blocks.CAMPFIRE));
        }
        if (lv >= 2) {
            for (int[] m : new int[][] {{c, 0}, {0, c}, {w - 1, c}}) {
                g.set(m[0], wy - 1, m[1], p.trim);
                g.set(m[0], wy, m[1], lv >= 4 ? s(p.banner) : s(Blocks.CAMPFIRE));
            }
        }
        if (lv >= 5) {
            for (int[] m : new int[][] {{c - 3, 0}, {c + 3, 0}, {0, c - 3}, {0, c + 3}, {w - 1, c - 3}, {w - 1, c + 3}}) {
                g.set(m[0], wy - 1, m[1], p.wall);
                g.set(m[0], wy, m[1], lantern(Blocks.LANTERN));
            }
        }
        return g.tpl();
    }

    /** Porto/pesqueiro: deque de tábuas com guarda-corpo, cabana de pesca, tanque de peixes, guindaste (3+), farol (5). */
    private static Templates.Tpl port(SettlementStyle st, int lv) {
        P p = pal(st, lv);
        int w = size(lv), d = 13, cx = w / 2;
        G g = new G(w, d);
        BlockState deck = st == SettlementStyle.DESERT ? s(Blocks.ACACIA_PLANKS) : s(Blocks.SPRUCE_PLANKS);
        g.fill(0, 0, 0, w - 1, 0, d - 1, deck);
        for (int x = 0; x < w; x++) {
            g.set(x, 0, 0, axis(s(p.log), Direction.Axis.X));
        }
        for (int z = 0; z < d; z++) {
            g.set(0, 0, z, axis(s(p.log), Direction.Axis.Z));
            g.set(w - 1, 0, z, axis(s(p.log), Direction.Axis.Z));
        }
        // guarda-corpo nas laterais e no fundo (vão de atracação no meio do fundo)
        for (int z = 0; z < d; z++) {
            g.set(0, 1, z, s(p.fence));
            g.set(w - 1, 1, z, s(p.fence));
        }
        for (int x = 0; x < w; x++) if (Math.abs(x - cx) > 1) g.set(x, 1, 0, s(p.fence));
        // postes de amarração com lanterna
        for (int z = 0; z < d; z += 4) {
            for (int x : new int[] {0, w - 1}) {
                g.set(x, 1, z, axis(s(p.log), Direction.Axis.Y));
                if (lv >= 2) g.set(x, 2, z, lantern(Blocks.LANTERN));
            }
        }
        // tanque de peixes (água parada, cercada pelo deque)
        int pz1 = lv >= 3 ? 6 : 4;
        for (int x = w - 4; x <= w - 2; x++) for (int z = 2; z <= pz1; z++) g.set(x, 0, z, s(Blocks.WATER));
        for (int z = 2; z <= pz1; z++) g.set(w - 5, 1, z, s(p.fence));
        g.set(w - 5, 2, 2, lantern(Blocks.LANTERN));
        // cabana de pesca à esquerda
        int hz0 = d - 8, hz1 = d - 4;
        int H = shell(g, p, 0, hz0, 4, hz1, 1);
        roof(g, p, 0, hz0, 4, hz1, H);
        g.set(1, 1, hz0 + 1, face(Blocks.BARREL, Direction.UP));
        g.set(3, 1, hz0 + 1, face(Blocks.CHEST, Direction.SOUTH));
        g.set(1, 1, hz0 + 2, lv >= 2 ? face(Blocks.SMOKER, Direction.EAST) : s(Blocks.CRAFTING_TABLE));
        g.set(3, 1, hz0 + 2, s(Blocks.COMPOSTER));
        g.set(2, H - 1, hz0 + 2, hanging(Blocks.LANTERN));
        // barris e caixotes perto do vão de atracação
        g.set(1, 1, 1, face(Blocks.BARREL, Direction.UP));
        g.set(2, 1, 1, face(Blocks.BARREL, Direction.UP));
        g.set(1, 2, 1, face(Blocks.BARREL, Direction.UP));
        if (lv >= 2) g.set(cx + 2, 1, 1, face(Blocks.BARREL, Direction.UP));
        // guindaste sobre o tanque (3+)
        if (lv >= 3) {
            for (int y = 1; y <= 5; y++) g.set(w - 1, y, 3, axis(s(p.log), Direction.Axis.Y));
            for (int x = w - 4; x <= w - 2; x++) g.set(x, 5, 3, s(p.fence));
            g.set(w - 4, 4, 3, s(Blocks.CHAIN));
            g.set(w - 4, 3, 3, hanging(Blocks.LANTERN));
        }
        // estandartes nos postes da frente (4+)
        if (lv >= 4) {
            g.set(0, 1, d - 1, axis(s(p.log), Direction.Axis.Y));
            g.set(w - 1, 1, d - 1, axis(s(p.log), Direction.Axis.Y));
            g.set(0, 2, d - 1, s(p.banner));
            g.set(w - 1, 2, d - 1, s(p.banner));
        }
        // farolete no canto de trás (5)
        if (lv >= 5) {
            g.clear(1, 1, 1);
            g.clear(1, 2, 1);
            for (int y = 1; y <= 6; y++) g.set(1, y, 1, y % 3 == 0 ? p.rich : p.wall);
            g.set(1, 7, 1, s(Blocks.SEA_LANTERN));
            g.set(1, 8, 1, slab(p.roofSlab, SlabType.BOTTOM));
            g.set(3, 1, 1, face(Blocks.BARREL, Direction.UP));
        }
        return g.tpl();
    }

    /** Mina: morro de pedra com túnel emoldurado em troncos, trilhos, câmara com veios de minério e lanternas. */
    private static Templates.Tpl mine(SettlementStyle st, int lv) {
        P p = pal(st, lv);
        int w = size(lv), cx = w / 2;
        int top = 4 + lv;
        G g = new G(w, w);
        Block[] rock;
        Block cap;
        switch (st) {
            case DESERT -> {
                rock = new Block[] {Blocks.SANDSTONE, Blocks.SANDSTONE, Blocks.TERRACOTTA, Blocks.SMOOTH_SANDSTONE,
                        Blocks.ORANGE_TERRACOTTA};
                cap = Blocks.SANDSTONE;
            }
            case NORDIC -> {
                rock = new Block[] {Blocks.STONE, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.ANDESITE, Blocks.STONE};
                cap = Blocks.SNOW_BLOCK;
            }
            case GOTHIC, DWARVEN -> {
                rock = new Block[] {Blocks.DEEPSLATE, Blocks.COBBLED_DEEPSLATE, Blocks.DEEPSLATE, Blocks.TUFF, Blocks.STONE};
                cap = st == SettlementStyle.DWARVEN ? Blocks.COBBLED_DEEPSLATE : Blocks.COARSE_DIRT;
            }
            case ELVEN -> {
                rock = new Block[] {Blocks.STONE, Blocks.MOSSY_COBBLESTONE, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.ANDESITE};
                cap = Blocks.MOSS_BLOCK;
            }
            default -> {
                rock = new Block[] {Blocks.STONE, Blocks.STONE, Blocks.COBBLESTONE, Blocks.ANDESITE, Blocks.MOSSY_COBBLESTONE};
                cap = Blocks.GRASS_BLOCK;
            }
        }
        Block[] ores = lv >= 5 ? new Block[] {Blocks.COAL_ORE, Blocks.IRON_ORE, Blocks.COPPER_ORE, Blocks.GOLD_ORE,
                Blocks.DIAMOND_ORE, Blocks.EMERALD_ORE}
                : lv >= 3 ? new Block[] {Blocks.COAL_ORE, Blocks.IRON_ORE, Blocks.COPPER_ORE, Blocks.GOLD_ORE}
                : new Block[] {Blocks.COAL_ORE, Blocks.IRON_ORE};
        int cw = lv >= 3 ? 3 : 2;
        int chH = lv >= 4 ? 4 : 3;
        // espaço escavado: túnel (3 de largura, 3 de altura) da frente até a câmara do fundo (z 1..3)
        boolean[][][] cut = new boolean[w][MAX_Y][w];
        for (int z = 1; z < w; z++) for (int x = cx - 1; x <= cx + 1; x++) for (int y = 1; y <= 3; y++) cut[x][y][z] = true;
        for (int z = 1; z <= 3; z++) for (int x = cx - cw; x <= cx + cw; x++) for (int y = 1; y <= chH; y++) cut[x][y][z] = true;
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < w; z++) {
                g.set(x, 0, z, s(rock[0] == Blocks.SANDSTONE ? Blocks.SMOOTH_SANDSTONE : Blocks.COBBLESTONE));
                int e = Math.min(Math.min(x, w - 1 - x), z);
                int hcol = Math.min(top, 3 + 2 * e);
                if (chH >= 4 && z <= 4 && Math.abs(x - cx) <= cw + 1) hcol = Math.max(hcol, chH + 1);
                for (int y = 1; y <= hcol; y++) {
                    if (cut[x][y][z]) continue;
                    boolean edge = x == 0 || z == 0 || x == w - 1 || z == w - 1;
                    boolean near = false;
                    for (int ax = -1; ax <= 1 && !near; ax++)
                        for (int ay = -1; ay <= 1 && !near; ay++)
                            for (int az = -1; az <= 1 && !near; az++) {
                                int nx = x + ax, ny = y + ay, nz = z + az;
                                if (nx >= 0 && nz >= 0 && nx < w && nz < w && ny >= 1 && ny < MAX_Y && cut[nx][ny][nz]) near = true;
                            }
                    if (!(edge || near || y >= hcol - 1)) continue;
                    BlockState b;
                    int hh = hash(x, y, z, 21);
                    if (y == hcol) b = s(cap);
                    else if (near && hh % 4 == 0) b = s(ores[hh / 4 % ores.length]);
                    else if (hh % 13 == 0) b = s(ores[hh / 13 % ores.length]);
                    else b = s(rock[hh % rock.length]);
                    g.set(x, y, z, b);
                }
            }
        }
        // trilhos pelo túnel até a câmara
        for (int z = 2; z <= w - 2; z++) g.set(cx, 1, z, s(Blocks.RAIL));
        // molduras de tronco a cada 3 blocos (a primeira é o portal da frente)
        BlockState post = axis(s(p.log), Direction.Axis.Y), beam = axis(s(p.log), Direction.Axis.X);
        for (int z = w - 1; z >= 5; z -= 3) {
            for (int y = 1; y <= 3; y++) {
                g.set(cx - 2, y, z, post);
                g.set(cx + 2, y, z, post);
            }
            for (int x = cx - 2; x <= cx + 2; x++) g.set(x, 4, z, beam);
            if (lv >= 2 || z == w - 1) g.set(cx + 1, 3, z, hanging(Blocks.LANTERN));
        }
        // portal: pedra lavrada em volta (2+) e pedra preciosa no fecho (5)
        if (lv >= 2) {
            BlockState fr = st == SettlementStyle.DESERT ? s(Blocks.CUT_SANDSTONE) : s(Blocks.STONE_BRICKS);
            for (int y = 1; y <= 5; y++) {
                g.set(cx - 3, y, w - 1, fr);
                g.set(cx + 3, y, w - 1, fr);
            }
            for (int x = cx - 3; x <= cx + 3; x++) g.set(x, 5, w - 1, fr);
            g.set(cx - 3, 6, w - 1, lantern(Blocks.LANTERN));
            g.set(cx + 3, 6, w - 1, lantern(Blocks.LANTERN));
        }
        if (lv >= 5) g.set(cx, 5, w - 1, s(Blocks.EMERALD_BLOCK));
        // câmara do fundo: baú, barris, bancada e lanternas
        g.set(cx - 2, 1, 1, face(Blocks.CHEST, Direction.SOUTH));
        g.set(cx + 2, 1, 1, face(Blocks.BARREL, Direction.UP));
        g.set(cx - 2, 1, 3, s(Blocks.CRAFTING_TABLE));
        if (lv >= 3) {
            g.set(cx + 3, 1, 1, face(Blocks.BARREL, Direction.UP));
            g.set(cx - 3, 1, 1, face(Blocks.FURNACE, Direction.SOUTH));
            g.set(cx + 2, 1, 3, s(Blocks.ANVIL));
        }
        if (lv >= 4) g.set(cx - 3, 1, 3, face(Blocks.BLAST_FURNACE, Direction.EAST));
        g.set(cx - 1, chH, 2, hanging(Blocks.LANTERN));
        g.set(cx + 1, chH, 2, hanging(Blocks.LANTERN));
        return g.tpl();
    }

    // =====================================================================================================
    // Estátua, arco, base de terraplenagem
    // =====================================================================================================

    /** Estátua: pedestal em degraus e figura humana (pedra -> quartzo -> ouro), espada erguida no 3+, capa no 4+. */
    private static Templates.Tpl statue(SettlementStyle st, int lv) {
        P p = pal(st, lv);
        int w = new int[] {5, 7, 7, 9, 9}[lv - 1];
        int c = w / 2, T = (w - 3) / 2;
        G g = new G(w, w);
        Block pst;
        BlockState pb, pave;
        switch (st) {
            case DESERT -> {
                pst = lv >= 3 ? Blocks.SMOOTH_SANDSTONE_STAIRS : Blocks.SANDSTONE_STAIRS;
                pb = s(lv >= 3 ? Blocks.SMOOTH_SANDSTONE : Blocks.SANDSTONE);
                pave = s(Blocks.CUT_SANDSTONE);
            }
            case NORDIC -> {
                pst = lv >= 3 ? Blocks.STONE_BRICK_STAIRS : Blocks.COBBLESTONE_STAIRS;
                pb = s(lv >= 3 ? Blocks.STONE_BRICKS : Blocks.COBBLESTONE);
                pave = s(Blocks.MOSSY_COBBLESTONE);
            }
            default -> {
                pst = Blocks.STONE_BRICK_STAIRS;
                pb = s(Blocks.STONE_BRICKS);
                pave = s(Blocks.POLISHED_ANDESITE);
                Block[] cut = masonry(st, lv);
                if (cut != null) {
                    pst = cut[1];
                    pb = s(cut[0]);
                    pave = s(masonry(st, 5)[0]);
                }
            }
        }
        g.fill(0, 0, 0, w - 1, 0, w - 1, pave);
        // pedestal em degraus (o último degrau é cheio, com borda lavrada no 3+)
        for (int t = 1; t <= T; t++) {
            int r = c - t + 1;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    boolean rim = Math.max(Math.abs(dx), Math.abs(dz)) == r;
                    BlockState b = pb;
                    if (rim && t < T) {
                        Direction f = dz == -r ? Direction.SOUTH : dz == r ? Direction.NORTH : dx == -r ? Direction.EAST : Direction.WEST;
                        b = stair(pst, f);
                    } else if (rim && lv >= 3) {
                        b = p.trim;
                    }
                    g.set(c + dx, t, c + dz, b);
                }
            }
        }
        int yb = T + 1;
        BlockState m = s(lv >= 5 ? Blocks.GOLD_BLOCK : lv >= 3 ? Blocks.QUARTZ_BLOCK : Blocks.STONE);
        BlockState leg = lv >= 3 && lv < 5 ? s(Blocks.QUARTZ_PILLAR) : m;
        BlockState head = s(lv >= 5 ? Blocks.GOLD_BLOCK : lv >= 3 ? Blocks.CHISELED_QUARTZ_BLOCK : Blocks.CHISELED_STONE_BRICKS);
        // pernas, tronco, braços, cabeça (olhando para +Z)
        for (int y = yb; y <= yb + 1; y++) {
            g.set(c - 1, y, c, leg);
            g.set(c + 1, y, c, leg);
        }
        for (int y = yb + 2; y <= yb + 3; y++) for (int x = c - 1; x <= c + 1; x++) g.set(x, y, c, m);
        g.set(c - 2, yb + 2, c, m);
        g.set(c - 2, yb + 3, c, m);
        if (lv >= 3) {
            // braço direito erguido segurando a espada
            g.set(c + 2, yb + 3, c, m);
            g.set(c + 2, yb + 4, c, m);
            g.set(c + 2, yb + 5, c, lv >= 5 ? s(Blocks.END_ROD) : s(Blocks.IRON_BARS));
            g.set(c + 2, yb + 6, c, lv >= 5 ? s(Blocks.END_ROD) : s(Blocks.IRON_BARS));
        } else {
            g.set(c + 2, yb + 2, c, m);
            g.set(c + 2, yb + 3, c, m);
        }
        g.set(c, yb + 4, c, head);
        if (lv >= 4) for (int y = yb + 1; y <= yb + 3; y++) for (int x = c - 1; x <= c + 1; x++) g.set(x, y, c - 1, p.wool);
        if (lv >= 5) g.set(c, yb + 5, c, lantern(Blocks.LANTERN));
        // postes de luz nos cantos (2+), luzes embutidas no pedestal (5)
        if (lv >= 2) {
            for (int[] cc : new int[][] {{0, 0}, {w - 1, 0}, {0, w - 1}, {w - 1, w - 1}}) {
                g.set(cc[0], 1, cc[1], lv >= 5 ? p.rich : pb);
                g.set(cc[0], 2, cc[1], s(p.fence));
                g.set(cc[0], 3, cc[1], lantern(Blocks.LANTERN));
            }
        }
        if (lv >= 5) {
            int r = c - T + 1;
            for (int[] d : new int[][] {{-r, -r}, {r, -r}, {-r, r}, {r, r}}) g.set(c + d[0], T, c + d[1], s(Blocks.SEA_LANTERN));
        }
        return g.tpl();
    }

    /** Arco monumental: duas colunas, lintel com arco de escadas, cornija/ameias, estandartes e ouro no topo. */
    private static Templates.Tpl arch(SettlementStyle st, int lv) {
        P p = pal(st, lv);
        int w = new int[] {7, 7, 9, 9, 11}[lv - 1];
        int d = lv >= 4 ? 5 : 3;
        int ow = lv >= 3 ? 5 : 3;
        int cw = (w - ow) / 2, c = w / 2;
        int hA = new int[] {4, 4, 5, 6, 7}[lv - 1];
        G g = new G(w, d);
        BlockState col;
        Block stB, slB;
        switch (st) {
            case DESERT -> {
                col = s(lv >= 3 ? Blocks.SMOOTH_SANDSTONE : Blocks.SANDSTONE);
                stB = lv >= 3 ? Blocks.SMOOTH_SANDSTONE_STAIRS : Blocks.SANDSTONE_STAIRS;
                slB = lv >= 3 ? Blocks.SMOOTH_SANDSTONE_SLAB : Blocks.SANDSTONE_SLAB;
            }
            default -> {
                col = s(lv >= 3 ? Blocks.STONE_BRICKS : Blocks.COBBLESTONE);
                stB = lv >= 3 ? Blocks.STONE_BRICK_STAIRS : Blocks.COBBLESTONE_STAIRS;
                slB = lv >= 3 ? Blocks.STONE_BRICK_SLAB : Blocks.COBBLESTONE_SLAB;
                Block[] cut = masonry(st, lv);
                if (cut != null) {
                    // Oriental: o arco é um torii — colunas vermelhas, travessa escura
                    col = st == SettlementStyle.ORIENTAL ? s(Blocks.STRIPPED_MANGROVE_LOG) : s(cut[0]);
                    stB = st == SettlementStyle.ORIENTAL ? Blocks.DARK_OAK_STAIRS : cut[1];
                    slB = st == SettlementStyle.ORIENTAL ? Blocks.DARK_OAK_SLAB : cut[2];
                }
            }
        }
        g.fill(0, 0, 0, w - 1, 0, d - 1, p.base);
        // colunas
        for (int x = 0; x < w; x++) {
            if (x >= cw && x < w - cw) continue;
            for (int z = 0; z < d; z++) {
                for (int y = 1; y <= hA; y++) {
                    BlockState b = y == 1 ? p.base : col;
                    if (y == hA && lv >= 2) b = p.trim;
                    boolean corner = (x == 0 || x == w - 1) && (z == 0 || z == d - 1);
                    if (corner && st == SettlementStyle.NORDIC && y > 1 && y < hA) b = axis(p.pillar, Direction.Axis.Y);
                    g.set(x, y, z, b);
                }
            }
        }
        // lintel
        g.fill(0, hA + 1, 0, w - 1, hA + 2, d - 1, col);
        if (lv >= 2) {
            for (int x = 0; x < w; x++) {
                g.set(x, hA + 2, 0, p.accent);
                g.set(x, hA + 2, d - 1, p.accent);
            }
        }
        // curva do arco (escadas de cabeça para baixo)
        for (int z = 0; z < d; z++) {
            if (ow == 3) {
                g.set(cw, hA, z, stair(stB, Direction.WEST, Half.TOP));
                g.set(w - cw - 1, hA, z, stair(stB, Direction.EAST, Half.TOP));
            } else {
                g.set(cw, hA, z, col);
                g.set(w - cw - 1, hA, z, col);
                g.set(cw + 1, hA, z, stair(stB, Direction.WEST, Half.TOP));
                g.set(w - cw - 2, hA, z, stair(stB, Direction.EAST, Half.TOP));
                g.set(cw, hA - 1, z, stair(stB, Direction.WEST, Half.TOP));
                g.set(w - cw - 1, hA - 1, z, stair(stB, Direction.EAST, Half.TOP));
            }
        }
        if (lv >= 3) {
            g.set(c, hA + 1, 0, lv >= 5 ? p.rich : p.trim);
            g.set(c, hA + 1, d - 1, lv >= 5 ? p.rich : p.trim);
        }
        if (lv >= 2) g.set(c, hA, d / 2, hanging(Blocks.LANTERN));
        // cornija (2+) e ameias (4+)
        int yt = hA + 3;
        if (lv >= 2) {
            for (int x = 0; x < w; x++) {
                for (int z = 0; z < d; z++) {
                    boolean edge = x == 0 || x == w - 1 || z == 0 || z == d - 1;
                    boolean crenel = lv >= 4 && edge && (x + z) % 2 == 0;
                    g.set(x, yt, z, crenel ? col : slab(slB, SlabType.BOTTOM));
                }
            }
        }
        if (lv >= 3) {
            for (int[] cc : new int[][] {{0, 0}, {w - 1, 0}, {0, d - 1}, {w - 1, d - 1}}) {
                g.set(cc[0], yt, cc[1], lv >= 5 ? p.rich : col);
                g.set(cc[0], yt + 1, cc[1], lantern(Blocks.LANTERN));
            }
        }
        if (lv >= 4) {
            g.set(0, yt, d / 2, col);
            g.set(w - 1, yt, d / 2, col);
            g.set(0, yt + 1, d / 2, s(p.banner));
            g.set(w - 1, yt + 1, d / 2, s(p.banner));
        }
        if (lv >= 5) {
            g.set(c, yt, d / 2, p.rich);
            g.set(c, yt + 1, d / 2, s(Blocks.END_ROD));
        }
        return g.tpl();
    }

    /**
     * Campo de armadilhas (v1.0, defesa de cerco): pátio de terra batida com fileiras de estacas (cerca + grade de
     * ferro), paliçada baixa com passagem nos 4 lados e o sino de alarme no meio. Só enfeite: quem fere a horda é o
     * {@code CityDefense} (nada aqui machuca moradores ou jogadores).
     */
    private static Templates.Tpl trapField(SettlementStyle st, int lv) {
        P p = pal(st, lv);
        int w = size(lv), c = w / 2;
        G g = new G(w, w);
        BlockState dirt = s(st == SettlementStyle.DESERT ? Blocks.PACKED_MUD : Blocks.COARSE_DIRT);
        BlockState rough = s(st == SettlementStyle.DESERT ? Blocks.SMOOTH_SANDSTONE : Blocks.GRAVEL);
        BlockState rail = s(lv >= 4 ? (st == SettlementStyle.DESERT ? Blocks.SANDSTONE_WALL : Blocks.COBBLESTONE_WALL) : p.fence);
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < w; z++) {
                boolean path = x == c || z == c;
                g.set(x, 0, z, path ? s(Blocks.DIRT_PATH) : hash(x, 0, z, 77) % 4 == 0 ? rough : dirt);
                boolean edge = x == 0 || z == 0 || x == w - 1 || z == w - 1;
                // paliçada baixa, com 3 blocos de passagem no meio de cada lado
                if (edge && Math.abs(x - c) > 1 && Math.abs(z - c) > 1) g.set(x, 1, z, rail);
                // estacas: xadrez espaçado fora dos caminhos
                boolean inner = x >= 2 && z >= 2 && x <= w - 3 && z <= w - 3;
                if (inner && Math.abs(x - c) > 1 && Math.abs(z - c) > 1 && (x + z) % 2 == 0 && (x % 2 == 0)) {
                    g.set(x, 1, z, s(p.fence));
                    g.set(x, 2, z, s(Blocks.IRON_BARS));
                    if (lv >= 5) g.set(x, 3, z, s(Blocks.IRON_BARS));
                }
            }
        }
        // postes de canto com lanterna
        for (int[] cc : new int[][] {{0, 0}, {w - 1, 0}, {0, w - 1}, {w - 1, w - 1}}) {
            g.set(cc[0], 1, cc[1], p.pillar);
            g.set(cc[0], 2, cc[1], p.pillar);
            g.set(cc[0], 3, cc[1], lantern(lv >= 5 ? Blocks.SOUL_LANTERN : Blocks.LANTERN));
        }
        // sino de alarme num pórtico no meio (o caminho passa por baixo)
        for (int y = 1; y <= 3; y++) {
            g.set(c - 1, y, c, s(p.fence));
            g.set(c + 1, y, c, s(p.fence));
        }
        for (int x = c - 1; x <= c + 1; x++) g.set(x, 4, c, p.plank);
        g.set(c, 3, c, Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, BellAttachType.CEILING));
        // 3+: bonecos de treino (feno + abóbora) e estandarte do estilo
        if (lv >= 3) {
            for (int[] d : new int[][] {{2, 2}, {w - 3, w - 3}}) {
                g.set(d[0], 1, d[1], s(Blocks.HAY_BLOCK));
                g.set(d[0], 2, d[1], s(Blocks.CARVED_PUMPKIN));
            }
            g.set(c, 5, c, s(p.banner));
        }
        return g.tpl();
    }

    /** Base de terraplenagem 15 x 1 x 15: grama (reino/nórdico) ou areia (deserto), cruz de trilha no meio. */
    private static Templates.Tpl pad(SettlementStyle st) {
        G g = new G(15, 15);
        for (int x = 0; x < 15; x++) {
            for (int z = 0; z < 15; z++) {
                int h = hash(x, 0, z, 31);
                Block b;
                if (x == 7 || z == 7) b = Blocks.DIRT_PATH;
                else if (st == SettlementStyle.DESERT) b = h % 3 == 0 ? Blocks.SMOOTH_SANDSTONE : Blocks.SAND;
                else if (st == SettlementStyle.NORDIC || st == SettlementStyle.GOTHIC || st == SettlementStyle.ELVEN) b = h % 5 == 0 ? Blocks.COARSE_DIRT : h % 11 == 0 ? Blocks.PODZOL : Blocks.GRASS_BLOCK;
                else b = Blocks.GRASS_BLOCK;
                g.set(x, 0, z, s(b));
            }
        }
        return g.tpl();
    }

    // =====================================================================================================
    // Templos elementais (a paleta é do ELEMENTO; o estilo só muda a base/plinto)
    // =====================================================================================================

    /** Paleta de um elemento. wall == null = paredes abertas (vento). */
    private static final class E {
        BlockState floor, floor2, wall, pillar, trim, roofFull, light, finial, finial2;
        Block roofStair, roofSlab, glass, lantern;
    }

    private static E element(String el, int lv) {
        E e = new E();
        e.lantern = Blocks.LANTERN;
        switch (el) {
            case "fire" -> {
                e.floor = s(Blocks.NETHER_BRICKS);
                e.floor2 = s(Blocks.RED_NETHER_BRICKS);
                e.wall = s(Blocks.NETHER_BRICKS);
                e.pillar = s(lv >= 4 ? Blocks.CHISELED_NETHER_BRICKS : Blocks.BLACKSTONE);
                e.trim = s(Blocks.RED_NETHER_BRICKS);
                e.roofStair = lv >= 3 ? Blocks.RED_NETHER_BRICK_STAIRS : Blocks.NETHER_BRICK_STAIRS;
                e.roofSlab = lv >= 3 ? Blocks.RED_NETHER_BRICK_SLAB : Blocks.NETHER_BRICK_SLAB;
                e.roofFull = s(lv >= 3 ? Blocks.RED_NETHER_BRICKS : Blocks.NETHER_BRICKS);
                e.glass = Blocks.ORANGE_STAINED_GLASS_PANE;
                e.light = s(Blocks.SHROOMLIGHT);
                e.finial = s(Blocks.MAGMA_BLOCK);
                e.finial2 = s(Blocks.CAMPFIRE);
            }
            case "water" -> {
                e.floor = s(Blocks.PRISMARINE_BRICKS);
                e.floor2 = s(Blocks.DARK_PRISMARINE);
                e.wall = s(Blocks.PRISMARINE);
                e.pillar = s(Blocks.DARK_PRISMARINE);
                e.trim = s(Blocks.PRISMARINE_BRICKS);
                e.roofStair = lv >= 3 ? Blocks.DARK_PRISMARINE_STAIRS : Blocks.PRISMARINE_STAIRS;
                e.roofSlab = lv >= 3 ? Blocks.DARK_PRISMARINE_SLAB : Blocks.PRISMARINE_SLAB;
                e.roofFull = s(lv >= 3 ? Blocks.DARK_PRISMARINE : Blocks.PRISMARINE);
                e.glass = Blocks.LIGHT_BLUE_STAINED_GLASS_PANE;
                e.light = s(Blocks.SEA_LANTERN);
                e.finial = s(Blocks.SEA_LANTERN);
            }
            case "earth" -> {
                e.floor = s(Blocks.MUD_BRICKS);
                e.floor2 = s(Blocks.PACKED_MUD);
                e.wall = s(Blocks.MOSSY_STONE_BRICKS);
                e.pillar = s(Blocks.DRIPSTONE_BLOCK);
                e.trim = s(Blocks.MOSS_BLOCK);
                e.roofStair = Blocks.MUD_BRICK_STAIRS;
                e.roofSlab = Blocks.MUD_BRICK_SLAB;
                e.roofFull = s(Blocks.MUD_BRICKS);
                e.glass = Blocks.LIME_STAINED_GLASS_PANE;
                e.light = s(Blocks.OCHRE_FROGLIGHT);
                e.finial = s(Blocks.MOSS_BLOCK);
                e.finial2 = s(Blocks.FLOWERING_AZALEA);
            }
            case "wind" -> {
                e.floor = s(Blocks.WHITE_CONCRETE);
                e.floor2 = s(Blocks.SMOOTH_QUARTZ);
                e.wall = null;
                e.pillar = s(Blocks.QUARTZ_PILLAR);
                e.trim = s(Blocks.WHITE_CONCRETE);
                e.roofStair = Blocks.POLISHED_DIORITE_STAIRS;
                e.roofSlab = Blocks.POLISHED_DIORITE_SLAB;
                e.roofFull = s(Blocks.POLISHED_DIORITE);
                e.glass = Blocks.CYAN_STAINED_GLASS_PANE;
                e.light = s(Blocks.SEA_LANTERN);
                e.finial = s(Blocks.WHITE_WOOL);
                e.finial2 = s(Blocks.END_ROD);
            }
            case "light" -> {
                e.floor = s(Blocks.QUARTZ_BRICKS);
                e.floor2 = s(Blocks.SMOOTH_QUARTZ);
                e.wall = s(Blocks.QUARTZ_BLOCK);
                e.pillar = s(Blocks.QUARTZ_PILLAR);
                e.trim = s(Blocks.CHISELED_QUARTZ_BLOCK);
                e.roofStair = Blocks.QUARTZ_STAIRS;
                e.roofSlab = Blocks.QUARTZ_SLAB;
                e.roofFull = s(Blocks.QUARTZ_BLOCK);
                e.glass = Blocks.YELLOW_STAINED_GLASS_PANE;
                e.light = s(Blocks.GLOWSTONE);
                e.finial = s(Blocks.GOLD_BLOCK);
                e.finial2 = lantern(Blocks.LANTERN);
            }
            case "shadow" -> {
                e.floor = s(Blocks.POLISHED_BLACKSTONE);
                e.floor2 = s(Blocks.POLISHED_BLACKSTONE_BRICKS);
                e.wall = s(Blocks.POLISHED_BLACKSTONE_BRICKS);
                e.pillar = s(Blocks.OBSIDIAN);
                e.trim = s(Blocks.CHISELED_POLISHED_BLACKSTONE);
                e.roofStair = Blocks.BLACKSTONE_STAIRS;
                e.roofSlab = Blocks.BLACKSTONE_SLAB;
                e.roofFull = s(Blocks.BLACKSTONE);
                e.glass = lv >= 3 ? Blocks.BLACK_STAINED_GLASS_PANE : Blocks.GRAY_STAINED_GLASS_PANE;
                e.light = s(Blocks.CRYING_OBSIDIAN);
                e.finial = s(Blocks.CRYING_OBSIDIAN);
                e.finial2 = lantern(Blocks.SOUL_LANTERN);
                e.lantern = Blocks.SOUL_LANTERN;
            }
            default -> { // arcane
                e.floor = s(Blocks.PURPUR_BLOCK);
                e.floor2 = s(lv >= 3 ? Blocks.AMETHYST_BLOCK : Blocks.PURPUR_PILLAR);
                e.wall = s(Blocks.PURPUR_BLOCK);
                e.pillar = s(Blocks.PURPUR_PILLAR);
                e.trim = s(Blocks.AMETHYST_BLOCK);
                e.roofStair = Blocks.PURPUR_STAIRS;
                e.roofSlab = Blocks.PURPUR_SLAB;
                e.roofFull = s(Blocks.PURPUR_BLOCK);
                e.glass = Blocks.MAGENTA_STAINED_GLASS_PANE;
                e.light = s(Blocks.PEARLESCENT_FROGLIGHT);
                e.finial = s(Blocks.AMETHYST_BLOCK);
                e.finial2 = s(Blocks.END_ROD);
            }
        }
        return e;
    }

    /** Templo: plinto do estilo, colunata do elemento, telhado em pirâmide com remate e altar elemental no centro. */
    private static Templates.Tpl temple(String el, SettlementStyle st, int lv) {
        P p = pal(st, lv);
        E e = element(el, lv);
        int w = size(lv), c = w / 2;
        int H = new int[] {5, 5, 6, 6, 7}[lv - 1];
        G g = new G(w, w);
        // chão: borda com a pedra do estilo, dentro o xadrez do elemento
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < w; z++) {
                boolean edge = x == 0 || z == 0 || x == w - 1 || z == w - 1;
                g.set(x, 0, z, edge ? p.base : (x + z) % 2 == 0 ? e.floor : e.floor2);
            }
        }
        // posições das colunas ao longo de cada parede (cantos + a cada 2 a partir do meio)
        List<Integer> pp = new ArrayList<>();
        pp.add(0);
        pp.add(w - 1);
        for (int k = -3; k <= 3; k++) {
            int v = c + 2 * k;
            if (k != 0 && v >= 2 && v <= w - 3) pp.add(v);
        }
        int doorHalf = lv >= 3 ? 1 : 0;
        int winTop = lv >= 3 ? H - 2 : Math.min(3, H - 2);
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < w; z++) {
                boolean ns = z == 0 || z == w - 1, ew = x == 0 || x == w - 1;
                if (!ns && !ew) continue;
                int u = ns ? x : z;
                boolean isP = pp.contains(u) || (ns && ew);
                boolean doorway = z == w - 1 && Math.abs(x - c) <= doorHalf;
                for (int y = 1; y < H; y++) {
                    BlockState b;
                    if (doorway && y <= 3) b = null;
                    else if (isP) b = axis(e.pillar, Direction.Axis.Y);
                    else if (y == 1) b = e.wall == null ? p.trim : p.base;
                    else if (e.wall == null) b = y == H - 1 ? slab(e.roofSlab, SlabType.TOP) : y == H - 2 ? s(e.glass) : null;
                    else if (y <= winTop && !doorway) b = s(e.glass);
                    else b = e.wall;
                    g.set(x, y, z, b);
                }
                g.set(x, H, z, e.trim);
            }
        }
        // plinto do estilo nos pés das colunas
        for (int v : pp) {
            g.set(v, 1, 0, p.base);
            g.set(0, 1, v, p.base);
            g.set(w - 1, 1, v, p.base);
            if (Math.abs(v - c) > doorHalf) g.set(v, 1, w - 1, p.base);
        }
        if (lv >= 3) for (int x = c - doorHalf; x <= c + doorHalf; x++) g.set(x, 4, w - 1, e.trim);
        // forro com luzes embutidas
        g.fill(1, H, 1, w - 2, H, w - 2, e.roofFull);
        for (int[] d : new int[][] {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
            g.set(c + d[0], H, c + d[1], e.light);
            if (lv >= 2) g.set(c + d[0], H - 1, c + d[1], hanging(e.lantern));
        }
        g.set(c, H, c, e.light);
        // telhado em pirâmide (anéis de escadas) e remate
        int top = H;
        for (int i = 0; ; i++) {
            int a = i, b = w - 1 - i, y = H + 1 + i;
            if (a > b) break;
            top = y;
            if (a == b) {
                g.set(a, y, a, e.roofFull);
                break;
            }
            for (int x = a; x <= b; x++) {
                g.set(x, y, a, stair(e.roofStair, Direction.SOUTH));
                g.set(x, y, b, stair(e.roofStair, Direction.NORTH));
            }
            for (int z = a + 1; z <= b - 1; z++) {
                g.set(a, y, z, stair(e.roofStair, Direction.EAST));
                g.set(b, y, z, stair(e.roofStair, Direction.WEST));
            }
        }
        g.set(c, top + 1, c, e.finial);
        if (e.finial2 != null) g.set(c, top + 2, c, e.finial2);
        if (lv >= 4) {
            // pináculos nos cantos do beiral
            for (int[] cc : new int[][] {{0, 0}, {w - 1, 0}, {0, w - 1}, {w - 1, w - 1}}) {
                g.set(cc[0], H + 1, cc[1], lv >= 5 ? e.light : e.trim);
            }
        }
        altar(g, el, e, p, lv, w, c);
        return g.tpl();
    }

    /** Altar e enfeites internos de cada elemento. */
    private static void altar(G g, String el, E e, P p, int lv, int w, int c) {
        int ya = lv >= 3 ? 2 : 1;
        if (lv >= 3 && !el.equals("water")) g.fill(c - 1, 1, c - 1, c + 1, 1, c + 1, e.trim);
        int[][] corners = {{c - 3, c - 3}, {c + 3, c - 3}, {c - 3, c + 3}, {c + 3, c + 3}};
        switch (el) {
            case "fire" -> {
                g.set(c, ya, c, s(Blocks.MAGMA_BLOCK));
                g.set(c, ya + 1, c, s(Blocks.CAMPFIRE));
                if (lv >= 2) {
                    for (int[] cc : corners) {
                        g.set(cc[0], 1, cc[1], s(Blocks.RED_NETHER_BRICKS));
                        g.set(cc[0], 2, cc[1], s(Blocks.CAMPFIRE));
                    }
                }
                if (lv >= 4) {
                    for (int d = -1; d <= 1; d += 2) {
                        g.set(c + d * 2, 0, c, s(Blocks.MAGMA_BLOCK));
                        g.set(c, 0, c + d * 2, s(Blocks.MAGMA_BLOCK));
                    }
                }
                if (lv >= 5) {
                    g.set(c - 1, 2, c - 1, s(Blocks.BLACKSTONE_WALL));
                    g.set(c + 1, 2, c - 1, s(Blocks.BLACKSTONE_WALL));
                    g.set(c - 1, 2, c + 1, s(Blocks.BLACKSTONE_WALL));
                    g.set(c + 1, 2, c + 1, s(Blocks.BLACKSTONE_WALL));
                }
            }
            case "water" -> {
                int r = lv >= 3 ? 2 : 1;
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        boolean rim = Math.max(Math.abs(dx), Math.abs(dz)) == r;
                        g.set(c + dx, 1, c + dz, rim ? s(lv >= 3 ? Blocks.DARK_PRISMARINE : Blocks.PRISMARINE_BRICKS) : s(Blocks.WATER));
                    }
                }
                g.set(c, 0, c, s(Blocks.SEA_LANTERN));
                if (lv >= 5) {
                    g.set(c, 1, c, s(Blocks.PRISMARINE_BRICKS));
                    g.set(c, 2, c, s(Blocks.PRISMARINE_BRICKS));
                    g.set(c, 3, c, s(Blocks.SEA_LANTERN));
                }
                if (lv >= 2) {
                    for (int[] cc : corners) {
                        g.set(cc[0], 1, cc[1], s(Blocks.PRISMARINE_WALL));
                        g.set(cc[0], 2, cc[1], s(Blocks.SEA_LANTERN));
                    }
                }
                if (lv >= 4) {
                    for (int[] cc : corners) g.set(cc[0], 3, cc[1], s(Blocks.PRISMARINE_SLAB));
                }
            }
            case "earth" -> {
                if (lv >= 3) {
                    for (int dx = -1; dx <= 1; dx++)
                        for (int dz = -1; dz <= 1; dz++)
                            g.set(c + dx, 1, c + dz, s((dx + dz) % 2 == 0 ? Blocks.ROOTED_DIRT : Blocks.MOSS_BLOCK));
                }
                g.set(c, ya, c, s(Blocks.MOSS_BLOCK));
                g.set(c, ya + 1, c, s(lv >= 2 ? Blocks.FLOWERING_AZALEA : Blocks.AZALEA));
                if (lv >= 2) {
                    for (int[] cc : corners) {
                        g.set(cc[0], 1, cc[1], s(Blocks.DRIPSTONE_BLOCK));
                        g.set(cc[0], 2, cc[1], s(Blocks.DRIPSTONE_BLOCK));
                        g.set(cc[0], 3, cc[1], lv >= 4 ? leaves(Blocks.FLOWERING_AZALEA_LEAVES) : s(Blocks.MOSS_BLOCK));
                    }
                }
                if (lv >= 3) {
                    for (int x = 1; x < w - 1; x++) {
                        for (int z = 1; z < w - 1; z++) {
                            if (Math.abs(x - c) <= 1 && z >= c) continue;
                            if (g.get(x, 1, z) == null && hash(x, 1, z, 41) % 4 == 0) g.set(x, 1, z, s(Blocks.MOSS_CARPET));
                        }
                    }
                }
                if (lv >= 5) {
                    g.set(c - 1, 2, c - 1, s(Blocks.AZALEA));
                    g.set(c + 1, 2, c + 1, s(Blocks.AZALEA));
                }
            }
            case "wind" -> {
                g.set(c, ya, c, s(Blocks.QUARTZ_PILLAR));
                g.set(c, ya + 1, c, s(Blocks.QUARTZ_PILLAR));
                g.set(c, ya + 2, c, s(Blocks.END_ROD));
                if (lv >= 2) {
                    for (int[] cc : corners) for (int y = 1; y <= Math.min(3, lv); y++) g.set(cc[0], y, cc[1], s(Blocks.END_ROD));
                }
                if (lv >= 3) {
                    g.set(c - 1, 2, c, s(Blocks.CYAN_STAINED_GLASS_PANE));
                    g.set(c + 1, 2, c, s(Blocks.CYAN_STAINED_GLASS_PANE));
                }
                if (lv >= 4) {
                    g.set(c - 2, 1, c, s(Blocks.WHITE_BANNER));
                    g.set(c + 2, 1, c, s(Blocks.WHITE_BANNER));
                    g.set(c, 1, c - 2, s(Blocks.WHITE_BANNER));
                }
            }
            case "light" -> {
                g.set(c, ya, c, lv >= 3 ? s(Blocks.GOLD_BLOCK) : s(Blocks.CHISELED_QUARTZ_BLOCK));
                g.set(c, ya + 1, c, s(Blocks.GLOWSTONE));
                if (lv >= 5) g.set(c, ya + 2, c, s(Blocks.END_ROD));
                if (lv >= 2) {
                    for (int[] cc : corners) {
                        g.set(cc[0], 1, cc[1], s(Blocks.QUARTZ_PILLAR));
                        g.set(cc[0], 2, cc[1], lantern(Blocks.LANTERN));
                    }
                }
                if (lv >= 4) {
                    for (int[] d : new int[][] {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) g.set(c + d[0], 2, c + d[1], s(Blocks.SHROOMLIGHT));
                }
            }
            case "shadow" -> {
                if (lv >= 3) g.fill(c - 1, 1, c - 1, c + 1, 1, c + 1, s(Blocks.OBSIDIAN));
                g.set(c, ya, c, s(Blocks.CRYING_OBSIDIAN));
                g.set(c, ya + 1, c, s(Blocks.SOUL_CAMPFIRE));
                if (lv >= 2) {
                    for (int[] cc : corners) {
                        g.set(cc[0], 1, cc[1], s(Blocks.OBSIDIAN));
                        g.set(cc[0], 2, cc[1], lantern(Blocks.SOUL_LANTERN));
                    }
                }
                if (lv >= 4) {
                    for (int[] d : new int[][] {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) g.set(c + d[0], 2, c + d[1], s(Blocks.CHAIN));
                }
                if (lv >= 5) {
                    g.set(c - 2, 1, c - 2, s(Blocks.CRYING_OBSIDIAN));
                    g.set(c + 2, 1, c - 2, s(Blocks.CRYING_OBSIDIAN));
                }
            }
            default -> { // arcano
                g.set(c, ya, c, s(Blocks.ENCHANTING_TABLE));
                if (lv >= 3) {
                    for (int dx = -2; dx <= 2; dx++) {
                        for (int dz = -2; dz <= 2; dz++) {
                            if (Math.max(Math.abs(dx), Math.abs(dz)) != 2) continue;
                            if (dz == 2 && dx == 0) continue; // vão de entrada
                            g.set(c + dx, 1, c + dz, s(Blocks.BOOKSHELF));
                            if (lv >= 4 && (Math.abs(dx) + Math.abs(dz)) % 2 == 1) g.set(c + dx, 2, c + dz, s(Blocks.BOOKSHELF));
                        }
                    }
                    for (int[] d : new int[][] {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) g.set(c + d[0], 2, c + d[1], s(Blocks.END_ROD));
                }
                if (lv >= 2) {
                    for (int[] cc : corners) {
                        g.set(cc[0], 1, cc[1], s(Blocks.AMETHYST_BLOCK));
                        g.set(cc[0], 2, cc[1], s(lv >= 4 ? Blocks.AMETHYST_CLUSTER : Blocks.LARGE_AMETHYST_BUD));
                    }
                }
                if (lv >= 5) {
                    g.set(c - 1, 1, c + 3, s(Blocks.BUDDING_AMETHYST));
                    g.set(c + 1, 1, c + 3, s(Blocks.BUDDING_AMETHYST));
                }
            }
        }
    }

    // =====================================================================================================
    // v1.5 (city_arcana): prédios mágicos — Barreira Arcana, Obelisco de Nulificação, Torre de Mana, Farol de Vigia
    // =====================================================================================================

    /** Paleta "arcana" de um estilo: pedra, pilar, friso, piso, runa, luz e o vidro da cor do escudo. */
    private static final class AP {
        BlockState stone, pillar, trim, floor, rune, glow, rich, glass;
        Block stairs, slab, fence;
    }

    private static AP arc(SettlementStyle st, int lv) {
        AP a = new AP();
        switch (st) {
            case DESERT -> {
                a.stone = s(lv >= 3 ? Blocks.CUT_SANDSTONE : Blocks.SANDSTONE);
                a.pillar = s(lv >= 3 ? Blocks.SMOOTH_SANDSTONE : Blocks.CUT_SANDSTONE);
                a.trim = s(Blocks.CHISELED_SANDSTONE);
                a.floor = s(Blocks.SMOOTH_SANDSTONE);
                a.rune = s(lv >= 3 ? Blocks.LAPIS_BLOCK : Blocks.ORANGE_TERRACOTTA);
                a.glow = s(lv >= 4 ? Blocks.SEA_LANTERN : Blocks.GLOWSTONE);
                a.rich = s(Blocks.GOLD_BLOCK);
                a.glass = s(Blocks.YELLOW_STAINED_GLASS);
                a.stairs = Blocks.SMOOTH_SANDSTONE_STAIRS;
                a.slab = Blocks.CUT_SANDSTONE_SLAB;
                a.fence = Blocks.ACACIA_FENCE;
            }
            case NORDIC, GOTHIC, DWARVEN -> {
                a.stone = s(lv >= 3 ? Blocks.DEEPSLATE_BRICKS : Blocks.COBBLED_DEEPSLATE);
                a.pillar = s(lv >= 3 ? Blocks.PACKED_ICE : Blocks.POLISHED_DEEPSLATE);
                a.trim = s(lv >= 3 ? Blocks.CHISELED_DEEPSLATE : Blocks.POLISHED_DEEPSLATE);
                a.floor = s(Blocks.DEEPSLATE_TILES);
                a.rune = s(Blocks.BLUE_ICE);
                a.glow = s(Blocks.SEA_LANTERN);
                a.rich = s(Blocks.WAXED_OXIDIZED_COPPER);
                a.glass = s(Blocks.CYAN_STAINED_GLASS);
                a.stairs = Blocks.DEEPSLATE_BRICK_STAIRS;
                a.slab = Blocks.DEEPSLATE_TILE_SLAB;
                a.fence = Blocks.SPRUCE_FENCE;
            }
            default -> {
                a.stone = s(lv >= 3 ? Blocks.STONE_BRICKS : Blocks.COBBLESTONE);
                a.pillar = s(lv >= 3 ? Blocks.QUARTZ_PILLAR : Blocks.STONE_BRICKS);
                a.trim = s(lv >= 3 ? Blocks.CHISELED_STONE_BRICKS : Blocks.STONE_BRICKS);
                a.floor = s(Blocks.POLISHED_ANDESITE);
                a.rune = s(lv >= 3 ? Blocks.PRISMARINE_BRICKS : Blocks.LIGHT_BLUE_TERRACOTTA);
                a.glow = s(Blocks.SEA_LANTERN);
                a.rich = s(Blocks.GOLD_BLOCK);
                a.glass = s(Blocks.LIGHT_BLUE_STAINED_GLASS);
                a.stairs = Blocks.STONE_BRICK_STAIRS;
                a.slab = Blocks.STONE_BRICK_SLAB;
                a.fence = Blocks.OAK_FENCE;
            }
        }
        return a;
    }

    /** Degrau olhando para FORA do centro (a parte alta fica para dentro) — para bases em pirâmide. */
    private static BlockState outStair(Block b, int dx, int dz) {
        Direction f = Math.abs(dx) >= Math.abs(dz) ? (dx < 0 ? Direction.EAST : Direction.WEST)
                : (dz < 0 ? Direction.SOUTH : Direction.NORTH);
        return stair(b, f);
    }

    /** Base quadrada em degraus (raio r em volta de c, na altura y): borda de degraus e miolo cheio. */
    private static void plinth(G g, int c, int y, int r, BlockState fill, Block stairs) {
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                boolean rim = Math.max(Math.abs(dx), Math.abs(dz)) == r;
                g.set(c + dx, y, c + dz, rim ? outStair(stairs, dx, dz) : fill);
            }
        }
    }

    /**
     * Barreira Arcana: praça redonda com o pilar-catalisador no meio e um cristal no topo. Cresce de nível em nível:
     * 2 = 4 pilares rúnicos; 3 = nervuras do domo; 4 = 7 pilares + anéis de vidro do domo; 5 = domo de vidro inteiro,
     * catalisador mais alto com farol, ouro e pontos de luz.
     */
    private static Templates.Tpl arcaneWard(SettlementStyle st, int lv) {
        AP a = arc(st, lv);
        int w = new int[] {9, 9, 11, 11, 13}[lv - 1];
        int c = w / 2, R = c;
        G g = new G(w, w);
        g.fill(0, 0, 0, w - 1, 0, w - 1, a.floor);
        // círculo de runas no chão e borda de lajes
        double runeR = R - 1.5;
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < w; z++) {
                int dx = x - c, dz = z - c;
                double d = Math.sqrt(dx * dx + dz * dz);
                if (lv >= 2 && Math.abs(d - runeR) < 0.55) g.set(x, 0, z, a.rune);
                if (lv >= 4 && Math.abs(d - (runeR - 2)) < 0.5) g.set(x, 0, z, a.rune);
                if (oct(dx, dz, R) && !oct(dx, dz, R - 1) && !(dz > 0 && Math.abs(dx) <= 1)) {
                    g.set(x, 1, z, slab(a.slab, SlabType.BOTTOM));
                }
            }
        }
        // domo (casca de um elipsoide): nervuras no 3, nervuras + anéis no 4, inteiro no 5
        if (lv >= 3) {
            double rd = R - 0.5, hd = R + 2.5;
            for (int x = 0; x < w; x++) {
                for (int z = 0; z < w; z++) {
                    for (int y = 1; y < MAX_Y; y++) {
                        int dx = x - c, dz = z - c;
                        if (!inDome(dx, y, dz, rd, hd) || shellInside(dx, y, dz, rd, hd)) continue;
                        boolean rib = dx == 0 || dz == 0 || Math.abs(dx) == Math.abs(dz);
                        boolean band = lv >= 4 && y % 3 == 0;
                        if (lv < 5 && !rib && !band) continue;
                        if (dz > 0 && Math.abs(dx) <= 1 && y <= 3) continue; // entrada
                        g.set(x, y, z, rib && lv < 5 ? a.trim : a.glass);
                        if (lv >= 5 && rib && hash(x, y, z, 31) % 3 == 0) g.set(x, y, z, a.glow);
                    }
                }
            }
        }
        // pilares rúnicos (4 nos cantos no 2-3; 7 no 4+), sem tapar a entrada
        if (lv >= 2) {
            int pr = R - 1, ph = 2 + lv / 2;
            int[][] spots = lv >= 4
                    ? new int[][] {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}, {0, -1}, {-1, 0}, {1, 0}}
                    : new int[][] {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}};
            for (int[] sp : spots) {
                int k = (sp[0] != 0 && sp[1] != 0) ? (int) Math.round(pr * 0.7) : pr;
                int px = c + sp[0] * k, pz = c + sp[1] * k;
                for (int y = 1; y <= ph; y++) g.set(px, y, pz, y == 1 ? a.trim : a.pillar);
                g.set(px, ph + 1, pz, lv >= 3 ? s(Blocks.END_ROD) : lantern(Blocks.SOUL_LANTERN));
                if (lv >= 5) g.set(px, 1, pz, a.rich);
            }
        }
        // catalisador central: base em degraus, coluna, cristal
        int H = 4 + 2 * lv;
        if (lv >= 3) plinth(g, c, 1, 1, a.trim, a.stairs);
        for (int y = lv >= 3 ? 2 : 1; y <= H; y++) {
            BlockState b = y % 4 == 0 ? a.glow : a.pillar;
            if (lv >= 4 && y % 4 == 2) b = a.trim;
            g.set(c, y, c, b);
        }
        g.set(c, H + 1, c, s(Blocks.AMETHYST_BLOCK));
        if (lv >= 2) g.set(c, H + 2, c, lv >= 5 ? s(Blocks.BEACON) : s(Blocks.AMETHYST_BLOCK));
        g.set(c, H + (lv >= 2 ? 3 : 2), c, s(Blocks.AMETHYST_CLUSTER));
        if (lv >= 4) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                g.set(c + d.getStepX(), H + 2, c + d.getStepZ(), face(Blocks.AMETHYST_CLUSTER, d));
                g.set(c + d.getStepX(), H, c + d.getStepZ(), a.glass);
            }
        }
        if (lv >= 5) {
            for (int[] d : new int[][] {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) g.set(c + d[0], H + 1, c + d[1], a.rich);
        }
        return g.tpl();
    }

    /** Ponto (dx, y, dz) dentro do elipsoide do domo (y=0 é o chão). */
    private static boolean inDome(int dx, int y, int dz, double rd, double hd) {
        double e = (dx * dx + dz * dz) / (rd * rd) + (y - 0.5) * (y - 0.5) / (hd * hd);
        return e <= 1.0;
    }

    /** O ponto está "por dentro" (os vizinhos também no domo) — não faz parte da casca. */
    private static boolean shellInside(int dx, int y, int dz, double rd, double hd) {
        return inDome(dx + 1, y, dz, rd, hd) && inDome(dx - 1, y, dz, rd, hd) && inDome(dx, y + 1, dz, rd, hd)
                && inDome(dx, y, dz + 1, rd, hd) && inDome(dx, y, dz - 1, rd, hd);
    }

    /**
     * Obelisco de Nulificação: agulha escura que engole a magia. Base em degraus (3+), veios do estilo, postes de
     * almas (2+), anéis flutuantes e obeliscos satélites (4+). A Runa de Comando (magnetita) na frente é onde o
     * governante escolhe quem é anulado.
     */
    private static Templates.Tpl nullObelisk(SettlementStyle st, int lv) {
        int w = new int[] {9, 9, 11, 11, 13}[lv - 1];
        int c = w / 2;
        G g = new G(w, w);
        BlockState body = s(Blocks.OBSIDIAN);
        BlockState vein, pave, pave2, post, cap;
        Block ringSlab, baseStairs;
        switch (st) {
            case DESERT -> {
                vein = lv >= 3 ? s(Blocks.GOLD_BLOCK) : s(Blocks.CHISELED_SANDSTONE);
                pave = s(Blocks.CUT_SANDSTONE);
                pave2 = s(Blocks.CHISELED_SANDSTONE);
                ringSlab = Blocks.CUT_SANDSTONE_SLAB;
                baseStairs = Blocks.SMOOTH_SANDSTONE_STAIRS;
                post = s(Blocks.SMOOTH_SANDSTONE);
                cap = s(Blocks.GOLD_BLOCK);
            }
            case NORDIC, GOTHIC, DWARVEN -> {
                vein = s(lv >= 3 ? Blocks.BLUE_ICE : Blocks.POLISHED_DEEPSLATE);
                pave = s(Blocks.DEEPSLATE_TILES);
                pave2 = s(Blocks.CHISELED_DEEPSLATE);
                ringSlab = Blocks.DEEPSLATE_TILE_SLAB;
                baseStairs = Blocks.DEEPSLATE_TILE_STAIRS;
                post = s(Blocks.POLISHED_DEEPSLATE);
                cap = s(lv >= 3 ? Blocks.CRYING_OBSIDIAN : Blocks.OBSIDIAN);
            }
            default -> {
                vein = s(lv >= 3 ? Blocks.CRYING_OBSIDIAN : Blocks.POLISHED_BLACKSTONE);
                pave = s(Blocks.POLISHED_BLACKSTONE_BRICKS);
                pave2 = s(Blocks.CHISELED_POLISHED_BLACKSTONE);
                ringSlab = Blocks.POLISHED_BLACKSTONE_BRICK_SLAB;
                baseStairs = Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS;
                post = s(Blocks.POLISHED_BLACKSTONE);
                cap = s(lv >= 3 ? Blocks.CRYING_OBSIDIAN : Blocks.OBSIDIAN);
            }
        }
        g.fill(0, 0, 0, w - 1, 0, w - 1, pave);
        // xadrez de selos no chão em volta (2+)
        if (lv >= 2) {
            for (int x = 1; x < w - 1; x++) {
                for (int z = 1; z < w - 1; z++) {
                    int d = Math.max(Math.abs(x - c), Math.abs(z - c));
                    if (d == c - 1 && (x + z) % 2 == 0) g.set(x, 0, z, pave2);
                }
            }
        }
        int Ht = 8 + 3 * lv;
        int y0 = 1;
        if (lv >= 3) {
            plinth(g, c, 1, 2, pave, baseStairs);
            y0 = 2;
        }
        // corpo: 3x3 até ~70% da altura, depois 1x1 até o topo
        int thick = (int) Math.round(Ht * 0.68);
        for (int y = y0; y < Ht; y++) {
            int r = y <= thick ? 1 : 0;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockState b = body;
                    boolean side = r == 1 && (dx == 0) != (dz == 0);
                    if (side && (y % 3 == 0 || lv >= 4 && y % 3 == 1 && hash(dx, y, dz, 7) % 2 == 0)) b = vein;
                    g.set(c + dx, y, c + dz, b);
                }
            }
        }
        // degraus de transição no ombro do obelisco
        if (lv >= 2) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                g.set(c + d.getStepX(), thick + 1, c + d.getStepZ(), stair(Blocks.POLISHED_BLACKSTONE_STAIRS, d.getOpposite()));
            }
        }
        g.set(c, Ht, c, cap);
        g.set(c, Ht + 1, c, lv >= 3 ? s(Blocks.END_ROD) : s(Blocks.LIGHTNING_ROD));
        // postes de almas nos cantos (2+); no 4+ viram obeliscos satélites
        if (lv >= 2) {
            int k = c - 1;
            for (int[] d : new int[][] {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
                int px = c + d[0] * k, pz = c + d[1] * k;
                g.set(px, 1, pz, post);
                if (lv >= 4) {
                    g.set(px, 2, pz, body);
                    g.set(px, 3, pz, body);
                    g.set(px, 4, pz, lv >= 5 ? cap : body);
                    g.set(px, 5, pz, s(Blocks.END_ROD));
                } else {
                    g.set(px, 2, pz, post);
                    g.set(px, 3, pz, lantern(Blocks.SOUL_LANTERN));
                }
            }
        }
        // anéis flutuantes em volta da agulha (4: um; 5: dois)
        if (lv >= 4) {
            int[] heights = lv >= 5 ? new int[] {thick + 3, thick + 6} : new int[] {thick + 3};
            for (int hy : heights) {
                if (hy >= Ht) continue;
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != 2) continue;
                        boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                        g.set(c + dx, hy, c + dz, corner ? vein : slab(ringSlab, SlabType.TOP));
                    }
                }
            }
        }
        // fogueiras das almas encostadas na base (3+): luz fria e "almas" subindo
        if (lv >= 3) {
            g.set(c - 3, 1, c, s(Blocks.SOUL_CAMPFIRE));
            g.set(c + 3, 1, c, s(Blocks.SOUL_CAMPFIRE));
            g.set(c, 1, c - 3, s(Blocks.SOUL_CAMPFIRE));
        }
        // Runa de Comando na frente (clique do governante)
        g.set(c, 1, w - 3, s(Blocks.LODESTONE));
        if (lv >= 3) {
            g.set(c - 1, 1, w - 3, slab(ringSlab, SlabType.BOTTOM));
            g.set(c + 1, 1, w - 3, slab(ringSlab, SlabType.BOTTOM));
        }
        return g.tpl();
    }

    /**
     * Torre de Mana: coluna-condutora de vidro e luz entre pilares, com um cristal de ametista FLUTUANDO no topo. Sobe
     * 3 blocos por nível; anéis de laje (3+), fontes de cristal no chão (4+), cristal maior e ouro no 5.
     */
    private static Templates.Tpl manaSpire(SettlementStyle st, int lv) {
        AP a = arc(st, lv);
        int w = new int[] {7, 9, 9, 11, 11}[lv - 1];
        int c = w / 2;
        G g = new G(w, w);
        g.fill(0, 0, 0, w - 1, 0, w - 1, a.floor);
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < w; z++) {
                int dx = x - c, dz = z - c;
                double d = Math.sqrt(dx * dx + dz * dz);
                if (Math.abs(d - (c - 0.8)) < 0.5) g.set(x, 0, z, a.rune);
            }
        }
        int Hc = 4 + 3 * lv;
        plinth(g, c, 1, lv >= 2 ? 2 : 1, a.trim, a.stairs);
        if (lv >= 2) plinth(g, c, 2, 1, a.trim, a.stairs);
        int start = lv >= 2 ? 3 : 2;
        for (int y = start; y < Hc; y++) g.set(c, y, c, y % 3 == 0 ? a.glow : a.glass);
        g.set(c, Hc, c, a.trim);
        // pilares em volta (o vão da frente fica livre)
        int k = lv >= 4 ? 3 : 2;
        int ph = Hc - (lv >= 3 ? 2 : 3);
        for (int[] d : new int[][] {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
            int px = c + d[0] * k, pz = c + d[1] * k;
            for (int y = 1; y <= ph; y++) g.set(px, y, pz, y % 5 == 0 ? a.trim : a.pillar);
            g.set(px, ph + 1, pz, s(Blocks.END_ROD));
            if (lv >= 5) g.set(px, 1, pz, a.rich);
        }
        // anéis de laje ligando os pilares (3+)
        if (lv >= 3) {
            int[] ys = lv >= 5 ? new int[] {ph / 2, ph - 1} : new int[] {ph - 1};
            for (int ry : ys) {
                for (int dx = -k; dx <= k; dx++) {
                    for (int dz = -k; dz <= k; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != k) continue;
                        if (Math.abs(dx) == k && Math.abs(dz) == k) continue;
                        g.set(c + dx, ry, c + dz, slab(a.slab, SlabType.TOP));
                    }
                }
                g.set(c, ry, c, a.glow);
            }
        }
        // fontes de cristal no chão (4+)
        if (lv >= 4) {
            for (Direction d : new Direction[] {Direction.NORTH, Direction.EAST, Direction.WEST}) {
                int fx = c + d.getStepX() * (k + 1), fz = c + d.getStepZ() * (k + 1);
                g.set(fx, 1, fz, s(Blocks.AMETHYST_BLOCK));
                g.set(fx, 2, fz, s(Blocks.AMETHYST_CLUSTER));
            }
        }
        // cristal flutuante (um bloco de ar entre a coluna e ele)
        int cy = Hc + 2;
        g.set(c, cy, c, s(Blocks.AMETHYST_BLOCK));
        g.set(c, cy + 1, c, lv >= 3 ? s(Blocks.BUDDING_AMETHYST) : s(Blocks.AMETHYST_BLOCK));
        g.set(c, cy + 2, c, s(Blocks.AMETHYST_CLUSTER));
        if (lv >= 2) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                g.set(c + d.getStepX(), cy + 1, c + d.getStepZ(), lv >= 5 ? s(Blocks.AMETHYST_BLOCK) : face(Blocks.AMETHYST_CLUSTER, d));
                if (lv >= 5) g.set(c + 2 * d.getStepX(), cy + 1, c + 2 * d.getStepZ(), face(Blocks.AMETHYST_CLUSTER, d));
            }
        }
        return g.tpl();
    }

    /**
     * Farol de Vigia Arcano: torre de pedra com escada de mão, sala da lanterna de vidro do estilo e o "olho" de luz no
     * meio. 3+: varanda com parapeito; 4+: raios de bastão do fim saindo do olho e lanternas nos cantos; 5: ouro e runas.
     */
    private static Templates.Tpl arcaneBeacon(SettlementStyle st, int lv) {
        AP a = arc(st, lv);
        int w = new int[] {7, 7, 9, 9, 11}[lv - 1];
        int c = w / 2;
        int t = 2; // torre 5x5
        int Ht = new int[] {7, 9, 11, 13, 15}[lv - 1];
        G g = new G(w, w);
        g.fill(0, 0, 0, w - 1, 0, w - 1, a.floor);
        if (lv >= 5) {
            for (int x = 0; x < w; x++) {
                for (int z = 0; z < w; z++) {
                    if (Math.max(Math.abs(x - c), Math.abs(z - c)) == c && (x + z) % 2 == 0) g.set(x, 0, z, a.rune);
                }
            }
        }
        // corpo da torre: paredes com faixas, janelas de seteira
        for (int y = 1; y < Ht; y++) {
            for (int dx = -t; dx <= t; dx++) {
                for (int dz = -t; dz <= t; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != t) continue;
                    boolean corner = Math.abs(dx) == t && Math.abs(dz) == t;
                    BlockState b = corner ? a.pillar : (y % 4 == 0 ? a.trim : a.stone);
                    if (!corner && y % 4 == 2 && (dx == 0 || dz == 0) && y > 2) b = s(Blocks.GLASS_PANE);
                    g.set(c + dx, y, c + dz, b);
                }
            }
        }
        // porta na frente e escada de mão no fundo (sobe até a sala da lanterna)
        door(g, c, 1, c + t, st == SettlementStyle.DESERT ? Blocks.ACACIA_DOOR : st == SettlementStyle.NORDIC || st == SettlementStyle.DWARVEN ? Blocks.SPRUCE_DOOR
                : st == SettlementStyle.ELVEN ? Blocks.BIRCH_DOOR : st == SettlementStyle.ORIENTAL ? Blocks.MANGROVE_DOOR : Blocks.DARK_OAK_DOOR);
        for (int dx = -t; dx <= t; dx++) for (int dz = -t; dz <= t; dz++) g.set(c + dx, Ht, c + dz, a.trim);
        for (int y = 1; y <= Ht; y++) g.set(c, y, c - t + 1, ladder(Direction.SOUTH));
        g.set(c, Ht - 1, c, hanging(Blocks.LANTERN));
        // varanda (3+)
        if (lv >= 3) {
            int b = t + 1;
            for (int dx = -b; dx <= b; dx++) {
                for (int dz = -b; dz <= b; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != b) continue;
                    g.set(c + dx, Ht, c + dz, slab(a.slab, SlabType.TOP));
                    g.set(c + dx, Ht + 1, c + dz, s(a.fence));
                }
            }
        }
        // sala da lanterna: pilares nos cantos, vidro do estilo nas laterais
        for (int y = Ht + 1; y <= Ht + 3; y++) {
            for (int dx = -t; dx <= t; dx++) {
                for (int dz = -t; dz <= t; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != t) continue;
                    boolean corner = Math.abs(dx) == t && Math.abs(dz) == t;
                    g.set(c + dx, y, c + dz, corner ? a.pillar : a.glass);
                }
            }
        }
        // saída para a varanda (frente)
        if (lv >= 3) {
            g.clear(c, Ht + 1, c + t);
            g.clear(c, Ht + 2, c + t);
        }
        // o olho
        g.set(c, Ht + 1, c, a.glow);
        g.set(c, Ht + 2, c, lv >= 3 ? s(Blocks.AMETHYST_BLOCK) : a.glow);
        if (lv >= 4) {
            for (Direction d : Direction.Plane.HORIZONTAL) g.set(c + d.getStepX(), Ht + 2, c + d.getStepZ(), face(Blocks.END_ROD, d));
        }
        // telhado em pirâmide e antena
        for (int dx = -t; dx <= t; dx++) {
            for (int dz = -t; dz <= t; dz++) {
                boolean rim = Math.max(Math.abs(dx), Math.abs(dz)) == t;
                g.set(c + dx, Ht + 4, c + dz, rim ? outStair(a.stairs, dx, dz) : a.stone);
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                g.set(c + dx, Ht + 5, c + dz, (dx == 0 && dz == 0) ? (lv >= 5 ? a.rich : a.trim) : outStair(a.stairs, dx, dz));
            }
        }
        g.set(c, Ht + 6, c, lv >= 3 ? s(Blocks.END_ROD) : s(Blocks.LIGHTNING_ROD));
        // lanternas nos cantos do terreno (4+)
        if (lv >= 4) {
            for (int[] cc : new int[][] {{0, 0}, {w - 1, 0}, {0, w - 1}, {w - 1, w - 1}}) {
                g.set(cc[0], 1, cc[1], lv >= 5 ? a.rich : a.trim);
                g.set(cc[0], 2, cc[1], s(a.fence));
                g.set(cc[0], 3, cc[1], lantern(Blocks.SOUL_LANTERN));
            }
        }
        return g.tpl();
    }

    private ProcBuildings() {}
}
