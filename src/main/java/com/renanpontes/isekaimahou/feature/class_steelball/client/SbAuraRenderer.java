package com.renanpontes.isekaimahou.feature.class_steelball.client;

import java.util.function.BiFunction;
import java.util.function.Function;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Turbilhão e espiral da Rotação Infinita: anéis translúcidos que brilham, na escala do efeito (abrem e fecham).
 */
public class SbAuraRenderer<T extends Entity & GeoAnimatable> extends GeoEntityRenderer<T> {
    private final BiFunction<T, Float, Float> scale;

    public SbAuraRenderer(EntityRendererProvider.Context ctx, String id, Function<T, String> texture, BiFunction<T, Float, Float> scale) {
        super(ctx, new SbGeoModel<>(id, texture));
        this.scale = scale;
        this.shadowRadius = 0F;
    }

    @Override
    public void render(T e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float s = Math.max(0.01F, scale.apply(e, partialTick));
        this.scaleWidth = s;
        this.scaleHeight = s;
        super.render(e, 0F, partialTick, pose, buffers, LightTexture.FULL_BRIGHT);
    }

    /** O modelo é bem maior que a caixa da entidade (turbilhão de 3,5+ blocos): não some ao sair o centro da tela. */
    @Override
    public boolean shouldRender(T e, Frustum frustum, double camX, double camY, double camZ) {
        if (!e.shouldRender(camX, camY, camZ)) return false;
        float r = Math.max(1F, scale.apply(e, 1F) * 4F);
        return frustum.isVisible(e.getBoundingBox().inflate(r, 2.0, r));
    }

    @Override
    public RenderType getRenderType(T animatable, ResourceLocation texture, MultiBufferSource buffers, float partialTick) {
        return RenderType.entityTranslucentEmissive(texture);
    }
}
