package com.renanpontes.isekaimahou.client.stack;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Cliente do núcleo v0.8 — A2: HUD da Pilha ({@link StackHud}), letreiro "CONTRAMÁGICA!" ({@link StackBanner}) e o
 * círculo rúnico estilhaçando no mundo ({@link CounterShatter}). Os dados chegam pelos pacotes de
 * {@code core/stack/StackPayloads} ({@link ClientStack}).
 */
public final class StackCoreClient {
    public static void init(IEventBus modBus) {
        modBus.addListener(StackCoreClient::layers);
        NeoForge.EVENT_BUS.addListener(StackCoreClient::tick);
        NeoForge.EVENT_BUS.addListener(StackCoreClient::logout);
        NeoForge.EVENT_BUS.addListener(CounterShatter::onRender);
    }

    private static void layers(RegisterGuiLayersEvent e) {
        // Abaixo do chat (mensagens novas por cima do painel); o letreiro fica por cima de tudo
        e.registerBelow(VanillaGuiLayers.CHAT, IsekaiMahou.id("core_stack"), new StackHud());
        e.registerAboveAll(IsekaiMahou.id("core_stack_banner"), new StackBanner());
    }

    private static void tick(ClientTickEvent.Post e) {
        if (Minecraft.getInstance().level == null) return;
        ClientStack.tick();
    }

    private static void logout(ClientPlayerNetworkEvent.LoggingOut e) {
        ClientStack.clear();
        StackBanner.clear();
        CounterShatter.clear();
    }

    private StackCoreClient() {}
}
