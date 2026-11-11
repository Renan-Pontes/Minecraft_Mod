package com.renanpontes.isekaimahou.feature.creatures_c.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.creatures_c.entity.Mimic;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Mímico: disfarçado, é idêntico a um baú (sem sombra de mob para não se entregar);
 * revelado, mostra os olhos brilhando dentro da tampa.
 */
public class MimicRenderer extends GeoEntityRenderer<Mimic> {
    public MimicRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(IsekaiMahou.id("mimic")));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public void render(Mimic mimic, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        this.shadowRadius = mimic.isDisguised() ? 0.0F : 0.5F;
        super.render(mimic, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }
}
