package com.renanpontes.isekaimahou.feature.structures_c;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
import net.neoforged.neoforge.event.village.WandererTradesEvent;

/**
 * Mapas de explorador: o cartógrafo (nível Aprendiz) e o mercador ambulante vendem o "Mapa da Guilda", para o
 * jogador achar cedo a Vila da Guilda dos Aventureiros; o cartógrafo Especialista vende o Mapa do Labirinto.
 */
public final class StructureMapTrades {
    public static final TagKey<Structure> ON_GUILD_MAPS = TagKey.create(Registries.STRUCTURE, IsekaiMahou.id("on_guild_maps"));
    public static final TagKey<Structure> ON_LABYRINTH_MAPS = TagKey.create(Registries.STRUCTURE, IsekaiMahou.id("on_labyrinth_maps"));

    public static void register() {
        NeoForge.EVENT_BUS.addListener(StructureMapTrades::onVillagerTrades);
        NeoForge.EVENT_BUS.addListener(StructureMapTrades::onWandererTrades);
    }

    private static void onVillagerTrades(VillagerTradesEvent event) {
        if (event.getType() != VillagerProfession.CARTOGRAPHER) return;
        event.getTrades().get(2).add(new VillagerTrades.TreasureMapForEmeralds(8, ON_GUILD_MAPS,
                "filled_map.isekai_mahou.guild_hall_village_map", MapDecorationTypes.PLAINS_VILLAGE, 12, 10));
        event.getTrades().get(4).add(new VillagerTrades.TreasureMapForEmeralds(16, ON_LABYRINTH_MAPS,
                "filled_map.isekai_mahou.minotaur_labyrinth_map", MapDecorationTypes.RED_X, 6, 20));
    }

    private static void onWandererTrades(WandererTradesEvent event) {
        event.getGenericTrades().add(new VillagerTrades.TreasureMapForEmeralds(6, ON_GUILD_MAPS,
                "filled_map.isekai_mahou.guild_hall_village_map", MapDecorationTypes.PLAINS_VILLAGE, 4, 1));
    }

    private StructureMapTrades() {}
}
