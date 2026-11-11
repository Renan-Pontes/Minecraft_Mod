package com.renanpontes.isekaimahou.feature.fire_water.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.fire_water.entity.SummonedSalamander;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Salamandra: modelo GeckoLib com crista e ponta da cauda brilhando (textura _glowmask). */
public class SalamanderRenderer extends GeoEntityRenderer<SummonedSalamander> {
    public SalamanderRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(IsekaiMahou.id("summoned_salamander"), true));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 0.5F;
    }
}
