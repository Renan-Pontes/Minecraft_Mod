package com.renanpontes.isekaimahou.feature.cinematic.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;

/**
 * v1.1 — Desenhos 2D das cutscenes coreografadas: máscara radial (escuridão com só o personagem iluminado, vinheta
 * colorida), linhas de velocidade de mangá, quadro de impacto (cor invertida), relógio com ponteiros, faixas de calor
 * e de "rebobinar", barras de falha. Tudo em triângulos coloridos num lote só (sem texturas novas, sem alocação por
 * linha).
 */
final class ScreenFx {
    private static final int SEG = 40;
    private static final float[] COS = new float[SEG + 1];
    private static final float[] SIN = new float[SEG + 1];

    static {
        for (int i = 0; i <= SEG; i++) {
            float a = i * Mth.TWO_PI / SEG;
            COS[i] = Mth.cos(a);
            SIN[i] = Mth.sin(a);
        }
    }

    // ------------------------------------------------------------ lote de triângulos

    private static BufferBuilder begin(GuiGraphics g) {
        g.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        return Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
    }

    private static void end(BufferBuilder bb) {
        MeshData mesh = bb.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static void tri(BufferBuilder bb, Matrix4f m, float x0, float y0, int c0, float x1, float y1, int c1,
                            float x2, float y2, int c2) {
        bb.addVertex(m, x0, y0, 0F).setColor(c0);
        bb.addVertex(m, x1, y1, 0F).setColor(c1);
        bb.addVertex(m, x2, y2, 0F).setColor(c2);
    }

    /** Quadrilátero (a-b-c-d em ordem) com uma cor por vértice. */
    private static void quad(BufferBuilder bb, Matrix4f m, float ax, float ay, int ca, float bx, float by, int cb,
                             float cx, float cy, int cc, float dx, float dy, int cd) {
        tri(bb, m, ax, ay, ca, bx, by, cb, cx, cy, cc);
        tri(bb, m, ax, ay, ca, cx, cy, cc, dx, dy, cd);
    }

    // ------------------------------------------------------------ efeitos

    /**
     * Máscara radial: transparente até {@code r0}, sobe até {@code alpha} em {@code r1} e cobre o resto da tela.
     * Com cor preta e raio pequeno = "só o personagem iluminado"; com cor e raio grande = vinheta colorida.
     */
    static void radial(GuiGraphics g, float cx, float cy, float r0, float r1, int rgb, float alpha) {
        if (alpha <= 0.01F) return;
        float far = (float) Math.sqrt((double) g.guiWidth() * g.guiWidth() + (double) g.guiHeight() * g.guiHeight()) + 8F;
        far += Math.max(Math.abs(cx - g.guiWidth() / 2F), Math.abs(cy - g.guiHeight() / 2F));
        int clear = CinematicDraw.argb(rgb, 0F), solid = CinematicDraw.argb(rgb, Math.min(1F, alpha));
        Matrix4f m = g.pose().last().pose();
        BufferBuilder bb = begin(g);
        for (int i = 0; i < SEG; i++) {
            float c0 = COS[i], s0 = SIN[i], c1 = COS[i + 1], s1 = SIN[i + 1];
            quad(bb, m, cx + c0 * r0, cy + s0 * r0, clear, cx + c1 * r0, cy + s1 * r0, clear,
                    cx + c1 * r1, cy + s1 * r1, solid, cx + c0 * r1, cy + s0 * r1, solid);
            quad(bb, m, cx + c0 * r1, cy + s0 * r1, solid, cx + c1 * r1, cy + s1 * r1, solid,
                    cx + c1 * far, cy + s1 * far, solid, cx + c0 * far, cy + s0 * far, solid);
        }
        end(bb);
    }

    /**
     * Linhas de velocidade de mangá: cunhas finas da borda da tela para o centro, sorteadas de novo a cada
     * {@code seed} (troque a cada 1–2 ticks para "vibrar").
     */
    static void speedLines(GuiGraphics g, float cx, float cy, float inner, int rgb, float alpha, int seed, int count) {
        if (alpha <= 0.01F || count <= 0) return;
        float far = (float) Math.sqrt((double) g.guiWidth() * g.guiWidth() + (double) g.guiHeight() * g.guiHeight());
        Matrix4f m = g.pose().last().pose();
        BufferBuilder bb = begin(g);
        for (int i = 0; i < count; i++) {
            float r1 = hash(seed, i * 3), r2 = hash(seed, i * 3 + 1), r3 = hash(seed, i * 3 + 2);
            float ang = (i + r1 * 0.8F) * Mth.TWO_PI / count;
            float half = (0.004F + r2 * 0.012F);
            float tip = inner * (0.85F + r3 * 0.9F);
            float a = alpha * (0.45F + 0.55F * r2);
            int solid = CinematicDraw.argb(rgb, a), clear = CinematicDraw.argb(rgb, 0F);
            float ca = Mth.cos(ang), sa = Mth.sin(ang);
            float cl = Mth.cos(ang - half), sl = Mth.sin(ang - half), cr = Mth.cos(ang + half), sr = Mth.sin(ang + half);
            tri(bb, m, cx + ca * tip, cy + sa * tip, clear, cx + cl * far, cy + sl * far, solid, cx + cr * far, cy + sr * far, solid);
        }
        end(bb);
    }

    /** Quadro de impacto: inverte as cores da tela ({@code alpha} 1 = inversão total). */
    static void invert(GuiGraphics g, float alpha) {
        if (alpha <= 0.01F) return;
        float a = Math.min(1F, alpha);
        int c = CinematicDraw.argb(((int) (a * 255) * 0x010101), a);
        Matrix4f m = g.pose().last().pose();
        BufferBuilder bb = begin(g);
        // resultado = destino·(1−a) + (1−destino)·a  →  a = 1 inverte tudo
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        float w = g.guiWidth(), h = g.guiHeight();
        quad(bb, m, 0, 0, c, w, 0, c, w, h, c, 0, h, c);
        end(bb);
    }

    /** Tela inteira de uma cor (tingir / lavar as cores). */
    static void tint(GuiGraphics g, int rgb, float alpha) {
        if (alpha <= 0.01F) return;
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), CinematicDraw.argb(rgb, alpha));
    }

    /** Relógio: anel, 12 marcas e dois ponteiros (ângulos em radianos, 0 = 12 horas). */
    static void clock(GuiGraphics g, float cx, float cy, float r, int rgb, float alpha, float minute, float hour) {
        if (alpha <= 0.01F) return;
        int solid = CinematicDraw.argb(rgb, alpha), soft = CinematicDraw.argb(rgb, alpha * 0.45F);
        Matrix4f m = g.pose().last().pose();
        BufferBuilder bb = begin(g);
        float t = Math.max(1.5F, r * 0.025F);
        // anel duplo
        ring(bb, m, cx, cy, r - t, r, solid);
        ring(bb, m, cx, cy, r * 0.9F - t * 0.5F, r * 0.9F, soft);
        // marcas
        for (int i = 0; i < 12; i++) {
            float a = i * Mth.TWO_PI / 12F;
            float len = i % 3 == 0 ? r * 0.14F : r * 0.07F;
            line(bb, m, cx + Mth.sin(a) * (r * 0.86F - len), cy - Mth.cos(a) * (r * 0.86F - len),
                    cx + Mth.sin(a) * r * 0.86F, cy - Mth.cos(a) * r * 0.86F, t * (i % 3 == 0 ? 1.4F : 0.8F), solid);
        }
        // ponteiros
        line(bb, m, cx, cy, cx + Mth.sin(minute) * r * 0.78F, cy - Mth.cos(minute) * r * 0.78F, t * 1.1F, solid);
        line(bb, m, cx, cy, cx + Mth.sin(hour) * r * 0.5F, cy - Mth.cos(hour) * r * 0.5F, t * 1.8F, solid);
        end(bb);
    }

    /**
     * Faixas horizontais que correm na vertical (calor subindo, ou linhas de "rebobinar" de fita).
     * {@code speed} em pixels por tick (negativo = sobe).
     */
    static void bands(GuiGraphics g, float time, int rgb, float alpha, float spacing, float thickness, float speed) {
        if (alpha <= 0.01F) return;
        int w = g.guiWidth(), h = g.guiHeight();
        float off = (time * speed) % spacing;
        if (off < 0) off += spacing;
        int solid = CinematicDraw.argb(rgb, alpha), clear = CinematicDraw.argb(rgb, 0F);
        for (float y = off - spacing; y < h; y += spacing) {
            int y0 = Math.round(y), y1 = Math.round(y + thickness);
            g.fillGradient(0, y0, w, (y0 + y1) / 2, clear, solid);
            g.fillGradient(0, (y0 + y1) / 2, w, y1, solid, clear);
        }
    }

    /** Barras de "falha" (quadros pulados): retângulos finos em posições sorteadas por {@code seed}. */
    static void glitch(GuiGraphics g, int seed, int rgb, float alpha, int count) {
        if (alpha <= 0.01F) return;
        int w = g.guiWidth(), h = g.guiHeight();
        for (int i = 0; i < count; i++) {
            float y = hash(seed, i * 2) * h;
            float th = 1F + hash(seed, i * 2 + 1) * 6F;
            float x0 = hash(seed + 7, i) * w * 0.5F;
            float x1 = x0 + w * (0.25F + hash(seed + 13, i) * 0.75F);
            g.fill(Math.round(x0), Math.round(y), Math.round(Math.min(w, x1)), Math.round(y + th),
                    CinematicDraw.argb(rgb, alpha * (0.4F + 0.6F * hash(seed + 3, i))));
        }
    }

    // ------------------------------------------------------------ peças

    private static void ring(BufferBuilder bb, Matrix4f m, float cx, float cy, float r0, float r1, int color) {
        for (int i = 0; i < SEG; i++) {
            float c0 = COS[i], s0 = SIN[i], c1 = COS[i + 1], s1 = SIN[i + 1];
            quad(bb, m, cx + c0 * r0, cy + s0 * r0, color, cx + c1 * r0, cy + s1 * r0, color,
                    cx + c1 * r1, cy + s1 * r1, color, cx + c0 * r1, cy + s0 * r1, color);
        }
    }

    private static void line(BufferBuilder bb, Matrix4f m, float x0, float y0, float x1, float y1, float width, int color) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = Mth.sqrt(dx * dx + dy * dy);
        if (len < 1.0E-3F) return;
        float nx = -dy / len * width * 0.5F, ny = dx / len * width * 0.5F;
        quad(bb, m, x0 + nx, y0 + ny, color, x1 + nx, y1 + ny, color, x1 - nx, y1 - ny, color, x0 - nx, y0 - ny, color);
    }

    /** Pseudo-aleatório estável 0..1 (sem RandomSource por quadro). */
    static float hash(int seed, int i) {
        int x = seed * 0x9E3779B1 + i * 0x85EBCA6B;
        x ^= x >>> 15;
        x *= 0x2C1B3C6D;
        x ^= x >>> 12;
        x *= 0x297A2D39;
        x ^= x >>> 15;
        return (x & 0xFFFFFF) / (float) 0x1000000;
    }

    private ScreenFx() {}
}
