package com.renanpontes.isekaimahou.core.worldgen;

import java.util.List;
import java.util.Optional;

import com.mojang.datafixers.Products.P5;
import com.mojang.datafixers.Products.P9;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;

/**
 * Posicionamento "isekai_mahou:random_spread_multi": o {@code random_spread} do vanilla (mesma grade, mesmo sal, mesmo
 * {@code /locate}) com VÁRIAS zonas de exclusão — o vanilla só aceita uma ({@code exclusion_zone}). Uma estrutura não
 * nasce se algum dos conjuntos listados tiver um chunk de início a até {@code chunk_count} chunks (Chebyshev) dela.
 * <pre>
 * "placement": {
 *   "type": "isekai_mahou:random_spread_multi", "spacing": 40, "separation": 12, "salt": 1946300104,
 *   "exclusion_zones": [
 *     {"other_set": "minecraft:villages", "chunk_count": 4},
 *     {"other_set": "isekai_mahou:guild_city", "chunk_count": 9},
 *     {"other_set": "isekai_mahou:capital_city", "chunk_count": 12}
 *   ]
 * }
 * </pre>
 * Os campos do vanilla continuam valendo (inclusive o {@code exclusion_zone} antigo, somado às zonas novas).
 * Como no vanilla, as exclusões são de mão única: não faça dois conjuntos se excluírem mutuamente (um ciclo é cortado
 * pela proteção de profundidade de {@link StructureProximity} e vira "nada por perto").
 */
@SuppressWarnings("deprecation") // ExclusionZone (a zona única do vanilla) continua aceita por compatibilidade
public class RandomSpreadMultiPlacement extends RandomSpreadStructurePlacement {

    /** Uma zona: o conjunto vizinho e a distância mínima em chunks (1–32; o vanilla vai só até 16). */
    public record Zone(Holder<StructureSet> otherSet, int chunkCount) {
        public static final Codec<Zone> CODEC = RecordCodecBuilder.create(i -> i.group(
                RegistryFileCodec.create(Registries.STRUCTURE_SET, StructureSet.DIRECT_CODEC, false)
                        .fieldOf("other_set").forGetter(Zone::otherSet),
                Codec.intRange(1, 32).fieldOf("chunk_count").forGetter(Zone::chunkCount)
        ).apply(i, Zone::new));
    }

    public static final MapCodec<RandomSpreadMultiPlacement> CODEC = RecordCodecBuilder.<RandomSpreadMultiPlacement>mapCodec(i -> {
        P5<RecordCodecBuilder.Mu<RandomSpreadMultiPlacement>, Vec3i, FrequencyReductionMethod, Float, Integer,
                Optional<ExclusionZone>> base = placementCodec(i);
        return new P9<>(base.t1(), base.t2(), base.t3(), base.t4(), base.t5(),
                Codec.intRange(0, 4096).fieldOf("spacing").forGetter(RandomSpreadMultiPlacement::spacing),
                Codec.intRange(0, 4096).fieldOf("separation").forGetter(RandomSpreadMultiPlacement::separation),
                RandomSpreadType.CODEC.optionalFieldOf("spread_type", RandomSpreadType.LINEAR)
                        .forGetter(RandomSpreadMultiPlacement::spreadType),
                Zone.CODEC.listOf().optionalFieldOf("exclusion_zones", List.of())
                        .forGetter(RandomSpreadMultiPlacement::exclusionZones))
                .apply(i, RandomSpreadMultiPlacement::new);
    }).validate(RandomSpreadMultiPlacement::validate);

    private final List<Zone> zones;

    public RandomSpreadMultiPlacement(Vec3i locateOffset, FrequencyReductionMethod frequencyReductionMethod, float frequency,
                                      int salt, Optional<ExclusionZone> exclusionZone, int spacing, int separation,
                                      RandomSpreadType spreadType, List<Zone> zones) {
        super(locateOffset, frequencyReductionMethod, frequency, salt, exclusionZone, spacing, separation, spreadType);
        this.zones = List.copyOf(zones);
    }

    private static DataResult<RandomSpreadMultiPlacement> validate(RandomSpreadMultiPlacement p) {
        return p.spacing() <= p.separation()
                ? DataResult.error(() -> "Spacing has to be larger than separation")
                : DataResult.success(p);
    }

    /** As zonas novas (sem a {@code exclusion_zone} antiga do vanilla). */
    public List<Zone> exclusionZones() {
        return zones;
    }

    /**
     * Chamado por {@code StructurePlacement.isStructureChunk} depois de o chunk já ser o sorteado da região: aplica a
     * zona antiga do vanilla e depois todas as novas.
     */
    @Override
    public boolean applyInteractionsWithOtherStructures(ChunkGeneratorStructureState state, int x, int z) {
        if (!super.applyInteractionsWithOtherStructures(state, x, z)) return false;
        for (Zone zone : zones) {
            if (StructureProximity.hasStructureNear(state, zone.otherSet(), x, z, zone.chunkCount())) return false;
        }
        return true;
    }

    @Override
    public StructurePlacementType<?> type() {
        return WorldgenCore.RANDOM_SPREAD_MULTI.get();
    }
}
