package com.renanpontes.isekaimahou.feature.light_shadow.client;

import com.renanpontes.isekaimahou.feature.light_shadow.LsRegistries;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.minecraft.client.renderer.entity.NoopRenderer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Parte de cliente do módulo "light_shadow": renderers das invocações (GeckoLib) e das zonas (só partículas). */
public class LightShadowClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener(LightShadowClient::renderers);
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(LsRegistries.GUARDIAN_FAIRY.get(), GuardianFairyRenderer::new);
        e.registerEntityRenderer(LsRegistries.LIVING_SHADOW.get(), LivingShadowRenderer::new);
        e.registerEntityRenderer(LsRegistries.SACRED_BARRIER_ZONE.get(), NoopRenderer::new);
        e.registerEntityRenderer(LsRegistries.SANCTUARY_ZONE.get(), NoopRenderer::new);
    }
}
