package com.renanpontes.isekaimahou.feature.journal.client;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/** Paleta e desenhos comuns das abas Diário e Bestiário (estilo "pergaminho mágico" sobre a Janela de Status). */
final class JournalUi {
    static final int PAPER = 0xC0181220, PAPER_DARK = 0xD00E0A14;
    static final int BORDER = 0xFF6F5A9C, BORDER_SOFT = 0x606F5A9C;
    static final int TEXT = 0xFFEDE4D0, MUTED = 0xFFA79BB8, FAINT = 0xFF6E6580;
    static final int GOLD = 0xFFFFD86B, GREEN = 0xFF86E08A, RED = 0xFFFF7A6B, BLUE = 0xFF8FC8FF;
    static final int LINE = 10;

    /** Caixa com fundo e contorno. */
    static void panel(GuiGraphics g, int x, int y, int w, int h, int border) {
        g.fill(x, y, x + w, y + h, PAPER);
        g.renderOutline(x, y, w, h, border);
    }

    /** Barra de progresso fina. */
    static void bar(GuiGraphics g, int x, int y, int w, int h, float frac, int rgb) {
        g.fill(x, y, x + w, y + h, 0x50000000);
        int fw = Math.round(w * Mth.clamp(frac, 0F, 1F));
        if (fw > 0) {
            g.fill(x, y, x + fw, y + h, 0xFF000000 | rgb);
            g.fill(x, y, x + fw, y + 1, 0x60FFFFFF);
        }
        g.renderOutline(x - 1, y - 1, w + 2, h + 2, BORDER_SOFT);
    }

    /** Texto quebrado em linhas; retorna a altura usada. */
    static int wrapped(GuiGraphics g, Font font, FormattedText text, int x, int y, int w, int color) {
        List<FormattedCharSequence> lines = font.split(text, w);
        for (int i = 0; i < lines.size(); i++) g.drawString(font, lines.get(i), x, y + i * LINE, color, false);
        return lines.size() * LINE;
    }

    /** Corta o texto com "…" para caber na largura. */
    static FormattedCharSequence ellipsize(Font font, Component text, int w) {
        if (font.width(text) <= w) return text.getVisualOrderText();
        FormattedText cut = font.substrByWidth(text, Math.max(0, w - font.width("…")));
        return FormattedCharSequence.composite(net.minecraft.locale.Language.getInstance().getVisualOrder(cut),
                FormattedCharSequence.forward("…", net.minecraft.network.chat.Style.EMPTY));
    }

    /** Barra de rolagem vertical (só aparece se houver o que rolar). */
    static void scrollbar(GuiGraphics g, int x, int top, int bottom, int scroll, int maxScroll, int contentH) {
        if (maxScroll <= 0) return;
        int vh = bottom - top;
        int barH = Math.max(12, vh * vh / Math.max(1, contentH));
        int barY = top + (int) ((vh - barH) * (scroll / (float) maxScroll));
        g.fill(x, top, x + 2, bottom, 0x40000000);
        g.fill(x, barY, x + 2, barY + barH, BORDER);
    }

    static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Cor ARGB mais escura/clara a partir de RGB. */
    static int shade(int rgb, float f) {
        int r = Mth.clamp((int) (((rgb >> 16) & 255) * f), 0, 255);
        int gg = Mth.clamp((int) (((rgb >> 8) & 255) * f), 0, 255);
        int b = Mth.clamp((int) ((rgb & 255) * f), 0, 255);
        return 0xFF000000 | (r << 16) | (gg << 8) | b;
    }

    private JournalUi() {}
}
