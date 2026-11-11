package com.renanpontes.isekaimahou.feature.hueco_mundo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmRealm;
import com.renanpontes.isekaimahou.feature.hueco_mundo.entity.RealmGate;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Desenho dos portais dos reinos.
 * <ul>
 *   <li><b>Garganta</b>: rasgo negro denteado aberto no ar (descendo até o chão), com borda azul-pálida que pulsa; abre
 *       de cima para baixo e alarga como um tecido sendo rasgado. Sempre de frente para quem olha.</li>
 *   <li><b>Círculo das Fadas</b>: anel de runas e pétalas girando no chão + uma coluna de luz suave.</li>
 * </ul>
 */
public class RealmGateRenderer extends EntityRenderer<RealmGate> {
    private static final ResourceLocation RIFT = IsekaiMahou.id("textures/entity/garganta_rift.png");
    private static final ResourceLocation RIFT_EDGE = IsekaiMahou.id("textures/entity/garganta_rift_edge.png");
    private static final ResourceLocation RING = IsekaiMahou.id("textures/entity/fey_ring_gate.png");
    private static final ResourceLocation BEAM = IsekaiMahou.id("textures/entity/fey_beam.png");

    public RealmGateRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(RealmGate gate, float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float open = gate.openness(partialTick);
        if (open <= 0.01F) return;
        float t = gate.tickCount + partialTick;
        if (gate.realm() == HmRealm.HOLLOW) {
            renderRift(pose, buffers, open, t);
        } else {
            renderRing(pose, buffers, open, t);
        }
        super.render(gate, entityYaw, partialTick, pose, buffers, light);
    }

    private void renderRift(PoseStack pose, MultiBufferSource buffers, float open, float t) {
        Camera cam = entityRenderDispatcher.camera;
        pose.pushPose();
        // Gira só no eixo Y para ficar de frente para a câmera (o rasgo continua de pé).
        pose.mulPose(Axis.YP.rotationDegrees(180F - cam.getYRot()));
        float height = 5.2F * Mth.clamp(open * 1.4F, 0F, 1F);
        float width = 2.4F * open * (1F + 0.04F * Mth.sin(t * 0.35F));
        float top = 5.6F;
        float bottom = top - height;
        PoseStack.Pose p = pose.last();
        quad(buffers.getBuffer(RenderType.entityTranslucent(RIFT)), p, -width / 2, bottom, width / 2, top, 0xFFFFFFFF);
        int pulse = (int) (170 + 85 * Mth.sin(t * 0.25F));
        int edge = (Mth.clamp(pulse, 0, 255) << 24) | 0xB8D8FF;
        float ew = width * 1.25F;
        quad(buffers.getBuffer(RenderType.entityTranslucentEmissive(RIFT_EDGE)), p, -ew / 2, bottom - 0.2F, ew / 2, top + 0.2F, edge);
        pose.popPose();
    }

    private void renderRing(PoseStack pose, MultiBufferSource buffers, float open, float t) {
        float r = 1.8F * open;
        pose.pushPose();
        pose.translate(0, 0.06, 0);
        pose.mulPose(Axis.YP.rotationDegrees(t * 1.5F));
        pose.mulPose(Axis.XP.rotationDegrees(90F));
        quad(buffers.getBuffer(RenderType.entityTranslucentEmissive(RING)), pose.last(), -r, -r, r, r, 0xFFF6C8F0);
        pose.popPose();
        Camera cam = entityRenderDispatcher.camera;
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180F - cam.getYRot()));
        int a = (int) (140 * open + 40 * Mth.sin(t * 0.2F));
        quad(buffers.getBuffer(RenderType.entityTranslucentEmissive(BEAM)), pose.last(), -1.3F * open, 0F, 1.3F * open, 4.5F * open,
                (Mth.clamp(a, 0, 255) << 24) | 0xFFE6FA);
        pose.popPose();
    }

    /** Retângulo em pé no plano XY (u: esquerda→direita, v: topo→base). */
    private static void quad(VertexConsumer vc, PoseStack.Pose p, float x0, float y0, float x1, float y1, int argb) {
        vertex(vc, p, x0, y0, 0, 1, argb);
        vertex(vc, p, x1, y0, 1, 1, argb);
        vertex(vc, p, x1, y1, 1, 0, argb);
        vertex(vc, p, x0, y1, 0, 0, argb);
        // Verso (visto de trás, sem cull).
        vertex(vc, p, x0, y1, 0, 0, argb);
        vertex(vc, p, x1, y1, 1, 0, argb);
        vertex(vc, p, x1, y0, 1, 1, argb);
        vertex(vc, p, x0, y0, 0, 1, argb);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float u, float v, int argb) {
        vc.addVertex(pose, x, y, 0.0F).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0.0F, 0.0F, 1.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(RealmGate gate) {
        return gate.realm() == HmRealm.HOLLOW ? RIFT : RING;
    }
}
