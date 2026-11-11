package com.renanpontes.isekaimahou.feature.creatures_c.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.creatures_c.entity.DireWolf;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Lobo Atroz: olhos âmbar brilhando; a sela só aparece selado e a coleira só se estiver domado. */
public class DireWolfRenderer extends GeoEntityRenderer<DireWolf> {
    public DireWolfRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(IsekaiMahou.id("dire_wolf"), true));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 0.9F;
    }

    @Override
    public void preRender(PoseStack poseStack, DireWolf wolf, BakedGeoModel model, MultiBufferSource bufferSource,
                          VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        model.getBone("saddle").ifPresent(b -> b.setHidden(!wolf.isSaddled()));
        model.getBone("collar").ifPresent(b -> b.setHidden(!wolf.isTame()));
        super.preRender(poseStack, wolf, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
    }
}
