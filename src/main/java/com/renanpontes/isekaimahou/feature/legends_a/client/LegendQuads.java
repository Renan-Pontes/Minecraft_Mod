package com.renanpontes.isekaimahou.feature.legends_a.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Peças de desenho das Magias Lendárias A: quads texturizados brilhantes (círculos, portais, halos), esferas,
 * cilindros e feixes. Tudo "emissivo" (não escurece no escuro) e translúcido.
 */
public final class LegendQuads {
    public static final ResourceLocation SIGIL = tex("explosion_sigil");
    public static final ResourceLocation PORTAL = tex("arsenal_portal");
    public static final ResourceLocation PORTAL_RIM = tex("arsenal_portal_rim");
    public static final ResourceLocation GLOW = tex("legend_glow");
    public static final ResourceLocation FIRE = tex("crater_fire");
    public static final ResourceLocation SMOKE = tex("crater_smoke");
    public static final ResourceLocation SHOCKWAVE = tex("crater_shockwave");

    private static final int SPHERE_LAT = 14;
    private static final int SPHERE_LON = 24;

    public static ResourceLocation tex(String name) {
        return IsekaiMahou.id("textures/entity/" + name + ".png");
    }

    /** Camada translúcida emissiva, sem "cull" (os dois lados aparecem). */
    public static VertexConsumer glow(MultiBufferSource buffers, ResourceLocation texture) {
        return buffers.getBuffer(RenderType.entityTranslucentEmissive(texture));
    }

    /**
     * Camada aditiva que respeita o alpha (soma luz: brilhos, feixes) — o "eyes" com mistura SRC_ALPHA + ONE, igual à
     * do raio. Tem "cull": desenhe os dois lados.
     */
    public static VertexConsumer additive(MultiBufferSource buffers, ResourceLocation texture) {
        return buffers.getBuffer(RenderType.EYES.apply(texture, RenderStateShard.LIGHTNING_TRANSPARENCY));
    }

    // =====================================================================================
    //  Quads
    // =====================================================================================

    /** Quad horizontal (plano XZ) centrado na pose, com rotação {@code rotDeg} em volta de Y. */
    public static void flat(PoseStack ps, VertexConsumer vc, float half, float rotDeg, int rgb, float alpha) {
        float c = Mth.cos(rotDeg * Mth.DEG_TO_RAD) * half;
        float s = Mth.sin(rotDeg * Mth.DEG_TO_RAD) * half;
        PoseStack.Pose pose = ps.last();
        int a = alphaByte(alpha);
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        // Cantos (-1,-1) (-1,1) (1,1) (1,-1) girados
        vertex(vc, pose, -c + s, 0, -s - c, 0, 0, r, g, b, a, 0, 1, 0);
        vertex(vc, pose, -c - s, 0, -s + c, 0, 1, r, g, b, a, 0, 1, 0);
        vertex(vc, pose, c - s, 0, s + c, 1, 1, r, g, b, a, 0, 1, 0);
        vertex(vc, pose, c + s, 0, s - c, 1, 0, r, g, b, a, 0, 1, 0);
    }

    /** Quad no plano XY (normal +Z) centrado na pose, girado {@code rotDeg} em volta de Z. Desenha os dois lados. */
    public static void disc(PoseStack ps, VertexConsumer vc, float half, float rotDeg, int rgb, float alpha, boolean bothSides) {
        float c = Mth.cos(rotDeg * Mth.DEG_TO_RAD) * half;
        float s = Mth.sin(rotDeg * Mth.DEG_TO_RAD) * half;
        PoseStack.Pose pose = ps.last();
        int a = alphaByte(alpha);
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        float x0 = -c + s, y0 = -s - c;   // (-1,-1)
        float x1 = c + s, y1 = s - c;     // (1,-1)
        float x2 = c - s, y2 = s + c;     // (1,1)
        float x3 = -c - s, y3 = -s + c;   // (-1,1)
        vertex(vc, pose, x0, y0, 0, 0, 1, r, g, b, a, 0, 0, 1);
        vertex(vc, pose, x1, y1, 0, 1, 1, r, g, b, a, 0, 0, 1);
        vertex(vc, pose, x2, y2, 0, 1, 0, r, g, b, a, 0, 0, 1);
        vertex(vc, pose, x3, y3, 0, 0, 0, r, g, b, a, 0, 0, 1);
        if (bothSides) {
            vertex(vc, pose, x3, y3, 0, 0, 0, r, g, b, a, 0, 0, -1);
            vertex(vc, pose, x2, y2, 0, 1, 0, r, g, b, a, 0, 0, -1);
            vertex(vc, pose, x1, y1, 0, 1, 1, r, g, b, a, 0, 0, -1);
            vertex(vc, pose, x0, y0, 0, 0, 1, r, g, b, a, 0, 0, -1);
        }
    }

