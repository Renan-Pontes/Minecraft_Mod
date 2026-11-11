package com.renanpontes.isekaimahou.feature.fusions_c.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;

/**
 * Peças de desenho das Misturas C (só cliente): quads brilhantes com as texturas do módulo, cilindros e esferas com
 * texturas de BLOCO do jogo (lava, gelo compactado, areia — animadas pelo próprio atlas), anéis, feixes entre dois
 * pontos e relâmpagos quebrados.
 */
public final class FcQuads {
    public static final ResourceLocation GLOW = tex("fc_glow");
    public static final ResourceLocation BEAM = tex("fc_beam");
    public static final ResourceLocation RING = tex("fc_ring");
    public static final ResourceLocation NOISE = tex("fc_noise");
    public static final ResourceLocation RUNE = tex("fc_rune");
    public static final ResourceLocation PETALS = tex("fc_petals");
    public static final ResourceLocation CRACKS = tex("fc_cracks");

    public static ResourceLocation tex(String name) {
        return IsekaiMahou.id("textures/entity/" + name + ".png");
    }

    /** Translúcido emissivo (não escurece no escuro), sem cull. */
    public static VertexConsumer glow(MultiBufferSource buffers, ResourceLocation texture) {
        return buffers.getBuffer(RenderType.entityTranslucentEmissive(texture));
    }

    /** Aditivo (soma luz) — brilhos e feixes. Tem cull: desenhe os dois lados. */
    public static VertexConsumer additive(MultiBufferSource buffers, ResourceLocation texture) {
        return buffers.getBuffer(RenderType.EYES.apply(texture, RenderStateShard.LIGHTNING_TRANSPARENCY));
    }

