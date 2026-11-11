package com.renanpontes.isekaimahou.client.title;

import com.renanpontes.isekaimahou.core.MahouConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Troca o menu principal do vanilla pelo {@link MahouTitleScreen} ({@code ScreenEvent.Opening}): só quando a tela que
 * vai abrir é EXATAMENTE o {@link TitleScreen} do vanilla (outros mods que já trocaram por uma tela própria ficam como
 * estão), fora do modo demo e com a config {@code customTitleScreen} ligada. O botão "Menu clássico" volta ao vanilla
 * até fechar o jogo; no clássico aparece um botão "Isekai Mahou" para voltar.
 */
public final class TitleHook {
    /** O jogador escolheu o menu clássico nesta sessão do jogo. */
    private static boolean classic;
    /** O primeiro menu da sessão entra com o fundo aparecendo devagar (como o do vanilla). */
    private static boolean shownOnce;

    public static void init() {
        // Prioridade baixa: se outro mod trocar o menu antes, a tela dele vale
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, ScreenEvent.Opening.class, TitleHook::onOpening);
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Init.Post.class, TitleHook::onInit);
    }

    private static boolean enabled() {
        return MahouConfig.safe(MahouConfig.CUSTOM_TITLE_SCREEN);
    }

    private static void onOpening(ScreenEvent.Opening e) {
        Screen next = e.getNewScreen();
        if (next == null || next.getClass() != TitleScreen.class) return;
        if (classic || !enabled() || Minecraft.getInstance().isDemo()) return;
        e.setNewScreen(new MahouTitleScreen(!shownOnce));
        shownOnce = true;
    }

    /** No menu clássico (escolhido pelo jogador), um botão pequeno para voltar ao menu do Isekai Mahou. */
    private static void onInit(ScreenEvent.Init.Post e) {
        Screen s = e.getScreen();
        if (s.getClass() != TitleScreen.class || !classic || !enabled() || Minecraft.getInstance().isDemo()) return;
        Component label = Component.translatable("title.isekai_mahou.back_to_mahou");
        int w = Minecraft.getInstance().font.width(label) + 16;
        Button b = Button.builder(label, btn -> openMahou())
                .bounds(s.width - w - 4, 4, w, 16)
                .tooltip(Tooltip.create(Component.translatable("title.isekai_mahou.back_to_mahou.tooltip")))
                .build();
        e.addListener(b);
    }

    /** "Menu clássico": o vanilla até o fim da sessão (ou até clicar em "Isekai Mahou"). */
    static void openClassic() {
        classic = true;
        Minecraft.getInstance().setScreen(new TitleScreen());
    }

    static void openMahou() {
        classic = false;
        Minecraft.getInstance().setScreen(new MahouTitleScreen(false));
    }

    private TitleHook() {}
}
