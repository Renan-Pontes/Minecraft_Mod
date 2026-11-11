package com.renanpontes.isekaimahou.feature.ui_hud.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Primitivas de desenho do HUD e da Janela de Status: sprites tingidos, painéis estilo "janela de sistema",
 * barras, varredura de recarga (relógio) e progresso no perímetro (anel da ultimate).
 */
final class HudDraw {
    static final ResourceLocation SPRITES = IsekaiMahou.id("textures/gui/ui_hud_sprites.png");
    private static final int TEX = 64;

    // Sprites (x, y, w, h) — mesmos números de tools/gen/ui_hud.py
    static final int[] ORB_S = {0, 0, 7, 7};
    static final int[] ORB_L = {8, 0, 9, 9};
    static final int[] RING = {18, 0, 13, 13};
    static final int[] ICON_SNEAK = {0, 16, 7, 7};
    static final int[] ICON_SPRINT = {8, 16, 7, 7};
    static final int[] ICON_AIR = {16, 16, 7, 7};
    static final int[] ICON_WATER = {24, 16, 7, 7};
    static final int[] LOCK = {32, 16, 7, 7};
    static final int[] STAR = {40, 16, 7, 7};
    static final int[] CRYSTAL = {48, 16, 7, 9};
    static final int[] NODE_ON = {0, 32, 5, 5};
    static final int[] NODE_OFF = {6, 32, 5, 5};
    static final int[] WARN = {12, 32, 5, 7};
    // v0.7: Olho Analítico, barras de vida e banners
    static final int[] HEART = {0, 40, 7, 7};
    static final int[] SHIELD = {8, 40, 7, 7};
    static final int[] EYE = {16, 40, 9, 7};
    static final int[] CROWN = {26, 40, 9, 7};
    static final int[] ELITE = {36, 40, 7, 7};
    static final int[] UP = {44, 40, 5, 5};
    static final int[] DOWN = {50, 40, 5, 5};
    static final int[] IMMUNE = {56, 40, 5, 5};
    static final int[] TABLET = {0, 48, 7, 7};
    static final int[] SKULL = {8, 48, 7, 7};
    // v0.8: lugares, facções e condições
    static final int[] PIN = {16, 48, 7, 7};
    static final int[] WANTED = {24, 48, 7, 7};
    static final int[] BANNER = {32, 48, 7, 7};
    static final int[] HOURGLASS = {40, 48, 5, 7};

    // Paleta da "janela de sistema"
    static final int SYS_BG_TOP = 0xE0122036;
    static final int SYS_BG_BOTTOM = 0xE0080E1C;
    static final int SYS_BORDER = 0xFF4FC8F0;
    static final int SYS_BORDER_DIM = 0x904FC8F0;
    static final int SYS_TEXT = 0xFFE6F4FF;
    static final int SYS_MUTED = 0xFF8FA6BF;
    static final int GOLD = 0xFFFFD86B;
    static final int GOLD_LIGHT = 0xFFFFF0B8;
    static final int RED = 0xFFFF4D4D;
    static final int MANA = 0xFFB98CFF;

    private HudDraw() {}

    // =====================================================================================
    //  Cores e tempo
    // =====================================================================================
    static int argb(int rgb) {
        return 0xFF000000 | rgb;
    }

