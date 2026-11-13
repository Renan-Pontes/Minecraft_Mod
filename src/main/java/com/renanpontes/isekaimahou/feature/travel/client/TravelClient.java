package com.renanpontes.isekaimahou.feature.travel.client;

import com.renanpontes.isekaimahou.client.menu.MenuTabs;
import com.renanpontes.isekaimahou.client.onboard.ClientTravel;
import com.renanpontes.isekaimahou.feature.travel.TravelContent;
import com.renanpontes.isekaimahou.feature.travel.guide.GuideNet;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Parte de cliente do módulo "travel": renderer animado da Pedra de Teleporte, a tela da pedra (aberta quando o servidor
 * manda a lista de uma viagem de pedra), a aba "Viagem" do menu B (Destinos + Guia de Portais), a seta do portal
 * rastreado ({@link GuideClient}) e os pontos automáticos no minimapa do Xaero ({@link XaeroMarkers}).
 */
public class TravelClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) ->
                e.registerBlockEntityRenderer(TravelContent.TRAVEL_STONE_BE.get(), TravelStoneRenderer::new));
        MenuTabs.register("travel", 65, Component.translatable("tab.isekai_mahou.travel"), TravelTab::new);

        ClientTravel.onMenu(menu -> {
            Minecraft mc = Minecraft.getInstance();
            // Lista de pedra: abre a tela (se já estiver aberta, ela se atualiza sozinha pela versão)
            if (menu.fromStone() && !(mc.screen instanceof TravelScreen)) mc.setScreen(new TravelScreen(menu.stone()));
        });
        ClientTravel.onOutcome(TravelView::onOutcome);

        // v1.0: Guia de Portais (seta no rastreador) e pontos do minimapa
        GuideClient.init();
        GuideNet.onMarkers = XaeroMarkers::accept;
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
            if (Minecraft.getInstance().level == null) return;
            GuideClient.tick();
            XaeroMarkers.tick();
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            TravelView.reset();
            GuideClient.reset();
            XaeroMarkers.reset();
        });
    }
}
