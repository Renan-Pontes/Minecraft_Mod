package com.renanpontes.isekaimahou.feature.fusions_b.client;

import java.util.UUID;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.feature.fusions_b.entity.TwilightCloneEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

/**
 * Clone do Crepúsculo: o modelo de jogador com a PELE DO DONO (ou a padrão), translúcido e tingido —
 * dourado e iluminado para o clone de luz, roxo-escuro para o clone de sombra.
 */
public class TwilightCloneRenderer extends LivingEntityRenderer<TwilightCloneEntity, PlayerModel<TwilightCloneEntity>> {
    private static final int LIGHT_TINT = 0xC8FFE6A0;
    private static final int SHADOW_TINT = 0xC0583A80;

    private final TintedModel wide;
    private final TintedModel slim;

    public TwilightCloneRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new TintedModel(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.4F);
        this.wide = (TintedModel) this.model;
        this.slim = new TintedModel(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true);
    }

    @Override
    public void render(TwilightCloneEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        TintedModel m = skin(e).model() == PlayerSkin.Model.SLIM ? slim : wide;
        m.tint = e.isLight() ? LIGHT_TINT : SHADOW_TINT;
        this.model = m;
        super.render(e, yaw, pt, pose, buffers, e.isLight() ? LightTexture.FULL_BRIGHT : light);
    }

    @Override
    public ResourceLocation getTextureLocation(TwilightCloneEntity e) {
        return skin(e).texture();
    }

    @Override
    protected RenderType getRenderType(TwilightCloneEntity e, boolean bodyVisible, boolean translucent, boolean glowing) {
        return RenderType.entityTranslucent(getTextureLocation(e));
    }

    @Override
    protected boolean shouldShowName(TwilightCloneEntity e) {
        return e.hasCustomName() && super.shouldShowName(e);
    }

    @Override
    protected void scale(TwilightCloneEntity e, PoseStack pose, float pt) {
        pose.scale(0.9375F, 0.9375F, 0.9375F); // mesma escala do jogador
    }

    /** Pele do dono se ele estiver na lista de jogadores do cliente; senão a pele padrão do UUID. */
    private static PlayerSkin skin(TwilightCloneEntity e) {
        UUID owner = e.getOwnerUUID();
        if (owner == null) return DefaultPlayerSkin.get(e.getUUID());
        var conn = Minecraft.getInstance().getConnection();
        PlayerInfo info = conn != null ? conn.getPlayerInfo(owner) : null;
        return info != null ? info.getSkin() : DefaultPlayerSkin.get(owner);
    }

    /** Modelo de jogador que multiplica a cor de tudo pelo tom do clone. */
    static class TintedModel extends PlayerModel<TwilightCloneEntity> {
        int tint = -1;

        TintedModel(ModelPart root, boolean slim) {
            super(root, slim);
        }

        @Override
        public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light, int overlay, int color) {
            super.renderToBuffer(pose, buffer, light, overlay, FbRender.multiply(color, tint));
        }
    }
}
