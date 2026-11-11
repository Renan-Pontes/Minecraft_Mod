package com.renanpontes.isekaimahou.feature.settlements.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Tela do coração da cidade (governo, Juramento ou informações), com o mesmo painel da aba "Cidade" do menu B. */
public class SettlementScreen extends Screen {
    private static final int MAX_W = 520, MAX_H = 300;
    private final SettlementPanel panel = new SettlementPanel();
    private int left, top, pw, ph;

    public SettlementScreen() {
        super(Component.translatable("settlements.isekai_mahou.ui.title"));
    }

    @Override
    protected void init() {
        pw = Math.min(MAX_W, width - 16);
        ph = Math.min(MAX_H, height - 16);
        left = (width - pw) / 2;
        top = (height - ph) / 2;
        panel.init(left + 6, top + 6, pw - 12, ph - 12);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        g.fillGradient(left, top, left + pw, top + ph, 0xF4211810, 0xF4140E09);
        int border = 0xFFC9A55A;
        g.fill(left, top, left + pw, top + 1, border);
        g.fill(left, top + ph - 1, left + pw, top + ph, border);
        g.fill(left, top, left + 1, top + ph, border);
        g.fill(left + pw - 1, top, left + pw, top + ph, border);
        panel.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        return panel.mouseClicked(mx, my, button) || super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        return panel.mouseScrolled(mx, my, sy) || super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
