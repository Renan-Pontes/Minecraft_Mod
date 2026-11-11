package com.renanpontes.isekaimahou.feature.forms_a.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.forms_a.FormsFx;
import com.renanpontes.isekaimahou.feature.forms_a.entity.SpellFallingEntity;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Meteoro da Queda: dois "cartões" virados para a câmera, sem sombra e sempre aceso —
 * um halo grande e pulsante na cor da magia e um núcleo rochoso brilhante girando por cima.
 * As texturas são em tons de cinza para receberem a cor da magia.
 */
public class SpellFallingRenderer extends EntityRenderer<SpellFallingEntity> {
    private static final ResourceLocation CORE = IsekaiMahou.id("textures/entity/spell_falling.png");
    private static final ResourceLocation GLOW = IsekaiMahou.id("textures/entity/spell_falling_glow.png");

    public SpellFallingRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0F;
    }

    @Override
    protected int getBlockLightLevel(SpellFallingEntity entity, BlockPos pos) {
        return 15;
    }

    @Override
    public void render(SpellFallingEntity entity, float entityYaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight) {
        float size = entity.size();
        float t = entity.tickCount + partialTick;
        int rgb = entity.color() & 0xFFFFFF;

        pose.pushPose();
        pose.translate(0.0F, entity.getBbHeight() * 0.5F, 0.0F);
        pose.mulPose(this.entityRenderDispatcher.cameraOrientation());

        // Halo
        float pulse = 1.0F + 0.1F * Mth.sin(t * 0.7F);
        pose.pushPose();
        pose.scale(2.6F * size * pulse, 2.6F * size * pulse, 2.6F * size * pulse);
        pose.mulPose(Axis.ZP.rotationDegrees(-t * 5.0F));
        quad(buffers.getBuffer(RenderType.entityTranslucentEmissive(GLOW)), pose.last(), 0xC0000000 | rgb);
        pose.popPose();

        // Núcleo
        pose.pushPose();
        pose.scale(1.25F * size, 1.25F * size, 1.25F * size);
        pose.mulPose(Axis.ZP.rotationDegrees(t * 14.0F));
        quad(buffers.getBuffer(RenderType.entityTranslucentEmissive(CORE)), pose.last(), 0xFF000000 | FormsFx.lighten(rgb, 0.35F));
        pose.popPose();

        pose.popPose();
        super.render(entity, entityYaw, partialTick, pose, buffers, packedLight);
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose pose, int argb) {
        vertex(vc, pose, argb, -0.5F, -0.5F, 0, 1);
        vertex(vc, pose, argb, 0.5F, -0.5F, 1, 1);
        vertex(vc, pose, argb, 0.5F, 0.5F, 1, 0);
        vertex(vc, pose, argb, -0.5F, 0.5F, 0, 0);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, int argb, float x, float y, float u, float v) {
        vc.addVertex(pose, x, y, 0.0F)
                .setColor(argb)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(SpellFallingEntity entity) {
        return CORE;
    }
}
