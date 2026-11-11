package com.renanpontes.isekaimahou.feature.forms_b.client;

import java.util.UUID;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.forms_b.entity.SpellIllusionEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * Modelo humanoide da Ilusão com o mapeamento UV da skin de jogador: usa a skin do dono
 * (braços finos para skins "slim"); se o dono não é jogador, uma silhueta sombria padrão.
 */
public class IllusionModel extends DefaultedEntityGeoModel<SpellIllusionEntity> {
    private static final ResourceLocation WIDE = IsekaiMahou.id("geo/entity/spell_illusion.geo.json");
    private static final ResourceLocation SLIM = IsekaiMahou.id("geo/entity/spell_illusion_slim.geo.json");
    private static final ResourceLocation SHADOW = IsekaiMahou.id("textures/entity/spell_illusion.png");

    public IllusionModel() {
        super(IsekaiMahou.id("spell_illusion"), true);
    }

    private static PlayerSkin skinOf(SpellIllusionEntity e) {
        UUID owner = e.getOwnerUUID();
        ClientPacketListener conn = Minecraft.getInstance().getConnection();
        if (owner == null || conn == null) return null;
        PlayerInfo info = conn.getPlayerInfo(owner);
        return info != null ? info.getSkin() : null;
    }

    @Override
    public ResourceLocation getModelResource(SpellIllusionEntity e) {
        PlayerSkin skin = skinOf(e);
        return skin != null && skin.model() == PlayerSkin.Model.SLIM ? SLIM : WIDE;
    }

    @Override
    public ResourceLocation getTextureResource(SpellIllusionEntity e) {
        PlayerSkin skin = skinOf(e);
        return skin != null ? skin.texture() : SHADOW;
    }
}
