package com.renanpontes.isekaimahou.feature.legends_e.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.legends_e.entity.GraspHeartEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Agarrar o Coração: modelo GeckoLib da mão espectral (translúcida e luminosa) e do coração batendo. A mão é virada
 * para o alvo pela rotação da entidade (não é uma criatura, então o giro é aplicado aqui).
 */
public class GraspHeartRenderer extends GeoEntityRenderer<GraspHeartEntity> {
    public GraspHeartRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(IsekaiMahou.id("grasp_heart_hand")));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 0F;
        withScale(1.25F);
    }

    @Override
    public RenderType getRenderType(GraspHeartEntity animatable, ResourceLocation texture, MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityTranslucentEmissive(texture);
    }

    @Override
    protected void applyRotations(GraspHeartEntity e, PoseStack pose, float ageInTicks, float rotationYaw, float partialTick, float nativeScale) {
        pose.mulPose(Axis.YP.rotationDegrees(180F - Mth.rotLerp(partialTick, e.yRotO, e.getYRot())));
    }

    @Override
    public boolean shouldRender(GraspHeartEntity e, Frustum frustum, double x, double y, double z) {
        return true;
    }
}
