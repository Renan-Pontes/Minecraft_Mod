package com.renanpontes.isekaimahou.feature.fusions_d.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.feature.fusions_d.entity.YinYangEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Equilíbrio Absoluto: o símbolo yin-yang (calculado ponto a ponto num disco polar, sempre de frente) girando, com
 * aura dourada e violeta, uma esfera de luz e uma de sombra orbitando e um anel no chão do alcance.
 */
public class YinYangRenderer extends EntityRenderer<YinYangEntity> {
    private static final int ANG = 48, RAD = 12;
    private static final int LIGHT = 0xFFF4D8, DARK = 0x2A0E40;

    public YinYangRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(YinYangEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        float open = e.openness(pt, 10);
        if (open <= 0) return;
        float t = e.tickCount + pt;
        double R = 0.9 * open;
        Vec3 cam = FdQuads.cameraLocal(e, pt);
        Vec3 n = cam.normalize();
        Vec3 u = n.cross(new Vec3(0, 1, 0));
        u = u.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : u.normalize();
        Vec3 v = u.cross(n).normalize();
        double rot = t * 0.07;
        Matrix4f m = ps.last().pose();
        VertexConsumer disk = buffers.getBuffer(FdQuads.dark());
        for (int i = 0; i < ANG; i++) {
            double a0 = Math.PI * 2 * i / ANG, a1 = Math.PI * 2 * (i + 1) / ANG;
            for (int j = 0; j < RAD; j++) {
                double r0 = (double) j / RAD, r1 = (double) (j + 1) / RAD;
                double am = (a0 + a1) / 2, rm = (r0 + r1) / 2;
                int col = FdQuads.argb(yin(Math.cos(am) * rm, Math.sin(am) * rm) ? LIGHT : DARK, 0.95F * open);
                FdQuads.tex(disk, ps.last(), p(u, v, a0 + rot, r0 * R), p(u, v, a0 + rot, r1 * R), p(u, v, a1 + rot, r1 * R),
                        p(u, v, a1 + rot, r0 * R), col, 0, 0, 1, 1);
            }
        }
        VertexConsumer glow = buffers.getBuffer(FdQuads.glow());
        FdQuads.halo(glow, m, Vec3.ZERO, cam, R, R * 1.7, FdQuads.argb(0xFFD27A, 0.55F * open), FdQuads.argb(0xC06AA0, 0F), 32);
        FdQuads.halo(glow, m, Vec3.ZERO, cam, R * 1.6, R * 2.4, FdQuads.argb(0x8E4FC8, 0.3F * open), FdQuads.argb(0x4A1E6E, 0F), 32);
        double oa = t * 0.12;
        Vec3 sun = new Vec3(Math.cos(oa) * 1.6, Math.sin(oa * 2) * 0.3, Math.sin(oa) * 1.6);
        FdQuads.halo(glow, m, sun, cam, 0, 0.35 * open, FdQuads.argb(0xFFFFFF, 1F), FdQuads.argb(0xFFD27A, 0F), 16);
        FdQuads.halo(glow, m, sun.reverse(), cam, 0.12 * open, 0.4 * open, FdQuads.argb(0x9B4DFF, 0.9F), FdQuads.argb(0x4A1E6E, 0F), 16);
        float pulse = (t % 20F) / 20F;
        FdQuads.ring(glow, ps.last().pose(), -1.6, e.radius() * pulse, e.radius() * pulse + 0.15,
                FdQuads.argb(0xFFE4A0, 0.5F * (1 - pulse) * open), FdQuads.argb(0xC06AA0, 0.3F * (1 - pulse) * open), 48, 0);
        super.render(e, yaw, pt, ps, buffers, light);
    }

    private static Vec3 p(Vec3 u, Vec3 v, double a, double r) {
        return u.scale(Math.cos(a) * r).add(v.scale(Math.sin(a) * r));
    }

    /** O ponto (x, y) do disco unitário é da metade clara? */
    private static boolean yin(double x, double y) {
        double up = x * x + (y - 0.5) * (y - 0.5), down = x * x + (y + 0.5) * (y + 0.5);
        if (up < 0.022) return false;
        if (down < 0.022) return true;
        if (up < 0.25) return true;
        if (down < 0.25) return false;
        return x < 0;
    }

    @Override
    public ResourceLocation getTextureLocation(YinYangEntity e) {
        return FdQuads.WHITE;
    }
}
