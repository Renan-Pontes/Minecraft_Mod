package com.renanpontes.isekaimahou.feature.legends_e.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.legends_e.entity.BlackGoatStormEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;

/**
 * Iä! Cabra Negra: o selo negro da Cabra no chão (com brilho verde-doentio que explode no sacrifício) e o vento negro —
 * faixas escuras em espiral subindo e girando em volta da área, cada vez mais fechadas no centro.
 */
public class BlackGoatStormRenderer extends EntityRenderer<BlackGoatStormEntity> {
    private static final int STRANDS = 16;
    private static final int STEPS = 14;

    public BlackGoatStormRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(BlackGoatStormEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        float ageF = e.age() + pt;
        float area = e.area();
        float wind = e.wind(ageF);
        float sig = e.sigil(ageF);
        float burst = Mth.clamp(1F - Math.abs(ageF - BlackGoatStormEntity.SACRIFICE) / 14F, 0F, 1F);

        // Selo: sombra (escurece) + brilho verde por cima
        if (sig > 0.01F) {
            pose.pushPose();
            pose.translate(0, 0.07, 0);
            pose.mulPose(Axis.YP.rotationDegrees(ageF * 0.5F));
            float half = area * 0.95F;
            LeDraw.texFlat(buffers.getBuffer(LeRenderTypes.shade(LeRenderTypes.GOAT_SIGIL)), pose.last().pose(), half,
                    LeDraw.argb(0x12051C, 0.9F * sig));
            pose.translate(0, 0.03, 0);
            LeDraw.texFlat(buffers.getBuffer(LeRenderTypes.additive(LeRenderTypes.GOAT_SIGIL)), pose.last().pose(), half,
                    LeDraw.argb(0x5AFF3A, (0.18F + 0.8F * burst) * sig));
            pose.popPose();
        }

        // Vento negro
        if (wind > 0.01F) {
            VertexConsumer dark = buffers.getBuffer(LeRenderTypes.DARK);
            Matrix4f m = pose.last().pose();
            for (int s = 0; s < STRANDS; s++) {
                float speed = 0.16F + (s % 3) * 0.04F;
                double a0 = Math.PI * 2 * s / STRANDS + ageF * speed;
                float top = 6F + (s % 4) * 1.2F;
                int col = s % 3 == 0 ? 0x2A0A3A : 0x07020C;
                Vec3 prev = null;
                float prevW = 0;
                for (int k = 0; k <= STEPS; k++) {
                    float t = k / (float) STEPS;
                    double a = a0 + t * 2.6;
                    double r = area * (0.95 - 0.6 * t) * (0.85 + 0.15 * wind);
                    Vec3 p = new Vec3(Math.cos(a) * r, t * top * wind + 0.2, Math.sin(a) * r);
                    float w = (0.9F - 0.6F * t) * wind;
                    if (prev != null) {
                        float al = 0.55F * wind * (1F - t * 0.7F);
                        LeDraw.softRibbon(dark, m, prev, p, new Vec3(0, 1, 0), prevW, w, col, al, al);
                    }
                    prev = p;
                    prevW = w;
                }
            }
            // Funil escuro no centro
            LeDraw.radialFlat(dark, m, 0F, area * 0.5F, LeDraw.argb(0x000000, 0.45F * wind), LeDraw.argb(0x000000, 0F), 32);
        }
        // Brilho verde do sacrifício subindo do centro
        if (burst > 0.01F) {
            VertexConsumer g = buffers.getBuffer(LeRenderTypes.GLOW);
            Matrix4f m = pose.last().pose();
            Vec3 cam = entityRenderDispatcher.camera.getPosition()
                    .subtract(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
            LeDraw.cylinder(g, m, 1.2F + area * 0.1F, 0F, 10F * burst, 24, 0x5AFF3A, 0.05F * burst, 0.5F * burst, cam, 0F);
            pose.pushPose();
            pose.translate(0, 0.12, 0);
            LeDraw.ringFlat(g, pose.last().pose(), area * (0.4F + 0.7F * (1F - burst)), 0.7F, 0x7DFF4A, burst, 64);
            pose.popPose();
        }
        super.render(e, yaw, pt, pose, buffers, light);
    }

    @Override
    public boolean shouldRender(BlackGoatStormEntity e, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(BlackGoatStormEntity e) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
