package com.renanpontes.isekaimahou.feature.cities.world;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Geração das Cidades (trabalho "city_build", docs/V08_DESIGN.md §5.1): o tipo de estrutura
 * "isekai_mahou:guild_city" (usado por worldgen/structure/guild_city.json e pelas 3 capitais: capital_city, capital_sahra,
 * capital_nordheim — v0.9) e as duas peças
 * ("city_ground", feita em código, e "city_building", os templates de structure/city/&lt;paleta&gt;/).
 * Tudo aqui é sem estado: as peças guardam no NBT do chunk o que precisam para serem recriadas.
 */
public final class CityWorldgen {
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, IsekaiMahou.MODID);
    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, IsekaiMahou.MODID);

    public static final DeferredHolder<StructureType<?>, StructureType<CityStructure>> GUILD_CITY =
            STRUCTURE_TYPES.register("guild_city", () -> () -> CityStructure.CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> GROUND_PIECE =
            STRUCTURE_PIECES.register("city_ground", () -> (StructurePieceType.ContextlessType) CityGroundPiece::new);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> BUILDING_PIECE =
            STRUCTURE_PIECES.register("city_building",
                    () -> (StructurePieceType.StructureTemplateType) CityBuildingPiece::new);

    public static void register(IEventBus modBus) {
        STRUCTURE_TYPES.register(modBus);
        STRUCTURE_PIECES.register(modBus);
    }

    private CityWorldgen() {}
}
