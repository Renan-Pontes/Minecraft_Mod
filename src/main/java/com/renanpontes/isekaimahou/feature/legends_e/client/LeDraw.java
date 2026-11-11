package com.renanpontes.isekaimahou.feature.legends_e.client;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Geometria desenhada pelas Magias de Nível Supremo (só cliente): discos e anéis deitados, quads texturizados (no
 * chão e vistos de baixo), faixas de runas em volta de um cilindro, cilindros de luz com borda mais forte (pilar),
 * fitas, esferas e "sóis" virados para a câmera.
 */
final class LeDraw {

    static int argb(int rgb, float a) {
        return ((int) (Mth.clamp(a, 0F, 1F) * 255) << 24) | (rgb & 0xFFFFFF);
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

    /** Suavização 0..1 (smoothstep) com entrada livre. */
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
    //  Discos e anéis
    // =====================================================================================

    /** Disco radial deitado (plano XZ): cor {@code cIn} no raio {@code rIn}, {@code cOut} no raio {@code rOut}. */
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

    /** Anel deitado com o miolo e a borda suaves. */
    static void ringFlat(VertexConsumer vc, Matrix4f m, float r, float width, int rgb, float alpha, int seg) {
        radialFlat(vc, m, Math.max(0F, r - width), r, argb(rgb, 0F), argb(rgb, alpha), seg);
        radialFlat(vc, m, r, r + width, argb(rgb, alpha), argb(rgb, 0F), seg);
    }

    /** Disco radial no plano XY local (use depois de virar para a câmera). */
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

    /** "Sol" virado para a câmera em {@code c} (coordenadas da matriz), usando os vetores da câmera. */
    static void billboard(VertexConsumer vc, Matrix4f m, Vec3 c, Vector3f left, Vector3f up, float r, int cIn, int cOut) {
        int seg = 14;
        for (int k = 0; k < seg; k++) {
            double a0 = Math.PI * 2 * k / seg, a1 = Math.PI * 2 * (k + 1) / seg;
            float x0 = (float) Math.cos(a0) * r, y0 = (float) Math.sin(a0) * r, x1 = (float) Math.cos(a1) * r, y1 = (float) Math.sin(a1) * r;
            bv(vc, m, c, left, up, 0, 0, cIn);
            bv(vc, m, c, left, up, x0, y0, cOut);
            bv(vc, m, c, left, up, x1, y1, cOut);
            bv(vc, m, c, left, up, 0, 0, cIn);
        }
    }

    private static void bv(VertexConsumer vc, Matrix4f m, Vec3 c, Vector3f left, Vector3f up, float x, float y, int argb) {
        vc.addVertex(m, (float) (c.x + left.x() * x + up.x() * y), (float) (c.y + left.y() * x + up.y() * y),
                (float) (c.z + left.z() * x + up.z() * y)).setColor(argb);
    }

    // =====================================================================================
    //  Quads texturizados
    // =====================================================================================

    /** Quad texturizado deitado (plano XZ), meia largura {@code half}. */
    static void texFlat(VertexConsumer vc, Matrix4f m, float half, int argb) {
        vc.addVertex(m, -half, 0, -half).setUv(0, 0).setColor(argb);
        vc.addVertex(m, -half, 0, half).setUv(0, 1).setColor(argb);
        vc.addVertex(m, half, 0, half).setUv(1, 1).setColor(argb);
        vc.addVertex(m, half, 0, -half).setUv(1, 0).setColor(argb);
    }

    /**
     * Quad deitado para ser visto DE BAIXO sem espelhar: o topo da imagem fica em +Z e a direita em +X (olhando para
     * cima, o sentido horário da imagem continua horário).
     */
    static void texFlatBelow(VertexConsumer vc, Matrix4f m, float half, int argb) {
        vc.addVertex(m, -half, 0, -half).setUv(0, 1).setColor(argb);
        vc.addVertex(m, -half, 0, half).setUv(0, 0).setColor(argb);
        vc.addVertex(m, half, 0, half).setUv(1, 0).setColor(argb);
        vc.addVertex(m, half, 0, -half).setUv(1, 1).setColor(argb);
    }

    /** Quad texturizado em pé (plano XY), meia largura {@code half}. */
    static void texUpright(VertexConsumer vc, Matrix4f m, float half, int argb) {
        vc.addVertex(m, -half, -half, 0).setUv(0, 1).setColor(argb);
        vc.addVertex(m, half, -half, 0).setUv(1, 1).setColor(argb);
        vc.addVertex(m, half, half, 0).setUv(1, 0).setColor(argb);
        vc.addVertex(m, -half, half, 0).setUv(0, 0).setColor(argb);
    }

    /** Faixa de runas em volta de um cilindro de raio {@code r}, de {@code y0} a {@code y1}; a textura repete {@code uRepeat} vezes. */
    static void texBand(VertexConsumer vc, Matrix4f m, float r, float y0, float y1, int seg, float uRepeat, float uOffset, int argb) {
        for (int k = 0; k < seg; k++) {
            float a0 = (float) (Math.PI * 2 * k / seg), a1 = (float) (Math.PI * 2 * (k + 1) / seg);
            float u0 = uOffset + uRepeat * k / seg, u1 = uOffset + uRepeat * (k + 1) / seg;
            float x0 = Mth.cos(a0) * r, z0 = Mth.sin(a0) * r, x1 = Mth.cos(a1) * r, z1 = Mth.sin(a1) * r;
            vc.addVertex(m, x0, y0, z0).setUv(u0, 1).setColor(argb);
            vc.addVertex(m, x1, y0, z1).setUv(u1, 1).setColor(argb);
            vc.addVertex(m, x1, y1, z1).setUv(u1, 0).setColor(argb);
            vc.addVertex(m, x0, y1, z0).setUv(u0, 0).setColor(argb);
        }
    }

    // =====================================================================================
    //  Cilindros, fitas, esferas
    // =====================================================================================

    /**
     * Cilindro de luz de {@code y0} a {@code y1}: alfa {@code aCenter} onde a parede encara a câmera e {@code aRim} na
     * borda (efeito "fresnel": o pilar parece volumoso). {@code cam} = câmera no espaço local (sem rotação).
     */
    static void cylinder(VertexConsumer vc, Matrix4f m, float r, float y0, float y1, int seg, int rgb, float aCenter, float aRim,
                         Vec3 cam, float fadeTop) {
        for (int k = 0; k < seg; k++) {
            float a0 = (float) (Math.PI * 2 * k / seg), a1 = (float) (Math.PI * 2 * (k + 1) / seg);
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            int b0 = argb(rgb, wallAlpha(c0, s0, r, y0, cam, aCenter, aRim));
            int b1 = argb(rgb, wallAlpha(c1, s1, r, y0, cam, aCenter, aRim));
            int t0 = argb(rgb, wallAlpha(c0, s0, r, y1, cam, aCenter, aRim) * fadeTop);
            int t1 = argb(rgb, wallAlpha(c1, s1, r, y1, cam, aCenter, aRim) * fadeTop);
            v(vc, m, c0 * r, y0, s0 * r, b0);
            v(vc, m, c1 * r, y0, s1 * r, b1);
            v(vc, m, c1 * r, y1, s1 * r, t1);
            v(vc, m, c0 * r, y1, s0 * r, t0);
        }
    }

    private static float wallAlpha(float nx, float nz, float r, float y, Vec3 cam, float aCenter, float aRim) {
        double vx = cam.x - nx * r, vz = cam.z - nz * r;
        double len = Math.sqrt(vx * vx + vz * vz);
        float facing = len < 1.0E-4 ? 1F : (float) Math.abs((nx * vx + nz * vz) / len);
        float rim = (float) Math.pow(1F - facing, 1.5);
        return Mth.lerp(rim, aCenter, aRim);
    }

    /** Fita reta de {@code a} a {@code b} virada para {@code side}, com largura e cor em cada ponta. */
    static void ribbon(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 side, float wa, float wb, int ca, int cb) {
        Vec3 sa = side.scale(wa), sb = side.scale(wb);
        v(vc, m, (float) (a.x - sa.x), (float) (a.y - sa.y), (float) (a.z - sa.z), ca);
        v(vc, m, (float) (a.x + sa.x), (float) (a.y + sa.y), (float) (a.z + sa.z), ca);
        v(vc, m, (float) (b.x + sb.x), (float) (b.y + sb.y), (float) (b.z + sb.z), cb);
        v(vc, m, (float) (b.x - sb.x), (float) (b.y - sb.y), (float) (b.z - sb.z), cb);
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

    /** Lado de uma fita de {@code a} a {@code b} para ela ficar de frente para a câmera em {@code cam}. */
    static Vec3 facingSide(Vec3 a, Vec3 b, Vec3 cam) {
        Vec3 d = b.subtract(a);
        Vec3 side = d.cross(cam.subtract(a));
        return side.lengthSqr() < 1.0E-8 ? new Vec3(1, 0, 0) : side.normalize();
    }

    /**
     * Esfera "de rocha": cada vértice ganha um calombo e uma cor da paleta por um hash fixo de {@code seed} (a mesma
     * rocha em todos os quadros; a costura fecha porque o hash usa a coluna módulo {@code lon}).
     */
    static void rock(VertexConsumer vc, Matrix4f m, float r, int lat, int lon, long seed, int[] palette) {
        for (int i = 0; i < lat; i++) {
            for (int j = 0; j < lon; j++) {
                rv(vc, m, r, i, j, lat, lon, seed, palette);
                rv(vc, m, r, i, j + 1, lat, lon, seed, palette);
                rv(vc, m, r, i + 1, j + 1, lat, lon, seed, palette);
                rv(vc, m, r, i + 1, j, lat, lon, seed, palette);
            }
        }
    }

    private static void rv(VertexConsumer vc, Matrix4f m, float r, int i, int j, int lat, int lon, long seed, int[] pal) {
        int col = (i == 0 || i == lat) ? 0 : j % lon;
        float th = (float) Math.PI * i / lat, ph = (float) Math.PI * 2 * col / lon;
        float nx = Mth.sin(th) * Mth.cos(ph), ny = Mth.cos(th), nz = Mth.sin(th) * Mth.sin(ph);
        long h = seed * 0x9E3779B97F4A7C15L + i * 73856093L + col * 19349663L;
        h ^= (h >>> 29);
        h *= 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 32);
        float bump = 1F + 0.16F * (((h & 255) / 255F) - 0.5F);
        v(vc, m, nx * r * bump, ny * r * bump, nz * r * bump, pal[(int) ((h >>> 8) & 0x7FFFFFFF) % pal.length]);
    }

    /** Triângulo deitado (estilhaço) centrado na origem da matriz. */
    static void shard(VertexConsumer vc, Matrix4f m, float size, int argb) {
        v(vc, m, 0, 0, -size, argb);
        v(vc, m, size * 0.7F, 0, size * 0.5F, argb);
        v(vc, m, -size * 0.6F, 0, size * 0.4F, argb);
        v(vc, m, 0, 0, -size, argb);
    }

    private LeDraw() {}
}
