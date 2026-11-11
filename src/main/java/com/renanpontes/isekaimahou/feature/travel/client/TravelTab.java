package com.renanpontes.isekaimahou.feature.travel.client;

import com.renanpontes.isekaimahou.client.menu.MenuTab;
import com.renanpontes.isekaimahou.client.menu.StatusScreen;
import com.renanpontes.isekaimahou.client.onboard.ClientTravel;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Aba "Viagem" do menu B: viajar de qualquer lugar para um destino já visitado (custo ×3 e recarga de 10 min, regras
 * do núcleo). Ao abrir, pede a lista ao servidor.
 */
public class TravelTab implements MenuTab {
    /** Evita pedir a lista várias vezes seguidas (trocar de aba/redimensionar). */
    private static long lastRequest;

    private final TravelView view = new TravelView(false);

    @Override
    public void init(StatusScreen screen, int x, int y, int w, int h) {
        view.init(x, y, w, h, screen::addTabWidget);
        long now = Util.getMillis();
        ClientTravel.Menu m = ClientTravel.menu();
        if (m == null || m.fromStone() || now - lastRequest > 1000) {
            lastRequest = now;
            ClientTravel.requestMenu();
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        view.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void tick() {
        view.tick();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        return view.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        return view.mouseScrolled(mx, my, scrollY);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        return view.keyPressed(key);
    }

    @Override
    public void removed() {
        view.removed();
    }
}
