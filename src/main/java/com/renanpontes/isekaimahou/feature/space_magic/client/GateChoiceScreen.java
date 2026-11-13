package com.renanpontes.isekaimahou.feature.space_magic.client;

import java.util.List;

import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.feature.space_magic.SpaceGates;
import com.renanpontes.isekaimahou.feature.space_magic.network.GateChoicePayload;
import com.renanpontes.isekaimahou.feature.space_magic.network.GateSelectPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Tela do Abrir Portal: um botão por dimensão já visitada. Escolher manda o pedido ao servidor; fechar (Esc ou
 * "Cancelar") avisa que desistiu.
 */
public class GateChoiceScreen extends Screen {
    private static final int BTN_W = 200, BTN_H = 20, GAP = 4;
    private final List<String> dims;
    private final int color;
    private boolean answered;

    private GateChoiceScreen(List<String> dims, int color) {
        super(Component.translatable("screen.isekai_mahou.space_magic.gate_title"));
        this.dims = dims;
        this.color = 0xFF000000 | color;
    }

    /** Chamado pelo pacote do servidor (já na thread do cliente). */
    public static void open(GateChoicePayload p) {
        Minecraft.getInstance().setScreen(new GateChoiceScreen(p.dims(), p.color()));
    }

    private int panelH() {
        return 46 + (dims.size() + 1) * (BTN_H + GAP);
    }

    @Override
    protected void init() {
        int x = (width - BTN_W) / 2;
        int y = (height - panelH()) / 2 + 36;
        for (String id : dims) {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            Component name = rl == null ? Component.literal(id) : SpaceGates.dimName(rl);
            addRenderableWidget(Button.builder(name, b -> answer(id)).bounds(x, y, BTN_W, BTN_H).build());
            y += BTN_H + GAP;
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(x + 50, y + 2, BTN_W - 100, BTN_H).build());
    }

    private void answer(String id) {
        answered = true;
        PacketDistributor.sendToServer(new GateSelectPayload(id));
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public void onClose() {
        if (!answered) {
            answered = true;
            PacketDistributor.sendToServer(new GateSelectPayload(""));
        }
        super.onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        int w = BTN_W + 24, h = panelH();
        int x = (width - w) / 2, y = (height - h) / 2;
        MahouUi.frame(g, x, y, w, h, color);
        g.drawCenteredString(font, title, width / 2, y + 10, MahouUi.GOLD);
        g.drawCenteredString(font, Component.translatable("screen.isekai_mahou.space_magic.gate_hint", SpaceGates.GATE_LIFE / 20),
                width / 2, y + 22, MahouUi.TEXT_DIM);
        super.render(g, mouseX, mouseY, pt);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float pt) {
        renderTransparentBackground(g);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
