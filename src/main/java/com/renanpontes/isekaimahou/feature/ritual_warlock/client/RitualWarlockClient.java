package com.renanpontes.isekaimahou.feature.ritual_warlock.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.ritual_warlock.WarlockContent;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Parte de cliente do Ritual do Warlock: renderers dos Patronos e do altar, HUD do pacto. */
public class RitualWarlockClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) -> {
            e.registerEntityRenderer(WarlockContent.EMBER_LORD.get(), ctx -> new PatronRenderer<>(ctx, "patron_ember_lord", 1.0F));
            e.registerEntityRenderer(WarlockContent.VOID_LADY.get(), ctx -> new PatronRenderer<>(ctx, "patron_void_lady", 0.6F));
            e.registerEntityRenderer(WarlockContent.DROWNED_KING.get(), ctx -> new PatronRenderer<>(ctx, "patron_drowned_king", 1.1F));
            e.registerBlockEntityRenderer(WarlockContent.OFFERING_ALTAR_BE.get(), OfferingAltarRenderer::new);
        });
        modBus.addListener((RegisterGuiLayersEvent e) -> e.registerAboveAll(IsekaiMahou.id("warlock_pact"), PactHudOverlay::render));
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> PactHudOverlay.clear());
        // Mesmo canto do rastreador de objetivos do núcleo: reserva a altura do painel do pacto
        com.renanpontes.isekaimahou.client.objectives.ObjectiveTracker.reserveTop("warlock_pact", PactHudOverlay::reservedHeight);
    }
}
