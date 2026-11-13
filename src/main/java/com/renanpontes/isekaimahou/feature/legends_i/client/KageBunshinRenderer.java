package com.renanpontes.isekaimahou.feature.legends_i.client;

import java.util.UUID;

import com.renanpontes.isekaimahou.feature.legends_i.entity.KageBunshinEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

/**
 * Clone das Sombras: o modelo de jogador com a PELE DO DONO (a que o cliente já baixou para a lista de jogadores),
 * com a arma e a armadura copiadas. Se o dono não é um jogador conhecido, usa a pele padrão pelo id.
 */
public class KageBunshinRenderer extends HumanoidMobRenderer<KageBunshinEntity, PlayerModel<KageBunshinEntity>> {
    public KageBunshinRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        addLayer(new HumanoidArmorLayer<>(this, new HumanoidArmorModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidArmorModel<>(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)), ctx.getModelManager()));
    }

    @Override
    public ResourceLocation getTextureLocation(KageBunshinEntity e) {
        UUID owner = e.getOwnerUUID();
        if (owner == null) return DefaultPlayerSkin.getDefaultTexture();
        ClientPacketListener conn = Minecraft.getInstance().getConnection();
        PlayerInfo info = conn == null ? null : conn.getPlayerInfo(owner);
        return info != null ? info.getSkin().texture() : DefaultPlayerSkin.get(owner).texture();
    }
}
