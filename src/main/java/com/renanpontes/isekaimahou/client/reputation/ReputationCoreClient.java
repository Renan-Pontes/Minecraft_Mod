package com.renanpontes.isekaimahou.client.reputation;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.client.menu.MenuTabs;
import com.renanpontes.isekaimahou.client.objectives.ObjectiveTracker;
import com.renanpontes.isekaimahou.core.reputation.FactionRegistry;
import com.renanpontes.isekaimahou.core.reputation.ReputationPayloads;

import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Inicialização do núcleo v0.8 (cliente) — A3: reputação e facções.
 * Aba "Reputação" do menu B (ordem 55), avisos laterais de ganho/perda e limpeza ao sair do mundo.
 */
public final class ReputationCoreClient {
    public static void init(IEventBus modBus) {
        MenuTabs.register("reputation", 55, Component.translatable("screen.isekai_mahou.tab.reputation"), ReputationTab::new);
        ReputationPayloads.onNotify = ReputationNotices::push;
        ObjectiveTracker.reserveTop(ReputationNotices.RESERVE_ID, ReputationNotices::reservedHeight);
        modBus.addListener((RegisterGuiLayersEvent e) ->
                e.registerBelow(VanillaGuiLayers.CHAT, IsekaiMahou.id("reputation_notices"), new ReputationNotices()));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> ReputationNotices.tick());
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            FactionRegistry.clearClient();
            ReputationNotices.clear();
        });
    }

    private ReputationCoreClient() {}
}
