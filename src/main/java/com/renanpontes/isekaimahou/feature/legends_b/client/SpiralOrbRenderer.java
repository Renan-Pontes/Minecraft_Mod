package com.renanpontes.isekaimahou.feature.legends_b.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.legends_b.entity.SpiralOrbEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;

/**
 * Esfera Espiral: núcleo branco, duas cascas de vento em redemoinho girando em eixos diferentes, borda brilhante,
 * três anéis orbitando rápido e um halo. Na mão ela é desenhada exatamente no ponto da mão (sem atraso).
 * Triturando: pulsa maior e mais forte.
 */
public class SpiralOrbRenderer extends EntityRenderer<SpiralOrbEntity> {
    private static final ResourceLocation SWIRL = LbRenderTypes.tex("spiral_swirl");

    public SpiralOrbRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(SpiralOrbEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        float ageF = e.age() + pt;
        float time = e.tickCount + pt;
        float f = e.formation(ageF);
        f = f * f * (3 - 2 * f);
        if (f <= 0.01F) return;
        Vec3 entPos = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
        Vec3 center = entPos;
        if (e.isHeld() && e.state() != SpiralOrbEntity.STATE_THROWN && e.ownerEntity() instanceof LivingEntity owner) {
            center = SpiralOrbEntity.handPoint(owner, pt);
        }
        boolean grind = e.state() == SpiralOrbEntity.STATE_GRIND;
        float r = e.radius() * f * (grind ? 1.22F + 0.07F * Mth.sin(time * 2.6F) : 1F + 0.04F * Mth.sin(time * 0.8F));
        int rgb = e.color();
        int bright = LbDraw.lighten(rgb, 0.6F);
        Vec3 camRel = entityRenderDispatcher.camera.getPosition().subtract(center);

        pose.pushPose();
        pose.translate(center.x - entPos.x, center.y - entPos.y, center.z - entPos.z);
        Matrix4f m = pose.last().pose();
        VertexConsumer g = buffers.getBuffer(LbRenderTypes.GLOW);
        LbDraw.glowSphere(g, m, r * 0.45F, 6, 10, 0xFFFFFF, grind ? 1F : 0.9F, 0.45F, camRel);

        // Cascas de vento (o redemoinho gira rápido em dois eixos)
        float spin = grind ? 2F : 1F;
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(time * 28F * spin));
        pose.mulPose(Axis.XP.rotationDegrees(time * 17F * spin));
        VertexConsumer sw = buffers.getBuffer(RenderType.energySwirl(SWIRL, (time * 0.08F) % 1F, (time * 0.05F) % 1F));
        LbDraw.texSphere(sw, pose.last(), r, 10, 16, LbDraw.swirl(LbDraw.lighten(rgb, 0.4F), 1F), 2F, 0.05F, time * 3F);
        pose.popPose();
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-time * 21F * spin));
        pose.mulPose(Axis.ZP.rotationDegrees(time * 11F * spin));
        VertexConsumer sw2 = buffers.getBuffer(RenderType.energySwirl(SWIRL, (-time * 0.06F) % 1F, (time * 0.09F) % 1F));
        LbDraw.texSphere(sw2, pose.last(), r * 1.12F, 10, 16, LbDraw.swirl(rgb, 0.7F), 3F, 0.07F, time * 3F + 20F);
        pose.popPose();

        g = buffers.getBuffer(LbRenderTypes.GLOW);
        LbDraw.glowSphere(g, m, r * 1.18F, 8, 14, bright, 0F, 0.75F, camRel);
        // Três anéis orbitando
        for (int k = 0; k < 3; k++) {
            pose.pushPose();
            pose.mulPose(Axis.XP.rotationDegrees(60F * k + 20F));
            pose.mulPose(Axis.YP.rotationDegrees(time * 35F * spin + k * 50F));
            LbDraw.ringFlat(g, pose.last().pose(), r * 1.32F, 0.04F + 0.03F * r, bright, 0.75F, 32);
            pose.popPose();
        }
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        LbDraw.halo(g, pose.last().pose(), r * 0.9F, r * 1.2F, r * (grind ? 3.0F : 2.4F), rgb, grind ? 0.55F : 0.35F, 24);
        pose.popPose();
        super.render(e, yaw, pt, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(SpiralOrbEntity e) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
