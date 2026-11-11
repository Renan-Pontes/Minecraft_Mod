package com.renanpontes.isekaimahou.feature.ascension.client;

import java.util.Optional;
import java.util.UUID;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.ascension.entity.AscensionShadowEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * Modelo da Sombra: humanoide no layout de skin de jogador (64×64, com a segunda camada).
 * Se a Sombra tem dono, usa a SKIN do dono (e o modelo de braços finos se a skin for "slim");
 * sem dono, usa a textura própria (silhueta escura).
 */
public class AscensionShadowModel extends DefaultedEntityGeoModel<AscensionShadowEntity> {
    private static final ResourceLocation SLIM = IsekaiMahou.id("geo/entity/ascension_shadow_slim.geo.json");

    public AscensionShadowModel() {
        super(IsekaiMahou.id("ascension_shadow"), true);
    }

    static Optional<PlayerSkin> skinOf(AscensionShadowEntity shadow) {
        Optional<UUID> owner = shadow.ownerId();
        if (owner.isEmpty()) return Optional.empty();
        ClientPacketListener conn = Minecraft.getInstance().getConnection();
        PlayerInfo info = conn != null ? conn.getPlayerInfo(owner.get()) : null;
        return Optional.of(info != null ? info.getSkin() : DefaultPlayerSkin.get(owner.get()));
    }

    @Override
    public ResourceLocation getModelResource(AscensionShadowEntity shadow) {
        Optional<PlayerSkin> skin = skinOf(shadow);
        if (skin.isPresent() && skin.get().model() == PlayerSkin.Model.SLIM) return SLIM;
        return super.getModelResource(shadow);
    }

    @Override
    public ResourceLocation getTextureResource(AscensionShadowEntity shadow) {
        return skinOf(shadow).map(PlayerSkin::texture).orElseGet(() -> super.getTextureResource(shadow));
    }
}
