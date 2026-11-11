package com.renanpontes.isekaimahou.feature.cities.client;

import java.util.Set;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.cities.entity.CityNpc;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * Modelo GeckoLib único dos NPCs da cidade (geo/animações "city_npc", ver tools/gen/cities.py): textura por variante
 * (textures/entity/city_npc/&lt;nome&gt;.png) e peças opcionais (elmo, lança, coroa, avental, capuz...) mostradas
 * conforme o tipo, a profissão e a variante.
 */
public class CityNpcModel extends DefaultedEntityGeoModel<CityNpc> {
    /** Todas as peças opcionais do modelo (as outras são o corpo e ficam sempre visíveis). */
    public static final Set<String> OPTIONAL = Set.of("hair_long", "beard", "helmet", "plume", "crown", "hood", "cap",
            "mortarboard", "glasses", "goggles", "mask", "spear", "shield", "hammer", "book", "flask", "scepter", "mug",
            "dagger", "basket", "pauldron_r", "pauldron_l", "cape", "apron", "skirt", "satchel");

    public CityNpcModel() {
        super(IsekaiMahou.id("city_npc"), true);
    }

    @Override
    public ResourceLocation getTextureResource(CityNpc npc) {
        return IsekaiMahou.id("textures/entity/city_npc/" + npc.textureName() + ".png");
    }

    @Override
    public void setCustomAnimations(CityNpc npc, long instanceId, AnimationState<CityNpc> state) {
        super.setCustomAnimations(npc, instanceId, state);
        Set<String> visible = npc.visibleBones();
        for (String bone : OPTIONAL) getBone(bone).ifPresent(b -> b.setHidden(!visible.contains(bone)));
    }
}
