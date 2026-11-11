package com.renanpontes.isekaimahou.feature.legends_c.client;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Geometria desenhada pelas Técnicas Malditas (só cliente): esferas com borda "fresnel", esferas texturizadas que
 * ondulam (distorção), discos/halos, anéis, fitas de corte com bordas suaves e raios zigue-zague.
 */
final class LcDraw {

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

    /** Cinza para a camada que ESCURECE (multiplica): 0 = preto total, 1 = não muda. */
    static int shade(float keep) {
        int v = (int) (Mth.clamp(keep, 0F, 1F) * 255);
        return 0xFF000000 | (v << 16) | (v << 8) | v;
    }

    private static void v(VertexConsumer vc, Matrix4f m, float x, float y, float z, int argb) {
        vc.addVertex(m, x, y, z).setColor(argb);
    }

    /** Rotação que leva o eixo +Y até a direção dada. */
    static Quaternionf yTo(Vec3 dir) {
        Vec3 d = dir.lengthSqr() < 1.0E-8 ? new Vec3(0, 1, 0) : dir.normalize();
        return new Quaternionf().rotationTo(0F, 1F, 0F, (float) d.x, (float) d.y, (float) d.z);
    }

    // =====================================================================================
    //  Esferas
    // =====================================================================================

    /** Esfera de brilho com alfa diferente no centro da vista e na borda ({@code cam} = câmera no espaço local). */
    static void glowSphere(VertexConsumer vc, Matrix4f m, float r, int lat, int lon, int rgb, float aCenter, float aRim, Vec3 cam) {
        for (int i = 0; i < lat; i++) {
            for (int j = 0; j < lon; j++) {
                sv(vc, m, r, i, j, lat, lon, rgb, aCenter, aRim, cam);
                sv(vc, m, r, i, j + 1, lat, lon, rgb, aCenter, aRim, cam);
                sv(vc, m, r, i + 1, j + 1, lat, lon, rgb, aCenter, aRim, cam);
                sv(vc, m, r, i + 1, j, lat, lon, rgb, aCenter, aRim, cam);
            }
        }
    }

