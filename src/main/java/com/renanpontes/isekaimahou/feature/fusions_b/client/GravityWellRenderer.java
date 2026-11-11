package com.renanpontes.isekaimahou.feature.fusions_b.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.fusions_b.entity.GravityWellEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * Ponto de Atração: uma esfera de vazio (o "céu" estrelado do portal do End) com um disco de acreção roxo
 * girando inclinado em volta, que brilha (mistura aditiva). Abre crescendo e some encolhendo.
 */
public class GravityWellRenderer extends EntityRenderer<GravityWellEntity> {
    private static final int LAT = 8;
    private static final int LON = 14;
    private static final int DISK = 32;

    public GravityWellRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(GravityWellEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        float open = e.openness(pt, 8);
        if (open <= 0) return;
        float t = e.tickCount + pt;
        float r = 0.55F * open * (1F + 0.06F * Mth.sin(t * 0.3F));
        pose.pushPose();
        pose.translate(0, 0.5, 0);
        sphere(buffers.getBuffer(RenderType.endGateway()), pose.last().pose(), r);

        // Disco de acreção
        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(t * 5F));
        pose.mulPose(Axis.XP.rotationDegrees(22F));
        Matrix4f m = pose.last().pose();
        float in = r * 1.25F, out = r * (2.6F + 0.2F * Mth.sin(t * 0.2F));
        for (int i = 0; i < DISK; i++) {
            float a0 = (float) (i * Math.PI * 2 / DISK), a1 = (float) ((i + 1) * Math.PI * 2 / DISK);
            // Faixas mais claras e mais escuras girando dão a impressão de redemoinho
            float band = 0.55F + 0.45F * Mth.sin(a0 * 3 + t * 0.15F);
            int ci = FbRender.alpha(0xC8A0FF, 0.8F * band * open);
            int co = FbRender.alpha(0x5A2A8A, 0F);
            FbRender.colorQuad(glow, m,
                    Mth.cos(a0) * in, 0, Mth.sin(a0) * in, ci,
                    Mth.cos(a0) * out, 0, Mth.sin(a0) * out, co,
                    Mth.cos(a1) * out, 0, Mth.sin(a1) * out, co,
                    Mth.cos(a1) * in, 0, Mth.sin(a1) * in, ci);
        }
        pose.popPose();
        // Halo fino em volta da esfera (o "horizonte de eventos")
        pose.mulPose(this.entityRenderDispatcher.cameraOrientation());
        Matrix4f hm = pose.last().pose();
        for (int i = 0; i < DISK; i++) {
            float a0 = (float) (i * Math.PI * 2 / DISK), a1 = (float) ((i + 1) * Math.PI * 2 / DISK);
            int ci = FbRender.alpha(0xE0C8FF, 0.7F * open);
            int co = FbRender.alpha(0x6B5BA8, 0F);
            float r0 = r * 0.98F, r1 = r * 1.35F;
            FbRender.colorQuad(glow, hm,
                    Mth.cos(a0) * r0, Mth.sin(a0) * r0, 0, ci,
                    Mth.cos(a0) * r1, Mth.sin(a0) * r1, 0, co,
                    Mth.cos(a1) * r1, Mth.sin(a1) * r1, 0, co,
                    Mth.cos(a1) * r0, Mth.sin(a1) * r0, 0, ci);
        }
        pose.popPose();
        super.render(e, yaw, pt, pose, buffers, light);
    }

    /** Esfera em faixas de latitude/longitude, só posição (o shader do End pinta). */
    private static void sphere(VertexConsumer vc, Matrix4f m, float r) {
        for (int i = 0; i < LAT; i++) {
            float t0 = (float) (Math.PI * i / LAT), t1 = (float) (Math.PI * (i + 1) / LAT);
            float y0 = Mth.cos(t0) * r, y1 = Mth.cos(t1) * r;
            float s0 = Mth.sin(t0) * r, s1 = Mth.sin(t1) * r;
            for (int j = 0; j < LON; j++) {
                float p0 = (float) (Math.PI * 2 * j / LON), p1 = (float) (Math.PI * 2 * (j + 1) / LON);
                FbRender.quad(vc, m,
                        Mth.cos(p0) * s0, y0, Mth.sin(p0) * s0,
                        Mth.cos(p1) * s0, y0, Mth.sin(p1) * s0,
                        Mth.cos(p1) * s1, y1, Mth.sin(p1) * s1,
                        Mth.cos(p0) * s1, y1, Mth.sin(p0) * s1);
            }
        }
    }

    @Override
    public ResourceLocation getTextureLocation(GravityWellEntity e) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
