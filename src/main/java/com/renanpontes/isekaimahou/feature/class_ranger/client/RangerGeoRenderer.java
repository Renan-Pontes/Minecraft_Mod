package com.renanpontes.isekaimahou.feature.class_ranger.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.util.Color;

/**
 * Renderer GeckoLib do módulo: camada que brilha (_glowmask) e, para a Águia Espiritual, translucidez, luz máxima e
 * escala fixa.
 */
public class RangerGeoRenderer<T extends Entity & GeoAnimatable> extends GeoEntityRenderer<T> {
    private float alpha = 1.0F;
    private float scale = 1.0F;
    private boolean fullBright;

    public RangerGeoRenderer(EntityRendererProvider.Context ctx, GeoModel<T> model, float shadow) {
        super(ctx, model);
        this.shadowRadius = shadow;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    /** Fantasma: translúcido, sempre aceso e maior. */
    public RangerGeoRenderer<T> spirit(float alpha, float scale) {
        this.alpha = alpha;
        this.scale = scale;
        this.fullBright = true;
        return this;
    }

    @Override
    public RenderType getRenderType(T animatable, ResourceLocation texture, MultiBufferSource bufferSource, float partialTick) {
        return alpha < 1.0F ? RenderType.entityTranslucent(texture) : super.getRenderType(animatable, texture, bufferSource, partialTick);
    }

    @Override
    public Color getRenderColor(T animatable, float partialTick, int packedLight) {
        return alpha < 1.0F ? Color.ofRGBA(1.0F, 1.0F, 1.0F, alpha) : super.getRenderColor(animatable, partialTick, packedLight);
    }

    @Override
    public void scaleModelForRender(float widthScale, float heightScale, PoseStack poseStack, T animatable, BakedGeoModel model,
                                    boolean isReRender, float partialTick, int packedLight, int packedOverlay) {
        super.scaleModelForRender(widthScale * scale, heightScale * scale, poseStack, animatable, model, isReRender, partialTick,
                packedLight, packedOverlay);
    }

    @Override
    protected int getBlockLightLevel(T entity, BlockPos pos) {
        return fullBright ? 15 : super.getBlockLightLevel(entity, pos);
    }
}
