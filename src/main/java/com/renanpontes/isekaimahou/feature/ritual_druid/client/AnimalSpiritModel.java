package com.renanpontes.isekaimahou.feature.ritual_druid.client;

import java.util.EnumMap;
import java.util.Map;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.ritual_druid.entity.AnimalSpiritEntity;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/** Modelo do Espírito Animal: cada forma (cervo, lobo, falcão) tem o seu .geo, textura e animações. */
public class AnimalSpiritModel extends GeoModel<AnimalSpiritEntity> {
    private record Files(ResourceLocation geo, ResourceLocation texture, ResourceLocation animation) {}

    private static final Map<AnimalSpiritEntity.Kind, Files> FILES = new EnumMap<>(AnimalSpiritEntity.Kind.class);

    static {
        for (AnimalSpiritEntity.Kind k : AnimalSpiritEntity.Kind.values()) {
            String id = "animal_spirit_" + k.id();
            FILES.put(k, new Files(IsekaiMahou.id("geo/entity/" + id + ".geo.json"),
                    IsekaiMahou.id("textures/entity/" + id + ".png"),
                    IsekaiMahou.id("animations/entity/" + id + ".animation.json")));
        }
    }

    @Override
    public ResourceLocation getModelResource(AnimalSpiritEntity spirit) {
        return FILES.get(spirit.kind()).geo();
    }

    @Override
    public ResourceLocation getTextureResource(AnimalSpiritEntity spirit) {
        return FILES.get(spirit.kind()).texture();
    }

    @Override
    public ResourceLocation getAnimationResource(AnimalSpiritEntity spirit) {
        return FILES.get(spirit.kind()).animation();
    }
}
