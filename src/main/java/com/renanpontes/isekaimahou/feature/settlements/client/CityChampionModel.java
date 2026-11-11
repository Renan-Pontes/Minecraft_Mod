package com.renanpontes.isekaimahou.feature.settlements.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.settlements.entity.CityChampion;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/** Campeão da Cidade: armadura de placas, elmo de crista, capa e espadão; textura por reino. */
public class CityChampionModel extends DefaultedEntityGeoModel<CityChampion> {
    public CityChampionModel() {
        super(IsekaiMahou.id("city_champion"), true);
    }

    @Override
    public ResourceLocation getTextureResource(CityChampion champ) {
        return IsekaiMahou.id("textures/entity/city_champion_" + champ.style().id + ".png");
    }
}
