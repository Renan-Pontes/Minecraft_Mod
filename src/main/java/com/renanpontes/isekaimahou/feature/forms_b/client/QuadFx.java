package com.renanpontes.isekaimahou.feature.forms_b.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Desenho de quads texturizados brilhantes (círculos mágicos, anéis, orbes) para os renderers do forms_b. */
public final class QuadFx {

    public static ResourceLocation tex(String name) {
        return IsekaiMahou.id("textures/entity/" + name + ".png");
    }

    /** Camada translúcida e "emissiva" (não escurece no escuro, não escreve profundidade). */
    public static VertexConsumer glow(MultiBufferSource buffers, ResourceLocation texture) {
        return buffers.getBuffer(RenderType.entityTranslucentEmissive(texture));
    }

    /** Quad horizontal (plano XZ), centrado na pose atual, com meia largura {@code half}. */
    public static void flat(PoseStack ps, VertexConsumer vc, float half, int rgb, float alpha) {
        PoseStack.Pose pose = ps.last();
        int a = alphaByte(alpha);
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        vertex(vc, pose, -half, 0, -half, 0, 0, r, g, b, a, 0, 1, 0);
        vertex(vc, pose, -half, 0, half, 0, 1, r, g, b, a, 0, 1, 0);
        vertex(vc, pose, half, 0, half, 1, 1, r, g, b, a, 0, 1, 0);
        vertex(vc, pose, half, 0, -half, 1, 0, r, g, b, a, 0, 1, 0);
    }

    /** Quad vertical (plano XY), centrado na pose atual. */
    public static void upright(PoseStack ps, VertexConsumer vc, float halfW, float halfH, int rgb, float alpha) {
        PoseStack.Pose pose = ps.last();
        int a = alphaByte(alpha);
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        vertex(vc, pose, -halfW, -halfH, 0, 0, 1, r, g, b, a, 0, 0, 1);
        vertex(vc, pose, halfW, -halfH, 0, 1, 1, r, g, b, a, 0, 0, 1);
        vertex(vc, pose, halfW, halfH, 0, 1, 0, r, g, b, a, 0, 0, 1);
        vertex(vc, pose, -halfW, halfH, 0, 0, 0, r, g, b, a, 0, 0, 1);
    }

    /** Mistura a cor com branco (0..1). */
    public static int lighten(int rgb, float t) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        r = (int) (r + (255 - r) * t);
        g = (int) (g + (255 - g) * t);
        b = (int) (b + (255 - b) * t);
        return (r << 16) | (g << 8) | b;
    }

    /** Escurece a cor (0..1). */
    public static int darken(int rgb, float t) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        return ((int) (r * (1 - t)) << 16) | ((int) (g * (1 - t)) << 8) | (int) (b * (1 - t));
    }

    private static int alphaByte(float alpha) {
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

    private QuadFx() {}
}
