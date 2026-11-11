package com.renanpontes.isekaimahou.feature.fusions_b.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.fusions_b.entity.VoidRiftEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * Rasgo do Vazio: uma fenda vertical de bordas irregulares (o interior é o céu estrelado do portal do End)
 * com uma borda roxa brilhante. Abre a partir de um risco fino e fecha do mesmo jeito.
 */
public class VoidRiftRenderer extends EntityRenderer<VoidRiftEntity> {
    private static final int ROWS = 14;
    private static final float MAX_HALF_WIDTH = 0.55F;
    private static final float EDGE = 0.16F;

    public VoidRiftRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(VoidRiftEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        float open = e.openness(pt, 8);
        if (open <= 0) return;
        float t = e.tickCount + pt;
        float h = VoidRiftEntity.HEIGHT;
        float[] cx = new float[ROWS + 1];
        float[] hw = new float[ROWS + 1];
        float[] y = new float[ROWS + 1];
        for (int i = 0; i <= ROWS; i++) {
            float f = i / (float) ROWS;
            y[i] = f * h;
            // Lente afinada nas pontas + bordas que "respiram"
            float jag = 0.75F + 0.25F * Mth.sin(i * 2.1F + t * 0.25F) * Mth.cos(i * 1.3F - t * 0.11F);
            hw[i] = MAX_HALF_WIDTH * Mth.sin((float) Math.PI * f) * open * jag;
            cx[i] = 0.09F * Mth.sin(i * 2.7F + e.getId()) + 0.04F * Mth.sin(t * 0.1F + i);
        }
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-e.getYRot()));
        Matrix4f m = pose.last().pose();
        VertexConsumer voidVc = buffers.getBuffer(RenderType.endGateway());
        for (int i = 0; i < ROWS; i++) {
            FbRender.quad(voidVc, m,
                    cx[i] - hw[i], y[i], 0,
                    cx[i] + hw[i], y[i], 0,
                    cx[i + 1] + hw[i + 1], y[i + 1], 0,
                    cx[i + 1] - hw[i + 1], y[i + 1], 0);
        }
        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
        int inner = FbRender.alpha(0xB070FF, 0.9F * open);
        int outer = FbRender.alpha(0x5A2A8A, 0F);
        for (int i = 0; i < ROWS; i++) {
            // Borda esquerda e direita
            for (int side = -1; side <= 1; side += 2) {
                float x0 = cx[i] + side * hw[i], x1 = cx[i + 1] + side * hw[i + 1];
                FbRender.colorQuad(glow, m,
                        x0, y[i], 0, inner,
                        x0 + side * EDGE, y[i], 0, outer,
                        x1 + side * EDGE, y[i + 1], 0, outer,
                        x1, y[i + 1], 0, inner);
            }
        }
        pose.popPose();
        super.render(e, yaw, pt, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(VoidRiftEntity e) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
