package com.renanpontes.isekaimahou.feature.bosses_a.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.bosses_a.entity.ZephyrCurtain;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Cortina de Ventos: cada parede é uma faixa vertical translúcida (duas camadas levemente afastadas, com listras de
 * vento que correm em sentidos diferentes), mais transparente nas pontas. Brilho total para ser vista no escuro.
 */
public class CurtainRenderer extends EntityRenderer<ZephyrCurtain> {
    private static final ResourceLocation TEXTURE = IsekaiMahou.id("textures/entity/zephyr_curtain.png");
    private static final int SEGMENTS = 8;

    public CurtainRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0F;
    }

    @Override
    public void render(ZephyrCurtain curtain, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float age = curtain.age(partialTick);
        float fade = curtain.fade(age);
        if (fade <= 0.01F) return;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));
        PoseStack.Pose p = pose.last();
        float inner = curtain.inner(), outer = curtain.outer();
        // Ao surgir a parede "sobe" do chão
        float h = ZephyrCurtain.HEIGHT * Mth.clamp(age / 6F, 0.15F, 1F);
        for (int k = 0; k < curtain.walls(); k++) {
            double rad = Math.toRadians(curtain.wallYaw(k, age));
            // Mesma convenção do servidor (Vec3.directionFromRotation): (-sin, cos)
            float dx = (float) -Math.sin(rad), dz = (float) Math.cos(rad);
            float nx = -dz, nz = dx;
            for (int layer = -1; layer <= 1; layer += 2) {
                float off = layer * 0.28F;
                float scroll = age * (layer > 0 ? 0.045F : -0.032F);
                for (int s = 0; s < SEGMENTS; s++) {
                    float f0 = s / (float) SEGMENTS, f1 = (s + 1) / (float) SEGMENTS;
                    float r0 = Mth.lerp(f0, inner, outer), r1 = Mth.lerp(f1, inner, outer);
                    int a0 = alpha(f0, fade), a1 = alpha(f1, fade);
                    float u0 = f0 * 3F + scroll, u1 = f1 * 3F + scroll;
                    float x0 = dx * r0 + nx * off, z0 = dz * r0 + nz * off;
                    float x1 = dx * r1 + nx * off, z1 = dz * r1 + nz * off;
                    vertex(vc, p, x0, -0.2F, z0, u0, 1F, a0, nx, nz);
                    vertex(vc, p, x1, -0.2F, z1, u1, 1F, a1, nx, nz);
                    vertex(vc, p, x1, h, z1, u1, 0F, a1 / 3, nx, nz);
                    vertex(vc, p, x0, h, z0, u0, 0F, a0 / 3, nx, nz);
                }
            }
        }
        super.render(curtain, yaw, partialTick, pose, buffers, light);
    }

    /** Mais forte no meio da parede, sumindo perto do centro e da ponta. */
    private static int alpha(float f, float fade) {
        float edge = Math.min(Mth.clamp(f / 0.12F, 0F, 1F), Mth.clamp((1F - f) / 0.18F, 0F, 1F));
        return (int) (220 * edge * fade);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float u, float v, int alpha,
                               float nx, float nz) {
        vc.addVertex(p, x, y, z).setColor(255, 255, 255, alpha).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(p, nx, 0, nz);
    }

    @Override
    public ResourceLocation getTextureLocation(ZephyrCurtain entity) {
        return TEXTURE;
    }
}
