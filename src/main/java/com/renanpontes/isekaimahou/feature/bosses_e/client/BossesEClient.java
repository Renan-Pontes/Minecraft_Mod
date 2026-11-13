package com.renanpontes.isekaimahou.feature.bosses_e.client;

import com.renanpontes.isekaimahou.feature.bosses_e.BeRegistries;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Parte de cliente do módulo "bosses_e": renderers GeckoLib do Dragão Primordial, dos Clones de Sombra e da Âncora das Correntes. */
public class BossesEClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener(this::renderers);
        DragonSkyFx.init();
    }

    private void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(BeRegistries.PRIMORDIAL_DRAGON.get(), PrimordialDragonRenderer::new);
        e.registerEntityRenderer(BeRegistries.PRIMORDIAL_SHADE.get(), PrimordialShadeRenderer::new);
        e.registerEntityRenderer(BeRegistries.PRIMORDIAL_CHAIN.get(), PrimordialChainRenderer::new);
    }
}
