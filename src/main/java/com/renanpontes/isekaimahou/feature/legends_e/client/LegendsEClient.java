package com.renanpontes.isekaimahou.feature.legends_e.client;

import com.renanpontes.isekaimahou.feature.legends_e.LeNet;
import com.renanpontes.isekaimahou.feature.legends_e.LeRegistry;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Parte de cliente do módulo "legends_e": renderers dos círculos supremos, do pilar da Queda Final, do vento negro,
 * do relógio da morte, da mão do Agarrar o Coração e do Filhote Sombrio; efeitos curtos (lanças, meteoros, ondas) e o
 * clarão na tela.
 */
public class LegendsEClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        LeNet.clientFx = LeFxClient::add;
        modBus.addListener(LegendsEClient::renderers);
        NeoForge.EVENT_BUS.addListener(LeFxClient::tick);
        NeoForge.EVENT_BUS.addListener(LeFxClient::render);
        NeoForge.EVENT_BUS.addListener(LeFxClient::gui);
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(LeRegistry.SUPREME_CIRCLE.get(), SupremeCircleRenderer::new);
        e.registerEntityRenderer(LeRegistry.FALLEN_PILLAR.get(), FallenPillarRenderer::new);
        e.registerEntityRenderer(LeRegistry.BLACK_GOAT_STORM.get(), BlackGoatStormRenderer::new);
        e.registerEntityRenderer(LeRegistry.DEATH_CLOCK.get(), DeathClockRenderer::new);
        e.registerEntityRenderer(LeRegistry.GRASP_HEART.get(), GraspHeartRenderer::new);
        e.registerEntityRenderer(LeRegistry.DARK_YOUNG.get(), DarkYoungRenderer::new);
    }
}
