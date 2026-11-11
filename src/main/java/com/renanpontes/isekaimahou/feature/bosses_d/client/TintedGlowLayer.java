package com.renanpontes.isekaimahou.feature.bosses_d.client;

import java.util.function.ToIntFunction;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.texture.AutoGlowingTexture;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Camada brilhante TINGIDA: usa o "_glowmask" da textura atual (pintado em branco/cinza) e multiplica pela cor
 * do elemento. Assim uma fusão usa o corpo do pai A com o brilho na cor do pai B. Pulsa devagar (mais rápido
 * quando {@code fast} devolve true, ex.: canalizando o golpe máximo).
 */
public class TintedGlowLayer<T extends LivingEntity & GeoAnimatable> extends GeoRenderLayer<T> {
    private final ToIntFunction<T> color;
    private final java.util.function.Predicate<T> fast;

    public TintedGlowLayer(GeoRenderer<T> renderer, ToIntFunction<T> color, java.util.function.Predicate<T> fast) {
        super(renderer);
        this.color = color;
        this.fast = fast;
    }

    @Override
    public void render(PoseStack poseStack, T animatable, BakedGeoModel bakedModel, RenderType renderType,
                       MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
        if (animatable.isInvisible()) return;
        RenderType glow = AutoGlowingTexture.getRenderType(getTextureResource(animatable));
        int rgb = color.applyAsInt(animatable);
        float speed = fast.test(animatable) ? 0.45F : 0.12F;
        float pulse = 0.8F + 0.2F * Mth.sin((animatable.tickCount + partialTick) * speed);
        if (animatable.isDeadOrDying()) pulse *= Math.max(0.2F, 1F - animatable.deathTime / 70F);
        int r = (int) (((rgb >> 16) & 255) * pulse);
        int g = (int) (((rgb >> 8) & 255) * pulse);
        int b = (int) ((rgb & 255) * pulse);
        int argb = 0xFF000000 | (r << 16) | (g << 8) | b;
        getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, glow, bufferSource.getBuffer(glow), partialTick,
                LightTexture.FULL_BRIGHT, packedOverlay, argb);
    }
}
