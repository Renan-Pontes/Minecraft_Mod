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
        // v1.3: tela das maldições assinatura e clarões dos chefes
        BossScreenFx.init(modBus);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            TelegraphRenderer.clear();
            BossCameraShake.clear();
            BossScreenFx.clear();
        });
    }

    private BossToolkitClient() {}
}
