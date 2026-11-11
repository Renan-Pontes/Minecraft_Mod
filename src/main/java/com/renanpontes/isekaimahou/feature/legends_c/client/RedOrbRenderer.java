package com.renanpontes.isekaimahou.feature.legends_c.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.legends_c.LcFx;
import com.renanpontes.isekaimahou.feature.legends_c.entity.RedOrbEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;

/**
 * Vermelho: antes de explodir, um ponto vermelho incandescente com raios girando e rastro; ao explodir, uma casca
 * vermelha que se expande (onda de repulsão) com distorção e um anel no chão.
 */
public class RedOrbRenderer extends EntityRenderer<RedOrbEntity> {
    private static final ResourceLocation SWIRL = LcRenderTypes.tex("swirl");

    public RedOrbRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0F;
    }

    @Override
    public void render(RedOrbEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        float age = e.tickCount + pt;
        Vec3 pos = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
        Vec3 camRel = entityRenderDispatcher.camera.getPosition().subtract(pos);
        if (e.burstAge() >= 0) renderBurst(e, e.burstAge() + pt, camRel, pose, buffers);
        else renderOrb(e, age, camRel, pose, buffers);
        super.render(e, yaw, pt, pose, buffers, light);
    }

    private void renderOrb(RedOrbEntity e, float age, Vec3 camRel, PoseStack pose, MultiBufferSource buffers) {
        float r = e.size() * Mth.clamp(age / 4F, 0.3F, 1F) * (1F + 0.12F * Mth.sin(age * 1.7F));
        Matrix4f m = pose.last().pose();
        VertexConsumer g = buffers.getBuffer(LcRenderTypes.GLOW);
        LcDraw.glowSphere(g, m, r * 0.5F, 8, 12, 0xFFFFFF, 1F, 0.6F, camRel);
        LcDraw.glowSphere(g, m, r, 10, 16, LcFx.RED, 0.4F, 1F, camRel);

        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-age * 30F));
        pose.mulPose(Axis.XP.rotationDegrees(age * 11F));
        VertexConsumer sw = buffers.getBuffer(RenderType.energySwirl(SWIRL, (age * 0.06F) % 1F, (age * 0.04F) % 1F));
        LcDraw.texSphere(sw, pose.last(), r * 1.25F, 10, 16, LcDraw.swirl(LcFx.RED, 1F), 3F, 0.12F, age);
        pose.popPose();

        // Raios de repulsão girando (de frente para a câmera)
        g = buffers.getBuffer(LcRenderTypes.GLOW);
        pose.pushPose();
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        pose.mulPose(Axis.ZP.rotationDegrees(age * 9F));
        Matrix4f bm = pose.last().pose();
        LcDraw.halo(g, bm, r * 0.6F, r * 1.2F, r * 4.2F, LcFx.RED, 0.7F, 32);
        for (int i = 0; i < 8; i++) {
            float a = i * Mth.TWO_PI / 8F;
            float len = r * (3.2F + 1.2F * Mth.sin(age * 0.8F + i));
            Vec3 tip = new Vec3(Mth.cos(a) * len, Mth.sin(a) * len, 0);
            Vec3 side = new Vec3(-Mth.sin(a), Mth.cos(a), 0);
            LcDraw.softRibbon(g, bm, Vec3.ZERO, tip, side, r * 0.25F, 0.01F, LcFx.RED, 0.7F, 0F);
        }
        pose.popPose();

        // Rastro do voo
        Vec3 v = e.getDeltaMovement();
        if (v.lengthSqr() > 0.01) {
            Vec3 tail = v.normalize().scale(-Math.min(6.0, v.length() * 3.0));
            Vec3 side = Vec3.ZERO.subtract(camRel).cross(tail);
            side = side.lengthSqr() < 1.0E-8 ? new Vec3(0, 1, 0) : side.normalize();
            LcDraw.softRibbon(g, m, Vec3.ZERO, tail, side, r * 0.9F, 0.02F, LcFx.RED, 0.8F, 0F);
            LcDraw.softRibbon(g, m, Vec3.ZERO, tail.scale(0.6), side, r * 0.3F, 0.01F, 0xFFFFFF, 0.8F, 0F);
        }
    }

    private void renderBurst(RedOrbEntity e, float age, Vec3 camRel, PoseStack pose, MultiBufferSource buffers) {
        float t = Mth.clamp(age / RedOrbEntity.BURST_TICKS, 0F, 1F);
        float ease = 1F - (1F - t) * (1F - t) * (1F - t);
        float r = e.radius() * (0.15F + 1.1F * ease);
        float a = 1F - t;
        Matrix4f m = pose.last().pose();
        VertexConsumer g = buffers.getBuffer(LcRenderTypes.GLOW);
        LcDraw.glowSphere(g, m, r, 16, 26, LcFx.RED, 0.06F * a, 0.95F * a, camRel);
        LcDraw.glowSphere(g, m, r * 0.7F, 12, 20, 0xFFC0C0, 0F, 0.5F * a * a, camRel);

        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(age * 14F));
        VertexConsumer sw = buffers.getBuffer(RenderType.energySwirl(SWIRL, (age * 0.08F) % 1F, (age * 0.05F) % 1F));
        LcDraw.texSphere(sw, pose.last(), r * 0.92F, 14, 24, LcDraw.swirl(LcFx.RED_DEEP, a), 4F, 0.1F, age);
        pose.popPose();

        g = buffers.getBuffer(LcRenderTypes.GLOW);
        LcDraw.ringFlat(g, m, r * 1.1F, 0.3F + r * 0.15F, LcFx.RED, 0.9F * a, 64);
        LcDraw.ringFlat(g, m, r * 0.8F, 0.15F + r * 0.06F, 0xFFFFFF, 0.6F * a, 48);
        if (age < 4F) {
            pose.pushPose();
            pose.mulPose(entityRenderDispatcher.cameraOrientation());
            float fa = 1F - age / 4F;
            LcDraw.halo(g, pose.last().pose(), 0F, e.radius() * 0.6F, e.radius() * 1.8F, 0xFFFFFF, 0.9F * fa, 32);
            pose.popPose();
        }
    }

    @Override
    public ResourceLocation getTextureLocation(RedOrbEntity e) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
