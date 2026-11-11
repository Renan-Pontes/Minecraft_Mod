package com.renanpontes.isekaimahou.feature.ritual_mage.client;

import com.renanpontes.isekaimahou.feature.ritual_mage.MageClientBridge;
import com.renanpontes.isekaimahou.feature.ritual_mage.MageRegistries;
import com.renanpontes.isekaimahou.feature.ritual_mage.item.ResearchNoteItem;
import com.renanpontes.isekaimahou.feature.ritual_mage.network.ClientResearch;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Parte de cliente do Ritual do Mago: telas da Mesa e do Atlas e a cor das Notas de Pesquisa. */
public class RitualMageClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener((RegisterMenuScreensEvent e) -> e.register(MageRegistries.RESEARCH_TABLE_MENU.get(), ResearchTableScreen::new));
        // A runa desenhada na nota (camada 1) toma a cor do elemento
        modBus.addListener((RegisterColorHandlersEvent.Item e) -> e.register(
                (stack, tint) -> tint == 1 ? ResearchNoteItem.elementOf(stack).argb() : -1, MageRegistries.RESEARCH_NOTE.get()));
        MageClientBridge.openAtlas = MageAtlasScreen::open;
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> ClientResearch.reset());
    }
}
