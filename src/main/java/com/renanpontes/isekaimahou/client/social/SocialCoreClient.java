package com.renanpontes.isekaimahou.client.social;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.client.hud.HudLayout;
import com.renanpontes.isekaimahou.client.menu.MenuTabs;
import com.renanpontes.isekaimahou.core.social.ClientSocialView;
import com.renanpontes.isekaimahou.core.social.SocialCore;
import com.renanpontes.isekaimahou.core.social.SocialPayloads;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Cliente do núcleo v1.0 — A2: roda de interação (Agachar + scroll olhando para outro jogador), tela de troca, perfil,
 * HUD de grupo, avisos de pedidos/duelo/arena, aba "Grupo" do menu B e marcas nos nomes (grupo/inimigo/duelo).
 * Release 1.0 (grupo melhorado): marcador dos amigos no HUD ({@link PartyMarkers}) e no mapa do Xaero
 * ({@link XaeroPartyTracker}).
 */
public final class SocialCoreClient {
    public static void init(IEventBus modBus) {
        modBus.addListener((RegisterMenuScreensEvent e) -> e.register(SocialCore.TRADE_MENU.get(), TradeScreen::new));
        modBus.addListener((RegisterGuiLayersEvent e) -> {
            e.registerBelow(VanillaGuiLayers.CHAT, IsekaiMahou.id("social_party_hud"), new PartyHud());
            e.registerBelow(VanillaGuiLayers.CHAT, IsekaiMahou.id("social_status"), new SocialStatus());
            // Release 1.0: marcador dos amigos (por baixo dos cartões do HUD)
            e.registerBelow(IsekaiMahou.id("social_party_hud"), IsekaiMahou.id("social_party_markers"), new PartyMarkers());
        });
        MenuTabs.register("party", 57, Component.translatable("screen.isekai_mahou.tab.party"), SocialTab::new);
        HudLayout.reserve(HudLayout.Region.ARENA, SocialStatus.RESERVE_ID, SocialStatus::height);

        SocialPayloads.onProfile = p -> Minecraft.getInstance().execute(() -> ProfileScreen.open(p));
        SocialPayloads.onState = s -> Minecraft.getInstance().execute(() -> SocialStatus.onState(s));

        IEventBus bus = NeoForge.EVENT_BUS;
        // Depois dos diálogos/cutscenes (HIGH/HIGHEST, que cancelam o scroll quando ativos): Agachar + scroll num jogador
        bus.addListener(EventPriority.NORMAL, InputEvent.MouseScrollingEvent.class, InteractionWheelScreen::onScroll);
        bus.addListener(RenderNameTagEvent.class, NameTags::onNameTag);
        bus.addListener(RenderLevelStageEvent.class, PartyMarkers::onLevel);
        bus.addListener(ClientTickEvent.Post.class, e -> {
            if (Minecraft.getInstance().level == null) return;
            PartyHud.tick();
            SocialStatus.tick();
            XaeroPartyTracker.tick();
        });
        bus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> {
            ClientSocialView.reset();
            SocialStatus.clear();
            PartyHud.clear();
            PartyMarkers.clear();
            XaeroPartyTracker.clear();
        });
    }

    private SocialCoreClient() {}
}
