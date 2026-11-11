package com.renanpontes.isekaimahou.feature.afterlife.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/**
 * A alma: no lugar do jogador normal, um corpo translúcido azulado com a pele dele, flutuando de leve, braços caídos e
 * pernas "se desfazendo". Todos os jogadores veem assim. Em primeira pessoa a mão não aparece.
 */
final class SpiritRender {
    private static PlayerModel<AbstractClientPlayer> wide;
    private static PlayerModel<AbstractClientPlayer> slim;

    static void onRenderPlayer(RenderPlayerEvent.Pre e) {
        Player p = e.getEntity();
        if (!(p instanceof AbstractClientPlayer acp) || !ClientAfterlife.isSpirit(p)) return;
        e.setCanceled(true);
        if (wide == null) {
            Minecraft mc = Minecraft.getInstance();
            wide = new PlayerModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
            slim = new PlayerModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM), true);
        }
        PlayerSkin skin = acp.getSkin();
        PlayerModel<AbstractClientPlayer> m = skin.model() == PlayerSkin.Model.SLIM ? slim : wide;
        float pt = e.getPartialTick();
        float age = p.tickCount + pt;
        float bodyYaw = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot);
        float headYaw = Mth.rotLerp(pt, p.yHeadRotO, p.yHeadRot) - bodyYaw;
        float swing = p.walkAnimation.position(pt);
        float amount = Math.min(1F, p.walkAnimation.speed(pt));
        pose(m, age, headYaw, Mth.lerp(pt, p.xRotO, p.getXRot()), swing, amount);

        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        ps.translate(0.0, 0.12 + Mth.sin(age * 0.12F) * 0.06, 0.0);
        ps.mulPose(Axis.YP.rotationDegrees(180F - bodyYaw));
        ps.scale(-0.9375F, -0.9375F, 0.9375F);
        ps.translate(0.0F, -1.501F, 0.0F);
        int alpha = 105 + (int) (Mth.sin(age * 0.2F) * 20);
        int color = (alpha << 24) | 0xA8F0FF;
        m.renderToBuffer(ps, e.getMultiBufferSource().getBuffer(RenderType.entityTranslucent(skin.texture())), LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, color);
        ps.popPose();
    }

    static void onRenderHand(RenderHandEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && ClientAfterlife.isSpirit(mc.player)) e.setCanceled(true);
    }

    private static void pose(PlayerModel<AbstractClientPlayer> m, float age, float headYaw, float pitch, float swing, float amount) {
        for (ModelPart part : new ModelPart[] {m.head, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg}) {
            part.xRot = 0F;
            part.yRot = 0F;
            part.zRot = 0F;
        }
        m.young = false;
        m.crouching = false;
        m.riding = false;
        m.head.yRot = headYaw * Mth.DEG_TO_RAD;
        m.head.xRot = pitch * Mth.DEG_TO_RAD;
        m.rightArm.xRot = 0.15F + Mth.cos(swing * 0.6662F) * 0.4F * amount + Mth.sin(age * 0.09F) * 0.05F;
        m.leftArm.xRot = 0.15F - Mth.cos(swing * 0.6662F) * 0.4F * amount - Mth.sin(age * 0.09F) * 0.05F;
        m.rightArm.zRot = 0.12F;
        m.leftArm.zRot = -0.12F;
        m.rightLeg.xRot = 0.25F + Mth.cos(swing * 0.6662F) * 0.3F * amount;
        m.leftLeg.xRot = 0.25F - Mth.cos(swing * 0.6662F) * 0.3F * amount;
        m.hat.copyFrom(m.head);
        m.jacket.copyFrom(m.body);
        m.rightSleeve.copyFrom(m.rightArm);
        m.leftSleeve.copyFrom(m.leftArm);
        m.rightPants.copyFrom(m.rightLeg);
        m.leftPants.copyFrom(m.leftLeg);
    }

    private SpiritRender() {}
}
