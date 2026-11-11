package com.renanpontes.isekaimahou.feature.creatures_e.client;

import com.renanpontes.isekaimahou.feature.creatures_e.CeRegistries;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Parte de cliente do modulo "creatures_e": renderizadores GeckoLib das criaturas e do projetil. */
public class CreaturesEClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener(this::renderers);
    }

    private void renderers(EntityRenderersEvent.RegisterRenderers e) {
        // Plano Elemental
        e.registerEntityRenderer(CeRegistries.GREATER_FIRE_ELEMENTAL.get(),
                ctx -> new CreatureRenderer<>(ctx, "greater_fire_elemental", 0.9F).light(15).glowing().dissolves());
        e.registerEntityRenderer(CeRegistries.GREATER_WATER_ELEMENTAL.get(),
                ctx -> new CreatureRenderer<>(ctx, "greater_water_elemental", 0.8F).translucent().light(9).dissolves());
        e.registerEntityRenderer(CeRegistries.GREATER_EARTH_ELEMENTAL.get(),
                ctx -> new CreatureRenderer<>(ctx, "greater_earth_elemental", 1.3F).glowing());
        e.registerEntityRenderer(CeRegistries.GREATER_AIR_ELEMENTAL.get(),
                ctx -> new CreatureRenderer<>(ctx, "greater_air_elemental", 0.6F).translucent().light(10).glowing().dissolves());
        e.registerEntityRenderer(CeRegistries.LESSER_PHOENIX.get(),
                ctx -> new CreatureRenderer<>(ctx, "lesser_phoenix", 0.5F).light(15).dissolves());
        e.registerEntityRenderer(CeRegistries.STORM_WYVERN.get(),
                ctx -> new CreatureRenderer<>(ctx, "storm_wyvern", 1.2F).glowing());
        // Abismo
        e.registerEntityRenderer(CeRegistries.VOID_CRAWLER.get(),
                ctx -> new CreatureRenderer<>(ctx, "void_crawler", 0.9F).glowing());
        e.registerEntityRenderer(CeRegistries.WATCHER_EYE.get(),
                ctx -> new CreatureRenderer<>(ctx, "watcher_eye", 0.6F).glowing().dissolves());
        e.registerEntityRenderer(CeRegistries.YOUNG_KRAKEN.get(),
                ctx -> new CreatureRenderer<>(ctx, "young_kraken", 1.0F).glowing());
        // Santuario Celestial
        e.registerEntityRenderer(CeRegistries.ANGEL_SENTINEL.get(),
                ctx -> new CreatureRenderer<>(ctx, "angel_sentinel", 0.6F).glowing().dissolves());
        e.registerEntityRenderer(CeRegistries.CHERUB.get(),
                ctx -> new CreatureRenderer<>(ctx, "cherub", 0.35F).light(12).dissolves());
        e.registerEntityRenderer(CeRegistries.CRYSTAL_GOLEM.get(),
                ctx -> new CreatureRenderer<>(ctx, "crystal_golem", 1.1F).translucent().glowing());
        // Projetil
        e.registerEntityRenderer(CeRegistries.DIMENSIONAL_BOLT.get(), DimensionalBoltRenderer::new);
    }
}
