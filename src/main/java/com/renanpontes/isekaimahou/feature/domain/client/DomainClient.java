package com.renanpontes.isekaimahou.feature.domain.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.domain.DomainNet;
import com.renanpontes.isekaimahou.feature.domain.DomainRegistries;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/** Parte de cliente do módulo "domain": renderer do domo, névoa/céu interno, vinheta e tremor de câmera. */
public class DomainClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) ->
                e.registerEntityRenderer(DomainRegistries.DOMAIN_DOME.get(), DomainDomeRenderer::new));
        modBus.addListener((RegisterGuiLayersEvent e) ->
                e.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, IsekaiMahou.id("domain_tint"), DomainClientEffects::renderVignette));

        NeoForge.EVENT_BUS.addListener(DomainClientEffects::onClientTick);
        NeoForge.EVENT_BUS.addListener(DomainClientEffects::onFogColor);
        NeoForge.EVENT_BUS.addListener(DomainClientEffects::onRenderFog);
        NeoForge.EVENT_BUS.addListener(DomainClientEffects::onCameraAngles);
        // v0.8: socar a parede de um domo (barreira com integridade)
        NeoForge.EVENT_BUS.addListener(DomainBarrierInput::onInteraction);

        DomainNet.clientShake = DomainClientEffects::onShake;
    }
}
