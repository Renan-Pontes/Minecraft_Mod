package com.renanpontes.isekaimahou.feature.class_quincy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.class_quincy.entity.SpiritArrowEntity;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Flecha Espiritual: a mesma geometria cruzada da flecha vanilla, mas emissiva (sempre acesa) e com um halo maior e
 * translúcido em volta — uma lança de luz azul.
 */
public class SpiritArrowRenderer extends EntityRenderer<SpiritArrowEntity> {
    private static final ResourceLocation TEXTURE = IsekaiMahou.id("textures/entity/quincy_spirit_arrow.png");

    public SpiritArrowRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0F;
    }

    @Override
    public void render(SpiritArrowEntity arrow, float yaw, float partialTicks, PoseStack ps, MultiBufferSource buffers, int light) {
        trail(arrow, partialTicks, ps, buffers);
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTicks, arrow.yRotO, arrow.getYRot()) - 90.0F));
        ps.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partialTicks, arrow.xRotO, arrow.getXRot())));
        float s = arrow.size();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
        // núcleo
        ps.pushPose();
        ps.scale(s, s, s);
        draw(ps, vc, 0xFFFFFFFF);
        ps.popPose();
        // halo: maior e quase transparente, pulsando
        float pulse = 1.45F + 0.1F * Mth.sin((arrow.tickCount + partialTicks) * 0.6F);
        ps.pushPose();
        ps.scale(s * pulse, s * pulse * 1.2F, s * pulse * 1.2F);
        draw(ps, vc, 0x5A9FD8FF);
        ps.popPose();
        ps.popPose();
        super.render(arrow, yaw, partialTicks, ps, buffers, light);
    }

    /**
     * Rastro de luz: uma fita aditiva virada para a câmera, da flecha para trás ao longo da velocidade (mais longa
     * quanto mais rápida), e um brilho redondo na ponta.
     */
    private void trail(SpiritArrowEntity arrow, float pt, PoseStack ps, MultiBufferSource buffers) {
        net.minecraft.world.phys.Vec3 v = arrow.getDeltaMovement();
        double speed = v.length();
        float s = arrow.size();
        net.minecraft.world.phys.Vec3 toCam = this.entityRenderDispatcher.camera.getPosition().subtract(arrow.getPosition(pt));
        org.joml.Matrix4f m = ps.last().pose();
        VertexConsumer glow = buffers.getBuffer(QuincyRenderTypes.additive(QuincyRenderTypes.GLOW));
        org.joml.Vector3f left = this.entityRenderDispatcher.camera.getLeftVector(), up = this.entityRenderDispatcher.camera.getUpVector();
        float pulse = 0.35F + 0.06F * Mth.sin((arrow.tickCount + pt) * 0.9F);
        QuincyRenderTypes.billboard(glow, m, left, up, 0, 0, 0, pulse * s, pulse * s, 0, QuincyRenderTypes.argb(0x8FD8FF, 0.85F));
        QuincyRenderTypes.billboard(glow, m, left, up, 0, 0, 0, pulse * s * 0.4F, pulse * s * 0.4F, 0, QuincyRenderTypes.argb(0xFFFFFF, 0.9F));
        if (speed < 0.05 || arrow.tickCount < 1) return;
        net.minecraft.world.phys.Vec3 dir = v.scale(1 / speed);
        float len = (float) Math.min(6.0, speed * 2.2) * Math.min(1F, (arrow.tickCount + pt) / 3F);
        net.minecraft.world.phys.Vec3 side = dir.cross(toCam);
        if (side.lengthSqr() < 1.0E-6) return;
        side = side.normalize().scale(0.13 * s);
        net.minecraft.world.phys.Vec3 tail = dir.scale(-len);
        VertexConsumer vc = buffers.getBuffer(QuincyRenderTypes.additive(QuincyRenderTypes.STREAK));
        int col = QuincyRenderTypes.argb(0x9FDcFF, 0.9F), core = QuincyRenderTypes.argb(0xFFFFFF, 0.8F);
        for (int k = 0; k < 2; k++) {
            net.minecraft.world.phys.Vec3 sd = k == 0 ? side : side.scale(0.4);
            int c = k == 0 ? col : core;
            vc.addVertex(m, (float) (tail.x - sd.x), (float) (tail.y - sd.y), (float) (tail.z - sd.z)).setUv(0, 0).setColor(c);
            vc.addVertex(m, (float) -sd.x, (float) -sd.y, (float) -sd.z).setUv(1, 0).setColor(c);
            vc.addVertex(m, (float) sd.x, (float) sd.y, (float) sd.z).setUv(1, 1).setColor(c);
            vc.addVertex(m, (float) (tail.x + sd.x), (float) (tail.y + sd.y), (float) (tail.z + sd.z)).setUv(0, 1).setColor(c);
        }
    }

    /** Planos da flecha (lateral 16×5 + traseira 5×5), como no ArrowRenderer. */
    private static void draw(PoseStack ps, VertexConsumer vc, int argb) {
        ps.pushPose();
        ps.mulPose(Axis.XP.rotationDegrees(45.0F));
        ps.scale(0.05625F, 0.05625F, 0.05625F);
        ps.translate(-4.0F, 0.0F, 0.0F);
        PoseStack.Pose pose = ps.last();
        vertex(pose, vc, -7, -2, -2, 0.0F, 0.15625F, -1, 0, 0, argb);
        vertex(pose, vc, -7, -2, 2, 0.15625F, 0.15625F, -1, 0, 0, argb);
        vertex(pose, vc, -7, 2, 2, 0.15625F, 0.3125F, -1, 0, 0, argb);
        vertex(pose, vc, -7, 2, -2, 0.0F, 0.3125F, -1, 0, 0, argb);
        vertex(pose, vc, -7, 2, -2, 0.0F, 0.15625F, 1, 0, 0, argb);
        vertex(pose, vc, -7, 2, 2, 0.15625F, 0.15625F, 1, 0, 0, argb);
        vertex(pose, vc, -7, -2, 2, 0.15625F, 0.3125F, 1, 0, 0, argb);
        vertex(pose, vc, -7, -2, -2, 0.0F, 0.3125F, 1, 0, 0, argb);
        for (int j = 0; j < 4; j++) {
            ps.mulPose(Axis.XP.rotationDegrees(90.0F));
            PoseStack.Pose p = ps.last();
            vertex(p, vc, -8, -2, 0, 0.0F, 0.0F, 0, 1, 0, argb);
            vertex(p, vc, 8, -2, 0, 0.5F, 0.0F, 0, 1, 0, argb);
            vertex(p, vc, 8, 2, 0, 0.5F, 0.15625F, 0, 1, 0, argb);
            vertex(p, vc, -8, 2, 0, 0.0F, 0.15625F, 0, 1, 0, argb);
        }
        ps.popPose();
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vc, int x, int y, int z, float u, float v, int nx, int ny, int nz, int argb) {
        vc.addVertex(pose, x, y, z).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(pose, nx, nz, ny);
    }

    @Override
    public ResourceLocation getTextureLocation(SpiritArrowEntity arrow) {
        return TEXTURE;
    }
}
