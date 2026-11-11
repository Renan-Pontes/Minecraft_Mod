package com.renanpontes.isekaimahou.feature.legends_c.client;

import com.renanpontes.isekaimahou.feature.legends_c.LcNet;
import com.renanpontes.isekaimahou.feature.legends_c.LcRegistry;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Parte de cliente do módulo "legends_c": renderers (Santuário GeckoLib, esfera do Infinito, Azul, Vermelho, Fuga,
 * cosmos do Vazio), os efeitos desenhados que chegam por pacote (cortes, grade, ondas, blocos sugados) e o céu/névoa
 * de dentro dos domínios.
 */
public class LegendsCClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        LcNet.clientFx = LcFxClient::add;
        modBus.addListener(LegendsCClient::renderers);
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ClientTickEvent.Post.class, LcFxClient::tick);
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, RenderLevelStageEvent.class, LcFxClient::render);
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ClientTickEvent.Post.class, LcAtmosphere::tick);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, false, ViewportEvent.ComputeFogColor.class, LcAtmosphere::fogColor);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, false, ViewportEvent.RenderFog.class, LcAtmosphere::renderFog);
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, RenderGuiEvent.Post.class, LcAtmosphere::vignette);
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(LcRegistry.SHRINE.get(), ShrineRenderer::new);
        e.registerEntityRenderer(LcRegistry.INFINITY_FIELD.get(), InfinityFieldRenderer::new);
        e.registerEntityRenderer(LcRegistry.BLUE_ORB.get(), BlueOrbRenderer::new);
        e.registerEntityRenderer(LcRegistry.RED_ORB.get(), RedOrbRenderer::new);
        e.registerEntityRenderer(LcRegistry.FUGA_ARROW.get(), FugaArrowRenderer::new);
        e.registerEntityRenderer(LcRegistry.VOID_FIELD.get(), VoidFieldRenderer::new);
    }
}
