package com.renanpontes.isekaimahou.feature.legends_d.client;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Geometria desenhada pelas lendas do legends_d (só cliente): esferas de brilho com borda "fresnel", cascas de
 * energia texturizadas, cascas de negativo, discos/halos/anéis, fitas de luz, paredes de cilindro e o feixe estilo
 * farol com comprimento livre.
 */
final class LdDraw {

    static int argb(int rgb, float a) {
        return ((int) (Mth.clamp(a, 0F, 1F) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    /** Cor para cascas energySwirl (mistura aditiva pura: a intensidade vai no RGB). */
    static int swirl(int rgb, float intensity) {
        float k = Mth.clamp(intensity, 0F, 1F);
        int r = (int) (((rgb >> 16) & 255) * k), g = (int) (((rgb >> 8) & 255) * k), b = (int) ((rgb & 255) * k);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    static int lighten(int rgb, float f) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        return ((int) (r + (255 - r) * f) << 16) | ((int) (g + (255 - g) * f) << 8) | (int) (b + (255 - b) * f);
    }

    static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static void v(VertexConsumer vc, Matrix4f m, float x, float y, float z, int argb) {
        vc.addVertex(m, x, y, z).setColor(argb);
    }

    /** Rotação que leva +Y até a direção dada. */
    static Quaternionf yTo(Vec3 dir) {
        Vec3 d = dir.lengthSqr() < 1.0E-8 ? new Vec3(0, 1, 0) : dir.normalize();
        return new Quaternionf().rotationTo(0F, 1F, 0F, (float) d.x, (float) d.y, (float) d.z);
    }

    // =====================================================================================
    //  Esferas
    // =====================================================================================

    /** Esfera de brilho: alfa {@code aCenter} de frente e {@code aRim} na borda. {@code cam} = câmera no espaço local. */
    static void glowSphere(VertexConsumer vc, Matrix4f m, float r, int lat, int lon, int rgb, float aCenter, float aRim, Vec3 cam) {
        for (int i = 0; i < lat; i++) {
            for (int j = 0; j < lon; j++) {
                glowVertex(vc, m, r, i, j, lat, lon, rgb, aCenter, aRim, cam);
                glowVertex(vc, m, r, i, j + 1, lat, lon, rgb, aCenter, aRim, cam);
                glowVertex(vc, m, r, i + 1, j + 1, lat, lon, rgb, aCenter, aRim, cam);
                glowVertex(vc, m, r, i + 1, j, lat, lon, rgb, aCenter, aRim, cam);
            }
        }
    }

    private static void glowVertex(VertexConsumer vc, Matrix4f m, float r, int i, int j, int lat, int lon, int rgb,
                                   float aCenter, float aRim, Vec3 cam) {
        float th = (float) Math.PI * i / lat, ph = (float) Math.PI * 2 * j / lon;
        float nx = Mth.sin(th) * Mth.cos(ph), ny = Mth.cos(th), nz = Mth.sin(th) * Mth.sin(ph);
        float px = nx * r, py = ny * r, pz = nz * r;
        double vx = cam.x - px, vy = cam.y - py, vz = cam.z - pz;
        double len = Math.sqrt(vx * vx + vy * vy + vz * vz);
        float facing = len < 1.0E-5 ? 1F : (float) Math.abs((nx * vx + ny * vy + nz * vz) / len);
        float rim = (float) Math.pow(1F - facing, 1.6);
        v(vc, m, px, py, pz, argb(rgb, Mth.lerp(rim, aCenter, aRim)));
    }

    /** Esfera de cor única (cascas de negativo: branco opaco). */
    static void flatSphere(VertexConsumer vc, Matrix4f m, float r, int lat, int lon, int argb) {
        for (int i = 0; i < lat; i++) {
            float t0 = (float) Math.PI * i / lat, t1 = (float) Math.PI * (i + 1) / lat;
            float y0 = Mth.cos(t0) * r, y1 = Mth.cos(t1) * r, s0 = Mth.sin(t0) * r, s1 = Mth.sin(t1) * r;
            for (int j = 0; j < lon; j++) {
                float p0 = (float) Math.PI * 2 * j / lon, p1 = (float) Math.PI * 2 * (j + 1) / lon;
                v(vc, m, Mth.cos(p0) * s0, y0, Mth.sin(p0) * s0, argb);
                v(vc, m, Mth.cos(p1) * s0, y0, Mth.sin(p1) * s0, argb);
                v(vc, m, Mth.cos(p1) * s1, y1, Mth.sin(p1) * s1, argb);
                v(vc, m, Mth.cos(p0) * s1, y1, Mth.sin(p0) * s1, argb);
            }
        }
    }

    /** Esfera texturizada (energySwirl) com ondulação na superfície ({@code wobble}). */
    static void texSphere(VertexConsumer vc, PoseStack.Pose pose, float r, int lat, int lon, int argb, float uRepeat,
                          float wobble, float time) {
        for (int i = 0; i < lat; i++) {
            for (int j = 0; j < lon; j++) {
                texVertex(vc, pose, r, i, j, lat, lon, argb, uRepeat, wobble, time);
                texVertex(vc, pose, r, i, j + 1, lat, lon, argb, uRepeat, wobble, time);
                texVertex(vc, pose, r, i + 1, j + 1, lat, lon, argb, uRepeat, wobble, time);
                texVertex(vc, pose, r, i + 1, j, lat, lon, argb, uRepeat, wobble, time);
            }
        }
    }

    private static void texVertex(VertexConsumer vc, PoseStack.Pose pose, float r, int i, int j, int lat, int lon, int argb,
                                  float uRepeat, float wobble, float time) {
        float th = (float) Math.PI * i / lat, ph = (float) Math.PI * 2 * j / lon;
        float nx = Mth.sin(th) * Mth.cos(ph), ny = Mth.cos(th), nz = Mth.sin(th) * Mth.sin(ph);
        float rr = r * (1F + wobble * Mth.sin(ph * 3F + th * 4F + time * 0.35F) * Mth.sin(th));
        vc.addVertex(pose, nx * rr, ny * rr, nz * rr)
                .setColor(argb)
                .setUv(j / (float) lon * uRepeat, i / (float) lat)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, nx, ny, nz);
    }

    // =====================================================================================
    //  Discos, anéis, quads, fitas
    // =====================================================================================

    /** Disco radial no plano XY local (vire para a câmera antes). */
    static void radial(VertexConsumer vc, Matrix4f m, float rIn, float rOut, int cIn, int cOut, int seg) {
        for (int k = 0; k < seg; k++) {
            float a0 = (float) (Math.PI * 2 * k / seg), a1 = (float) (Math.PI * 2 * (k + 1) / seg);
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            v(vc, m, c0 * rIn, s0 * rIn, 0, cIn);
            v(vc, m, c0 * rOut, s0 * rOut, 0, cOut);
            v(vc, m, c1 * rOut, s1 * rOut, 0, cOut);
            v(vc, m, c1 * rIn, s1 * rIn, 0, cIn);
        }
    }

    /** Disco radial deitado (plano XZ local). */
    static void radialFlat(VertexConsumer vc, Matrix4f m, float rIn, float rOut, int cIn, int cOut, int seg) {
        for (int k = 0; k < seg; k++) {
            float a0 = (float) (Math.PI * 2 * k / seg), a1 = (float) (Math.PI * 2 * (k + 1) / seg);
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            v(vc, m, c0 * rIn, 0, s0 * rIn, cIn);
            v(vc, m, c0 * rOut, 0, s0 * rOut, cOut);
            v(vc, m, c1 * rOut, 0, s1 * rOut, cOut);
            v(vc, m, c1 * rIn, 0, s1 * rIn, cIn);
        }
    }

    /** Halo: transparente em r0, cheio em r1, transparente em r2 (plano XY). */
    static void halo(VertexConsumer vc, Matrix4f m, float r0, float r1, float r2, int rgb, float alpha, int seg) {
        radial(vc, m, r0, r1, argb(rgb, 0F), argb(rgb, alpha), seg);
        radial(vc, m, r1, r2, argb(rgb, alpha), argb(rgb, 0F), seg);
    }

    /** Anel deitado (plano XZ) com miolo e borda suaves. */
    static void ringFlat(VertexConsumer vc, Matrix4f m, float r, float width, int rgb, float alpha, int seg) {
        radialFlat(vc, m, Math.max(0F, r - width), r, argb(rgb, 0F), argb(rgb, alpha), seg);
        radialFlat(vc, m, r, r + width, argb(rgb, alpha), argb(rgb, 0F), seg);
    }

    /** Quad texturizado deitado (plano XZ) de meia largura {@code half}. */
    static void texFlat(VertexConsumer vc, Matrix4f m, float half, int argb) {
        vc.addVertex(m, -half, 0, -half).setUv(0, 0).setColor(argb);
        vc.addVertex(m, -half, 0, half).setUv(0, 1).setColor(argb);
        vc.addVertex(m, half, 0, half).setUv(1, 1).setColor(argb);
        vc.addVertex(m, half, 0, -half).setUv(1, 0).setColor(argb);
    }

    /** Fita reta de {@code a} a {@code b}, larguras {@code wa}/{@code wb}, virada para {@code side}. */
    static void ribbon(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 side, float wa, float wb, int ca, int cb) {
        Vec3 sa = side.scale(wa), sb = side.scale(wb);
        v(vc, m, (float) (a.x - sa.x), (float) (a.y - sa.y), (float) (a.z - sa.z), ca);
        v(vc, m, (float) (a.x + sa.x), (float) (a.y + sa.y), (float) (a.z + sa.z), ca);
        v(vc, m, (float) (b.x + sb.x), (float) (b.y + sb.y), (float) (b.z + sb.z), cb);
        v(vc, m, (float) (b.x - sb.x), (float) (b.y - sb.y), (float) (b.z - sb.z), cb);
    }

    /** Fita de brilho com bordas suaves (cheia no meio, transparente nas beiradas). */
    static void softRibbon(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 side, float wa, float wb, int rgb, float alphaA, float alphaB) {
        Vec3 sa = side.scale(wa), sb = side.scale(wb);
        int ca = argb(rgb, alphaA), cb = argb(rgb, alphaB), zero = argb(rgb, 0F);
        for (int s = -1; s <= 1; s += 2) {
            v(vc, m, (float) a.x, (float) a.y, (float) a.z, ca);
            v(vc, m, (float) (a.x + sa.x * s), (float) (a.y + sa.y * s), (float) (a.z + sa.z * s), zero);
            v(vc, m, (float) (b.x + sb.x * s), (float) (b.y + sb.y * s), (float) (b.z + sb.z * s), zero);
            v(vc, m, (float) b.x, (float) b.y, (float) b.z, cb);
        }
    }

    /** Fita de luz de {@code a} a {@code b} sempre virada para a câmera ({@code cam} no mesmo espaço). */
    static void beamTo(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 cam, float width, int rgb, float alphaA, float alphaB) {
        Vec3 d = b.subtract(a);
        Vec3 side = d.cross(cam.subtract(a));
        side = side.lengthSqr() < 1.0E-8 ? new Vec3(1, 0, 0) : side.normalize();
        softRibbon(vc, m, a, b, side, width, width, rgb, alphaA, alphaB);
    }

    /** Parede de cilindro (eixo Y), alfa {@code aBottom} embaixo e {@code aTop} em cima. */
    static void cylinderWall(VertexConsumer vc, Matrix4f m, float r, float h, int seg, int rgb, float aBottom, float aTop) {
        int cb = argb(rgb, aBottom), ct = argb(rgb, aTop);
        for (int k = 0; k < seg; k++) {
            float a0 = (float) (Math.PI * 2 * k / seg), a1 = (float) (Math.PI * 2 * (k + 1) / seg);
            float x0 = Mth.cos(a0) * r, z0 = Mth.sin(a0) * r, x1 = Mth.cos(a1) * r, z1 = Mth.sin(a1) * r;
            v(vc, m, x0, 0, z0, cb);
            v(vc, m, x1, 0, z1, cb);
            v(vc, m, x1, h, z1, ct);
            v(vc, m, x0, h, z0, ct);
        }
    }

    /** Quad que cobre a tela toda logo à frente da câmera (coordenadas relativas à câmera). */
    static void screenQuad(VertexConsumer vc, Matrix4f m, Camera camera, int argb) {
        Vector3f look = camera.getLookVector(), left = camera.getLeftVector(), up = camera.getUpVector();
        float d = 0.2F, s = 2.0F;
        float cx = look.x() * d, cy = look.y() * d, cz = look.z() * d;
        v(vc, m, cx + (left.x() + up.x()) * s, cy + (left.y() + up.y()) * s, cz + (left.z() + up.z()) * s, argb);
        v(vc, m, cx + (left.x() - up.x()) * s, cy + (left.y() - up.y()) * s, cz + (left.z() - up.z()) * s, argb);
        v(vc, m, cx + (-left.x() - up.x()) * s, cy + (-left.y() - up.y()) * s, cz + (-left.z() - up.z()) * s, argb);
        v(vc, m, cx + (-left.x() + up.x()) * s, cy + (-left.y() + up.y()) * s, cz + (-left.z() + up.z()) * s, argb);
    }

    // =====================================================================================
    //  Feixe estilo farol (comprimento livre)
    // =====================================================================================
    static void beaconBeam(PoseStack ps, MultiBufferSource buffers, Vec3 from, Vec3 vec, int rgb, float radius, float glowRadius,
                           float alpha, float time) {
        float len = (float) vec.length();
        if (len < 0.05F || radius <= 0.001F) return;
        ps.pushPose();
        ps.translate(from.x, from.y, from.z);
        ps.mulPose(yTo(vec));
        float spin = (time % 40F) * 2.25F * 4F - 45F;
        float f1 = -time;
        float scroll = Mth.frac(f1 * 0.2F - (float) Mth.floor(f1 * 0.1F));
        float v0 = -1F + scroll;

        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(spin));
        VertexConsumer core = buffers.getBuffer(RenderType.beaconBeam(BeaconRenderer.BEAM_LOCATION, false));
        int cc = 0xFF000000 | (rgb & 0xFFFFFF);
        float v1 = len * (0.5F / radius) + v0;
        prism(core, ps.last(), cc, len, 0F, radius, radius, 0F, -radius, 0F, 0F, -radius, v0, v1);
        ps.popPose();

        VertexConsumer glow = buffers.getBuffer(RenderType.beaconBeam(BeaconRenderer.BEAM_LOCATION, true));
        int gc = argb(rgb, 0.18F * alpha);
        float g = glowRadius;
        prism(glow, ps.last(), gc, len, -g, -g, g, -g, -g, g, g, g, v0, len + v0);
        ps.popPose();
    }

    private static void prism(VertexConsumer vc, PoseStack.Pose pose, int color, float h,
                              float x1, float z1, float x2, float z2, float x3, float z3, float x4, float z4, float v0, float v1) {
        face(vc, pose, color, h, x1, z1, x2, z2, v0, v1);
        face(vc, pose, color, h, x4, z4, x3, z3, v0, v1);
        face(vc, pose, color, h, x2, z2, x4, z4, v0, v1);
        face(vc, pose, color, h, x3, z3, x1, z1, v0, v1);
    }

    private static void face(VertexConsumer vc, PoseStack.Pose pose, int color, float h, float x0, float z0, float x1, float z1,
                             float v0, float v1) {
        beamVertex(vc, pose, color, h, x0, z0, 1F, v1);
        beamVertex(vc, pose, color, 0, x0, z0, 1F, v0);
        beamVertex(vc, pose, color, 0, x1, z1, 0F, v0);
        beamVertex(vc, pose, color, h, x1, z1, 0F, v1);
    }

    private static void beamVertex(VertexConsumer vc, PoseStack.Pose pose, int color, float y, float x, float z, float u, float v) {
        vc.addVertex(pose, x, y, z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0F, 1F, 0F);
    }

    private LdDraw() {}
}
