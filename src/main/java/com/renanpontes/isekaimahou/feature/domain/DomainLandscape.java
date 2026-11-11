package com.renanpontes.isekaimahou.feature.domain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MahouConfig;
import com.renanpontes.isekaimahou.core.api.Places;

import it.unimi.dsi.fastutil.longs.LongArrayList;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

/**
 * Lei "Paisagem Elemental": troca TEMPORARIAMENTE a superfície do chão dentro do domo por blocos do elemento
 * (numa onda que parte do centro) e devolve os blocos originais quando o domínio acaba.
 *
 * Segurança: nunca mexe em blocos com BlockEntity, líquidos, blocos indestrutíveis ou que não sejam cubos
 * cheios, nem dentro de lugares seguros (cidades). A varredura tem orçamento por tick (domos enormes). Plantas em cima do chão são guardadas e removidas junto (para não "estourarem" e soltarem itens).
 * As trocas usam flags sem atualização de vizinhos. Tudo fica salvo no NBT do domo, então sobrevive a
 * descarregar o chunk / fechar o mundo. Respeita {@link MahouConfig#DOMAIN_GRIEFING}.
 */
public final class DomainLandscape {
    /** Sem atualizar vizinhos nem formas: plantas e redstone ao redor não reagem à troca temporária. */
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    /** Ticks que a onda leva do centro até a borda. */
    private static final int WAVE_TICKS = 14;
    /** Limite de blocos trocados (domos enormes com muitos Expandir). */
    private static final int MAX_BLOCKS = 6000;

    private record Swap(BlockState original, BlockState placed) {}

    /** Colunas a varrer por tick (cada uma lê até ~18 blocos): orçamento para domos enormes. */
    private static final int COLUMNS_PER_TICK = 700;

    private final LinkedHashMap<BlockPos, Swap> swaps = new LinkedHashMap<>();
    /** Colunas pendentes (distância² nos 32 bits altos, dx/dz nos baixos), do centro para fora. Null = terminou. */
    private long[] columns;
    private int columnIndex;
    private int scanX, scanZ, scanTop, scanBottom, scanDepth = 1;
    private int waveTick;
    private boolean usesMagma;

    // =====================================================================================
    //  Paletas por elemento
    // =====================================================================================
    private static final Map<Element, List<BlockState>> PALETTES = new EnumMap<>(Element.class);

    private static void pal(Element e, Object... weighted) {
        List<BlockState> list = new ArrayList<>();
        for (int i = 0; i < weighted.length; i += 2) {
            Block b = (Block) weighted[i];
            int w = (Integer) weighted[i + 1];
            for (int k = 0; k < w; k++) list.add(b.defaultBlockState());
        }
        PALETTES.put(e, list);
    }

    static {
        pal(Element.ARCANE, Blocks.AMETHYST_BLOCK, 3, Blocks.PURPUR_BLOCK, 2, Blocks.CALCITE, 1);
        pal(Element.FIRE, Blocks.MAGMA_BLOCK, 6, Blocks.NETHERRACK, 2);
        pal(Element.WATER, Blocks.BLUE_ICE, 3, Blocks.PRISMARINE, 2, Blocks.DARK_PRISMARINE, 1, Blocks.PRISMARINE_BRICKS, 1);
        pal(Element.EARTH, Blocks.DEEPSLATE, 4, Blocks.COBBLED_DEEPSLATE, 2, Blocks.TUFF, 1);
        pal(Element.WIND, Blocks.WHITE_WOOL, 3, Blocks.CALCITE, 3);
        pal(Element.LIGHT, Blocks.QUARTZ_BLOCK, 3, Blocks.GLOWSTONE, 2, Blocks.SMOOTH_QUARTZ, 1);
        pal(Element.SHADOW, Blocks.BLACKSTONE, 4, Blocks.POLISHED_BLACKSTONE, 2, Blocks.CRYING_OBSIDIAN, 1, Blocks.SCULK, 1);
    }

