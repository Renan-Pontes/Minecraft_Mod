package com.renanpontes.isekaimahou.feature.fusions_d.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.feature.fusions_d.entity.VoidTearEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Fenda do Vazio: um rasgo vertical serrilhado no ar (sempre de frente para quem olha), com o vazio estrelado dentro,
 * bordas violeta-brancas tremendo e uma aura escura. A saída, no céu, é menor e mais brilhante.
 */
public class VoidTearRenderer extends EntityRenderer<VoidTearEntity> {
    private static final int N = 16;

    public VoidTearRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(VoidTearEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        float open = e.openness(pt, 10);
        if (open <= 0) return;
        float t = e.tickCount + pt;
        float size = e.size();
        Vec3 cam = FdQuads.cameraLocal(e, pt);
        Vec3 side = new Vec3(cam.z, 0, -cam.x);
        side = side.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : side.normalize();
        double cy = 1.4 * size, hh = 1.5 * size * (0.3 + 0.7 * open);
        Vec3[] left = new Vec3[N + 1], right = new Vec3[N + 1];
        for (int i = 0; i <= N; i++) {
            double s = i / (double) N;
            double jag = 0.7 + 0.3 * (((i * 7 + e.getId()) % 5) / 4.0);
            double w = 0.85 * size * Math.pow(Math.sin(Math.PI * s), 0.7) * jag * open * (1 + 0.06 * Mth.sin(t * 0.7F + i));
            double wig = 0.12 * Math.sin(s * 9 + t * 0.15);
            Vec3 c = side.scale(wig).add(0, cy - hh + 2 * hh * s, 0);
            left[i] = c.subtract(side.scale(w));
            right[i] = c.add(side.scale(w));
        }
        Matrix4f m = ps.last().pose();
        VertexConsumer space = buffers.getBuffer(FdQuads.space());
        for (int i = 0; i < N; i++) FdQuads.space(space, m, left[i], right[i], right[i + 1], left[i + 1]);
        VertexConsumer glow = buffers.getBuffer(FdQuads.glow());
        int edge = FdQuads.argb(e.isExit() ? 0xFFFFFF : 0xF2E0FF, 0.95F * open), fade = FdQuads.argb(0x9B4DFF, 0F);
        for (int i = 0; i < N; i++) {
            FdQuads.softBeam(glow, m, left[i], left[i + 1], cam, 0.16, edge, fade);
            FdQuads.softBeam(glow, m, right[i], right[i + 1], cam, 0.16, edge, fade);
        }
        Vec3 center = new Vec3(0, cy, 0);
        FdQuads.halo(glow, m, center, cam, hh * 0.4, hh * 1.5, FdQuads.argb(0x5A2A8A, 0.35F * open), FdQuads.argb(0x12061E, 0F), 32);
        if (e.isExit()) FdQuads.halo(glow, m, center, cam, 0, hh * 0.9, FdQuads.argb(0xD9A8FF, 0.3F * open), FdQuads.argb(0xD9A8FF, 0F), 24);
        super.render(e, yaw, pt, ps, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(VoidTearEntity e) {
        return FdQuads.WHITE;
    }
}
