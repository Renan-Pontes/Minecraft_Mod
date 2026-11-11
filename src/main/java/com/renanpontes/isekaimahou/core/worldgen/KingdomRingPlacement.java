package com.renanpontes.isekaimahou.core.worldgen;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import com.mojang.datafixers.Products.P10;
import com.mojang.datafixers.Products.P5;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import net.neoforged.fml.util.ObfuscationReflectionHelper;

/**
 * Posicionamento "isekai_mahou:kingdom_ring" — a capital de um reino: UMA estrutura escolhida entre os pontos de um
 * {@code concentric_rings} do vanilla. Motivo: com {@code count: 1} todos os anéis do mundo usam o MESMO ângulo (o
 * ângulo vem só da semente do mundo), então Aldória, Sahra e Nordheim cairiam alinhadas na mesma direção a partir do
 * spawn; e a busca do bioma preferido (112 blocos) falha muitas vezes. Aqui o anel gera {@code count} candidatos em
 * volta do spawn (ângulos 360°/spread) e a capital usa o primeiro da lista {@code candidates} em que o vanilla ACHOU um
 * bioma preferido (o ponto saiu do lugar sorteado); se nenhum achou, o primeiro da lista cujo ponto cai em terra que a
 * estrutura aceita (bioma da tag {@code biomes} da estrutura no centro e na maioria de 4 pontos a 48 blocos — assim o
 * reino não some por ter caído no mar); se nem isso, o primeiro da lista.
 * <pre>
 * "placement": {"type": "isekai_mahou:kingdom_ring", "distance": 30, "spread": 6, "count": 6, "salt": 1946300803,
 *               "preferred_biomes": ["minecraft:desert", ...], "candidates": [2, 1, 3]}
 * </pre>
 * Com spread = count = 6, o candidato k fica no ângulo (ângulo do mundo + k·60°) e Aldória (concentric_rings de 1)
 * fica no k = 0: listas de candidatos sem índices em comum põem cada reino numa direção diferente. Distância do
 * centro: 4·distance ± 1,25·distance chunks. O {@code /locate} funciona (é um concentric_rings para o jogo).
 */
@SuppressWarnings("deprecation") // ExclusionZone (a zona única do vanilla) continua aceita por compatibilidade
public class KingdomRingPlacement extends ConcentricRingsStructurePlacement {
    public static final MapCodec<KingdomRingPlacement> CODEC = RecordCodecBuilder.<KingdomRingPlacement>mapCodec(i -> {
        P5<RecordCodecBuilder.Mu<KingdomRingPlacement>, Vec3i, FrequencyReductionMethod, Float, Integer,
                Optional<ExclusionZone>> base = placementCodec(i);
        return new P10<>(base.t1(), base.t2(), base.t3(), base.t4(), base.t5(),
                Codec.intRange(0, 1023).fieldOf("distance").forGetter(KingdomRingPlacement::distance),
                Codec.intRange(1, 1023).fieldOf("spread").forGetter(KingdomRingPlacement::spread),
                Codec.intRange(1, 64).fieldOf("count").forGetter(KingdomRingPlacement::count),
                RegistryCodecs.homogeneousList(Registries.BIOME).fieldOf("preferred_biomes")
                        .forGetter(KingdomRingPlacement::preferredBiomes),
                Codec.intRange(0, 63).listOf().optionalFieldOf("candidates", List.of(0))
                        .forGetter(KingdomRingPlacement::candidates))
                .apply(i, KingdomRingPlacement::new);
    }).validate(KingdomRingPlacement::validate);

    private final List<Integer> candidates;
    /** Escolha já feita para a lista de posições de um estado (a lista do vanilla é sempre o mesmo objeto). */
    private volatile Choice choice;

    private record Choice(List<ChunkPos> ring, ChunkPos pos) {}

    public KingdomRingPlacement(Vec3i locateOffset, FrequencyReductionMethod frequencyReductionMethod, float frequency,
                                int salt, Optional<ExclusionZone> exclusionZone, int distance, int spread, int count,
                                HolderSet<Biome> preferredBiomes, List<Integer> candidates) {
        super(locateOffset, frequencyReductionMethod, frequency, salt, exclusionZone, distance, spread, count,
                preferredBiomes);
        this.candidates = List.copyOf(candidates.isEmpty() ? List.of(0) : candidates);
    }

    private static DataResult<KingdomRingPlacement> validate(KingdomRingPlacement p) {
        for (int c : p.candidates) {
            if (c >= p.count()) return DataResult.error(() -> "kingdom_ring: candidate " + c + " >= count " + p.count());
        }
        return DataResult.success(p);
    }

    public List<Integer> candidates() {
        return candidates;
    }

    @Override
    protected boolean isPlacementChunk(ChunkGeneratorStructureState state, int x, int z) {
        ChunkPos pos = chosen(state);
        return pos != null && pos.x == x && pos.z == z;
    }

    /** O chunk de início da capital neste estado (null se o conjunto não existe nele, ex.: outra dimensão). */
    @Nullable
    public ChunkPos chosen(ChunkGeneratorStructureState state) {
        List<ChunkPos> ring = state.getRingPositionsFor(this);
        if (ring == null || ring.isEmpty()) return null;
        Choice c = choice;
        if (c != null && c.ring() == ring) return c.pos();
        ChunkPos pos = pick(state, ring, rawPositions(state.getLevelSeed()));
        choice = new Choice(ring, pos);
        return pos;
    }

