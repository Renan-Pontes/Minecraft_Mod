package com.renanpontes.isekaimahou.feature.fusions_a.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.fusions_a.entity.SummonedEnt;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Ent invocado: modelo GeckoLib de árvore viva; olhos e veios de seiva brilham (textura _glowmask). */
public class SummonedEntRenderer extends GeoEntityRenderer<SummonedEnt> {
    public SummonedEntRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(IsekaiMahou.id("summoned_ent"), true));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 0.9F;
    }
}
