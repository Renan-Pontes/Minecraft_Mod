package com.renanpontes.isekaimahou.feature.end_library.client;

import com.renanpontes.isekaimahou.feature.bosses_g.client.BgGeoRenderer;
import com.renanpontes.isekaimahou.feature.end_library.ElRegistries;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Parte de cliente do módulo "end_library": renderers GeckoLib (chefe e criaturas) e as armas do Portão do Tesouro. */
public class EndLibraryClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener(this::onRenderers);
    }

    private void onRenderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ElRegistries.STAR_DEVOURER.get(), ctx -> new BgGeoRenderer<>(ctx, "star_devourer", false, 1.0F, 1.6F));
        e.registerEntityRenderer(ElRegistries.VOID_SCRIBE.get(), ctx -> new ElMobRenderer<>(ctx, "void_scribe", 0.5F));
        e.registerEntityRenderer(ElRegistries.STAR_WISP.get(), ctx -> new ElMobRenderer<>(ctx, "star_wisp", 0.25F));
        e.registerEntityRenderer(ElRegistries.TREASURE_BLADE.get(), TreasureBladeRenderer::new);
    }
}
