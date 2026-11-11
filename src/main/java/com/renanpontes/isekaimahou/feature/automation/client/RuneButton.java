package com.renanpontes.isekaimahou.feature.automation.client;

import java.util.function.IntConsumer;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * Botãozinho no estilo das máquinas arcanas (fundo violeta, borda dourada). Clique esquerdo = {@code 0},
 * direito = {@code 1} (para voltar uma opção). O texto pode mudar a cada quadro ({@code label}).
 */
public class RuneButton extends AbstractButton {
    private static final int BG = 0xFF231A33, BG_HOVER = 0xFF33264A, BORDER = 0xFF7A5C1C, BORDER_HOVER = 0xFFF2D06B;
    private static final int TEXT = 0xFFEDE3FF, TEXT_OFF = 0xFF7D7390;

    private final Supplier<Component> label;
    private final IntConsumer action;
    private int lastButton;

    public RuneButton(int x, int y, int w, int h, Supplier<Component> label, IntConsumer action) {
        super(x, y, w, h, label.get());
        this.label = label;
        this.action = action;
    }

    @Override
    protected boolean isValidClickButton(int button) {
        return button == 0 || button == 1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        lastButton = button;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onPress() {
        action.accept(lastButton);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean hover = active && isHoveredOrFocused();
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        g.fill(x, y, x + w, y + h, hover ? BORDER_HOVER : BORDER);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, hover ? BG_HOVER : BG);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x30FFFFFF);
        Font font = Minecraft.getInstance().font;
        Component text = label.get();
        setMessage(text);
        int tw = font.width(text);
        int tx = x + (w - tw) / 2 + 1;
        int ty = y + (h - 8) / 2 + 1;
        g.drawString(font, text, tx, ty, active ? (hover ? 0xFFFFF2B0 : TEXT) : TEXT_OFF, false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}
