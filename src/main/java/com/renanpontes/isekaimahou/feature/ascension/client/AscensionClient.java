package com.renanpontes.isekaimahou.feature.ascension.client;

import com.renanpontes.isekaimahou.feature.ascension.AscensionRegistry;
import com.renanpontes.isekaimahou.feature.ascension.AscensionTracking;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Parte de cliente do módulo "ascension": renderers do Núcleo, do Pedestal, da Sombra e do raio arcano, e o
 * rastreador de objetivos da Ascensão (tributo + setinha para o Núcleo).
 */
public class AscensionClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener(this::onRenderers);
        AscensionObjectives.register();
        // Outro mundo/servidor: o Núcleo seguido não vale mais.
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> AscensionTracking.forget());
    }

    private void onRenderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerBlockEntityRenderer(AscensionRegistry.CORE_BE.get(), AscensionCoreRenderer::new);
        e.registerBlockEntityRenderer(AscensionRegistry.PEDESTAL_BE.get(), AscensionPedestalRenderer::new);
        e.registerEntityRenderer(AscensionRegistry.SHADOW.get(), AscensionShadowRenderer::new);
        e.registerEntityRenderer(AscensionRegistry.BOLT.get(), TribulationBoltRenderer::new);
    }
}
