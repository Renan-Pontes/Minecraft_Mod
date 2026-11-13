package com.renanpontes.isekaimahou.feature.class_hollow.client;

import java.util.List;

import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.feature.class_hollow.HollowNetwork;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * SÓ CLIENTE. Antes da luta contra o Vazio Interior: "Lutar contra ele" ou "Aceitar o hollow interior". Aceitar pede
 * CONFIRMAÇÃO (aviso vermelho: é permanente — vira a classe Hollow, perde a zanpakutō). Fechar sem escolher não faz nada
 * (falar com o espírito de novo reabre).
 */
public class HollowChoiceScreen extends Screen {
    private static final int W = 300, H = 170;
    private boolean confirming;

    public HollowChoiceScreen() {
        super(Component.translatable("screen.isekai_mahou.hollow.choice.title"));
    }

    @Override
    protected void init() {
        clearWidgets();
        int left = (width - W) / 2, top = (height - H) / 2;
        int bw = (W - 30) / 2;
        if (!confirming) {
            addRenderableWidget(Button.builder(Component.translatable("screen.isekai_mahou.hollow.choice.fight"), b -> send(HollowNetwork.CHOICE_FIGHT))
                    .bounds(left + 10, top + H - 30, bw, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("screen.isekai_mahou.hollow.choice.accept").withColor(0xFF6070), b -> {
                confirming = true;
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_HEARTBEAT, 0.8F, 1.0F));
                init();
            }).bounds(left + 20 + bw, top + H - 30, bw, 20).build());
        } else {
            addRenderableWidget(Button.builder(Component.translatable("screen.isekai_mahou.hollow.choice.back"), b -> {
                confirming = false;
                init();
            }).bounds(left + 10, top + H - 30, bw, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("screen.isekai_mahou.hollow.choice.confirm").withColor(0xFF3040),
                    b -> send(HollowNetwork.CHOICE_ACCEPT)).bounds(left + 20 + bw, top + H - 30, bw, 20).build());
        }
    }

    private void send(int option) {
        PacketDistributor.sendToServer(new HollowNetwork.Choice(option, 0));
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(
                option == HollowNetwork.CHOICE_ACCEPT ? SoundEvents.WARDEN_ROAR : SoundEvents.ANVIL_PLACE, 0.6F, 0.8F));
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        Font font = Minecraft.getInstance().font;
        int left = (width - W) / 2, top = (height - H) / 2;
        int accent = confirming ? 0xFFE01030 : 0xFFE6DFCF;
        g.drawCenteredString(font, Component.translatable(confirming ? "screen.isekai_mahou.hollow.choice.warn_title"
                : "screen.isekai_mahou.hollow.choice.title").withColor(accent & 0xFFFFFF), width / 2, top + 10, accent);
        Component body = Component.translatable(confirming ? "screen.isekai_mahou.hollow.choice.warn" : "screen.isekai_mahou.hollow.choice.text");
        List<FormattedCharSequence> lines = font.split(body, W - 24);
        int y = top + 28;
        for (FormattedCharSequence l : lines) {
            if (y > top + H - 40) break;
            g.drawString(font, l, left + 12, y, confirming ? 0xFFFFB0B0 : MahouUi.TEXT, false);
            y += 10;
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        int left = (width - W) / 2, top = (height - H) / 2;
        int accent = confirming ? 0xFFE01030 : 0xFFE6DFCF;
        MahouUi.frame(g, left, top, W, H, accent);
        if (confirming && minecraft != null && minecraft.level != null) {
            // o Reiatsu rubro pulsando nas bordas
            float p = 0.5F + 0.5F * Mth.sin((minecraft.level.getGameTime() + pt) * 0.3F);
            g.fill(left, top, left + W, top + 2, MahouUi.alpha(0xFFE01030, (int) (120 + 120 * p)));
            g.fill(left, top + H - 2, left + W, top + H, MahouUi.alpha(0xFFE01030, (int) (120 + 120 * p)));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