    /** Quad horizontal para camadas aditivas (que têm "cull"): desenha em cima e embaixo. */
    public static void flatBoth(PoseStack ps, VertexConsumer vc, float half, float rotDeg, int rgb, float alpha) {
        flat(ps, vc, half, rotDeg, rgb, alpha);
        float c = Mth.cos(rotDeg * Mth.DEG_TO_RAD) * half;
        float s = Mth.sin(rotDeg * Mth.DEG_TO_RAD) * half;
        PoseStack.Pose pose = ps.last();
        int a = alphaByte(alpha);
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        vertex(vc, pose, c + s, 0, s - c, 1, 0, r, g, b, a, 0, -1, 0);
        vertex(vc, pose, c - s, 0, s + c, 1, 1, r, g, b, a, 0, -1, 0);
        vertex(vc, pose, -c - s, 0, -s + c, 0, 1, r, g, b, a, 0, -1, 0);
        vertex(vc, pose, -c + s, 0, -s - c, 0, 0, r, g, b, a, 0, -1, 0);
    }

    // =====================================================================================
    //  Formas 3D
    // =====================================================================================

    /**
     * Esfera (ou elipsoide: raio {@code ry} na vertical) com textura que "rola" ({@code scrollV}) — bola de fogo,
     * cogumelo. {@code bottomAlpha}/{@code topAlpha} fazem degradê de baixo para cima.
     */
    public static void sphere(PoseStack ps, VertexConsumer vc, float r, float ry, float scrollU, float scrollV, float uRep,
                              int bottomRgb, int topRgb, float bottomAlpha, float topAlpha) {
        PoseStack.Pose pose = ps.last();
        for (int i = 0; i < SPHERE_LAT; i++) {
            float t0 = Mth.PI * i / SPHERE_LAT, t1 = Mth.PI * (i + 1) / SPHERE_LAT;
            float k0 = 1 - i / (float) SPHERE_LAT, k1 = 1 - (i + 1) / (float) SPHERE_LAT; // 1 em cima, 0 embaixo
            int c0 = mix(bottomRgb, topRgb, k0), c1 = mix(bottomRgb, topRgb, k1);
            int a0 = alphaByte(Mth.lerp(k0, bottomAlpha, topAlpha)), a1 = alphaByte(Mth.lerp(k1, bottomAlpha, topAlpha));
            for (int j = 0; j < SPHERE_LON; j++) {
                float p0 = Mth.TWO_PI * j / SPHERE_LON, p1 = Mth.TWO_PI * (j + 1) / SPHERE_LON;
                float u0 = j / (float) SPHERE_LON * uRep + scrollU, u1 = (j + 1) / (float) SPHERE_LON * uRep + scrollU;
                float v0 = i / (float) SPHERE_LAT + scrollV, v1 = (i + 1) / (float) SPHERE_LAT + scrollV;
                sv(vc, pose, r, ry, t0, p0, u0, v0, c0, a0);
                sv(vc, pose, r, ry, t0, p1, u1, v0, c0, a0);
                sv(vc, pose, r, ry, t1, p1, u1, v1, c1, a1);
                sv(vc, pose, r, ry, t1, p0, u0, v1, c1, a1);
            }
        }
    }

