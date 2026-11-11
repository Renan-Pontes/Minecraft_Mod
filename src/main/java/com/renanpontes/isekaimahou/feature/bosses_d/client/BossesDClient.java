package com.renanpontes.isekaimahou.feature.bosses_d.client;

import com.renanpontes.isekaimahou.feature.bosses_d.BdRegistries;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Parte de cliente do módulo "bosses_d": renderers GeckoLib do Avatar Elemental e da Sentinela. */
public class BossesDClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener(this::renderers);
    }

    private void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(BdRegistries.TRIAL_AVATAR.get(), TrialAvatarRenderer::new);
        e.registerEntityRenderer(BdRegistries.TRIAL_SENTINEL.get(), TrialSentinelRenderer::new);
    }
}
