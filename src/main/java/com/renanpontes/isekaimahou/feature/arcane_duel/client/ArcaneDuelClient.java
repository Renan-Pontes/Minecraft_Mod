package com.renanpontes.isekaimahou.feature.arcane_duel.client;

import com.renanpontes.isekaimahou.feature.arcane_duel.DuelRegistry;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Parte de cliente do módulo "arcane_duel": o renderer dos visuais de duelo. */
public class ArcaneDuelClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) -> e.registerEntityRenderer(DuelRegistry.FX.get(), DuelFxRenderer::new));
    }
}
