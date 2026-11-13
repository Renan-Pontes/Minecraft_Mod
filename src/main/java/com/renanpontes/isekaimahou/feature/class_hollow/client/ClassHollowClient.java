package com.renanpontes.isekaimahou.feature.class_hollow.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.client.PathSelectionScreen;
import com.renanpontes.isekaimahou.client.menu.MenuTabs;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_hollow.HollowNetwork;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Cliente da classe Hollow (v1.4: + as teclas das técnicas inatas, {@link HollowInput}, e a Roda Hollow): o corpo (máscara, buraco, ossos, Resurrección) nos modelos de jogador, os efeitos
 * ({@link HollowFxClient}), a cena da hollowificação e as Almas na tela ({@link HollowOverlay}), as telas de escolha, a
 * aba "Hollow" do menu B (só para Hollow) e a dica "classe secreta" na tela inicial de classes.
 */
public class ClassHollowClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        HollowNetwork.clientFx = HollowFxClient::accept;
        HollowNetwork.clientLook = HollowLooks::accept;
        HollowNetwork.clientCinematic = HollowOverlay::accept;
        HollowNetwork.clientSense = HollowFxClient::acceptSense;
        HollowNetwork.clientTechCd = HollowInput::accept;
        HollowInput.register(modBus);
        HollowNetwork.clientOpen = p -> {
            Minecraft mc = Minecraft.getInstance();
            if (p.screen() == HollowNetwork.SCREEN_HOLLOW_CHOICE) mc.setScreen(new HollowChoiceScreen());
            else if (p.screen() == HollowNetwork.SCREEN_RELEASE) mc.setScreen(new ReleaseScreen());
        };
        NeoForge.EVENT_BUS.addListener(HollowFxClient::tick);
        NeoForge.EVENT_BUS.addListener(HollowFxClient::render);
        NeoForge.EVENT_BUS.addListener(HollowFxClient::onCamera);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            HollowFxClient.clear();
            HollowLooks.clear();
            HollowOverlay.clear();
            HollowInput.clear();
        });
        NeoForge.EVENT_BUS.addListener(ClassHollowClient::onScreenRender);
        modBus.addListener(ClassHollowClient::onLayers);
        modBus.addListener(ClassHollowClient::onGuiLayers);
        MenuTabs.register("hollowfied", 88, Component.translatable("tab.isekai_mahou.hollow"), HollowTab::new);
        MenuTabs.setClassTab("hollowfied", MagicPath.HOLLOW);
    }

    @SuppressWarnings("unchecked")
    private static void onLayers(EntityRenderersEvent.AddLayers e) {
        for (PlayerSkin.Model skin : e.getSkins()) {
            if (e.getSkin(skin) instanceof LivingEntityRenderer<?, ?> r) {
                var pr = (LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>) r;
                pr.addLayer(new HollowBodyLayer(pr));
            }
        }
    }

    private static void onGuiLayers(RegisterGuiLayersEvent e) {
        e.registerAbove(VanillaGuiLayers.HOTBAR, IsekaiMahou.id("hollow_souls"), HollowOverlay::renderHud);
        e.registerAboveAll(IsekaiMahou.id("hollow_cinematic"), HollowOverlay::renderScene);
    }

    /** Tela inicial de classes: o Hollow não se escolhe — só a dica de como se chega a ele. */
    private static void onScreenRender(ScreenEvent.Render.Post e) {
        if (true) return; // a dica agora fica no rodapé da própria tela (com o Shinigami selecionado)
        if (!(e.getScreen() instanceof PathSelectionScreen s)) return;
        GuiGraphics g = e.getGuiGraphics();
        Font font = Minecraft.getInstance().font;
        Component line = Component.translatable("screen.isekai_mahou.hollow.locked_line", MagicPath.HOLLOW.displayName(),
                MagicPath.HOLLOW.lockedHint());
        // uma linha só, no rodapé (a tela deixa ~20 px livres embaixo do painel)
        FormattedCharSequence l = MahouUi.fit(font, line, Math.min(s.width - 16, 460));
        g.drawCenteredString(font, l, s.width / 2, s.height - 12, MahouUi.alpha(0xFFB8A89A, 220));
    }
}
