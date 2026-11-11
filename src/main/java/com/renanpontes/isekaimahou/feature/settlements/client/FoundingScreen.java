package com.renanpontes.isekaimahou.feature.settlements.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.settlements.SettlementStyle;
import com.renanpontes.isekaimahou.feature.settlements.network.SettlementsNetwork;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Carta de Fundação: escolha do estilo (Reino, Desértico, Nórdico — gravura, descrição e bônus de cada um) e do nome
 * da cidade. Confirmar manda o pedido; o servidor confere tudo de novo e funda.
 */
public class FoundingScreen extends Screen {
    private static final int MAX_W = 480, MAX_H = 270;
    private final BlockPos pos;
    private SettlementStyle chosen = SettlementStyle.KINGDOM;
    private EditBox name;
    private int left, top, pw, ph;

    public FoundingScreen(BlockPos pos) {
        super(Component.translatable("settlements.isekai_mahou.found.title"));
        this.pos = pos;
    }

    @Override
    protected void init() {
        pw = Math.min(MAX_W, width - 16);
        ph = Math.min(MAX_H, height - 16);
        left = (width - pw) / 2;
        top = (height - ph) / 2;
        String old = name == null ? Component.translatable("settlements.isekai_mahou.found.default_name",
                minecraft != null && minecraft.player != null ? minecraft.player.getGameProfile().getName() : "").getString() : name.getValue();
        name = new EditBox(font, left + 12, top + ph - 26, pw - 200, 16, Component.translatable("settlements.isekai_mahou.found.name"));
        name.setMaxLength(32);
        name.setValue(old);
        addRenderableWidget(name);
        addRenderableWidget(Button.builder(Component.translatable("settlements.isekai_mahou.found.confirm"), b -> confirm())
                .bounds(left + pw - 182, top + ph - 28, 96, 20).build());
        addRenderableWidget(Button.builder(net.minecraft.network.chat.CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(left + pw - 82, top + ph - 28, 70, 20).build());
    }

    private void confirm() {
        PacketDistributor.sendToServer(new SettlementsNetwork.Found(pos, chosen.id, name.getValue()));
        onClose();
    }

    private int cardW() {
        return (pw - 32) / 3;
    }

    /** Tudo é desenhado no fundo: os widgets (nome, botões) ficam por cima. */
    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        g.fillGradient(left, top, left + pw, top + ph, 0xF4211810, 0xF4140E09);
        frame(g, left, top, pw, ph, 0xFFC9A55A);
        Font f = font;
        Component title = Component.translatable("settlements.isekai_mahou.found.title");
        g.drawString(f, title, left + (pw - f.width(title)) / 2, top + 6, 0xFFFFD86B, true);
        int cw = cardW(), ch = ph - 58;
        for (int i = 0; i < 3; i++) {
            SettlementStyle s = SettlementStyle.values()[i];
            int cx = left + 8 + i * (cw + 8), cy = top + 20;
            boolean sel = s == chosen;
            boolean hover = mx >= cx && my >= cy && mx < cx + cw && my < cy + ch;
            g.fill(cx, cy, cx + cw, cy + ch, sel ? 0xE0302418 : hover ? 0xD0281E14 : 0xC0201810);
            frame(g, cx, cy, cw, ch, sel ? 0xFF000000 | s.color : hover ? 0xFFC9A55A : 0x70C9A55A);
            int ih = Math.min(70, cw * 80 / 128);
            ResourceLocation art = IsekaiMahou.id("textures/gui/settlements/style_" + s.id + ".png");
            g.blit(art, cx + 3, cy + 3, cw - 6, ih, 0, 0, 128, 80, 128, 80);
            int ty = cy + ih + 6;
            g.drawString(f, s.displayName(), cx + 5, ty, 0xFF000000 | s.color, true);
            ty += 12;
            for (FormattedCharSequence line : f.split(s.description(), cw - 10)) {
                if (ty > cy + ch - 30) break;
                g.drawString(f, line, cx + 5, ty, 0xFFB8A27A, false);
                ty += 9;
            }
            ty += 3;
            for (FormattedCharSequence line : f.split(s.bonus(), cw - 10)) {
                if (ty > cy + ch - 10) break;
                g.drawString(f, line, cx + 5, ty, 0xFFFFD86B, false);
                ty += 9;
            }
        }
        g.drawString(f, Component.translatable("settlements.isekai_mahou.found.name"), left + 12, top + ph - 38, 0xFFB8A27A, false);
    }

    private static void frame(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c);
        g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y, x + 1, y + h, c);
        g.fill(x + w - 1, y, x + w, y + h, c);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int cw = cardW(), ch = ph - 58;
        for (int i = 0; i < 3; i++) {
            int cx = left + 8 + i * (cw + 8), cy = top + 20;
            if (mx >= cx && my >= cy && mx < cx + cw && my < cy + ch) {
                chosen = SettlementStyle.values()[i];
                if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
