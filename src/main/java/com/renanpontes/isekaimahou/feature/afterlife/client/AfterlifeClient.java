package com.renanpontes.isekaimahou.feature.afterlife.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.afterlife.AfterlifeContent;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Parte de cliente do módulo "afterlife": corpo, Ceifador, alma translúcida, HUD do desafio e marcador do corpo. */
public class AfterlifeClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener((EntityRenderersEvent.RegisterLayerDefinitions e) -> e.registerLayerDefinition(ReaperModel.LAYER, ReaperModel::createLayer));
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) -> {
            e.registerEntityRenderer(AfterlifeContent.CORPSE.get(), CorpseRenderer::new);
            e.registerEntityRenderer(AfterlifeContent.REAPER.get(), ReaperRenderer::new);
        });
        modBus.addListener((RegisterGuiLayersEvent e) -> e.registerAboveAll(IsekaiMahou.id("afterlife_hud"), AfterlifeHud::render));
        modBus.addListener((RegisterKeyMappingsEvent e) -> e.register(ClientAfterlife.GIVE_UP));

        var bus = NeoForge.EVENT_BUS;
        bus.addListener(ClientAfterlife::onTick);
        bus.addListener(ClientAfterlife::onKey);
        bus.addListener(ClientAfterlife::onInteractKey);
        bus.addListener((RenderPlayerEvent.Pre e) -> SpiritRender.onRenderPlayer(e));
        bus.addListener((RenderHandEvent e) -> SpiritRender.onRenderHand(e));
        bus.addListener((RenderLevelStageEvent e) -> CorpseMarker.onRenderLevel(e));
        bus.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> ClientAfterlife.reset());
        CorpseMarker.register();
    }
}
