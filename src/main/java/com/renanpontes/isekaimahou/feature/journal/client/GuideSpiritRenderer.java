package com.renanpontes.isekaimahou.feature.journal.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.journal.entity.GuideSpiritEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.util.Color;

/**
 * Espírito Guia: translúcido, sempre aceso (luz máxima), com o núcleo/coroa/halo brilhando (glowmask)
 * e levemente tingido com a cor do capítulo. Some aos poucos ao se desfazer.
 */
public class GuideSpiritRenderer extends GeoEntityRenderer<GuideSpiritEntity> {
    public GuideSpiritRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(IsekaiMahou.id("guide_spirit")));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 0.0F;
    }

    @Override
    public RenderType getRenderType(GuideSpiritEntity spirit, ResourceLocation texture, MultiBufferSource buffers, float partialTick) {
        return RenderType.entityTranslucent(texture);
    }

    @Override
    public Color getRenderColor(GuideSpiritEntity spirit, float partialTick, int packedLight) {
        int c = spirit.color();
        // Tinge 30% com a cor do capítulo (o branco da textura continua predominante).
        float r = Mth.lerp(0.3F, 1F, ((c >> 16) & 255) / 255F);
        float g = Mth.lerp(0.3F, 1F, ((c >> 8) & 255) / 255F);
        float b = Mth.lerp(0.3F, 1F, (c & 255) / 255F);
        float alpha = 0.9F + 0.08F * Mth.sin((spirit.tickCount + partialTick) * 0.15F);
        return Color.ofRGBA(r, g, b, alpha);
    }

    @Override
    protected int getBlockLightLevel(GuideSpiritEntity spirit, BlockPos pos) {
        return 15;
    }
}
