package com.renanpontes.isekaimahou.feature.legends_f.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.feature.legends_f.LfNet;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Camada nos jogadores: as MOTOSSERRAS do Pacto da Motosserra (uma saindo da testa, uma de cada antebraço, com a
 * corrente de dentes correndo e o motor laranja) e o pigmento laranja do Modo Sábio em volta dos olhos.
 * Lê os estados sincronizados pelo servidor ({@link LfFxClient#has}).
 */
final class PlayerLayers {
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            EntityRenderer<? extends Player> r = event.getSkin(skin);
            if (r instanceof LivingEntityRenderer lr && lr.getModel() instanceof PlayerModel) lr.addLayer(new Layer(lr));
        }
    }

    static final class Layer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        Layer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffer, int light, AbstractClientPlayer player, float limbSwing,
                           float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (player.isInvisible()) return;
            boolean saw = LfFxClient.has(player.getId(), LfNet.S_CHAINSAW);
            boolean sage = LfFxClient.has(player.getId(), LfNet.S_SAGE);
            if (!saw && !sage) return;
            PlayerModel<AbstractClientPlayer> model = getParentModel();
            if (saw) {
                VertexConsumer solid = buffer.getBuffer(RenderType.entityCutoutNoCull(LfRenderTypes.WHITE));
                float run = ageInTicks * 0.9F;
                // Cabeça: lâmina saindo da testa, para frente
                pose.pushPose();
                model.head.translateAndRotate(pose);
                pose.translate(0, -0.32, -0.25);
                saw(solid, pose, light, run, 0.62F, true);
                pose.popPose();
                // Braços: lâminas saindo dos antebraços, para baixo/frente
                for (ModelPart arm : new ModelPart[] {model.rightArm, model.leftArm}) {
                    pose.pushPose();
                    arm.translateAndRotate(pose);
                    pose.translate(arm == model.rightArm ? -0.06 : 0.06, 0.45, 0);
                    pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90F));
                    saw(solid, pose, light, run, 0.72F, false);
                    pose.popPose();
                }
            }
            if (sage) {
                VertexConsumer glow = buffer.getBuffer(LfRenderTypes.GLOW);
                pose.pushPose();
                model.head.translateAndRotate(pose);
                Matrix4f m = pose.last().pose();
                float z = -0.2515F;
                for (int s = -1; s <= 1; s += 2) {
                    float cx = s * 0.125F;
                    // Pigmento laranja em volta do olho
                    quad(glow, m, cx - 0.1F, -0.32F, cx + 0.1F, -0.2F, z, LfDraw.argb(0xF08A24, 0.85F));
                    // Pupila horizontal de sapo
                    quad(glow, m, cx - 0.05F, -0.27F, cx + 0.05F, -0.25F, z - 0.001F, LfDraw.argb(0xFFE070, 1F));
                }
                pose.popPose();
            }
        }

        /** Uma motosserra: motor laranja, lâmina cinza e a corrente de dentes correndo. Comprimento {@code len} (em -Z). */
        private static void saw(VertexConsumer vc, PoseStack pose, int light, float run, float len, boolean head) {
            PoseStack.Pose p = pose.last();
            int orange = 0xFFFF7A1A, grey = 0xFFB8BEC6, dark = 0xFF2A2A30, steel = 0xFF8A9098;
            // Motor (bloco laranja) na base
            LfDraw.box(vc, p, -0.07F, -0.09F, -0.02F, 0.07F, 0.09F, 0.12F, orange, light, 1F);
            LfDraw.box(vc, p, -0.075F, -0.03F, 0.02F, 0.075F, 0.03F, 0.08F, dark, light, 1F);
            // Lâmina
            LfDraw.box(vc, p, -0.015F, -0.075F, -len, 0.015F, 0.075F, -0.02F, grey, light, 1F);
            LfDraw.box(vc, p, -0.017F, -0.02F, -len + 0.04F, 0.017F, 0.02F, -0.04F, steel, light, 1F);
            // Dentes correndo (em cima e embaixo da lâmina)
            int teeth = (int) (len / 0.06F);
            float off = (run * 0.06F) % 0.06F;
            for (int i = 0; i < teeth; i++) {
                float zc = -0.04F - i * 0.06F - off;
                if (zc < -len + 0.02F) continue;
                LfDraw.box(vc, p, -0.02F, 0.075F, zc - 0.02F, 0.02F, 0.1F, zc + 0.01F, dark, light, 1F);
                LfDraw.box(vc, p, -0.02F, -0.1F, zc - 0.01F, 0.02F, -0.075F, zc + 0.02F, dark, light, 1F);
            }
            // Ponta arredondada
            LfDraw.box(vc, p, -0.02F, -0.06F, -len - 0.04F, 0.02F, 0.06F, -len, dark, light, 1F);
            if (head) LfDraw.box(vc, p, -0.03F, -0.12F, 0.0F, 0.03F, -0.09F, 0.1F, dark, light, 1F);
        }

        private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float z, int argb) {
            LfDraw.v(vc, m, x0, y0, z, argb);
            LfDraw.v(vc, m, x0, y1, z, argb);
            LfDraw.v(vc, m, x1, y1, z, argb);
            LfDraw.v(vc, m, x1, y0, z, argb);
        }
    }

    private PlayerLayers() {}
}
