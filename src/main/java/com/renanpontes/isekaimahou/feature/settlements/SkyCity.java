package com.renanpontes.isekaimahou.feature.settlements;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.settlements.build.CityLayout;
import com.renanpontes.isekaimahou.feature.settlements.build.CityTerrain;
import com.renanpontes.isekaimahou.feature.settlements.build.ConstructionEngine;
import com.renanpontes.isekaimahou.feature.settlements.build.Project;
import com.renanpontes.isekaimahou.feature.ui_hud.Hints;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

/**
 * Onda 7 (sky_island) — <b>Ilha Celeste (SkyLand)</b>: a cidade do jogador fundada sobre uma ilha flutuante criada pelo
 * mod. É um TIPO DE TERRENO ({@link #GROUND} / {@link #SKYLAND}), que combina com qualquer estilo. Tudo o que é regra
 * da ilha mora aqui; cidade no chão ({@code !s.sky}) nunca entra em nada desta classe.
 * <ul>
 *   <li><b>Altura</b>: o Coração nasce acima do ponto escolhido no chão, em {@link #heartY}; o topo da ilha (onde se
 *       pisa) é {@link #topY} = a altura do piso da praça. A ilha inteira é plana nessa altura.</li>
 *   <li><b>Forma</b> ({@link #inside}, {@link #thickness}): conta pura com a posição (não lê o mundo) — um quadrado de
 *       cantos redondos que cobre o território do raio + {@link #MARGIN} blocos, com a borda estufada por ruído, e uma
 *       barriga em cone invertido (grossa no miolo, fina nos anéis de fora) com pontas de pedra. A forma de um raio
 *       maior sempre CONTÉM a do raio menor, então a ilha só cresce.</li>
 *   <li><b>Geração aos poucos</b> ({@link #tick}): anel por anel a partir do coração, com orçamento de blocos por tick
 *       (o mesmo das obras: {@link ConstructionEngine#claim}), só em chunk carregado, com o cursor salvo na cidade —
 *       continua depois de reiniciar. Colunas em chunk descarregado ficam para uma nova passada.</li>
 *   <li><b>Nunca destrói</b>: só entra em ar, fluido e mato solto; o que o jogador construiu é contornado. Ao crescer,
 *       a terra que já existia só ganha blocos EMBAIXO (o que o jogador cavou não é tapado de volta).</li>
 *   <li><b>Sombra</b> ({@link #onSpawnCheck}): sem monstro natural na superfície debaixo da ilha.</li>
 * </ul>
 * <b>Para os outros sistemas</b>: {@link #isSky}, {@link #topY}, {@link #hasLand}, {@link #landReady},
 * {@link #waitingLand}, {@link #inFootprint}, {@link #reach}, {@link #rimPoint}, {@link #pierSide}, {@link #pierPoint},
 * {@link #generating}, {@link #percent} e o gancho {@link #onLand}.
 */
public final class SkyCity {
    /** Tipos de terreno da fundação (o campo novo do pacote {@code Found}; id salvo: nunca renomear). */
    public static final String GROUND = "ground";
    public static final String SKYLAND = "skyland";

    // ---- altura
    /** A ilha fica este tanto acima do terreno mais alto por perto... */
    public static final int LIFT = 64;
    /** ...mas sempre entre estas alturas (nuvens ~192; o prédio mais alto cabe abaixo do limite de construção). */
    public static final int MIN_Y = 160, MAX_Y = 208;
    /** Folga mínima entre o terreno mais alto e o coração (senão a barriga da ilha encosta na montanha). */
    private static final int MIN_CLEARANCE = 48;

    // ---- forma
    /** Terra garantida além do território do raio (faixa fora da muralha e chegada dos moradores). */
    public static final int MARGIN = 14;
    /** Raio dos cantos arredondados. */
    private static final int CORNER = 30;
    /** Até quanto a borda estufa para fora (ruído). */
    private static final int EDGE = 18;
    /** Raio (em blocos) do cone grosso do miolo. */
    private static final int CORE = 64;
    /** Espessura máxima de uma coluna. */
    public static final int MAX_THICK = 56;
    /** A esta distância para dentro da borda a coluna já tem a espessura cheia (ver {@link #thickness}). */
    private static final int FULL_INSET = 40;

    // ---- geração
    /** Blocos por tick (saem do orçamento comum das obras). */
    private static final int BLOCKS_PER_TICK = 320;
    /** Colunas examinadas por tick (a maioria é só conta; limita as passadas sobre terra que já existe). */
    private static final int COLUMNS_PER_TICK = 1500;
    /** Anel em que o miolo da praça está pronto: o fundador sobe e os primeiros construtores chegam. */
    public static final int CORE_RING = 12;
    /** Espera antes de voltar às colunas que ficaram em chunk descarregado. */
    private static final int RETRY_WAIT = 200;

    /** Avisado quando a terra de uma cidade do céu muda: começou a crescer ({@code finished=false}) ou terminou. */
    public interface LandListener {
        void changed(ServerLevel sl, Settlement s, int oldRadius, int newRadius, boolean finished);
    }

    private static final List<LandListener> LISTENERS = new ArrayList<>();

    /**
     * Gancho "a ilha cresceu". Dispara duas vezes por crescimento: no começo ({@code finished=false}: a forma nova já
     * vale para {@link #inFootprint}/{@link #rimPlanned}) e quando a terra do raio novo está toda no lugar
     * ({@code finished=true}: {@link #rimPoint} já devolve a borda nova). A primeira ilha tem {@code oldRadius = 0}.
     */
    public static void onLand(LandListener l) {
        LISTENERS.add(l);
    }

