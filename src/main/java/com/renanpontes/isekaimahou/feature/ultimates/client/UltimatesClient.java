package com.renanpontes.isekaimahou.feature.ultimates.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.ultimates.UltNet;
import com.renanpontes.isekaimahou.feature.ultimates.UltRegistries;
import com.renanpontes.isekaimahou.feature.ultimates.entity.MeteorEntity;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Parte de cliente do módulo "ultimates": renderers GeckoLib do Meteoro e do Colosso Espectral, som crescente do
 * meteoro, céu escarlate da Chuva de Meteoros, auréola/asas da Transcendência e os efeitos curtos (cortes, anéis).
 */
public class UltimatesClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener(UltimatesClient::renderers);
        modBus.addListener(UltimatesClient::layers);

        UltNet.clientOmen = UltClientFx::onOmen;
        UltNet.clientTranscend = UltClientFx::onTranscend;
        UltNet.clientFx = UltClientFx::onFx;
        MeteorEntity.clientSpawnHook = MeteorSound::start;

        NeoForge.EVENT_BUS.addListener(UltClientFx::tick);
        NeoForge.EVENT_BUS.addListener(UltClientFx::render);
        NeoForge.EVENT_BUS.addListener(UltClientFx::onFogColor);
        NeoForge.EVENT_BUS.addListener(UltClientFx::onRenderFog);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            UltClientFx.clear();
            MeteorSound.clear();
        });
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(UltRegistries.METEOR.get(), MeteorRenderer::new);
        e.registerEntityRenderer(UltRegistries.SPECTRAL_COLOSSUS.get(), ColossusRenderer::new);
    }

    private static void layers(RegisterGuiLayersEvent e) {
        e.registerBelowAll(IsekaiMahou.id("ultimates_sky_tint"), UltClientFx::overlay);
    }
}
