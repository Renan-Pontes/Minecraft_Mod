package com.renanpontes.isekaimahou.feature.nether_citadel.client;

import com.renanpontes.isekaimahou.feature.bosses_g.client.BgGeoRenderer;
import com.renanpontes.isekaimahou.feature.nether_citadel.NcRegistries;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Parte de cliente do módulo "nether_citadel": renderers GeckoLib (reaproveita o {@link BgGeoRenderer} dos chefes:
 * camada brilhante _glowmask e ossos que aparecem/somem — as asas da Forma Demoníaca).
 */
public class NetherCitadelClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener(this::onRenderers);
    }

    private void onRenderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(NcRegistries.DEMON_LORD.get(), ctx -> new BgGeoRenderer<>(ctx, "demon_lord", true, 1.0F, 1.0F));
        e.registerEntityRenderer(NcRegistries.DEMON_KNIGHT.get(), ctx -> new BgGeoRenderer<>(ctx, "demon_knight", true, 1.0F, 0.6F));
        e.registerEntityRenderer(NcRegistries.DEMON_SUCCUBUS.get(), ctx -> new BgGeoRenderer<>(ctx, "demon_succubus", true, 1.0F, 0.5F));
    }
}
