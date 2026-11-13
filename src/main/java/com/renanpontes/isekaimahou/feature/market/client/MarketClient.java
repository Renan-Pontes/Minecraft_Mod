package com.renanpontes.isekaimahou.feature.market.client;

import com.renanpontes.isekaimahou.client.menu.MenuReveal;
import com.renanpontes.isekaimahou.client.menu.MenuTabs;
import com.renanpontes.isekaimahou.client.menu.StatusScreen;
import com.renanpontes.isekaimahou.feature.market.MarketNet;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Parte de cliente do módulo "market": a aba "Mercado" do menu B (categoria Social; ícone
 * {@code textures/gui/tab/market.png}) e a cópia dos dados que o servidor manda.
 */
public class MarketClient implements ClientModule {
    public static final String TAB = "market";

    @Override
    public void initClient(IEventBus modBus) {
        MarketNet.onListings = MarketClientState::onListings;
        MarketNet.onBox = MarketClientState::onBox;
        MarketNet.onResult = MarketClientState::onResult;
        MarketNet.onOpen = () -> {
            if (Minecraft.getInstance().player != null) StatusScreen.open(TAB);
        };
        MenuTabs.register(TAB, 61, Component.translatable("market.isekai_mahou.ui.tab"), MarketTab::new);
        MenuTabs.setCategory(TAB, MenuTabs.Category.SOCIAL);
        // Menu enxuto no começo: a aba aparece no nível 5 (abrir pelo Quadro do Mercado/da Guilda libera na hora)
        MenuReveal.addRule(TAB, d -> d.level() >= 5);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> MarketClientState.reset());
    }
}