    /** Acento extra de cada fusão (somado à mistura das paletas dos dois elementos-pai). */
    private static Object[] accent(Element e) {
        return switch (e) {
            case MAGMA -> new Object[]{Blocks.MAGMA_BLOCK, 3, Blocks.BASALT, 2};
            case STEAM -> new Object[]{Blocks.WHITE_CONCRETE, 2};
            case LIGHTNING -> new Object[]{Blocks.WAXED_COPPER_BLOCK, 2, Blocks.WAXED_CUT_COPPER, 1};
            case SUN -> new Object[]{Blocks.SHROOMLIGHT, 2, Blocks.OCHRE_FROGLIGHT, 1};
            case HELLFIRE -> new Object[]{Blocks.SOUL_SOIL, 3, Blocks.NETHER_BRICKS, 1};
            case NATURE -> new Object[]{Blocks.MOSS_BLOCK, 4};
            case ICE -> new Object[]{Blocks.PACKED_ICE, 4};
            case PURITY -> new Object[]{Blocks.WHITE_CONCRETE, 2, Blocks.DIORITE, 1};
            case ABYSS -> new Object[]{Blocks.DARK_PRISMARINE, 2, Blocks.SCULK, 2};
            case SAND -> new Object[]{Blocks.SMOOTH_SANDSTONE, 3, Blocks.SANDSTONE, 2};
            case CRYSTAL -> new Object[]{Blocks.AMETHYST_BLOCK, 4};
            case GRAVITY -> new Object[]{Blocks.OBSIDIAN, 2, Blocks.CRYING_OBSIDIAN, 1};
            case ASTRAL -> new Object[]{Blocks.END_STONE, 2, Blocks.PURPUR_BLOCK, 2};
            case VOID -> new Object[]{Blocks.OBSIDIAN, 2, Blocks.END_STONE_BRICKS, 1};
            case TWILIGHT -> new Object[]{Blocks.PURPUR_BLOCK, 2, Blocks.CRYING_OBSIDIAN, 1};
            default -> new Object[0];
        };
    }

    /** Paleta do elemento: base direta; fusões misturam as dos pais + o acento próprio. */
    public static List<BlockState> palette(Element e) {
        if (!e.isFusion()) return PALETTES.getOrDefault(e, PALETTES.get(Element.ARCANE));
        List<BlockState> list = new ArrayList<>(PALETTES.get(e.parentA));
        list.addAll(PALETTES.get(e.parentB));
        Object[] acc = accent(e);
        for (int i = 0; i < acc.length; i += 2) {
            for (int k = 0; k < (Integer) acc[i + 1]; k++) list.add(((Block) acc[i]).defaultBlockState());
        }
        return list;
    }

    // =====================================================================================
    //  Ciclo de vida
    // =====================================================================================
    public boolean usesMagma() { return usesMagma; }
    public boolean contains(BlockPos pos) { return swaps.containsKey(pos); }

    /**
     * O bloco em {@code pos} é um bloco que a paisagem COLOCOU (e ainda está lá)? Posições de plantas tiradas
     * (viraram ar) não contam: se um jogador pôs algo ali, é dele.
     */
    public boolean isPlaced(BlockPos pos, BlockState state) {
        Swap s = swaps.get(pos);
        return s != null && !s.placed().isAir() && state == s.placed();
    }

