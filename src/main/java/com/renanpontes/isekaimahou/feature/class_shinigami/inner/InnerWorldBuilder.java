package com.renanpontes.isekaimahou.feature.class_shinigami.inner;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.PinkPetalsBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Paisagens do Mundo Interior, geradas por código (sem estruturas em arquivo): uma ilha flutuante de raio ~26 em forma
 * de cone invertido, com uma praça plana no centro (onde o espírito da espada espera), espaço livre para a luta e
 * cenário só no anel de fora. Seis paisagens: sem nome (Mar de Névoa, com a espada gigante cravada) e uma por arquétipo
 * (Planície de Ferro, Jardim das Mil Pétalas, Campo de Neve Eterna, Templo da Chama Ancestral, Página em Branco).
 * Uma parede invisível (barreira) na borda impede quedas. Determinística por (faixa, paisagem): trocar de paisagem
 * coloca os blocos novos e apaga só o que sobrou da antiga. Construída aos poucos ({@link #BUDGET} blocos por tick).
 */
public final class InnerWorldBuilder {
    public static final int RADIUS = 26;
    /** Blocos colocados por tick (todas as obras juntas). */
    public static final int BUDGET = 4500;
    private static final TicketType<ChunkPos> TICKET = TicketType.create(IsekaiMahou.MODID + ":inner_world", Comparator.comparingLong(ChunkPos::toLong), 400);
    private static final int FLAGS = 2 | 16;

    private record Palette(BlockState top, BlockState top2, BlockState sub, BlockState under, BlockState path, BlockState accent) {}

    private static Palette palette(int land) {
        return switch (land) {
            case 1 -> new Palette(Blocks.SMOOTH_BASALT.defaultBlockState(), Blocks.TUFF.defaultBlockState(), Blocks.DEEPSLATE.defaultBlockState(),
                    Blocks.COBBLED_DEEPSLATE.defaultBlockState(), Blocks.POLISHED_DEEPSLATE.defaultBlockState(), Blocks.IRON_BLOCK.defaultBlockState());
            case 2 -> new Palette(Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.MOSS_BLOCK.defaultBlockState(), Blocks.DIRT.defaultBlockState(),
                    Blocks.STONE.defaultBlockState(), Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), Blocks.CHERRY_PLANKS.defaultBlockState());
            case 3 -> new Palette(Blocks.SNOW_BLOCK.defaultBlockState(), Blocks.CALCITE.defaultBlockState(), Blocks.SNOW_BLOCK.defaultBlockState(),
                    Blocks.PACKED_ICE.defaultBlockState(), Blocks.CALCITE.defaultBlockState(), Blocks.BLUE_ICE.defaultBlockState());
            case 4 -> new Palette(Blocks.BLACKSTONE.defaultBlockState(), Blocks.BASALT.defaultBlockState(), Blocks.BLACKSTONE.defaultBlockState(),
                    Blocks.NETHERRACK.defaultBlockState(), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), Blocks.GILDED_BLACKSTONE.defaultBlockState());
            case 5 -> new Palette(Blocks.WHITE_CONCRETE.defaultBlockState(), Blocks.QUARTZ_BLOCK.defaultBlockState(), Blocks.CALCITE.defaultBlockState(),
                    Blocks.WHITE_TERRACOTTA.defaultBlockState(), Blocks.SMOOTH_QUARTZ.defaultBlockState(), Blocks.BLACK_CONCRETE.defaultBlockState());
            default -> new Palette(Blocks.COARSE_DIRT.defaultBlockState(), Blocks.ANDESITE.defaultBlockState(), Blocks.STONE.defaultBlockState(),
                    Blocks.COBBLESTONE.defaultBlockState(), Blocks.POLISHED_ANDESITE.defaultBlockState(), Blocks.STONE_BRICKS.defaultBlockState());
        };
    }

    // =====================================================================================
    //  Plano (determinístico)
    // =====================================================================================
    /** Raio da borda num ângulo (contorno irregular, igual em todas as paisagens da faixa). */
    private static double edge(double ang, int index) {
        double s1 = (index * 1.37) % Mth.TWO_PI, s2 = (index * 2.11) % Mth.TWO_PI;
        return RADIUS + 1.6 * Math.sin(3 * ang + s1) + 1.1 * Math.sin(5 * ang + s2);
    }

    private static int hash(int x, int z, int salt) {
        int h = x * 73856093 ^ z * 19349663 ^ salt * 83492791;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        return h ^ (h >>> 15);
    }

    /** Altura da superfície (relativa ao piso) num ponto; a praça e a área de luta (r &lt; 10) são planas. */
    private static int surface(int dx, int dz, int index) {
        double r = Math.sqrt(dx * dx + dz * dz);
        if (r < 10) return 0;
        double w = Math.sin(dx * 0.31 + index) * 0.8 + Math.cos(dz * 0.27 - index * 0.5) * 0.8;
        return (int) Math.round(Mth.clamp(w * Math.min(1, (r - 10) / 6), -1, 1));
    }

    /** Todas as colocações da paisagem {@code land} (0 = sem nome, 1..5 = arquétipo) na faixa {@code index}. */
    public static Map<BlockPos, BlockState> plan(int index, int land) {
        Map<BlockPos, BlockState> out = new LinkedHashMap<>();
        BlockPos o = InnerWorld.origin(index);
        Palette p = palette(land);
        RandomSource rnd = RandomSource.create(index * 7919L + land * 104729L);
        int R = RADIUS + 3;
        Set<Long> island = new HashSet<>();
        for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) {
                double r = Math.sqrt(dx * dx + dz * dz);
                if (r <= edge(Math.atan2(dz, dx), index)) island.add(BlockPos.asLong(dx, 0, dz));
            }
        }
        for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) {
                if (!island.contains(BlockPos.asLong(dx, 0, dz))) continue;
                double r = Math.sqrt(dx * dx + dz * dz);
                double rr = edge(Math.atan2(dz, dx), index);
                int top = surface(dx, dz, index);
                int depth = 2 + (int) ((rr - r) * 0.55) + Math.floorMod(hash(dx, dz, index), 3);
                int h = hash(dx, dz, land + 11);
                boolean plaza = r <= 5.5;
                BlockState surf = plaza ? p.path : (Math.floorMod(h, 5) == 0 ? p.top2 : p.top);
                if (r > 5.5 && r <= 6.5) surf = p.accent.is(Blocks.IRON_BLOCK) || p.accent.is(Blocks.BLUE_ICE) ? p.path : p.accent;
                if (land == 5 && !plaza && inkStroke(dx, dz, index)) surf = Blocks.BLACK_CONCRETE.defaultBlockState();
                if (land == 4 && !plaza && r > 12 && Math.floorMod(h, 23) == 0) surf = Blocks.MAGMA_BLOCK.defaultBlockState();
                out.put(o.offset(dx, top, dz), surf);
                for (int k = 1; k <= depth; k++) out.put(o.offset(dx, top - k, dz), k <= 2 ? p.sub : p.under);
                // borda: parede invisível para ninguém cair
                boolean border = false;
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    if (!island.contains(BlockPos.asLong(dx + d.getStepX(), 0, dz + d.getStepZ()))) border = true;
                }
                if (border) for (int k = 1; k <= 5; k++) out.put(o.offset(dx, top + k, dz), Blocks.BARRIER.defaultBlockState());
            }
        }
        decorate(out, o, land, index, rnd, island);
        return out;
    }

    /** Traços de tinta da Página em Branco: três curvas largas atravessando a ilha. */
    private static boolean inkStroke(int dx, int dz, int index) {
        for (int k = 0; k < 3; k++) {
            double ang = index * 0.7 + k * 2.1;
            double c = Math.cos(ang), s = Math.sin(ang);
            double u = dx * c + dz * s, v = -dx * s + dz * c;
            double curve = Math.sin(u * 0.18 + k) * 4 + (k - 1) * 7;
            double width = 0.8 + 0.6 * Math.cos(u * 0.12);
            if (Math.abs(v - curve) < width && Math.abs(u) < 22) return true;
        }
        return false;
    }

    private static boolean free(int dx, int dz) {
        double r = Math.sqrt(dx * dx + dz * dz);
        // praça + área de luta + caminho da chegada ficam livres
        return r >= 13 && !(Math.abs(dx) <= 2 && dz > 0 && dz < 14);
    }

    private static void decorate(Map<BlockPos, BlockState> out, BlockPos o, int land, int index, RandomSource rnd, Set<Long> island) {
        // lanternas na praça (4 postes)
        BlockState post = switch (land) {
            case 1 -> Blocks.IRON_BARS.defaultBlockState();
            case 2 -> Blocks.CHERRY_FENCE.defaultBlockState();
            case 3 -> Blocks.PACKED_ICE.defaultBlockState();
            case 4 -> Blocks.NETHER_BRICK_FENCE.defaultBlockState();
            case 5 -> Blocks.DARK_OAK_FENCE.defaultBlockState();
            default -> Blocks.COBBLESTONE_WALL.defaultBlockState();
        };
        BlockState lamp = land == 3 || land == 1 ? Blocks.SOUL_LANTERN.defaultBlockState() : Blocks.LANTERN.defaultBlockState();
        for (int[] c : new int[][]{{7, 7}, {-7, 7}, {7, -7}, {-7, -7}}) {
            for (int k = 1; k <= 2; k++) out.put(o.offset(c[0], k, c[1]), post);
            out.put(o.offset(c[0], 3, c[1]), lamp.setValue(LanternBlock.HANGING, false));
        }
        int features = 0;
        for (int tries = 0; tries < 400 && features < 26; tries++) {
            int dx = rnd.nextInt(2 * RADIUS + 1) - RADIUS, dz = rnd.nextInt(2 * RADIUS + 1) - RADIUS;
            double r = Math.sqrt(dx * dx + dz * dz);
            if (!free(dx, dz) || r > RADIUS - 3 || !island.contains(BlockPos.asLong(dx, 0, dz))) continue;
            BlockPos base = o.offset(dx, surface(dx, dz, index) + 1, dz);
            features++;
            switch (land) {
                case 1 -> weightFeature(out, base, rnd);
                case 2 -> petalFeature(out, base, rnd);
                case 3 -> frostFeature(out, base, rnd);
                case 4 -> flameFeature(out, base, rnd);
                case 5 -> brushFeature(out, base, rnd);
                default -> mistFeature(out, base, rnd);
            }
        }
        // cobertura leve (pétalas/neve) no anel de fora
        if (land == 2 || land == 3) {
            for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    double r = Math.sqrt(dx * dx + dz * dz);
                    if (r < 7 || !island.contains(BlockPos.asLong(dx, 0, dz)) || Math.floorMod(hash(dx, dz, land * 5), 4) != 0) continue;
                    BlockPos at = o.offset(dx, surface(dx, dz, index) + 1, dz);
                    if (out.containsKey(at)) continue;
                    out.put(at, land == 2
                            ? Blocks.PINK_PETALS.defaultBlockState().setValue(PinkPetalsBlock.AMOUNT, 1 + Math.floorMod(hash(dx, dz, 3), 4))
                                    .setValue(PinkPetalsBlock.FACING, Direction.from2DDataValue(Math.floorMod(hash(dx, dz, 9), 4)))
                            : Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1 + Math.floorMod(hash(dx, dz, 4), 2)));
                }
            }
        }
        if (land == 0) giantSword(out, o.offset(-9, 1, -14));
    }

    private static void column(Map<BlockPos, BlockState> out, BlockPos base, int h, BlockState s) {
        for (int k = 0; k < h; k++) out.put(base.above(k), s);
    }

    /** Sem nome: a espada gigante cravada na névoa. */
    private static void giantSword(Map<BlockPos, BlockState> out, BlockPos b) {
        BlockState blade = Blocks.IRON_BLOCK.defaultBlockState(), edgeB = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
        for (int k = 0; k < 9; k++) {
            out.put(b.above(k), blade);
            out.put(b.above(k).east(), edgeB);
            out.put(b.above(k).west(), edgeB);
        }
        for (int dx = -3; dx <= 3; dx++) out.put(b.above(9).east(dx), Blocks.POLISHED_BLACKSTONE.defaultBlockState());
        column(out, b.above(10), 4, Blocks.DARK_OAK_LOG.defaultBlockState());
        out.put(b.above(14), Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
        out.put(b.above(9), Blocks.REDSTONE_BLOCK.defaultBlockState());
    }

    private static void mistFeature(Map<BlockPos, BlockState> out, BlockPos b, RandomSource rnd) {
        if (rnd.nextBoolean()) {
            // árvore morta
            int h = 3 + rnd.nextInt(3);
            column(out, b, h, Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState());
            Direction d = Direction.from2DDataValue(rnd.nextInt(4));
            out.put(b.above(h - 1).relative(d), Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState()
                    .setValue(RotatedPillarBlock.AXIS, d.getAxis()));
            out.put(b.above(h - 2).relative(d.getOpposite()), Blocks.DARK_OAK_FENCE.defaultBlockState());
        } else {
            column(out, b, 1 + rnd.nextInt(3), Blocks.MOSSY_COBBLESTONE.defaultBlockState());
        }
    }

    private static void weightFeature(Map<BlockPos, BlockState> out, BlockPos b, RandomSource rnd) {
        switch (rnd.nextInt(3)) {
            case 0 -> {
                // corrente gigante saindo do chão
                column(out, b, 4 + rnd.nextInt(5), Blocks.CHAIN.defaultBlockState());
                out.put(b, Blocks.IRON_BLOCK.defaultBlockState());
            }
            case 1 -> out.put(b, Blocks.ANVIL.defaultBlockState());
            default -> {
                // bloco de peso (cubo de ferro meio enterrado)
                for (int dx = 0; dx < 2; dx++) for (int dz = 0; dz < 2; dz++) column(out, b.offset(dx, 0, dz), 2, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
                out.put(b.offset(0, 2, 0), Blocks.HEAVY_CORE.defaultBlockState());
            }
        }
    }

    private static void petalFeature(Map<BlockPos, BlockState> out, BlockPos b, RandomSource rnd) {
        if (rnd.nextInt(3) > 0) {
            // cerejeira
            int h = 4 + rnd.nextInt(2);
            column(out, b, h, Blocks.CHERRY_LOG.defaultBlockState());
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        if (Math.abs(dx) + Math.abs(dz) + Math.abs(dy) > 3) continue;
                        BlockPos at = b.offset(dx, h + dy, dz);
                        if (!out.containsKey(at)) out.put(at, Blocks.CHERRY_LEAVES.defaultBlockState()
                                .setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true));
                    }
                }
            }
        } else {
            // portal (torii) pequeno
            BlockState red = Blocks.RED_CONCRETE.defaultBlockState();
            column(out, b.west(2), 4, red);
            column(out, b.east(2), 4, red);
            for (int dx = -3; dx <= 3; dx++) out.put(b.offset(dx, 4, 0), Blocks.DARK_OAK_PLANKS.defaultBlockState());
            for (int dx = -2; dx <= 2; dx++) out.put(b.offset(dx, 3, 0), red);
        }
    }

    private static void frostFeature(Map<BlockPos, BlockState> out, BlockPos b, RandomSource rnd) {
        int h = 3 + rnd.nextInt(6);
        BlockState ice = rnd.nextInt(3) == 0 ? Blocks.BLUE_ICE.defaultBlockState() : Blocks.PACKED_ICE.defaultBlockState();
        column(out, b, h, ice);
        if (h > 4) {
            for (Direction d : Direction.Plane.HORIZONTAL) column(out, b.relative(d), h / 2, Blocks.PACKED_ICE.defaultBlockState());
        }
    }

    private static void flameFeature(Map<BlockPos, BlockState> out, BlockPos b, RandomSource rnd) {
        switch (rnd.nextInt(3)) {
            case 0 -> {
                // pilar de chama eterna
                column(out, b, 3, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
                out.put(b.above(3), Blocks.NETHERRACK.defaultBlockState());
                out.put(b.above(4), Blocks.FIRE.defaultBlockState());
            }
            case 1 -> {
                // poça de lava cercada (afundada)
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) out.put(b.offset(dx, 0, dz), Blocks.BLACKSTONE_WALL.defaultBlockState());
                out.put(b.below(), Blocks.LAVA.defaultBlockState());
                out.put(b, Blocks.AIR.defaultBlockState());
                out.put(b.below(2), Blocks.BLACKSTONE.defaultBlockState());
            }
            default -> {
                column(out, b, 2, Blocks.BASALT.defaultBlockState());
                out.put(b.above(2), Blocks.SHROOMLIGHT.defaultBlockState());
            }
        }
    }

    private static void brushFeature(Map<BlockPos, BlockState> out, BlockPos b, RandomSource rnd) {
        if (rnd.nextBoolean()) {
            // pincel gigante fincado: cabo escuro, cerdas negras
            int h = 5 + rnd.nextInt(3);
            column(out, b, 2, Blocks.BLACK_WOOL.defaultBlockState());
            column(out, b.above(2), h, Blocks.DARK_OAK_LOG.defaultBlockState());
            out.put(b.above(h + 2), Blocks.GOLD_BLOCK.defaultBlockState());
        } else {
            // "letra" de tinta em pé
            column(out, b, 3, Blocks.BLACK_CONCRETE.defaultBlockState());
            out.put(b.above(2).east(), Blocks.BLACK_CONCRETE.defaultBlockState());
            out.put(b.above(1).west(), Blocks.BLACK_CONCRETE.defaultBlockState());
        }
    }

    // =====================================================================================
    //  Obras (construção aos poucos)
    // =====================================================================================
    private static final class Job {
        final ServerLevel level;
        final int index;
        final List<Map.Entry<BlockPos, BlockState>> place;
        final List<BlockPos> clear;
        final Runnable done;
        int i, j;

        Job(ServerLevel level, int index, List<Map.Entry<BlockPos, BlockState>> place, List<BlockPos> clear, Runnable done) {
            this.level = level;
            this.index = index;
            this.place = place;
            this.clear = clear;
            this.done = done;
        }
    }

    private static final Deque<Job> JOBS = new ArrayDeque<>();

    /** Agenda a troca de paisagem {@code from} (-1 = vazio) → {@code to}; {@code done} roda ao terminar. */
    public static void queue(ServerLevel level, int index, int from, int to, Runnable done) {
        Map<BlockPos, BlockState> next = plan(index, to);
        List<BlockPos> clear = new ArrayList<>();
        if (from >= 0 && from != to) {
            for (BlockPos p : plan(index, from).keySet()) if (!next.containsKey(p)) clear.add(p);
        }
        ChunkPos c = new ChunkPos(InnerWorld.origin(index));
        level.getChunkSource().addRegionTicket(TICKET, c, 3, c);
        JOBS.add(new Job(level, index, new ArrayList<>(next.entrySet()), clear, done));
    }

    public static boolean busy(int index) {
        for (Job j : JOBS) if (j.index == index) return true;
        return false;
    }

    static void tick(MinecraftServer server) {
        int budget = BUDGET;
        while (budget > 0 && !JOBS.isEmpty()) {
            Job job = JOBS.peekFirst();
            if (server.getLevel(job.level.dimension()) != job.level) {
                JOBS.pollFirst();
                continue;
            }
            ChunkPos c = new ChunkPos(InnerWorld.origin(job.index));
            job.level.getChunkSource().addRegionTicket(TICKET, c, 3, c);
            while (budget > 0 && job.i < job.place.size()) {
                Map.Entry<BlockPos, BlockState> e = job.place.get(job.i++);
                job.level.setBlock(e.getKey(), e.getValue(), FLAGS);
                budget--;
            }
            while (budget > 0 && job.i >= job.place.size() && job.j < job.clear.size()) {
                job.level.setBlock(job.clear.get(job.j++), Blocks.AIR.defaultBlockState(), FLAGS);
                budget--;
            }
            if (job.i >= job.place.size() && job.j >= job.clear.size()) {
                JOBS.pollFirst();
                try {
                    job.done.run();
                } catch (RuntimeException ex) {
                    IsekaiMahou.LOGGER.warn("class_shinigami: fim da obra do Mundo Interior falhou: {}", ex.toString());
                }
            }
        }
    }

    static void clear() {
        JOBS.clear();
    }

    private InnerWorldBuilder() {}
}
