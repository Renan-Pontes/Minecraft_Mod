package com.renanpontes.isekaimahou.feature.fusions_d.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.feature.fusions_d.entity.DimensionCutEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Corte Dimensional: primeiro um risco de luz correndo pela linha; depois o espaço se abre num rasgo vertical em
 * forma de lente, com bordas serrilhadas acesas e o vazio estrelado lá dentro, que se fecha devagar.
 */
public class DimensionCutRenderer extends EntityRenderer<DimensionCutEntity> {
    private static final int N = 28;

    public DimensionCutRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(DimensionCutEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        Vec3 here = e.getPosition(pt);
        Vec3 a = e.from().subtract(here), b = e.to().subtract(here);
        Vec3 cam = FdQuads.cameraLocal(e, pt);
        Matrix4f m = ps.last().pose();
        VertexConsumer glow = buffers.getBuffer(FdQuads.glow());
        float sa = e.stateAge() + pt;
        if (e.state() == DimensionCutEntity.LINE) {
            float p = Mth.clamp(sa / DimensionCutEntity.LINE_TICKS, 0F, 1F);
            Vec3 end = a.add(b.subtract(a).scale(p));
            FdQuads.softBeam(glow, m, a, end, cam, 0.12, FdQuads.argb(0xFFFFFF, 1F), FdQuads.argb(0xD9A8FF, 0F));
            FdQuads.halo(glow, m, end, cam, 0, 0.6, FdQuads.argb(0xFFFFFF, 0.9F), FdQuads.argb(0xD9A8FF, 0F), 16);
            super.render(e, yaw, pt, ps, buffers, light);
            return;
        }
        float o = Math.min(Mth.clamp(sa / 3F, 0F, 1F), e.openness(pt, 12));
        if (o <= 0) return;
        float h = e.height();
        Vec3[] top = new Vec3[N + 1], bot = new Vec3[N + 1], mid = new Vec3[N + 1];
        for (int i = 0; i <= N; i++) {
            float s = i / (float) N;
            double jag = 0.75 + 0.25 * (((i * 7919 + e.getId() * 31) % 13) / 12.0);
            double hh = h * 0.5 * Math.pow(Math.sin(Math.PI * s), 0.55) * o * (i == 0 || i == N ? 0 : jag);
            mid[i] = a.add(b.subtract(a).scale(s));
            top[i] = mid[i].add(0, hh, 0);
            bot[i] = mid[i].add(0, -hh, 0);
        }
        VertexConsumer space = buffers.getBuffer(FdQuads.space());
        for (int i = 0; i < N; i++) FdQuads.space(space, m, bot[i], bot[i + 1], top[i + 1], top[i]);
        glow = buffers.getBuffer(FdQuads.glow());
        int edge = FdQuads.argb(0xF2E0FF, 0.95F * o), fade = FdQuads.argb(0x9B4DFF, 0F);
        for (int i = 0; i < N; i++) {
            FdQuads.softBeam(glow, m, top[i], top[i + 1], cam, 0.14, edge, fade);
            FdQuads.softBeam(glow, m, bot[i], bot[i + 1], cam, 0.14, edge, fade);
        }
        FdQuads.softBeam(glow, m, a, b, cam, 0.35 * o, FdQuads.argb(0xD9A8FF, 0.75F * o), FdQuads.argb(0x5A2A8A, 0F));
        FdQuads.softBeam(glow, m, a, b, cam, 0.08, FdQuads.argb(0xFFFFFF, o), FdQuads.argb(0xFFFFFF, 0.2F * o));
        super.render(e, yaw, pt, ps, buffers, light);
    }

    @Override
    public boolean shouldRender(DimensionCutEntity e, net.minecraft.client.renderer.culling.Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(DimensionCutEntity e) {
        return FdQuads.WHITE;
    }
}
