package com.renanpontes.isekaimahou.feature.fusions_d.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.fusions_d.entity.AbyssalMawEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Bocarra Abissal: modelo GeckoLib (anel de mandíbulas com presas, garganta e olhos que brilham), 1,7x. */
public class AbyssalMawRenderer extends GeoEntityRenderer<AbyssalMawEntity> {
    public AbyssalMawRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(IsekaiMahou.id("fd_abyssal_maw")));
        withScale(1.7F);
        this.shadowRadius = 2.2F;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }
}
