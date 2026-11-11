package com.renanpontes.isekaimahou.client.title;

import com.renanpontes.isekaimahou.client.MahouUi;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Logo desenhado do menu principal: "ISEKAI MAHOU" grande em dourado de dois tons (metade de cima clara, de baixo
 * funda — por recorte), contorno violeta, sombra, um reflexo que atravessa as letras de tempos em tempos, e embaixo
 * "異世界魔法" com filetes e losangos, mais a frase de efeito. Sem textura: escala com a tela.
 */
final class TitleLogo {
    static final Component TITLE = Component.translatable("title.isekai_mahou.logo");
    static final Component SUBTITLE = Component.translatable("title.isekai_mahou.logo_sub");
    static final Component TAGLINE = Component.translatable("title.isekai_mahou.tagline");

    /** Altura total do bloco do logo para a escala dada. */
    static int height(float scale) {
        return Math.round(8 * scale) + 30;
    }

    /** Escala do título para caber em ~80% da largura (entre 2 e 4,5). */
    static float scale(Font font, int screenW) {
        int tw = Math.max(1, font.width(TITLE));
        return Mth.clamp(screenW * 0.8F / tw, 2F, 4.5F);
    }

    static void render(GuiGraphics g, Font font, int screenW, int top, float scale, float alpha, float seconds) {
        if (alpha <= 0.02F) return;
        int al = Math.max(5, Math.round(alpha * 255));
        int cx = screenW / 2;
        int tw = font.width(TITLE);
        float x0 = cx - tw * scale / 2F;
        int textH = Math.round(8 * scale);

        // Contorno violeta (8 direções) + sombra
        int outline = MahouUi.alpha(0xFF2A0F4A, al);
        float o = Math.max(1F, scale / 2.5F);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                text(g, font, TITLE, x0 + dx * o, top + dy * o, scale, outline);
            }
        }
        text(g, font, TITLE, x0 + o, top + o * 2.5F, scale, MahouUi.alpha(0xFF000000, Math.round(al * 0.55F)));

        // Dourado de dois tons: metade de cima clara, de baixo funda (recorte na tela)
        int left = Math.round(x0) - 2, right = Math.round(x0 + tw * scale) + 2;
        int mid = top + Math.round(textH * 0.5F);
        g.enableScissor(left, top - 2, right, mid);
        text(g, font, TITLE, x0, top, scale, MahouUi.alpha(0xFFFFF2B0, al));
        g.disableScissor();
        g.enableScissor(left, mid, right, top + textH + 2);
        text(g, font, TITLE, x0, top, scale, MahouUi.alpha(0xFFFFB547, al));
        g.disableScissor();

        // Reflexo atravessando (a cada ~4 s)
        float cycle = (seconds % 4.2F) / 1.4F;
        if (cycle < 1F) {
            int band = Math.round(left - 30 + (right - left + 60) * cycle);
            int bw = Math.max(6, Math.round(scale * 3));
            g.enableScissor(Math.max(left, band), top - 2, Math.min(right, band + bw), top + textH + 2);
            text(g, font, TITLE, x0, top, scale, MahouUi.alpha(0xFFFFFFFF, Math.round(al * 0.85F)));
            g.disableScissor();
        }

        // Subtítulo em japonês com filetes e losangos
        int sy = top + textH + 6;
        Component sub = SUBTITLE.copy().withStyle(ChatFormatting.BOLD);
        float ss = 1.15F;
        int sw = Math.round(font.width(sub) * ss);
        int lineLen = Math.min(70, Math.round(tw * scale / 2F) - sw / 2 - 8);
        int lc = MahouUi.alpha(0xFFB98CFF, Math.round(al * 0.8F));
        if (lineLen > 10) {
            g.fillGradient(cx - sw / 2 - 8 - lineLen, sy + 5, cx - sw / 2 - 8, sy + 6, lc, lc);
            g.fillGradient(cx + sw / 2 + 8, sy + 5, cx + sw / 2 + 8 + lineLen, sy + 6, lc, lc);
            MahouUi.diamond(g, cx - sw / 2 - 8 - lineLen, sy + 5, 2, MahouUi.alpha(MahouUi.GOLD, al));
            MahouUi.diamond(g, cx + sw / 2 + 8 + lineLen, sy + 5, 2, MahouUi.alpha(MahouUi.GOLD, al));
        }
        float glow = 0.75F + 0.25F * Mth.sin(seconds * 1.6F);
        text(g, font, sub, cx - sw / 2F, sy, ss, MahouUi.alpha(MahouUi.mix(0xFFD9C2FF, 0xFFFFFFFF, glow * 0.4F), al));

        // Frase de efeito
        Component tag = TAGLINE.copy().withStyle(ChatFormatting.ITALIC);
        float ts = 0.85F;
        text(g, font, tag, cx - font.width(tag) * ts / 2F, sy + 14, ts, MahouUi.alpha(MahouUi.TEXT_DIM, Math.round(al * 0.9F)));
    }

    private static void text(GuiGraphics g, Font font, Component c, float x, float y, float scale, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1F);
        g.drawString(font, c, 0, 0, color, false);
        g.pose().popPose();
    }

    private TitleLogo() {}
}
