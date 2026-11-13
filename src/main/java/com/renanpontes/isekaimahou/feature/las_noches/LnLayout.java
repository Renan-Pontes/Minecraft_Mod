package com.renanpontes.isekaimahou.feature.las_noches;

import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.hueco_mundo.HmRegistries;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;

/**
 * Planta de Las Noches, descrita por FUNÇÃO (bloco em cada posição), sem molde .nbt: o construtor ({@link LnBuilder})
 * percorre as colunas chunk por chunk e pergunta {@link #at}. Coordenadas RELATIVAS ao centro da cúpula C (x leste,
 * y = altura sobre o piso, z sul). C fica 150 blocos ao norte do centro do Portão (o antigo palácio), no mesmo nível do
 * piso dele.
 * <pre>
 *            N  Trono de Aizen (sala vazia, livro de lore)
 *   [1|3]                         [8|9]
 *  2 Barragan ── colunata ── TORRE ── colunata ── 4 Ulquiorra
 *   [5|7]      pátio de areia (Menos)       6 Grimmjow
 *            S  salão de entrada → corredor branco em L → Portão (palácio antigo)
 * </pre>
 * Cúpula: casca dupla (fora quartzo com nervuras, dentro o CÉU FALSO azul com nuvens e um sol), achatada no alto num
 * terraço de raio ~46: lá em cima, ao sul da torre, fica a arena da Segunda Etapa. A torre central atravessa o terraço.
 */
public final class LnLayout {
    /** Raio da cúpula, altura do terraço (topo achatado), topo da torre e da agulha. */
    public static final int R = 72, CAP = 54, TOWER_TOP = CAP + 40, SPIRE = 14;
    /** Deslocamento do centro da cúpula a partir do centro do palácio antigo. */
    public static final int OFFSET_Z = -150;
    static final double TOWER_R = 5.4, TOWER_IN = 3.5, CROWN_R = 6.6;
    /** Pátio de areia (anel entre a torre e a colunata) e a colunata circular. */
    static final double COURT_IN = 6.0, COURT_OUT = 33.5, RING_IN = 33.5, RING_OUT = 38.5, RING_MID = 36.0;
    /** Arena da Segunda Etapa no terraço (relativa a C) e o raio dela. */
    public static final Vec3 DOME_ARENA = new Vec3(0.5, CAP + 2, 18.5);
    public static final double DOME_ARENA_RADIUS = 24.0;
    /** Pés do jogador nos selos da torre (embaixo e no terraço) e onde cada selo leva. */
    public static final BlockPos PAD_LOW = new BlockPos(0, 1, 0), PAD_HIGH = new BlockPos(0, CAP + 2, 0);
    public static final BlockPos EXIT_LOW = new BlockPos(0, 1, 9), EXIT_HIGH = new BlockPos(0, CAP + 2, 9);
    /** Limites (relativos) de tudo que é construído: cúpula, corredores, obeliscos e a porta no palácio. */
    public static final int MIN_X = -86, MAX_X = 86, MIN_Z = -86, MAX_Z = 114, MIN_Y = -40, MAX_Y = TOWER_TOP + SPIRE + 1;

    record Box(int x0, int y0, int z0, int x1, int y1, int z1) {
        boolean in(int x, int y, int z) {
            return x >= x0 && x <= x1 && y >= y0 && y <= y1 && z >= z0 && z <= z1;
        }
    }

    /** Trecho de corredor coberto (caixa externa inclusiva; piso em y=0, teto em y=h). */
    record Seg(int x0, int z0, int x1, int z1, int h) {
        boolean outer(int x, int z) {
            return x >= x0 && x <= x1 && z >= z0 && z <= z1;
        }

        boolean inner(int x, int y, int z) {
            return x > x0 && x < x1 && z > z0 && z < z1 && y > 0 && y < h;
        }
    }

    enum Kind { AIZEN, BARRAGAN, ULQUIORRA, GRIMMJOW, WING }

    /**
     * Sala (caixa inclusiva, teto em y=h). Porta (aberta nas salas das Espada, selada nas alas) na parede {@code side},
     * centrada em {@code doorAt}; os números ficam acima da porta, legíveis de fora.
     */
    record Door(Direction side, int at, String number, boolean open) {}

