package com.renanpontes.isekaimahou.feature.legends_i.client;

import com.renanpontes.isekaimahou.feature.legends_i.LiNet;
import com.renanpontes.isekaimahou.feature.legends_i.LiRegistry;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderArmEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Parte de cliente do módulo "legends_i": renderer do Clone das Sombras, efeitos/estados vindos do servidor
 * ({@link LiFxClient}), braços negros do Haki ({@link HakiArms}) e o planeio das Asas de Papel ({@link PaperGlide}).
 */
public class LegendsIClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        LiNet.clientFx = LiFxClient::add;
        LiNet.clientState = LiFxClient::state;
        LiNet.clientSense = LiFxClient::sense;
        modBus.addListener(LegendsIClient::renderers);
        NeoForge.EVENT_BUS.addListener(LiFxClient::tick);
        NeoForge.EVENT_BUS.addListener(LiFxClient::render);
        NeoForge.EVENT_BUS.addListener(RenderPlayerEvent.Post.class, HakiArms::afterPlayer);
        NeoForge.EVENT_BUS.addListener(RenderArmEvent.class, HakiArms::firstPersonArm);
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(LiRegistry.KAGE_BUNSHIN.get(), KageBunshinRenderer::new);
    }
}
