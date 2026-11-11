package com.renanpontes.isekaimahou.client.boss;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Parte de cliente do kit de chefes (desenho das telegrafias, tremor de câmera). Só no cliente. */
public final class BossToolkitClient {
    public static void init(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(TelegraphRenderer::onClientTick);
        NeoForge.EVENT_BUS.addListener(TelegraphRenderer::onRender);
        NeoForge.EVENT_BUS.addListener(BossCameraShake::onClientTick);
        NeoForge.EVENT_BUS.addListener(BossCameraShake::onCameraAngles);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            TelegraphRenderer.clear();
            BossCameraShake.clear();
        });
    }

    private BossToolkitClient() {}
}
