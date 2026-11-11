package com.renanpontes.isekaimahou.feature.settlements.client;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.settlements.SettlementStyle;
import com.renanpontes.isekaimahou.feature.settlements.block.SettlementHeartBlockEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/**
 * Cristal do Coração da cidade do jogador: uma bipirâmide hexagonal alta girando sobre o pedestal, um halo de seis
 * lascas em órbita inclinada e um anel de runas no chão — na cor do estilo (azul-real, ouro do deserto, gelo do norte).
 */
public class SettlementHeartRenderer implements BlockEntityRenderer<SettlementHeartBlockEntity> {
    private static final ResourceLocation TEX = IsekaiMahou.id("textures/entity/settlement_heart_crystal.png");
    private static final int FULL_BRIGHT = 0xF000F0;

    public SettlementHeartRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(SettlementHeartBlockEntity be, float partialTick, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null) return;
        float t = (be.getLevel().getGameTime() % 24000L) + partialTick;
        int[] c = colors(be.style());
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(TEX));
        ps.pushPose();
        ps.translate(0.5, 1.65 + Mth.sin(t * 0.05F) * 0.07F, 0.5);
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(t * 1.2F));
        bipyramid(ps, vc, 0.24F, 0.62F, c[0], c[1], c[2], 220);
        ps.popPose();
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-t * 2.5F));
        bipyramid(ps, vc, 0.1F, 0.3F, c[3], c[4], c[5], 255);
        ps.popPose();
        // halo de lascas, inclinado e girando
        ps.pushPose();
        ps.mulPose(Axis.XP.rotationDegrees(18.0F));
        for (int i = 0; i < 6; i++) {
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(t * 1.8F + i * 60.0F));
            ps.translate(0.72, Mth.sin((t + i * 13) * 0.07F) * 0.05F, 0.0);
            ps.mulPose(Axis.ZP.rotationDegrees(t * 4.0F));
            bipyramid(ps, vc, 0.045F, 0.11F, c[6], c[7], c[8], 210);
            ps.popPose();
        }
        ps.popPose();
        ps.popPose();
        // anel de runas no topo do pedestal
        ps.pushPose();
        ps.translate(0.5, 0.83, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(-t * 0.6F));
        for (int i = 0; i < 12; i++) {
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(i * 30.0F));
            ps.translate(0.55, 0, 0);
            float pulse = 0.6F + 0.4F * Mth.sin(t * 0.1F + i);
            quad(ps, vc, 0.06F, (int) (c[6] * pulse), (int) (c[7] * pulse), (int) (c[8] * pulse), 200);
            ps.popPose();
        }
        ps.popPose();
    }

    private static int[] colors(SettlementStyle s) {
        return switch (s) {
            case DESERT -> new int[] {255, 196, 90, 255, 245, 210, 255, 215, 120};
            case NORDIC -> new int[] {180, 230, 255, 245, 255, 255, 205, 240, 255};
            default -> new int[] {110, 160, 255, 220, 235, 255, 255, 220, 120};
        };
    }

    private static void bipyramid(PoseStack ps, VertexConsumer vc, float r, float h, int cr, int cg, int cb, int a) {
        PoseStack.Pose pose = ps.last();
        Vector3f top = new Vector3f(0, h, 0), bottom = new Vector3f(0, -h * 0.7F, 0);
        Vector3f[] ring = new Vector3f[6];
        for (int i = 0; i < 6; i++) {
            double ang = Math.PI / 3 * i;
            ring[i] = new Vector3f((float) Math.cos(ang) * r, 0, (float) Math.sin(ang) * r);
        }
        for (int i = 0; i < 6; i++) {
            Vector3f p0 = ring[i], p1 = ring[(i + 1) % 6];
            float shade = i % 2 == 0 ? 1.0F : 0.85F;
            tri(pose, vc, top, p1, p0, (int) (cr * shade), (int) (cg * shade), (int) (cb * shade), a);
            tri(pose, vc, bottom, p0, p1, (int) (cr * 0.7F), (int) (cg * 0.72F), cb, a);
        }
    }

    private static void quad(PoseStack ps, VertexConsumer vc, float s, int r, int g, int b, int a) {
        PoseStack.Pose pose = ps.last();
        Vector3f n = new Vector3f(0, 1, 0);
        vertex(pose, vc, new Vector3f(-s, 0, -s), 0, 0, n, r, g, b, a);
        vertex(pose, vc, new Vector3f(-s, 0, s), 0, 1, n, r, g, b, a);
        vertex(pose, vc, new Vector3f(s, 0, s), 1, 1, n, r, g, b, a);
        vertex(pose, vc, new Vector3f(s, 0, -s), 1, 0, n, r, g, b, a);
    }

    private static void tri(PoseStack.Pose pose, VertexConsumer vc, Vector3f a, Vector3f b, Vector3f c, int r, int g, int bl, int al) {
        Vector3f n = new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a)).normalize();
        vertex(pose, vc, a, 0.5F, 0.0F, n, r, g, bl, al);
        vertex(pose, vc, b, 0.0F, 1.0F, n, r, g, bl, al);
        vertex(pose, vc, c, 1.0F, 1.0F, n, r, g, bl, al);
        vertex(pose, vc, c, 1.0F, 1.0F, n, r, g, bl, al);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vc, Vector3f p, float u, float v, Vector3f n, int r, int g, int b, int a) {
        vc.addVertex(pose, p.x(), p.y(), p.z()).setColor(r, g, b, a).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT).setNormal(pose, n.x(), n.y(), n.z());
    }

    @Override
    public AABB getRenderBoundingBox(SettlementHeartBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(1.0).expandTowards(0.0, 2.0, 0.0);
    }
}
