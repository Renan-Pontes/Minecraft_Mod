package com.renanpontes.isekaimahou.feature.dark_self.client;

import java.util.Optional;
import java.util.UUID;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.dark_self.entity.DarkSelfEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;

/**
 * Desenha a Sombra com o modelo de JOGADOR e a skin do dono (braços finos se a skin for "slim"), escurecida para um
 * roxo quase preto e um pouco translúcida, com os olhos brilhando em vermelho/branco. Armadura e itens nas mãos vêm das
 * camadas normais (cópias visuais que a Sombra veste). Nos primeiros ticks ela sobe de dentro do chão.
 */
public class DarkSelfRenderer extends HumanoidMobRenderer<DarkSelfEntity, DarkSelfRenderer.DarkModel> {
    private static final ResourceLocation EYES = IsekaiMahou.id("textures/entity/dark_self_eyes.png");
    /** Cor multiplicada na skin (ARGB): roxo bem escuro, ~92% opaco. */
    private static final int TINT = 0xEB3A2852;

    private final DarkModel wide;
    private final DarkModel slim;

    public DarkSelfRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DarkModel(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        this.wide = this.model;
        this.slim = new DarkModel(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        addLayer(new HumanoidArmorLayer<>(this, new HumanoidArmorModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidArmorModel<>(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)), ctx.getModelManager()));
        addLayer(new EyesLayer(this));
    }

    /** O modelo de jogador com a cor escura aplicada no corpo (os olhos usam {@link #renderPlain}). */
    public static final class DarkModel extends PlayerModel<DarkSelfEntity> {
        public DarkModel(ModelPart root, boolean slim) {
            super(root, slim);
        }

        @Override
        public void renderToBuffer(PoseStack ps, VertexConsumer vc, int light, int overlay, int color) {
            super.renderToBuffer(ps, vc, light, overlay, FastColor.ARGB32.multiply(color, TINT));
        }

        void renderPlain(PoseStack ps, VertexConsumer vc, int light, int overlay, int color) {
            super.renderToBuffer(ps, vc, light, overlay, color);
        }
    }

    static Optional<PlayerSkin> skinOf(DarkSelfEntity e) {
        Optional<UUID> owner = e.ownerId();
        if (owner.isEmpty()) return Optional.empty();
        ClientPacketListener conn = Minecraft.getInstance().getConnection();
        PlayerInfo info = conn != null ? conn.getPlayerInfo(owner.get()) : null;
        return Optional.of(info != null ? info.getSkin() : DefaultPlayerSkin.get(owner.get()));
    }

    @Override
    public void render(DarkSelfEntity e, float yaw, float partialTick, PoseStack ps, MultiBufferSource buffers, int light) {
        Optional<PlayerSkin> skin = skinOf(e);
        this.model = skin.isPresent() && skin.get().model() == PlayerSkin.Model.SLIM ? slim : wide;
        this.model.rightArmPose = e.getMainHandItem().isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
        this.model.leftArmPose = e.getOffhandItem().isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
        float rise = e.riseProgress(partialTick);
        ps.pushPose();
        if (rise < 1F) ps.translate(0, -(1F - rise) * 1.9F, 0); // sai de dentro do chão
        super.render(e, yaw, partialTick, ps, buffers, light);
        ps.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(DarkSelfEntity e) {
        return skinOf(e).map(PlayerSkin::texture).orElseGet(() -> DefaultPlayerSkin.getDefaultTexture());
    }

    @Override
    protected RenderType getRenderType(DarkSelfEntity e, boolean bodyVisible, boolean translucent, boolean glowing) {
        return RenderType.entityTranslucent(getTextureLocation(e));
    }

    /** Olhos acesos (render "eyes": aditivo, ignora a luz do ambiente). */
    private static final class EyesLayer extends RenderLayer<DarkSelfEntity, DarkModel> {
        EyesLayer(RenderLayerParent<DarkSelfEntity, DarkModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack ps, MultiBufferSource buffers, int light, DarkSelfEntity e, float limbSwing,
                           float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            float pulse = 0.8F + 0.2F * (float) Math.sin(ageInTicks * 0.2F);
            int a = (int) (255 * pulse);
            getParentModel().renderPlain(ps, buffers.getBuffer(RenderType.eyes(EYES)), 0xF000F0, OverlayTexture.NO_OVERLAY,
                    FastColor.ARGB32.color(a, a, a, a));
        }
    }
}
