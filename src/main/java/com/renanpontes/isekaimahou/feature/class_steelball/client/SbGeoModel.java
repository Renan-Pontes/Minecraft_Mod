package com.renanpontes.isekaimahou.feature.class_steelball.client;

import java.util.function.Function;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * Modelo GeckoLib das entidades do Steel Ball (geo/animação pelo id); a textura pode variar por estado
 * (bola de aço × bola dourada, unha rosa × unha dourada).
 */
public class SbGeoModel<T extends GeoAnimatable> extends DefaultedEntityGeoModel<T> {
    private final Function<T, String> texture;

    public SbGeoModel(String id, Function<T, String> texture) {
        super(IsekaiMahou.id(id), false);
        this.texture = texture;
    }

    @Override
    public ResourceLocation getTextureResource(T animatable) {
        return IsekaiMahou.id("textures/entity/" + texture.apply(animatable) + ".png");
    }
}
