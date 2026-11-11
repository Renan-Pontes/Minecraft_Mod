package com.renanpontes.isekaimahou.feature.fusions_d.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.fusions_d.entity.PlanetCoreEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Núcleo do Esmagamento Planetário: pequena esfera negra de gravidade com dois anéis giroscópicos violeta e um campo
 * de distorção que pulsa. Na hora do desabamento, encolhe e some.
 */
public class PlanetCoreRenderer extends EntityRenderer<PlanetCoreEntity> {
    public PlanetCoreRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(PlanetCoreEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        float open = Math.min(e.openness(pt, 8), e.released() ? 0.4F : 1F);
        if (open <= 0) return;
        float t = e.tickCount + pt;
        float r = 0.6F * open * (1F + 0.08F * Mth.sin(t * 0.5F));
        ps.pushPose();
        ps.translate(0, 0.5, 0);
        Vec3 cam = FdQuads.cameraLocal(e, pt).subtract(0, 0.5, 0);
        Matrix4f m = ps.last().pose();
        FdQuads.spaceSphere(buffers.getBuffer(FdQuads.space()), m, r, 8, 12);
        VertexConsumer glow = buffers.getBuffer(FdQuads.glow());
        FdQuads.halo(glow, m, Vec3.ZERO, cam, r, r * 2.2, FdQuads.argb(0xB59CFF, 0.8F * open), FdQuads.argb(0x3A2266, 0F), 32);
        float pulse = (t * 0.06F) % 1F;
        FdQuads.halo(glow, m, Vec3.ZERO, cam, r * (2 + pulse * 6), r * (2.4 + pulse * 6), FdQuads.argb(0x8E78E8, 0.35F * (1 - pulse) * open),
                FdQuads.argb(0x8E78E8, 0F), 40);
        for (int k = 0; k < 2; k++) {
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(t * (k == 0 ? 7F : -5F)));
            ps.mulPose(Axis.XP.rotationDegrees(k == 0 ? 60F + t * 3F : -35F - t * 2F));
            float rr = r * (k == 0 ? 1.9F : 2.5F);
            FdQuads.ring(glow, ps.last().pose(), 0, rr, rr + 0.09, FdQuads.argb(0xD8C8FF, 0.9F * open), FdQuads.argb(0x6B5BA8, 0.4F * open), 36, 0);
            ps.popPose();
        }
        ps.popPose();
        super.render(e, yaw, pt, ps, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(PlanetCoreEntity e) {
        return FdQuads.WHITE;
    }
}
