package com.renanpontes.isekaimahou.feature.fusions_c.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.feature.fusions_c.entity.PollenCloudEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * Tempestade de Pólen: um redemoinho de pétalas (rosa, amarelas), folhas e bolinhas de pólen girando em espiral em volta
 * do centro, sobre uma névoa dourada em forma de domo. Abre e se dissipa suavemente.
 */
public class PollenCloudRenderer extends EntityRenderer<PollenCloudEntity> {
    private static final int PETALS = 44;
    private static final int[] TINTS = {0xFFFFFF, 0xFFF0F6, 0xFFF8D0, 0xF0FFE8};

    public PollenCloudRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public boolean shouldRender(PollenCloudEntity e, Frustum frustum, double x, double y, double z) {
        return frustum.isVisible(e.getBoundingBox().inflate(e.scale() + 2));
    }

    @Override
    public void render(PollenCloudEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        float t = e.tickCount + pt;
        float r = e.scale();
        float open = e.openness(pt, 15);
        if (open <= 0) return;
        // Névoa dourada (domo baixo)
        ps.pushPose();
        FcQuads.sphere(ps, FcQuads.glow(buffers, FcQuads.NOISE), r * open, 2.4F * open, 0, 0, 3, 1, t * 0.01F, 0xFFE07A, 0.18F * open, 8, 18);
        ps.popPose();
        // Pétalas em espiral: cada uma tem raio, altura e velocidade próprios (semente fixa por índice)
        VertexConsumer vc = FcQuads.glow(buffers, FcQuads.PETALS);
        java.util.Random rnd = new java.util.Random(e.getId() * 131L);
        for (int i = 0; i < PETALS; i++) {
            float pr = r * (0.2F + 0.8F * (float) Math.sqrt(rnd.nextFloat())) * open;
            float speed = 0.04F + 0.05F * rnd.nextFloat();
            float phase = rnd.nextFloat() * Mth.TWO_PI;
            float baseY = 0.3F + rnd.nextFloat() * 2.6F;
            int cell = rnd.nextInt(4);
            float size = 0.12F + rnd.nextFloat() * 0.14F;
            float a = phase + t * speed;
            float y = baseY + 0.35F * Mth.sin(t * 0.07F + phase);
            ps.pushPose();
            ps.translate(Mth.cos(a) * pr, y, Mth.sin(a) * pr);
            ps.mulPose(this.entityRenderDispatcher.cameraOrientation());
            float u0 = (cell % 2) * 0.5F, v0 = (cell / 2) * 0.5F;
            FcQuads.billboardUv(ps, vc, size, t * (4 + i % 5) + i * 40, u0, v0, u0 + 0.5F, v0 + 0.5F, TINTS[i % TINTS.length], 0.95F * open);
            ps.popPose();
        }
        super.render(e, yaw, pt, ps, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(PollenCloudEntity e) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
