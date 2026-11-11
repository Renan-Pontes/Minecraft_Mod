package com.renanpontes.isekaimahou.feature.fire_water.client;

import com.renanpontes.isekaimahou.feature.fire_water.FwRegistries;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.minecraft.client.renderer.entity.NoopRenderer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Parte de cliente do módulo "fire_water": renderers das invocações (GeckoLib) e das entidades só-partícula. */
public class FireWaterClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener(FireWaterClient::renderers);
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(FwRegistries.FIELD.get(), NoopRenderer::new);
        e.registerEntityRenderer(FwRegistries.WATER_JET.get(), NoopRenderer::new);
        e.registerEntityRenderer(FwRegistries.SALAMANDER.get(), SalamanderRenderer::new);
        e.registerEntityRenderer(FwRegistries.UNDINE.get(), UndineRenderer::new);
    }
}