    /** Multiplica o alfa de uma cor ARGB. */
    static int fade(int argb, float a) {
        int alpha = Math.round(((argb >>> 24) & 0xFF) * Math.max(0F, Math.min(1F, a)));
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    static int lerp(float t, int a, int b) {
        t = Math.max(0F, Math.min(1F, t));
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return (Math.round(aa + (ba - aa) * t) << 24) | (Math.round(ar + (br - ar) * t) << 16)
                | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
    }

    static int brighten(int argb, float f) {
        return lerp(f, argb, (argb & 0xFF000000) | 0xFFFFFF);
    }

    static int darken(int argb, float f) {
        return lerp(f, argb, argb & 0xFF000000);
    }

    /** Opção booleana da config do cliente; antes de a config carregar vale o padrão (nunca derruba o HUD). */
    static boolean config(ModConfigSpec.BooleanValue value) {
        try {
            return value.get();
        } catch (RuntimeException e) {
            return value.getDefault();
        }
    }

    static float easeOut(float t) {
        t = Math.max(0F, Math.min(1F, t));
        return 1F - (1F - t) * (1F - t) * (1F - t);
    }

    /** Passa um pouco do fim e volta (efeito "pop"). */
    static float easeOutBack(float t) {
        t = Math.max(0F, Math.min(1F, t));
        float c1 = 1.70158F, c3 = c1 + 1F;
        float u = t - 1F;
        return 1F + c3 * u * u * u + c1 * u * u;
    }

    /** Onda 0..1 com o período informado (ms), para pulsos e brilhos. */
    static float pulse(long periodMs) {
        double t = (Util.getMillis() % periodMs) / (double) periodMs;
        return (float) (0.5 - 0.5 * Math.cos(t * Math.PI * 2));
    }

    /** Liga/desliga com o período informado (piscar). */
    static boolean blink(long periodMs) {
        return (Util.getMillis() % periodMs) < periodMs / 2;
    }

    // =====================================================================================
    //  Sprites
    // =====================================================================================
    static void sprite(GuiGraphics g, int[] s, int x, int y, int argb) {
        float a = ((argb >>> 24) & 0xFF) / 255F;
        if (a <= 0.01F) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(((argb >> 16) & 0xFF) / 255F, ((argb >> 8) & 0xFF) / 255F, (argb & 0xFF) / 255F, a);
        g.blit(SPRITES, x, y, (float) s[0], (float) s[1], s[2], s[3], TEX, TEX);
        g.setColor(1F, 1F, 1F, 1F);
    }

    /** Sprite centrado em (cx, cy). */
    static void spriteCentered(GuiGraphics g, int[] s, int cx, int cy, int argb) {
        sprite(g, s, cx - s[2] / 2, cy - s[3] / 2, argb);
    }

    // =====================================================================================
    //  Painéis e molduras
    // =====================================================================================
    /** Painel "janela de sistema": fundo em degradê, borda fina e cantos destacados. */
    static void systemPanel(GuiGraphics g, int x, int y, int w, int h, int accent) {
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, SYS_BG_TOP, SYS_BG_BOTTOM);
        g.renderOutline(x, y, w, h, fade(accent, 0.55F));
        // linha interna sutil (efeito de vidro)
        g.fill(x + 2, y + 2, x + w - 2, y + 3, 0x18FFFFFF);
        corners(g, x, y, w, h, accent, 4);
    }

    /** Cantos em "L" (colchetes) — a marca visual das janelas de status. */
    static void corners(GuiGraphics g, int x, int y, int w, int h, int color, int len) {
        len = Math.min(len, Math.min(w, h) / 2);
        g.fill(x - 1, y - 1, x + len, y, color);
        g.fill(x - 1, y - 1, x, y + len, color);
        g.fill(x + w - len, y - 1, x + w + 1, y, color);
        g.fill(x + w, y - 1, x + w + 1, y + len, color);
        g.fill(x - 1, y + h, x + len, y + h + 1, color);
        g.fill(x - 1, y + h - len, x, y + h + 1, color);
        g.fill(x + w - len, y + h, x + w + 1, y + h + 1, color);
        g.fill(x + w, y + h - len, x + w + 1, y + h + 1, color);
    }

    /** Brilho em volta de um retângulo (camadas translúcidas). */
    static void glow(GuiGraphics g, int x, int y, int w, int h, int color, float strength, int layers) {
        for (int i = 1; i <= layers; i++) {
            int c = fade(color, strength * (1F - (i - 1F) / layers) * 0.45F);
            g.fill(x - i, y - i, x + w + i, y - i + 1, c);
            g.fill(x - i, y + h + i - 1, x + w + i, y + h + i, c);
            g.fill(x - i, y - i + 1, x - i + 1, y + h + i - 1, c);
            g.fill(x + w + i - 1, y - i + 1, x + w + i, y + h + i - 1, c);
        }
    }