    private static void fire(ServerLevel sl, Settlement s, int oldRadius, int newRadius, boolean finished) {
        for (LandListener l : LISTENERS) {
            try {
                l.changed(sl, s, oldRadius, newRadius, finished);
            } catch (RuntimeException e) {
                // um ouvinte com defeito não pode parar a geração da ilha
                IsekaiMahou.LOGGER.error("Ilha Celeste: gancho de terra falhou", e);
            }
        }
    }

    // =====================================================================================
    //  Consultas
    // =====================================================================================
    public static boolean isSky(@Nullable Settlement s) {
        return s != null && s.sky;
    }

    /** Altura do topo plano da ilha (o bloco em que se pisa; os pés ficam em {@code topY + 1}). */
    public static int topY(Settlement s) {
        return s.heart.getY() - 1;
    }

    /** Raio cuja forma vale agora: o que está sendo gerado, senão o que já existe. */
    public static int shapeRadius(Settlement s) {
        // onda 9 (isles): no arquipélago não há "forma do raio" — vale o raio da cidade assim que a ilha do meio existe
        if (s.isles) return SkyIsles.coreDone(s) ? Math.max(1, s.radius) : 0;
        return Math.max(s.skyGoal, s.skyLand);
    }

    /** Maior distância (num eixo) do coração até a borda mais estufada da ilha de um raio. */
    public static int reach(int radius) {
        return CityLayout.half(radius) + MARGIN + EDGE + 1;
    }

    /** Caixa que contém a ilha de um raio: barriga inteira embaixo, espaço dos prédios em cima. */
    public static BoundingBox box(BlockPos heart, int radius) {
        int r = reach(radius);
        return new BoundingBox(heart.getX() - r, heart.getY() - 1 - MAX_THICK, heart.getZ() - r,
                heart.getX() + r, heart.getY() + CityLayout.BOX_UP, heart.getZ() + r);
    }

    /** A coluna (x, z) faz parte da PEGADA da ilha (forma do raio atual — a terra pode ainda estar se formando)? */
    public static boolean inFootprint(Settlement s, int x, int z) {
        if (s.sky && s.isles) return SkyIsles.inFootprint(s, x, z);
        return s.sky && inside(s.heart, shapeRadius(s), x - s.heart.getX(), z - s.heart.getZ());
    }

    /** Anéis já percorridos pela geração em andamento (colunas puladas por chunk descarregado contam como "não"). */
    private static int doneRing(Settlement s) {
        return s.skySkip >= 0 ? Math.min(s.skyRing, s.skySkip) : s.skyRing;
    }

    /**
     * Já existe terra na coluna (x, z)? Conta pura (não lê o mundo): a terra de um raio já completo, mais os anéis que
     * a geração em andamento já percorreu. Cidade no chão: sempre true.
     */
    public static boolean hasLand(Settlement s, int x, int z) {
        if (!s.sky) return true;
        // onda 9 (isles): terra só nas ilhas prontas (o vão entre elas é o vazio)
        if (s.isles) return SkyIsles.hasLand(s, x, z);
        int dx = x - s.heart.getX(), dz = z - s.heart.getZ();
        if (s.skyLand > 0 && inside(s.heart, s.skyLand, dx, dz)) return true;
        if (s.skyGoal <= 0) return false;
        return Math.max(Math.abs(dx), Math.abs(dz)) < doneRing(s) && inside(s.heart, s.skyGoal, dx, dz);
    }

    /**
     * Altura do bloco mais BAIXO da ilha na coluna ({@link Integer#MAX_VALUE} = sem terra). A fundação das obras nunca
     * desce abaixo disto: nada de pilar pendurado no vazio.
     */
    public static int floorY(Settlement s, int x, int z) {
        if (s.sky && s.isles) return SkyIsles.floorY(s, x, z);
        if (!s.sky || !hasLand(s, x, z)) return Integer.MAX_VALUE;
        int dx = x - s.heart.getX(), dz = z - s.heart.getZ();
        int t = s.skyLand > 0 ? thickness(s.heart, s.skyLand, dx, dz) : 0;
        if (s.skyGoal > 0 && Math.max(Math.abs(dx), Math.abs(dz)) < doneRing(s)) t = Math.max(t, thickness(s.heart, s.skyGoal, dx, dz));
        return topY(s) - Math.max(1, t) + 1;
    }

    /** O quadrado de meia-largura {@code half} em volta de (cx, cz) já tem terra (cantos, lados e meio)? */
    public static boolean landReady(Settlement s, int cx, int cz, int half) {
        if (!s.sky) return true;
        int h = Math.max(1, half);
        for (int ox = -h; ox <= h; ox += h) {
            for (int oz = -h; oz <= h; oz += h) {
                if (!hasLand(s, cx + ox, cz + oz)) return false;
            }
        }
        return true;
    }

    /** O lote (grade ou livre) já tem terra — o lote inteiro e a linha de rua em volta? */
    public static boolean landReady(Settlement s, int cell) {
        if (!s.sky) return true;
        BlockPos c = CityLayout.cellCenter(s.heart, cell);
        return landReady(s, c.getX(), c.getZ(), CityTerrain.LEVEL_HALF);
    }

    /** A obra está parada esperando a terra da peça atual se formar? (cidade no chão: nunca) */
    public static boolean waitingLand(Settlement s, Project p) {
        if (!s.sky) return false;
        Project.Piece piece = p.current();
        if (piece == null) return false;
        // onda 9 (isles): torre/portão de muralha fica numa ilhota de vigia (a conferência olha só o miolo dela)
        int half = piece.kind == Project.Piece.BUILDING ? Math.max(p.clearHalf, 7) : s.isles ? 4 : 6;
        return !landReady(s, piece.cx, piece.cz, half);
    }

    /** A terra ainda está se formando (primeira ilha ou anel novo)? */
    public static boolean generating(Settlement s) {
        if (s.sky && s.isles) return SkyIsles.generating(s);
        return s.sky && s.skyGoal > 0;
    }