    private ChunkPos pick(ChunkGeneratorStructureState state, List<ChunkPos> ring, List<ChunkPos> raw) {
        // 1) A busca do vanilla achou o bioma do reino perto deste candidato.
        for (int idx : candidates) {
            if (idx < ring.size() && idx < raw.size() && !ring.get(idx).equals(raw.get(idx))) return ring.get(idx);
        }
        // 2) Nenhum achou: o primeiro candidato em terra que a estrutura aceita (não deixa o reino cair no mar).
        BiomeSource source = biomeSource(state);
        HolderSet<Biome> allowed = structureBiomes(state);
        if (source != null && allowed != null) {
            for (int idx : candidates) {
                if (idx < ring.size() && acceptsCity(state, source, allowed, ring.get(idx))) return ring.get(idx);
            }
        }
        // 3) Reserva: o primeiro da lista (comportamento antigo).
        return ring.get(Math.min(candidates.get(0), ring.size() - 1));
    }

    /** Distância (blocos) dos 4 pontos extras medidos em volta do candidato. */
    private static final int LAND_PROBE = 48;
    private static final int PROBE_Y = QuartPos.fromBlock(64);

    /** Centro em bioma aceito e pelo menos 3 dos 4 pontos em volta também (área de capital, ~200 blocos). */
    private static boolean acceptsCity(ChunkGeneratorStructureState state, BiomeSource source, HolderSet<Biome> allowed,
                                       ChunkPos pos) {
        int x = pos.getMiddleBlockX();
        int z = pos.getMiddleBlockZ();
        if (!allowed.contains(biomeAt(state, source, x, z))) return false;
        int ok = 0;
        int[][] around = {{LAND_PROBE, 0}, {-LAND_PROBE, 0}, {0, LAND_PROBE}, {0, -LAND_PROBE}};
        for (int[] d : around) {
            if (allowed.contains(biomeAt(state, source, x + d[0], z + d[1]))) ok++;
        }
        return ok >= 3;
    }

    private static Holder<Biome> biomeAt(ChunkGeneratorStructureState state, BiomeSource source, int x, int z) {
        return source.getNoiseBiome(QuartPos.fromBlock(x), PROBE_Y, QuartPos.fromBlock(z), state.randomState().sampler());
    }

    /** Biomas aceitos pela(s) estrutura(s) do conjunto que usa ESTE posicionamento (null se não achar). */
    @Nullable
    private HolderSet<Biome> structureBiomes(ChunkGeneratorStructureState state) {
        for (Holder<StructureSet> holder : state.possibleStructureSets()) {
            if (!holder.isBound() || holder.value().placement() != this) continue;
            List<StructureSet.StructureSelectionEntry> entries = holder.value().structures();
            if (entries.isEmpty() || !entries.get(0).structure().isBound()) return null;
            return entries.get(0).structure().value().biomes();
        }
        return null;
    }

    /** O BiomeSource do estado (campo privado no vanilla; nomes Mojang em runtime no NeoForge 1.21.1). */
    @Nullable
    private static BiomeSource biomeSource(ChunkGeneratorStructureState state) {
        if (BIOME_SOURCE == null) return null;
        try {
            return (BiomeSource) BIOME_SOURCE.get(state);
        } catch (Throwable t) {
            return null;
        }
    }

    @Nullable
    private static final Field BIOME_SOURCE = findBiomeSourceField();

    @Nullable
    private static Field findBiomeSourceField() {
        try {
            return ObfuscationReflectionHelper.findField(ChunkGeneratorStructureState.class, "biomeSource");
        } catch (Throwable t) {
            IsekaiMahou.LOGGER.warn("kingdom_ring: sem acesso ao BiomeSource ({}); capitais usam só a busca do vanilla",
                    t.toString());
            return null;
        }
    }

    /**
     * Os pontos SORTEADOS do anel antes da busca de bioma — mesma conta e mesma sequência aleatória de
     * {@code ChunkGeneratorStructureState.generateRingPositions} (que semeia com a semente do mundo e consome um
     * nextDouble por posição + um nextLong do fork da busca).
     */
    List<ChunkPos> rawPositions(long seed) {
        int distance = distance();
        int count = count();
        int spread = spread();
        LegacyRandomSource random = new LegacyRandomSource(seed);
        double angle = random.nextDouble() * Math.PI * 2.0;
        int inRing = 0;
        int ring = 0;
        List<ChunkPos> out = new ArrayList<>(count);
        for (int j = 0; j < count; j++) {
            double dist = (double) (4 * distance + distance * ring * 6) + (random.nextDouble() - 0.5) * (double) distance * 2.5;
            out.add(new ChunkPos((int) Math.round(Math.cos(angle) * dist), (int) Math.round(Math.sin(angle) * dist)));
            random.nextLong();
            angle += (Math.PI * 2) / (double) spread;
            if (++inRing == spread) {
                ring++;
                inRing = 0;
                spread += 2 * spread / (ring + 1);
                spread = Math.min(spread, count - j);
                angle += random.nextDouble() * Math.PI * 2.0;
            }
        }
        return out;
    }

    @Override
    public StructurePlacementType<?> type() {
        return WorldgenCore.KINGDOM_RING.get();
    }
}
