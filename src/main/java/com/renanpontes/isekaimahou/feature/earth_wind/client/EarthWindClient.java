package com.renanpontes.isekaimahou.feature.earth_wind.client;

import com.renanpontes.isekaimahou.feature.earth_wind.EwNetwork;
import com.renanpontes.isekaimahou.feature.earth_wind.EwRegistries;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.minecraft.client.renderer.entity.NoopRenderer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Parte de cliente do modulo "earth_wind": renderers, Pulo Duplo (tecla de pulo no ar) e tremor de tela. */
public class EarthWindClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener(EarthWindClient::renderers);
        NeoForge.EVENT_BUS.addListener(DoubleJumpInput::onClientTick);
        NeoForge.EVENT_BUS.addListener(CameraShake::onClientTick);
        NeoForge.EVENT_BUS.addListener(CameraShake::onCameraAngles);
        EwNetwork.clientShake = CameraShake::start;
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(EwRegistries.STONE_GOLEM.get(), StoneGolemRenderer::new);
        e.registerEntityRenderer(EwRegistries.SYLPH.get(), SylphRenderer::new);
        e.registerEntityRenderer(EwRegistries.STALAGMITE_SPIKE.get(), StalagmiteRenderer::new);
        // Tornado e Tempestade sao feitos so de particulas
        e.registerEntityRenderer(EwRegistries.WIND_TORNADO.get(), NoopRenderer::new);
        e.registerEntityRenderer(EwRegistries.TEMPEST_STORM.get(), NoopRenderer::new);
    }
}
