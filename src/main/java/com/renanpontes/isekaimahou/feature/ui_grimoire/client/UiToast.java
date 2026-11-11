package com.renanpontes.isekaimahou.feature.ui_grimoire.client;

import java.util.List;

import com.renanpontes.isekaimahou.client.MahouUi;

import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;

/** Mensagem curta de retorno (salvo, erro...) mostrada no rodapé da aba e que some sozinha. */
public final class UiToast {
    private static final long DURATION = 2600L;
    private static Component text;
    private static int color;
    private static long shownAt;

    public static void ok(Component c) {
        show(c, Gfx.GOOD);
    }

    public static void info(Component c) {
        show(c, Gfx.TEXT);
    }

    public static void error(Component c) {
        show(c, Gfx.BAD);
        Gfx.sound(SoundEvents.NOTE_BLOCK_BASS, 0.7F, 0.5F);
    }

    public static void show(Component c, int rgb) {
        text = c;
        color = rgb;
        shownAt = Util.getMillis();
    }

    /** Desenha centralizado em cx, com a base em bottomY. */
    public static void render(GuiGraphics g, int cx, int bottomY, int maxW) {
        if (text == null) return;
        long age = Util.getMillis() - shownAt;
        if (age > DURATION) {
            text = null;
            return;
        }
        float a = age < 120 ? age / 120F : age > DURATION - 500 ? (DURATION - age) / 500F : 1F;
        int alpha = (int) (a * 255);
        if (alpha < 8) return;
        Font font = Gfx.font();
        // mensagem comprida quebra em até 2 linhas (em vez de ser cortada)
        List<FormattedCharSequence> lines = font.split(text, maxW - 12);
        if (lines.size() > 2) {
            lines = List.of(lines.get(0), MahouUi.fit(font, Component.literal(rest(lines)), maxW - 12));
        }
        int w = 0;
        for (FormattedCharSequence l : lines) w = Math.max(w, font.width(l));
        w += 12;
        int h = 5 + lines.size() * 9;
        int x = cx - w / 2;
        int y = bottomY - h - (int) ((1F - Math.min(1F, age / 120F)) * 4);
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        Gfx.round(g, x, y, w, h, Gfx.alpha(0x0D0B14, (int) (alpha * 0.92F)));
        Gfx.frame(g, x, y, w, h, Gfx.alpha(color, alpha));
        for (int i = 0; i < lines.size(); i++) {
            FormattedCharSequence l = lines.get(i);
            g.drawString(font, l, x + (w - font.width(l)) / 2, y + 3 + i * 9, Gfx.alpha(color, alpha), false);
        }
        g.pose().popPose();
    }

    /** Texto das linhas a partir da 2ª (para a última linha visível terminar com "…"). */
    private static String rest(List<FormattedCharSequence> lines) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < lines.size(); i++) {
            lines.get(i).accept((idx, style, cp) -> {
                sb.appendCodePoint(cp);
                return true;
            });
            sb.append(' ');
        }
        return sb.toString().trim();
    }

    private UiToast() {}
}