    /** Andamento da geração atual, 0..99; 100 = terra completa; -1 = cidade no chão. */
    public static int percent(Settlement s) {
        if (!s.sky) return -1;
        if (s.isles) return SkyIsles.percent(s);
        if (s.skyGoal <= 0) return s.skyLand >= s.radius ? 100 : 0;
        long a = (long) s.skyStart * s.skyStart, b = (long) (reach(s.skyGoal) + 1) * (reach(s.skyGoal) + 1);
        long now = (long) doneRing(s) * doneRing(s);
        return b <= a ? 99 : (int) Mth.clamp((now - a) * 100L / (b - a), 0L, 99L);
    }

    // ---- borda / cais
    /**
     * Última coluna COM TERRA indo do coração na direção (stepX, stepZ) — passos de -1, 0 ou 1. Devolve a posição dos
     * pés ({@code topY + 1}) de quem está em pé na borda; null = ainda não há terra nenhuma.
     */
    @Nullable
    public static BlockPos rimPoint(Settlement s, int stepX, int stepZ) {
        return rim(s, stepX, stepZ, false);
    }

    @Nullable
    public static BlockPos rimPoint(Settlement s, Direction side) {
        return rim(s, side.getStepX(), side.getStepZ(), false);
    }

    /** Como {@link #rimPoint}, mas pela FORMA do raio atual (onde a borda vai ficar quando a terra terminar de se formar). */
    @Nullable
    public static BlockPos rimPlanned(Settlement s, Direction side) {
        return rim(s, side.getStepX(), side.getStepZ(), true);
    }

    @Nullable
    private static BlockPos rim(Settlement s, int stepX, int stepZ, boolean planned) {
        if (!s.sky || stepX == 0 && stepZ == 0) return null;
        // onda 9 (isles): o arquipélago não tem uma borda só (cada ilha tem a sua)
        if (s.isles) return null;
        int sx = Integer.signum(stepX), sz = Integer.signum(stepZ);
        for (int d = reach(shapeRadius(s)); d >= 0; d--) {
            int x = s.heart.getX() + sx * d, z = s.heart.getZ() + sz * d;
            if (planned ? inFootprint(s, x, z) : hasLand(s, x, z)) return new BlockPos(x, topY(s) + 1, z);
        }
        return null;
    }

    /** Lado da ilha reservado para o cais (o da frente da praça; não muda quando a ilha cresce). */
    public static Direction pierSide(Settlement s) {
        return s.plazaRotation().rotate(Direction.SOUTH);
    }

    /** Ponto da borda onde o cais encosta (pés), no lado de {@link #pierSide}; null = ainda sem terra. */
    @Nullable
    public static BlockPos pierPoint(Settlement s) {
        // onda 9 (isles): não há cais na borda — quem chega é posto ao lado do poço da corrente, no meio
        if (s.isles) return SkyIsles.landing(s);
        return rimPoint(s, pierSide(s));
    }

    // =====================================================================================
    //  Forma (conta pura: mesma resposta no servidor e no cliente)
    // =====================================================================================
    static int seed(BlockPos heart) {
        return heart.getX() * 31 + heart.getZ() * 17;
    }

    static int hash(int x, int z, int seed) {
        int h = x * 374761393 + z * 668265263 + seed * 1274126177;
        h = (h ^ h >>> 13) * 1274126177;
        return h ^ h >>> 16;
    }

    /** Ruído de valor suave em [0, 1). */
    static double noise(double x, double z, int seed) {
        int x0 = Mth.floor(x), z0 = Mth.floor(z);
        double fx = x - x0, fz = z - z0;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        double a = unit(x0, z0, seed), b = unit(x0 + 1, z0, seed), c = unit(x0, z0 + 1, seed), d = unit(x0 + 1, z0 + 1, seed);
        return Mth.lerp(fz, Mth.lerp(fx, a, b), Mth.lerp(fx, c, d));
    }

    private static double unit(int x, int z, int seed) {
        return (hash(x, z, seed) & 0xFFFFFF) / (double) 0x1000000;
    }

    /** Distância com sinal até o quadrado de cantos redondos do raio (negativa = dentro). */
    private static double sdf(int radius, int dx, int dz) {
        double b = CityLayout.half(radius) + MARGIN;
        double qx = Math.abs(dx) - (b - CORNER), qz = Math.abs(dz) - (b - CORNER);
        double out = Math.hypot(Math.max(qx, 0), Math.max(qz, 0));
        return out + Math.min(Math.max(qx, qz), 0) - CORNER;
    }

    /** Quanto a borda estufa para fora neste ponto (0..EDGE): é o que tira a ilha do formato de régua. */
    private static double edgeAt(int seed, int dx, int dz) {
        // três tamanhos de ondulação: baías largas, recortes médios e um serrilhado fino
        return EDGE * (0.55 * noise(dx / 34.0, dz / 34.0, seed) + 0.30 * noise(dx / 13.0, dz / 13.0, seed + 1)
                + 0.15 * noise(dx / 5.0, dz / 5.0, seed + 7));
    }

    /** (dx, dz a partir do coração) está dentro da forma da ilha do raio? */
    public static boolean inside(BlockPos heart, int radius, int dx, int dz) {
        if (radius <= 0) return false;
        double d = sdf(radius, dx, dz);
        if (d > EDGE) return false;
        return d <= 0 || d <= edgeAt(seed(heart), dx, dz);
    }

