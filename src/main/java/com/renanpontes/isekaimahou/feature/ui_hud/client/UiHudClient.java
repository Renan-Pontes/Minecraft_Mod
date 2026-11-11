package com.renanpontes.isekaimahou.feature.ui_hud.client;

import java.util.function.Consumer;
import java.util.function.IntSupplier;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.client.CoreHud;
import com.renanpontes.isekaimahou.client.hud.HudLayout;
import com.renanpontes.isekaimahou.client.menu.MenuTabs;
import com.renanpontes.isekaimahou.client.stack.StackHud;
import com.renanpontes.isekaimahou.client.status.ClientStatuses;
import com.renanpontes.isekaimahou.module.ClientModule;
import com.renanpontes.isekaimahou.network.ClientHooks;
import com.renanpontes.isekaimahou.network.CorePayloads;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Parte de cliente do módulo "ui_hud": HUD completo, Olho Analítico, barras de vida flutuantes, banners de
 * nível/Círculo e a aba "Status" (Janela de Status).
 */
public class UiHudClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        // Este módulo desenha o HUD completo: desliga o básico do núcleo
        CoreHud.enabled = false;
        modBus.addListener((RegisterGuiLayersEvent e) -> {
            // Abaixo das mensagens da barra de ação, títulos e chat (que devem ficar por cima do HUD)
            e.registerBelow(VanillaGuiLayers.OVERLAY_MESSAGE, IsekaiMahou.id("ui_hud"), new MahouHudLayer());
            // Topo central (Olho Analítico + banners) por cima do HUD do mod; desenha depois das barras de chefe
            e.registerAbove(IsekaiMahou.id("ui_hud"), IsekaiMahou.id("ui_hud_top"), new MahouTopLayer());
        });
        NeoForge.EVENT_BUS.addListener(HudState::tick);
        NeoForge.EVENT_BUS.addListener(HudState::logout);

        // Onde terminam as barras de chefe neste quadro (o painel do alvo fica logo abaixo)
        NeoForge.EVENT_BUS.addListener((RenderGuiEvent.Pre e) -> BossBarTracker.frameStart(e));
        // receiveCanceled: uma barra escondida (o Jade no modo HIDE_BOSS_BAR esconde as barras enquanto a caixa dele
        // aparece) continua reservando o espaço — senão o painel subiria/desceria conforme a mira
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, (CustomizeGuiOverlayEvent.BossEventProgress e) -> BossBarTracker.onBossBar(e));
        // Barrinhas de vida no mundo (com os ícones de condição de status; o núcleo desenha os dele quando elas estão desligadas)
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent e) -> WorldHealthBars.render(e));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> StatusView.tick(Minecraft.getInstance()));
        // v1.1: dicas de criaturas do Jade desligadas uma vez (o Olho Analítico já mostra a vida do alvo)
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn e) -> JadeCompat.disableEntityTooltipsOnce());
        // Os corações do vanilla saem: a vida do jogador é a barra numérica do HUD (HudHealthBar)
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.RenderGuiLayerEvent.Pre e) -> {
            if (!e.getName().equals(net.neoforged.neoforge.client.gui.VanillaGuiLayers.PLAYER_HEALTH)) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.gameMode != null && mc.gameMode.canHurtPlayer() && !mc.getDebugOverlay().showDebugScreen()) e.setCanceled(true);
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            HudPlace.clear();
            HudPlayerStatuses.clear();
            HudHealthBar.clear();
            JadeCompat.clear();
            BossBarTracker.clear();
            StatusView.clear();
            ClientStatuses.setWorldIconsHandledExternally(false);
        });
        // A Pilha (meio à esquerda) desvia da coluna de condições do jogador ao lado da barra de mana
        IntSupplier stackEdge = StackHud.leftEdge;
        StackHud.leftEdge = () -> Math.max(stackEdge.getAsInt(), HudPlayerStatuses.stackEdge());

        // Início/fim EXATOS da conjuração (completou x interrompida, canalizações longas). Encadeia o gancho:
        // quem registrou antes (animações, cinemática...) continua recebendo o estado.
        Consumer<CorePayloads.CastState> previous = ClientHooks.castState;
        ClientHooks.castState = p -> {
            previous.accept(p);
            HudState.onCastState(p);
        };

        // v1.0 (HudLayout): a barra de conjuração fica abaixo da mira e empurra o aviso da arena para baixo dela
        HudLayout.reserve(HudLayout.Region.ARENA, HudCastBar.RESERVE_ID, HudCastBar::reservedHeight);

        // Substitui a aba "status" padrão pela Janela de Status completa
        MenuTabs.register("status", 0, Component.translatable("screen.isekai_mahou.status_window.tab"), StatusWindowTab::new);
    }
}
