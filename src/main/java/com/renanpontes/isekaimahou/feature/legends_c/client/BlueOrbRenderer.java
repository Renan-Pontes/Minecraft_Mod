package com.renanpontes.isekaimahou.feature.legends_c.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.legends_c.LcFx;
import com.renanpontes.isekaimahou.feature.legends_c.entity.BlueOrbEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;

/**
 * Azul: miolo escuro que "engole a luz", casca de energia azul girando rápido, dois discos de acreção inclinados,
 * riscos de luz sendo SUGADOS de longe para dentro e um halo. Cresce ao abrir e encolhe até implodir.
 */
public class BlueOrbRenderer extends EntityRenderer<BlueOrbEntity> {
    private static final ResourceLocation SWIRL = LcRenderTypes.tex("swirl");
    private static final int STREAKS = 30;

    public BlueOrbRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0F;
    }

    @Override
    public void render(BlueOrbEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        float age = e.tickCount + pt;
        int life = e.life();
        float grow = Mth.clamp(age / 6F, 0F, 1F);
        float shrink = Mth.clamp((life - age) / 8F, 0F, 1F);
        float r = e.size() * grow * (0.4F + 0.6F * shrink) * (1F + 0.06F * Mth.sin(age * 0.9F));
        if (r <= 0.02F) return;
        Vec3 pos = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
        Vec3 camRel = entityRenderDispatcher.camera.getPosition().subtract(pos);
        Matrix4f m = pose.last().pose();

        // Miolo que apaga a luz
        LcDraw.flatSphere(buffers.getBuffer(LcRenderTypes.DARKEN), m, r * 0.55F, 10, 16, LcDraw.shade(0.08F));

        VertexConsumer g = buffers.getBuffer(LcRenderTypes.GLOW);
        LcDraw.glowSphere(g, m, r * 0.72F, 10, 18, LcFx.BLUE, 0.15F, 1F, camRel);

        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(age * 24F));
        pose.mulPose(Axis.ZP.rotationDegrees(20F));
        VertexConsumer sw = buffers.getBuffer(RenderType.energySwirl(SWIRL, (age * 0.05F) % 1F, (age * 0.03F) % 1F));
        LcDraw.texSphere(sw, pose.last(), r, 12, 20, LcDraw.swirl(LcFx.BLUE, 0.9F), 3F, 0.08F, age);
        pose.popPose();

        g = buffers.getBuffer(LcRenderTypes.GLOW);
        LcDraw.glowSphere(g, m, r * 1.15F, 10, 18, LcFx.BLUE_LIGHT, 0F, 0.8F, camRel);

        // Discos de acreção
        for (int i = 0; i < 2; i++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(age * (i == 0 ? 18F : -26F)));
            pose.mulPose(Axis.XP.rotationDegrees(i == 0 ? 18F : -32F));
            Matrix4f dm = pose.last().pose();
            LcDraw.ringFlat(g, dm, r * (1.8F + i * 0.5F), r * 0.45F, LcFx.BLUE, 0.55F * shrink, 48);
            LcDraw.ringFlat(g, dm, r * (1.5F + i * 0.4F), r * 0.12F, 0xFFFFFF, 0.5F * shrink, 40);
            pose.popPose();
        }

        // Riscos de luz sugados para o centro
        float pull = e.pull();
        RandomSource rnd = RandomSource.create(e.getId() * 7919L);
        for (int i = 0; i < STREAKS; i++) {
            Vec3 dir = LcDraw.randomOnSphere(rnd);
            float speed = 0.04F + rnd.nextFloat() * 0.05F;
            float ph = (age * speed + rnd.nextFloat()) % 1F;
            float dist = r + (pull * 0.9F - r) * (1F - ph * ph);
            float len = 0.6F + 1.6F * ph;
            Vec3 a = dir.scale(dist);
            Vec3 b = dir.scale(Math.max(r, dist - len));
            float al = Mth.clamp(ph * 1.4F, 0F, 1F) * 0.8F * grow;
            Vec3 side = a.subtract(camRel).cross(b.subtract(a));
            side = side.lengthSqr() < 1.0E-8 ? new Vec3(0, 1, 0) : side.normalize();
            LcDraw.softRibbon(g, m, a, b, side, 0.06F, 0.02F, LcFx.BLUE_LIGHT, al * 0.3F, al);
        }

        // Halo
        pose.pushPose();
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        LcDraw.halo(g, pose.last().pose(), r * 0.8F, r * 1.3F, r * 3.6F, LcFx.BLUE, 0.6F * grow, 32);
        pose.popPose();

        // Faíscas na superfície
        long seed = (long) (age / 2) * 977L + e.getId();
        RandomSource sr = RandomSource.create(seed);
        for (int k = 0; k < 2; k++) {
            Vec3 a = LcDraw.randomOnSphere(sr).scale(r * 1.05);
            Vec3 b = a.add(LcDraw.randomOnSphere(sr).scale(r)).normalize().scale(r * 1.4);
            LcDraw.arc(g, m, a, b, camRel, seed + k, r * 0.3F, 0.03F, LcFx.BLUE_LIGHT, 0.8F);
        }
        super.render(e, yaw, pt, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(BlueOrbEntity e) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
