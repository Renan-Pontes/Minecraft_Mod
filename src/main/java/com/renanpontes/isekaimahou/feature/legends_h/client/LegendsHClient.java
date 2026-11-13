package com.renanpontes.isekaimahou.feature.legends_h.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.legends_h.LhNet;
import com.renanpontes.isekaimahou.feature.legends_h.LhRegistry;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Parte de cliente do "legends_h": o tanque do Sheer Heart Attack e os efeitos de tela (King Crimson, tempo apagado,
 * pulo do tempo, Bites the Dust). O resto dos visuais vem do {@code ElementFx} do núcleo.
 */
public class LegendsHClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        LhNet.clientScreen = LhScreenFx::onScreen;
        modBus.addListener(LegendsHClient::renderers);
        modBus.addListener(LegendsHClient::layers);
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(ClientTickEvent.Post.class, LhScreenFx::tick);
        bus.addListener(EventPriority.HIGH, RenderPlayerEvent.Pre.class, LhScreenFx::onRenderPlayer);
        bus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, LhScreenFx::onLogout);
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(LhRegistry.SHEER_HEART_ATTACK.get(), SheerHeartAttackRenderer::new);
    }

    private static void layers(RegisterGuiLayersEvent e) {
        e.registerAboveAll(IsekaiMahou.id("legends_h_screen"), LhScreenFx::renderHud);
    }
}