    /**
     * Prepara a onda de troca: as colunas dentro do domo, do centro para fora. A varredura de cada coluna (achar a
     * superfície) é feita aos poucos em {@link #tick}, com orçamento por tick — domos de raio 80+ têm 20 mil colunas.
     * @param depth 1 = só a superfície; 2 = superfície + a camada de baixo (Domínio Absoluto)
     */
    public void begin(ServerLevel level, Vec3 center, float radius, Element element, int depth) {
        if (!MahouConfig.DOMAIN_GRIEFING.get()) return;
        int r = (int) Math.floor(radius - 0.5F);
        int cx = (int) Math.floor(center.x), cz = (int) Math.floor(center.z);
        LongArrayList cols = new LongArrayList();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 > r * r) continue;
                // distância nos bits altos: ordenar o long ordena do centro para fora
                cols.add(((long) d2 << 32) | ((long) (dx + 32768) << 16) | (dz + 32768));
            }
        }
        long[] arr = cols.toLongArray();
        Arrays.sort(arr);
        columns = arr;
        columnIndex = 0;
        scanX = cx;
        scanZ = cz;
        scanTop = (int) Math.floor(center.y + Math.min(radius * 0.6F, 10));
        scanBottom = (int) Math.floor(center.y - Math.min(radius * 0.6F, 8));
        scanDepth = depth;
        waveTick = 0;
    }

    /** Avança a onda de troca (chamado todo tick enquanto o domo está ativo). */
    public void tick(ServerLevel level, Vec3 center, float radius, Element element) {
        if (columns == null) return;
        waveTick++;
        double front = radius * Math.min(1.0, waveTick / (double) WAVE_TICKS);
        double front2 = front * front;
        List<BlockState> pal = palette(element);
        RandomSource rnd = level.random;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int budget = COLUMNS_PER_TICK;
        while (columnIndex < columns.length && budget-- > 0) {
            long col = columns[columnIndex];
            int d2 = (int) (col >>> 32);
            if (d2 > front2) break;
            columnIndex++;
            if (swaps.size() >= MAX_BLOCKS) {
                columnIndex = columns.length;
                break;
            }
            int dx = (int) ((col >>> 16) & 0xFFFF) - 32768, dz = (int) (col & 0xFFFF) - 32768;
            BlockPos surface = surfaceOf(level, m, scanX + dx, scanZ + dz, center, radius);
            if (surface == null) continue;
            swap(level, surface, pal, rnd);
            if (scanDepth > 1) {
                BlockPos below = surface.below();
                BlockState belowState = level.getBlockState(below);
                if (isTerrain(belowState) && canReplace(level, below, belowState)) swap(level, below, pal, rnd);
            }
        }
        if (columnIndex >= columns.length) columns = null;
    }

    /**
     * Superfície da coluna (x, z): primeiro bloco de chão natural abaixo de um espaço aberto (em cavernas, ou com o
     * topo da varredura dentro de um morro, pula a rocha do teto e acha o chão de verdade). Null se não houver.
     */
    private BlockPos surfaceOf(ServerLevel level, BlockPos.MutableBlockPos m, int x, int z, Vec3 center, float radius) {
        boolean open = false;
        for (int y = scanTop; y >= scanBottom; y--) {
            m.set(x, y, z);
            if (!level.isLoaded(m)) return null;
            BlockState st = level.getBlockState(m);
            if (st.isAir() || isPlant(st) || st.is(BlockTags.LEAVES)) {
                open = true;
                continue;
            }
            if (!open) continue;
            // primeiro bloco "de verdade" abaixo do espaço aberto (telhado, tronco etc.: a coluna fica como está)
            if (isTerrain(st) && Vec3.atCenterOf(m).distanceToSqr(center) <= radius * radius && canReplace(level, m, st)
                    && !Places.isSafe(level, m)) {
                return m.immutable();
            }
            return null;
        }
        return null;
    }

    private void swap(ServerLevel level, BlockPos p, List<BlockState> pal, RandomSource rnd) {
        BlockState cur = level.getBlockState(p);
        if (swaps.containsKey(p) || !canReplace(level, p, cur)) return;
        BlockState placed = pal.get(rnd.nextInt(pal.size()));
        if (placed.is(Blocks.MAGMA_BLOCK)) usesMagma = true;
        // planta em cima: guarda e tira (senão ela quebra e solta item)
        BlockPos up = p.above();
        BlockState upState = level.getBlockState(up);
        if (isPlant(upState) && !swaps.containsKey(up)) {
            if (upState.getBlock() instanceof DoublePlantBlock
                    && upState.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER) {
                BlockPos up2 = up.above();
                swaps.put(up2, new Swap(level.getBlockState(up2), Blocks.AIR.defaultBlockState()));
                level.setBlock(up2, Blocks.AIR.defaultBlockState(), FLAGS);
            }
            swaps.put(up, new Swap(upState, Blocks.AIR.defaultBlockState()));
            level.setBlock(up, Blocks.AIR.defaultBlockState(), FLAGS);
        }
        swaps.put(p, new Swap(cur, placed));
        level.setBlock(p, placed, FLAGS);
        if (rnd.nextInt(4) == 0) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, placed), p.getX() + 0.5, p.getY() + 1.05,
                    p.getZ() + 0.5, 4, 0.3, 0.05, 0.3, 0.1);
        }
    }

    /** Devolve TODOS os blocos originais (na ordem inversa da troca; sem atualizar vizinhos, então a ordem não quebra plantas). */
    public void restore(Level level) {
        columns = null;
        columnIndex = 0;
        if (swaps.isEmpty()) return;
        List<Map.Entry<BlockPos, Swap>> list = new ArrayList<>(swaps.entrySet());
        ListIterator<Map.Entry<BlockPos, Swap>> it = list.listIterator(list.size());
        int i = 0;
        while (it.hasPrevious()) {
            Map.Entry<BlockPos, Swap> e = it.previous();
            BlockPos p = e.getKey();
            Swap s = e.getValue();
            BlockState cur = level.getBlockState(p);
            // v0.8: a magia destruiu o terreno ali (cratera, Lei Ruína, Vazio Roxo...): sumiu e há um buraco embaixo —
            // devolver criaria blocos flutuando sobre a cratera. A destruição vence; o original não volta.
            if (cur.isAir() && level.getBlockState(p.below()).isAir()) continue;
            // Só devolve se o bloco ainda for o nosso (ou se sumiu): nunca apaga algo que outro jogador pôs.
            if (cur == s.placed() || cur.isAir()) {
                level.setBlock(p, s.original(), FLAGS);
                if (level instanceof ServerLevel sl && (i++ % 6) == 0 && !s.original().isAir()) {
                    sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, s.placed().isAir() ? s.original() : s.placed()),
                            p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5, 3, 0.3, 0.1, 0.3, 0.05);
                }
            }
        }
        swaps.clear();
    }

    // =====================================================================================
    //  Regras de troca
    // =====================================================================================
    private static boolean isPlant(BlockState st) {
        return !st.isAir() && st.canBeReplaced() && !st.hasBlockEntity() && st.getFluidState().isEmpty();
    }

    /** Só chão natural (terra, pedra, areia...): construções dos jogadores nunca são trocadas. */
    private static boolean isTerrain(BlockState st) {
        return st.is(BlockTags.DIRT) || st.is(BlockTags.SAND) || st.is(BlockTags.BASE_STONE_OVERWORLD)
                || st.is(BlockTags.BASE_STONE_NETHER) || st.is(BlockTags.NYLIUM) || st.is(BlockTags.TERRACOTTA)
                || st.is(Blocks.GRAVEL) || st.is(Blocks.SANDSTONE) || st.is(Blocks.RED_SANDSTONE) || st.is(Blocks.SNOW_BLOCK)
                || st.is(Blocks.CLAY) || st.is(Blocks.COBBLESTONE) || st.is(Blocks.MOSSY_COBBLESTONE) || st.is(Blocks.CALCITE)
                || st.is(Blocks.END_STONE) || st.is(Blocks.PACKED_MUD) || st.is(Blocks.DRIPSTONE_BLOCK)
                || st.is(Blocks.SMOOTH_BASALT) || st.is(Blocks.SOUL_SOIL) || st.is(Blocks.SOUL_SAND);
    }

    private static boolean canReplace(Level level, BlockPos pos, BlockState st) {
        if (st.isAir() || st.hasBlockEntity() || !st.getFluidState().isEmpty()) return false;
        if (st.is(BlockTags.FEATURES_CANNOT_REPLACE) || st.is(BlockTags.WITHER_IMMUNE)) return false;
        if (st.getDestroySpeed(level, pos) < 0) return false;
        return st.isCollisionShapeFullBlock(level, pos);
    }

    // =====================================================================================
    //  NBT
    // =====================================================================================
    public void save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<BlockPos, Swap> e : swaps.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.put("pos", NbtUtils.writeBlockPos(e.getKey()));
            t.put("orig", NbtUtils.writeBlockState(e.getValue().original()));
            t.put("placed", NbtUtils.writeBlockState(e.getValue().placed()));
            list.add(t);
        }
        tag.put("Landscape", list);
        tag.putBoolean("LandscapeMagma", usesMagma);
    }

    public void load(CompoundTag tag, Level level) {
        swaps.clear();
        HolderGetter<Block> blocks = level.holderLookup(Registries.BLOCK);
        ListTag list = tag.getList("Landscape", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            NbtUtils.readBlockPos(t, "pos").ifPresent(p -> swaps.put(p,
                    new Swap(NbtUtils.readBlockState(blocks, t.getCompound("orig")), NbtUtils.readBlockState(blocks, t.getCompound("placed")))));
        }
        // A fila da onda não é salva: depois de recarregar, a paisagem fica como estava.
        usesMagma = tag.getBoolean("LandscapeMagma");
        columns = null;
        columnIndex = 0;
    }
}
