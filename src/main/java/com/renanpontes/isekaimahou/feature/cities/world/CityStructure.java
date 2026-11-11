package com.renanpontes.isekaimahou.feature.cities.world;

import java.util.Arrays;
import java.util.Optional;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.neoforged.neoforge.common.Tags;

/**
 * Tipo de estrutura "isekai_mahou:guild_city": uma cidade murada inteira (não é jigsaw — o jigsaw não passa de 128
 * blocos e não faz muralha). Mede o terreno numa grade sobre a área da cidade em alguns pontos perto do chunk
 * sorteado, escolhe o mais plano/seco, define a altura do piso (mediana do terreno), a paleta (pelo bioma do centro
 * ou pelo reino da capital), a rotação e os portões, e entrega a planta para {@link CityPlanner}.
 * <pre>{"type": "isekai_mahou:guild_city", "capital": false, "size": 2, "max_slope": 36, "max_water": 0.3,
 *  "kingdom": "sahra" (opcional), ...}</pre>
 * size = anéis de quarteirões em volta da praça (2 cidade, 3 capital); max_slope = desnível tolerado (blocos entre o
 * 10º e o 90º percentil das alturas); max_water = fração de pontos sobre água tolerada; kingdom = reino da capital
 * ({@link CityKingdom}: fixa a paleta — Aldória planície, Sahra deserto, Nordheim neve — em qualquer bioma; sem ele a
 * paleta sai do bioma e o reino da cidade é o da paleta).
 */