    record Hall(Kind kind, int x0, int z0, int x1, int z1, int h, List<Door> doors) {
        boolean in(int x, int z) {
            return x >= x0 && x <= x1 && z >= z0 && z <= z1;
        }
    }

    /** Sala de uma Espada: id da entidade (módulo espada), sala, ponto de nascer e para onde olha. */
    public record Chamber(String espadaId, int number, int x0, int z0, int x1, int z1, int h, BlockPos spawn, float yaw, int color) {
        public BlockPos center() {
            return new BlockPos((x0 + x1) / 2, 1, (z0 + z1) / 2);
        }

        /** O jogador (posição relativa) está dentro da sala? */
        public boolean contains(double x, double y, double z) {
            return x > x0 && x < x1 + 1 && z > z0 && z < z1 + 1 && y >= 0 && y < h;
        }
    }

    public static final Chamber BARRAGAN = new Chamber("barragan", 2, -62, -11, -40, 11, 18, new BlockPos(-55, 1, 0), -90F, 0x9E7BD8);
    public static final Chamber ULQUIORRA = new Chamber("ulquiorra", 4, 40, -10, 60, 10, 18, new BlockPos(52, 1, 0), 90F, 0x5CE88A);
    public static final Chamber GRIMMJOW = new Chamber("grimmjow", 6, 30, 30, 48, 48, 18, new BlockPos(39, 1, 41), 180F, 0x4FC3F7);
    public static final List<Chamber> CHAMBERS = List.of(BARRAGAN, ULQUIORRA, GRIMMJOW);
    /** Sala do trono de Aizen (o livro de lore fica no púlpito). */
    public static final Hall AIZEN = new Hall(Kind.AIZEN, -12, -64, 12, -40, 20, List.of(new Door(Direction.SOUTH, 0, "", true)));
    public static final BlockPos LECTERN = new BlockPos(0, 1, -52);
    public static final BlockPos[] AIZEN_CHESTS = {new BlockPos(-4, 4, -62), new BlockPos(4, 4, -62)};
    public static final BlockPos BARRAGAN_CHEST = new BlockPos(-60, 3, 3);
    public static final BlockPos AIZEN_SIGN = new BlockPos(0, 1, -42);

    static final List<Hall> HALLS = List.of(
            AIZEN,
            new Hall(Kind.BARRAGAN, -62, -11, -40, 11, 18, List.of(new Door(Direction.EAST, 0, "2", true))),
            new Hall(Kind.ULQUIORRA, 40, -10, 60, 10, 18, List.of(new Door(Direction.WEST, 0, "4", true))),
            new Hall(Kind.GRIMMJOW, 30, 30, 48, 48, 18, List.of(new Door(Direction.NORTH, 39, "6", true))),
            new Hall(Kind.WING, -48, -48, -32, -32, 13, List.of(new Door(Direction.SOUTH, -40, "1", false), new Door(Direction.EAST, -40, "3", false))),
            new Hall(Kind.WING, -48, 32, -32, 48, 13, List.of(new Door(Direction.NORTH, -40, "5", false), new Door(Direction.EAST, 40, "7", false))),
            new Hall(Kind.WING, 32, -48, 48, -32, 13, List.of(new Door(Direction.SOUTH, 40, "8", false), new Door(Direction.WEST, -40, "9", false))));

    /** Corredor branco: salão de entrada (dentro da cúpula), reta para o sul e o "L" até o portão do palácio antigo. */
    static final List<Seg> SEGS = List.of(
            new Seg(-7, 39, 7, 66, 14),
            new Seg(-4, 65, 4, 100, 10),
            new Seg(-24, 92, 4, 100, 10),
            new Seg(-24, 92, -16, 111, 10));

    /** Aberturas (viram ar): porta no palácio antigo, saída lateral para o deserto, portas das salas e da torre. */
    static final List<Box> CUTS = List.of(
            new Box(-22, 1, 110, -18, 6, 113),
            new Box(-14, 1, 100, -10, 5, 100),
            new Box(-5, 1, 39, 5, 10, 39),
            new Box(-3, 1, -40, 3, 8, -40),
            new Box(-40, 1, -2, -40, 6, 2),
            new Box(40, 1, -2, 40, 6, 2),
            new Box(37, 1, 30, 41, 6, 30),
            new Box(-1, 1, 3, 1, 4, 6),
            new Box(-1, CAP + 2, 3, 1, CAP + 5, 6));

