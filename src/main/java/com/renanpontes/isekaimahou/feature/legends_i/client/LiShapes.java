package com.renanpontes.isekaimahou.feature.legends_i.client;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Geometria dos glifos de anime (só cliente, POSITION_COLOR em coordenadas relativas à câmera): braço de borracha,
 * relâmpagos, esfera do Shinra, núcleo e pedras do Chibaku, meia-lua do Susanoo, costelas do Susanoo, asas de papel,
 * mão do Chidori.
 */
final class LiShapes {

    static int argb(int rgb, float a) {
        return ((int) (Mth.clamp(a, 0F, 1F) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    static int shade(int rgb, float f) {
        int r = (int) Math.min(255, ((rgb >> 16) & 255) * f), g = (int) Math.min(255, ((rgb >> 8) & 255) * f),
                b = (int) Math.min(255, (rgb & 255) * f);
        return (r << 16) | (g << 8) | b;
    }

    static float smooth(float x) {
        float t = Mth.clamp(x, 0F, 1F);
        return t * t * (3 - 2 * t);
    }

    static void v(VertexConsumer vc, Matrix4f m, Vec3 p, int argb) {
        vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(argb);
    }

    static void quad(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int ca, int cb, int cc, int cd) {
        v(vc, m, a, ca);
        v(vc, m, b, cb);
        v(vc, m, c, cc);
        v(vc, m, d, cd);
    }

    /** Lado de uma fita de {@code a} a {@code b} virada para a câmera ({@code toCam} = câmera − ponto). */
    static Vec3 side(Vec3 a, Vec3 b, Vec3 toCam) {
        Vec3 s = b.subtract(a).cross(toCam);
        return s.lengthSqr() < 1.0E-8 ? new Vec3(1, 0, 0) : s.normalize();
    }

    /** Fita reta (largura {@code w}) com cor cheia. */
    static void ribbon(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 side, float wa, float wb, int ca, int cb) {
        Vec3 sa = side.scale(wa * 0.5), sb = side.scale(wb * 0.5);
        quad(vc, m, a.subtract(sa), a.add(sa), b.add(sb), b.subtract(sb), ca, ca, cb, cb);
    }

    /** "Sol" virado para a câmera. */
    static void billboard(VertexConsumer vc, Matrix4f m, Vec3 c, Vector3f left, Vector3f up, float r, int cIn, int cOut) {
        int seg = 12;
        Vec3 l = new Vec3(left.x(), left.y(), left.z()), u = new Vec3(up.x(), up.y(), up.z());
        for (int k = 0; k < seg; k++) {
            double a0 = Math.PI * 2 * k / seg, a1 = Math.PI * 2 * (k + 1) / seg;
            Vec3 p0 = c.add(l.scale(Math.cos(a0) * r)).add(u.scale(Math.sin(a0) * r));
            Vec3 p1 = c.add(l.scale(Math.cos(a1) * r)).add(u.scale(Math.sin(a1) * r));
            quad(vc, m, c, p0, p1, c, cIn, cOut, cOut, cIn);
        }
    }

    /** Prisma de {@code sides} lados de {@code a} até {@code b} (faces com luz falsa). */
    static void tube(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, float r, int rgb, float alpha, int sides) {
        Vec3 d = b.subtract(a);
        if (d.lengthSqr() < 1.0E-6) return;
        Vec3 n = d.normalize();
        Vec3 u = Math.abs(n.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 s1 = n.cross(u).normalize(), s2 = n.cross(s1).normalize();
        for (int k = 0; k < sides; k++) {
            double a0 = Math.PI * 2 * k / sides, a1 = Math.PI * 2 * (k + 1) / sides;
            Vec3 o0 = s1.scale(Math.cos(a0) * r).add(s2.scale(Math.sin(a0) * r));
            Vec3 o1 = s1.scale(Math.cos(a1) * r).add(s2.scale(Math.sin(a1) * r));
            float light = 0.62F + 0.38F * (float) Math.max(0, (o0.y + o1.y) / (2 * r));
            int col = argb(shade(rgb, light), alpha);
            quad(vc, m, a.add(o0), b.add(o0), b.add(o1), a.add(o1), col, col, col, col);
        }
        // tampas
        int capCol = argb(shade(rgb, 0.85F), alpha);
        for (int k = 0; k < sides; k++) {
            double a0 = Math.PI * 2 * k / sides, a1 = Math.PI * 2 * (k + 1) / sides;
            Vec3 o0 = s1.scale(Math.cos(a0) * r).add(s2.scale(Math.sin(a0) * r));
            Vec3 o1 = s1.scale(Math.cos(a1) * r).add(s2.scale(Math.sin(a1) * r));
            quad(vc, m, b, b.add(o0), b.add(o1), b, capCol, capCol, capCol, capCol);
        }
    }

    /** Cubo girado ({@code rot} em radianos em dois eixos) — pedras do Chibaku. */
    static void cube(VertexConsumer vc, Matrix4f m, Vec3 c, float h, float rx, float ry, int rgb, float alpha) {
        Vec3[] ax = {new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1)};
        for (int i = 0; i < 3; i++) ax[i] = ax[i].xRot(rx).yRot(ry);
        for (int f = 0; f < 3; f++) {
            Vec3 n = ax[f], a = ax[(f + 1) % 3], b = ax[(f + 2) % 3];
            for (int s = -1; s <= 1; s += 2) {
                Vec3 fc = c.add(n.scale(h * s));
                float light = 0.55F + 0.45F * (float) Math.max(0, n.y * s);
                int col = argb(shade(rgb, light), alpha);
                quad(vc, m, fc.add(a.scale(-h)).add(b.scale(-h)), fc.add(a.scale(h)).add(b.scale(-h)),
                        fc.add(a.scale(h)).add(b.scale(h)), fc.add(a.scale(-h)).add(b.scale(h)), col, col, col, col);
            }
        }
    }

    // =====================================================================================
    //  Gomu Gomu
    // =====================================================================================
    /** Fração do braço esticado: sai rápido, segura, volta. */
    static float armReach(float t) {
        if (t < 0.3F) return smooth(t / 0.3F);
        if (t < 0.6F) return 1F;
        return 1F - smooth((t - 0.6F) / 0.4F);
    }

    static void gomuArm(VertexConsumer vc, Matrix4f m, Vec3 c, Vec3 dir, float size, int skin, float t) {
        float f = armReach(t);
        if (f <= 0.02F) return;
        Vec3 n = dir.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : dir.normalize();
        Vec3 tip = c.add(dir.scale(f));
        double len = tip.distanceTo(c);
        // manga vermelha no ombro, braço fino de borracha (afina quanto mais estica), punho no fim
        tube(vc, m, c, c.add(n.scale(Math.min(0.4, len))), size * 0.62F, 0xC8202A, 1F, 6);
        float thin = (float) Mth.clamp(size * 0.42 / Math.max(1.0, len * 0.12), size * 0.18, size * 0.42);
        if (len > 0.5) tube(vc, m, c.add(n.scale(0.35)), tip.subtract(n.scale(size * 0.6)), thin, skin, 1F, 6);
        cube(vc, m, tip, size * 0.75F, 0F, (float) Math.atan2(n.x, n.z), skin, 1F);
    }

    // =====================================================================================
    //  Relâmpagos
    // =====================================================================================
    /** Relâmpagos saindo do centro até {@code radius} (Haki do Rei). */
    static void bolts(VertexConsumer vc, Matrix4f m, Vec3 c, float radius, int rgb, float width, float alpha, long seed, int n, Vec3 toCam) {
        if (alpha <= 0.01F) return;
        RandomSource r = RandomSource.create(seed);
        int col = argb(rgb, alpha), end = argb(rgb, 0F);
        for (int k = 0; k < n; k++) {
            double yaw = r.nextDouble() * Math.PI * 2, pitch = (r.nextDouble() - 0.35) * 0.9;
            Vec3 d = new Vec3(Math.cos(yaw) * Math.cos(pitch), Math.sin(pitch), Math.sin(yaw) * Math.cos(pitch));
            float len = radius * (0.55F + r.nextFloat() * 0.45F);
            Vec3 prev = c.add(d.scale(0.4));
            int segs = 6;
            for (int s = 1; s <= segs; s++) {
                Vec3 next = c.add(d.scale(0.4 + len * s / segs)).add((r.nextDouble() - 0.5) * 0.8, (r.nextDouble() - 0.5) * 0.8,
                        (r.nextDouble() - 0.5) * 0.8);
                float w0 = width * (1F - (s - 1F) / segs), w1 = width * (1F - (float) s / segs);
                ribbon(vc, m, prev, next, side(prev, next, toCam), w0, w1, s == 1 ? col : argb(rgb, alpha * (1F - (s - 1F) / segs)),
                        s == segs ? end : argb(rgb, alpha * (1F - (float) s / segs)));
                prev = next;
            }
        }
    }

    /** Raio quebrado de {@code c} até {@code c+dir} com núcleo branco (Chidori). */
    static void lightning(VertexConsumer vc, Matrix4f m, Vec3 c, Vec3 dir, int rgb, float width, float alpha, long seed, Vec3 toCam, int branches) {
        if (alpha <= 0.01F) return;
        RandomSource r = RandomSource.create(seed);
        double len = dir.length();
        int segs = Mth.clamp((int) (len * 1.5), 3, 16);
        Vec3 prev = c;
        for (int s = 1; s <= segs; s++) {
            Vec3 next = c.add(dir.scale((double) s / segs));
            if (s < segs) next = next.add((r.nextDouble() - 0.5) * 0.6, (r.nextDouble() - 0.5) * 0.6, (r.nextDouble() - 0.5) * 0.6);
            Vec3 sd = side(prev, next, toCam);
            ribbon(vc, m, prev, next, sd, width, width, argb(rgb, alpha * 0.8F), argb(rgb, alpha * 0.8F));
            ribbon(vc, m, prev, next, sd, width * 0.3F, width * 0.3F, argb(0xFFFFFF, alpha), argb(0xFFFFFF, alpha));
            if (branches > 0 && r.nextInt(3) == 0) {
                Vec3 b = next.add((r.nextDouble() - 0.5) * 1.6, (r.nextDouble() - 0.5) * 1.6, (r.nextDouble() - 0.5) * 1.6);
                ribbon(vc, m, next, b, side(next, b, toCam), width * 0.5F, 0F, argb(rgb, alpha * 0.7F), argb(rgb, 0F));
                branches--;
            }
            prev = next;
        }
    }

    /** Fio ondulado de força (Bansho Ten'in). */
    static void wavyLine(VertexConsumer vc, Matrix4f m, Vec3 c, Vec3 dir, int rgb, float width, float alpha, float time, Vec3 toCam) {
        if (alpha <= 0.01F) return;
        double len = dir.length();
        int segs = Mth.clamp((int) (len * 2), 4, 40);
        Vec3 sd = side(c, c.add(dir), toCam);
        Vec3 prev = c;
        for (int s = 1; s <= segs; s++) {
            double f = (double) s / segs;
            Vec3 next = c.add(dir.scale(f)).add(sd.scale(Math.sin(f * 12 + time * 0.8) * 0.15 * Math.sin(f * Math.PI)));
            ribbon(vc, m, prev, next, sd, width, width, argb(rgb, alpha * 0.7F), argb(rgb, alpha * 0.7F));
            prev = next;
        }
    }

    // =====================================================================================
    //  Pain
    // =====================================================================================
    static void ring(VertexConsumer vc, Matrix4f m, Vec3 c, Vec3 a, Vec3 b, float r, float w, int rgb, float alpha, int seg) {
        for (int k = 0; k < seg; k++) {
            double t0 = Math.PI * 2 * k / seg, t1 = Math.PI * 2 * (k + 1) / seg;
            Vec3 d0 = a.scale(Math.cos(t0)).add(b.scale(Math.sin(t0))), d1 = a.scale(Math.cos(t1)).add(b.scale(Math.sin(t1)));
            int mid = argb(rgb, alpha), zero = argb(rgb, 0F);
            quad(vc, m, c.add(d0.scale(r - w)), c.add(d0.scale(r)), c.add(d1.scale(r)), c.add(d1.scale(r - w)), zero, mid, mid, zero);
            quad(vc, m, c.add(d0.scale(r)), c.add(d0.scale(r + w)), c.add(d1.scale(r + w)), c.add(d1.scale(r)), mid, zero, zero, mid);
        }
    }

    /** Shinra Tensei: esfera de anéis que cresce e some, clarão no começo. */
    static void shinra(VertexConsumer vc, PoseStack ps, Vec3 c, float size, int rgb, float t, Vector3f left, Vector3f up) {
        Matrix4f m = ps.last().pose();
        float r = size * smooth(Math.min(1F, t * 1.6F));
        float a = (1F - t) * 0.8F;
        float w = Math.max(0.15F, r * 0.08F);
        int seg = 40;
        Vec3 X = new Vec3(1, 0, 0), Y = new Vec3(0, 1, 0), Z = new Vec3(0, 0, 1);
        ring(vc, m, c, X, Z, r, w, rgb, a, seg);
        ring(vc, m, c, X, Y, r * 0.98F, w * 0.8F, rgb, a * 0.7F, seg);
        ring(vc, m, c, Z, Y, r * 0.98F, w * 0.8F, rgb, a * 0.7F, seg);
        ring(vc, m, c, X.add(Z).normalize(), Y, r * 0.96F, w * 0.6F, 0xFFFFFF, a * 0.4F, seg);
        if (t < 0.3F) billboard(vc, m, c, left, up, 1.5F + size * 0.3F * t, argb(0xFFFFFF, (0.3F - t) * 2.5F), argb(rgb, 0F));
    }

    /** Chibaku: núcleo negro + pedras girando (parte sólida). */
    static void chibakuSolid(VertexConsumer vc, Matrix4f m, Vec3 c, float size, float t, float age, int life, Vector3f left, Vector3f up,
                             boolean low, long seed) {
        float grow = smooth(age / 20F) * (1F - smooth((age - (life - 10)) / 10F));
        if (grow <= 0.01F) return;
        float core = size * 0.6F * grow;
        billboard(vc, m, c, left, up, core, argb(0x020005, 0.98F), argb(0x1A1026, 0.92F));
        RandomSource r = RandomSource.create(seed);
        int n = low ? 8 : 18;
        float shell = size * 1.5F * grow;
        for (int k = 0; k < n; k++) {
            double baseA = r.nextDouble() * Math.PI * 2, y = r.nextDouble() * 2 - 1;
            float speed = 0.02F + r.nextFloat() * 0.03F;
            double a = baseA + age * speed;
            double rr = Math.sqrt(1 - y * y) * (shell + r.nextFloat() * 0.8F);
            Vec3 p = c.add(Math.cos(a) * rr, y * (shell * 0.9), Math.sin(a) * rr);
            float h = 0.18F + r.nextFloat() * 0.3F;
            int col = switch (k % 3) {
                case 0 -> 0x5A5048;
                case 1 -> 0x6B5B4A;
                default -> 0x47423E;
            };
            cube(vc, m, p, h * grow, age * 0.05F + k, age * 0.03F + k * 0.7F, col, 1F);
        }
    }

    /** Chibaku: borda roxa pulsando e redemoinho (parte brilhante). */
    static void chibakuGlow(VertexConsumer vc, Matrix4f m, Vec3 c, float size, float t, float age, int life, Vector3f left, Vector3f up, int rgb) {
        float grow = smooth(age / 20F) * (1F - smooth((age - (life - 10)) / 10F));
        if (grow <= 0.01F) return;
        float core = size * 0.6F * grow;
        float pulse = 0.5F + 0.2F * Mth.sin(age * 0.3F);
        // aro: anel virado para a câmera
        Vec3 l = new Vec3(left.x(), left.y(), left.z()), u = new Vec3(up.x(), up.y(), up.z());
        ring(vc, m, c, l, u, core * 1.08F, core * 0.25F, 0x9A7CFF, pulse * grow, 32);
        Vec3 a = new Vec3(Math.cos(age * 0.05), 0, Math.sin(age * 0.05)), b = new Vec3(-a.z, 0, a.x);
        ring(vc, m, c, a, b, size * 1.25F * grow, 0.1F, 0xB9A6E8, 0.35F * grow, 40);
    }

    // =====================================================================================
    //  Susanoo
    // =====================================================================================
    /** Meia-lua espectral (corte do Susanoo / Totsuka). */
    static void crescent(VertexConsumer vc, Matrix4f m, Vec3 c, Vec3 dir, float size, int rgb, float alpha) {
        if (alpha <= 0.01F) return;
        Vec3 n = dir.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : dir.normalize();
        Vec3 sd = n.cross(new Vec3(0, 1, 0));
        sd = sd.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : sd.normalize();
        int seg = 24;
        double span = Math.toRadians(75);
        for (int k = 0; k < seg; k++) {
            double t0 = -span + 2 * span * k / seg, t1 = -span + 2 * span * (k + 1) / seg;
            float taper0 = (float) Math.cos(t0 / span * Math.PI / 2), taper1 = (float) Math.cos(t1 / span * Math.PI / 2);
            Vec3 d0 = n.scale(Math.cos(t0) * 0.55).add(sd.scale(Math.sin(t0))), d1 = n.scale(Math.cos(t1) * 0.55).add(sd.scale(Math.sin(t1)));
            float in0 = size * (1F - 0.18F * taper0), in1 = size * (1F - 0.18F * taper1);
            quad(vc, m, c.add(d0.scale(in0)), c.add(d0.scale(size)), c.add(d1.scale(size)), c.add(d1.scale(in1)),
                    argb(rgb, 0F), argb(0xFFFFFF, alpha * taper0), argb(0xFFFFFF, alpha * taper1), argb(rgb, 0F));
            quad(vc, m, c.add(d0.scale(size)), c.add(d0.scale(size + 0.25F * taper0)), c.add(d1.scale(size + 0.25F * taper1)),
                    c.add(d1.scale(size)), argb(rgb, alpha * taper0), argb(rgb, 0F), argb(rgb, 0F), argb(rgb, alpha * taper1));
        }
    }

    /** Eixos do corpo (frente, direita) pela rotação do corpo. */
    static Vec3[] bodyAxes(LivingEntity le, float pt) {
        float yaw = Mth.rotLerp(pt, le.yBodyRotO, le.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        return new Vec3[]{fwd, right};
    }

    /** Costelas espectrais do Susanoo em volta do corpo, coluna e crânio. */
    static void susanoo(VertexConsumer vc, PoseStack ps, Vec3 p, LivingEntity le, float pt, float time, float fade, boolean firstPerson, boolean low) {
        Matrix4f m = ps.last().pose();
        Vec3[] ax = bodyAxes(le, pt);
        Vec3 fwd = ax[0], right = ax[1], back = fwd.scale(-1), up = new Vec3(0, 1, 0);
        float h = le.getBbHeight() / 1.8F;
        float flicker = 0.85F + 0.15F * Mth.sin(time * 0.7F);
        float a = 0.6F * fade * flicker * (firstPerson ? 0.45F : 1F);
        int rgb = 0x9A4DFF;
        int ribs = low ? 4 : 6;
        float R = 0.95F * Math.max(0.8F, h);
        for (int i = 0; i < ribs; i++) {
            double y = (0.95 + i * 0.27) * h;
            float rR = R * (1F - Math.abs(i - ribs * 0.45F) * 0.06F);
            for (int s = -1; s <= 1; s += 2) {
                Vec3 prev = null;
                int seg = low ? 6 : 10;
                for (int k = 0; k <= seg; k++) {
                    double th = Math.toRadians(150.0 * k / seg);
                    double drop = 0.22 * k / seg;
                    Vec3 pt3 = p.add(back.scale(Math.cos(th) * rR * 0.75)).add(right.scale(Math.sin(th) * rR * s)).add(up.scale(y - drop));
                    if (prev != null) {
                        float w = 0.09F * (1F - 0.5F * k / seg);
                        quad(vc, m, prev.subtract(0, w, 0), prev.add(0, w, 0), pt3.add(0, w, 0), pt3.subtract(0, w, 0),
                                argb(rgb, a), argb(0xE0C8FF, a), argb(0xE0C8FF, a), argb(rgb, a));
                    }
                    prev = pt3;
                }
            }
        }
        if (firstPerson) return;
        // coluna
        Vec3 s0 = p.add(back.scale(R * 0.75)).add(up.scale(0.6 * h)), s1 = p.add(back.scale(R * 0.7)).add(up.scale(2.55 * h));
        quad(vc, m, s0.add(right.scale(0.1)), s0.subtract(right.scale(0.1)), s1.subtract(right.scale(0.1)), s1.add(right.scale(0.1)),
                argb(rgb, a), argb(rgb, a), argb(0xE0C8FF, a), argb(0xE0C8FF, a));
        // clavículas e crânio
        Vec3 sh = p.add(up.scale(2.45 * h));
        for (int s = -1; s <= 1; s += 2) {
            Vec3 o = sh.add(right.scale(s * R * 1.1)).add(back.scale(0.2));
            quad(vc, m, sh.add(back.scale(0.3)).subtract(0, 0.06, 0), sh.add(back.scale(0.3)).add(0, 0.06, 0), o.add(0, 0.06, 0), o.subtract(0, 0.06, 0),
                    argb(rgb, a), argb(rgb, a), argb(0xE0C8FF, a * 0.6F), argb(0xE0C8FF, a * 0.6F));
        }
        Vec3 skull = p.add(up.scale(2.95 * h)).add(back.scale(0.15));
        ring(vc, m, skull, right, up, 0.42F * h, 0.12F, rgb, a * 0.8F, 20);
        Vec3 eyeL = skull.add(fwd.scale(0.35)).add(right.scale(0.15)), eyeR = skull.add(fwd.scale(0.35)).add(right.scale(-0.15));
        quad(vc, m, eyeL.add(0, 0.05, 0), eyeL.add(right.scale(0.08)), eyeL.subtract(0, 0.05, 0), eyeL.subtract(right.scale(0.08)),
                argb(0xFFE0FF, fade), argb(0xFFE0FF, fade), argb(0xFFE0FF, fade), argb(0xFFE0FF, fade));
        quad(vc, m, eyeR.add(0, 0.05, 0), eyeR.add(right.scale(0.08)), eyeR.subtract(0, 0.05, 0), eyeR.subtract(right.scale(0.08)),
                argb(0xFFE0FF, fade), argb(0xFFE0FF, fade), argb(0xFFE0FF, fade), argb(0xFFE0FF, fade));
    }

    // =====================================================================================
    //  Konan — asas de papel
    // =====================================================================================
    static void paperWings(VertexConsumer vc, PoseStack ps, Vec3 p, LivingEntity le, float pt, float time) {
        Matrix4f m = ps.last().pose();
        Vec3[] ax = bodyAxes(le, pt);
        Vec3 back = ax[0].scale(-1), right = ax[1], up = new Vec3(0, 1, 0);
        float h = le.getBbHeight() / 1.8F;
        Vec3 root = p.add(up.scale((le.isCrouching() ? 1.05 : 1.3) * h)).add(back.scale(0.28));
        boolean air = !le.onGround();
        float flap = Mth.sin(time * (air ? 0.35F : 0.12F)) * (air ? 0.22F : 0.08F);
        for (int s = -1; s <= 1; s += 2) {
            for (int k = 0; k < 6; k++) {
                double ang = Math.toRadians(8 + k * 15) + flap;
                Vec3 d = right.scale(s * Math.cos(ang)).add(up.scale(Math.sin(ang) * 0.85)).add(back.scale(0.18 + 0.03 * k)).normalize();
                float len = (1.45F - k * 0.13F) * h;
                Vec3 a = root.add(d.scale(0.12)), b = root.add(d.scale(len));
                Vec3 wv = d.cross(back).normalize().scale(0.14 * h);
                int edge = argb(0xD8D2C0, 0.95F), mid = argb(0xF6F2E6, 0.97F);
                // folha de papel: um losango comprido (dobra no meio)
                Vec3 mid1 = a.lerp(b, 0.45);
                quad(vc, m, a, mid1.add(wv), b, mid1, mid, edge, edge, mid);
                quad(vc, m, a, mid1, b, mid1.subtract(wv), mid, mid, edge, edge);
            }
        }
    }

    // =====================================================================================
    //  Chidori — a mão crepitando
    // =====================================================================================
    static void chidoriHand(VertexConsumer vc, Matrix4f m, Vec3 p, LivingEntity le, float pt, float time, Vector3f left, Vector3f up,
                            Vec3 toCam) {
        Vec3[] ax = bodyAxes(le, pt);
        Vec3 hand = p.add(0, le.getBbHeight() * 0.55, 0).add(ax[0].scale(0.45)).add(ax[1].scale(-0.38));
        float f = 0.8F + 0.2F * Mth.sin(time * 2.1F);
        billboard(vc, m, hand, left, up, 0.55F * f, argb(0xFFFFFF, 0.9F), argb(0x8FD8FF, 0F));
        billboard(vc, m, hand, left, up, 0.9F * f, argb(0x8FD8FF, 0.45F), argb(0x8FD8FF, 0F));
        RandomSource r = RandomSource.create((long) (time * 3));
        for (int k = 0; k < 4; k++) {
            Vec3 d = new Vec3(r.nextDouble() - 0.5, r.nextDouble() - 0.5, r.nextDouble() - 0.5).normalize().scale(0.7 + r.nextDouble() * 0.4);
            lightning(vc, m, hand, d, 0x8FD8FF, 0.06F, 0.9F, r.nextLong(), toCam, 0);
        }
    }

    private LiShapes() {}
}
