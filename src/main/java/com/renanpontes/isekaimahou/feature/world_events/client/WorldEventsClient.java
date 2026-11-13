package com.renanpontes.isekaimahou.feature.world_events.client;

import com.renanpontes.isekaimahou.feature.bosses_g.client.BgGeoRenderer;
import com.renanpontes.isekaimahou.feature.world_events.WeRegistries;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Parte de cliente do módulo "world_events": renderers GeckoLib dos invasores (reaproveita o {@link BgGeoRenderer}
 * dos chefes: camada brilhante _glowmask) e o céu dos eventos ({@link WeClientState}). O tremor de tela usa o do
 * núcleo ({@code BossKit.shake}), que já respeita a opção "tremor de tela".
 */
public class WorldEventsClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener(this::onRenderers);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, WeClientState::onClientTick);
        NeoForge.EVENT_BUS.addListener(ViewportEvent.ComputeFogColor.class, WeClientState::onFogColor);
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, WeClientState::onLogout);
    }

    private void onRenderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(WeRegistries.ALIEN_GRUNT.get(), ctx -> new BgGeoRenderer<>(ctx, "world_events_alien_grunt", true, 1.0F, 0.45F));
        e.registerEntityRenderer(WeRegistries.ALIEN_SPITTER.get(), ctx -> new BgGeoRenderer<>(ctx, "world_events_alien_spitter", false, 1.0F, 0.5F));
        e.registerEntityRenderer(WeRegistries.ALIEN_LARVA.get(), ctx -> new BgGeoRenderer<>(ctx, "world_events_alien_larva", false, 1.0F, 0.3F));
        e.registerEntityRenderer(WeRegistries.ALIEN_OVERSEER.get(), ctx -> new BgGeoRenderer<>(ctx, "world_events_alien_overseer", false, 1.0F, 0.9F));
    }
}
