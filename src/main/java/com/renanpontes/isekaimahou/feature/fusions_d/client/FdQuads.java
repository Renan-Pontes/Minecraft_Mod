package com.renanpontes.isekaimahou.feature.fusions_d.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Desenho procedural das Misturas D (só cliente): quads de brilho aditivo ({@link RenderType#lightning()}),
 * "céu do vazio" ({@link RenderType#endGateway()}), translúcidos escuros com textura branca/chamas
 * ({@link #DARK}) e fitas viradas para a câmera (feixes). Tudo desenhado dos dois lados.
 */
public final class FdQuads {
    public static final ResourceLocation WHITE = IsekaiMahou.id("textures/entity/fd_white.png");
    public static final ResourceLocation BLACK_FLAME = IsekaiMahou.id("textures/entity/fd_black_flame.png");

    /** Translúcido (não aditivo) e sempre aceso: dá para desenhar preto e cores sólidas semitransparentes. */
    public static RenderType dark() { return RenderType.entityTranslucentEmissive(WHITE); }

    public static RenderType flame() { return RenderType.entityTranslucentEmissive(BLACK_FLAME); }

    public static RenderType glow() { return RenderType.lightning(); }

    public static RenderType space() { return RenderType.endGateway(); }

    public static int argb(int rgb, float a) {
        return ((int) (Mth.clamp(a, 0F, 1F) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    // ---------------------------------------------------------------- vértices
    private static void cv(VertexConsumer vc, Matrix4f m, float x, float y, float z, int c) {
        vc.addVertex(m, x, y, z).setColor((c >> 16) & 255, (c >> 8) & 255, c & 255, (c >>> 24) & 255);
    }

    private static void tv(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, int c, float u, float v) {
        vc.addVertex(p.pose(), x, y, z).setColor((c >> 16) & 255, (c >> 8) & 255, c & 255, (c >>> 24) & 255).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(p, 0, 1, 0);
    }

    /** Quad colorido (brilho aditivo), cores por vértice, dos dois lados. */
    public static void color(VertexConsumer vc, Matrix4f m, Vec3 a, int ca, Vec3 b, int cb, Vec3 c, int cc, Vec3 d, int cd) {
        cv(vc, m, (float) a.x, (float) a.y, (float) a.z, ca);
        cv(vc, m, (float) b.x, (float) b.y, (float) b.z, cb);
        cv(vc, m, (float) c.x, (float) c.y, (float) c.z, cc);
        cv(vc, m, (float) d.x, (float) d.y, (float) d.z, cd);
        cv(vc, m, (float) d.x, (float) d.y, (float) d.z, cd);
        cv(vc, m, (float) c.x, (float) c.y, (float) c.z, cc);
        cv(vc, m, (float) b.x, (float) b.y, (float) b.z, cb);
        cv(vc, m, (float) a.x, (float) a.y, (float) a.z, ca);
    }

    /** Quad só com posição (céu do vazio), dos dois lados. */
    public static void space(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 c, Vec3 d) {
        Vec3[] q = {a, b, c, d, d, c, b, a};
        for (Vec3 v : q) vc.addVertex(m, (float) v.x, (float) v.y, (float) v.z);
    }

    /** Quad texturizado translúcido aceso, dos dois lados. */
    public static void tex(VertexConsumer vc, PoseStack.Pose p, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int col, float u0, float v0, float u1, float v1) {
        tex4(vc, p, a, col, u0, v1, b, col, u1, v1, c, col, u1, v0, d, col, u0, v0);
    }

    public static void tex4(VertexConsumer vc, PoseStack.Pose p, Vec3 a, int ca, float ua, float va, Vec3 b, int cb, float ub, float vb,
                            Vec3 c, int cc, float uc, float vcc, Vec3 d, int cd, float ud, float vd) {
        tv(vc, p, (float) a.x, (float) a.y, (float) a.z, ca, ua, va);
        tv(vc, p, (float) b.x, (float) b.y, (float) b.z, cb, ub, vb);
        tv(vc, p, (float) c.x, (float) c.y, (float) c.z, cc, uc, vcc);
        tv(vc, p, (float) d.x, (float) d.y, (float) d.z, cd, ud, vd);
        tv(vc, p, (float) d.x, (float) d.y, (float) d.z, cd, ud, vd);
        tv(vc, p, (float) c.x, (float) c.y, (float) c.z, cc, uc, vcc);
        tv(vc, p, (float) b.x, (float) b.y, (float) b.z, cb, ub, vb);
        tv(vc, p, (float) a.x, (float) a.y, (float) a.z, ca, ua, va);
    }

    // ---------------------------------------------------------------- formas
    /** Posição da câmera relativa à entidade desenhada (o PoseStack está na posição interpolada dela). */
    public static Vec3 cameraLocal(Entity e, float pt) {
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        return cam.subtract(e.getPosition(pt));
    }

    /** Lado de uma fita de a→b virada para a câmera. */
    public static Vec3 ribbonSide(Vec3 a, Vec3 b, Vec3 cam, double halfWidth) {
        Vec3 side = b.subtract(a).cross(cam.subtract(a));
        if (side.lengthSqr() < 1.0E-8) side = new Vec3(1, 0, 0);
        return side.normalize().scale(halfWidth);
    }

    /** Fita (feixe) de a até b virada para a câmera, com cores nas pontas. */
    public static void beam(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 cam, double halfWidth, int ca, int cb) {
        Vec3 s = ribbonSide(a, b, cam, halfWidth);
        color(vc, m, a.subtract(s), ca, a.add(s), ca, b.add(s), cb, b.subtract(s), cb);
    }

    /** Feixe com núcleo claro e bordas que somem (dois quads por lado). */
    public static void softBeam(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 cam, double halfWidth, int core, int edge) {
        Vec3 s = ribbonSide(a, b, cam, halfWidth);
        color(vc, m, a, core, a.add(s), edge, b.add(s), edge, b, core);
        color(vc, m, a, core, a.subtract(s), edge, b.subtract(s), edge, b, core);
    }

    /** Disco/anel horizontal (y fixo) de r0 a r1 com cores interna/externa. */
    public static void ring(VertexConsumer vc, Matrix4f m, double y, double r0, double r1, int c0, int c1, int seg, double rot) {
        for (int i = 0; i < seg; i++) {
            double a0 = rot + Math.PI * 2 * i / seg, a1 = rot + Math.PI * 2 * (i + 1) / seg;
            color(vc, m, new Vec3(Math.cos(a0) * r0, y, Math.sin(a0) * r0), c0, new Vec3(Math.cos(a0) * r1, y, Math.sin(a0) * r1), c1,
                    new Vec3(Math.cos(a1) * r1, y, Math.sin(a1) * r1), c1, new Vec3(Math.cos(a1) * r0, y, Math.sin(a1) * r0), c0);
        }
    }

    /** Cilindro vertical (só laterais) de y0 a y1, cores embaixo/em cima. */
    public static void cylinder(VertexConsumer vc, Matrix4f m, double r0, double r1, double y0, double y1, int c0, int c1, int seg, double rot) {
        for (int i = 0; i < seg; i++) {
            double a0 = rot + Math.PI * 2 * i / seg, a1 = rot + Math.PI * 2 * (i + 1) / seg;
            color(vc, m, new Vec3(Math.cos(a0) * r0, y0, Math.sin(a0) * r0), c0, new Vec3(Math.cos(a1) * r0, y0, Math.sin(a1) * r0), c0,
                    new Vec3(Math.cos(a1) * r1, y1, Math.sin(a1) * r1), c1, new Vec3(Math.cos(a0) * r1, y1, Math.sin(a0) * r1), c1);
        }
    }

    /** Cilindro texturizado (chamas) com a textura rolando para cima. */
    public static void texCylinder(VertexConsumer vc, PoseStack.Pose p, double r0, double r1, double y0, double y1, int c0, int c1, int seg,
                                   double rot, float uRepeat, float vRepeat, float scroll) {
        for (int i = 0; i < seg; i++) {
            double a0 = rot + Math.PI * 2 * i / seg, a1 = rot + Math.PI * 2 * (i + 1) / seg;
            float u0 = uRepeat * i / seg, u1 = uRepeat * (i + 1) / seg;
            tex4(vc, p, new Vec3(Math.cos(a0) * r0, y0, Math.sin(a0) * r0), c0, u0, vRepeat + scroll,
                    new Vec3(Math.cos(a1) * r0, y0, Math.sin(a1) * r0), c0, u1, vRepeat + scroll,
                    new Vec3(Math.cos(a1) * r1, y1, Math.sin(a1) * r1), c1, u1, scroll,
                    new Vec3(Math.cos(a0) * r1, y1, Math.sin(a0) * r1), c1, u0, scroll);
        }
    }

    /** Esfera em faixas (céu do vazio). */
    public static void spaceSphere(VertexConsumer vc, Matrix4f m, double r, int lat, int lon) {
        for (int i = 0; i < lat; i++) {
            double t0 = Math.PI * i / lat, t1 = Math.PI * (i + 1) / lat;
            for (int j = 0; j < lon; j++) {
                double p0 = Math.PI * 2 * j / lon, p1 = Math.PI * 2 * (j + 1) / lon;
                space(vc, m, sph(r, t0, p0), sph(r, t0, p1), sph(r, t1, p1), sph(r, t1, p0));
            }
        }
    }

    /** Esfera colorida (casca de brilho ou escura, conforme o RenderType). */
    public static void colorSphere(VertexConsumer vc, Matrix4f m, double r, int c, int lat, int lon) {
        for (int i = 0; i < lat; i++) {
            double t0 = Math.PI * i / lat, t1 = Math.PI * (i + 1) / lat;
            for (int j = 0; j < lon; j++) {
                double p0 = Math.PI * 2 * j / lon, p1 = Math.PI * 2 * (j + 1) / lon;
                color(vc, m, sph(r, t0, p0), c, sph(r, t0, p1), c, sph(r, t1, p1), c, sph(r, t1, p0), c);
            }
        }
    }

    /** Esfera translúcida escura/sólida (RenderType {@link #dark()}). */
    public static void darkSphere(VertexConsumer vc, PoseStack.Pose p, double r, int c, int lat, int lon) {
        for (int i = 0; i < lat; i++) {
            double t0 = Math.PI * i / lat, t1 = Math.PI * (i + 1) / lat;
            for (int j = 0; j < lon; j++) {
                double p0 = Math.PI * 2 * j / lon, p1 = Math.PI * 2 * (j + 1) / lon;
                tex(vc, p, sph(r, t0, p0), sph(r, t0, p1), sph(r, t1, p1), sph(r, t1, p0), c, 0, 0, 1, 1);
            }
        }
    }

    private static Vec3 sph(double r, double theta, double phi) {
        double s = Math.sin(theta);
        return new Vec3(Math.cos(phi) * s * r, Math.cos(theta) * r, Math.sin(phi) * s * r);
    }

    /** Halo plano virado para a câmera em volta do centro (raios r0→r1). */
    public static void halo(VertexConsumer vc, Matrix4f m, Vec3 center, Vec3 cam, double r0, double r1, int c0, int c1, int seg) {
        Vec3 n = cam.subtract(center).normalize();
        Vec3 u = n.cross(new Vec3(0, 1, 0));
        if (u.lengthSqr() < 1.0E-6) u = new Vec3(1, 0, 0);
        u = u.normalize();
        Vec3 v = u.cross(n).normalize();
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg;
            Vec3 d0 = u.scale(Math.cos(a0)).add(v.scale(Math.sin(a0))), d1 = u.scale(Math.cos(a1)).add(v.scale(Math.sin(a1)));
            color(vc, m, center.add(d0.scale(r0)), c0, center.add(d0.scale(r1)), c1, center.add(d1.scale(r1)), c1, center.add(d1.scale(r0)), c0);
        }
    }

    /** Estrela de 4 pontas virada para a câmera. */
    public static void star(VertexConsumer vc, Matrix4f m, Vec3 center, Vec3 cam, double size, double rot, int core, int tip) {
        Vec3 n = cam.subtract(center).normalize();
        Vec3 u = n.cross(new Vec3(0, 1, 0));
        if (u.lengthSqr() < 1.0E-6) u = new Vec3(1, 0, 0);
        u = u.normalize();
        Vec3 v = u.cross(n).normalize();
        for (int k = 0; k < 4; k++) {
            double a = rot + Math.PI / 2 * k;
            Vec3 dir = u.scale(Math.cos(a)).add(v.scale(Math.sin(a)));
            Vec3 perp = u.scale(Math.cos(a + Math.PI / 2)).add(v.scale(Math.sin(a + Math.PI / 2)));
            Vec3 tipP = center.add(dir.scale(size));
            color(vc, m, center.add(perp.scale(size * 0.18)), core, tipP, tip, tipP, tip, center.subtract(perp.scale(size * 0.18)), core);
        }
    }

    private FdQuads() {}
}
