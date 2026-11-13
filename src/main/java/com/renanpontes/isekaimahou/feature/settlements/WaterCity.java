package com.renanpontes.isekaimahou.feature.settlements;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.settlements.build.CityLayout;
import com.renanpontes.isekaimahou.feature.settlements.build.Project;
import com.renanpontes.isekaimahou.feature.settlements.build.TierLook;
import com.renanpontes.isekaimahou.feature.ui_hud.Hints;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Onda 8 (water_city) — <b>Cidade das Águas</b>: a cidade do jogador fundada EM CIMA DA ÁGUA (mar, lago grande, rio
 * largo). É o terceiro TIPO DE TERRENO ({@code ground} / {@code skyland} / {@link #WATER}) e combina com qualquer estilo.
 * <ul>
 *   <li><b>Altura</b>: o Coração fica 2 blocos acima da água; o piso da cidade inteira ({@link #deckY}) é o bloco logo
 *       acima dela. Como na Ilha Celeste, a cidade é plana: todo lote e toda muralha ficam nessa altura.</li>
 *   <li><b>Chão</b>: não existe "terra" a gerar. Cada lote ganha o SEU deque sobre estacas como parte da obra do prédio
 *       ({@link #deckColumn}, chamado pela fundação do {@code ConstructionEngine}); lote sem obra continua sendo água —
 *       é por ali, e pelo canal em volta de cada muralha, que se anda de barco. As passarelas que ligam os lotes, as
 *       pontes e o cais ficam em {@link WaterWorks}.</li>
 *   <li><b>Nunca aterra o mar</b>: coluna molhada só recebe o bloco do piso e, de 4 em 4 blocos, uma estaca até o fundo
 *       (no máximo {@link #PILE_MAX} blocos: em água funda ela termina numa pedra de fundação). Coluna de terra firme
 *       (margem) segue as regras de sempre do terreno.</li>
 * </ul>
 * Cidade no chão ou no céu ({@code !s.water}) nunca entra em nada daqui.
 */
public final class WaterCity {
    /** Id do tipo de terreno (vai no pacote {@code Found} e no NBT da cidade: nunca renomear). */
    public static final String WATER = "water";
    /** Parte mínima (%) do território inicial que precisa ser água com pelo menos 2 blocos de fundo. */
    /** Onda 9 (fundar mais fácil): era 60 — um lago grande, uma baía ou um rio largo já servem. */
    public static final int MIN_WET = 35;
    /** Comprimento máximo de uma estaca. */
    public static final int PILE_MAX = 10;
    /** Meia-largura do deque que nasce junto com o Coração (o fundador e os primeiros construtores precisam de chão). */
    private static final int CORE_HALF = 4;

    // =====================================================================================
    //  Consultas
    // =====================================================================================
    public static boolean isWater(@Nullable Settlement s) {
        return s != null && s.water;
    }

    /** Altura do piso da cidade (o bloco em que se pisa; os pés ficam em {@code deckY + 1}). */
    public static int deckY(Settlement s) {
        return s.heart.getY() - 1;
    }

    /** Altura do bloco de água de cima (o piso fica logo acima dele). */
    public static int seaY(Settlement s) {
        return s.heart.getY() - 2;
    }

    private static boolean water(ServerLevel sl, BlockPos pos) {
        return sl.getFluidState(pos).is(FluidTags.WATER);
    }

    /** A coluna (x, z) é água na altura do mar da cidade? (só com o chunk carregado) */
    public static boolean wet(ServerLevel sl, Settlement s, int x, int z) {
        return s.water && sl.hasChunk(x >> 4, z >> 4) && water(sl, new BlockPos(x, seaY(s), z));
    }

    /** Coluna de um lote cujo piso ({@code baseY}) fica acima da água: ganha deque sobre estacas em vez de aterro. */
    public static boolean wet(ServerLevel sl, Settlement s, int x, int z, int baseY) {
        return baseY >= deckY(s) && wet(sl, s, x, z);
    }

    /** Altura pedida no painel para um lote: na água nada fica abaixo do piso (cavar ali abriria um buraco no mar). */
    public static int offset(Settlement s, int off) {
        return s.water ? Math.max(0, off) : off;
    }

    /** Lado da frente da praça: é para lá que saem a passarela principal, a ponte e o cais dos barcos. */
    public static Direction front(Settlement s) {
        return s.plazaRotation().rotate(Direction.SOUTH);
    }

    /** Traço "Porto": comida que a pesca dá por dia (modesta; cresce com o degrau). */
    public static int fishing(Settlement s) {
        return s.water ? 2 + 2 * Math.max(1, s.level) : 0;
    }

    // =====================================================================================
    //  Paleta por estilo
    // =====================================================================================
    /** Madeiras/pedras do estilo: deque dos lotes, passarelas dos dois primeiros degraus, estaca, guarda-corpo, luz, ponte e barco. */
    public record Pal(BlockState deck, BlockState[] walk1, BlockState[] walk2, BlockState pile, BlockState post, BlockState lamp,
                      Block stairs, Boat.Type boat) {}

    private static final Map<SettlementStyle, Pal> PALS = new EnumMap<>(SettlementStyle.class);
    private static final Set<Block> FLOORS = new HashSet<>();

    private static void pal(SettlementStyle style, Block deck, BlockState[] walk1, BlockState[] walk2, Block pile, Block post, Block lamp,
                            Block stairs, Boat.Type boat) {
        PALS.put(style, new Pal(deck.defaultBlockState(), walk1, walk2, pile.defaultBlockState(), post.defaultBlockState(),
                lamp.defaultBlockState(), stairs, boat));
        FLOORS.add(deck);
        for (BlockState st : walk1) FLOORS.add(st.getBlock());
        for (BlockState st : walk2) FLOORS.add(st.getBlock());
    }

    static {
        pal(SettlementStyle.KINGDOM, Blocks.SPRUCE_PLANKS, TierLook.mix(Blocks.OAK_PLANKS, 3, Blocks.SPRUCE_PLANKS, 1),
                TierLook.mix(Blocks.SPRUCE_PLANKS, 3, Blocks.STRIPPED_SPRUCE_WOOD, 1), Blocks.SPRUCE_LOG, Blocks.SPRUCE_FENCE, Blocks.LANTERN,
                Blocks.SPRUCE_STAIRS, Boat.Type.SPRUCE);
        pal(SettlementStyle.DESERT, Blocks.SMOOTH_SANDSTONE, TierLook.mix(Blocks.ACACIA_PLANKS, 3, Blocks.CUT_SANDSTONE, 1),
                TierLook.mix(Blocks.CUT_SANDSTONE, 2, Blocks.SMOOTH_SANDSTONE, 1), Blocks.STRIPPED_ACACIA_LOG, Blocks.ACACIA_FENCE,
                Blocks.LANTERN, Blocks.SANDSTONE_STAIRS, Boat.Type.ACACIA);
        pal(SettlementStyle.NORDIC, Blocks.SPRUCE_PLANKS, TierLook.mix(Blocks.SPRUCE_PLANKS, 3, Blocks.STRIPPED_SPRUCE_WOOD, 1),
                TierLook.mix(Blocks.DARK_OAK_PLANKS, 2, Blocks.SPRUCE_PLANKS, 1), Blocks.SPRUCE_LOG, Blocks.SPRUCE_FENCE, Blocks.LANTERN,
                Blocks.SPRUCE_STAIRS, Boat.Type.SPRUCE);
        pal(SettlementStyle.ORIENTAL, Blocks.BAMBOO_PLANKS, TierLook.mix(Blocks.BAMBOO_PLANKS, 3, Blocks.BAMBOO_MOSAIC, 1),
                TierLook.mix(Blocks.BAMBOO_MOSAIC, 2, Blocks.CHERRY_PLANKS, 1), Blocks.STRIPPED_BAMBOO_BLOCK, Blocks.BAMBOO_FENCE,
                Blocks.LANTERN, Blocks.BAMBOO_STAIRS, Boat.Type.CHERRY);
        pal(SettlementStyle.ELVEN, Blocks.BIRCH_PLANKS, TierLook.mix(Blocks.BIRCH_PLANKS, 3, Blocks.STRIPPED_BIRCH_WOOD, 1),
                TierLook.mix(Blocks.STRIPPED_BIRCH_WOOD, 1, Blocks.BIRCH_PLANKS, 1, Blocks.MOSSY_STONE_BRICKS, 1), Blocks.BIRCH_LOG,
                Blocks.BIRCH_FENCE, Blocks.LANTERN, Blocks.BIRCH_STAIRS, Boat.Type.BIRCH);
        pal(SettlementStyle.GOTHIC, Blocks.DARK_OAK_PLANKS, TierLook.mix(Blocks.DARK_OAK_PLANKS, 3, Blocks.STRIPPED_DARK_OAK_WOOD, 1),
                TierLook.mix(Blocks.DARK_OAK_PLANKS, 2, Blocks.DEEPSLATE_TILES, 1), Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_FENCE,
                Blocks.SOUL_LANTERN, Blocks.DARK_OAK_STAIRS, Boat.Type.DARK_OAK);
        pal(SettlementStyle.DWARVEN, Blocks.SPRUCE_PLANKS, TierLook.mix(Blocks.SPRUCE_PLANKS, 3, Blocks.COBBLED_DEEPSLATE, 1),
                TierLook.mix(Blocks.POLISHED_ANDESITE, 2, Blocks.SPRUCE_PLANKS, 1), Blocks.DEEPSLATE_BRICKS, Blocks.SPRUCE_FENCE, Blocks.LANTERN,
                Blocks.COBBLED_DEEPSLATE_STAIRS, Boat.Type.SPRUCE);
    }

    public static Pal pal(SettlementStyle style) {
        Pal p = PALS.get(style);
        return p != null ? p : PALS.get(SettlementStyle.KINGDOM);
    }

    /** Piso do deque de um lote (o "quintal" da cidade das águas). */
    public static BlockState deck(Settlement s) {
        return pal(s.style).deck();
    }

    /**
     * Piso das passarelas (e da linha de rua dos lotes) no degrau {@code tier}: tábuas nos dois primeiros degraus; do
     * Burgo em diante o calçamento do estilo ({@link TierLook#street}) — menos o que cairia na água ou viraria lama.
     */
    public static BlockState walk(SettlementStyle style, int tier, int hash) {
        Pal p = pal(style);
        int t = TierLook.clamp(tier);
        if (t >= 3) {
            BlockState st = TierLook.street(style, t, hash);
            if (!(st.getBlock() instanceof FallingBlock) && !st.is(BlockTags.DIRT) && !st.is(Blocks.DIRT_PATH)) return st;
        }
        BlockState[] row = t <= 1 ? p.walk1() : p.walk2();
        return row[Math.floorMod(hash, row.length)];
    }

    /** O bloco é piso de cidade das águas (deque/passarela de algum estilo, ou calçamento de algum degrau)? */
    public static boolean isFloor(BlockState st) {
        return FLOORS.contains(st.getBlock()) || TierLook.isStreet(st);
    }

    /** Só as tábuas/pedras próprias da cidade das águas (sem o calçamento comum): o que a reforma do degrau repavimenta a mais. */
    public static boolean isDeck(BlockState st) {
        return FLOORS.contains(st.getBlock());
    }

    static int hash(int x, int z) {
        int h = x * 73856093 ^ z * 83492791;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return h & 0x7fffffff;
    }

    // =====================================================================================
    //  Piso sobre a água (chamado pelas obras e pelas passarelas)
    // =====================================================================================
    /** Dá para pôr o nosso piso/estaca aqui sem apagar nada de ninguém? (ar, água, mato solto) */
    static boolean open(BlockState st) {
        // bloco do jogador encharcado (cerca, placa, escada com água) NÃO é "livre": só a água e as plantas dela
        return st.isAir() || st.canBeReplaced() || st.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock
                || st.is(Blocks.KELP) || st.is(Blocks.KELP_PLANT) || st.is(Blocks.TALL_SEAGRASS) || st.is(Blocks.BUBBLE_COLUMN)
                || st.is(Blocks.LILY_PAD);
    }

    /**
     * Uma coluna MOLHADA de uma obra: o bloco do piso em {@code piece.baseY} (deque do lote, passarela da linha de rua
     * ou a base da muralha) e, de 4 em 4 blocos, uma estaca até o fundo. A muralha vira quebra-mar: a base desce 2
     * blocos para dentro da água em toda a extensão. Devolve quantos blocos mudou.
     *
     * @param top  piso da margem do lote (rua/quintal), ou null debaixo do prédio (o molde põe o piso dele depois)
     * @param wall peça de muralha (trecho, torre, portão)
     */
    public static int deckColumn(ServerLevel sl, Settlement s, Project.Piece piece, int x, int z, @Nullable BlockState top, boolean wall) {
        int n = 0, base = piece.baseY, sea = seaY(s);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(x, base, z);
        BlockState want = wall ? s.style.foundation() : top != null ? top : deck(s);
        BlockState cur = sl.getBlockState(m);
        if (!m.equals(s.heart) && cur != want && (open(cur) || isFloor(cur))) {
            sl.setBlock(m, want, 2 | 16);
            n++;
        }
        if (wall) {
            BlockState found = s.style.foundation();
            for (int y = base - 1; y >= sea - 1; y--) {
                if (!open(sl.getBlockState(m.setY(y)))) break;
                sl.setBlock(m, found, 2 | 16);
                n++;
            }
            if (Math.floorMod(x + z, 3) == 0) n += pile(sl, s, x, z, sea - 2);
        } else if ((x & 3) == 0 && (z & 3) == 0) {
            n += pile(sl, s, x, z, base - 1);
        }
        return n;
    }

    /**
     * Estaca da coluna, de {@code fromY} para baixo, até achar o fundo. Em água funda ela para em {@link #PILE_MAX}
     * blocos e termina numa pedra de fundação do estilo (nada de pilar de 40 blocos).
     */
    static int pile(ServerLevel sl, Settlement s, int x, int z, int fromY) {
        BlockState pile = pal(s.style).pile();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(x, fromY, z);
        int n = 0;
        for (int y = fromY; y > fromY - PILE_MAX && y > sl.getMinBuildHeight(); y--) {
            BlockState cur = sl.getBlockState(m.setY(y));
            if (!open(cur)) {
                // chegou ao fundo: um tufo de alga ao pé de algumas estacas (enfeite natural, some com a água)
                if (n > 1 && hash(x, z) % 3 == 0 && sl.hasChunk(x + 1 >> 4, z >> 4)) {
                    BlockState w = sl.getBlockState(m.set(x + 1, y + 1, z));
                    BlockPos under = m.below();
                    if (w.is(Blocks.WATER) && w.getFluidState().isSource() && sl.getBlockState(under).isFaceSturdy(sl, under, Direction.UP)) {
                        sl.setBlock(m, Blocks.SEAGRASS.defaultBlockState(), 2 | 16);
                    }
                }
                return n;
            }
            boolean last = y == fromY - PILE_MAX + 1;
            sl.setBlock(m, last ? s.style.foundation() : pile, 2 | 16);
            n++;
        }
        return n;
    }

    /**
     * Uma coluna de PASSARELA na altura do piso: sobre a água entra o piso (e a estaca, de 4 em 4); em terra firme a
     * passarela vira um caminho no chão, se ele estiver na mesma altura e livre em cima. Piso nosso de outro degrau ou
     * estilo é trocado. Devolve quantos blocos mudou.
     */
    static int walkColumn(ServerLevel sl, Settlement s, int x, int z) {
        int deck = deckY(s), n = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(x, deck, z);
        if (m.equals(s.heart)) return 0;
        BlockState want = walk(s.style, s.level, hash(x, z));
        BlockState cur = sl.getBlockState(m);
        boolean wet = water(sl, m.setY(deck - 1)) || open(sl.getBlockState(m));
        m.setY(deck);
        if (open(cur)) {
            sl.setBlock(m, want, 2 | 16);
            n++;
        } else if (!cur.is(want.getBlock())) {
            boolean ours = isDeck(cur) || TierLook.isStreet(cur);
            boolean soil = cur.is(BlockTags.DIRT) || cur.is(BlockTags.SAND) || cur.is(Blocks.GRAVEL);
            // caminho na margem: só troca o chão se dá para andar por cima dele
            if ((ours || soil) && open(sl.getBlockState(m.above())) && open(sl.getBlockState(m.above(2)))) {
                sl.setBlock(m, want, 2 | 16);
                n++;
            }
        }
        if (wet && (x & 3) == 0 && (z & 3) == 0) n += pile(sl, s, x, z, deck - 1);
        return n;
    }

    // =====================================================================================
    //  Onde ficar em pé
    // =====================================================================================
    /** Piso firme e seco com dois blocos livres em cima, nesta coluna e nesta altura dos pés? */
    private static boolean stand(ServerLevel sl, BlockPos.MutableBlockPos m, int x, int y, int z) {
        BlockState below = sl.getBlockState(m.set(x, y - 1, z));
        if (!below.getFluidState().isEmpty() || !below.isFaceSturdy(sl, m, Direction.UP)) return false;
        BlockState feet = sl.getBlockState(m.setY(y));
        if (!feet.getFluidState().isEmpty() || !feet.getCollisionShape(sl, m).isEmpty()) return false;
        BlockState head = sl.getBlockState(m.setY(y + 1));
        return head.getFluidState().isEmpty() && head.getCollisionShape(sl, m).isEmpty();
    }

    /**
     * O piso firme mais perto de (x, z), procurando em anéis até {@code reach} blocos: primeiro na altura do deque,
     * depois no chão natural (margem). Devolve a posição dos pés, ou null se só há água por perto.
     */
    @Nullable
    public static BlockPos firm(ServerLevel sl, Settlement s, int x, int z, int reach) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int feet = deckY(s) + 1;
        for (int r = 0; r <= reach; r++) {
            for (int ox = -r; ox <= r; ox++) {
                for (int oz = -r; oz <= r; oz++) {
                    if (Math.max(Math.abs(ox), Math.abs(oz)) != r) continue;
                    int px = x + ox, pz = z + oz;
                    if (!sl.hasChunk(px >> 4, pz >> 4)) continue;
                    if (stand(sl, m, px, feet, pz)) return new BlockPos(px, feet, pz);
                    int g = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, px, pz);
                    // margem: só chão mais BAIXO que o piso ou 1 acima (mais alto que isso pode ser um telhado)
                    if (g != feet && g >= feet - 6 && g <= feet + 1 && stand(sl, m, px, g, pz)) return new BlockPos(px, g, pz);
                }
            }
        }
        return null;
    }

    /** Como {@link #firm}, mas nunca null: sem piso por perto, vale a praça (ao lado do Coração). */
    public static BlockPos firmOrPlaza(ServerLevel sl, Settlement s, BlockPos at, int reach) {
        BlockPos f = firm(sl, s, at.getX(), at.getZ(), reach);
        if (f != null) return f;
        f = firm(sl, s, s.heart.getX() + 2, s.heart.getZ() + 2, 6);
        return f != null ? f : s.heart.offset(2, 0, 2);
    }

    /**
     * Construtor empacado numa obra sobre a água: aparece no piso firme mais perto do bloco da vez, se houver algum ao
     * alcance da obra (9 blocos). false = não há piso por ali ainda (vale a regra de sempre: ele chega pela água).
     */
    public static boolean bring(ServerLevel sl, Settlement s, net.minecraft.world.entity.Mob builder, BlockPos focus) {
        BlockPos to = firm(sl, s, focus.getX(), focus.getZ(), 9);
        if (to == null) return false;
        sl.sendParticles(ParticleTypes.CLOUD, builder.getX(), builder.getY() + 1, builder.getZ(), 8, 0.3, 0.5, 0.3, 0.02);
        builder.teleportTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5);
        builder.getNavigation().stop();
        sl.sendParticles(ParticleTypes.CLOUD, to.getX() + 0.5, to.getY() + 1, to.getZ() + 0.5, 8, 0.3, 0.5, 0.3, 0.02);
        return true;
    }

    /**
     * Ponto de chegada (moradores, mascates, comerciantes) numa cidade das águas: o piso firme mais perto do ponto
     * pedido. Sem o mundo carregado, devolve o próprio ponto.
     */
    public static BlockPos arrival(Settlement s, BlockPos at) {
        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        ServerLevel sl = server == null ? null : server.getLevel(s.dim);
        return sl == null ? at : firmOrPlaza(sl, s, at, 20);
    }

    // =====================================================================================
    //  Fundação
    // =====================================================================================
    /** Altura do bloco de água de cima da coluna, procurando perto de {@code nearY}; {@link Integer#MIN_VALUE} = não há água ali. */
    public static int surfaceY(ServerLevel sl, int x, int z, int nearY) {
        if (!sl.hasChunk(x >> 4, z >> 4)) return Integer.MIN_VALUE;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int y = nearY + 3; y >= nearY - 12; y--) {
            if (water(sl, m.set(x, y, z))) return water(sl, m.setY(y + 1)) ? Integer.MIN_VALUE : y;
            if (y <= nearY && sl.getBlockState(m).isSolid()) return Integer.MIN_VALUE;
        }
        return Integer.MIN_VALUE;
    }

    /** Onde o Coração nasce para o ponto escolhido: 2 blocos acima da água daquela coluna. null = o ponto não é água. */
    @Nullable
    public static BlockPos heartPos(ServerLevel sl, BlockPos at) {
        int y = surfaceY(sl, at.getX(), at.getZ(), at.getY());
        return y == Integer.MIN_VALUE ? null : new BlockPos(at.getX(), y + 2, at.getZ());
    }

    /** Parte (%) do território inicial que é água com 2 blocos de fundo ou mais (amostra de 3 em 3; só chunks carregados). */
    public static int wetPercent(ServerLevel sl, BlockPos heart) {
        int sea = heart.getY() - 2, wet = 0, known = 0;
        int h = CityLayout.half(CityLayout.START_RADIUS);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int ox = -h; ox <= h; ox += 3) {
            for (int oz = -h; oz <= h; oz += 3) {
                int x = heart.getX() + ox, z = heart.getZ() + oz;
                if (!sl.hasChunk(x >> 4, z >> 4)) continue;
                known++;
                if (water(sl, m.set(x, sea, z)) && water(sl, m.setY(sea - 1))) wet++;
            }
        }
        return known == 0 ? 0 : wet * 100 / known;
    }

    /**
     * Por que não dá para fundar uma Cidade das Águas neste ponto (null = pode): o ponto tem de ser água, com espaço
     * livre para o Coração, água de verdade sob {@link #MIN_WET}% do território inicial, e sem monumento oceânico nem
     * estrutura do mod no caminho. As regras de distância de outros lugares continuam no {@code SettlementService}.
     */
    @Nullable
    public static Component siteProblem(ServerLevel sl, BlockPos at) {
        BlockPos heart = heartPos(sl, at);
        if (heart == null) return SettlementsLang.tr("water.found.aim");
        if (!open(sl.getBlockState(heart)) || !sl.getFluidState(heart).isEmpty() || !open(sl.getBlockState(heart.above()))) {
            return SettlementsLang.tr("water.found.aim");
        }
        int pct = wetPercent(sl, heart);
        if (pct < MIN_WET) return SettlementsLang.tr("water.found.dry", pct, MIN_WET);
        return structure(sl, heart) ? SettlementsLang.tr("water.found.structure") : null;
    }

    /** Monumento oceânico ou estrutura do mod dentro do território inicial? (pela caixa da estrutura, só em chunks carregados) */
    private static boolean structure(ServerLevel sl, BlockPos heart) {
        BoundingBox t = CityLayout.territory(heart);
        BoundingBox box = new BoundingBox(t.minX(), sl.getMinBuildHeight(), t.minZ(), t.maxX(), sl.getMaxBuildHeight(), t.maxZ());
        try {
            var reg = sl.registryAccess().registryOrThrow(Registries.STRUCTURE);
            for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
                for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                    LevelChunk chunk = sl.getChunkSource().getChunkNow(cx, cz);
                    if (chunk == null) continue;
                    for (Map.Entry<Structure, LongSet> e : chunk.getAllReferences().entrySet()) {
                        if (e.getValue().isEmpty()) continue;
                        ResourceLocation key = reg.getKey(e.getKey());
                        if (key == null || !(key.getNamespace().equals(IsekaiMahou.MODID) || key.getPath().contains("monument"))) continue;
                        for (long start : e.getValue()) {
                            ChunkPos cp = new ChunkPos(start);
                            StructureStart st = sl.structureManager().getStartForStructure(SectionPos.of(cp, 0), e.getKey(),
                                    sl.getChunk(cp.x, cp.z, ChunkStatus.STRUCTURE_STARTS));
                            if (st != null && st.isValid() && st.getBoundingBox().intersects(box)) return true;
                        }
                    }
                }
            }
        } catch (RuntimeException e) {
            // ler referências de estrutura nunca derruba a fundação
            IsekaiMahou.LOGGER.debug("Cidade das Águas: falha ao conferir estruturas", e);
        }
        return false;
    }

    /**
     * Chamado pela fundação (o Coração já está sobre a água): o miolo do deque nasce na hora — é onde o fundador sobe
     * e os dois primeiros construtores chegam; o resto da praça é a obra de sempre, agora sobre estacas.
     */
    static void founded(ServerPlayer sp, ServerLevel sl, Settlement s) {
        int hx = s.heart.getX(), hz = s.heart.getZ();
        for (int ox = -CORE_HALF; ox <= CORE_HALF; ox++) {
            for (int oz = -CORE_HALF; oz <= CORE_HALF; oz++) walkColumn(sl, s, hx + ox, hz + oz);
        }
        SettlementService.spawnNpc(sl, s, Settlement.Role.BUILDER, s.heart.offset(2, 0, 2), s.heart);
        SettlementService.spawnNpc(sl, s, Settlement.Role.BUILDER, s.heart.offset(-2, 0, 2), s.heart);
        // o fundador (no barco, nadando ou na margem) vai para o deque, se estiver perto e mais baixo que ele
        if (sp.level() == sl && sp.distanceToSqr(hx + 0.5, s.heart.getY(), hz + 0.5) < 48 * 48 && sp.getY() <= s.heart.getY() + 1 && !sp.isSpectator()) {
            BlockPos to = firmOrPlaza(sl, s, s.heart.offset(2, 0, -2), 4);
            sp.stopRiding();
            sp.teleportTo(sl, to.getX() + 0.5, to.getY(), to.getZ() + 0.5, sp.getYRot(), sp.getXRot());
        }
        sl.sendParticles(ParticleTypes.SPLASH, hx + 0.5, s.heart.getY() - 0.5, hz + 0.5, 120, 4.0, 0.3, 4.0, 0.2);
        sl.playSound(null, s.heart, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.BLOCKS, 0.8F, 0.8F);
        sp.displayClientMessage(SettlementsLang.tr("water.found.start").withColor(s.style.color), true);
        Hints.show(sp, "city_water_found");
    }

    // ---- prévia (cliente pergunta, servidor responde)
    /**
     * Resposta da prévia para a Cidade das Águas. Devolve false se o terreno pedido não é a água. Campos novos:
     * {@code Water} (true), {@code WaterY} (altura do Coração; 0 = o ponto não é água) e {@code WaterPct} (parte do
     * território que é água). {@code Cells}: 0 = lote sobre a água, 1 = lote em terra firme.
     */
    static boolean probe(ServerPlayer sp, BlockPos pos, String terrain, CompoundTag out) {
        if (!WATER.equals(terrain)) return false;
        ServerLevel sl = sp.serverLevel();
        Component pr = SettlementService.foundingProblem(sp, pos, WATER);
        out.putBoolean("Ok", pr == null);
        if (pr != null) out.putString("Problem", SettlementView.json(pr, sp.registryAccess()));
        out.putBoolean("Water", true);
        BlockPos heart = heartPos(sl, pos);
        out.putInt("WaterY", heart == null ? 0 : heart.getY());
        out.putInt("WaterPct", heart == null ? 0 : wetPercent(sl, heart));
        int[] cells = new int[CityLayout.CELLS];
        if (heart != null) {
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            for (int c = 0; c < CityLayout.CELLS; c++) {
                // onda 9 (isles): a cidade nova é um arquipélago — os lotes da prévia ficam onde as plataformas vão nascer
                BlockPos cc = CityLayout.cellCenter(heart, CityLayout.isleCell(CityLayout.cellI(c), CityLayout.cellJ(c)));
                cells[c] = sl.hasChunk(cc.getX() >> 4, cc.getZ() >> 4) && water(sl, m.set(cc.getX(), heart.getY() - 2, cc.getZ())) ? 0 : 1;
            }
        }
        out.putIntArray("Cells", cells);
        return true;
    }

    /** O jogador confirmou a prévia sobre a água: abre a tela de estilo/nome, ou diz o motivo. true = o pedido era da água. */
    static boolean open(ServerPlayer sp, BlockPos pos, String terrain) {
        if (!WATER.equals(terrain)) return false;
        Component problem = SettlementService.foundingProblem(sp, pos, WATER);
        if (problem != null) {
            sp.displayClientMessage(problem.copy().withColor(0xE8735E), true);
            sp.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1.0F, 1.0F);
            return true;
        }
        PacketDistributor.sendToPlayer(sp, new com.renanpontes.isekaimahou.feature.settlements.network.SettlementsNetwork.FoundOpen(pos));
        return true;
    }

    // =====================================================================================
    //  Tela
    // =====================================================================================
    /** Para o cliente (dentro do NBT da cidade): {@code Terrain} (id do tipo de terreno), {@code Water} e {@code WaterFish}. */
    static void view(CompoundTag t, Settlement s) {
        t.putString("Terrain", s.terrain());
        if (!s.water) return;
        t.putBoolean("Water", true);
        if (s.isles) t.putBoolean("Isles", true);
        t.putInt("WaterFish", fishing(s));
    }

    private WaterCity() {}
}
