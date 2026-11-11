package com.renanpontes.isekaimahou.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/**
 * Peças de desenho do estilo "Janela de Status" do núcleo (cliente). Usadas pelo menu B, pelo toast de
 * recompensa e pelo rastreador de objetivos; módulos de UI podem reutilizar para ficar com o mesmo visual.
 * Cores são ARGB.
 */
public final class MahouUi {
    public static final int GOLD = 0xFFFFD86B;
    public static final int GOLD_DIM = 0xFF8A7038;
    public static final int GOLD_PALE = 0xFFFFF2B0;
    public static final int INK_TOP = 0xF2181226;
    public static final int INK_BOTTOM = 0xF20B0912;
    public static final int EDGE_DARK = 0xFF07050C;
    public static final int TEXT = 0xFFE8E2F4;
    public static final int TEXT_DIM = 0xFFA89EC0;
    public static final int ARCANE = 0xFFB98CFF;

    /** Folha de sprites do núcleo (gerada por tools/gen/core_ui.py). */
    public static final ResourceLocation SPRITES = IsekaiMahou.id("textures/gui/core_ui.png");
    public static final int SPRITES_W = 64, SPRITES_H = 32;
    /** Seta 16×16 apontando para cima (branca, tingível). */
    public static final int ARROW_U = 0, ARROW_V = 0;
    /** Cantos ornamentais 8×8: superior-esquerdo, superior-direito, inferior-esquerdo, inferior-direito. */
    public static final int CORNER_U = 16, CORNER_V = 0;
    /** Gema em losango 7×7 e estrela 7×7. */
    public static final int GEM_U = 32, GEM_V = 0, STAR_U = 40, STAR_V = 0;

    // ---------------------------------------------------------------- Cor
    public static int alpha(int argb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (argb & 0xFFFFFF);
    }

    /** Mistura duas cores ARGB (t=0 → a, t=1 → b). */
    public static int mix(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int aa = a >>> 24, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int ba = b >>> 24, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    public static int lighten(int argb, float t) { return mix(argb, argb | 0xFFFFFF, t); }
    public static int darken(int argb, float t) { return mix(argb, argb & 0xFF000000, t); }

    // ---------------------------------------------------------------- Formas
    /** Retângulo de cantos cortados (1px; 2px se tiver 14px ou mais) com gradiente vertical. */
    public static void roundRect(GuiGraphics g, int x, int y, int w, int h, int top, int bottom) {
        if (w <= 2 || h <= 2) {
            g.fillGradient(x, y, x + w, y + h, top, bottom);
            return;
        }
        int r = Math.min(w, h) >= 14 ? 2 : 1;
        g.fillGradient(x, y + r, x + w, y + h - r, mix(top, bottom, (float) r / h), mix(top, bottom, 1F - (float) r / h));
        for (int i = 0; i < r; i++) {
            int inset = r - i;
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, mix(top, bottom, (float) i / h));
            g.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, mix(top, bottom, 1F - (float) i / h));
        }
    }

    /** Contorno de cantos cortados (1px). */
    public static void roundOutline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x + 1, y, x + w - 1, y + 1, color);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    /** Cantoneiras em L (comprimento {@code len}) nos 4 cantos. */
    public static void brackets(GuiGraphics g, int x, int y, int w, int h, int len, int color) {
        int r = x + w, b = y + h;
        g.fill(x, y, x + len, y + 1, color);
        g.fill(x, y, x + 1, y + len, color);
        g.fill(r - len, y, r, y + 1, color);
        g.fill(r - 1, y, r, y + len, color);
        g.fill(x, b - 1, x + len, b, color);
        g.fill(x, b - len, x + 1, b, color);
        g.fill(r - len, b - 1, r, b, color);
        g.fill(r - 1, b - len, r, b, color);
    }

    /** Losango sólido (meia-largura {@code r}) centrado em (cx, cy). */
    public static void diamond(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int i = -r; i <= r; i++) {
            int half = r - Math.abs(i);
            g.fill(cx - half, cy + i, cx + half + 1, cy + i + 1, color);
        }
    }

    /**
     * Moldura arcana completa (fundo em gradiente escuro, borda dupla, cantos ornamentais).
     * {@code accent} colore a borda interna (ex.: cor da classe ou do elemento).
     */
    public static void frame(GuiGraphics g, int x, int y, int w, int h, int accent) {
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0x55000000);
        g.fillGradient(x, y, x + w, y + h, INK_TOP, INK_BOTTOM);
        g.renderOutline(x, y, w, h, EDGE_DARK);
        g.renderOutline(x + 1, y + 1, w - 2, h - 2, GOLD_DIM);
        if (w > 8 && h > 8) g.renderOutline(x + 3, y + 3, w - 6, h - 6, alpha(accent, 0x90));
        corners(g, x, y, w, h);
    }

    /** Ornamentos dourados nos 4 cantos (sprite; se faltar a textura, cantoneiras simples). */
    public static void corners(GuiGraphics g, int x, int y, int w, int h) {
        if (w < 20 || h < 20) {
            brackets(g, x + 1, y + 1, w - 2, h - 2, Math.min(5, Math.min(w, h) / 3), GOLD);
            return;
        }
        if (GlyphArt.exists(SPRITES)) {
            sprite(g, x - 1, y - 1, CORNER_U, CORNER_V, 8, 8);
            sprite(g, x + w - 7, y - 1, CORNER_U + 8, CORNER_V, 8, 8);
            sprite(g, x - 1, y + h - 7, CORNER_U, CORNER_V + 8, 8, 8);
            sprite(g, x + w - 7, y + h - 7, CORNER_U + 8, CORNER_V + 8, 8, 8);
        } else {
            brackets(g, x + 1, y + 1, w - 2, h - 2, 6, GOLD);
        }
    }

    /** Desenha um pedaço da folha de sprites do núcleo em tamanho natural. */
    public static void sprite(GuiGraphics g, int x, int y, int u, int v, int w, int h) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blit(SPRITES, x, y, u, v, w, h, SPRITES_W, SPRITES_H);
        RenderSystem.disableBlend();
    }

    /** Barra fina de progresso (fundo escuro + preenchimento com brilho no topo). */
    public static void bar(GuiGraphics g, int x, int y, int w, int h, float frac, int color) {
        g.fill(x, y, x + w, y + h, 0x90000000);
        int fw = Math.round(w * Math.max(0, Math.min(1, frac)));
        if (fw <= 0) return;
        g.fill(x, y, x + fw, y + h, color);
        g.fill(x, y, x + fw, y + 1, lighten(color, 0.35F));
    }

    // ---------------------------------------------------------------- Texto
    /** Texto cortado com "…" para caber em {@code width} pixels (mantém cores/estilos). */
    public static FormattedCharSequence fit(Font font, Component text, int width) {
        if (font.width(text) <= width) return text.getVisualOrderText();
        if (width <= 0) return FormattedCharSequence.EMPTY;
        int ell = font.width("…");
        FormattedText cut = font.substrByWidth(text, Math.max(0, width - ell));
        return Language.getInstance().getVisualOrder(FormattedText.composite(cut, FormattedText.of("…")));
    }

    /** Desenha texto em escala ({@code scale} < 1 para letras miúdas). */
    public static void smallText(GuiGraphics g, Font font, Component text, float x, float y, float scale, int color, boolean shadow) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1F);
        g.drawString(font, text, 0, 0, color, shadow);
        g.pose().popPose();
    }

    private MahouUi() {}
}
