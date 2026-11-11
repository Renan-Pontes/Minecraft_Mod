package com.renanpontes.isekaimahou.feature.afterlife.client;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.afterlife.entity.CorpseEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * Desenha o Corpo: o modelo de jogador com a pele do dono (fina ou larga), deitado de costas, com a armadura e o item
 * da mão (camadas do vanilla — valem armaduras GeckoLib). Carbonizado = corpo quase preto. Desbota quando esvazia.
 * A pele vem do jogador se ele estiver carregado; senão das texturas sincronizadas pelo corpo (o dono pode estar offline).
 */
public class CorpseRenderer extends LivingEntityRenderer<CorpseEntity, PlayerModel<CorpseEntity>> {
    private final PlayerModel<CorpseEntity> wide;
    private final PlayerModel<CorpseEntity> slim;
    private final Map<CorpseEntity, GameProfile> profiles = new WeakHashMap<>();

    public CorpseRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.35F);
        this.wide = this.model;
        this.slim = new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        addLayer(new HumanoidArmorLayer<>(this, new HumanoidArmorModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidArmorModel<>(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)), ctx.getModelManager()));
        addLayer(new ItemInHandLayer<>(this, ctx.getItemInHandRenderer()));
    }

    private PlayerSkin skin(CorpseEntity e) {
        Minecraft mc = Minecraft.getInstance();
        UUID owner = e.owner();
        if (owner == null) return DefaultPlayerSkin.get(e.getUUID());
        if (mc.level != null) {
            Player p = mc.level.getPlayerByUUID(owner);
            if (p instanceof AbstractClientPlayer acp) return acp.getSkin();
        }
        if (e.skinValue().isEmpty()) return DefaultPlayerSkin.get(owner);
        GameProfile gp = profiles.computeIfAbsent(e, c -> {
            GameProfile g = new GameProfile(owner, c.ownerName().isEmpty() ? "corpse" : c.ownerName());
            String sig = c.skinSignature();
            g.getProperties().put("textures", new Property("textures", c.skinValue(), sig.isEmpty() ? null : sig));
            return g;
        });
        return mc.getSkinManager().getInsecureSkin(gp);
    }

    @Override
    public void render(CorpseEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        float alpha = e.alpha(pt);
        if (alpha <= 0.01F) return;
        PlayerSkin skin = skin(e);
        this.model = skin.model() == PlayerSkin.Model.SLIM ? slim : wide;
        pose(this.model);
        float age = e.tickCount + pt;
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(180F - e.getYRot()));
        ps.translate(0.0, 0.13, -0.85);
        ps.mulPose(Axis.XP.rotationDegrees(90F));
        ps.scale(-0.9375F, -0.9375F, 0.9375F);
        ps.translate(0.0F, -1.501F, 0.0F);
        int a = (int) (255 * alpha);
        int tint = e.charred() ? 0x2E2622 : 0xFFFFFF;
        RenderType type = alpha < 0.99F || e.inChallenge() ? RenderType.entityTranslucent(skin.texture())
                : RenderType.entityCutoutNoCull(skin.texture());
        // no desafio o corpo fica levemente pálido (a alma está fora)
        if (e.inChallenge()) tint = 0xC8D2DC;
        this.model.renderToBuffer(ps, buffers.getBuffer(type), light, OverlayTexture.NO_OVERLAY, (a << 24) | tint);
        if (alpha >= 0.99F) {
            for (RenderLayer<CorpseEntity, PlayerModel<CorpseEntity>> layer : this.layers) {
                layer.render(ps, buffers, light, e, 0F, 0F, pt, age, 0F, 0F);
            }
        }
        ps.popPose();
        if (shouldShowName(e)) renderNameTag(e, e.getDisplayName(), ps, buffers, light, pt);
    }

    /** Deitado: braços junto ao corpo, cabeça levemente inclinada para não afundar no chão. */
    private static void pose(PlayerModel<CorpseEntity> m) {
        for (ModelPart p : new ModelPart[] {m.head, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg}) {
            p.xRot = 0F;
            p.yRot = 0F;
            p.zRot = 0F;
        }
        m.young = false;
        m.crouching = false;
        m.riding = false;
        m.attackTime = 0F;
        m.head.xRot = 0.35F;
        m.rightArm.zRot = 0.12F;
        m.leftArm.zRot = -0.12F;
        m.rightLeg.zRot = 0.04F;
        m.leftLeg.zRot = -0.04F;
        m.hat.copyFrom(m.head);
        m.jacket.copyFrom(m.body);
        m.rightSleeve.copyFrom(m.rightArm);
        m.leftSleeve.copyFrom(m.leftArm);
        m.rightPants.copyFrom(m.rightLeg);
        m.leftPants.copyFrom(m.leftLeg);
    }

    @Override
    public ResourceLocation getTextureLocation(CorpseEntity e) {
        return skin(e).texture();
    }
}
