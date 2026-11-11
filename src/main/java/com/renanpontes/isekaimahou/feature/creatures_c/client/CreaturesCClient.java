package com.renanpontes.isekaimahou.feature.creatures_c.client;

import com.renanpontes.isekaimahou.feature.creatures_c.CcRegistries;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Parte de cliente do modulo "creatures_c": renderers GeckoLib das seis feras. */
public class CreaturesCClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener(CreaturesCClient::renderers);
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(CcRegistries.DIRE_WOLF.get(), DireWolfRenderer::new);
        e.registerEntityRenderer(CcRegistries.HARPY.get(), HarpyRenderer::new);
        e.registerEntityRenderer(CcRegistries.BASILISK.get(), BasiliskRenderer::new);
        e.registerEntityRenderer(CcRegistries.MIMIC.get(), MimicRenderer::new);
        e.registerEntityRenderer(CcRegistries.GARGOYLE.get(), GargoyleRenderer::new);
        e.registerEntityRenderer(CcRegistries.FAIRY.get(), FairyRenderer::new);
    }
}
