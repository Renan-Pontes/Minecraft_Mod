package com.renanpontes.isekaimahou.feature.class_quincy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.class_quincy.entity.LightCageEntity;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/**
 * Sankt Zwinger: a estrela Quincy brilhando no chão, 20 grades verticais de luz que SOBEM em volta (0,5 s), um anel no
 * topo e uma cúpula leve; tudo emissivo e girando devagar. Some aos poucos no fim.
 */
public class LightCageRenderer extends EntityRenderer<LightCageEntity> {
    private static final ResourceLocation BAR = IsekaiMahou.id("textures/entity/quincy_light_cage.png");
    private static final ResourceLocation FLOOR = IsekaiMahou.id("textures/entity/quincy_sankt_zwinger_floor.png");
    private static final int BARS = 20;

    public LightCageRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0F;
    }

    @Override
    public boolean shouldRender(LightCageEntity e, Frustum frustum, double camX, double camY, double camZ) {
        float r = e.radius() + 1;
        return frustum.isVisible(new AABB(e.getX() - r, e.getY(), e.getZ() - r, e.getX() + r, e.getY() + e.height() + 1, e.getZ() + r));
    }

    @Override
    public void render(LightCageEntity e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float t = e.tickCount + partialTick;
        float rise = Mth.clamp(t / 10F, 0F, 1F);
        float fade = Mth.clamp((e.life() - t) / 10F, 0F, 1F);
        if (fade <= 0.01F) return;
        float r = e.radius(), h = e.height() * rise;
        pose.pushPose();
        // estrela no chão
        VertexConsumer floor = buffers.getBuffer(RenderType.entityTranslucentEmissive(FLOOR));
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(t * 0.6F));
        PoseStack.Pose fp = pose.last();
        float fr = r * 1.08F;
        float fa = 0.85F * fade;
        vertex(floor, fp, -fr, 0.06F, -fr, 0, 0, 1F, 1F, 1F, fa, 0, 1, 0);
        vertex(floor, fp, -fr, 0.06F, fr, 0, 1, 1F, 1F, 1F, fa, 0, 1, 0);
        vertex(floor, fp, fr, 0.06F, fr, 1, 1, 1F, 1F, 1F, fa, 0, 1, 0);
        vertex(floor, fp, fr, 0.06F, -fr, 1, 0, 1F, 1F, 1F, fa, 0, 1, 0);
        // cinco PILARES DE LUZ nas pontas da estrela: caem do céu (0,3 s) e ficam de pé, pulsando
        VertexConsumer beam = buffers.getBuffer(QuincyRenderTypes.additive(QuincyRenderTypes.BEAM));
        org.joml.Matrix4f bm = pose.last().pose();
        float drop = Mth.clamp(t / 6F, 0F, 1F);
        float top = h + 9F;
        float bottom = top * (1F - drop * drop);
        float glow = (0.75F + 0.25F * Mth.sin(t * 0.45F)) * fade;
        for (int k = 0; k < 5; k++) {
            float a = -Mth.HALF_PI + k * Mth.TWO_PI / 5;
            float x = Mth.cos(a) * r, z = Mth.sin(a) * r;
            for (int q = 0; q < 2; q++) {
                float w = q == 0 ? 0.55F : 1.4F;
                float dx = q == 0 ? w : 0, dz = q == 0 ? 0 : w;
                int col = QuincyRenderTypes.argb(q == 0 ? 0xFFFFFF : 0x9FD8FF, (q == 0 ? 0.95F : 0.55F) * glow);
                // v 0 = topo (some), v 1 = base (forte)
                beam.addVertex(bm, x - dx, bottom, z - dz).setUv(0, 1).setColor(col);
                beam.addVertex(bm, x + dx, bottom, z + dz).setUv(1, 1).setColor(col);
                beam.addVertex(bm, x + dx, top, z + dz).setUv(1, 0).setColor(col);
                beam.addVertex(bm, x - dx, top, z - dz).setUv(0, 0).setColor(col);
                beam.addVertex(bm, x - dz, bottom, z - dx).setUv(0, 1).setColor(col);
                beam.addVertex(bm, x + dz, bottom, z + dx).setUv(1, 1).setColor(col);
                beam.addVertex(bm, x + dz, top, z + dx).setUv(1, 0).setColor(col);
                beam.addVertex(bm, x - dz, top, z - dx).setUv(0, 0).setColor(col);
            }
        }
        pose.popPose();
        // grades
        VertexConsumer bar = buffers.getBuffer(RenderType.entityTranslucentEmissive(BAR));
        pose.mulPose(Axis.YP.rotationDegrees(-t * 1.2F));
        PoseStack.Pose p = pose.last();
        float half = 0.14F;
        float pulse = 0.8F + 0.2F * Mth.sin(t * 0.5F);
        for (int k = 0; k < BARS; k++) {
            float a = k * Mth.TWO_PI / BARS;
            float x = Mth.cos(a) * r, z = Mth.sin(a) * r;
            float tx = -Mth.sin(a) * half, tz = Mth.cos(a) * half;
            float alpha = 0.9F * fade * pulse;
            // plano tangente (de frente para o centro) e plano radial
            quad(bar, p, x - tx, 0, z - tz, x + tx, 0, z + tz, x + tx, h, z + tz, x - tx, h, z - tz, 0.75F, 0.92F, 1F, alpha);
            float rx = Mth.cos(a) * half, rz = Mth.sin(a) * half;
            quad(bar, p, x - rx, 0, z - rz, x + rx, 0, z + rz, x + rx, h, z + rz, x - rx, h, z - rz, 0.75F, 0.92F, 1F, alpha * 0.7F);
        }
        // anel do topo (faixas horizontais entre as grades)
        if (rise >= 1F) {
            for (int k = 0; k < BARS; k++) {
                float a0 = k * Mth.TWO_PI / BARS, a1 = (k + 1) * Mth.TWO_PI / BARS;
                float x0 = Mth.cos(a0) * r, z0 = Mth.sin(a0) * r, x1 = Mth.cos(a1) * r, z1 = Mth.sin(a1) * r;
                quad(bar, p, x0, h - 0.18F, z0, x1, h - 0.18F, z1, x1, h + 0.18F, z1, x0, h + 0.18F, z0, 1F, 1F, 1F, 0.9F * fade);
                quad(bar, p, x0, 0.02F, z0, x1, 0.02F, z1, x1, 0.3F, z1, x0, 0.3F, z0, 1F, 1F, 1F, 0.6F * fade);
            }
        }
        pose.popPose();
        super.render(e, yaw, partialTick, pose, buffers, light);
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose p, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy,
                             float cz, float dx, float dy, float dz, float r, float g, float b, float a) {
        vertex(vc, p, ax, ay, az, 0, 1, r, g, b, a, 0, 1, 0);
        vertex(vc, p, bx, by, bz, 1, 1, r, g, b, a, 0, 1, 0);
        vertex(vc, p, cx, cy, cz, 1, 0, r, g, b, a, 0, 1, 0);
        vertex(vc, p, dx, dy, dz, 0, 0, r, g, b, a, 0, 1, 0);
        vertex(vc, p, dx, dy, dz, 0, 0, r, g, b, a, 0, 1, 0);
        vertex(vc, p, cx, cy, cz, 1, 0, r, g, b, a, 0, 1, 0);
        vertex(vc, p, bx, by, bz, 1, 1, r, g, b, a, 0, 1, 0);
        vertex(vc, p, ax, ay, az, 0, 1, r, g, b, a, 0, 1, 0);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float u, float v, float r, float g, float b,
                               float a, float nx, float ny, float nz) {
        vc.addVertex(p, x, y, z).setColor(r, g, b, a).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(p, nx, ny, nz);
    }

    @Override
    public ResourceLocation getTextureLocation(LightCageEntity e) {
        return BAR;
    }
}
