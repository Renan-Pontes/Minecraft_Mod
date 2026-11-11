package com.renanpontes.isekaimahou.feature.origins_b.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.origins_b.entity.ObSummonEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.util.Color;

/**
 * Renderer GeckoLib das invocações do módulo: modelo/animação/textura por id, camada de brilho (olhos, brasas,
 * runas) e, nos últimos segundos de vida, a criatura vai ficando translúcida e tremeluz antes de sumir.
 */
public class SummonGeoRenderer<T extends ObSummonEntity> extends GeoEntityRenderer<T> {
    public SummonGeoRenderer(EntityRendererProvider.Context ctx, String id, float shadow) {
        super(ctx, new DefaultedEntityGeoModel<>(IsekaiMahou.id(id), true));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        shadowRadius = shadow;
    }

    @Override
    public RenderType getRenderType(T animatable, ResourceLocation texture, MultiBufferSource buffers, float partialTick) {
        return RenderType.entityTranslucent(texture);
    }

    @Override
    public Color getRenderColor(T animatable, float partialTick, int packedLight) {
        int alpha = 255;
        int life = animatable.lifetimeClient();
        if (life >= 0 && life < 60) {
            // Desvanecendo: pisca cada vez mais rápido
            float t = animatable.tickCount + partialTick;
            float fade = life / 60F;
            alpha = Mth.clamp((int) (90 + 165 * fade + 40 * Mth.sin(t * (1.2F - fade * 0.8F))), 40, 255);
        }
        return new Color((alpha << 24) | 0xFFFFFF);
    }
}
