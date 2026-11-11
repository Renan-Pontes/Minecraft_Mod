package com.renanpontes.isekaimahou.client.spellfx;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Ajudantes de desenho das partículas fx (quads livres no buffer de partículas, coordenadas relativas à câmera). */
public final class FxDraw {

    /**
     * Quad centrado em (cx, cy, cz) com semi-eixos {@code a} (direção U) e {@code b} (direção V), dos dois lados.
     * UV do sprite inteiro. Cor 0..1.
     */
    public static void quad(VertexConsumer vc, float cx, float cy, float cz, Vec3 a, Vec3 b, TextureAtlasSprite s,
                            float r, float g, float bl, float alpha, int light) {
        float ax = (float) a.x, ay = (float) a.y, az = (float) a.z;
        float bx = (float) b.x, by = (float) b.y, bz = (float) b.z;
        float u0 = s.getU0(), u1 = s.getU1(), v0 = s.getV0(), v1 = s.getV1();
        // frente
        v(vc, cx - ax - bx, cy - ay - by, cz - az - bz, u0, v1, r, g, bl, alpha, light);
        v(vc, cx + ax - bx, cy + ay - by, cz + az - bz, u1, v1, r, g, bl, alpha, light);
        v(vc, cx + ax + bx, cy + ay + by, cz + az + bz, u1, v0, r, g, bl, alpha, light);
        v(vc, cx - ax + bx, cy - ay + by, cz - az + bz, u0, v0, r, g, bl, alpha, light);
        // verso
        v(vc, cx - ax + bx, cy - ay + by, cz - az + bz, u0, v0, r, g, bl, alpha, light);
        v(vc, cx + ax + bx, cy + ay + by, cz + az + bz, u1, v0, r, g, bl, alpha, light);
        v(vc, cx + ax - bx, cy + ay - by, cz + az - bz, u1, v1, r, g, bl, alpha, light);
        v(vc, cx - ax - bx, cy - ay - by, cz - az - bz, u0, v1, r, g, bl, alpha, light);
    }

    /**
     * Fita de {@code from} a {@code to} (relativos à câmera), larga {@code width}, virada para a câmera.
     * U corre do início (u0) ao fim (u1) do sprite; V atravessa a largura.
     */
    public static void streak(VertexConsumer vc, Vec3 from, Vec3 to, float width, TextureAtlasSprite s,
                              float r, float g, float bl, float alpha, int light) {
        Vec3 d = to.subtract(from);
        Vec3 mid = from.add(d.scale(0.5));
        // a câmera está na origem: o lado é perpendicular ao segmento e à linha de visão
        Vec3 side = d.cross(mid);
        if (side.lengthSqr() < 1.0E-8) side = d.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1.0E-8) side = new Vec3(1, 0, 0);
        side = side.normalize().scale(width * 0.5);
        float u0 = s.getU0(), u1 = s.getU1(), v0 = s.getV0(), v1 = s.getV1();
        float fx = (float) from.x, fy = (float) from.y, fz = (float) from.z;
        float tx = (float) to.x, ty = (float) to.y, tz = (float) to.z;
        float sx = (float) side.x, sy = (float) side.y, sz = (float) side.z;
        v(vc, fx - sx, fy - sy, fz - sz, u0, v1, r, g, bl, alpha, light);
        v(vc, tx - sx, ty - sy, tz - sz, u1, v1, r, g, bl, alpha, light);
        v(vc, tx + sx, ty + sy, tz + sz, u1, v0, r, g, bl, alpha, light);
        v(vc, fx + sx, fy + sy, fz + sz, u0, v0, r, g, bl, alpha, light);
        v(vc, fx + sx, fy + sy, fz + sz, u0, v0, r, g, bl, alpha, light);
        v(vc, tx + sx, ty + sy, tz + sz, u1, v0, r, g, bl, alpha, light);
        v(vc, tx - sx, ty - sy, tz - sz, u1, v1, r, g, bl, alpha, light);
        v(vc, fx - sx, fy - sy, fz - sz, u0, v1, r, g, bl, alpha, light);
    }

    private static void v(VertexConsumer vc, float x, float y, float z, float u, float vv, float r, float g, float b, float a, int light) {
        vc.addVertex(x, y, z).setUv(u, vv).setColor(r, g, b, a).setLight(light);
    }

    /** Base ortonormal (u, v) do plano perpendicular a {@code n}. */
    public static Vec3[] basis(Vec3 n) {
        Vec3 nn = n.lengthSqr() < 1.0E-8 ? new Vec3(0, 1, 0) : n.normalize();
        Vec3 ref = Math.abs(nn.y) < 0.95 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 u = ref.cross(nn).normalize();
        Vec3 v = nn.cross(u).normalize();
        return new Vec3[] {u, v};
    }

    /** Gira a base (u, v) por {@code angle} radianos dentro do plano; devolve os semi-eixos já escalados por {@code half}. */
    public static Vec3[] spin(Vec3 u, Vec3 v, float angle, float half) {
        float c = Mth.cos(angle), s = Mth.sin(angle);
        Vec3 a = u.scale(c).add(v.scale(s)).scale(half);
        Vec3 b = v.scale(c).subtract(u.scale(s)).scale(half);
        return new Vec3[] {a, b};
    }

    public static float r(int rgb) { return ((rgb >> 16) & 255) / 255F; }

    public static float g(int rgb) { return ((rgb >> 8) & 255) / 255F; }

    public static float b(int rgb) { return (rgb & 255) / 255F; }

    public static int lighten(int rgb, float f) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        r = (int) (r + (255 - r) * f);
        g = (int) (g + (255 - g) * f);
        b = (int) (b + (255 - b) * f);
        return (r << 16) | (g << 8) | b;
    }

    public static int darken(int rgb, float f) {
        int r = (int) (((rgb >> 16) & 255) * f), g = (int) (((rgb >> 8) & 255) * f), b = (int) ((rgb & 255) * f);
        return (r << 16) | (g << 8) | b;
    }

    private FxDraw() {}
}