    private static void sv(VertexConsumer vc, PoseStack.Pose pose, float r, float ry, float theta, float phi, float u, float v,
                           int rgb, int a) {
        float st = Mth.sin(theta), ct = Mth.cos(theta);
        float nx = st * Mth.cos(phi), ny = ct, nz = st * Mth.sin(phi);
        vertex(vc, pose, nx * r, ny * ry, nz * r, u, v, (rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, a, nx, ny, nz);
    }

    /**
     * Cilindro vertical aberto (coluna de fogo/fumaça), de y0 a y1, raio embaixo {@code r0} e em cima {@code r1}.
     * Alpha some nas pontas (base forte, topo mais fraco).
     */
    public static void tube(PoseStack ps, VertexConsumer vc, float r0, float r1, float y0, float y1, float scrollV, float uRep,
                            float vRep, int rgb, float alphaBottom, float alphaTop) {
        PoseStack.Pose pose = ps.last();
        int seg = 20;
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        int ab = alphaByte(alphaBottom), at = alphaByte(alphaTop);
        for (int j = 0; j < seg; j++) {
            float p0 = Mth.TWO_PI * j / seg, p1 = Mth.TWO_PI * (j + 1) / seg;
            float c0 = Mth.cos(p0), s0 = Mth.sin(p0), c1 = Mth.cos(p1), s1 = Mth.sin(p1);
            float u0 = j / (float) seg * uRep, u1 = (j + 1) / (float) seg * uRep;
            vertex(vc, pose, c0 * r0, y0, s0 * r0, u0, vRep + scrollV, r, g, b, ab, c0, 0, s0);
            vertex(vc, pose, c1 * r0, y0, s1 * r0, u1, vRep + scrollV, r, g, b, ab, c1, 0, s1);
            vertex(vc, pose, c1 * r1, y1, s1 * r1, u1, scrollV, r, g, b, at, c1, 0, s1);
            vertex(vc, pose, c0 * r1, y1, s0 * r1, u0, scrollV, r, g, b, at, c0, 0, s0);
        }
    }

    /** Anel achatado no chão entre os raios {@code inner} e {@code outer} (onda de choque). */
    public static void ring(PoseStack ps, VertexConsumer vc, float inner, float outer, int rgb, float alpha) {
        PoseStack.Pose pose = ps.last();
        int seg = 40;
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        int a = alphaByte(alpha);
        for (int j = 0; j < seg; j++) {
            float p0 = Mth.TWO_PI * j / seg, p1 = Mth.TWO_PI * (j + 1) / seg;
            float c0 = Mth.cos(p0), s0 = Mth.sin(p0), c1 = Mth.cos(p1), s1 = Mth.sin(p1);
            float u0 = j / (float) seg, u1 = (j + 1) / (float) seg;
            vertex(vc, pose, c0 * inner, 0, s0 * inner, u0, 1, r, g, b, a, 0, 1, 0);
            vertex(vc, pose, c0 * outer, 0, s0 * outer, u0, 0, r, g, b, a, 0, 1, 0);
            vertex(vc, pose, c1 * outer, 0, s1 * outer, u1, 0, r, g, b, a, 0, 1, 0);
            vertex(vc, pose, c1 * inner, 0, s1 * inner, u1, 1, r, g, b, a, 0, 1, 0);
        }
    }

    /** Feixe vertical (4 faces cruzadas) de y0 a y1 com meia largura {@code half}, para camadas aditivas. */
    public static void beam(PoseStack ps, VertexConsumer vc, float half, float y0, float y1, int rgb, float alpha) {
        PoseStack.Pose pose = ps.last();
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        int a = alphaByte(alpha);
        for (int k = 0; k < 2; k++) {
            float dx = k == 0 ? half : 0, dz = k == 0 ? 0 : half;
            for (int side = 0; side < 2; side++) {
                float sx = side == 0 ? dx : -dx, sz = side == 0 ? dz : -dz;
                // v fixo no meio da textura de brilho: perfil suave na largura, uniforme no comprimento
                vertex(vc, pose, -sx, y0, -sz, 0, 0.5F, r, g, b, a, 0, 0, 1);
                vertex(vc, pose, sx, y0, sz, 1, 0.5F, r, g, b, a, 0, 0, 1);
                vertex(vc, pose, sx, y1, sz, 1, 0.5F, r, g, b, a, 0, 0, 1);
                vertex(vc, pose, -sx, y1, -sz, 0, 0.5F, r, g, b, a, 0, 0, 1);
            }
        }
    }

    // =====================================================================================
    //  Cor
    // =====================================================================================
    public static int mix(int a, int b, float t) {
        t = Mth.clamp(t, 0, 1);
        int r = (int) Mth.lerp(t, (a >> 16) & 255, (b >> 16) & 255);
        int g = (int) Mth.lerp(t, (a >> 8) & 255, (b >> 8) & 255);
        int bl = (int) Mth.lerp(t, a & 255, b & 255);
        return (r << 16) | (g << 8) | bl;
    }

    public static int lighten(int rgb, float t) {
        return mix(rgb, 0xFFFFFF, t);
    }

    public static int darken(int rgb, float t) {
        return mix(rgb, 0x000000, t);
    }

    static int alphaByte(float alpha) {
        return Mth.clamp((int) (alpha * 255), 0, 255);
    }

    static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v,
                       int r, int g, int b, int a, float nx, float ny, float nz) {
        vc.addVertex(pose, x, y, z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, nx, ny, nz);
    }

    private LegendQuads() {}
}
