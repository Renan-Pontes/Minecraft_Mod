package com.renanpontes.isekaimahou.feature.fusions_b.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Pequenos utilitários de desenho (só cliente): quads de dois lados para os tipos de render sem textura
 * (céu do End = POSITION; brilho aditivo "lightning" = POSITION_COLOR).
 */
final class FbRender {

    /** Quad só com posição (render type endGateway/endPortal), desenhado dos dois lados. */
    static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1,
                     float x2, float y2, float z2, float x3, float y3, float z3) {
        vc.addVertex(m, x0, y0, z0);
        vc.addVertex(m, x1, y1, z1);
        vc.addVertex(m, x2, y2, z2);
        vc.addVertex(m, x3, y3, z3);
        vc.addVertex(m, x3, y3, z3);
        vc.addVertex(m, x2, y2, z2);
        vc.addVertex(m, x1, y1, z1);
        vc.addVertex(m, x0, y0, z0);
    }

    /** Quad colorido (render type lightning), dos dois lados. Cores ARGB por vértice. */
    static void colorQuad(VertexConsumer vc, Matrix4f m, float x0, float y0, float z0, int c0, float x1, float y1, float z1, int c1,
                          float x2, float y2, float z2, int c2, float x3, float y3, float z3, int c3) {
        vertex(vc, m, x0, y0, z0, c0);
        vertex(vc, m, x1, y1, z1, c1);
        vertex(vc, m, x2, y2, z2, c2);
        vertex(vc, m, x3, y3, z3, c3);
        vertex(vc, m, x3, y3, z3, c3);
        vertex(vc, m, x2, y2, z2, c2);
        vertex(vc, m, x1, y1, z1, c1);
        vertex(vc, m, x0, y0, z0, c0);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float z, int argb) {
        vc.addVertex(m, x, y, z).setColor((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, (argb >>> 24) & 255);
    }

    /** ARGB com alpha trocado (0–1). */
    static int alpha(int rgb, float a) {
        return ((int) (Math.max(0F, Math.min(1F, a)) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    /** Multiplica duas cores ARGB canal a canal (tingir modelos). */
    static int multiply(int a, int b) {
        int ra = (a >>> 24) * (b >>> 24) / 255;
        int rr = ((a >> 16) & 255) * ((b >> 16) & 255) / 255;
        int rg = ((a >> 8) & 255) * ((b >> 8) & 255) / 255;
        int rb = (a & 255) * (b & 255) / 255;
        return (ra << 24) | (rr << 16) | (rg << 8) | rb;
    }

    private FbRender() {}
}