public class CityStructure extends Structure {
    public static final MapCodec<CityStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            settingsCodec(i),
            Codec.BOOL.optionalFieldOf("capital", false).forGetter(s -> s.capital),
            Codec.intRange(CityGeometry.MIN_RINGS, CityGeometry.MAX_RINGS).optionalFieldOf("size", CityGeometry.MIN_RINGS)
                    .forGetter(s -> s.size),
            Codec.intRange(0, 256).optionalFieldOf("max_slope", 18).forGetter(s -> s.maxSlope),
            Codec.floatRange(0F, 1F).optionalFieldOf("max_water", 0.15F).forGetter(s -> s.maxWater),
            CityKingdom.CODEC.optionalFieldOf("kingdom").forGetter(s -> s.kingdom)
    ).apply(i, CityStructure::new));

    /** Centros candidatos em volta do meio do chunk sorteado (do centro para fora). Mantém a cidade dentro do
     *  alcance de referência de estruturas (8 chunks a partir do chunk de início). */
    private static final int[][] SEARCH = {{0, 0}, {16, 0}, {-16, 0}, {0, 16}, {0, -16}, {24, 24}, {-24, 24},
            {24, -24}, {-24, -24}};
    /** Desnível (blocos) a partir do qual não vale a pena medir outros centros. */
    private static final int FLAT_ENOUGH = 6;
    /** Fração tolerada de pontos da área em bioma fora da tag (rio e praia não contam): cidade e capital. */
    static final float MAX_BAD_BIOME_CITY = 0.55F;
    static final float MAX_BAD_BIOME_CAPITAL = 0.7F;

    private final boolean capital;
    private final int size;
    private final int maxSlope;
    private final float maxWater;
    private final Optional<CityKingdom> kingdom;

    public CityStructure(StructureSettings settings, boolean capital, int size, int maxSlope, float maxWater,
                         Optional<CityKingdom> kingdom) {
        super(settings);
        this.capital = capital;
        this.size = size;
        this.maxSlope = maxSlope;
        this.maxWater = maxWater;
        this.kingdom = kingdom;
    }

    /** Assinatura da v0.8 (sem reino fixo). */
    public CityStructure(StructureSettings settings, boolean capital, int size, int maxSlope, float maxWater) {
        this(settings, capital, size, maxSlope, maxWater, Optional.empty());
    }

    /** Reino fixo desta estrutura (as capitais); vazio = cidade comum (reino pela paleta do bioma). */
    public Optional<CityKingdom> kingdom() {
        return kingdom;
    }

    /** Resultado da medição do terreno num centro candidato. */
    private record Survey(int x, int z, int floorY, int maxY, int spread, float water, float badBiome, float score,
                          Holder<Biome> biome) {}

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
        CityGeometry geo = new CityGeometry(size, capital);
        int sea = ctx.chunkGenerator().getSeaLevel();
        int mx = ctx.chunkPos().getMiddleBlockX();
        int mz = ctx.chunkPos().getMiddleBlockZ();
        float maxBadBiome = capital ? MAX_BAD_BIOME_CAPITAL : MAX_BAD_BIOME_CITY;
        // "/place structure" aceita qualquer bioma: aí a cidade sai no melhor lugar medido, mesmo fora dos limites de
        // terreno (que só valem para a geração natural e para /locate).
        boolean forced = isForcedPlacement(ctx);
        Survey best = null;
        for (int[] off : SEARCH) {
            Survey s = survey(ctx, geo, mx + off[0], mz + off[1], sea, forced);
            if (s == null) continue;
            if (best == null || s.score() < best.score()) best = s;
            // Bem plano e seco: não gasta mais medições (a capital, tolerante, ainda assim procura o melhor lugar).
            if (best.spread() <= FLAT_ENOUGH && best.water() <= 0.05F) break;
        }
        if (best == null) return Optional.empty();
        if (!forced && (best.spread() > maxSlope || best.water() > maxWater || best.badBiome() > maxBadBiome)) {
            return Optional.empty();
        }
        int top = ctx.heightAccessor().getMaxBuildHeight() - 48;
        int floorY = Mth.clamp(Math.max(best.floorY(), sea), ctx.heightAccessor().getMinBuildHeight() + 16, top);
        // Limpa por cima até um pouco acima do ponto mais alto medido (morros dentro da cidade).
        int clearHeight = Mth.clamp(best.maxY() - floorY + 12, 28, 64);
        // Capital de reino: paleta do reino em qualquer bioma (o city_heart do molde diz o reino pela paleta).
        Holder<Biome> centerBiome = best.biome();
        CityPalette palette = kingdom.map(k -> k.palette).orElseGet(() -> CityPalette.forBiome(centerBiome));
        RandomSource random = ctx.random();
        long seed = random.nextLong();
        Rotation rotation = Rotation.getRandom(random);
        int gates = capital ? (CityGeometry.GATE_S | CityGeometry.GATE_E | CityGeometry.GATE_W) : randomGates(random);
        CityFrame frame = new CityFrame(best.x(), best.z(), floorY, rotation);
        return Optional.of(new GenerationStub(new BlockPos(best.x(), floorY + 1, best.z()),
                builder -> CityPlanner.build(builder, ctx.structureTemplateManager(), geo, frame, palette, gates,
                        clearHeight, seed)));
    }

    /** 2 a 4 portões: os dois de um eixo sempre; os outros dois com 60% cada. */
    private static int randomGates(RandomSource r) {
        boolean ns = r.nextBoolean();
        int gates = ns ? (CityGeometry.GATE_N | CityGeometry.GATE_S) : (CityGeometry.GATE_E | CityGeometry.GATE_W);
        int[] others = ns ? new int[] {CityGeometry.GATE_E, CityGeometry.GATE_W} : new int[] {CityGeometry.GATE_N, CityGeometry.GATE_S};
        for (int g : others) {
            if (r.nextFloat() < 0.6F) gates |= g;
        }
        return gates;
    }

    /**
     * Mede a altura do chão (ignorando a água) numa grade sobre toda a área da cidade. Água = chão abaixo do nível do
     * mar (rio, lago, oceano). Null se o centro estiver num bioma que não aceita a cidade ou se for mais água do que
     * terra.
     */
    @Nullable
    private Survey survey(GenerationContext ctx, CityGeometry geo, int cx, int cz, int sea, boolean forced) {
        Holder<Biome> center = biomeAt(ctx, cx, sea, cz);
        if (!ctx.validBiome().test(center)) return null;
        int n = geo.capital ? 9 : 7;
        int half = geo.towerOut;
        int total = n * n;
        int[] heights = new int[total];
        int dry = 0;
        int wet = 0;
        int bad = 0;
        int maxY = Integer.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                int x = cx - half + i * 2 * half / (n - 1);
                int z = cz - half + j * 2 * half / (n - 1);
                // Primeiro bloco livre acima do chão sólido (a água não conta como chão).
                int ground = ctx.chunkGenerator().getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG,
                        ctx.heightAccessor(), ctx.randomState()) - 1;
                if (ground < sea - 1) {
                    wet++;
                } else {
                    heights[dry++] = ground;
                    maxY = Math.max(maxY, ground);
                }
                // Rio e praia cortando a área não estragam a cidade (viram fundação/ponte): não contam como ruins.
                Holder<Biome> b = biomeAt(ctx, x, Math.max(ground, sea), z);
                if (!ctx.validBiome().test(b) && !b.is(Tags.Biomes.IS_RIVER) && !b.is(Tags.Biomes.IS_BEACH)) bad++;
            }
        }
        if (dry < total / 2 && !forced) return null;
        if (dry == 0) {
            // Só no /place em pleno mar: piso no nível do mar, tudo é fundação.
            return new Survey(cx, cz, sea, sea, 0, 1F, 0F, 40F, center);
        }
        Arrays.sort(heights, 0, dry);
        int p10 = heights[dry / 10];
        int p50 = heights[dry / 2];
        int p90 = heights[Math.min(dry - 1, dry * 9 / 10)];
        int spread = p90 - p10;
        float water = wet / (float) total;
        float badBiome = bad / (float) total;
        float score = spread + water * 40F + badBiome * 20F;
        return new Survey(cx, cz, p50, maxY, spread, water, badBiome, score, center);
    }

    /**
     * Verdadeiro quando quem pediu a estrutura aceita qualquer bioma (o comando /place passa "qualquer bioma"); na
     * geração natural e no /locate o filtro é o da tag has_structure, que nunca contém o bioma "the_void".
     */
    private static boolean isForcedPlacement(GenerationContext ctx) {
        return ctx.registryAccess().registry(Registries.BIOME)
                .flatMap(r -> r.getHolder(Biomes.THE_VOID))
                .map(h -> ctx.validBiome().test(h))
                .orElse(false);
    }

    private static Holder<Biome> biomeAt(GenerationContext ctx, int x, int y, int z) {
        return ctx.biomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z),
                ctx.randomState().sampler());
    }

    @Override
    public StructureType<?> type() {
        return CityWorldgen.GUILD_CITY.get();
    }
}
