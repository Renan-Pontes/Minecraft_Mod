package com.renanpontes.isekaimahou.feature.familiars.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.familiars.FamiliarEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Renderer do Familiar: um modelo GeckoLib por forma ({@code geo/entity/fam_<forma>.geo.json}) e uma textura por
 * estágio ({@code textures/entity/fam_<forma>_<estágio>.png} + {@code _glowmask}). Os enfeites de evolução são ossos
 * {@code s2_*} / {@code s3_*} do mesmo modelo, que só aparecem a partir do estágio 2 / 3. O tamanho vem do atributo de
 * escala da entidade.
 */
final class FamiliarRenderer extends GeoEntityRenderer<FamiliarEntity> {
    FamiliarRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new Model());
        shadowRadius = 0.22F;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public void renderRecursively(PoseStack poseStack, FamiliarEntity animatable, GeoBone bone, RenderType renderType,
                                  MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick,
                                  int packedLight, int packedOverlay, int colour) {
        String name = bone.getName();
        int stage = animatable.stage();
        // Enfeites de estágio que o familiar ainda não ganhou: pula o osso (e os filhos dele)
        if (stage < 2 && name.startsWith("s2_") || stage < 3 && name.startsWith("s3_")) return;
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, colour);
    }

    private static final class Model extends GeoModel<FamiliarEntity> {
        @Override
        public ResourceLocation getModelResource(FamiliarEntity e) {
            return IsekaiMahou.id("geo/entity/fam_" + e.form().id + ".geo.json");
        }

        @Override
        public ResourceLocation getTextureResource(FamiliarEntity e) {
            return IsekaiMahou.id("textures/entity/fam_" + e.form().id + "_" + e.stage() + ".png");
        }

        @Override
        public ResourceLocation getAnimationResource(FamiliarEntity e) {
            return IsekaiMahou.id("animations/entity/fam_" + e.form().id + ".animation.json");
        }
    }
}
