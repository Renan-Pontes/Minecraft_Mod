package com.renanpontes.isekaimahou.feature.class_gunslinger.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.class_gunslinger.entity.OutlawEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.util.Color;

/**
 * Forasteiro Espectral (GeckoLib): meio transparente (é um fantasma do faroeste), olhos e runas do revólver brilhando
 * (glowmask). O grau II (Ceifador do Meio-Dia) usa outra textura — poncho vermelho, caveira no chapéu.
 */
public class OutlawRenderer extends GeoEntityRenderer<OutlawEntity> {
    private static final ResourceLocation ID = IsekaiMahou.id("gunslinger_outlaw");
    private static final ResourceLocation TEX_REAPER = IsekaiMahou.id("textures/entity/gunslinger_outlaw_reaper.png");

    public OutlawRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<OutlawEntity>(ID, true) {
            @Override
            public ResourceLocation getTextureResource(OutlawEntity animatable) {
                return animatable.tier() >= 2 ? TEX_REAPER : super.getTextureResource(animatable);
            }
        });
        this.shadowRadius = 0.5F;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public RenderType getRenderType(OutlawEntity animatable, ResourceLocation texture, MultiBufferSource buffers, float partialTick) {
        return RenderType.entityTranslucent(texture);
    }

    @Override
    public Color getRenderColor(OutlawEntity animatable, float partialTick, int packedLight) {
        // na contagem do duelo ele "ainda está chegando": mais transparente
        float alpha = animatable.phase() == OutlawEntity.COUNTDOWN ? 0.6F : 0.88F;
        return Color.ofRGBA(1.0F, 1.0F, 1.0F, alpha);
    }
}
