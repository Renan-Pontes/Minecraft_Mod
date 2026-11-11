package com.renanpontes.isekaimahou.feature.legends_a.client;

import com.renanpontes.isekaimahou.feature.legends_a.LegendsARegistry;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Parte de cliente do módulo "legends_a": renderers das armas, portais, círculos no céu e da Explosão Colossal. */
public class LegendsAClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener(LegendsAClient::renderers);
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(LegendsARegistry.CONJURED_BLADE.get(), ConjuredBladeRenderer::new);
        e.registerEntityRenderer(LegendsARegistry.ARSENAL_PORTAL.get(), ArsenalPortalRenderer::new);
        e.registerEntityRenderer(LegendsARegistry.EXPLOSION_SIGIL.get(), ExplosionSigilRenderer::new);
        e.registerEntityRenderer(LegendsARegistry.CRATER_BLAST.get(), CraterBlastRenderer::new);
    }
}
