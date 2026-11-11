package com.renanpontes.isekaimahou.feature.ritual_warlock.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.ritual_warlock.entity.PatronEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Renderer GeckoLib dos Patronos: geo/animations/textures "patron_<id>" + camada brilhante
 * (textures/entity/patron_<id>_glowmask.png: olhos, lava, estrelas, coral).
 * O bone "head" acompanha o olhar. Ao morrer não tomba: a animação "vanish" afunda o corpo.
 */
public class PatronRenderer<T extends PatronEntity> extends GeoEntityRenderer<T> {
    public PatronRenderer(EntityRendererProvider.Context ctx, String id, float shadow) {
        super(ctx, new DefaultedEntityGeoModel<>(IsekaiMahou.id(id), true));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = shadow;
    }

    @Override
    protected float getDeathMaxRotation(T entity) {
        return 0.0F;
    }
}