    /** Espessura "cheia" da coluna (longe da borda): fina nos anéis de fora, cone grosso no miolo, pontas de pedra. */
    private static double fullThick(int seed, int dx, int dz) {
        double dist = Math.sqrt((double) dx * dx + (double) dz * dz);
        double core = dist >= CORE ? 0 : 1 - dist / CORE;
        core = core * core * (3 - 2 * core);
        double t = 6 + 8 * noise(dx / 38.0, dz / 38.0, seed + 2) + 26 * core;
        // pontas: uma por célula de 11 blocos (ou nenhuma), sempre inteira dentro da célula
        int cx = Math.floorDiv(dx, 11), cz = Math.floorDiv(dz, 11);
        int h = hash(cx, cz, seed + 3);
        if ((h & 127) < 70) {
            double px = cx * 11 + 4.5 + (h >> 8 & 3) * 0.66, pz = cz * 11 + 4.5 + (h >> 10 & 3) * 0.66;
            double r = 2.5 + (h >> 12 & 3) * 0.6;
            double d = Math.hypot(dx + 0.5 - px, dz + 0.5 - pz);
            if (d < r) t += (5 + (h >> 14 & 7) + 6 * core) * Math.pow(1 - d / r, 1.3);
        }
        return t;
    }

    /**
     * Blocos de terra da coluna na ilha de um raio (0 = fora). Perto da borda a coluna afina até 2–3 blocos; a
     * {@link #FULL_INSET} blocos para dentro ela já tem a espessura cheia — por isso, ao crescer, só a faixa da borda
     * antiga precisa engrossar.
     */
    public static int thickness(BlockPos heart, int radius, int dx, int dz) {
        if (radius <= 0) return 0;
        double d = sdf(radius, dx, dz);
        if (d > EDGE) return 0;
        int seed = seed(heart);
        double e = edgeAt(seed, dx, dz) - d;
        if (e < 0) return 0;
        double taper = 2 + e * 1.4 + 1.5 * noise(dx / 4.0, dz / 4.0, seed + 4);
        // bem para dentro a conta cara das pontas decide; na borda quem manda é o afinamento
        double full = fullThick(seed, dx, dz);
        return Mth.clamp((int) Math.min(full, taper), 1, MAX_THICK);
    }

    // =====================================================================================
    //  Paleta por estilo
    // =====================================================================================
    /** Camadas da ilha: topo (com manchas), subsolo, três pedras misturadas, faixa opcional e a ponta das estalactites. */
    record Palette(BlockState top, BlockState patch, double patchShare, BlockState sub, BlockState stone, BlockState stone2,
                           BlockState stone3, @Nullable BlockState band, BlockState tip) {}

    private static BlockState b(net.minecraft.world.level.block.Block block) {
        return block.defaultBlockState();
    }

    private static final Map<SettlementStyle, Palette> PALETTES = new java.util.EnumMap<>(SettlementStyle.class);

    static {
        // topo sempre "chão" para as obras (terra/areia/neve: CityTerrain.ground), para ruas e quintais entrarem por cima
        PALETTES.put(SettlementStyle.KINGDOM, new Palette(b(Blocks.GRASS_BLOCK), b(Blocks.GRASS_BLOCK), 0, b(Blocks.DIRT),
                b(Blocks.STONE), b(Blocks.ANDESITE), b(Blocks.COBBLESTONE), null, b(Blocks.ANDESITE)));
        PALETTES.put(SettlementStyle.DESERT, new Palette(b(Blocks.SAND), b(Blocks.SAND), 0, b(Blocks.SANDSTONE),
                b(Blocks.SANDSTONE), b(Blocks.SMOOTH_SANDSTONE), b(Blocks.TERRACOTTA), b(Blocks.ORANGE_TERRACOTTA), b(Blocks.RED_SANDSTONE)));
        PALETTES.put(SettlementStyle.NORDIC, new Palette(b(Blocks.GRASS_BLOCK), b(Blocks.SNOW_BLOCK), 0.38, b(Blocks.DIRT),
                b(Blocks.STONE), b(Blocks.ANDESITE), b(Blocks.PACKED_ICE), null, b(Blocks.PACKED_ICE)));
        PALETTES.put(SettlementStyle.ORIENTAL, new Palette(b(Blocks.GRASS_BLOCK), b(Blocks.MOSS_BLOCK), 0.12, b(Blocks.DIRT),
                b(Blocks.STONE), b(Blocks.ANDESITE), b(Blocks.CALCITE), null, b(Blocks.CALCITE)));
        PALETTES.put(SettlementStyle.ELVEN, new Palette(b(Blocks.GRASS_BLOCK), b(Blocks.MOSS_BLOCK), 0.3, b(Blocks.ROOTED_DIRT),
                b(Blocks.STONE), b(Blocks.MOSSY_COBBLESTONE), b(Blocks.MOSS_BLOCK), null, b(Blocks.MOSSY_COBBLESTONE)));
        PALETTES.put(SettlementStyle.GOTHIC, new Palette(b(Blocks.GRASS_BLOCK), b(Blocks.PODZOL), 0.3, b(Blocks.COARSE_DIRT),
                b(Blocks.DEEPSLATE), b(Blocks.COBBLED_DEEPSLATE), b(Blocks.TUFF), null, b(Blocks.BLACKSTONE)));
        PALETTES.put(SettlementStyle.DWARVEN, new Palette(b(Blocks.GRASS_BLOCK), b(Blocks.COARSE_DIRT), 0.22, b(Blocks.DIRT),
                b(Blocks.DEEPSLATE), b(Blocks.TUFF), b(Blocks.STONE), null, b(Blocks.SMOOTH_BASALT)));
    }

    static Palette palette(SettlementStyle style) {
        Palette p = PALETTES.get(style);
        return p != null ? p : PALETTES.get(SettlementStyle.KINGDOM);
    }

