package com.renanpontes.isekaimahou.client.status;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.status.StatusRegistries;

import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

/**
 * Cliente do núcleo v0.8 — A1: ícones/visuais das condições de status ({@link ClientStatuses}), textos das reações,
 * escombros da destruição e letreiro dos lugares ({@link ClientPlaces}). Só no cliente.
 */
public final class StatusCoreClient {
    public static void init(IEventBus modBus) {
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) ->
                e.registerEntityRenderer(StatusRegistries.DEBRIS.get(), DebrisRenderer::new));
        // Tinta das condições sobre o modelo (vanilla/mods comuns e GeckoLib)
        modBus.addListener(StatusTints::addLayers);
        NeoForge.EVENT_BUS.addListener(StatusTints::addGeoLayer);
        modBus.addListener((RegisterGuiLayersEvent e) ->
                e.registerAbove(VanillaGuiLayers.TITLE, IsekaiMahou.id("core_status_place_banner"), PlaceBanner::render));

        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
            if (Minecraft.getInstance().level == null) return;
            ClientStatuses.tick();
            StatusParticles.tick();
        });
        NeoForge.EVENT_BUS.addListener(StatusWorldRenderer::render);
        NeoForge.EVENT_BUS.addListener((EntityLeaveLevelEvent e) -> {
            if (e.getLevel().isClientSide()) ClientStatuses.forget(e.getEntity());
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            ClientStatuses.clear();
            ReactionTexts.clear();
            ClientPlaces.clear();
            PlaceBanner.clear();
        });
    }

    private StatusCoreClient() {}
}
