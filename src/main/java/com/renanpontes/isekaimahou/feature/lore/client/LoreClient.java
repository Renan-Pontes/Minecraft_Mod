package com.renanpontes.isekaimahou.feature.lore.client;

import com.renanpontes.isekaimahou.client.menu.ContainerTab;
import com.renanpontes.isekaimahou.client.menu.MenuTabs;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Parte de cliente do módulo "lore": a seção <b>Enciclopédia</b> dentro do Guia de Viagem do menu B (ao lado do Diário,
 * de Lugares e do Bestiário — nenhuma aba nova na barra), a página de leitura dos fragmentos e a seta do rastreador
 * para o lugar revelado.
 */
public class LoreClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        MenuTabs.register(LoreTab.TAB_ID, 75, Component.translatable("screen.isekai_mahou.lore.tab"), LoreTab::new);
        MenuTabs.setParent(LoreTab.TAB_ID, ContainerTab.GUIDE);
        ClientLore.init();
        // Ao sair do mundo: esquece os fragmentos do servidor anterior
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> ClientLore.reset());
    }
}
