package com.renanpontes.isekaimahou.feature.ritual_druid.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.feature.ritual_druid.entity.AnimalSpiritEntity;

import net.minecraft.client.renderer.LightTexture;
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
 * Espírito de luz: translúcido, sempre com brilho total, cintilando de leve, com uma segunda passada aditiva
 * (aura) e que some aos poucos quando se desfaz. O Grande Espírito (estágio X) é 1,5× maior.
 */
public class AnimalSpiritRenderer extends GeoEntityRenderer<AnimalSpiritEntity> {
    public AnimalSpiritRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new AnimalSpiritModel());
        addRenderLayer(new AuraLayer(this));
        this.shadowRadius = 0.0F;
    }

    static float fade(AnimalSpiritEntity spirit, float partialTick) {
        int f = spirit.fadeTicks();
        if (f <= 0) return Math.min(1F, (spirit.tickCount + partialTick) / 20F); // surge aos poucos
        return Math.max(0F, 1F - (f + partialTick) / AnimalSpiritEntity.FADE_TICKS);
    }

    @Override
    public void render(AnimalSpiritEntity spirit, float yaw, float partialTick, PoseStack ps, MultiBufferSource buffers, int packedLight) {
        ps.pushPose();
        if (spirit.isGreat()) ps.scale(1.5F, 1.5F, 1.5F);
        super.render(spirit, yaw, partialTick, ps, buffers, LightTexture.FULL_BRIGHT);
        ps.popPose();
    }

    @Override
    public RenderType getRenderType(AnimalSpiritEntity spirit, ResourceLocation texture, MultiBufferSource buffers, float partialTick) {
        return RenderType.entityTranslucentEmissive(texture);
    }

    @Override
    public Color getRenderColor(AnimalSpiritEntity spirit, float partialTick, int packedLight) {
        float flicker = 0.9F + 0.1F * (float) Math.sin((spirit.tickCount + partialTick) * 0.3F);
        return Color.ofRGBA(1F, 1F, 1F, 0.72F * flicker * fade(spirit, partialTick));
    }

    /** Aura aditiva na cor do espírito (render type "eyes": soma luz, não escurece). */
    private static final class AuraLayer extends GeoRenderLayer<AnimalSpiritEntity> {
        AuraLayer(GeoRenderer<AnimalSpiritEntity> renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack ps, AnimalSpiritEntity spirit, BakedGeoModel model, RenderType renderType,
                           MultiBufferSource buffers, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
            RenderType eyes = RenderType.eyes(getTextureResource(spirit));
            float pulse = 0.35F + 0.15F * (float) Math.sin((spirit.tickCount + partialTick) * 0.15F);
            float k = pulse * fade(spirit, partialTick);
            int c = spirit.kind().rgb;
            int r = (int) (((c >> 16) & 255) * k), g = (int) (((c >> 8) & 255) * k), b = (int) ((c & 255) * k);
            int argb = 0xFF000000 | (r << 16) | (g << 8) | b;
            getRenderer().reRender(model, ps, buffers, spirit, eyes, buffers.getBuffer(eyes), partialTick, packedLight,
                    OverlayTexture.NO_OVERLAY, argb);
        }
    }
}
