package com.renanpontes.isekaimahou.feature.legends_f.client;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Geometria desenhada pelo legends_f (só cliente): discos e anéis deitados, "sóis" virados para a câmera, fitas,
 * quads texturizados, esferas com textura repetida (cúpula hexagonal), e caixas/segmentos SÓLIDOS com luz do mundo
 * (formato de entidade: cor, UV, overlay, luz e normal) para montar a vassoura, os Kodama, as motosserras, o cervo
 * divino e as mãos da Inveja peça por peça.
 */
final class LfDraw {

    static int argb(int rgb, float a) {
        return ((int) (Mth.clamp(a, 0F, 1F) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    static int lighten(int rgb, float f) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        return ((int) (r + (255 - r) * f) << 16) | ((int) (g + (255 - g) * f) << 8) | (int) (b + (255 - b) * f);
    }

    static int darken(int rgb, float f) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        return ((int) (r * (1 - f)) << 16) | ((int) (g * (1 - f)) << 8) | (int) (b * (1 - f));
    }

    static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    static float smooth(float x) {
        float t = Mth.clamp(x, 0F, 1F);
        return t * t * (3 - 2 * t);
    }

    static void v(VertexConsumer vc, Matrix4f m, float x, float y, float z, int argb) {
        vc.addVertex(m, x, y, z).setColor(argb);
    }

    static Quaternionf yTo(Vec3 dir) {
        Vec3 d = dir.lengthSqr() < 1.0E-8 ? new Vec3(0, 1, 0) : dir.normalize();
        return new Quaternionf().rotationTo(0F, 1F, 0F, (float) d.x, (float) d.y, (float) d.z);
    }

    // =====================================================================================
    //  Discos, anéis e sóis (POSITION_COLOR)
    // =====================================================================================

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

    static void ringFlat(VertexConsumer vc, Matrix4f m, float r, float width, int rgb, float alpha, int seg) {
        radialFlat(vc, m, Math.max(0F, r - width), r, argb(rgb, 0F), argb(rgb, alpha), seg);
        radialFlat(vc, m, r, r + width, argb(rgb, alpha), argb(rgb, 0F), seg);
    }

    /** Anel em pé (plano XY local) — para anéis verticais. */
    static void ringUpright(VertexConsumer vc, Matrix4f m, float r, float width, int rgb, float alpha, int seg) {
        for (int k = 0; k < seg; k++) {
            float a0 = (float) (Math.PI * 2 * k / seg), a1 = (float) (Math.PI * 2 * (k + 1) / seg);
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            int in = argb(rgb, 0F), mid = argb(rgb, alpha);
            float ri = Math.max(0, r - width), ro = r + width;
            v(vc, m, c0 * ri, s0 * ri, 0, in);
            v(vc, m, c0 * r, s0 * r, 0, mid);
            v(vc, m, c1 * r, s1 * r, 0, mid);
            v(vc, m, c1 * ri, s1 * ri, 0, in);
            v(vc, m, c0 * r, s0 * r, 0, mid);
            v(vc, m, c0 * ro, s0 * ro, 0, in);
            v(vc, m, c1 * ro, s1 * ro, 0, in);
            v(vc, m, c1 * r, s1 * r, 0, mid);
        }
    }

    /** "Sol" virado para a câmera em {@code c} (coordenadas da matriz). */
    static void billboard(VertexConsumer vc, Matrix4f m, Vec3 c, Vector3f left, Vector3f up, float r, int cIn, int cOut) {
        int seg = 12;
        for (int k = 0; k < seg; k++) {
            double a0 = Math.PI * 2 * k / seg, a1 = Math.PI * 2 * (k + 1) / seg;
            float x0 = (float) Math.cos(a0) * r, y0 = (float) Math.sin(a0) * r, x1 = (float) Math.cos(a1) * r, y1 = (float) Math.sin(a1) * r;
            bv(vc, m, c, left, up, 0, 0, cIn);
            bv(vc, m, c, left, up, x0, y0, cOut);
            bv(vc, m, c, left, up, x1, y1, cOut);
            bv(vc, m, c, left, up, 0, 0, cIn);
        }
    }

    /** Estrela de {@code n} pontas virada para a câmera (estouro do Bang!). */
    static void starBurst(VertexConsumer vc, Matrix4f m, Vec3 c, Vector3f left, Vector3f up, float rOut, float rIn, int n, float rot,
                          int cIn, int cOut) {
        for (int k = 0; k < n; k++) {
            double a = rot + Math.PI * 2 * k / n, aL = a - Math.PI / n, aR = a + Math.PI / n;
            bv(vc, m, c, left, up, 0, 0, cIn);
            bv(vc, m, c, left, up, (float) Math.cos(aL) * rIn, (float) Math.sin(aL) * rIn, cIn);
            bv(vc, m, c, left, up, (float) Math.cos(a) * rOut, (float) Math.sin(a) * rOut, cOut);
            bv(vc, m, c, left, up, (float) Math.cos(aR) * rIn, (float) Math.sin(aR) * rIn, cIn);
        }
    }

    private static void bv(VertexConsumer vc, Matrix4f m, Vec3 c, Vector3f left, Vector3f up, float x, float y, int argb) {
        vc.addVertex(m, (float) (c.x + left.x() * x + up.x() * y), (float) (c.y + left.y() * x + up.y() * y),
                (float) (c.z + left.z() * x + up.z() * y)).setColor(argb);
    }

    /** Quad texturizado virado para a câmera (POSITION_TEX_COLOR). */
    static void texBillboard(VertexConsumer vc, Matrix4f m, Vec3 c, Vector3f left, Vector3f up, float half, int argb) {
        tbv(vc, m, c, left, up, -half, -half, 1, 1, argb);
        tbv(vc, m, c, left, up, half, -half, 0, 1, argb);
        tbv(vc, m, c, left, up, half, half, 0, 0, argb);
        tbv(vc, m, c, left, up, -half, half, 1, 0, argb);
    }

    private static void tbv(VertexConsumer vc, Matrix4f m, Vec3 c, Vector3f left, Vector3f up, float x, float y, float u, float vv, int argb) {
        vc.addVertex(m, (float) (c.x + left.x() * x + up.x() * y), (float) (c.y + left.y() * x + up.y() * y),
                (float) (c.z + left.z() * x + up.z() * y)).setUv(u, vv).setColor(argb);
    }

    static Vec3 facingSide(Vec3 a, Vec3 b, Vec3 cam) {
        Vec3 d = b.subtract(a);
        Vec3 side = d.cross(cam.subtract(a));
        return side.lengthSqr() < 1.0E-8 ? new Vec3(1, 0, 0) : side.normalize();
    }

    /** Fita com bordas suaves: cor cheia no meio, transparente nas beiradas. */
    static void softRibbon(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 side, float wa, float wb, int rgb, float alphaA, float alphaB) {
        Vec3 sa = side.scale(wa), sb = side.scale(wb);
        int ca = argb(rgb, alphaA), cb = argb(rgb, alphaB), za = argb(rgb, 0F);
        for (int s = -1; s <= 1; s += 2) {
            v(vc, m, (float) a.x, (float) a.y, (float) a.z, ca);
            v(vc, m, (float) (a.x + sa.x * s), (float) (a.y + sa.y * s), (float) (a.z + sa.z * s), za);
            v(vc, m, (float) (b.x + sb.x * s), (float) (b.y + sb.y * s), (float) (b.z + sb.z * s), za);
            v(vc, m, (float) b.x, (float) b.y, (float) b.z, cb);
        }
    }

    // =====================================================================================
    //  Quads e esferas texturizadas (POSITION_TEX_COLOR)
    // =====================================================================================

    static void texFlat(VertexConsumer vc, Matrix4f m, float half, int argb) {
        vc.addVertex(m, -half, 0, -half).setUv(0, 0).setColor(argb);
        vc.addVertex(m, -half, 0, half).setUv(0, 1).setColor(argb);
        vc.addVertex(m, half, 0, half).setUv(1, 1).setColor(argb);
        vc.addVertex(m, half, 0, -half).setUv(1, 0).setColor(argb);
    }

    /** Quad em pé (plano XY), largura 2·hw, altura 2·hh. */
    static void texUpright(VertexConsumer vc, Matrix4f m, float hw, float hh, int argb) {
        vc.addVertex(m, -hw, -hh, 0).setUv(0, 1).setColor(argb);
        vc.addVertex(m, hw, -hh, 0).setUv(1, 1).setColor(argb);
        vc.addVertex(m, hw, hh, 0).setUv(1, 0).setColor(argb);
        vc.addVertex(m, -hw, hh, 0).setUv(0, 0).setColor(argb);
    }

    /**
     * Esfera (ou cúpula, com {@code latMax} &lt; 1) de raio {@code r} com a textura repetida {@code uRep}×{@code vRep}.
     * A cor de cada vértice vem de {@code shade(nx, ny, nz)} (ARGB).
     */
    static void texSphere(VertexConsumer vc, Matrix4f m, float r, int lat, int lon, float latMax, float uRep, float vRep, float uOff,
                          java.util.function.IntUnaryOperator shadeByRow, int argb) {
        for (int i = 0; i < lat; i++) {
            float t0 = (float) i / lat * latMax, t1 = (float) (i + 1) / lat * latMax;
            float th0 = t0 * (float) Math.PI, th1 = t1 * (float) Math.PI;
            int c0 = shadeByRow.applyAsInt(i), c1 = shadeByRow.applyAsInt(i + 1);
            for (int j = 0; j < lon; j++) {
                float ph0 = (float) (Math.PI * 2 * j / lon), ph1 = (float) (Math.PI * 2 * (j + 1) / lon);
                float u0 = uOff + uRep * j / lon, u1 = uOff + uRep * (j + 1) / lon;
                sv(vc, m, r, th0, ph0, u0, vRep * t0, c0 == 0 ? argb : c0);
                sv(vc, m, r, th0, ph1, u1, vRep * t0, c0 == 0 ? argb : c0);
                sv(vc, m, r, th1, ph1, u1, vRep * t1, c1 == 0 ? argb : c1);
                sv(vc, m, r, th1, ph0, u0, vRep * t1, c1 == 0 ? argb : c1);
            }
        }
    }

    private static void sv(VertexConsumer vc, Matrix4f m, float r, float th, float ph, float u, float vv, int argb) {
        float x = Mth.sin(th) * Mth.cos(ph) * r, y = Mth.cos(th) * r, z = Mth.sin(th) * Mth.sin(ph) * r;
        vc.addVertex(m, x, y, z).setUv(u, vv).setColor(argb);
    }

    // =====================================================================================
    //  Peças sólidas com luz (formato de entidade)
    // =====================================================================================

    private static void ev(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, int argb, float u, float vv, int light,
                           float nx, float ny, float nz) {
        vc.addVertex(pose, x, y, z).setColor(argb).setUv(u, vv).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose, nx, ny, nz);
    }

