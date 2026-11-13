package com.renanpontes.isekaimahou.client.ux;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.client.hud.HudLayout;
import com.renanpontes.isekaimahou.client.hud.HudToasts;
import com.renanpontes.isekaimahou.client.reputation.ReputationNotices;
import com.renanpontes.isekaimahou.client.title.TitleHook;
import com.renanpontes.isekaimahou.network.ClientHooks;

import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Cliente do núcleo v1.0 — A3 (UX): menu principal próprio ({@code client/title}), layout fixo do HUD
 * ({@link HudLayout}: avisos de reputação/recompensa na esquerda-centro, rastreador abaixo do minimapa), borda e aviso
 * das arenas ({@link ArenaWallRenderer}, {@link ArenaNotice}) e a config do Xaero pré-semeada ({@link XaeroSeed}).
 * Ver docs/V10_DESIGN.md §2 A3 e docs/v10/API_ux.md.
 */
public final class UxCoreClient {
    public static void init(IEventBus modBus) {
        XaeroSeed.run();
        TitleHook.init();
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(VanillaTutorialOff::onLogin);
        MeditationPose.init();
        PeaceClient.init(modBus);

        // Coluna de avisos da esquerda-centro: reputação primeiro, recompensas embaixo
        HudLayout.reserve(HudLayout.Region.TOASTS, ReputationNotices.RESERVE_ID, ReputationNotices::columnHeight);
        HudLayout.reserve(HudLayout.Region.TOASTS, HudToasts.RESERVE_ID, HudToasts::columnHeight);
        // Recompensas do núcleo (Rewards.toast) saem do canto do vanilla (embaixo do minimapa) para a coluna da esquerda
        ClientHooks.toast = p -> HudToasts.push(p.title(), p.subtitle(), p.icon(), p.rgb());

        modBus.addListener((RegisterGuiLayersEvent e) -> {
            // Abaixo do chat (mensagens novas por cima)
            e.registerBelow(VanillaGuiLayers.CHAT, IsekaiMahou.id("core_ux_toasts"), new HudToasts());
            e.registerBelow(VanillaGuiLayers.CHAT, IsekaiMahou.id("core_ux_arena_notice"), new ArenaNotice());
        });

        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener((ClientTickEvent.Post e) -> {
            if (Minecraft.getInstance().level == null) return;
            HudToasts.tick();
            ArenaNotice.tick();
        });
        bus.addListener((RenderLevelStageEvent e) -> ArenaWallRenderer.onRender(e));
        bus.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            ClientArenas.clear();
            ArenaNotice.clear();
            HudToasts.clear();
            HudLayout.clearFrame();
        });
    }

    private UxCoreClient() {}
}
