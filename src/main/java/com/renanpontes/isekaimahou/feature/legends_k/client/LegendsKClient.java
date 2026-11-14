package com.renanpontes.isekaimahou.feature.legends_k.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.legends_k.LkNet;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Parte de cliente do "legends_k": só os efeitos de tela (tom azul da dimensão, apagão do Retorno pela Morte). */
public class LegendsKClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        LkNet.clientScreen = LkScreenFx::onScreen;
        modBus.addListener(LegendsKClient::layers);
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(ClientTickEvent.Post.class, LkScreenFx::tick);
        bus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, LkScreenFx::onLogout);
    }

    private static void layers(RegisterGuiLayersEvent e) {
        e.registerAboveAll(IsekaiMahou.id("legends_k_screen"), LkScreenFx::renderHud);
    }
}
