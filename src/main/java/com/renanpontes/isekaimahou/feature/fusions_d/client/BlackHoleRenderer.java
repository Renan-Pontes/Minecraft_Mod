package com.renanpontes.isekaimahou.feature.fusions_d.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.fusions_d.entity.BlackHoleEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Buraco Negro: esfera de vazio estrelado envolta numa sombra, anel de fótons brilhante virado para a câmera e um
 * disco de acreção inclinado girando (faixas claras e escuras), que cresce conforme ele devora o terreno.
 */
public class BlackHoleRenderer extends EntityRenderer<BlackHoleEntity> {
    public BlackHoleRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(BlackHoleEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        float open = e.openness(pt, 12);
        if (open <= 0) return;
        float t = e.tickCount + pt;
        float r = e.coreSize() * open * (1F + 0.05F * Mth.sin(t * 0.4F));
        ps.pushPose();
        ps.translate(0, 0.75, 0);
        Vec3 cam = FdQuads.cameraLocal(e, pt).subtract(0, 0.75, 0);
        Matrix4f m = ps.last().pose();
        FdQuads.spaceSphere(buffers.getBuffer(FdQuads.space()), m, r, 10, 16);
        FdQuads.darkSphere(buffers.getBuffer(FdQuads.dark()), ps.last(), r * 1.18, FdQuads.argb(0x05010C, 0.55F * open), 8, 14);
        VertexConsumer glow = buffers.getBuffer(FdQuads.glow());
        // Anel de fótons (borda do horizonte de eventos)
        FdQuads.halo(glow, m, Vec3.ZERO, cam, r * 1.02, r * 1.5, FdQuads.argb(0xF4F0FF, 0.9F * open), FdQuads.argb(0x9DB4FF, 0F), 40);
        FdQuads.halo(glow, m, Vec3.ZERO, cam, r * 1.5, r * 2.6, FdQuads.argb(0x6E5BD8, 0.25F * open), FdQuads.argb(0x3A2A80, 0F), 40);
        // Disco de acreção (duas camadas desencontradas)
        for (int layer = 0; layer < 2; layer++) {
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(t * (layer == 0 ? 6F : -4F)));
            ps.mulPose(Axis.XP.rotationDegrees(layer == 0 ? 18F : 24F));
            Matrix4f dm = ps.last().pose();
            float in = r * 1.45F, out = r * (layer == 0 ? 4.2F : 3.2F);
            int seg = 48;
            for (int i = 0; i < seg; i++) {
                float a0 = (float) (i * Math.PI * 2 / seg), a1 = (float) ((i + 1) * Math.PI * 2 / seg);
                float band = 0.45F + 0.55F * (0.5F + 0.5F * Mth.sin(a0 * 3 + t * 0.12F + layer * 2));
                int ci = FdQuads.argb(layer == 0 ? 0xFFF2E0 : 0xCFE0FF, band * open * 0.95F);
                int cm = FdQuads.argb(layer == 0 ? 0xFF9A50 : 0x7FA0FF, band * open * 0.55F);
                int co = FdQuads.argb(0x5A3AA0, 0F);
                float mid = (in + out) * 0.42F;
                FdQuads.color(glow, dm, new Vec3(Mth.cos(a0) * in, 0, Mth.sin(a0) * in), ci, new Vec3(Mth.cos(a0) * mid, 0, Mth.sin(a0) * mid), cm,
                        new Vec3(Mth.cos(a1) * mid, 0, Mth.sin(a1) * mid), cm, new Vec3(Mth.cos(a1) * in, 0, Mth.sin(a1) * in), ci);
                FdQuads.color(glow, dm, new Vec3(Mth.cos(a0) * mid, 0, Mth.sin(a0) * mid), cm, new Vec3(Mth.cos(a0) * out, 0, Mth.sin(a0) * out), co,
                        new Vec3(Mth.cos(a1) * out, 0, Mth.sin(a1) * out), co, new Vec3(Mth.cos(a1) * mid, 0, Mth.sin(a1) * mid), cm);
            }
            ps.popPose();
        }
        // Jatos polares
        FdQuads.softBeam(glow, m, new Vec3(0, r, 0), new Vec3(0, r + 4.5 * open, 0), cam, r * 0.35, FdQuads.argb(0xDDE6FF, 0.6F * open),
                FdQuads.argb(0x9DB4FF, 0F));
        FdQuads.softBeam(glow, m, new Vec3(0, -r, 0), new Vec3(0, -r - 4.5 * open, 0), cam, r * 0.35, FdQuads.argb(0xDDE6FF, 0.6F * open),
                FdQuads.argb(0x9DB4FF, 0F));
        ps.popPose();
        super.render(e, yaw, pt, ps, buffers, light);
    }

    @Override
    public boolean shouldRender(BlackHoleEntity e, net.minecraft.client.renderer.culling.Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(BlackHoleEntity e) {
        return FdQuads.WHITE;
    }
}
