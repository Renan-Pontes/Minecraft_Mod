package com.renanpontes.isekaimahou.feature.ascension.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.ascension.entity.AscensionShadowEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.Color;

/**
 * A Sombra é desenhada translúcida e escurecida (roxo-escuro) por cima da skin do dono,
 * com olhos e rachaduras brilhando na cor do elemento ativo dele.
 */
public class AscensionShadowRenderer extends GeoEntityRenderer<AscensionShadowEntity> {
    public AscensionShadowRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new AscensionShadowModel());
        addRenderLayer(new GlowLayer(this));
        this.shadowRadius = 0.5F;
    }

    @Override
    public RenderType getRenderType(AscensionShadowEntity shadow, ResourceLocation texture, MultiBufferSource buffers, float partialTick) {
        return RenderType.entityTranslucent(texture);
    }

    @Override
    public Color getRenderColor(AscensionShadowEntity shadow, float partialTick, int packedLight) {
        float flicker = 0.86F + 0.06F * (float) Math.sin((shadow.tickCount + partialTick) * 0.25F);
        // Skin do dono: escurece bastante. Textura própria (sem dono): já é escura, só a translucidez.
        if (shadow.ownerId().isEmpty()) return Color.ofRGBA(1F, 1F, 1F, flicker);
        return Color.ofRGBA(0.26F, 0.19F, 0.36F, flicker);
    }

    /** Olhos e veias brilhando (render type "eyes": aditivo e sem sombra de luz). */
    private static final class GlowLayer extends GeoRenderLayer<AscensionShadowEntity> {
        private static final ResourceLocation GLOW = IsekaiMahou.id("textures/entity/ascension_shadow_glow.png");

        GlowLayer(GeoRenderer<AscensionShadowEntity> renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack ps, AscensionShadowEntity shadow, BakedGeoModel model, RenderType renderType,
                           MultiBufferSource buffers, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
            RenderType eyes = RenderType.eyes(GLOW);
            int c = shadow.auraColor();
            // Clareia a cor do elemento para o brilho ficar visível mesmo em elementos escuros.
            int r = 128 + ((c >> 16) & 255) / 2, g = 128 + ((c >> 8) & 255) / 2, b = 128 + (c & 255) / 2;
            int argb = 0xFF000000 | (r << 16) | (g << 8) | b;
            getRenderer().reRender(model, ps, buffers, shadow, eyes, buffers.getBuffer(eyes), partialTick, packedLight,
                    OverlayTexture.NO_OVERLAY, argb);
        }
    }
}
