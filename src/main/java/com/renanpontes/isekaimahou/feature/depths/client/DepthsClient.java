package com.renanpontes.isekaimahou.feature.depths.client;

import com.renanpontes.isekaimahou.feature.bosses_g.client.BgGeoRenderer;
import com.renanpontes.isekaimahou.feature.depths.DepthsRegistries;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Parte de cliente do módulo "depths": renderers GeckoLib (reaproveita o {@link BgGeoRenderer} dos chefes: camada
 * brilhante _glowmask e ossos que somem — o corpo do verme some quando ele está enterrado e só o monte de terra fica).
 */
public class DepthsClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener(this::onRenderers);
    }

    private void onRenderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(DepthsRegistries.WORM_KING.get(), ctx -> new BgGeoRenderer<>(ctx, "depths_worm_king", false, 1.0F, 1.6F));
        e.registerEntityRenderer(DepthsRegistries.BURROWER.get(), ctx -> new BgGeoRenderer<>(ctx, "depths_burrower", false, 1.0F, 0.35F));
        e.registerEntityRenderer(DepthsRegistries.LOST_DELVER.get(), ctx -> new BgGeoRenderer<>(ctx, "depths_lost_delver", true, 1.0F, 0.5F));
    }
}
