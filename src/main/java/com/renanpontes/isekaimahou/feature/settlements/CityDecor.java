package com.renanpontes.isekaimahou.feature.settlements;

import java.util.List;
import java.util.Set;

import com.renanpontes.isekaimahou.feature.settlements.build.CityLayout;
import com.renanpontes.isekaimahou.feature.settlements.build.CityTerrain;

import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * ENFEITES DE FESTA da cidade (v1.0 — "faz o festival durar 2 dias e ter 'efeitos' na cidade tipo decoração").
 * <p>API (para o festival e o que mais precisar):
 * <pre>
 *   CityDecor.set(city, true)          liga os enfeites (ficam até alguém desligar)
 *   CityDecor.setUntil(city, dia)      liga e desliga sozinho quando o dia do Overworld passar de {@code dia}
 *   CityDecor.set(city, false)         desliga: os enfeites saem aos poucos
 *   CityDecor.isOn(city)               está enfeitada?
 *   CityDecor.clearNow(level, city)    tira tudo na hora (cidade dissolvida, comando)
 * </pre>
 * O que aparece, na frente de CADA prédio pronto e em volta da praça (na borda do lote, fora da calçada da porta):
 * um mastro de festa (cerca de cerejeira, lã colorida com 4 bandeirolas e lanterna), um poste de velas acesas e vasos
 * de flor. À noite sobem fogos de artifício de verdade (estouram bem acima dos telhados).
 * <p><b>Desfaz limpo</b>: só ocupa blocos de AR em cima de chão de rua/terreno; cada bloco colocado fica anotado na
 * cidade ({@link Settlement#decorBlocks}, salvo no mundo) e na hora de tirar só sai o que ainda for um bloco de
 * enfeite. Nada é quebrado, nada dá item. O trabalho é espalhado: 1 lote a cada 10 ticks, com o coração carregado.
 */
public final class CityDecor {
    public static final String FESTIVAL = "festival";

    private static final Block POLE = Blocks.CHERRY_FENCE;
    private static final Block[] WOOL = {Blocks.RED_WOOL, Blocks.YELLOW_WOOL, Blocks.LIME_WOOL, Blocks.LIGHT_BLUE_WOOL,
            Blocks.MAGENTA_WOOL, Blocks.ORANGE_WOOL};
    private static final Block[] BUNTING = {Blocks.RED_WALL_BANNER, Blocks.YELLOW_WALL_BANNER, Blocks.LIME_WALL_BANNER,
            Blocks.LIGHT_BLUE_WALL_BANNER, Blocks.MAGENTA_WALL_BANNER, Blocks.ORANGE_WALL_BANNER};
    private static final Block[] CANDLES = {Blocks.RED_CANDLE, Blocks.YELLOW_CANDLE, Blocks.LIME_CANDLE, Blocks.LIGHT_BLUE_CANDLE,
            Blocks.MAGENTA_CANDLE, Blocks.ORANGE_CANDLE};
    private static final Block[] POTS = {Blocks.POTTED_POPPY, Blocks.POTTED_DANDELION, Blocks.POTTED_BLUE_ORCHID, Blocks.POTTED_ALLIUM,
            Blocks.POTTED_AZURE_BLUET, Blocks.POTTED_RED_TULIP, Blocks.POTTED_PINK_TULIP, Blocks.POTTED_OXEYE_DAISY,
            Blocks.POTTED_CORNFLOWER};
    private static final int[] SPARK = {0xFF4D4D, 0xFFD23F, 0x6BE36B, 0x5CC8FF, 0xE86BFF, 0xFF9A3D, 0xFFFFFF};
    /** Tudo o que esta classe coloca (na hora de tirar, só sai bloco desta lista). */
    private static final Set<Block> OURS = new java.util.HashSet<>();

    static {
        OURS.add(POLE);
        OURS.add(Blocks.LANTERN);
        OURS.addAll(List.of(WOOL));
        OURS.addAll(List.of(BUNTING));
        OURS.addAll(List.of(CANDLES));
        OURS.addAll(List.of(POTS));
    }

    // =====================================================================================
    //  API
    // =====================================================================================
    public static boolean isOn(Settlement s) {
        return !s.decor.isEmpty();
    }

    /** Quantos blocos de enfeite a cidade tem agora. */
    public static int placed(Settlement s) {
        return s.decorBlocks.size();
    }

    /** Liga (até desligarem) ou desliga os enfeites. Os blocos entram/saem aos poucos pelo tick do coração. */
    public static void set(Settlement s, boolean on) {
        s.decor = on ? FESTIVAL : "";
        s.decorUntil = -1;
        s.decorCursor = 0;
    }

    /** Liga os enfeites até o fim do dia {@code lastDay} do Overworld (depois desligam sozinhos). */
    public static void setUntil(Settlement s, long lastDay) {
        s.decor = FESTIVAL;
        s.decorUntil = lastDay;
        s.decorCursor = 0;
    }

    /** Prédio novo ou reformado: a cidade enfeitada passa de novo pelos lotes (só completa o que falta). */
    public static void refresh(Settlement s) {
        s.decorCursor = 0;
    }

    /** Uma obra vai limpar esta área: os enfeites dali deixam de ser nossos (a limpeza leva os blocos). */
    public static void forgetArea(Settlement s, int x0, int z0, int x1, int z1) {
        if (s.decorBlocks.isEmpty()) return;
        s.decorBlocks.removeIf(p -> p.getX() >= x0 && p.getX() <= x1 && p.getZ() >= z0 && p.getZ() <= z1);
    }

    /** Tira todos os enfeites agora (só os de chunks carregados saem; os outros ficam anotados). Devolve quantos saíram. */
    public static int clearNow(ServerLevel sl, Settlement s) {
        s.decor = "";
        s.decorUntil = -1;
        return remove(sl, s, Integer.MAX_VALUE);
    }

    // =====================================================================================
    //  Tick (chamado pelo coração da cidade)
    // =====================================================================================
    public static void tick(ServerLevel sl, Settlement s, int age) {
        if (s.decor.isEmpty() && s.decorBlocks.isEmpty()) return;
        if (age % 10 != 5) return;
        long today = SettlementService.day(sl.getServer());
        // fim da festa; ou data de fim absurda de longe (sobra de relógio mexido em mundo antigo): desliga também
        if (isOn(s) && s.decorUntil >= 0 && (today > s.decorUntil || s.decorUntil > today + 8)) set(s, false);
        if (!isOn(s)) {
            remove(sl, s, 40);
            return;
        }
        int cell = site(s, s.decorCursor);
        if (cell != Integer.MIN_VALUE) {
            s.decorCursor++;
            decorate(sl, s, cell);
        }
        long time = sl.getDayTime() % 24000L;
        if (age % 40 == 5 && time >= 13000 && time <= 23000 && sl.random.nextInt(5) < 3
                && sl.getNearestPlayer(s.heart.getX(), s.heart.getY(), s.heart.getZ(), 160.0, false) != null) {
            firework(sl, s);
        }
    }

    /** O n-ésimo lugar a enfeitar: a praça e depois cada prédio (Integer.MIN_VALUE = acabou). */
    private static int site(Settlement s, int n) {
        if (n == 0) return CityLayout.CENTER_CELL;
        int k = 1;
        for (int cell : s.buildings.keySet()) {
            if (cell == CityLayout.CENTER_CELL || cell < 0) continue;
            if (k++ == n) return cell;
        }
        return Integer.MIN_VALUE;
    }

    private static void decorate(ServerLevel sl, Settlement s, int cell) {
        if (cell == CityLayout.CENTER_CELL) {
            // praça: mastros e velas nos quatro lados, fora das entradas
            int n = 0;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                Direction p = d.getClockWise();
                BlockPos edge = s.heart.relative(d, 7);
                mast(sl, s, edge.relative(p, 4), n++);
                candles(sl, s, edge.relative(p, -4), n++);
                pot(sl, s, edge.relative(p, 6), n);
            }
            return;
        }
        BlockPos c = CityLayout.cellCenter(s.heart, cell);
        Direction front = CityLayout.facingHeart(cell).rotate(Direction.SOUTH);
        Direction side = front.getClockWise();
        BlockPos edge = c.relative(front, 7);
        int n = Math.floorMod(cell * 31, 97);
        mast(sl, s, edge.relative(side, 4), n);
        candles(sl, s, edge.relative(side, -4), n + 1);
        pot(sl, s, edge.relative(side, 6), n + 2);
        pot(sl, s, edge.relative(side, -6), n + 3);
    }

    /**
     * Onde dá para pôr um enfeite na coluna (x, z): devolve o primeiro bloco de ar em cima do chão, ou null se o
     * lugar não serve (chunk fora, chão que não é rua/terreno, coisa no caminho, altura estranha).
     */
    private static BlockPos spot(ServerLevel sl, Settlement s, BlockPos at, int height) {
        if (!sl.hasChunk(at.getX() >> 4, at.getZ() >> 4)) return null;
        int y = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
        if (Math.abs(y - s.heart.getY()) > 40) return null;
        // lote com altura conhecida: só no chão dele (nunca em cima de um telhado ou de uma muralha)
        int dx = CityLayout.indexAt(s, at.getX() - s.heart.getX()), dz = CityLayout.indexAt(s, at.getZ() - s.heart.getZ());
        if (dx != Integer.MIN_VALUE && dz != Integer.MIN_VALUE) {
            Integer floor = CityTerrain.levelOf(s, CityLayout.cellAt(s, dx, dz));
            if (floor != null && Math.abs(y - 1 - floor) > 1) return null;
        }
        BlockPos base = new BlockPos(at.getX(), y, at.getZ());
        BlockState ground = sl.getBlockState(base.below());
        if (ground.isAir() || !ground.getFluidState().isEmpty() || ground.is(net.minecraft.tags.BlockTags.LEAVES)
                || !CityTerrain.ground(ground, s.style.foundation())
                && !com.renanpontes.isekaimahou.feature.settlements.build.TierLook.isStreet(ground)
                // onda 8 (water_city): o deque e as passarelas de tábua também são chão de festa
                && !(s.water && WaterCity.isDeck(ground)) || ground.canBeReplaced()) {
            return null;
        }
        for (int i = 0; i < height; i++) if (!CityTerrain.free(sl.getBlockState(base.above(i)), i == 0)) return null;
        return base;
    }

    private static void put(ServerLevel sl, Settlement s, BlockPos pos, BlockState st) {
        sl.setBlock(pos, st, 2 | 16);
        s.decorBlocks.add(pos.immutable());
    }

    /** Mastro de festa: 3 de cerca, lã colorida com bandeirolas nos 4 lados e lanterna no topo. */
    private static void mast(ServerLevel sl, Settlement s, BlockPos at, int n) {
        BlockPos base = spot(sl, s, at, 5);
        if (base == null) return;
        for (int i = 0; i < 3; i++) put(sl, s, base.above(i), POLE.defaultBlockState());
        BlockPos top = base.above(3);
        put(sl, s, top, WOOL[Math.floorMod(n, WOOL.length)].defaultBlockState());
        int k = n + 1;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos b = top.relative(d);
            if (!sl.getBlockState(b).isAir() || !sl.getBlockState(b.below()).isAir()) continue;
            put(sl, s, b, BUNTING[Math.floorMod(k++, BUNTING.length)].defaultBlockState().setValue(WallBannerBlock.FACING, d));
        }
        put(sl, s, top.above(), Blocks.LANTERN.defaultBlockState());
    }

    /** Poste baixo com velas coloridas acesas. */
    private static void candles(ServerLevel sl, Settlement s, BlockPos at, int n) {
        BlockPos base = spot(sl, s, at, 3);
        if (base == null) return;
        put(sl, s, base, POLE.defaultBlockState());
        put(sl, s, base.above(), CANDLES[Math.floorMod(n, CANDLES.length)].defaultBlockState()
                .setValue(CandleBlock.CANDLES, 3).setValue(AbstractCandleBlock.LIT, true));
    }

    private static void pot(ServerLevel sl, Settlement s, BlockPos at, int n) {
        BlockPos base = spot(sl, s, at, 2);
        if (base == null) return;
        put(sl, s, base, POTS[Math.floorMod(n, POTS.length)].defaultBlockState());
    }

    /** Tira até {@code max} enfeites, do último colocado para o primeiro (lanterna e bandeirolas antes do mastro). */
    private static int remove(ServerLevel sl, Settlement s, int max) {
        int done = 0;
        for (int i = s.decorBlocks.size() - 1; i >= 0 && done < max; i--) {
            BlockPos p = s.decorBlocks.get(i);
            if (!sl.hasChunk(p.getX() >> 4, p.getZ() >> 4)) continue;
            if (OURS.contains(sl.getBlockState(p).getBlock())) sl.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16);
            s.decorBlocks.remove(i);
            done++;
        }
        return done;
    }

    /** Fogo de artifício de verdade, solto acima dos telhados de um prédio qualquer (ou da praça). */
    static void firework(ServerLevel sl, Settlement s) {
        RandomSource r = sl.random;
        int cell = site(s, r.nextInt(Math.max(1, s.buildings.size() + 1)));
        BlockPos c = cell == Integer.MIN_VALUE || cell == CityLayout.CENTER_CELL ? s.heart : CityLayout.cellCenter(s.heart, cell);
        int x = c.getX() + r.nextInt(11) - 5, z = c.getZ() + r.nextInt(11) - 5;
        if (!sl.hasChunk(x >> 4, z >> 4)) return;
        // sai 6 blocos acima de tudo e voa bastante: o estouro fica longe de gente e de telhado
        int y = sl.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + 6;
        FireworkExplosion.Shape[] shapes = FireworkExplosion.Shape.values();
        FireworkExplosion boom = new FireworkExplosion(shapes[r.nextInt(shapes.length)],
                IntList.of(SPARK[r.nextInt(SPARK.length)], s.style.color), IntList.of(SPARK[r.nextInt(SPARK.length)]),
                r.nextBoolean(), r.nextBoolean());
        ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
        rocket.set(DataComponents.FIREWORKS, new Fireworks(2, List.of(boom)));
        sl.addFreshEntity(new FireworkRocketEntity(sl, x + 0.5, y, z + 0.5, rocket));
    }

    private CityDecor() {}
}