    /**
     * Faixa horizontal centrada em cx (meia-largura half) que é sólida no meio e some nas pontas
     * (fundo dos banners: não "corta" a tela com um retângulo duro).
     */
    static void hBand(GuiGraphics g, int cx, int y0, int y1, int half, int argb) {
        if (half <= 0 || y1 <= y0) return;
        int steps = Math.max(4, Math.min(24, half / 6));
        for (int i = 0; i < steps; i++) {
            float t = (i + 0.5F) / steps;
            float a = t < 0.5F ? 1F : 1F - (t - 0.5F) / 0.5F;
            a = a * a * (3F - 2F * a);
            int in = Math.round(half * i / (float) steps), out = Math.round(half * (i + 1) / (float) steps);
            if (out <= in) continue;
            int c = fade(argb, a);
            g.fill(cx - out, y0, cx - in, y1, c);
            g.fill(cx + in, y0, cx + out, y1, c);
        }
    }

    /** Losango cheio de "raio" r (pixels), para pontas de barras e marcadores. */
    static void diamond(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int i = -r; i <= r; i++) {
            int half = r - Math.abs(i);
            g.fill(cx - half, cy + i, cx + half + 1, cy + i + 1, color);
        }
    }

    /** Barra horizontal simples com fundo e moldura. */
    static void bar(GuiGraphics g, int x, int y, int w, int h, float frac, int top, int bottom, int frame) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, frame);
        g.fill(x, y, x + w, y + h, 0xFF0B0F1A);
        int fw = Math.round(w * Math.max(0F, Math.min(1F, frac)));
        if (fw > 0) {
            g.fillGradient(x, y, x + fw, y + h, top, bottom);
            g.fill(x, y, x + fw, y + 1, fade(0xFFFFFFFF, 0.25F));
        }
    }

    // =====================================================================================
    //  Formas especiais
    // =====================================================================================
    /**
     * Varredura de recarga estilo relógio sobre um quadrado: cobre a fração {@code remaining} (0..1)
     * que ainda falta, terminando no "meio-dia" e andando no sentido horário conforme a recarga acaba.
     */
    static void cooldownSweep(GuiGraphics g, float x, float y, float size, float remaining, int color) {
        if (remaining <= 0F) return;
        remaining = Math.min(1F, remaining);
        float cx = x + size / 2F, cy = y + size / 2F, half = size / 2F;
        double start = (1.0 - remaining) * Math.PI * 2.0, end = Math.PI * 2.0;

        // Pontos de quebra: início, cantos no meio do caminho e fim (lados retos ficam exatos)
        double[] pts = new double[8];
        int n = 0;
        pts[n++] = start;
        for (int k = 0; k < 4; k++) {
            double corner = Math.PI / 4.0 + k * Math.PI / 2.0;
            if (corner > start && corner < end) pts[n++] = corner;
        }
        pts[n++] = end;

        Matrix4f m = g.pose().last().pose();
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
        for (int i = 0; i + 1 < n; i++) {
            float[] a = edgePoint(cx, cy, half, pts[i]);
            float[] b = edgePoint(cx, cy, half, pts[i + 1]);
            // Triângulo como quad degenerado, nas duas ordens (só uma passa no culling)
            vc.addVertex(m, cx, cy, 0).setColor(color);
            vc.addVertex(m, b[0], b[1], 0).setColor(color);
            vc.addVertex(m, a[0], a[1], 0).setColor(color);
            vc.addVertex(m, a[0], a[1], 0).setColor(color);
            vc.addVertex(m, cx, cy, 0).setColor(color);
            vc.addVertex(m, a[0], a[1], 0).setColor(color);
            vc.addVertex(m, b[0], b[1], 0).setColor(color);
            vc.addVertex(m, b[0], b[1], 0).setColor(color);
        }
        g.flush();
    }

    /** Ponto na borda do quadrado (meia-largura half) no ângulo theta, medido do topo no sentido horário. */
    private static float[] edgePoint(float cx, float cy, float half, double theta) {
        double sx = Math.sin(theta), sy = -Math.cos(theta);
        double k = half / Math.max(Math.abs(sx), Math.abs(sy));
        return new float[] {(float) (cx + sx * k), (float) (cy + sy * k)};
    }

    /**
     * Preenche o perímetro de um retângulo (espessura t) do topo-centro no sentido horário até a fração frac.
     * @return a posição {x, y} da "cabeça" do progresso (para desenhar um brilho)
     */
    static int[] perimeter(GuiGraphics g, int x, int y, int w, int h, int t, float frac, int color) {
        int cx = x + w / 2;
        int total = 2 * (w + h);
        int len = Math.round(total * Math.max(0F, Math.min(1F, frac)));
        int[] head = {cx, y};
        int seg = x + w - cx;
        int s = Math.min(len, seg);
        if (s > 0) { g.fill(cx, y, cx + s, y + t, color); head = new int[] {cx + s, y}; }
        len -= seg;
        if (len <= 0) return head;
        s = Math.min(len, h);
        g.fill(x + w - t, y, x + w, y + s, color);
        head = new int[] {x + w, y + s};
        len -= h;
        if (len <= 0) return head;
        s = Math.min(len, w);
        g.fill(x + w - s, y + h - t, x + w, y + h, color);
        head = new int[] {x + w - s, y + h};
        len -= w;
        if (len <= 0) return head;
        s = Math.min(len, h);
        g.fill(x, y + h - s, x + t, y + h, color);
        head = new int[] {x, y + h - s};
        len -= h;
        if (len <= 0) return head;
        s = Math.min(len, cx - x);
        g.fill(x, y, x + s, y + t, color);
        return new int[] {x + s, y};
    }

    /** Ponto do perímetro (topo-centro, sentido horário) na fração frac — para brilhos que dão a volta. */
    static int[] perimeterPoint(int x, int y, int w, int h, float frac) {
        int cx = x + w / 2;
        int len = Math.round(2 * (w + h) * Math.max(0F, Math.min(1F, frac)));
        int seg = x + w - cx;
        if (len <= seg) return new int[] {cx + len, y};
        len -= seg;
        if (len <= h) return new int[] {x + w, y + len};
        len -= h;
        if (len <= w) return new int[] {x + w - len, y + h};
        len -= w;
        if (len <= h) return new int[] {x, y + h - len};
        len -= h;
        return new int[] {x + Math.min(len, cx - x), y};
    }

    // =====================================================================================
    //  Texto
    // =====================================================================================
    /** Tradução com chave montada em tempo de execução (ex.: um id de nó ou de classe no fim da chave). */
    static MutableComponent tr(String key, Object... args) {
        return Component.translatable(key, args);
    }

    /**
     * Texto com alfa: o Font trata alfa &lt; 4 como OPACO (o fim de um fade "piscaria"), então texto quase
     * transparente simplesmente não é desenhado.
     */
    static void text(GuiGraphics g, Font font, Component text, int x, int y, int argb, boolean shadow) {
        if ((argb >>> 24) < 6) return;
        g.drawString(font, text, x, y, argb, shadow);
    }

    static void text(GuiGraphics g, Font font, FormattedCharSequence text, int x, int y, int argb, boolean shadow) {
        if ((argb >>> 24) < 6) return;
        g.drawString(font, text, x, y, argb, shadow);
    }

    /** Número curto para vida/dano: inteiro quando é inteiro, senão uma casa decimal ("12", "7.5"). */
    static String num(float v) {
        if (v >= 1000F) return String.valueOf(Math.round(v));
        float r = Math.round(v * 10F) / 10F;
        return r == (int) r ? String.valueOf((int) r) : String.format(java.util.Locale.ROOT, "%.1f", r);
    }

    /** Texto em escala (ex.: 0.75 para rótulos pequenos). Coordenadas no espaço normal da tela. */
    static void scaledText(GuiGraphics g, Font font, Component text, float x, float y, float scale, int color, boolean shadow) {
        if ((color >>> 24) < 6) return;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1F);
        g.drawString(font, text, 0, 0, color, shadow);
        g.pose().popPose();
    }

    /** Texto cortado com "..." para caber na largura (respeita estilos, ex.: negrito). */
    static FormattedCharSequence ellipsize(Font font, Component text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text.getVisualOrderText();
        FormattedText head = font.substrByWidth(text, Math.max(0, maxWidth - font.width("...")));
        return Language.getInstance().getVisualOrder(FormattedText.composite(head, FormattedText.of("...", text.getStyle())));
    }
}
