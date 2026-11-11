package com.renanpontes.isekaimahou.feature.bosses_b.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.bosses_b.entity.TwilightHerald;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/** Modelo do Arauto: a textura (e o brilho) muda com a postura — o lado da luz ou o da sombra acende. */
public class HeraldModel extends DefaultedEntityGeoModel<TwilightHerald> {
    private static final ResourceLocation TEX_LIGHT = IsekaiMahou.id("textures/entity/twilight_herald_light.png");
    private static final ResourceLocation TEX_SHADOW = IsekaiMahou.id("textures/entity/twilight_herald_shadow.png");

    public HeraldModel() {
        super(IsekaiMahou.id("twilight_herald"), true);
    }

    @Override
    public ResourceLocation getTextureResource(TwilightHerald herald) {
        return herald.stance() == TwilightHerald.LIGHT ? TEX_LIGHT : TEX_SHADOW;
    }
}
