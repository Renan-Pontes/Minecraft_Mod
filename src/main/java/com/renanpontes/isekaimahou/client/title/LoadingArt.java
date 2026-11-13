package com.renanpontes.isekaimahou.client.title;

import com.renanpontes.isekaimahou.core.MahouConfig;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Telas de carregamento com a cara do mod (v1.0 RELEASE): criar/abrir mundo, entrar num servidor, "carregando
 * terreno", "salvando o mundo". Nada é trocado — as telas do vanilla continuam fazendo o trabalho delas; por cima do
 * FUNDO entra o céu mágico do menu principal ({@link TitleSky}), o logo pequeno no alto e, embaixo, uma dica de jogo
 * ({@link GameTips}).
 * <ul>
 *   <li>O céu usa {@code ScreenEvent.BackgroundRendered} (entre o fundo e o conteúdo da tela). A tela "carregando
 *       terreno" desenha o próprio fundo (portal do Nether/End) sem esse evento: nela ficam só o logo e a dica.</li>
 *   <li>Segue a opção do menu principal ({@code customTitleScreen}); as dicas, a opção {@code loadingTips}.</li>
 * </ul>
 */
final class LoadingArt {
    private static final TitleSky SKY = new TitleSky();
    private static final float LOGO_SCALE = 1.5F;

    private LoadingArt() {}

    static void init() {
        NeoForge.EVENT_BUS.addListener(ScreenEvent.BackgroundRendered.class, LoadingArt::onBackground);
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Render.Post.class, LoadingArt::onRenderPost);
    }

    private static boolean loading(Screen s) {
        return s instanceof LevelLoadingScreen || s instanceof ReceivingLevelScreen || s instanceof ProgressScreen
                || s instanceof ConnectScreen || s instanceof GenericMessageScreen;
    }

    private static boolean artEnabled() {
        return MahouConfig.safe(MahouConfig.CUSTOM_TITLE_SCREEN);
    }

    private static float seconds() {
        return (Util.getMillis() % 3_600_000L) / 1000F;
    }

    private static void onBackground(ScreenEvent.BackgroundRendered e) {
        Screen s = e.getScreen();
        if (!loading(s) || !artEnabled()) return;
        GuiGraphics g = e.getGuiGraphics();
        Font font = Minecraft.getInstance().font;
        // Em lote, como no menu principal (o céu são muitos retângulos pequenos). Sem paralaxe: mouse no centro.
        g.drawManaged(() -> SKY.render(g, font, s.width, s.height, s.width / 2, s.height / 2, s.height / 2, 1F, seconds()));
    }

    private static void onRenderPost(ScreenEvent.Render.Post e) {
        Screen s = e.getScreen();
        if (!loading(s)) return;
        GuiGraphics g = e.getGuiGraphics();
        Font font = Minecraft.getInstance().font;
        // Logo pequeno no alto — só com altura sobrando (o mapa de chunks da criação de mundo ocupa o meio)
        boolean roomForLogo = s.height >= (s instanceof LevelLoadingScreen ? 320 : 200);
        if (artEnabled() && roomForLogo) TitleLogo.render(g, font, s.width, 12, LOGO_SCALE, 0.9F, seconds());
        GameTips.draw(g, font, s.width / 2, s.height - 16, Math.min(360, s.width - 40), 3, 1F, true);
    }
}