    private static void sv(VertexConsumer vc, Matrix4f m, float r, int i, int j, int lat, int lon, int rgb,
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

    /** Esfera de cor sólida por vértice (para a camada de escurecer). */
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

    /** Esfera texturizada (energySwirl) com ondas que andam no tempo: a "distorção". */
    static void texSphere(VertexConsumer vc, PoseStack.Pose pose, float r, int lat, int lon, int argb, float uRepeat,
                          float wobble, float time) {
        for (int i = 0; i < lat; i++) {
            for (int j = 0; j < lon; j++) {
                tv(vc, pose, r, i, j, lat, lon, argb, uRepeat, wobble, time);
                tv(vc, pose, r, i, j + 1, lat, lon, argb, uRepeat, wobble, time);
                tv(vc, pose, r, i + 1, j + 1, lat, lon, argb, uRepeat, wobble, time);
                tv(vc, pose, r, i + 1, j, lat, lon, argb, uRepeat, wobble, time);
            }
        }
    }

    private static void tv(VertexConsumer vc, PoseStack.Pose pose, float r, int i, int j, int lat, int lon, int argb,
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

    /**
     * Esfera só com posição, com as faces viradas para DENTRO (tipos de render do End: o shader pinta o céu
     * estrelado). O tipo {@code endPortal} tem culling ligado: com a ordem de vértices "de fora" (anti-horária vista
     * de fora) quem está DENTRO veria só as costas das faces, que são descartadas — o cosmos sumiria. Aqui a ordem é
     * invertida (anti-horária vista de dentro).
     */
    static void posSphere(VertexConsumer vc, Matrix4f m, float r, int lat, int lon) {
        for (int i = 0; i < lat; i++) {
            float t0 = (float) Math.PI * i / lat, t1 = (float) Math.PI * (i + 1) / lat;
            float y0 = Mth.cos(t0) * r, y1 = Mth.cos(t1) * r, s0 = Mth.sin(t0) * r, s1 = Mth.sin(t1) * r;
            for (int j = 0; j < lon; j++) {
                float p0 = (float) Math.PI * 2 * j / lon, p1 = (float) Math.PI * 2 * (j + 1) / lon;
                vc.addVertex(m, Mth.cos(p0) * s0, y0, Mth.sin(p0) * s0);
                vc.addVertex(m, Mth.cos(p0) * s1, y1, Mth.sin(p0) * s1);
                vc.addVertex(m, Mth.cos(p1) * s1, y1, Mth.sin(p1) * s1);
                vc.addVertex(m, Mth.cos(p1) * s0, y0, Mth.sin(p1) * s0);
            }
        }
    }

    // =====================================================================================
    //  Discos, anéis, fitas
    // =====================================================================================

    /** Disco radial no plano XY local: {@code cIn} no raio {@code rIn}, {@code cOut} no raio {@code rOut}. */
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

    /** Halo: transparente em {@code r0}, cor em {@code r1}, transparente em {@code r2} (plano XY). */
    static void halo(VertexConsumer vc, Matrix4f m, float r0, float r1, float r2, int rgb, float alpha, int seg) {
        radial(vc, m, r0, r1, argb(rgb, 0F), argb(rgb, alpha), seg);
        radial(vc, m, r1, r2, argb(rgb, alpha), argb(rgb, 0F), seg);
    }

    /** Anel deitado (plano XZ) com miolo e borda suaves. */
    static void ringFlat(VertexConsumer vc, Matrix4f m, float r, float width, int rgb, float alpha, int seg) {
        radialFlat(vc, m, Math.max(0F, r - width), r, argb(rgb, 0F), argb(rgb, alpha), seg);
        radialFlat(vc, m, r, r + width, argb(rgb, alpha), argb(rgb, 0F), seg);
    }

    /** Anel no plano XY (de frente). */
    static void ring(VertexConsumer vc, Matrix4f m, float r, float width, int rgb, float alpha, int seg) {
        radial(vc, m, Math.max(0F, r - width), r, argb(rgb, 0F), argb(rgb, alpha), seg);
        radial(vc, m, r, r + width, argb(rgb, alpha), argb(rgb, 0F), seg);
    }

    /** Fita reta de {@code a} a {@code b}, larguras {@code wa}/{@code wb} na direção {@code side}. */
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
        int ca = argb(rgb, alphaA), cb = argb(rgb, alphaB), zero = argb(rgb, 0F);
        for (int s = -1; s <= 1; s += 2) {
            v(vc, m, (float) a.x, (float) a.y, (float) a.z, ca);
            v(vc, m, (float) (a.x + sa.x * s), (float) (a.y + sa.y * s), (float) (a.z + sa.z * s), zero);
            v(vc, m, (float) (b.x + sb.x * s), (float) (b.y + sb.y * s), (float) (b.z + sb.z * s), zero);
            v(vc, m, (float) b.x, (float) b.y, (float) b.z, cb);
        }
    }

    /** Lado de uma fita de {@code a} a {@code b} virada para a câmera (coordenadas relativas à câmera). */
    static Vec3 faceSide(Vec3 a, Vec3 b) {
        Vec3 d = b.subtract(a);
        Vec3 side = d.cross(a.add(b).scale(-0.5));
        return side.lengthSqr() < 1.0E-8 ? new Vec3(0, 1, 0) : side.normalize();
    }

    /** Raio zigue-zague entre dois pontos (fita virada para a câmera). */
    static void arc(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 cam, long seed, float jitter, float width, int rgb, float alpha) {
        int steps = 7;
        RandomSource rnd = RandomSource.create(seed);
        Vec3 prev = a;
        Vec3 d = b.subtract(a);
        for (int i = 1; i <= steps; i++) {
            Vec3 p = a.add(d.scale(i / (float) steps));
            if (i < steps) p = p.add((rnd.nextFloat() - 0.5) * jitter, (rnd.nextFloat() - 0.5) * jitter, (rnd.nextFloat() - 0.5) * jitter);
            Vec3 seg = p.subtract(prev);
            Vec3 side = seg.cross(cam.subtract(prev));
            side = side.lengthSqr() < 1.0E-8 ? new Vec3(0, 1, 0) : side.normalize();
            ribbon(vc, m, prev, p, side, width, width, argb(rgb, alpha), argb(rgb, alpha));
            ribbon(vc, m, prev, p, side, width * 0.35F, width * 0.35F, argb(0xFFFFFF, alpha), argb(0xFFFFFF, alpha));
            prev = p;
        }
    }

    static Vec3 randomOnSphere(RandomSource rnd) {
        double z = rnd.nextDouble() * 2 - 1, a = rnd.nextDouble() * Math.PI * 2, s = Math.sqrt(1 - z * z);
        return new Vec3(Math.cos(a) * s, z, Math.sin(a) * s);
    }

    private LcDraw() {}
}
