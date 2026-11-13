package com.renanpontes.isekaimahou.feature.magic_biomes;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.module.Module;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * Módulo "magic_biomes" (1.0): biomas mágicos raros, só em terreno NOVO, sem TerraBlender.
 * <ul>
 *   <li>Overworld: {@code crystal_forest} (Floresta de Cristal), {@code haunted_swamp} (Pântano Assombrado),
 *       {@code twilight_grove} (Bosque Crepuscular) e, no subterrâneo, {@code crystal_cavern} (Caverna de Cristal
 *       Mágico, iluminada pelos próprios cristais).</li>
 *   <li>Nether: {@code ember_crystal_wastes} (Ermo de Cristal Ígneo). End: {@code astral_gardens} (Jardins Astrais).</li>
 *   <li>Onde cada um entra: {@link MagicBiomes} (chamado pelos mixins {@code OverworldBiomeBuilderFlatMixin},
 *       {@code MultiNoiseBiomeSourceParameterListMixin} e {@code TheEndBiomeSourceMixin}).</li>
 *   <li>Blocos: {@link MagicBiomesContent}. Dicas de primeira visita + ar carregado: {@link MagicBiomesEvents}.</li>
 *   <li>Dados (biomas, features, tags, loot, texturas, diário, traduções): {@code tools/gen/magic_biomes.py}.</li>
 * </ul>
 */
public class MagicBiomesModule implements Module {
    @Override
    public String id() {
        return "magic_biomes";
    }

    @Override
    public void init(IEventBus modBus) {
        MagicBiomesContent.register(modBus);
        modBus.addListener(MagicBiomesModule::creativeTabs);
        NeoForge.EVENT_BUS.addListener(MagicBiomesEvents::onPlayerTick);
        MagicBiomesCommands.init();
    }

    private static void creativeTabs(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CoreRegistries.TAB_WORLD.getKey()) {
            MagicBiomesContent.BLOCK_ITEMS.forEach(i -> e.accept(i.get()));
        }
    }
}
