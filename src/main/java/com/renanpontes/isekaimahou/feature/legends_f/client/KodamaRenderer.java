package com.renanpontes.isekaimahou.feature.legends_f.client;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.legends_f.entity.KodamaEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Kodama: corpinho branco-leitoso, cabeça grande e redonda com três buracos escuros (dois olhos e a boca), bracinhos
 * finos. Anda balançando e, de vez em quando, gira a cabeça de um lado para o outro fazendo "toc-toc". Brilho fraco.
 */
public class KodamaRenderer extends EntityRenderer<KodamaEntity> {
    public KodamaRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.2F;
    }

    @Override
    public ResourceLocation getTextureLocation(KodamaEntity e) {
        return LfRenderTypes.WHITE;
    }

    @Override
    public void render(KodamaEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        float time = e.tickCount + pt;
        float body = Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot);
        float walk = e.walkAnimation.position(pt);
        float speed = Math.min(1F, e.walkAnimation.speed(pt));
        int white = 0xFFF2F2EA, shade = 0xFFDCDCD0, hole = 0xFF141414;
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-body));
        float bob = Math.abs(Mth.sin(walk * 0.8F)) * 0.05F * speed;
        pose.translate(0, bob, 0);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(LfRenderTypes.WHITE));
        PoseStack.Pose p = pose.last();
        // Pernas e corpo
        float sw = Mth.sin(walk * 0.8F) * 0.08F * speed;
        LfDraw.segment(vc, p, new Vec3(0.05, 0.14, 0), new Vec3(0.05, 0, sw), 0.03F, 0.03F, shade, light);
        LfDraw.segment(vc, p, new Vec3(-0.05, 0.14, 0), new Vec3(-0.05, 0, -sw), 0.03F, 0.03F, shade, light);
        LfDraw.cbox(vc, p, 0, 0.27F, 0, 0.09F, 0.14F, 0.07F, white, light);
        // Bracinhos
        LfDraw.segment(vc, p, new Vec3(0.09, 0.36, 0), new Vec3(0.15, 0.2, 0.03 - sw), 0.025F, 0.02F, shade, light);
        LfDraw.segment(vc, p, new Vec3(-0.09, 0.36, 0), new Vec3(-0.15, 0.2, 0.03 + sw), 0.025F, 0.02F, shade, light);
        // Cabeça (gira no toc-toc)
        int rattle = e.rattle();
        float tilt = rattle > 0 ? Mth.sin((rattle - pt) * 1.6F) * 28F : Mth.sin(time * 0.05F) * 4F;
        pose.pushPose();
        pose.translate(0, 0.47, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(tilt));
        pose.translate(0, 0.14, 0);
        PoseStack.Pose h = pose.last();
        LfDraw.ellipsoid(vc, h, 0.17F, 0.15F, 0.14F, 8, 12, white, light);
        LfDraw.cbox(vc, h, 0.06F, 0.02F, 0.13F, 0.025F, 0.03F, 0.02F, hole, light);
        LfDraw.cbox(vc, h, -0.06F, 0.02F, 0.13F, 0.025F, 0.03F, 0.02F, hole, light);
        LfDraw.cbox(vc, h, 0F, -0.07F, 0.125F, 0.02F, 0.025F, 0.02F, hole, light);
        pose.popPose();
        pose.popPose();
        // Brilho fraco
        VertexConsumer glow = buffers.getBuffer(LfRenderTypes.GLOW);
        var cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vector3f left = cam.getLeftVector(), up = cam.getUpVector();
        LfDraw.billboard(glow, pose.last().pose(), new Vec3(0, 0.45, 0), left, up, 0.45F,
                LfDraw.argb(0xE8FFE8, 0.12F + 0.05F * Mth.sin(time * 0.1F)), LfDraw.argb(0xE8FFE8, 0F));
        super.render(e, yaw, pt, pose, buffers, light);
    }
}
