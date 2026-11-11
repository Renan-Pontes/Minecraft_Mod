package com.renanpontes.isekaimahou.feature.class_rogue.client;

import java.util.UUID;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.class_rogue.entity.RogueAfterimageEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Desenha a imagem residual: o corpo do jogador dono (modelo de jogador com a pele dele — fina ou larga), translúcido e
 * tingido com a cor da técnica, numa pose de corrida/golpe. Desbota nos últimos ticks. Sem dono conhecido, usa a pele
 * padrão.
 */
public class RogueAfterimageRenderer extends EntityRenderer<RogueAfterimageEntity> {
    private final PlayerModel<AbstractClientPlayer> wide;
    private final PlayerModel<AbstractClientPlayer> slim;

    public RogueAfterimageRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.wide = new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false);
        this.slim = new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        this.shadowRadius = 0F;
    }

    private static PlayerSkin skinOf(RogueAfterimageEntity e) {
        UUID id = e.ownerId();
        Minecraft mc = Minecraft.getInstance();
        if (id != null && mc.level != null) {
            Player p = mc.level.getPlayerByUUID(id);
            if (p instanceof AbstractClientPlayer acp) return acp.getSkin();
            return DefaultPlayerSkin.get(id);
        }
        return DefaultPlayerSkin.get(e.getUUID());
    }

    @Override
    public void render(RogueAfterimageEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        PlayerSkin skin = skinOf(e);
        PlayerModel<AbstractClientPlayer> model = skin.model() == PlayerSkin.Model.SLIM ? slim : wide;
        float age = e.tickCount + pt;
        float left = e.life() - age;
        float fade = Mth.clamp(left / 6F, 0F, 1F) * Mth.clamp(age / 3F, 0F, 1F);
        if (fade <= 0.02F) return;
        pose(model, e.pose(), age);
        int rgb = e.color();
        int alpha = (int) (150 * fade);
        // tinta: mistura da cor da técnica com branco (a pele continua visível)
        int r = 128 + ((rgb >> 16) & 255) / 2, g = 128 + ((rgb >> 8) & 255) / 2, b = 128 + (rgb & 255) / 2;
        int color = (alpha << 24) | (r << 16) | (g << 8) | b;
        ps.pushPose();
        float bodyYaw = Mth.rotLerp(pt, e.yRotO, e.getYRot());
        ps.mulPose(Axis.YP.rotationDegrees(180F - bodyYaw));
        ps.scale(-0.9375F, -0.9375F, 0.9375F);
        ps.translate(0.0F, -1.501F, 0.0F);
        model.renderToBuffer(ps, buffers.getBuffer(RenderType.entityTranslucent(skin.texture())), LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, color);
        ps.popPose();
        super.render(e, yaw, pt, ps, buffers, light);
    }

    /** Poses fixas: parado, disparada (inclinado, braços para trás) e golpe (braço direito à frente). */
    private static void pose(PlayerModel<AbstractClientPlayer> m, byte pose, float age) {
        reset(m.head);
        reset(m.body);
        reset(m.rightArm);
        reset(m.leftArm);
        reset(m.rightLeg);
        reset(m.leftLeg);
        m.young = false;
        m.crouching = false;
        switch (pose) {
            case RogueAfterimageEntity.POSE_DASH -> {
                m.body.xRot = 0.45F;
                m.head.xRot = -0.25F;
                m.rightArm.xRot = 1.1F;
                m.leftArm.xRot = 1.1F;
                m.rightArm.zRot = 0.25F;
                m.leftArm.zRot = -0.25F;
                m.rightLeg.xRot = -0.9F + Mth.sin(age * 0.9F) * 0.3F;
                m.leftLeg.xRot = 0.7F - Mth.sin(age * 0.9F) * 0.3F;
            }
            case RogueAfterimageEntity.POSE_STRIKE -> {
                m.body.yRot = -0.4F;
                m.rightArm.xRot = -1.6F;
                m.rightArm.yRot = -0.3F;
                m.leftArm.xRot = 0.6F;
                m.rightLeg.xRot = -0.6F;
                m.leftLeg.xRot = 0.5F;
            }
            default -> {
                m.rightArm.xRot = Mth.sin(age * 0.1F) * 0.08F;
                m.leftArm.xRot = -Mth.sin(age * 0.1F) * 0.08F;
            }
        }
        m.hat.copyFrom(m.head);
        m.jacket.copyFrom(m.body);
        m.rightSleeve.copyFrom(m.rightArm);
        m.leftSleeve.copyFrom(m.leftArm);
        m.rightPants.copyFrom(m.rightLeg);
        m.leftPants.copyFrom(m.leftLeg);
    }

    private static void reset(ModelPart p) {
        p.xRot = 0F;
        p.yRot = 0F;
        p.zRot = 0F;
    }

    @Override
    public ResourceLocation getTextureLocation(RogueAfterimageEntity e) {
        return skinOf(e).texture();
    }
}
