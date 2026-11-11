package com.renanpontes.isekaimahou.feature.ultimates.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.ultimates.entity.SpectralColossusEntity;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.texture.AutoGlowingTexture;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.Color;

/**
 * Colosso Espectral: modelo GeckoLib (~3 blocos) escalado ×4, desenhado TRANSLÚCIDO e EMISSIVO na cor do elemento
 * (textura em tons de cinza tingida), pulsando de leve. Por cima, a camada de brilho (_glowmask: olhos, núcleo, fio
 * da espada, runas) mais clara. Surge e se desfaz esmaecendo.
 */
public class ColossusRenderer extends GeoEntityRenderer<SpectralColossusEntity> {
    public static final float SCALE = 4F;

    public ColossusRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(IsekaiMahou.id("spectral_colossus")));
        withScale(SCALE);
        addRenderLayer(new Glow(this));
        this.shadowRadius = 0F;
    }

    @Override
    public void render(SpectralColossusEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight) {
        super.render(entity, entityYaw, partialTick, poseStack, buffers, LightTexture.FULL_BRIGHT);
    }

    @Override
    public RenderType getRenderType(SpectralColossusEntity animatable, ResourceLocation texture, MultiBufferSource bufferSource,
                                    float partialTick) {
        return RenderType.entityTranslucentEmissive(texture);
    }

    @Override
    public Color getRenderColor(SpectralColossusEntity e, float partialTick, int packedLight) {
        int rgb = UltRenderTypes.lighten(e.color(), 0.2F);
        float pulse = 0.5F + 0.08F * Mth.sin((e.tickCount + partialTick) * 0.12F);
        int a = (int) (255 * pulse * e.visibility(partialTick));
        return Color.ofRGBA((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, Mth.clamp(a, 0, 255));
    }

    @Override
    protected void applyRotations(SpectralColossusEntity entity, PoseStack poseStack, float ageInTicks, float rotationYaw,
                                  float partialTick, float nativeScale) {
        float yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        poseStack.mulPose(Axis.YP.rotationDegrees(180F - yaw));
    }

    /** Camada brilhante (olhos, núcleo, fio da espada, runas) na cor do elemento clareada. */
    private static final class Glow extends GeoRenderLayer<SpectralColossusEntity> {
        Glow(GeoRenderer<SpectralColossusEntity> renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, SpectralColossusEntity e, BakedGeoModel model, RenderType renderType,
                           MultiBufferSource buffers, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
            float vis = e.visibility(partialTick);
            if (vis <= 0.02F) return;
            RenderType glow = AutoGlowingTexture.getRenderType(getTextureResource(e));
            int rgb = UltRenderTypes.lighten(e.color(), 0.55F);
            float pulse = 0.75F + 0.25F * Mth.sin((e.tickCount + partialTick) * 0.2F);
            int r = (int) (((rgb >> 16) & 255) * pulse * vis), g = (int) (((rgb >> 8) & 255) * pulse * vis),
                    b = (int) ((rgb & 255) * pulse * vis);
            int argb = ((int) (255 * vis) << 24) | (r << 16) | (g << 8) | b;
            getRenderer().reRender(model, poseStack, buffers, e, glow, buffers.getBuffer(glow), partialTick,
                    LightTexture.FULL_BRIGHT, packedOverlay, argb);
        }
    }
}
