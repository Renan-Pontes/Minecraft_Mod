package com.renanpontes.isekaimahou.feature.hueco_mundo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.hueco_mundo.entity.HmBolt;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Projéteis do módulo: orbe brilhante (branco tingido pela cor do tipo) virado para a câmera, duas camadas girando. */
public class HmBoltRenderer extends EntityRenderer<HmBolt> {
    private static final ResourceLocation TEX = IsekaiMahou.id("textures/entity/hm_bolt.png");

    public HmBoltRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(HmBolt bolt, float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(TEX));
        float t = bolt.tickCount + partialTick;
        float s = switch (bolt.kind()) {
            case HmBolt.BALA -> 0.45F;
            case HmBolt.SPARKLE -> 0.5F;
            default -> 0.8F;
        };
        int argb = 0xFF000000 | bolt.color();
        pose.pushPose();
        pose.translate(0.0F, bolt.getBbHeight() / 2.0F, 0.0F);
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        for (int layer = 0; layer < 2; layer++) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(t * (layer == 0 ? 20F : -13F)));
            float k = layer == 0 ? s : s * (0.6F + 0.1F * (float) Math.sin(t * 0.7F));
            pose.scale(k, k, k);
            PoseStack.Pose p = pose.last();
            int c = layer == 0 ? argb : 0xFFFFFFFF;
            vertex(vc, p, -0.5F, -0.5F, 0, 1, c);
            vertex(vc, p, 0.5F, -0.5F, 1, 1, c);
            vertex(vc, p, 0.5F, 0.5F, 1, 0, c);
            vertex(vc, p, -0.5F, 0.5F, 0, 0, c);
            pose.popPose();
        }
        pose.popPose();
        super.render(bolt, entityYaw, partialTick, pose, buffers, light);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float u, float v, int argb) {
        vc.addVertex(pose, x, y, 0.0F).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(HmBolt bolt) {
        return TEX;
    }
}
