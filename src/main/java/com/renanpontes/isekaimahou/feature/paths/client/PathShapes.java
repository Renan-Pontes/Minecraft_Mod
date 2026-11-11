package com.renanpontes.isekaimahou.feature.paths.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;

/**
 * Formas vetoriais simples para a GUI (linhas grossas com degradê, discos, anéis, losangos), feitas com
 * quads do RenderType.gui(). Cada chamada descarrega o buffer para manter a ordem de desenho com textos.
 */
final class PathShapes {
    private static final int SEGMENTS = 28;

    /** Quad com cores por vértice; a ordem é corrigida para a mesma orientação do {@code GuiGraphics.fill}. */
    static void quad(GuiGraphics g, float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4,
                     int c1, int c2, int c3, int c4) {
        float area = (x1 * y2 - x2 * y1) + (x2 * y3 - x3 * y2) + (x3 * y4 - x4 * y3) + (x4 * y1 - x1 * y4);
        Matrix4f m = g.pose().last().pose();
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
        if (area > 0) {
            vc.addVertex(m, x4, y4, 0).setColor(c4);
            vc.addVertex(m, x3, y3, 0).setColor(c3);
            vc.addVertex(m, x2, y2, 0).setColor(c2);
            vc.addVertex(m, x1, y1, 0).setColor(c1);
        } else {
            vc.addVertex(m, x1, y1, 0).setColor(c1);
            vc.addVertex(m, x2, y2, 0).setColor(c2);
            vc.addVertex(m, x3, y3, 0).setColor(c3);
            vc.addVertex(m, x4, y4, 0).setColor(c4);
        }
    }

    /** Linha grossa de (x1,y1) a (x2,y2), com degradê de colorA para colorB. */
    static void line(GuiGraphics g, float x1, float y1, float x2, float y2, float thickness, int colorA, int colorB) {
        segment(g, x1, y1, x2, y2, thickness, colorA, colorB);
        g.flush();
    }

    /**
     * Curva quadrática (Bézier) de (x1,y1) a (x2,y2) com ponto de controle (qx,qy), em degradê. Usada nas
     * ligações que, retas, atravessariam o nó de outra fusão.
     */
    static void curve(GuiGraphics g, float x1, float y1, float qx, float qy, float x2, float y2, float thickness,
                      int colorA, int colorB) {
        final int steps = 18;
        float px = x1, py = y1;
        for (int i = 1; i <= steps; i++) {
            float t = i / (float) steps, u = 1 - t;
            float x = u * u * x1 + 2 * u * t * qx + t * t * x2;
            float y = u * u * y1 + 2 * u * t * qy + t * t * y2;
            segment(g, px, py, x, y, thickness, mix(colorA, colorB, (i - 1) / (float) steps), mix(colorA, colorB, t));
            px = x;
            py = y;
        }
        g.flush();
    }

    private static void segment(GuiGraphics g, float x1, float y1, float x2, float y2, float thickness, int colorA, int colorB) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.01F) return;
        float nx = -dy / len * thickness / 2, ny = dx / len * thickness / 2;
        quad(g, x1 + nx, y1 + ny, x1 - nx, y1 - ny, x2 - nx, y2 - ny, x2 + nx, y2 + ny, colorA, colorA, colorB, colorB);
    }

    /** Disco cheio; {@code edge} é a cor da borda (degradê radial do centro para a borda). */
    static void disc(GuiGraphics g, float cx, float cy, float r, int center, int edge) {
        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = Math.PI * 2 * i / SEGMENTS, a1 = Math.PI * 2 * (i + 1) / SEGMENTS;
            float x0 = cx + (float) Math.cos(a0) * r, y0 = cy + (float) Math.sin(a0) * r;
            float xe = cx + (float) Math.cos(a1) * r, ye = cy + (float) Math.sin(a1) * r;
            quad(g, cx, cy, x0, y0, xe, ye, xe, ye, center, edge, edge, edge);
        }
        g.flush();
    }

    /** Anel entre os raios rIn e rOut. */
    static void ring(GuiGraphics g, float cx, float cy, float rIn, float rOut, int color) {
        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = Math.PI * 2 * i / SEGMENTS, a1 = Math.PI * 2 * (i + 1) / SEGMENTS;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            quad(g, cx + c0 * rIn, cy + s0 * rIn, cx + c0 * rOut, cy + s0 * rOut, cx + c1 * rOut, cy + s1 * rOut,
                    cx + c1 * rIn, cy + s1 * rIn, color, color, color, color);
        }
        g.flush();
    }

    /** Losango (ícone de glifo / runa). */
    static void diamond(GuiGraphics g, float cx, float cy, float r, int fill, int border) {
        quad(g, cx, cy - r - 1, cx - r - 1, cy, cx, cy + r + 1, cx + r + 1, cy, border, border, border, border);
        quad(g, cx, cy - r, cx - r, cy, cx, cy + r, cx + r, cy, fill, fill, fill, fill);
        g.flush();
    }

    /** Cor ARGB com outro alpha (0..255). */
    static int alpha(int argb, int a) {
        return (Math.max(0, Math.min(255, a)) << 24) | (argb & 0xFFFFFF);
    }

    /** Mistura duas cores ARGB. */
    static int mix(int a, int b, float t) {
        int aa = a >>> 24, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int ba = b >>> 24, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    private PathShapes() {}
}