    /** Caixa alinhada aos eixos (coordenadas locais da pose). UV 0..{@code uv} em cada face. */
    static void box(VertexConsumer vc, PoseStack.Pose p, float x0, float y0, float z0, float x1, float y1, float z1, int argb,
                    int light, float uv) {
        // topo / base
        ev(vc, p, x0, y1, z0, argb, 0, 0, light, 0, 1, 0);
        ev(vc, p, x0, y1, z1, argb, 0, uv, light, 0, 1, 0);
        ev(vc, p, x1, y1, z1, argb, uv, uv, light, 0, 1, 0);
        ev(vc, p, x1, y1, z0, argb, uv, 0, light, 0, 1, 0);
        ev(vc, p, x0, y0, z0, argb, 0, 0, light, 0, -1, 0);
        ev(vc, p, x1, y0, z0, argb, uv, 0, light, 0, -1, 0);
        ev(vc, p, x1, y0, z1, argb, uv, uv, light, 0, -1, 0);
        ev(vc, p, x0, y0, z1, argb, 0, uv, light, 0, -1, 0);
        // frente / trás (z)
        ev(vc, p, x0, y0, z1, argb, 0, uv, light, 0, 0, 1);
        ev(vc, p, x1, y0, z1, argb, uv, uv, light, 0, 0, 1);
        ev(vc, p, x1, y1, z1, argb, uv, 0, light, 0, 0, 1);
        ev(vc, p, x0, y1, z1, argb, 0, 0, light, 0, 0, 1);
        ev(vc, p, x0, y0, z0, argb, 0, uv, light, 0, 0, -1);
        ev(vc, p, x0, y1, z0, argb, 0, 0, light, 0, 0, -1);
        ev(vc, p, x1, y1, z0, argb, uv, 0, light, 0, 0, -1);
        ev(vc, p, x1, y0, z0, argb, uv, uv, light, 0, 0, -1);
        // lados (x)
        ev(vc, p, x1, y0, z0, argb, 0, uv, light, 1, 0, 0);
        ev(vc, p, x1, y1, z0, argb, 0, 0, light, 1, 0, 0);
        ev(vc, p, x1, y1, z1, argb, uv, 0, light, 1, 0, 0);
        ev(vc, p, x1, y0, z1, argb, uv, uv, light, 1, 0, 0);
        ev(vc, p, x0, y0, z0, argb, 0, uv, light, -1, 0, 0);
        ev(vc, p, x0, y0, z1, argb, uv, uv, light, -1, 0, 0);
        ev(vc, p, x0, y1, z1, argb, uv, 0, light, -1, 0, 0);
        ev(vc, p, x0, y1, z0, argb, 0, 0, light, -1, 0, 0);
    }

