package com.renanpontes.isekaimahou.feature.origins_a.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.origins_a.familiar.FamiliarCatEntity;
import com.renanpontes.isekaimahou.feature.origins_a.familiar.FamiliarCrowEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;

/** Renderers GeckoLib dos familiares (olhos brilhantes via glowmask). */
final class FamiliarRenderers {

    /** Corvo: mostra o item que está carregando no bico (osso "beak_item"). */
    static class Crow extends GeoEntityRenderer<FamiliarCrowEntity> {
        Crow(EntityRendererProvider.Context ctx) {
            super(ctx, new DefaultedEntityGeoModel<>(IsekaiMahou.id("familiar_crow"), true));
            withScale(1.2F);
            shadowRadius = 0.25F;
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
            addRenderLayer(new BlockAndItemGeoLayer<>(this,
                    (bone, crow) -> "beak_item".equals(bone.getName()) && !crow.getMainHandItem().isEmpty() ? crow.getMainHandItem() : null,
                    (bone, crow) -> null) {
                @Override
                protected ItemDisplayContext getTransformTypeForStack(GeoBone bone, ItemStack stack, FamiliarCrowEntity crow) {
                    return ItemDisplayContext.GROUND;
                }

                @Override
                protected void renderStackForBone(PoseStack ps, GeoBone bone, ItemStack stack, FamiliarCrowEntity crow,
                                                  MultiBufferSource buffers, float partialTick, int light, int overlay) {
                    ps.mulPose(Axis.XP.rotationDegrees(-90));
                    ps.scale(0.7F, 0.7F, 0.7F);
                    super.renderStackForBone(ps, bone, stack, crow, buffers, partialTick, light, overlay);
                }
            });
        }
    }

    /** Gato preto de olhos verdes. */
    static class Cat extends GeoEntityRenderer<FamiliarCatEntity> {
        Cat(EntityRendererProvider.Context ctx) {
            super(ctx, new DefaultedEntityGeoModel<>(IsekaiMahou.id("familiar_cat"), true));
            shadowRadius = 0.35F;
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }
    }

    private FamiliarRenderers() {}
}