    /** Camada com as texturas de bloco do jogo, iluminada pelo mundo (areia, gelo). */
    public static VertexConsumer blocks(MultiBufferSource buffers) {
        return buffers.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));
    }

    /** Texturas de bloco emissivas (lava, magma). */
    public static VertexConsumer blocksGlow(MultiBufferSource buffers) {
        return buffers.getBuffer(RenderType.entityTranslucentEmissive(InventoryMenu.BLOCK_ATLAS));
    }

    public static TextureAtlasSprite sprite(String vanillaBlockTexture) {
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(ResourceLocation.withDefaultNamespace("block/" + vanillaBlockTexture));
    }

    // =====================================================================================
    //  Orientação
    // =====================================================================================


    // =====================================================================================
    //  Quads
    // =====================================================================================

    /** Quad horizontal (plano XZ) centrado, girado {@code rotDeg} em Y, visível dos dois lados. */
    public static void flat(PoseStack ps, VertexConsumer vc, float half, float rotDeg, int rgb, float alpha) {
        float c = Mth.cos(rotDeg * Mth.DEG_TO_RAD) * half, s = Mth.sin(rotDeg * Mth.DEG_TO_RAD) * half;
        PoseStack.Pose p = ps.last();
        int a = alphaByte(alpha), r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        vertex(vc, p, -c + s, 0, -s - c, 0, 0, r, g, b, a, 0, 1, 0);
        vertex(vc, p, -c - s, 0, -s + c, 0, 1, r, g, b, a, 0, 1, 0);
        vertex(vc, p, c - s, 0, s + c, 1, 1, r, g, b, a, 0, 1, 0);
        vertex(vc, p, c + s, 0, s - c, 1, 0, r, g, b, a, 0, 1, 0);
        vertex(vc, p, c + s, 0, s - c, 1, 0, r, g, b, a, 0, -1, 0);
        vertex(vc, p, c - s, 0, s + c, 1, 1, r, g, b, a, 0, -1, 0);
        vertex(vc, p, -c - s, 0, -s + c, 0, 1, r, g, b, a, 0, -1, 0);
        vertex(vc, p, -c + s, 0, -s - c, 0, 0, r, g, b, a, 0, -1, 0);
    }

    /** Quad virado para a câmera (chame depois de {@code ps.mulPose(cameraOrientation)}), girado em Z. */
    public static void billboard(PoseStack ps, VertexConsumer vc, float half, float rotDeg, int rgb, float alpha) {
        float c = Mth.cos(rotDeg * Mth.DEG_TO_RAD) * half, s = Mth.sin(rotDeg * Mth.DEG_TO_RAD) * half;
        PoseStack.Pose p = ps.last();
        int a = alphaByte(alpha), r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        vertex(vc, p, -c + s, -s - c, 0, 0, 1, r, g, b, a, 0, 0, 1);
        vertex(vc, p, c + s, s - c, 0, 1, 1, r, g, b, a, 0, 0, 1);
        vertex(vc, p, c - s, s + c, 0, 1, 0, r, g, b, a, 0, 0, 1);
        vertex(vc, p, -c - s, -s + c, 0, 0, 0, r, g, b, a, 0, 0, 1);
    }

    /** Recorte de textura (u0..u1, v0..v1) num quad de câmera (pétalas de um atlas 2×2). */
    public static void billboardUv(PoseStack ps, VertexConsumer vc, float half, float rotDeg, float u0, float v0, float u1, float v1,
                                   int rgb, float alpha) {
        float c = Mth.cos(rotDeg * Mth.DEG_TO_RAD) * half, s = Mth.sin(rotDeg * Mth.DEG_TO_RAD) * half;
        PoseStack.Pose p = ps.last();
        int a = alphaByte(alpha), r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        vertex(vc, p, -c + s, -s - c, 0, u0, v1, r, g, b, a, 0, 0, 1);
        vertex(vc, p, c + s, s - c, 0, u1, v1, r, g, b, a, 0, 0, 1);
        vertex(vc, p, c - s, s + c, 0, u1, v0, r, g, b, a, 0, 0, 1);
        vertex(vc, p, -c - s, -s + c, 0, u0, v0, r, g, b, a, 0, 0, 1);
    }

    // =====================================================================================
    //  Formas 3D
    // =====================================================================================

    /**
     * Cilindro vertical de y0 a y1 (raio {@code r0} embaixo e {@code r1} em cima) com textura do módulo que rola em V.
     * Desenha por dentro e por fora (serve para camadas com cull).
     */
    public static void tube(PoseStack ps, VertexConsumer vc, float r0, float r1, float y0, float y1, float scrollV, float uRep,
                            float vRep, int rgb, float alphaBottom, float alphaTop, int seg) {
        PoseStack.Pose p = ps.last();
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        int ab = alphaByte(alphaBottom), at = alphaByte(alphaTop);
        for (int j = 0; j < seg; j++) {
            float p0 = Mth.TWO_PI * j / seg, p1 = Mth.TWO_PI * (j + 1) / seg;
            float c0 = Mth.cos(p0), s0 = Mth.sin(p0), c1 = Mth.cos(p1), s1 = Mth.sin(p1);
            float u0 = j / (float) seg * uRep, u1 = (j + 1) / (float) seg * uRep;
            vertex(vc, p, c0 * r0, y0, s0 * r0, u0, vRep + scrollV, r, g, b, ab, c0, 0, s0);
            vertex(vc, p, c1 * r0, y0, s1 * r0, u1, vRep + scrollV, r, g, b, ab, c1, 0, s1);
            vertex(vc, p, c1 * r1, y1, s1 * r1, u1, scrollV, r, g, b, at, c1, 0, s1);
            vertex(vc, p, c0 * r1, y1, s0 * r1, u0, scrollV, r, g, b, at, c0, 0, s0);
            vertex(vc, p, c0 * r1, y1, s0 * r1, u0, scrollV, r, g, b, at, -c0, 0, -s0);
            vertex(vc, p, c1 * r1, y1, s1 * r1, u1, scrollV, r, g, b, at, -c1, 0, -s1);
            vertex(vc, p, c1 * r0, y0, s1 * r0, u1, vRep + scrollV, r, g, b, ab, -c1, 0, -s1);
            vertex(vc, p, c0 * r0, y0, s0 * r0, u0, vRep + scrollV, r, g, b, ab, -c0, 0, -s0);
        }
    }

    /**
     * Cilindro com uma textura de BLOCO repetida a cada {@code tile} blocos de altura (o atlas não repete sozinho,
     * então cada faixa usa o sprite inteiro). {@code wobble} entorta o raio com o tempo (líquido borbulhando).
     */
    public static void spriteTube(PoseStack ps, VertexConsumer vc, TextureAtlasSprite sp, float radius, float y0, float y1, float tile,
                                  int seg, int rgb, float alpha, float time, float wobble) {
        PoseStack.Pose p = ps.last();
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255, a = alphaByte(alpha);
        int rows = Math.max(1, Mth.ceil((y1 - y0) / tile));
        float u0 = sp.getU0(), u1 = sp.getU1(), v0 = sp.getV0(), v1 = sp.getV1();
        for (int k = 0; k < rows; k++) {
            float ya = y0 + k * tile, yb = Math.min(y1, ya + tile);
            float frac = (yb - ya) / tile;
            float vb = v0 + (v1 - v0) * frac;
            for (int j = 0; j < seg; j++) {
                float p0 = Mth.TWO_PI * j / seg, p1 = Mth.TWO_PI * (j + 1) / seg;
                float ra0 = radius * (1 + wobble * Mth.sin(time * 0.3F + j * 1.7F + k)), ra1 = radius * (1 + wobble * Mth.sin(time * 0.3F + (j + 1) * 1.7F + k));
                float rb0 = radius * (1 + wobble * Mth.sin(time * 0.3F + j * 1.7F + k + 1)), rb1 = radius * (1 + wobble * Mth.sin(time * 0.3F + (j + 1) * 1.7F + k + 1));
                float c0 = Mth.cos(p0), s0 = Mth.sin(p0), c1 = Mth.cos(p1), s1 = Mth.sin(p1);
                // Cada coluna do cilindro usa metade do sprite (fica menos esticado)
                float ua = (j % 2 == 0) ? u0 : (u0 + u1) * 0.5F, ub = (j % 2 == 0) ? (u0 + u1) * 0.5F : u1;
                vertex(vc, p, c0 * ra0, ya, s0 * ra0, ua, vb, r, g, b, a, c0, 0, s0);
                vertex(vc, p, c1 * ra1, ya, s1 * ra1, ub, vb, r, g, b, a, c1, 0, s1);
                vertex(vc, p, c1 * rb1, yb, s1 * rb1, ub, v0, r, g, b, a, c1, 0, s1);
                vertex(vc, p, c0 * rb0, yb, s0 * rb0, ua, v0, r, g, b, a, c0, 0, s0);
            }
        }
    }

    /** Esfera com textura do módulo (rolando) ou de bloco; {@code ry} = raio vertical. Normal para fora. */
    public static void sphere(PoseStack ps, VertexConsumer vc, float r, float ry, float u0, float v0, float u1, float v1,
                              float scrollU, int rgb, float alpha, int lat, int lon) {
        PoseStack.Pose p = ps.last();
        int cr = (rgb >> 16) & 255, cg = (rgb >> 8) & 255, cb = rgb & 255, a = alphaByte(alpha);
        for (int i = 0; i < lat; i++) {
            float t0 = Mth.PI * i / lat, t1 = Mth.PI * (i + 1) / lat;
            for (int j = 0; j < lon; j++) {
                float p0 = Mth.TWO_PI * j / lon + scrollU, p1 = Mth.TWO_PI * (j + 1) / lon + scrollU;
                float ua = Mth.lerp(j / (float) lon, u0, u1), ub = Mth.lerp((j + 1) / (float) lon, u0, u1);
                float va = Mth.lerp(i / (float) lat, v0, v1), vb = Mth.lerp((i + 1) / (float) lat, v0, v1);
                sv(vc, p, r, ry, t0, p0, ua, va, cr, cg, cb, a);
                sv(vc, p, r, ry, t0, p1, ub, va, cr, cg, cb, a);
                sv(vc, p, r, ry, t1, p1, ub, vb, cr, cg, cb, a);
                sv(vc, p, r, ry, t1, p0, ua, vb, cr, cg, cb, a);
            }
        }
    }

    private static void sv(VertexConsumer vc, PoseStack.Pose p, float r, float ry, float theta, float phi, float u, float v,
                           int cr, int cg, int cb, int a) {
        float st = Mth.sin(theta), ct = Mth.cos(theta);
        float nx = st * Mth.cos(phi), ny = ct, nz = st * Mth.sin(phi);
        vertex(vc, p, nx * r, ny * ry, nz * r, u, v, cr, cg, cb, a, nx, ny, nz);
    }

    /** Anel achatado no chão entre {@code inner} e {@code outer} (v=0 na borda externa), dos dois lados. */
    public static void ring(PoseStack ps, VertexConsumer vc, float inner, float outer, int rgb, float alpha, int seg) {
        PoseStack.Pose p = ps.last();
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255, a = alphaByte(alpha);
        for (int j = 0; j < seg; j++) {
            float p0 = Mth.TWO_PI * j / seg, p1 = Mth.TWO_PI * (j + 1) / seg;
            float c0 = Mth.cos(p0), s0 = Mth.sin(p0), c1 = Mth.cos(p1), s1 = Mth.sin(p1);
            float u0 = j / (float) seg * 4, u1 = (j + 1) / (float) seg * 4;
            vertex(vc, p, c0 * inner, 0, s0 * inner, u0, 1, r, g, b, a, 0, 1, 0);
            vertex(vc, p, c0 * outer, 0, s0 * outer, u0, 0, r, g, b, a, 0, 1, 0);
            vertex(vc, p, c1 * outer, 0, s1 * outer, u1, 0, r, g, b, a, 0, 1, 0);
            vertex(vc, p, c1 * inner, 0, s1 * inner, u1, 1, r, g, b, a, 0, 1, 0);
            vertex(vc, p, c1 * inner, 0, s1 * inner, u1, 1, r, g, b, a, 0, -1, 0);
            vertex(vc, p, c1 * outer, 0, s1 * outer, u1, 0, r, g, b, a, 0, -1, 0);
            vertex(vc, p, c0 * outer, 0, s0 * outer, u0, 0, r, g, b, a, 0, -1, 0);
            vertex(vc, p, c0 * inner, 0, s0 * inner, u0, 1, r, g, b, a, 0, -1, 0);
        }
    }

    /**
     * Feixe ao longo de +Y (use {@link #alignY} antes) de comprimento {@code len}: 3 planos cruzados com a textura
     * {@code BEAM} rolando ({@code scroll}) — o perfil fica na largura, o comprimento repete a cada {@code vRep} blocos.
     */
    public static void beam(PoseStack ps, VertexConsumer vc, float half, float len, float scroll, float vRep, int rgb, float alpha) {
        PoseStack.Pose p = ps.last();
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255, a = alphaByte(alpha);
        float vEnd = len / vRep + scroll;
        for (int k = 0; k < 3; k++) {
            float ang = k * Mth.PI / 3F;
            float dx = Mth.cos(ang) * half, dz = Mth.sin(ang) * half;
            vertex(vc, p, -dx, 0, -dz, 0, scroll, r, g, b, a, 0, 0, 1);
            vertex(vc, p, dx, 0, dz, 1, scroll, r, g, b, a, 0, 0, 1);
            vertex(vc, p, dx, len, dz, 1, vEnd, r, g, b, a, 0, 0, 1);
            vertex(vc, p, -dx, len, -dz, 0, vEnd, r, g, b, a, 0, 0, 1);
            vertex(vc, p, -dx, len, -dz, 0, vEnd, r, g, b, a, 0, 0, -1);
            vertex(vc, p, dx, len, dz, 1, vEnd, r, g, b, a, 0, 0, -1);
            vertex(vc, p, dx, 0, dz, 1, scroll, r, g, b, a, 0, 0, -1);
            vertex(vc, p, -dx, 0, -dz, 0, scroll, r, g, b, a, 0, 0, -1);
        }
    }

    /** Fita reta (perfil suave do brilho) entre dois pontos locais — segmentos de relâmpago. */
    public static void strip(PoseStack ps, VertexConsumer vc, Vec3 a, Vec3 b, float half, int rgb, float alpha) {
        PoseStack.Pose p = ps.last();
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, bl = rgb & 255, al = alphaByte(alpha);
        Vec3 d = b.subtract(a);
        if (d.lengthSqr() < 1.0E-6) return;
        // Dois planos perpendiculares ao segmento (aparece de qualquer ângulo)
        Vec3 up = Math.abs(d.normalize().y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 s1 = d.cross(up).normalize().scale(half);
        Vec3 s2 = d.cross(s1).normalize().scale(half);
        for (Vec3 s : new Vec3[] {s1, s2}) {
            vtx(vc, p, a.subtract(s), 0, 0.5F, r, g, bl, al);
            vtx(vc, p, a.add(s), 1, 0.5F, r, g, bl, al);
            vtx(vc, p, b.add(s), 1, 0.5F, r, g, bl, al);
            vtx(vc, p, b.subtract(s), 0, 0.5F, r, g, bl, al);
            vtx(vc, p, b.subtract(s), 0, 0.5F, r, g, bl, al);
            vtx(vc, p, b.add(s), 1, 0.5F, r, g, bl, al);
            vtx(vc, p, a.add(s), 1, 0.5F, r, g, bl, al);
            vtx(vc, p, a.subtract(s), 0, 0.5F, r, g, bl, al);
        }
    }

    /**
     * Relâmpago quebrado de {@code a} até {@code b} (pontos locais): zigue-zague determinístico pela semente (muda a
     * cada poucos ticks = tremeluz), com ramificações curtas.
     */
    public static void bolt(PoseStack ps, VertexConsumer vc, Vec3 a, Vec3 b, long seed, float half, int rgb, float alpha) {
        java.util.Random rnd = new java.util.Random(seed);
        Vec3 d = b.subtract(a);
        double len = d.length();
        int n = Math.max(3, Math.min(16, (int) (len * 1.6)));
        double jitter = Math.min(0.9, 0.18 * len / n * 3);
        Vec3 prev = a;
        for (int i = 1; i <= n; i++) {
            Vec3 q = a.add(d.scale(i / (double) n));
            if (i < n) q = q.add((rnd.nextDouble() - 0.5) * jitter * 2, (rnd.nextDouble() - 0.5) * jitter * 2, (rnd.nextDouble() - 0.5) * jitter * 2);
            strip(ps, vc, prev, q, half * 3.2F, rgb, alpha * 0.35F);
            strip(ps, vc, prev, q, half, 0xFFFFFF, alpha);
            if (i < n && rnd.nextInt(4) == 0) {
                Vec3 br = q.add((rnd.nextDouble() - 0.5) * 1.4, (rnd.nextDouble() - 0.5) * 1.4, (rnd.nextDouble() - 0.5) * 1.4);
                strip(ps, vc, q, br, half * 0.7F, rgb, alpha * 0.8F);
            }
            prev = q;
        }
    }

    // =====================================================================================
    //  Cor e vértice
    // =====================================================================================

    static int alphaByte(float alpha) {
        return Mth.clamp((int) (alpha * 255), 0, 255);
    }

    private static void vtx(VertexConsumer vc, PoseStack.Pose p, Vec3 v, float u, float vv, int r, int g, int b, int a) {
        vertex(vc, p, (float) v.x, (float) v.y, (float) v.z, u, vv, r, g, b, a, 0, 1, 0);
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

    /** Vértice com luz do mundo (camadas não emissivas: areia, gelo). */
    static void litVertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v,
                          int rgb, int a, int light, float nx, float ny, float nz) {
        vc.addVertex(pose, x, y, z)
                .setColor((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }

    private FcQuads() {}
}
