package com.renanpontes.isekaimahou.feature.bosses_c.client;

import java.util.EnumMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.bosses_c.entity.ClassBossBolt;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Projéteis dos chefes: orbe brilhante virado para a câmera (duas camadas girando em sentidos opostos). */
public class BoltRenderer extends EntityRenderer<ClassBossBolt> {
    private static final Map<ClassBossBolt.Kind, ResourceLocation> TEXTURES = new EnumMap<>(ClassBossBolt.Kind.class);

    static {
        for (ClassBossBolt.Kind k : ClassBossBolt.Kind.values()) {
            TEXTURES.put(k, IsekaiMahou.id("textures/entity/" + k.texture() + ".png"));
        }
    }

    public BoltRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(ClassBossBolt bolt, float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        ResourceLocation tex = getTextureLocation(bolt);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(tex));
        float t = bolt.tickCount + partialTick;
        float s = 0.75F * bolt.size();
        pose.pushPose();
        pose.translate(0.0F, bolt.getBbHeight() / 2.0F, 0.0F);
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        for (int layer = 0; layer < 2; layer++) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(t * (layer == 0 ? 18F : -11F)));
            float k = layer == 0 ? s : s * (0.7F + 0.08F * (float) Math.sin(t * 0.6F));
            pose.scale(k, k, k);
            PoseStack.Pose p = pose.last();
            vertex(vc, p, -0.5F, -0.5F, 0, 1);
            vertex(vc, p, 0.5F, -0.5F, 1, 1);
            vertex(vc, p, 0.5F, 0.5F, 1, 0);
            vertex(vc, p, -0.5F, 0.5F, 0, 0);
            pose.popPose();
        }
        pose.popPose();
        super.render(bolt, entityYaw, partialTick, pose, buffers, light);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float u, float v) {
        vc.addVertex(pose, x, y, 0.0F)
                .setColor(-1)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(ClassBossBolt bolt) {
        return TEXTURES.get(bolt.kind());
    }
}
