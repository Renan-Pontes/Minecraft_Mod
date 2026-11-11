package com.renanpontes.isekaimahou.feature.arcane_duel.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

/** Geometria do duelo arcano (só cliente): quads texturizados, anéis, discos, colunas e cacos de vidro. */
final class DuelDraw {
    private static final float TAU = (float) (Math.PI * 2);

    static int argb(int rgb, float a) {
        return ((int) (Mth.clamp(a, 0F, 1F) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    static int lighten(int rgb, float f) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        return ((int) (r + (255 - r) * f) << 16) | ((int) (g + (255 - g) * f) << 8) | (int) (b + (255 - b) * f);
    }

    /** Suaviza 0..1 (entra/sai sem tranco). */
    static float smooth(float x) {
        x = Mth.clamp(x, 0F, 1F);
        return x * x * (3 - 2 * x);
    }

    /** Envelope: sobe em {@code in} ticks, desce nos últimos {@code out} ticks da vida. */
    static float envelope(float age, int life, float in, float out) {
        return smooth(age / Math.max(1F, in)) * smooth((life - age) / Math.max(1F, out));
    }

    private static void v(VertexConsumer vc, Matrix4f m, float x, float y, float z, int argb) {
        vc.addVertex(m, x, y, z).setColor(argb);
    }

    private static void vt(VertexConsumer vc, Matrix4f m, float x, float y, float z, float u, float v, int argb) {
        vc.addVertex(m, x, y, z).setUv(u, v).setColor(argb);
    }

    // =====================================================================================
    //  Texturizados (tipo aditivo POSITION_TEX_COLOR)
    // =====================================================================================

    /** Quad no plano XY local (para billboards e painéis), centrado na origem. */
    static void texQuadXY(VertexConsumer vc, Matrix4f m, float hw, float hh, int argb) {
        vt(vc, m, -hw, -hh, 0, 0, 1, argb);
        vt(vc, m, hw, -hh, 0, 1, 1, argb);
        vt(vc, m, hw, hh, 0, 1, 0, argb);
        vt(vc, m, -hw, hh, 0, 0, 0, argb);
    }

    /** Quad deitado no plano XZ (círculos no chão/no céu). */
    static void texQuadXZ(VertexConsumer vc, Matrix4f m, float r, float y, int argb) {
        vt(vc, m, -r, y, -r, 0, 0, argb);
        vt(vc, m, r, y, -r, 1, 0, argb);
        vt(vc, m, r, y, r, 1, 1, argb);
        vt(vc, m, -r, y, r, 0, 1, argb);
    }

    /** Quad no formato de entidade (translúcido, com luz/normal): o núcleo escuro da fenda. */
    static void entityQuadXY(VertexConsumer vc, PoseStack.Pose pose, float hw, float hh, int argb) {
        ev(vc, pose, -hw, -hh, 0, 1, argb);
        ev(vc, pose, hw, -hh, 1, 1, argb);
        ev(vc, pose, hw, hh, 1, 0, argb);
        ev(vc, pose, -hw, hh, 0, 0, argb);
    }

    private static void ev(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float u, float v, int argb) {
        vc.addVertex(pose, x, y, 0).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0, 0, 1);
    }

    // =====================================================================================
    //  Brilho (tipo GLOW POSITION_COLOR)
    // =====================================================================================

    /** Disco radial no plano XY: {@code cIn} no raio interno, {@code cOut} no externo (halo, clarão). */
    static void radial(VertexConsumer vc, Matrix4f m, float rIn, float rOut, int cIn, int cOut, int seg) {
        for (int k = 0; k < seg; k++) {
            float a0 = TAU * k / seg, a1 = TAU * (k + 1) / seg;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            v(vc, m, c0 * rIn, s0 * rIn, 0, cIn);
            v(vc, m, c0 * rOut, s0 * rOut, 0, cOut);
            v(vc, m, c1 * rOut, s1 * rOut, 0, cOut);
            v(vc, m, c1 * rIn, s1 * rIn, 0, cIn);
        }
    }

    /** Halo: brilho de {@code rIn} a {@code rMid} e esmaecendo até {@code rOut}. */
    static void halo(VertexConsumer vc, Matrix4f m, float rIn, float rMid, float rOut, int rgb, float a, int seg) {
        radial(vc, m, rIn, rMid, argb(rgb, 0F), argb(rgb, a), seg);
        radial(vc, m, rMid, rOut, argb(rgb, a), argb(rgb, 0F), seg);
    }

    /** Anel deitado (plano XZ) de espessura {@code w}. */
    static void ringFlat(VertexConsumer vc, Matrix4f m, float r, float w, float y, int rgb, float a, int seg) {
        int cIn = argb(rgb, a * 0.35F), cOut = argb(rgb, a);
        float ri = Math.max(0F, r - w), ro = r;
        for (int k = 0; k < seg; k++) {
            float a0 = TAU * k / seg, a1 = TAU * (k + 1) / seg;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            v(vc, m, c0 * ri, y, s0 * ri, cIn);
            v(vc, m, c0 * ro, y, s0 * ro, cOut);
            v(vc, m, c1 * ro, y, s1 * ro, cOut);
            v(vc, m, c1 * ri, y, s1 * ri, cIn);
        }
    }

    /** Coluna vertical (lados de um cilindro) de y0 a y1, alfa de baixo {@code a0} e de cima {@code a1}. */
    static void column(VertexConsumer vc, Matrix4f m, float r, float y0, float y1, int rgb, float a0, float a1, int seg) {
        int cb = argb(rgb, a0), ct = argb(rgb, a1);
        for (int k = 0; k < seg; k++) {
            float t0 = TAU * k / seg, t1 = TAU * (k + 1) / seg;
            float x0 = Mth.cos(t0) * r, z0 = Mth.sin(t0) * r, x1 = Mth.cos(t1) * r, z1 = Mth.sin(t1) * r;
            v(vc, m, x0, y0, z0, cb);
            v(vc, m, x1, y0, z1, cb);
            v(vc, m, x1, y1, z1, ct);
            v(vc, m, x0, y1, z0, ct);
        }
    }

    /** Caco triangular (quad degenerado) no plano XY: ponta em (px,py), base em volta de (bx,by). */
    static void shard(VertexConsumer vc, Matrix4f m, float px, float py, float bx, float by, float half, int cTip, int cBase) {
        float dx = px - bx, dy = py - by;
        float len = Mth.sqrt(dx * dx + dy * dy);
        if (len < 1.0E-4F) return;
        float nx = -dy / len * half, ny = dx / len * half;
        v(vc, m, bx + nx, by + ny, 0, cBase);
        v(vc, m, px, py, 0, cTip);
        v(vc, m, px, py, 0, cTip);
        v(vc, m, bx - nx, by - ny, 0, cBase);
    }

    /** Fita reta no plano XY de (x0,y0) a (x1,y1) com largura {@code w} (ponteiros do relógio, rachaduras). */
    static void strip(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float w, int c0, int c1) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = Mth.sqrt(dx * dx + dy * dy);
        if (len < 1.0E-4F) return;
        float nx = -dy / len * w * 0.5F, ny = dx / len * w * 0.5F;
        v(vc, m, x0 + nx, y0 + ny, 0, c0);
        v(vc, m, x1 + nx, y1 + ny, 0, c1);
        v(vc, m, x1 - nx, y1 - ny, 0, c1);
        v(vc, m, x0 - nx, y0 - ny, 0, c0);
    }

    private DuelDraw() {}
}
