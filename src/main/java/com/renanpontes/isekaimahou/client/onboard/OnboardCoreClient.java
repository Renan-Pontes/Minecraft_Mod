package com.renanpontes.isekaimahou.client.onboard;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.client.CoreKeys;

import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Cliente do núcleo v0.9 — A2: caixa de diálogo ({@link DialogueClient}), letreiro de parabéns ({@link CongratsOverlay}),
 * avisos guardados da entrada no jogo ({@link ClientOnboarding}), lista de viagem ({@link ClientTravel}) e clarão de
 * viagem ({@link TravelFlash}). Só no cliente.
 */
public final class OnboardCoreClient {
    public static void init(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent e) -> e.register(CoreKeys.DIALOGUE_NEXT));
        modBus.addListener((RegisterGuiLayersEvent e) -> {
            // Diálogo embaixo do chat (mensagens novas por cima); parabéns e clarão por cima de tudo
            e.registerBelow(VanillaGuiLayers.CHAT, IsekaiMahou.id("core_onboard_dialogue"), DialogueClient::render);
            e.registerAboveAll(IsekaiMahou.id("core_onboard_congrats"), CongratsOverlay::render);
            e.registerAboveAll(IsekaiMahou.id("core_onboard_travel_flash"), TravelFlash::render);
        });

        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener((ClientTickEvent.Post e) -> {
            ClientTravel.tick();
            TravelFlash.tick();
            if (Minecraft.getInstance().player == null) return;
            ClientOnboarding.tick();
            CongratsOverlay.tick();
            DialogueClient.tick();
        });
        bus.addListener(DialogueClient::onKey);
        bus.addListener(DialogueClient::onMouse);
        bus.addListener(EventPriority.HIGH, InputEvent.MouseScrollingEvent.class, DialogueClient::onScroll);
        bus.addListener(DialogueClient::onScreenOpen);
        bus.addListener(DialogueClient::onScreenClick);
        bus.addListener(DialogueClient::renderOverScreen);
        bus.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            DialogueClient.clear();
            CongratsOverlay.clear();
            TravelFlash.clear();
            ClientTravel.clear();
            ClientOnboarding.onLogout();
        });
    }

    private OnboardCoreClient() {}
}
