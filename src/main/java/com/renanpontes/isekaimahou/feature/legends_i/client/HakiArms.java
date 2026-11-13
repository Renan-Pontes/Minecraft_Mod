package com.renanpontes.isekaimahou.feature.legends_i.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.legends_i.LiNet;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.RenderArmEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/**
 * Haki do Armamento: os BRAÇOS ficam negros e lustrosos por 3 s depois de conjurar uma magia com o aumento. Em terceira
 * pessoa, os braços do modelo são desenhados de novo por cima (um pouco maiores) com a pele tingida de preto-arroxeado;
 * em primeira pessoa, a mão que aparece na tela é trocada pela versão negra.
 */
final class HakiArms {
    /** Preto com um leve brilho roxo (o "lustro" do Busoshoku). */
    private static final int TINT = 0xFF2A2234;

    static void afterPlayer(RenderPlayerEvent.Post e) {
        Player p = e.getEntity();
        if (!LiFxClient.has(p.getId(), LiNet.S_HAKI) || !(p instanceof AbstractClientPlayer acp)) return;
        if (p.isSleeping() || p.isVisuallySwimming() || p.isFallFlying() || p.isInvisible()) return;
        PlayerRenderer r = e.getRenderer();
        PlayerModel<AbstractClientPlayer> model = r.getModel();
        PoseStack ps = e.getPoseStack();
        float pt = e.getPartialTick();
        ps.pushPose();
        float scale = p.getScale();
        ps.scale(scale, scale, scale);
        float bodyYaw = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot);
        ps.mulPose(Axis.YP.rotationDegrees(180F - bodyYaw));
        ps.scale(-1F, -1F, 1F);
        ps.scale(0.9375F, 0.9375F, 0.9375F);
        ps.translate(0F, -1.501F, 0F);
        ResourceLocation skin = acp.getSkin().texture();
        VertexConsumer vc = e.getMultiBufferSource().getBuffer(RenderType.entityTranslucent(skin));
        int light = e.getPackedLight();
        drawBig(model.rightArm, ps, vc, light);
        drawBig(model.leftArm, ps, vc, light);
        ps.popPose();
    }

    private static void drawBig(ModelPart part, PoseStack ps, VertexConsumer vc, int light) {
        if (!part.visible) return;
        float sx = part.xScale, sy = part.yScale, sz = part.zScale;
        part.xScale = sx * 1.08F;
        part.yScale = sy * 1.02F;
        part.zScale = sz * 1.08F;
        part.render(ps, vc, light, OverlayTexture.NO_OVERLAY, TINT);
        part.xScale = sx;
        part.yScale = sy;
        part.zScale = sz;
    }

    /** Primeira pessoa: troca a mão da tela pela mão negra. */
    static void firstPersonArm(RenderArmEvent e) {
        AbstractClientPlayer p = e.getPlayer();
        if (!LiFxClient.has(p.getId(), LiNet.S_HAKI)) return;
        EntityRenderer<? super AbstractClientPlayer> er = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(p);
        if (!(er instanceof PlayerRenderer r)) return;
        PlayerModel<AbstractClientPlayer> model = r.getModel();
        model.attackTime = 0F;
        model.crouching = false;
        model.swimAmount = 0F;
        model.setupAnim(p, 0F, 0F, 0F, 0F, 0F);
        boolean right = e.getArm() == HumanoidArm.RIGHT;
        ModelPart arm = right ? model.rightArm : model.leftArm;
        ModelPart sleeve = right ? model.rightSleeve : model.leftSleeve;
        arm.xRot = 0F;
        sleeve.xRot = 0F;
        VertexConsumer vc = e.getMultiBufferSource().getBuffer(RenderType.entityTranslucent(p.getSkin().texture()));
        arm.render(e.getPoseStack(), vc, e.getPackedLight(), OverlayTexture.NO_OVERLAY, TINT);
        sleeve.render(e.getPoseStack(), vc, e.getPackedLight(), OverlayTexture.NO_OVERLAY, TINT);
        e.setCanceled(true);
    }

    private HakiArms() {}
}
