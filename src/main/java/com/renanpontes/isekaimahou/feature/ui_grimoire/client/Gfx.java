package com.renanpontes.isekaimahou.feature.ui_grimoire.client;

import java.text.Normalizer;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

/** Utilidades de desenho (cantos arredondados, círculos, cores) e sons de interface das abas do Grimório. */
public final class Gfx {
    // Paleta da interface (estilo "Janela de Status" arcana)
    public static final int TEXT = 0xFFEDE6FF;
    public static final int TEXT_DIM = 0xFF9A93AD;
    public static final int TEXT_FAINT = 0xFF5F586E;
    public static final int GOLD = 0xFFFFD86B;
    public static final int GOOD = 0xFF7DFF9A;
    public static final int BAD = 0xFFFF6B6B;
    public static final int WARN = 0xFFFFC857;
    public static final int MANA = 0xFF8FC8FF;
    public static final int FRAME = 0xFF4A3F66;
    public static final int FRAME_LIGHT = 0xFF7D6BA8;
    public static final int BOX = 0xC0181424;
    public static final int BOX_DARK = 0xE00D0B14;

    public static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    private static boolean batching;

    /**
     * Junta vários retângulos num só envio à GPU (no 1.21.1 cada fill fora do modo "managed" vira um draw call).
     * Use só para sequências SEM texto: o texto entra em outro buffer e a ordem de camadas se perderia.
     */
    @SuppressWarnings("deprecation")
    public static void batch(GuiGraphics g, Runnable fills) {
        if (batching) {
            fills.run();
            return;
        }
        batching = true;
        try {
            g.drawManaged(fills);
        } finally {
            batching = false;
        }
    }

    // ------------------------------------------------------------------ formas
    /** Retângulo cheio com cantos cortados em 1px (parece arredondado na escala da GUI). */
    public static void round(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (w <= 2 || h <= 2) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        batch(g, () -> {
            g.fill(x + 1, y, x + w - 1, y + 1, color);
            g.fill(x, y + 1, x + w, y + h - 1, color);
            g.fill(x + 1, y + h - 1, x + w - 1, y + h, color);
        });
    }

    /** Retângulo arredondado com degradê vertical. */
    public static void roundGradient(GuiGraphics g, int x, int y, int w, int h, int top, int bottom) {
        if (w <= 2 || h <= 2) {
            g.fillGradient(x, y, x + w, y + h, top, bottom);
            return;
        }
        batch(g, () -> {
            g.fill(x + 1, y, x + w - 1, y + 1, top);
            g.fillGradient(x, y + 1, x + w, y + h - 1, top, bottom);
            g.fill(x + 1, y + h - 1, x + w - 1, y + h, bottom);
        });
    }

    /** Contorno arredondado de 1px. */
    public static void frame(GuiGraphics g, int x, int y, int w, int h, int color) {
        batch(g, () -> {
            g.fill(x + 1, y, x + w - 1, y + 1, color);
            g.fill(x + 1, y + h - 1, x + w - 1, y + h, color);
            g.fill(x, y + 1, x + 1, y + h - 1, color);
            g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
        });
    }

    /** Caixa padrão: fundo escuro + contorno. */
    public static void box(GuiGraphics g, int x, int y, int w, int h, int border) {
        batch(g, () -> {
            round(g, x, y, w, h, BOX);
            frame(g, x, y, w, h, border);
        });
    }

    /** Caixinha de alternância (filtros, seletores): acesa usa a cor de destaque. */
    public static void toggle(GuiGraphics g, int x, int y, int w, int h, boolean on, boolean hover, int accent) {
        int bg = on ? darken(accent, 0.42F) : hover ? 0xD0262038 : 0xC0151220;
        int border = on ? lighten(accent, 0.1F) : hover ? FRAME_LIGHT : FRAME;
        batch(g, () -> {
            round(g, x, y, w, h, bg);
            frame(g, x, y, w, h, border);
        });
    }

    /** Círculo cheio (linhas horizontais). */
    public static void circle(GuiGraphics g, int cx, int cy, int r, int color) {
        batch(g, () -> {
            for (int dy = -r; dy <= r; dy++) {
                int dx = (int) Math.floor(Math.sqrt(r * r - dy * dy + 0.5));
                g.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
            }
        });
    }

    /** Anel (contorno de círculo) com a espessura informada. */
    public static void ring(GuiGraphics g, int cx, int cy, int r, int thickness, int color) {
        int inner = Math.max(0, r - thickness);
        batch(g, () -> ringRows(g, cx, cy, r, inner, color));
    }

