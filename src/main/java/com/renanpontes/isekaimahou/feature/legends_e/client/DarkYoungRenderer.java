package com.renanpontes.isekaimahou.feature.legends_e.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.legends_e.entity.DarkYoungEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Filhote Sombrio: modelo GeckoLib (tentáculos, bocas, cascos); olhos, baba e veios brilham (textura _glowmask). */
public class DarkYoungRenderer extends GeoEntityRenderer<DarkYoungEntity> {
    public DarkYoungRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(IsekaiMahou.id("dark_young"), false));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 1.3F;
    }
}
