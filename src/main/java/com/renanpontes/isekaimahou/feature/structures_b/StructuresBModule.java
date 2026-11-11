package com.renanpontes.isekaimahou.feature.structures_b;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.structures_b.world.StructureDiscovery;
import com.renanpontes.isekaimahou.module.Module;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * Modulo "structures_b": estruturas de classe (Torre do Mago Ancião, Cabana da Bruxa Anciã, Altar Profano,
 * Círculo de Pedras Druídico, Cripta Esquecida) e pequenas (Ruínas Arcanas, Acampamento do Mago Errante, Lago das Fadas).
 *
 * As estruturas em si são dados (worldgen jigsaw + .nbt gerados por tools/gen/structures_b.py). Aqui ficam:
 * blocos próprios das estruturas, funções de loot tolerantes a módulos ausentes e a "descoberta" de locais
 * (título na tela, XP e contadores {@code explore.<estrutura>} para Diário/Guilda).
 */
public class StructuresBModule implements Module {
    @Override
    public String id() {
        return "structures_b";
    }

    @Override
    public void init(IEventBus modBus) {
        StructuresBContent.register(modBus);
        modBus.addListener(this::onCreativeTabs);
        NeoForge.EVENT_BUS.addListener(StructureDiscovery::onPlayerTick);
    }

    private void onCreativeTabs(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CoreRegistries.TAB_WORLD.getKey()) {
            e.accept(StructuresBContent.FAIRY_GLOWBLOOM_ITEM.get());
            e.accept(StructuresBContent.CRYPT_SARCOPHAGUS_ITEM.get());
        }
    }
}
