package com.renanpontes.isekaimahou.feature.earth_wind.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.earth_wind.entity.SummonedSylph;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Silfide (GeckoLib): semitransparente (asas e cauda de vento) e sempre iluminada, como um espirito. */
public class SylphRenderer extends GeoEntityRenderer<SummonedSylph> {
    public SylphRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(IsekaiMahou.id("summoned_sylph"), true));
        this.shadowRadius = 0.3F;
    }

    @Override
    public RenderType getRenderType(SummonedSylph animatable, ResourceLocation texture, MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityTranslucent(texture);
    }

    @Override
    protected int getBlockLightLevel(SummonedSylph entity, BlockPos pos) {
        return 15;
    }
}
