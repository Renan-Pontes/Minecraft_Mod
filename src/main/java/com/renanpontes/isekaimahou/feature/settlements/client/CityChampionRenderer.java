package com.renanpontes.isekaimahou.feature.settlements.client;

import com.renanpontes.isekaimahou.feature.settlements.entity.CityChampion;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Renderer do Campeão da Cidade (GeckoLib, sombra larga). */
public class CityChampionRenderer extends GeoEntityRenderer<CityChampion> {
    public CityChampionRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new CityChampionModel());
        this.shadowRadius = 0.8F;
    }
}
