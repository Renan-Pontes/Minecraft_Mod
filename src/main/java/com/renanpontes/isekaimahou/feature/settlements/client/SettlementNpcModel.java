package com.renanpontes.isekaimahou.feature.settlements.client;

import java.util.Set;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.settlements.entity.SettlementNpc;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * Modelo GeckoLib único dos NPCs das cidades dos jogadores (geo/animações "settlement_npc", ver
 * tools/gen/settlements.py): textura por papel/estilo/variante e peças opcionais conforme o papel e o estilo.
 */
public class SettlementNpcModel extends DefaultedEntityGeoModel<SettlementNpc> {
    public static final Set<String> OPTIONAL = Set.of("hair_long", "beard", "helmet", "plume", "horns", "hood", "cap",
            "spear", "shield", "hammer", "sword", "axe", "pauldron_r", "pauldron_l", "cape", "apron", "skirt", "satchel");

    public SettlementNpcModel() {
        super(IsekaiMahou.id("settlement_npc"), true);
    }

    @Override
    public ResourceLocation getTextureResource(SettlementNpc npc) {
        return IsekaiMahou.id("textures/entity/settlement_npc/" + npc.textureName() + ".png");
    }

    @Override
    public void setCustomAnimations(SettlementNpc npc, long instanceId, AnimationState<SettlementNpc> state) {
        super.setCustomAnimations(npc, instanceId, state);
        Set<String> visible = npc.visibleBones();
        // variantes ímpares são mulheres: sem barba (o cabelo comprido aparece no lugar, menos sob capuz/elmo fechado)
        boolean female = Math.floorMod(npc.variant(), 2) == 1;
        for (String bone : OPTIONAL) {
            boolean show = visible.contains(bone);
            if (female && "beard".equals(bone)) show = false;
            if (female && "hair_long".equals(bone) && visible.contains("beard") && !visible.contains("hood")) show = true;
            boolean shown = show;
            getBone(bone).ifPresent(b -> b.setHidden(!shown));
        }
    }
}
