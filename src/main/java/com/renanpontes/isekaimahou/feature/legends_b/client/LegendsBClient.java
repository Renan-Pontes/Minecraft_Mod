package com.renanpontes.isekaimahou.feature.legends_b.client;

import com.renanpontes.isekaimahou.feature.legends_b.LegendsBNet;
import com.renanpontes.isekaimahou.feature.legends_b.LegendsBRegistry;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Parte de cliente do módulo "legends_b": renderers das formas lendárias e os efeitos desenhados (feixes, cortes, ondas). */
public class LegendsBClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        LegendsBNet.clientFx = LegendFxClient::add;
        modBus.addListener(LegendsBClient::renderers);
        NeoForge.EVENT_BUS.addListener(LegendFxClient::tick);
        NeoForge.EVENT_BUS.addListener(LegendFxClient::render);
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(LegendsBRegistry.CONVERGE_SPHERE.get(), ConvergeSphereRenderer::new);
        e.registerEntityRenderer(LegendsBRegistry.SKY_LANCES.get(), SkyLancesRenderer::new);
        e.registerEntityRenderer(LegendsBRegistry.SPIRAL_ORB.get(), SpiralOrbRenderer::new);
    }
}
