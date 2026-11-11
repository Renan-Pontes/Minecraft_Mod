package com.renanpontes.isekaimahou.feature.settlements.client;

import com.renanpontes.isekaimahou.client.menu.MenuTab;
import com.renanpontes.isekaimahou.client.menu.StatusScreen;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Aba "Cidade" do menu B: a cidade do jogador (a própria, a que governa, a de lealdade ou onde está). Governa de longe
 * (decretos, plantas, guerra); retirar moedas do cofre só no coração.
 */
public class SettlementTab implements MenuTab {
    private static long lastRequest;
    private final SettlementPanel panel = new SettlementPanel();

    @Override
    public void init(StatusScreen screen, int x, int y, int w, int h) {
        panel.init(x, y, w, h);
        long now = Util.getMillis();
        if (now - lastRequest > 1000) {
            lastRequest = now;
            ClientSettlements.request(null);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        panel.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        return panel.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        return panel.mouseScrolled(mx, my, scrollY);
    }
}
