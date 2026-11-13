package com.renanpontes.isekaimahou.feature.class_gambler.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.client.menu.MenuTabs;
import com.renanpontes.isekaimahou.client.menu.StatusScreen;
import com.renanpontes.isekaimahou.feature.class_gambler.GbNet;
import com.renanpontes.isekaimahou.feature.class_gambler.GbRegistries;
import com.renanpontes.isekaimahou.feature.class_gambler.item.GamblerDeckItem;
import com.renanpontes.isekaimahou.feature.class_gambler.outfit.GamblerOutfitItem;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/** Parte de cliente do Apostador: renderers, HUD da sorte/Jackpot/Rodada, aba "Mesa da Casa" e o traje 3D. */
public class ClassGamblerClient implements ClientModule {
    public static final String TAB = "casino";

    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener(ClassGamblerClient::renderers);
        modBus.addListener(ClassGamblerClient::layers);
        GbFxClient.init();
        GbNet.onRoll = GbClientState::onRoll;
        GbNet.onJackpot = GbClientState::onJackpot;
        GbNet.onChallenge = GbClientState::onChallenge;
        GamblerOutfitItem.renderFactory = GamblerOutfitRenderer.Provider::new;
        GamblerDeckItem.openTable = () -> {
            if (Minecraft.getInstance().screen == null) StatusScreen.open(TAB);
        };
        MenuTabs.register(TAB, 65, Component.translatable("screen.isekai_mahou.gambler.tab"), CasinoTab::new);
        RollHud.init();
        NeoForge.EVENT_BUS.addListener(GbClientState::tick);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> GbClientState.reset());
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(GbRegistries.THROWN_CARD.get(), ThrownCardRenderer::new);
        e.registerEntityRenderer(GbRegistries.DICE.get(), GbGeoRenderers.DiceRenderer::new);
        e.registerEntityRenderer(GbRegistries.ROULETTE.get(), GbGeoRenderers.RouletteRenderer::new);
        e.registerEntityRenderer(GbRegistries.SLOT_MACHINE.get(), GbGeoRenderers.SlotMachineRenderer::new);
        e.registerEntityRenderer(GbRegistries.CASINO_DOMAIN.get(), GbGeoRenderers.CasinoRenderer::new);
        e.registerEntityRenderer(GbRegistries.IDLE_DEATH.get(), GbGeoRenderers.IdleDeathRenderer::new);
        e.registerEntityRenderer(GbRegistries.GUM_TETHER.get(), GumTetherRenderer::new);
    }

    private static void layers(RegisterGuiLayersEvent e) {
        e.registerAbove(VanillaGuiLayers.CROSSHAIR, IsekaiMahou.id("class_gambler_hud"), RollHud::render);
    }
}
