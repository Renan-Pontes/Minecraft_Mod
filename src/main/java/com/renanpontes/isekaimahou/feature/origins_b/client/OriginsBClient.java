package com.renanpontes.isekaimahou.feature.origins_b.client;

import com.renanpontes.isekaimahou.feature.origins_b.ObRegistries;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Parte de cliente do módulo "origins_b": renderers das invocações e da bola de fogo dos diabretes. */
public class OriginsBClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener(OriginsBClient::renderers);
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ObRegistries.SUMMONED_IMP.get(), ctx -> new SummonGeoRenderer<>(ctx, "summoned_imp", 0.35F));
        e.registerEntityRenderer(ObRegistries.SUMMONED_DEMON.get(), ctx -> new SummonGeoRenderer<>(ctx, "summoned_demon", 1.0F));
        e.registerEntityRenderer(ObRegistries.UNDEAD_SERVANT.get(), ctx -> new SummonGeoRenderer<>(ctx, "undead_servant", 0.5F));
        e.registerEntityRenderer(ObRegistries.BONE_WARRIOR.get(), ctx -> new SummonGeoRenderer<>(ctx, "bone_warrior", 0.5F));
        e.registerEntityRenderer(ObRegistries.IMP_FIREBALL.get(), ImpFireballRenderer::new);
    }
}
