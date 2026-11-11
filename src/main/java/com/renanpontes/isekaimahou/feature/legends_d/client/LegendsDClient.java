package com.renanpontes.isekaimahou.feature.legends_d.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.legends_d.LdNet;
import com.renanpontes.isekaimahou.feature.legends_d.LdRegistry;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Parte de cliente do módulo "legends_d": renderers do Atômico e da Desintegração, a parada do tempo do lado do
 * cliente (entidades paradas, onda de negativo, tela cinza, relógio), letreiros/clarões/aviso "Abriguem-se!", a bolha
 * protetora do Atômico e a cabra da maldição.
 */
public class LegendsDClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        LdNet.clientFx = AtomicOverlay::onFx;
        LdNet.clientTimeStop = TimeStopClient::onSync;
        LdNet.clientTimeResume = TimeStopClient::onEnd;
        LdNet.clientGoat = GoatCurseClient::onSync;

        modBus.addListener(LegendsDClient::renderers);
        modBus.addListener(LegendsDClient::layers);

        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener(TimeStopClient::onEntityTick);
        bus.addListener(TimeStopClient::tick);
        bus.addListener(TimeStopClient::render);
        bus.addListener(TimeStopClient::onLogout);
        bus.addListener(AtomicOverlay::tick);
        bus.addListener(AtomicBubbleClient::tick);
        bus.addListener(AtomicBubbleClient::render);
        bus.addListener(AtomicBubbleClient::onLogout);
        bus.addListener(GoatCurseClient::tick);
        bus.addListener(GoatCurseClient::onRenderPlayer);
        bus.addListener(GoatCurseClient::onRenderHand);
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(LdRegistry.ATOMIC_NOVA.get(), AtomicNovaRenderer::new);
        e.registerEntityRenderer(LdRegistry.DISINTEGRATION_CAGE.get(), DisintegrationCageRenderer::new);
    }

    private static void layers(RegisterGuiLayersEvent e) {
        e.registerAboveAll(IsekaiMahou.id("legends_d_time_stop"), TimeStopClient::renderHud);
        e.registerAboveAll(IsekaiMahou.id("legends_d_atomic"), AtomicOverlay::render);
    }
}
