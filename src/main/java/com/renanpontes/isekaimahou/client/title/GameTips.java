package com.renanpontes.isekaimahou.client.title;

import java.util.List;

import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.core.MahouConfig;

import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * Dicas de jogo do menu principal e das telas de carregamento (v1.0 RELEASE). Textos em
 * {@code tip.isekai_mahou.0 .. COUNT-1} (pt_br + en_us). Uma dica por vez, trocando a cada {@link #ROTATE_MS} com um
 * fade curto; a primeira de cada sessão é sorteada. A opção "Dicas no menu e no carregamento" desliga.
 */
public final class GameTips {
    /** Quantas dicas existem nas traduções ({@code tip.isekai_mahou.<n>}). */
    public static final int COUNT = 30;
    private static final long ROTATE_MS = 9000L;
    private static final long FADE_MS = 450L;
    private static final int LABEL_COLOR = 0xFFE8C56A;
    private static final int TEXT_COLOR = 0xFFE8E4F4;
    /** Sorteado uma vez: cada vez que o jogo abre começa numa dica diferente. */
    private static final int START = (int) Math.floorMod(System.nanoTime() / 1000L, (long) COUNT);

    private GameTips() {}

    public static boolean enabled() {
        return MahouConfig.safe(MahouConfig.LOADING_TIPS);
    }

    private static Component tip(long now) {
        int i = (int) Math.floorMod(START + now / ROTATE_MS, (long) COUNT);
        return Component.translatable("tip.isekai_mahou." + i);
    }

    /** Fade de entrada/saída dentro do período de cada dica. */
    private static float phaseAlpha(long now) {
        long t = now % ROTATE_MS;
        return Mth.clamp(Math.min(t, ROTATE_MS - t) / (float) FADE_MS, 0F, 1F);
    }

    /**
     * Desenha a dica da vez centrada em {@code cx}, com a ÚLTIMA linha terminando em {@code bottom}: rótulo dourado
     * "Dica:" + texto, quebrado em até {@code maxLines} linhas de {@code maxWidth}. {@code backdrop} põe uma faixa
     * escura suave atrás (telas de carregamento, onde o fundo varia).
     */
    public static void draw(GuiGraphics g, Font font, int cx, int bottom, int maxWidth, int maxLines, float alpha, boolean backdrop) {
        if (!enabled() || maxWidth < 100 || maxLines < 1) return;
        long now = Util.getMillis();
        float a = alpha * phaseAlpha(now);
        int al = Math.round(a * 255F);
        if (al < 8) return;
        Component text = Component.translatable("tip.isekai_mahou.label").withColor(LABEL_COLOR & 0xFFFFFF)
                .append(tip(now).copy().withColor(TEXT_COLOR & 0xFFFFFF));
        List<FormattedCharSequence> lines = font.split(text, maxWidth);
        if (lines.isEmpty()) return;
        if (lines.size() > maxLines) lines = lines.subList(0, maxLines);
        int h = lines.size() * 10;
        int top = bottom - h;
        if (backdrop) {
            int w = 0;
            for (FormattedCharSequence l : lines) w = Math.max(w, font.width(l));
            g.fill(cx - w / 2 - 8, top - 4, cx + w / 2 + 8, bottom + 2, MahouUi.alpha(0xFF05020C, Math.round(a * 150F)));
            g.fill(cx - w / 2 - 8, top - 5, cx + w / 2 + 8, top - 4, MahouUi.alpha(LABEL_COLOR, Math.round(a * 110F)));
        }
        int y = top;
        for (FormattedCharSequence l : lines) {
            g.drawString(font, l, cx - font.width(l) / 2, y, (al << 24) | 0xFFFFFF, true);
            y += 10;
        }
    }
}
