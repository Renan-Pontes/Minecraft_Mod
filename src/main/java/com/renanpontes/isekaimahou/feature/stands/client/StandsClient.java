package com.renanpontes.isekaimahou.feature.stands.client;

import com.renanpontes.isekaimahou.feature.stands.StandNet;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Parte de cliente do módulo "stands": o modelo do stand e o desenho dos stands ativos. */
public class StandsClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        StandNet.client = StandRenderer::onShow;
        modBus.addListener((EntityRenderersEvent.RegisterLayerDefinitions e) -> e.registerLayerDefinition(StandModel.LAYER, StandModel::createLayer));
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(ClientTickEvent.Post.class, StandRenderer::tick);
        bus.addListener(RenderLevelStageEvent.class, StandRenderer::onRender);
        bus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, StandRenderer::onLogout);
    }
}
