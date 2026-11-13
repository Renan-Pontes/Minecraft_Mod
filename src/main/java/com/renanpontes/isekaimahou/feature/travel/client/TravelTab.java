package com.renanpontes.isekaimahou.feature.travel.client;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.client.menu.MenuTab;
import com.renanpontes.isekaimahou.client.menu.StatusScreen;
import com.renanpontes.isekaimahou.client.onboard.ClientTravel;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Aba "Viagem" do menu B, com duas sub-abas (v1.0):
 * <ul>
 *   <li><b>Destinos</b> — viajar de qualquer lugar para um destino já visitado (custo ×3 e recarga de 10 min, regras do
 *       núcleo). Ao abrir, pede a lista ao servidor.</li>
 *   <li><b>Guia de Portais</b> — cada dimensão do mod: onde fica o portal, como abrir, requisitos e "rastrear o mais
 *       próximo" ({@link PortalGuideView}).</li>
 * </ul>
 */
public class TravelTab implements MenuTab {
    /** Altura da faixa das sub-abas. */
    private static final int STRIP_H = 14;
    private static final String[] PAGE_KEYS = {"screen.isekai_mahou.travel.page.destinations", "screen.isekai_mahou.travel.page.portals"};
    /** Evita pedir a lista várias vezes seguidas (trocar de aba/redimensionar). */
    private static long lastRequest;
    /** Sub-aba aberta (lembrada enquanto o jogo está aberto): 0 = Destinos, 1 = Guia de Portais. */
    private static int page;

    private final TravelView view = new TravelView(false);
    private final PortalGuideView guide = new PortalGuideView();
    @Nullable
    private StatusScreen screen;
    private int x, y, w;
    private final int[] chipX = new int[PAGE_KEYS.length], chipW = new int[PAGE_KEYS.length];

    /** Abre o menu B direto no Guia de Portais (atalho para outros módulos/teclas). */
    public static void openGuide() {
        page = 1;
        StatusScreen.open("travel");
    }

    @Override
    public void init(StatusScreen screen, int x, int y, int w, int h) {
        this.screen = screen;
        this.x = x;
        this.y = y;
        this.w = w;
        Font font = Minecraft.getInstance().font;
        int cx = x + 2;
        for (int i = 0; i < PAGE_KEYS.length; i++) {
            chipX[i] = cx;
            chipW[i] = font.width(Component.translatable(PAGE_KEYS[i])) + 14;
            cx += chipW[i] + 4;
        }
        int top = y + STRIP_H, height = h - STRIP_H;
        if (page == 1) {
            guide.init(x, top, w, height);
            return;
        }
        view.init(x, top, w, height, screen::addTabWidget);
        long now = Util.getMillis();
        ClientTravel.Menu m = ClientTravel.menu();
        if (m == null || m.fromStone() || now - lastRequest > 1000) {
            lastRequest = now;
            ClientTravel.requestMenu();
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        for (int i = 0; i < PAGE_KEYS.length; i++) {
            boolean on = page == i;
            boolean hov = !on && mouseX >= chipX[i] && mouseX < chipX[i] + chipW[i] && mouseY >= y && mouseY < y + STRIP_H - 2;
            int base = on ? 0xFF2A2440 : 0xFF14101E;
            MahouUi.roundRect(g, chipX[i], y, chipW[i], STRIP_H - 2, hov ? MahouUi.lighten(base, 0.15F) : base, MahouUi.darken(base, 0.3F));
            MahouUi.roundOutline(g, chipX[i], y, chipW[i], STRIP_H - 2, on ? MahouUi.GOLD : MahouUi.alpha(MahouUi.GOLD_DIM, 0xA0));
            g.drawString(font, Component.translatable(PAGE_KEYS[i]), chipX[i] + 7, y + 2, on ? MahouUi.GOLD_PALE : MahouUi.TEXT_DIM, false);
        }
        if (page == 1) guide.render(g, mouseX, mouseY, partialTick);
        else view.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void tick() {
        if (page == 0) view.tick();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && my >= y && my < y + STRIP_H - 2) {
            for (int i = 0; i < PAGE_KEYS.length; i++) {
                if (mx < chipX[i] || mx >= chipX[i] + chipW[i]) continue;
                if (page != i) {
                    page = i;
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.2F, 0.35F));
                    // Recria os widgets da aba (o botão "Viajar" só existe em Destinos)
                    if (screen != null) screen.refreshTab();
                }
                return true;
            }
        }
        return page == 1 ? guide.mouseClicked(mx, my, button) : view.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        return page == 1 ? guide.mouseScrolled(mx, my, scrollY) : view.mouseScrolled(mx, my, scrollY);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        return page == 1 ? guide.keyPressed(key) : view.keyPressed(key);
    }

    @Override
    public void removed() {
        view.removed();
    }
}
