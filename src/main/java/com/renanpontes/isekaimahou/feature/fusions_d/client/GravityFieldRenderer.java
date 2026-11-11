package com.renanpontes.isekaimahou.feature.fusions_d.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.feature.fusions_d.entity.GravityFieldEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Poço Gravitacional: disco escuro no chão com anéis que se fecham para o centro (a gravidade "afundando"), grade
 * radial, borda acesa e riscos de pressão despencando do alto em toda a área.
 */
public class GravityFieldRenderer extends EntityRenderer<GravityFieldEntity> {
    public GravityFieldRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(GravityFieldEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        float life = e.openness(pt, 8);
        if (life <= 0) return;
        float t = e.tickCount + pt;
        float r = e.radius() * (0.3F + 0.7F * life);
        Matrix4f m = ps.last().pose();
        VertexConsumer dark = buffers.getBuffer(FdQuads.dark());
        for (int i = 0; i < 32; i++) {
            double a0 = Math.PI * 2 * i / 32, a1 = Math.PI * 2 * (i + 1) / 32;
            FdQuads.tex4(dark, ps.last(), new Vec3(0, 0.06, 0), FdQuads.argb(0x05020C, 0.7F * life), 0, 0,
                    new Vec3(Math.cos(a0) * r, 0.06, Math.sin(a0) * r), FdQuads.argb(0x1A1030, 0.25F * life), 1, 0,
                    new Vec3(Math.cos(a1) * r, 0.06, Math.sin(a1) * r), FdQuads.argb(0x1A1030, 0.25F * life), 1, 1,
                    new Vec3(0, 0.06, 0), FdQuads.argb(0x05020C, 0.7F * life), 0, 1);
        }
        VertexConsumer glow = buffers.getBuffer(FdQuads.glow());
        FdQuads.ring(glow, m, 0.08, r * 0.96, r * 1.02, FdQuads.argb(0xB59CFF, 0.9F * life), FdQuads.argb(0x8E78E8, 0.6F * life), 64, 0);
        for (int k = 0; k < 4; k++) {
            float ph = (t * 0.025F + k / 4F) % 1F;
            float rr = r * (1F - ph);
            FdQuads.ring(glow, m, 0.09, Math.max(0, rr - 0.12), rr, FdQuads.argb(0x8E78E8, 0.65F * ph * life), FdQuads.argb(0x6B5BA8, 0.1F * ph * life),
                    48, t * 0.02);
        }
        Vec3 up = FdQuads.cameraLocal(e, pt).add(0, 60, 0);
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12 + t * 0.01;
            FdQuads.beam(glow, m, new Vec3(Math.cos(a) * r * 0.15, 0.07, Math.sin(a) * r * 0.15), new Vec3(Math.cos(a) * r, 0.07, Math.sin(a) * r),
                    up, 0.03, FdQuads.argb(0x6B5BA8, 0.5F * life), FdQuads.argb(0x6B5BA8, 0.05F));
        }
        Vec3 cam = FdQuads.cameraLocal(e, pt);
        for (int i = 0; i < 18; i++) {
            double a = i * 2.39996, d = r * Math.sqrt(((i * 53) % 18 + 0.5) / 18.0);
            float y = 7F - ((t * 0.55F + i * 1.7F) % 7F);
            Vec3 top = new Vec3(Math.cos(a) * d, y + 1.6, Math.sin(a) * d), bot = new Vec3(Math.cos(a) * d, y, Math.sin(a) * d);
            FdQuads.beam(glow, m, top, bot, cam, 0.05, FdQuads.argb(0xB59CFF, 0F), FdQuads.argb(0xD8C8FF, 0.7F * life * Mth.clamp(y / 2F, 0F, 1F)));
        }
        super.render(e, yaw, pt, ps, buffers, light);
    }

    @Override
    public boolean shouldRender(GravityFieldEntity e, net.minecraft.client.renderer.culling.Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(GravityFieldEntity e) {
        return FdQuads.WHITE;
    }
}
