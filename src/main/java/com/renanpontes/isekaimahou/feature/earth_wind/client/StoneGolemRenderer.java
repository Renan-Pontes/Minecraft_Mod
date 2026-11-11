package com.renanpontes.isekaimahou.feature.earth_wind.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.earth_wind.entity.SummonedStoneGolem;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Golem de Pedra (GeckoLib) com as runas e os olhos brilhando (camada _glowmask). */
public class StoneGolemRenderer extends GeoEntityRenderer<SummonedStoneGolem> {
    public StoneGolemRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(IsekaiMahou.id("summoned_stone_golem"), true));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 0.9F;
    }
}
