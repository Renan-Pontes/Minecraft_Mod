package com.renanpontes.isekaimahou.feature.fusions_a.client;

import com.renanpontes.isekaimahou.feature.fusions_a.FaRegistries;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.minecraft.client.renderer.entity.NoopRenderer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Parte de cliente do módulo "fusions_a": renderer do Ent (GeckoLib) e da zona das fusões (só partículas). */
public class FusionsAClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener(FusionsAClient::renderers);
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(FaRegistries.FUSION_ZONE.get(), NoopRenderer::new);
        e.registerEntityRenderer(FaRegistries.SUMMONED_ENT.get(), SummonedEntRenderer::new);
    }
}
