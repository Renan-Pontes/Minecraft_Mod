package com.renanpontes.isekaimahou.feature.ui_grimoire.client;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * Botão no estilo arcano das abas (fundo escuro, borda colorida, brilho ao passar o mouse).
 * Com ícone: mostra ícone + rótulo quando cabe e só o ícone quando não cabe (o texto nunca é cortado;
 * o nome completo fica sempre na dica).
 */
public class RuneButton extends AbstractButton {
    private final Runnable action;
    private int accent;
    @Nullable private final Picker.Icon icon;
    /** Sem espaço para ícone + rótulo: true mostra só o rótulo (botão principal); false só o ícone. */
    private boolean preferLabel;
    private Component lastTip;

    public RuneButton(int x, int y, int w, int h, Component label, int accent, Runnable action) {
        this(x, y, w, h, label, accent, null, action);
    }

    public RuneButton(int x, int y, int w, int h, Component label, int accent, @Nullable Picker.Icon icon, Runnable action) {
        super(x, y, w, h, label);
        this.accent = accent;
        this.icon = icon;
        this.action = action;
    }

    public void accent(int color) {
        this.accent = color;
    }

    public RuneButton preferLabel() {
        this.preferLabel = true;
        return this;
    }

    /** Atualiza a dica só quando muda (evita reiniciar o atraso da dica). O nome do botão vai junto quando só há ícone. */
    public void tip(Component c) {
        Component full = c;
        if (!showLabel()) {
            full = c == null ? getMessage() : Component.empty().append(getMessage().copy().withStyle(s -> s.withBold(true))).append("\n").append(c);
        }
        if (full == null) {
            if (lastTip != null) setTooltip(null);
            lastTip = null;
            return;
        }
        if (!full.equals(lastTip)) {
            setTooltip(Tooltip.create(full));
            lastTip = full;
        }
    }

    private boolean fits(boolean withIcon) {
        return Gfx.font().width(getMessage()) + 8 + (withIcon ? 12 : 0) <= getWidth();
    }

    private boolean showIcon() {
        return icon != null && (fits(true) || !(preferLabel && fits(false)));
    }

    private boolean showLabel() {
        return icon == null || fits(true) || (preferLabel && fits(false));
    }

    @Override
    public void onPress() {
        action.run();
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Font font = Gfx.font();
        // Só o mouse acende o botão: depois de um clique ele fica com o foco da tela e pareceria "preso" aceso.
        boolean hov = active && isHovered();
        boolean keyFocus = active && isFocused() && Minecraft.getInstance().getLastInputType().isKeyboard();
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        if (keyFocus) Gfx.frame(g, x - 1, y - 1, w + 2, h + 2, Gfx.GOLD);
        int border = !active ? 0xFF3A3346 : hov ? Gfx.lighten(accent, 0.45F) : accent;
        int top = !active ? 0xFF17141E : hov ? Gfx.darken(accent, 0.42F) : Gfx.darken(accent, 0.26F);
        int bottom = !active ? 0xFF110F17 : hov ? Gfx.darken(accent, 0.22F) : Gfx.darken(accent, 0.12F);
        Gfx.round(g, x, y, w, h, border);
        Gfx.roundGradient(g, x + 1, y + 1, w - 2, h - 2, top, bottom);
        if (active) g.fill(x + 2, y + 1, x + w - 2, y + 2, Gfx.alpha(0xFFFFFF, hov ? 0x50 : 0x28));
        int color = active ? (hov ? 0xFFFFFFFF : Gfx.TEXT) : 0xFF6E6680;
        boolean withIcon = showIcon();
        int iconW = withIcon ? 9 : 0;
        Component label = showLabel() ? (icon == null ? Gfx.fit(font, getMessage(), w - 6) : getMessage()) : null;
        int contentW = iconW + (label != null ? (iconW > 0 ? 3 : 0) + font.width(label) : 0);
        int cx = x + (w - contentW) / 2;
        if (withIcon) {
            if (!active) g.setColor(0.45F, 0.45F, 0.5F, 1F);
            icon.draw(g, cx, y + (h - 9) / 2, 9);
            g.setColor(1F, 1F, 1F, 1F);
            cx += iconW + 3;
        }
        if (label != null) g.drawString(font, label, cx, y + (h - 8) / 2, color, active);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}
