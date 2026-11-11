package com.renanpontes.isekaimahou.feature.legends_f.client;

import com.renanpontes.isekaimahou.feature.legends_f.LfNet;
import com.renanpontes.isekaimahou.feature.legends_f.LfRegistry;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Parte de cliente do módulo "legends_f": renderers das entidades (Soldado das Sombras, Barreira, Vassoura, Mãos da
 * Inveja, Deus da Floresta, Kodama), a camada dos jogadores (motosserras, olhos do Modo Sábio) e os efeitos/estados
 * vindos do servidor.
 */
public class LegendsFClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        LfNet.clientFx = LfFxClient::add;
        LfNet.clientState = LfFxClient::state;
        modBus.addListener(LegendsFClient::renderers);
        modBus.addListener(PlayerLayers::addLayers);
        NeoForge.EVENT_BUS.addListener(LfFxClient::tick);
        NeoForge.EVENT_BUS.addListener(LfFxClient::render);
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(LfRegistry.SHADOW_SOLDIER.get(), ShadowSoldierRenderer::new);
        e.registerEntityRenderer(LfRegistry.BARRIER.get(), BarrierRenderer::new);
        e.registerEntityRenderer(LfRegistry.BROOM.get(), BroomRenderer::new);
        e.registerEntityRenderer(LfRegistry.ENVY_HANDS.get(), EnvyHandsRenderer::new);
        e.registerEntityRenderer(LfRegistry.FOREST_GOD.get(), ForestGodRenderer::new);
        e.registerEntityRenderer(LfRegistry.KODAMA.get(), KodamaRenderer::new);
    }
}
