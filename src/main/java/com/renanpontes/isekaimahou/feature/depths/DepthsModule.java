package com.renanpontes.isekaimahou.feature.depths;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementReactions;
import com.renanpontes.isekaimahou.feature.depths.puzzle.PuzzleBlocks;
import com.renanpontes.isekaimahou.module.Module;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * Módulo "depths" (v1.6) — motivo para descer ao subterrâneo:
 * <ul>
 *   <li><b>Masmorra em Camadas</b> ({@code depths_dungeon}): ruína na superfície, 4 andares cada vez mais difíceis
 *       (armadilhas, geradores, mímicos, loot melhor) e o ninho do <b>Verme-Rei das Profundezas</b> ({@code depths_worm_king});</li>
 *   <li>criaturas: <b>Verme Cavador</b> ({@code depths_burrower}) e <b>Explorador Perdido</b> ({@code depths_lost_delver});</li>
 *   <li><b>Geodos de Mana</b> gigantes (feature) com Cristal de Mana Germinante — fonte renovável de cristais;</li>
 *   <li>tesouros de anime: <b>Matadora de Dragões</b> e <b>Faca da Deusa</b>;</li>
 *   <li>v1.0: <b>quebra-cabeças</b> (pacote {@code puzzle}: Selo Rúnico + peças, usados também pela Cidadela, pela
 *       Biblioteca e pela Cidade Anã) e <b>afixos por andar</b> + cronômetro da descida ({@link DepthsAffixes}).</li>
 * </ul>
 * Assets/dados/estruturas: {@code tools/gen/depths.py}. Comandos: {@code /mahou depths ...}.
 */
public class DepthsModule implements Module {
    @Override
    public String id() {
        return "depths";
    }

    @Override
    public void init(IEventBus modBus) {
        DepthsRegistries.register(modBus);
        PuzzleBlocks.register(modBus);
        DepthsEvents.register();
        DepthsAffixes.register();
        DepthsCommands.register();
        com.renanpontes.isekaimahou.feature.depths.puzzle.enigma.EnigmaEvents.register();
        modBus.addListener(this::onCreativeTab);
        registerResistances();
    }

    private void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CoreRegistries.TAB_CREATURES.getKey()) {
            DepthsRegistries.eggs().forEach(egg -> event.accept(egg.get()));
        } else if (event.getTabKey() == CoreRegistries.TAB_WORLD.getKey()) {
            DepthsRegistries.worldItems().forEach(item -> event.accept(item.get()));
            PuzzleBlocks.items().forEach(item -> event.accept(item.get()));
        } else if (event.getTabKey() == CoreRegistries.TAB_MAGIC.getKey()) {
            DepthsRegistries.gearItems().forEach(item -> event.accept(item.get()));
        }
    }

    /** Vermes resistem à terra e sofrem com água/vento; o Explorador é um morto-vivo (luz fere). */
    private static void registerResistances() {
        for (String id : new String[] {"depths_worm_king", "depths_burrower"}) {
            ElementReactions.setMultiplier(IsekaiMahou.id(id), Element.EARTH, 0.5F);
            ElementReactions.setMultiplier(IsekaiMahou.id(id), Element.WATER, 1.25F);
            ElementReactions.setMultiplier(IsekaiMahou.id(id), Element.WIND, 1.25F);
        }
        ElementReactions.setMultiplier(IsekaiMahou.id("depths_lost_delver"), Element.LIGHT, 1.5F);
        ElementReactions.setMultiplier(IsekaiMahou.id("depths_lost_delver"), Element.SHADOW, 0.5F);
    }
}
