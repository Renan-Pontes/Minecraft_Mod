package com.renanpontes.isekaimahou.core.worldgen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

/**
 * Impede que estruturas de SUPERFÍCIE do vanilla (vilas, postos de saqueadores, ruínas, cabanas, templos...) nasçam
 * dentro ou colados nas cidades e capitais do mod. Usado pelo mixin em {@code StructurePlacement#isStructureChunk}.
 * Seguro: só age num estado de geração que TEM algum conjunto de cidade (o Overworld) e só sobre os conjuntos da
 * lista; o resto do jogo (outras dimensões, outros mods) não muda.
 */
public final class CityExclusion {
    /** Conjuntos do vanilla barrados perto das cidades. */
    private static final Set<ResourceLocation> GUARDED = Set.of(
            mc("villages"), mc("pillager_outposts"), mc("desert_pyramids"), mc("jungle_temples"), mc("igloos"),
            mc("swamp_huts"), mc("woodland_mansions"), mc("ruined_portals"), mc("trail_ruins"), mc("shipwrecks"),
            mc("ocean_ruins"));
    /** Distância mínima (chunks) do chunk de início de cada conjunto de cidade. Pior caso, em blocos a partir do canto
     *  do chunk de início da cidade: a cidade vai até 8 + 24 (deslocamento do centro) + 76 (raio com a margem) = 108, a
     *  capital até 8 + 24 + 103 = 135; uma vila/posto do vanilla (jigsaw) começa no canto do seu chunk e chega a 80
     *  blocos dele. Com 12 chunks a vila mais próxima começa em 13·16 − 80 = 128 &gt; 108; com 14, 15·16 − 80 = 160 &gt; 135:
     *  a vila inteira fica do lado de fora. */
    public static final int CITY_RANGE = 12;
    public static final int CAPITAL_RANGE = 14;
    private static final Map<ResourceLocation, Integer> CITY_SETS = Map.of(
            IsekaiMahou.id("guild_city"), CITY_RANGE,
            IsekaiMahou.id("capital_city"), CAPITAL_RANGE,
            IsekaiMahou.id("capital_sahra"), CAPITAL_RANGE,
            IsekaiMahou.id("capital_nordheim"), CAPITAL_RANGE);

    private record CitySet(Holder<StructureSet> set, int range) {}

    /** O que interessa de um estado: as colocações vigiadas (por identidade) e os conjuntos de cidade presentes. */
    private record Info(Set<StructurePlacement> guarded, List<CitySet> cities) {
        static final Info NONE = new Info(Set.of(), List.of());
    }

    /** Um por nível (o estado vive enquanto o nível existir); fraco para não segurar mundos fechados. */
    private static final Map<ChunkGeneratorStructureState, Info> CACHE = Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * Verdadeiro quando {@code placement} é de um conjunto vanilla vigiado e há uma cidade/capital perto do chunk
     * (x, z). Chamado só quando o chunk já ia receber a estrutura (caso raro), então o custo é desprezível.
     */
    public static boolean blocksVanilla(StructurePlacement placement, ChunkGeneratorStructureState state, int x, int z) {
        Info info = CACHE.computeIfAbsent(state, CityExclusion::scan);
        if (info.cities().isEmpty() || !info.guarded().contains(placement)) return false;
        for (CitySet city : info.cities()) {
            if (StructureProximity.hasStructureNear(state, city.set(), x, z, city.range())) return true;
        }
        return false;
    }

    private static Info scan(ChunkGeneratorStructureState state) {
        Set<StructurePlacement> guarded = Collections.newSetFromMap(new IdentityHashMap<>());
        List<CitySet> cities = new ArrayList<>();
        for (Holder<StructureSet> holder : state.possibleStructureSets()) {
            ResourceLocation id = holder.unwrapKey().map(ResourceKey::location).orElse(null);
            if (id == null || !holder.isBound()) continue;
            if (GUARDED.contains(id)) guarded.add(holder.value().placement());
            Integer range = CITY_SETS.get(id);
            if (range != null) cities.add(new CitySet(holder, range));
        }
        if (cities.isEmpty() || guarded.isEmpty()) return Info.NONE;
        return new Info(Collections.unmodifiableSet(guarded), List.copyOf(cities));
    }

    private static ResourceLocation mc(String path) {
        return ResourceLocation.withDefaultNamespace(path);
    }

    private CityExclusion() {}
}