    /** Obeliscos de quartzo em volta da cúpula. */
    static final int[][] OBELISKS = obelisks();

    // Sol do céu falso (ao norte, 40° acima do horizonte): direção unitária.
    private static final double SUN_X = 0, SUN_Y = Math.sin(Math.toRadians(40)), SUN_Z = -Math.cos(Math.toRadians(40));
    private static final double SUN_CORE = Math.cos(Math.toRadians(4.5)), SUN_RIM = Math.cos(Math.toRadians(6.0)),
            SUN_HALO = Math.cos(Math.toRadians(8.0));

    // Paleta.
    private static final BlockState AIR = Blocks.AIR.defaultBlockState(), LIGHT = Blocks.LIGHT.defaultBlockState();
    private static final BlockState SQ = Blocks.SMOOTH_QUARTZ.defaultBlockState(), QB = Blocks.QUARTZ_BRICKS.defaultBlockState(),
            QBLOCK = Blocks.QUARTZ_BLOCK.defaultBlockState(), QP = Blocks.QUARTZ_PILLAR.defaultBlockState(),
            CQ = Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState(), WHITE = Blocks.WHITE_CONCRETE.defaultBlockState(),
            BLACK = Blocks.BLACK_CONCRETE.defaultBlockState(), SKY = Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState(),
            SUN = Blocks.OCHRE_FROGLIGHT.defaultBlockState(), SUN_RIM_B = Blocks.YELLOW_CONCRETE.defaultBlockState(),
            GLASS = Blocks.WHITE_STAINED_GLASS.defaultBlockState(), DARK_GLASS = Blocks.BLACK_STAINED_GLASS.defaultBlockState(),
            PAD = Blocks.BLACK_GLAZED_TERRACOTTA.defaultBlockState(), GOLD = Blocks.GOLD_BLOCK.defaultBlockState(),
            BONE = Blocks.BONE_BLOCK.defaultBlockState(), DARK = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(),
            CARPET = Blocks.BLACK_CARPET.defaultBlockState(), CYAN = Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState(),
            CRACK = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(), SEA = Blocks.SEA_LANTERN.defaultBlockState(),
            SLAB = Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);

    private static BlockState sand() {
        return HmRegistries.HOLLOW_SAND.get().defaultBlockState();
    }

    private static BlockState sandBricks() {
        return HmRegistries.HOLLOW_SANDSTONE_BRICKS.get().defaultBlockState();
    }

    private static BlockState crystal() {
        return HmRegistries.HOLLOW_CRYSTAL.get().defaultBlockState();
    }

    static BlockState foundation() {
        return HmRegistries.HOLLOW_SANDSTONE.get().defaultBlockState();
    }

    private static BlockState stairs(Direction facing) {
        return Blocks.QUARTZ_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing);
    }

    static BlockState lectern() {
        return Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, Direction.SOUTH);
    }

    // =====================================================================================
    //  Colunas
    // =====================================================================================

    /** A coluna (x, z) tem alguma coisa de Las Noches? */
    static boolean usesColumn(int x, int z) {
        return maxY(x, z) >= 0;
    }

    /** Altura máxima (relativa) a examinar na coluna, ou -1 se a coluna não é usada. */
    static int maxY(int x, int z) {
        int top = -1;
        double r = Math.sqrt(x * x + z * z);
        if (r <= CROWN_R + 0.5) top = TOWER_TOP + SPIRE;
        else if (r <= R + 0.5) top = Math.min(CAP + 2, (int) Math.floor(Math.sqrt(Math.max(0, R * R - r * r))));
        for (Seg s : SEGS) if (s.outer(x, z)) top = Math.max(top, s.h);
        for (Box c : CUTS) if (x >= c.x0 && x <= c.x1 && z >= c.z0 && z <= c.z1) top = Math.max(top, c.y1);
        for (int[] o : OBELISKS) if (Math.abs(x - o[0]) <= 1 && Math.abs(z - o[1]) <= 1) top = Math.max(top, 26);
        return top;
    }

    /** A coluna leva fundação (base de arenito até o chão) embaixo do piso? */
    static boolean needsFoundation(int x, int z) {
        double r = Math.sqrt(x * x + z * z);
        if (r <= R + 0.5) return true;
        for (Seg s : SEGS) if (s.outer(x, z)) return true;
        for (int[] o : OBELISKS) if (Math.abs(x - o[0]) <= 1 && Math.abs(z - o[1]) <= 1) return true;
        return false;
    }

    private static int[][] obelisks() {
        int[][] o = new int[8][];
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(22.5 + 45 * i);
            o[i] = new int[] {(int) Math.round(Math.cos(a) * 82), (int) Math.round(Math.sin(a) * 82)};
        }
        return o;
    }

    // =====================================================================================
    //  Bloco em cada posição (null = não mexe)
    // =====================================================================================

    @Nullable
    static BlockState at(int x, int y, int z) {
        if (y >= 1) {
            for (Box c : CUTS) if (c.in(x, y, z)) return AIR;
        }
        BlockState s = corridor(x, y, z);
        if (s != null) return s;
        double r = Math.sqrt(x * x + z * z);
        if (r > R + 0.5) return obelisk(x, y, z);
        if (y < 0) return null;
        if (r <= TOWER_R || (r <= CROWN_R && y >= TOWER_TOP - 3)) {
            s = tower(x, y, z, r);
            if (s != null || y > CAP + 1) return s;
        }
        double d = Math.sqrt(r * r + y * y);
        if (y == CAP + 2) return d <= R && x % 9 == 0 && z % 9 == 0 && r > CROWN_R ? LIGHT : null;
        if (d > R || y > CAP + 1) return null;
        if (y >= CAP) return cap(x, y, z, r, d);
        if (d > R - 2) return shell(x, y, z, r, d);
        Hall h = hallAt(x, z);
        if (h != null && y <= h.h) return hall(h, x, y, z);
        if (y == 0) return floor(x, z, r);
        s = ring(x, y, z, r);
        if (s != null) return s;
        if (r > COURT_IN && r < COURT_OUT) {
            s = courtyard(x, y, z);
            if (s != null) return s;
        }
        return interior(x, y, z, d);
    }

    @Nullable
    private static Hall hallAt(int x, int z) {
        for (Hall h : HALLS) if (h.in(x, z)) return h;
        return null;
    }

    // ------------------------------------------------------------------ Corredores

    @Nullable
    private static BlockState corridor(int x, int y, int z) {
        if (y < 0) return null;
        int top = -1;
        boolean roof = false;
        for (Seg s : SEGS) {
            if (!s.outer(x, z) || y > s.h) continue;
            top = Math.max(top, s.h);
            if (y == s.h) roof = true;
        }
        if (top < 0) return null;
        for (Seg s : SEGS) {
            if (s.inner(x, y, z)) {
                return y == 6 && Math.floorMod(x, 7) == 0 && Math.floorMod(z, 7) == 0 ? LIGHT : AIR;
            }
        }
        if (y == 0) return (Math.floorMod(x, 4) == 0 || Math.floorMod(z, 4) == 0) ? QB : SQ;
        if (roof) return Math.floorMod(x + z, 6) == 0 ? QB : SQ;
        // Parede: pilastras a cada 6, rodapé, janelas altas de vidro branco.
        if (Math.floorMod(x + z, 6) == 0) return QP;
        if (y == 1) return QB;
        if (y >= 4 && y <= 6 && Math.floorMod(x + z, 6) == 3) return GLASS;
        return SQ;
    }

    // ------------------------------------------------------------------ Obeliscos

    @Nullable
    private static BlockState obelisk(int x, int y, int z) {
        for (int[] o : OBELISKS) {
            int dx = x - o[0], dz = z - o[1];
            if (Math.abs(dx) > 1 || Math.abs(dz) > 1) continue;
            if (y < 0) return null;
            if (y <= 2) return sandBricks();
            if (y == 3) return CQ;
            if (dx != 0 || dz != 0) return null;
            if (y <= 24) return QP;
            if (y == 25) return CQ;
            if (y == 26) return crystal();
            return null;
        }
        return null;
    }

    // ------------------------------------------------------------------ Torre

    @Nullable
    private static BlockState tower(int x, int y, int z, double r) {
        if (y > TOWER_TOP) {
            double t = (y - TOWER_TOP) / (double) SPIRE;
            if (y == TOWER_TOP + SPIRE) return r < 0.5 ? SEA : null;
            return r <= 3.2 * (1 - t) + 0.4 ? (y % 4 == 0 ? CQ : QBLOCK) : null;
        }
        if (y >= TOWER_TOP - 3) {
            // Coroa: aba larga logo abaixo do topo.
            if (r > TOWER_R) return y == TOWER_TOP ? QB : y == TOWER_TOP - 1 ? CQ : y == TOWER_TOP - 2 && r <= 6.0 ? SQ : null;
            if (y == TOWER_TOP) return SQ;
        }
        if (r > TOWER_R) return null;
        boolean inside = r <= TOWER_IN;
        if (inside) {
            if (y == 0 || y == CAP + 1) return Math.abs(x) <= 1 && Math.abs(z) <= 1 ? PAD : SQ;
            if ((y == 4 || y == CAP + 5) && x == 2 && z == -2) return LIGHT;
            return AIR;
        }
        if (y <= 2) return CQ;
        if (y % 16 == 0) return QB;
        // Janelas negras em fenda acima do terraço (nos quatro pontos cardeais).
        if (y > CAP + 3 && (x == 0 || z == 0) && Math.floorMod(y, 8) >= 3 && Math.floorMod(y, 8) <= 5) return DARK_GLASS;
        if ((x == 0 || z == 0) && y < CAP) return DARK;
        return QP;
    }

    // ------------------------------------------------------------------ Cúpula

    /** Topo achatado: embaixo o zênite do céu falso, em cima o terraço (com o anel da arena da Segunda Etapa). */
    private static BlockState cap(int x, int y, int z, double r, double d) {
        if (y == CAP) return d > R - 1 ? SQ : sky(x, y, z, d);
        double ax = x - (DOME_ARENA.x - 0.5), az = z - (DOME_ARENA.z - 0.5);
        double ar = Math.sqrt(ax * ax + az * az);
        if (Math.abs(ar - 22) < 0.55) return BLACK;
        if (ar < 1.5) return CQ;
        if (d > R - 1) return QB;
        return Math.floorMod((int) Math.round(r), 6) == 0 ? QB : SQ;
    }

    /** Casca: por fora quartzo com nervuras e faixas; por dentro o céu. */
    private static BlockState shell(int x, int y, int z, double r, double d) {
        if (d <= R - 1) return sky(x, y, z, d);
        if (y % 12 == 0) return QB;
        double ang = Math.atan2(z, x);
        double step = Math.toRadians(15);
        double off = Math.abs(ang - Math.round(ang / step) * step) * r;
        return off < 0.8 ? QB : SQ;
    }

    /** Céu falso: azul com nuvens brancas e o sol de froglight. Névoa clara rente ao "horizonte". */
    private static BlockState sky(int x, int y, int z, double d) {
        double dot = (x * SUN_X + y * SUN_Y + z * SUN_Z) / Math.max(1, d);
        if (dot > SUN_CORE) return SUN;
        if (dot > SUN_RIM) return SUN_RIM_B;
        if (dot > SUN_HALO) return WHITE;
        if (y < 4) return WHITE;
        double n = noise(x * 0.075 + y * 0.04, z * 0.075 - y * 0.03);
        return n > 0.66 ? WHITE : SKY;
    }

    // ------------------------------------------------------------------ Piso, pátio e colunata

    private static BlockState floor(int x, int z, double r) {
        if (r < COURT_OUT) {
            if (r >= COURT_OUT - 1) return QB;
            if (onPath(x, z)) return Math.abs(x) == 0 || Math.abs(z) == 0 || x == z ? BLACK : QB;
            return sand();
        }
        if (onPath(x, z)) return QB;
        if (Math.floorMod((int) Math.round(r), 8) == 0) return QB;
        double ang = Math.atan2(z, x), step = Math.toRadians(15);
        return Math.abs(ang - Math.round(ang / step) * step) * r < 0.5 ? QB : SQ;
    }

    /** Caminhos de quartzo da torre até cada porta (sem dunas por cima). */
    private static boolean onPath(int x, int z) {
        if (Math.abs(x) <= 2 || Math.abs(z) <= 2) return true;
        return x > 0 && z > 0 && Math.abs(x - z) <= 2;
    }

    @Nullable
    private static BlockState ring(int x, int y, int z, double r) {
        if (r < RING_IN || r > RING_OUT || y > 11) return null;
        if (y == 10) return SQ;
        if (y == 11) return r < RING_IN + 1 || r > RING_OUT - 1 ? SLAB : AIR;
        double ang = Math.atan2(z, x), step = Math.toRadians(10), off = Math.toRadians(5);
        double arc = Math.abs((ang - off) - Math.round((ang - off) / step) * step) * r;
        if (Math.abs(r - RING_MID) <= 0.75 && arc <= 0.75) return y == 1 || y == 9 ? CQ : QP;
        if (y == 9 && Math.abs(r - RING_MID) <= 0.75 && Math.abs(ang - Math.round(ang / step) * step) * r <= 0.75) return LIGHT;
        return AIR;
    }

    @Nullable
    private static BlockState courtyard(int x, int y, int z) {
        // Árvores de quartzo mortas (seis, em volta da torre).
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(15 + 60 * i);
            int tx = (int) Math.round(Math.cos(a) * 21), tz = (int) Math.round(Math.sin(a) * 21);
            int dx = x - tx, dz = z - tz;
            if (Math.abs(dx) > 2 || Math.abs(dz) > 2) continue;
            boolean axis = dx == 0 || dz == 0;
            int m = Math.abs(dx) + Math.abs(dz);
            if (m == 0) return y <= 8 ? QP : y == 9 ? crystal() : AIR;
            if (axis && m == 1 && y == 6) return SQ;
            if (axis && m == 2 && y == 7) return SQ;
            if (axis && m == 2 && y == 8) return crystal();
        }
        if (onPath(x, z)) return y == 5 && x % 10 == 0 && z % 10 == 0 ? LIGHT : AIR;
        int dune = (int) Math.floor(noise(x * 0.11, z * 0.11) * 3.2 - 0.4);
        if (y <= dune) return sand();
        return null;
    }

    private static BlockState interior(int x, int y, int z, double d) {
        if (y == 5 && x % 10 == 0 && z % 10 == 0) return LIGHT;
        if (y == 25 && x % 14 == 0 && z % 14 == 0) return LIGHT;
        if (y == CAP - 1 && x % 8 == 0 && z % 8 == 0) return LIGHT;
        if (d > R - 3 && Math.floorMod(hash(x, y, z), 24) == 0) return LIGHT;
        return AIR;
    }

    // ------------------------------------------------------------------ Salas

    private static BlockState hall(Hall h, int x, int y, int z) {
        boolean edge = x == h.x0 || x == h.x1 || z == h.z0 || z == h.z1;
        if (y == h.h) return edge ? QB : SQ;
        if (edge) return wall(h, x, y, z);
        if (h.kind == Kind.WING) return y == 0 ? SQ : QBLOCK;
        BlockState s = switch (h.kind) {
            case AIZEN -> aizen(x, y, z);
            case BARRAGAN -> barragan(x, y, z);
            case ULQUIORRA -> ulquiorra(x, y, z);
            case GRIMMJOW -> grimmjow(x, y, z);
            default -> null;
        };
        if (s != null) return s;
        if (y == 0) return Math.floorMod(x + z, 2) == 0 ? SQ : QB;
        int step = h.kind == Kind.ULQUIORRA ? 12 : 6;
        if (y == h.h - 4 && Math.floorMod(x, step) == 0 && Math.floorMod(z, step) == 0) return LIGHT;
        return AIR;
    }

    private static BlockState wall(Hall h, int x, int y, int z) {
        for (Door door : h.doors) {
            BlockState s = doorWall(h, door, x, y, z);
            if (s != null) return s;
        }
        if (h.kind == Kind.ULQUIORRA && x == h.x1) {
            // Janela em lua crescente na parede leste (o céu falso aparece por ela).
            double a = Math.hypot(z, y - 10), b = Math.hypot(z - 1.4, y - 10.9);
            if (a <= 4.2 && b > 3.3) return AIR;
        }
        if (h.kind == Kind.GRIMMJOW && x == h.x1) {
            // Buraco aberto na parede (a fúria do Sexto).
            double e = Math.pow((z - 40) / 4.5, 2) + Math.pow((y - 7) / 3.5, 2);
            if (e + (Math.floorMod(hash(x, y, z), 5) * 0.08) < 1.0) return AIR;
        }
        if (h.kind == Kind.GRIMMJOW && y == 3) return CYAN;
        int along = (x == h.x0 || x == h.x1) ? z : x;
        if (Math.floorMod(along, 4) == 0) return QP;
        if (y == 1) return QB;
        if (y == h.h - 1) return CQ;
        return SQ;
    }

    /** Parede da porta: moldura negra, folha selada (alas) e o número acima. Null = não é parte da porta. */
    @Nullable
    private static BlockState doorWall(Hall h, Door door, int x, int y, int z) {
        int along;
        int rightSign;
        switch (door.side) {
            case EAST -> { if (x != h.x1) return null; along = z; rightSign = -1; }
            case WEST -> { if (x != h.x0) return null; along = z; rightSign = 1; }
            case NORTH -> { if (z != h.z0) return null; along = x; rightSign = -1; }
            default -> { if (z != h.z1) return null; along = x; rightSign = 1; }
        }
        int half = door.open ? (h.kind == Kind.AIZEN ? 3 : 2) : 2;
        int height = h.kind == Kind.AIZEN ? 8 : 6;
        int u = along - door.at;
        if (Math.abs(u) <= half + 1 && y <= height + 1) {
            if (Math.abs(u) == half + 1 || y == height + 1) return BLACK;
            if (!door.open) return Math.abs(u) == 0 && y >= 3 && y <= 5 ? Blocks.GRAY_CONCRETE.defaultBlockState() : WHITE;
            return null;
        }
        if (door.number.isEmpty()) return null;
        int scale = h.kind == Kind.WING ? 1 : 2;
        int top = h.h - 1;
        int width = door.number.length() * 4 * scale - scale;
        int col = u * rightSign + width / 2;
        int row = (top - y) / scale;
        if (top - y < 0 || row > 4 || col < 0 || col >= width) return null;
        // Placa branca lisa atrás dos números (sem pilastras cortando os dígitos).
        int ch = col / (4 * scale), cx = (col % (4 * scale)) / scale;
        if (cx >= 3) return WHITE;
        return digitPixel(door.number.charAt(ch), cx, row) ? BLACK : WHITE;
    }

    private static final String[] FONT = {
            "111101101101111", "010110010010111", "111001111100111", "111001111001111", "101101111001001",
            "111100111001111", "111100111101111", "111001010010010", "111101111101111", "111101111001111"};

    private static boolean digitPixel(char c, int col, int row) {
        int d = c - '0';
        if (d < 0 || d > 9) return false;
        return FONT[d].charAt(row * 3 + col) == '1';
    }

    /** Sala do trono de Aizen: estrado em três degraus, o trono vazio, colunas duplas e o tapete negro até o púlpito. */
    @Nullable
    private static BlockState aizen(int x, int y, int z) {
        int ax = Math.abs(x);
        // Trono (assento, encosto alto com asas, braços).
        if (x == 0 && y == 4 && z == -61) return stairs(Direction.NORTH);
        if (ax == 1 && y == 4 && z == -61) return SLAB;
        if (ax <= 1 && z == -62 && y >= 4 && y <= 12) return y >= 11 ? CQ : x == 0 && y >= 6 && y <= 10 ? BLACK : WHITE;
        if (ax == 2 && z == -62 && y >= 5 && y <= 9) return WHITE;
        // Estrado.
        if (y == 1 && ax <= 6 && z <= -55 && z >= -63) return z == -55 ? stairs(Direction.NORTH) : QBLOCK;
        if (y == 2 && ax <= 5 && z <= -57 && z >= -63) return z == -57 ? stairs(Direction.NORTH) : QBLOCK;
        if (y == 3 && ax <= 4 && z <= -59 && z >= -63) return z == -59 ? stairs(Direction.NORTH) : QBLOCK;
        // Colunas duplas.
        if ((ax == 8 || ax == 9) && (z == -44 || z == -45 || z == -49 || z == -50 || z == -54 || z == -55 || z == -59 || z == -60)
                && y >= 1 && y <= 19) {
            return y == 1 || y == 19 ? CQ : QP;
        }
        if (y == 0) return ax == 3 ? BLACK : Math.floorMod(x + z, 2) == 0 ? SQ : QB;
        if (y == 1 && ax <= 1 && z >= -54 && z <= -41 && !(x == LECTERN.getX() && z == LECTERN.getZ())) return CARPET;
        return null;
    }

    /** Sala 2: o trono de ossos e ouro do Rei de Hueco Mundo, colunas quebradas e areia que se acumula. */
    @Nullable
    private static BlockState barragan(int x, int y, int z) {
        int az = Math.abs(z);
        if (x <= -60 && x >= -61 && az <= 2 && y >= 3 && y <= 10) return BONE;
        if (x == -60 && y == 11 && az <= 2) return az % 2 == 0 ? GOLD : BONE;
        if (x == -59 && y == 3 && z == 0) return stairs(Direction.WEST);
        if (x == -59 && y == 3 && az == 1) return GOLD;
        if (y == 1 && x <= -56 && az <= 4) return x == -56 ? stairs(Direction.WEST) : DARK;
        if (y == 2 && x <= -58 && az <= 3) return x == -58 ? stairs(Direction.WEST) : DARK;
        for (int px : new int[] {-56, -46}) {
            for (int pz : new int[] {-7, 7}) {
                if (x == px && z == pz && y >= 1) {
                    int top = (px == -56 && pz == 7) ? 9 : (px == -46 && pz == -7) ? 12 : 17;
                    return y <= top ? (y == top ? CQ : sandBricks()) : null;
                }
            }
        }
        if (y == 0) return Math.floorMod(x, 4) == 0 ? DARK : sandBricks();
        if (y == 1 && x > -55 && (az >= 8 || x >= -43) && Math.floorMod(hash(x, 0, z), 7) < 2) return sand();
        return null;
    }

    /** Sala 4: quase vazia. Piso branco com uma lua crescente negra, um banco e quatro colunas finas. */
    @Nullable
    private static BlockState ulquiorra(int x, int y, int z) {
        if (x == 58 && y == 1 && Math.abs(z) <= 1) return stairs(Direction.EAST);
        if ((Math.abs(x - 44) == 0 || Math.abs(x - 56) == 0) && Math.abs(z) == 6 && y >= 1) return y == 1 || y == 17 ? CQ : QP;
        if (y == 0) {
            double a = Math.hypot(x - 50, z), b = Math.hypot(x - 51.6, z + 1.3);
            if (a <= 6 && b > 4.8) return BLACK;
            return SQ;
        }
        return null;
    }

    /** Sala 6: rachaduras, marcas de garra, entulho e o buraco na parede. */
    @Nullable
    private static BlockState grimmjow(int x, int y, int z) {
        if (z == 47 && y >= 4 && y <= 13) {
            for (int k = 0; k < 3; k++) if (x == 33 + (y - 4) / 2 + k * 2) return BLACK;
        }
        if (y == 0) {
            int c = Math.floorMod(x * 3 + z * 5 + hash(x, 0, z) % 3, 11);
            return c == 0 ? CRACK : SQ;
        }
        if (y == 1 && x >= 44 && z >= 36 && z <= 44 && Math.floorMod(hash(x, 1, z), 4) == 0) return QB;
        return null;
    }

    // ------------------------------------------------------------------ Ruído determinístico

    private static int hash(int x, int y, int z) {
        int h = x * 73856093 ^ y * 19349663 ^ z * 83492791;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        return h ^ (h >>> 15);
    }

    private static double rnd(int x, int z) {
        return (hash(x, 7, z) & 0xFFFF) / 65535.0;
    }

    /** Ruído de valor 2D suave (0..1). */
    static double noise(double x, double z) {
        int x0 = (int) Math.floor(x), z0 = (int) Math.floor(z);
        double fx = x - x0, fz = z - z0;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        double a = rnd(x0, z0), b = rnd(x0 + 1, z0), c = rnd(x0, z0 + 1), d = rnd(x0 + 1, z0 + 1);
        return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fz;
    }

    private LnLayout() {}
}