    /** Caixa centrada em (cx, cy, cz) com meias medidas (hx, hy, hz). */
    static void cbox(VertexConsumer vc, PoseStack.Pose p, float cx, float cy, float cz, float hx, float hy, float hz, int argb, int light) {
        box(vc, p, cx - hx, cy - hy, cz - hz, cx + hx, cy + hy, cz + hz, argb, light, 1F);
    }

    /**
     * Segmento sólido (prisma de seção quadrada) de {@code a} a {@code b}, com espessura {@code wa} numa ponta e
     * {@code wb} na outra (galhos, dedos, braços). Coordenadas locais da pose.
     */
    static void segment(VertexConsumer vc, PoseStack.Pose p, Vec3 a, Vec3 b, float wa, float wb, int argb, int light) {
        Vec3 d = b.subtract(a);
        if (d.lengthSqr() < 1.0E-8) return;
        Vec3 dir = d.normalize();
        Vec3 ref = Math.abs(dir.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 s1 = dir.cross(ref).normalize();
        Vec3 s2 = dir.cross(s1).normalize();
        Vec3[] ca = new Vec3[4], cb = new Vec3[4];
        for (int k = 0; k < 4; k++) {
            double ang = Math.PI * 0.5 * k + Math.PI * 0.25;
            Vec3 off = s1.scale(Math.cos(ang)).add(s2.scale(Math.sin(ang)));
            ca[k] = a.add(off.scale(wa));
            cb[k] = b.add(off.scale(wb));
        }
        for (int k = 0; k < 4; k++) {
            int n = (k + 1) % 4;
            Vec3 nrm = ca[k].add(ca[n]).scale(0.5).subtract(a).normalize();
            quad(vc, p, ca[k], ca[n], cb[n], cb[k], argb, light, nrm);
        }
        quad(vc, p, cb[0], cb[1], cb[2], cb[3], argb, light, dir);
        quad(vc, p, ca[3], ca[2], ca[1], ca[0], argb, light, dir.scale(-1));
    }

    static void quad(VertexConsumer vc, PoseStack.Pose p, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int argb, int light, Vec3 n) {
        float nx = (float) n.x, ny = (float) n.y, nz = (float) n.z;
        ev(vc, p, (float) a.x, (float) a.y, (float) a.z, argb, 0, 0, light, nx, ny, nz);
        ev(vc, p, (float) b.x, (float) b.y, (float) b.z, argb, 1, 0, light, nx, ny, nz);
        ev(vc, p, (float) c.x, (float) c.y, (float) c.z, argb, 1, 1, light, nx, ny, nz);
        ev(vc, p, (float) d.x, (float) d.y, (float) d.z, argb, 0, 1, light, nx, ny, nz);
    }

    /** Elipsoide sólido (corpo do cervo, cabeça do Kodama). */
    static void ellipsoid(VertexConsumer vc, PoseStack.Pose p, float rx, float ry, float rz, int lat, int lon, int argb, int light) {
        for (int i = 0; i < lat; i++) {
            float th0 = (float) Math.PI * i / lat, th1 = (float) Math.PI * (i + 1) / lat;
            for (int j = 0; j < lon; j++) {
                float ph0 = (float) (Math.PI * 2 * j / lon), ph1 = (float) (Math.PI * 2 * (j + 1) / lon);
                ellV(vc, p, rx, ry, rz, th0, ph0, argb, light);
                ellV(vc, p, rx, ry, rz, th1, ph0, argb, light);
                ellV(vc, p, rx, ry, rz, th1, ph1, argb, light);
                ellV(vc, p, rx, ry, rz, th0, ph1, argb, light);
            }
        }
    }

    private static void ellV(VertexConsumer vc, PoseStack.Pose p, float rx, float ry, float rz, float th, float ph, int argb, int light) {
        float nx = Mth.sin(th) * Mth.cos(ph), ny = Mth.cos(th), nz = Mth.sin(th) * Mth.sin(ph);
        ev(vc, p, nx * rx, ny * ry, nz * rz, argb, ph / (float) (Math.PI * 2), th / (float) Math.PI, light, nx, ny, nz);
    }

    // =====================================================================================
    //  Caixa de brilho (POSITION_COLOR)
    // =====================================================================================

    static void glowBox(VertexConsumer vc, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, int argb) {
        v(vc, m, x0, y1, z0, argb); v(vc, m, x0, y1, z1, argb); v(vc, m, x1, y1, z1, argb); v(vc, m, x1, y1, z0, argb);
        v(vc, m, x0, y0, z0, argb); v(vc, m, x1, y0, z0, argb); v(vc, m, x1, y0, z1, argb); v(vc, m, x0, y0, z1, argb);
        v(vc, m, x0, y0, z1, argb); v(vc, m, x1, y0, z1, argb); v(vc, m, x1, y1, z1, argb); v(vc, m, x0, y1, z1, argb);
        v(vc, m, x0, y0, z0, argb); v(vc, m, x0, y1, z0, argb); v(vc, m, x1, y1, z0, argb); v(vc, m, x1, y0, z0, argb);
        v(vc, m, x1, y0, z0, argb); v(vc, m, x1, y1, z0, argb); v(vc, m, x1, y1, z1, argb); v(vc, m, x1, y0, z1, argb);
        v(vc, m, x0, y0, z0, argb); v(vc, m, x0, y0, z1, argb); v(vc, m, x0, y1, z1, argb); v(vc, m, x0, y1, z0, argb);
    }

    private LfDraw() {}
}
