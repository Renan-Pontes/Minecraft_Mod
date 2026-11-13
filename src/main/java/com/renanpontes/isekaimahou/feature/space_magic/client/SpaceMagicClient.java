package com.renanpontes.isekaimahou.feature.space_magic.client;

import com.renanpontes.isekaimahou.feature.space_magic.SpaceContent;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Parte de cliente do módulo "space_magic": desenho do Portal Espacial (a tela de escolha abre pelo pacote). */
public class SpaceMagicClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) ->
                e.registerEntityRenderer(SpaceContent.SPACE_GATE.get(), SpaceGateRenderer::new));
    }
}
