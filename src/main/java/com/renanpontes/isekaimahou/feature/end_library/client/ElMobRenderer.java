package com.renanpontes.isekaimahou.feature.end_library.client;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.LivingEntity;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Renderer GeckoLib das criaturas do vazio: modelo/animação/textura pelo id + camada brilhante (_glowmask). */
public class ElMobRenderer<T extends LivingEntity & GeoEntity> extends GeoEntityRenderer<T> {
    public ElMobRenderer(EntityRendererProvider.Context ctx, String id, float shadow) {
        super(ctx, new DefaultedEntityGeoModel<>(IsekaiMahou.id(id), true));
        this.shadowRadius = shadow;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }
}
