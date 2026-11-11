package com.renanpontes.isekaimahou.feature.creatures_c.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.creatures_c.entity.Harpy;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Harpia: modelo GeckoLib com asas de penas nos braços (planos com recorte). */
public class HarpyRenderer extends GeoEntityRenderer<Harpy> {
    public HarpyRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(IsekaiMahou.id("harpy"), true));
        this.shadowRadius = 0.45F;
    }
}