    private static void ringRows(GuiGraphics g, int cx, int cy, int r, int inner, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int outer = (int) Math.floor(Math.sqrt(r * r - dy * dy + 0.5));
            int in2 = inner * inner - dy * dy;
            if (in2 <= 0) {
                g.fill(cx - outer, cy + dy, cx + outer + 1, cy + dy + 1, color);
            } else {
                int innerDx = (int) Math.floor(Math.sqrt(in2));
                g.fill(cx - outer, cy + dy, cx - innerDx, cy + dy + 1, color);
                g.fill(cx + innerDx + 1, cy + dy, cx + outer + 1, cy + dy + 1, color);
            }
        }
    }

    /** Esfera sombreada (orbe): degradê do centro iluminado para a borda escura. */
    public static void orb(GuiGraphics g, int cx, int cy, int r, int rgb) {
        batch(g, () -> {
            circle(g, cx, cy, r, darken(rgb, 0.45F));
            circle(g, cx, cy, Math.max(1, r - 1), darken(rgb, 0.75F));
            circle(g, cx - r / 4, cy - r / 4, Math.max(1, r * 2 / 3), opaque(rgb));
            circle(g, cx - r / 3, cy - r / 3, Math.max(1, r / 4), lerp(opaque(rgb), 0xFFFFFFFF, 0.6F));
        });
    }

    /** Degradê horizontal (colunas de 1px). */
    public static void hGradient(GuiGraphics g, int x, int y, int w, int h, int left, int right) {
        batch(g, () -> {
            for (int i = 0; i < w; i++) {
                float t = w <= 1 ? 0 : (float) i / (w - 1);
                g.fill(x + i, y, x + i + 1, y + h, lerp(left, right, t));
            }
        });
    }

    // ------------------------------------------------------------------ cores
    public static int opaque(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    public static int alpha(int rgb, int a) {
        return (Mth.clamp(a, 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    public static int lerp(int a, int b, float t) {
        return FastColor.ARGB32.lerp(Mth.clamp(t, 0F, 1F), a, b);
    }

    /** Multiplica o brilho (mantém alpha 255). */
    public static int darken(int rgb, float f) {
        int r = (int) (FastColor.ARGB32.red(rgb) * f);
        int gg = (int) (FastColor.ARGB32.green(rgb) * f);
        int b = (int) (FastColor.ARGB32.blue(rgb) * f);
        return FastColor.ARGB32.color(255, Mth.clamp(r, 0, 255), Mth.clamp(gg, 0, 255), Mth.clamp(b, 0, 255));
    }

    public static int lighten(int rgb, float t) {
        return lerp(opaque(rgb), 0xFFFFFFFF, t);
    }

    /** Luminância relativa (0..1) para escolher texto claro ou escuro. */
    public static float luminance(int rgb) {
        return (0.299F * FastColor.ARGB32.red(rgb) + 0.587F * FastColor.ARGB32.green(rgb) + 0.114F * FastColor.ARGB32.blue(rgb)) / 255F;
    }

    /** Tons de cinza (para glifos desconhecidos). */
    public static int grey(int rgb, float brightness) {
        int l = (int) (luminance(rgb) * 255 * brightness);
        l = Mth.clamp(l, 0, 255);
        return FastColor.ARGB32.color(255, l, l, l);
    }

    public static int hsv(float h, float s, float v) {
        return 0xFF000000 | Mth.hsvToRgb(Mth.frac(h), Mth.clamp(s, 0F, 1F), Mth.clamp(v, 0F, 1F));
    }

    /** RGB -> {h, s, v} (0..1). */
    public static float[] toHsv(int rgb) {
        float r = FastColor.ARGB32.red(rgb) / 255F;
        float gg = FastColor.ARGB32.green(rgb) / 255F;
        float b = FastColor.ARGB32.blue(rgb) / 255F;
        float max = Math.max(r, Math.max(gg, b));
        float min = Math.min(r, Math.min(gg, b));
        float d = max - min;
        float h = 0;
        if (d > 1e-5F) {
            if (max == r) h = ((gg - b) / d) % 6F;
            else if (max == gg) h = (b - r) / d + 2F;
            else h = (r - gg) / d + 4F;
            h /= 6F;
            if (h < 0) h += 1F;
        }
        float s = max <= 0 ? 0 : d / max;
        return new float[]{h, s, max};
    }

    // ------------------------------------------------------------------ texto
    public static Font font() {
        return Minecraft.getInstance().font;
    }

    /** Corta o texto para caber na largura, com reticências. */
    public static String fit(Font font, String s, int width) {
        if (font.width(s) <= width) return s;
        String dots = "…";
        return font.plainSubstrByWidth(s, Math.max(0, width - font.width(dots))) + dots;
    }

    public static Component fit(Font font, Component c, int width) {
        if (font.width(c) <= width) return c;
        FormattedText cut = font.substrByWidth(c, Math.max(0, width - font.width("…")));
        return Component.literal(cut.getString() + "…").withStyle(c.getStyle());
    }

    /** Texto em escala menor (0.5–1). */
    public static void small(GuiGraphics g, String s, float x, float y, int color, float scale) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1F);
        g.drawString(font(), s, 0, 0, color, false);
        g.pose().popPose();
    }

    /** Normaliza para busca (minúsculo, sem acento). */
    public static String norm(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n.toLowerCase(Locale.ROOT);
    }

    /** Segundos com 1 casa a partir de ticks. */
    public static String seconds(int ticks) {
        return String.format(Locale.ROOT, "%.1f", ticks / 20F);
    }

    // ------------------------------------------------------------------ sons
    public static void sound(SoundEvent e, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(e, pitch, volume));
    }

    public static void sound(Holder<SoundEvent> e, float pitch, float volume) {
        sound(e.value(), pitch, volume);
    }

    private Gfx() {}
}
