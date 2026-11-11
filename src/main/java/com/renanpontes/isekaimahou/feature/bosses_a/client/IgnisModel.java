package com.renanpontes.isekaimahou.feature.bosses_a.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.bosses_a.entity.IgnisEntity;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/** Modelo do Ignis: na fase 2+ troca para a textura de chamas azuis (e o glowmask azul junto). */
public class IgnisModel extends DefaultedEntityGeoModel<IgnisEntity> {
    private static final ResourceLocation BLUE = IsekaiMahou.id("textures/entity/ignis_blue.png");

    public IgnisModel() {
        super(IsekaiMahou.id("ignis"));
    }

    @Override
    public ResourceLocation getTextureResource(IgnisEntity animatable) {
        return animatable.isBlueFlame() ? BLUE : super.getTextureResource(animatable);
    }
}
