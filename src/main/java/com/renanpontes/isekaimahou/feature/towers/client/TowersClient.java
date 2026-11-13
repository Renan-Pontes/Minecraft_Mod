package com.renanpontes.isekaimahou.feature.towers.client;

import com.renanpontes.isekaimahou.feature.bosses_g.client.BgGeoRenderer;
import com.renanpontes.isekaimahou.feature.towers.TwRegistries;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Parte de cliente do módulo "towers": renderer GeckoLib do Lorde Kobold (reaproveita o {@link BgGeoRenderer} dos chefes:
 * camada brilhante _glowmask e ossos que aparecem/somem — machado/broquel ↔ nodachi). Os blocos usam render_type nos
 * modelos JSON (o Selo é translúcido) e os avisos de andar vêm do servidor (títulos e barra de ação).
 */
public class TowersClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener(this::onRenderers);
    }

    private void onRenderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(TwRegistries.KOBOLD_LORD.get(), ctx -> new BgGeoRenderer<>(ctx, "tower_kobold_lord", true, 1.0F, 0.9F));
    }
}