    /** Bloco da camada {@code k} (0 = topo) de uma coluna de {@code t} blocos. */
    private static BlockState layer(Palette pal, int k, int t, int x, int y, int z, boolean supported) {
        if (k == 0) {
            BlockState top = pal.patchShare() > 0 && noise(x / 9.0, z / 9.0, 77) < pal.patchShare() ? pal.patch() : pal.top();
            // areia sem apoio cairia no vazio: vira o bloco de baixo
            return top.getBlock() instanceof FallingBlock && !supported ? pal.sub() : top;
        }
        int soil = Math.min(3, t - 2) + (hash(x, z, 5) & 1);
        if (k <= soil) return pal.sub();
        if (t >= 12 && k >= t - 2) return pal.tip();
        if (pal.band() != null && Math.floorMod(y + (hash(x >> 2, z >> 2, 6) & 1), 5) == 0) return pal.band();
        double v = noise((x + y * 0.7) / 5.0, (z - y * 0.7) / 5.0, 91);
        return v < 0.6 ? pal.stone() : v < 0.84 ? pal.stone2() : pal.stone3();
    }

    // =====================================================================================
    //  Fundação
    // =====================================================================================
    /**
     * Altura do Coração de uma ilha fundada sobre o ponto {@code ground}: {@code clamp(terreno mais alto por perto +
     * 64, 160, 208)}. -1 = o terreno ali é alto demais (a barriga da ilha encostaria na montanha).
     */
    public static int heartY(ServerLevel sl, BlockPos ground) {
        int highest = ground.getY();
        for (int ox = -96; ox <= 96; ox += 16) {
            for (int oz = -96; oz <= 96; oz += 16) {
                int x = ground.getX() + ox, z = ground.getZ() + oz;
                if (sl.hasChunk(x >> 4, z >> 4)) highest = Math.max(highest, sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
            }
        }
        // onda 9 (isles): toda Ilha Celeste nova é um arquipélago, BEM mais alto ("a cidade voadora tem que ser bem mais
        // alta"): SkyIsles.ALTITUDE, ou CLEARANCE acima do morro mais alto, até SkyIsles.MAX_Y. As antigas não mudam de altura.
        return SkyIsles.heartY(highest);
    }

    /**
     * Algo no céu no caminho da ilha de um raio? As Ilhas Voadoras e a Torre Flutuante (estruturas "do céu", conferidas
     * pela caixa delas, só em chunks carregados) e, com {@code blocks}, qualquer coisa já construída no volume da ilha
     * (amostra esparsa). null = céu livre.
     */
    @Nullable
    public static Component obstacle(ServerLevel sl, BlockPos heart, int radius, boolean blocks) {
        BoundingBox box = box(heart, radius);
        try {
            var reg = sl.registryAccess().registryOrThrow(Registries.STRUCTURE);
            for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
                for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                    LevelChunk chunk = sl.getChunkSource().getChunkNow(cx, cz);
                    if (chunk == null) continue;
                    for (Map.Entry<Structure, LongSet> e : chunk.getAllReferences().entrySet()) {
                        if (e.getValue().isEmpty()) continue;
                        ResourceLocation key = reg.getKey(e.getKey());
                        if (key == null || !(key.getPath().contains("sky_island") || key.getPath().contains("floating"))) continue;
                        for (long start : e.getValue()) {
                            ChunkPos cp = new ChunkPos(start);
                            StructureStart st = sl.structureManager().getStartForStructure(SectionPos.of(cp, 0), e.getKey(),
                                    sl.getChunk(cp.x, cp.z, ChunkStatus.STRUCTURE_STARTS));
                            if (st != null && st.isValid() && st.getBoundingBox().intersects(box)) return SettlementsLang.tr("sky.found.blocked");
                        }
                    }
                }
            }
        } catch (RuntimeException e) {
            // ler referências de estrutura nunca derruba a fundação: vale a amostra de blocos abaixo
            IsekaiMahou.LOGGER.debug("Ilha Celeste: falha ao conferir estruturas do céu", e);
        }
        if (!blocks) return null;
        int found = 0, top = heart.getY() - 1;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int r = reach(radius);
        for (int ox = -r; ox <= r; ox += 8) {
            for (int oz = -r; oz <= r; oz += 8) {
                int x = heart.getX() + ox, z = heart.getZ() + oz;
                if (!sl.hasChunk(x >> 4, z >> 4) || !inside(heart, radius, ox, oz)) continue;
                for (int y : new int[] {top + 6, top, top - 6}) {
                    if (!sl.getBlockState(m.set(x, y, z)).isAir() && ++found >= 3) return SettlementsLang.tr("sky.found.blocked");
                }
            }
        }
        for (int y = heart.getY(); y <= heart.getY() + 8; y++) {
            if (!sl.getBlockState(m.set(heart.getX(), y, heart.getZ())).isAir()) return SettlementsLang.tr("sky.found.blocked");
        }
        return null;
    }

    /** Chamado pela fundação (o Coração já está no céu): começa a formar a terra e avisa o fundador. */
    static void founded(ServerPlayer sp, ServerLevel sl, Settlement s, BlockPos ground) {
        s.skyLift = true;
        // onda 9 (isles): primeiro só a ilha do meio; as outras nascem quando a cidade precisar de cada lote
        if (s.isles) SkyIsles.founded(sl, s);
        else start(sl, s, s.radius);
        refreshShades(sl.getServer());
        // feixe do ponto escolhido até a ilha (só para o fundador: ele está no chão, longe do Coração)
        for (int y = ground.getY() + 2; y < s.heart.getY(); y += 5) {
            sl.sendParticles(sp, ParticleTypes.END_ROD, true, ground.getX() + 0.5, y, ground.getZ() + 0.5, 2, 0.15, 1.2, 0.15, 0.01);
        }
        sp.playNotifySound(SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.9F, 1.3F);
        sp.displayClientMessage(SettlementsLang.tr("sky.found.forming").withColor(s.style.color), true);
        Hints.show(sp, "city_sky_found");
    }

    // =====================================================================================
    //  Geração aos poucos
    // =====================================================================================
    /** Primeiro anel que uma passada de crescimento precisa olhar: para dentro disso a terra velha já está cheia. */
    private static int firstRing(int oldRadius) {
        return oldRadius <= 0 ? 0 : Math.max(0, CityLayout.half(oldRadius) + MARGIN - FULL_INSET);
    }

    /** As formas de dois raios são a mesma? (anel par: o território não aumenta — a muralha de fora já contava) */
    private static boolean sameShape(int a, int b) {
        return a > 0 && b > 0 && CityLayout.half(a) == CityLayout.half(b);
    }

    private static void start(ServerLevel sl, Settlement s, int goal) {
        int old = s.skyLand;
        if (sameShape(old, goal)) {
            // a ilha já tem essa terra: nada a gerar, nada a anunciar
            s.skyLand = Math.min(CityLayout.MAX_RADIUS, goal);
            return;
        }
        s.skyGoal = Math.min(CityLayout.MAX_RADIUS, goal);
        s.skyStart = s.skyRing = firstRing(old);
        s.skyIdx = 0;
        s.skySkip = -1;
        s.skyWait = 0;
        fire(sl, s, old, s.skyGoal, false);
        if (old > 0) {
            MinecraftServer server = sl.getServer();
            Component name = SettlementService.cityName(server, s);
            for (ServerPlayer g : CityEconomy.governors(server, s)) {
                g.displayClientMessage(SettlementsLang.tr("sky.growing", name).withColor(s.style.color), true);
            }
            CityPace.diary(server, s, SettlementsLang.tr("sky.diary.grow"));
            sl.playSound(null, s.heart, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.5F, 0.6F);
        }
    }

    /**
     * O raio da cidade mudou ({@code CityPlanning.expand}, comandos): a ilha começa a ganhar o anel novo já. Chamar de
     * novo sem mudança não faz nada; o {@link #tick} também percebe sozinho (uma vez por segundo).
     */
    public static void radiusChanged(ServerLevel sl, Settlement s) {
        // onda 9 (isles): crescer só libera terrenos — a terra vem com cada obra, não um anel inteiro de uma vez
        if (s.isles) return;
        if (!s.sky || s.radius <= shapeRadius(s)) return;
        if (s.skyGoal <= 0) {
            start(sl, s, s.radius);
            return;
        }
        // cresceu de novo no meio de uma geração: a borda do raio intermediário (afinada) volta para a fila
        int prev = s.skyGoal;
        s.skyGoal = Math.min(CityLayout.MAX_RADIUS, s.radius);
        if (sameShape(prev, s.skyGoal)) return;
        int back = firstRing(prev);
        if (s.skyRing > back) {
            s.skyRing = back;
            s.skyIdx = 0;
        }
        s.skyStart = Math.min(s.skyStart, s.skyRing);
        fire(sl, s, prev, s.skyGoal, false);
    }

    /** Tick do Coração (só com o chunk dele carregado). Cidade no chão sai na primeira linha. */
    public static void tick(ServerLevel sl, Settlement s, int age) {
        if (!s.sky) return;
        if (s.isles) {
            // onda 9 (isles): ilhas sob demanda, geração aos poucos e pontes
            SkyIsles.tick(sl, s, age);
            if (s.skyLift && age % 40 == 11 && SkyIsles.coreDone(s)) lift(sl, s);
            return;
        }
        // fundador que saiu do jogo enquanto o miolo se formava: sobe quando voltar
        if (s.skyLift && age % 40 == 11 && hasLand(s, s.heart.getX() + 2, s.heart.getZ() + 2)
                && (s.skyGoal <= 0 || doneRing(s) > CORE_RING)) lift(sl, s);
        if (s.skyGoal <= 0) {
            if (age % 20 == 3 && s.radius > s.skyLand) start(sl, s, s.radius);
            return;
        }
        if (s.radius > s.skyGoal) radiusChanged(sl, s);
        if (s.skyWait > 0) {
            s.skyWait--;
            return;
        }
        int budget = ConstructionEngine.claim(sl, BLOCKS_PER_TICK);
        if (budget > 0) run(sl, s, budget, COLUMNS_PER_TICK, true);
    }

    /** Posição (dx, dz) da coluna {@code idx} do anel: quatro frentes andando juntas, uma em cada lado. */
    private static int[] ringPos(int ring, int idx) {
        if (ring == 0) return new int[] {0, 0};
        int count = 8 * ring;
        // 2·anel+1 é primo com 8·anel: a ordem passa por todas as colunas, pulando de lado em lado
        int i = (int) ((long) idx * (2 * ring + 1) % count);
        int side = i / (2 * ring), off = i % (2 * ring);
        return switch (side) {
            case 0 -> new int[] {-ring + off, -ring};
            case 1 -> new int[] {ring, -ring + off};
            case 2 -> new int[] {ring - off, ring};
            default -> new int[] {-ring, ring - off};
        };
    }

    /**
     * Avança a geração: até {@code blocks} blocos e {@code columns} colunas examinadas. Cada coluna é feita inteira
     * (o orçamento pode passar um pouco). Devolve quantos blocos colocou.
     */
    private static int run(ServerLevel sl, Settlement s, int blocks, int columns, boolean fx) {
        int goal = s.skyGoal;
        if (goal <= 0) return 0;
        int end = reach(goal);
        int hx = s.heart.getX(), hz = s.heart.getZ();
        int placed = 0, seen = 0;
        while (s.skyRing <= end && placed < blocks && seen < columns) {
            int ring = s.skyRing, count = ring == 0 ? 1 : 8 * ring;
            while (s.skyIdx < count && placed < blocks && seen < columns) {
                int[] o = ringPos(ring, s.skyIdx++);
                seen++;
                int t = thickness(s.heart, goal, o[0], o[1]);
                if (t <= 0) continue;
                // terra de um raio anterior: só falta o que engrossou embaixo (o que o jogador cavou não é tapado)
                int from = s.skyLand > 0 ? thickness(s.heart, s.skyLand, o[0], o[1]) : 0;
                if (from >= t) continue;
                int x = hx + o[0], z = hz + o[1];
                if (!sl.hasChunk(x >> 4, z >> 4)) {
                    if (s.skySkip < 0 || ring < s.skySkip) s.skySkip = ring;
                    continue;
                }
                int n = column(sl, s, x, z, from, t);
                placed += n;
                if (fx && n > 0 && from == 0) formFx(sl, s, x, z);
            }
            if (s.skyIdx >= count) {
                s.skyRing++;
                s.skyIdx = 0;
                if (ring == CORE_RING && s.skyLand <= 0) coreReady(sl, s);
            }
        }
        if (s.skyRing > end) passDone(sl, s);
        return placed;
    }

    /** Uma coluna, de baixo para cima (areia nunca fica no ar), só as camadas {@code from..t-1} a partir do topo. */
    static int column(ServerLevel sl, Settlement s, int x, int z, int from, int t) {
        int top = topY(s), placed = 0;
        Palette pal = palette(s.style);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        boolean supported = false;
        for (int k = t - 1; k >= from; k--) {
            int y = top - k;
            if (y < sl.getMinBuildHeight()) continue;
            BlockState cur = sl.getBlockState(m.set(x, y, z));
            // só ar, fluido e mato solto: construção do jogador (ou outra ilha) é contornada
            if (!(cur.isAir() || cur.canBeReplaced())) {
                supported = cur.isSolid();
                continue;
            }
            sl.setBlock(m, layer(pal, k, t, x, y, z, supported), 2 | 16);
            supported = true;
            placed++;
        }
        return placed;
    }

    /** "A terra se forma": névoa e som discretos, de vez em quando, só para quem está perto. */
    static void formFx(ServerLevel sl, Settlement s, int x, int z) {
        // ~20 colunas novas por tick: uma névoa a cada ~30 colunas e um som a cada ~200 (dois ou três por segundo, baixos)
        int r = sl.random.nextInt(400);
        if (r < 13) sl.sendParticles(ParticleTypes.CLOUD, x + 0.5, topY(s) + 1.3, z + 0.5, 3, 0.4, 0.15, 0.4, 0.01);
        else if (r < 15) sl.playSound(null, x + 0.5, topY(s) + 1, z + 0.5, SoundEvents.ROOTED_DIRT_PLACE, SoundSource.BLOCKS, 0.8F, 0.7F);
    }

    /** O miolo da praça existe: o fundador é levado para lá e os primeiros construtores chegam. */
    static void coreReady(ServerLevel sl, Settlement s) {
        lift(sl, s);
        SettlementService.populate(sl, s);
    }

    /**
     * Leva o fundador do chão para a praça (uma vez). Se ele saiu do jogo nesses segundos, fica para quando voltar; se
     * já subiu sozinho, morreu ou foi para longe, a carona é cancelada.
     */
    private static void lift(ServerLevel sl, Settlement s) {
        if (!s.skyLift) return;
        ServerPlayer sp = s.owner == null ? null : sl.getServer().getPlayerList().getPlayer(s.owner);
        if (sp == null && s.owner != null) return;
        s.skyLift = false;
        if (sp == null || !sp.isAlive() || sp.isSpectator() || sp.level() != sl) return;
        if (Math.abs(sp.getX() - s.heart.getX()) >= 160 || Math.abs(sp.getZ() - s.heart.getZ()) >= 160) return;
        if (sp.getY() >= s.heart.getY() - 8) return;
        sp.stopRiding();
        sp.teleportTo(sl, s.heart.getX() + 2.5, topY(s) + 1, s.heart.getZ() + 2.5, sp.getYRot(), sp.getXRot());
        sp.resetFallDistance();
        sl.sendParticles(ParticleTypes.CLOUD, sp.getX(), sp.getY() + 0.5, sp.getZ(), 24, 0.6, 0.3, 0.6, 0.02);
        sl.playSound(null, sp.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.0F);
        sp.displayClientMessage(SettlementsLang.tr("sky.lifted", SettlementService.cityName(sl.getServer(), s)).withColor(s.style.color), true);
    }

    /** A passada chegou ao último anel: ou a terra está completa, ou faltam colunas em chunk descarregado. */
    private static void passDone(ServerLevel sl, Settlement s) {
        if (s.skySkip >= 0) {
            s.skyRing = s.skySkip;
            s.skyIdx = 0;
            s.skySkip = -1;
            s.skyWait = RETRY_WAIT;
            return;
        }
        int old = s.skyLand;
        s.skyLand = s.skyGoal;
        s.skyGoal = 0;
        s.skyRing = s.skyIdx = s.skyStart = 0;
        MinecraftServer server = sl.getServer();
        refreshShades(server);
        Component name = SettlementService.cityName(server, s);
        for (ServerPlayer g : CityEconomy.governors(server, s)) {
            g.displayClientMessage(SettlementsLang.tr(old > 0 ? "sky.grown" : "sky.formed", name).withColor(s.style.color), true);
        }
        CityPace.diary(server, s, SettlementsLang.tr(old > 0 ? "sky.diary.grown" : "sky.diary.formed"));
        sl.playSound(null, s.heart, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.5F, 0.8F);
        fire(sl, s, old, s.skyLand, true);
        SettlementService.refreshViewers(server, s);
    }

    /**
     * Termina a terra AGORA (comandos de teste e {@code /mahou city finish}): sem orçamento, mas ainda só em chunk
     * carregado. Devolve os blocos colocados; se {@link #generating} continuar true, faltaram colunas descarregadas.
     */
    public static int finishNow(ServerLevel sl, Settlement s) {
        if (!s.sky) return 0;
        if (s.isles) return SkyIsles.finishNow(sl, s);
        if (s.skyGoal <= 0) {
            if (s.radius <= s.skyLand) return 0;
            start(sl, s, s.radius);
        } else if (s.radius > s.skyGoal) {
            radiusChanged(sl, s);
        }
        if (s.skyGoal <= 0) return 0;
        s.skyWait = 0;
        // uma passada inteira; se sobrar coluna descarregada o cursor volta para ela e espera
        return run(sl, s, Integer.MAX_VALUE, Integer.MAX_VALUE, false);
    }

    /**
     * Refaz a passada do raio atual desde o coração (teste): completa colunas que faltam — inclusive buracos cavados.
     */
    public static void regenerate(ServerLevel sl, Settlement s) {
        if (!s.sky) return;
        if (s.isles) {
            SkyIsles.regenerate(sl, s);
            return;
        }
        int goal = Math.max(s.radius, shapeRadius(s));
        s.skyLand = 0;
        s.skyGoal = goal;
        s.skyRing = s.skyIdx = s.skyStart = 0;
        s.skySkip = -1;
        s.skyWait = 0;
        // o miolo já existe: ninguém é levado de novo para a praça
        s.skyLift = false;
    }

    // =====================================================================================
    //  Sombra: sem monstro natural na superfície debaixo da ilha
    // =====================================================================================
    private record Shade(ResourceKey<Level> dim, BlockPos heart, int radius) {}

    /** Ilhas do servidor (lista pequena, trocada inteira: o evento de spawn só lê). */
    private static volatile Shade[] shades = new Shade[0];

    /** Refaz a lista das ilhas (a cada poucos segundos e quando uma ilha nasce ou cresce). */
    public static void refreshShades(MinecraftServer server) {
        List<Shade> out = new ArrayList<>();
        for (Settlement s : SettlementData.get(server).all()) {
            // onda 9 (isles): as ilhas do arquipélago são pequenas e altas — a sombra delas não vira fábrica de monstros
            if (s.sky && !s.isles && shapeRadius(s) > 0) out.add(new Shade(s.dim, s.heart, shapeRadius(s)));
        }
        shades = out.toArray(new Shade[0]);
    }

    public static void clear() {
        shades = new Shade[0];
        SkyIsles.clear();
    }

    /**
     * Uma ilha grande escurece o chão: sem isto, debaixo dela nasceria monstro em pleno dia. Barra só o spawn NATURAL
     * de monstros em céu aberto sob a pegada (16 blocos livres acima = superfície; caverna e porão seguem normais).
     */
    public static void onSpawnCheck(MobSpawnEvent.PositionCheck e) {
        Shade[] list = shades;
        if (list.length == 0 || e.getSpawnType() != MobSpawnType.NATURAL) return;
        if (e.getEntity().getType().getCategory() != MobCategory.MONSTER) return;
        ServerLevel sl = e.getLevel().getLevel();
        BlockPos pos = e.getEntity().blockPosition();
        for (Shade sh : list) {
            if (sh.dim() != sl.dimension() || pos.getY() >= sh.heart().getY() - MAX_THICK) continue;
            int dx = pos.getX() - sh.heart().getX(), dz = pos.getZ() - sh.heart().getZ();
            int r = reach(sh.radius());
            if (Math.abs(dx) > r || Math.abs(dz) > r || !inside(sh.heart(), sh.radius(), dx, dz)) continue;
            if (openAbove(sl, pos)) e.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
            return;
        }
    }

    /** Nada sólido nos 16 blocos acima (copa de árvore não conta como teto). */
    private static boolean openAbove(ServerLevel sl, BlockPos pos) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int y = pos.getY() + 2; y <= pos.getY() + 17; y++) {
            BlockState st = sl.getBlockState(m.set(pos.getX(), y, pos.getZ()));
            if (st.isSolid() && !st.is(BlockTags.LEAVES) && !st.is(BlockTags.LOGS)) return false;
        }
        return true;
    }

    // =====================================================================================
    //  Tela e comandos
    // =====================================================================================
    /**
     * Estado da ilha para o cliente (dentro do NBT da cidade): {@code Sky}, {@code SkyY} (altitude do Coração),
     * {@code SkyPct} (0..100) e os números da geração ({@code SkyLand}, {@code SkyGoal}, {@code SkyRing},
     * {@code SkySkip}) — com eles e a posição do Coração o cliente pode repetir as contas de {@link #inside}.
     */
    static void view(CompoundTag t, Settlement s) {
        if (!s.sky) return;
        t.putBoolean("Sky", true);
        t.putInt("SkyY", s.heart.getY());
        t.putInt("SkyPct", percent(s));
        // onda 9 (isles): o cliente não repete a conta da forma do arquipélago (sem SkyGoal, o painel não pinta lotes)
        if (s.isles) {
            t.putBoolean("Isles", true);
            return;
        }
        t.putInt("SkyLand", s.skyLand);
        t.putInt("SkyGoal", s.skyGoal);
        t.putInt("SkyRing", doneRing(s));
        t.putInt("SkySkip", s.skySkip);
    }

    /** Linha de estado para {@code /mahou city sky}. */
    static Component debug(MinecraftServer server, Settlement s) {
        Component name = SettlementService.cityName(server, s);
        if (!s.sky) return SettlementsLang.tr("cmd.sky.ground", name);
        if (s.isles) return SkyIsles.debug(server, s);
        if (s.skyGoal <= 0) return SettlementsLang.tr("cmd.sky.done", name, s.heart.getY(), s.skyLand, reach(Math.max(1, s.skyLand)));
        return SettlementsLang.tr("cmd.sky.state", name, s.heart.getY(), s.skyLand, s.skyGoal, s.skyRing, reach(s.skyGoal), percent(s),
                s.skySkip >= 0 ? s.skySkip : "-");
    }

    private SkyCity() {}
}
