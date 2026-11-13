package com.renanpontes.isekaimahou.feature.espada.client;

import com.renanpontes.isekaimahou.feature.espada.EspadaPayloads;
import com.renanpontes.isekaimahou.feature.espada.EspadaRegistries;
import com.renanpontes.isekaimahou.feature.espada.entity.EspadaBoss;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Parte de cliente do módulo "espada": renderers GeckoLib (uma forma por textura), projéteis como riscos de energia,
 * feixes/orbes/lanças desenhados no mundo, efeitos de tela (Envelhecido, escuridão da Segunda Etapa, clarões) e a
 * coreografia dos efeitos que o servidor manda.
 */
public class EspadaClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener(this::onRenderers);
        modBus.addListener((RegisterGuiLayersEvent e) -> EspadaScreenFx.register(e));
        EspadaPayloads.onFx = EspadaFxClient::handle;
        EspadaBoss.clientTickHook = EspadaFxClient::bossTick;
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
            if (Minecraft.getInstance().level == null || Minecraft.getInstance().isPaused()) return;
            EspadaFxKit.tick();
            EspadaBeams.tick();
            EspadaScreenFx.tick();
        });
        NeoForge.EVENT_BUS.addListener(EspadaBeams::onRender);
        NeoForge.EVENT_BUS.addListener(EspadaScreenFx::onCamera);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            EspadaFxKit.clear();
            EspadaScreenFx.reset();
        });
    }

    private void onRenderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(EspadaRegistries.ULQUIORRA.get(), ctx -> new EspadaRenderer(ctx, "ulquiorra", 0.5F));
        e.registerEntityRenderer(EspadaRegistries.BARRAGAN.get(), ctx -> new EspadaRenderer(ctx, "barragan", 0.8F));
        e.registerEntityRenderer(EspadaRegistries.GRIMMJOW.get(), ctx -> new EspadaRenderer(ctx, "grimmjow", 0.55F));
        e.registerEntityRenderer(EspadaRegistries.BOLT.get(), BoltRenderer::new);
    }
}
