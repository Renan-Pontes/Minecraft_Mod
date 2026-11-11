package com.renanpontes.isekaimahou.feature.origins_a.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Quads brilhantes texturizados (círculos de runa, parede do Ward) para os renderers do origins_a. */
final class OaQuad {

    static ResourceLocation tex(String name) {
        return IsekaiMahou.id("textures/entity/" + name + ".png");
    }

    /** Translúcido e "emissivo": não escurece à noite e não esconde o que está atrás. */
    static VertexConsumer glow(MultiBufferSource buffers, ResourceLocation texture) {
        return buffers.getBuffer(RenderType.entityTranslucentEmissive(texture));
    }

    /** Quad horizontal centrado na pose atual (plano XZ), meia largura {@code half}. */
    static void flat(PoseStack ps, VertexConsumer vc, float half, int rgb, float alpha) {
        PoseStack.Pose pose = ps.last();
        int a = alpha(alpha);
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        vertex(vc, pose, -half, 0, -half, 0, 0, r, g, b, a, 0, 1, 0);
        vertex(vc, pose, -half, 0, half, 0, 1, r, g, b, a, 0, 1, 0);
        vertex(vc, pose, half, 0, half, 1, 1, r, g, b, a, 0, 1, 0);
        vertex(vc, pose, half, 0, -half, 1, 0, r, g, b, a, 0, 1, 0);
    }

    /**
     * Parede cilíndrica (raio {@code radius}, altura {@code height}) feita de {@code segments} quads.
     * A textura cobre cada segmento na horizontal; {@code vScroll} anima a textura subindo.
     */
    static void cylinder(PoseStack ps, VertexConsumer vc, float radius, float height, int segments, float vScroll,
                         int rgb, float alpha) {
        PoseStack.Pose pose = ps.last();
        int a = alpha(alpha);
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2 * i / segments;
            double a1 = Math.PI * 2 * (i + 1) / segments;
            float x0 = (float) Math.cos(a0) * radius, z0 = (float) Math.sin(a0) * radius;
            float x1 = (float) Math.cos(a1) * radius, z1 = (float) Math.sin(a1) * radius;
            float nx = (float) Math.cos((a0 + a1) / 2), nz = (float) Math.sin((a0 + a1) / 2);
            vertex(vc, pose, x0, 0, z0, 0, 1 + vScroll, r, g, b, a, nx, 0, nz);
            vertex(vc, pose, x1, 0, z1, 1, 1 + vScroll, r, g, b, a, nx, 0, nz);
            vertex(vc, pose, x1, height, z1, 1, vScroll, r, g, b, a, nx, 0, nz);
            vertex(vc, pose, x0, height, z0, 0, vScroll, r, g, b, a, nx, 0, nz);
        }
    }

    static int lighten(int rgb, float t) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        return ((int) (r + (255 - r) * t) << 16) | ((int) (g + (255 - g) * t) << 8) | (int) (b + (255 - b) * t);
    }

    private static int alpha(float alpha) {
        return Mth.clamp((int) (alpha * 255), 0, 255);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v,
                               int r, int g, int b, int a, float nx, float ny, float nz) {
        vc.addVertex(pose, x, y, z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, nx, ny, nz);
    }

    private OaQuad() {}
}
