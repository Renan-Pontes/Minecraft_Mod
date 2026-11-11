package com.renanpontes.isekaimahou.feature.fusions_b.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.feature.fusions_b.entity.EclipseFieldEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * Eclipse: no alto da área, um disco de vazio (céu do End, preto estrelado) sempre virado para a câmera,
 * cercado por uma coroa dourada brilhante com raios que pulsam e um halo rosado — Luz e Sombra juntas.
 */
public class EclipseFieldRenderer extends EntityRenderer<EclipseFieldEntity> {
    private static final int SEGMENTS = 40;
    private static final int RAYS = 18;

    public EclipseFieldRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(EclipseFieldEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        float open = e.openness(pt, 20);
        if (open <= 0) return;
        float t = e.tickCount + pt;
        float r = 2.2F * open;
        pose.pushPose();
        pose.translate(0, EclipseFieldEntity.SKY_HEIGHT, 0);
        pose.mulPose(this.entityRenderDispatcher.cameraOrientation());
        Matrix4f m = pose.last().pose();

        // Disco escuro
        VertexConsumer dark = buffers.getBuffer(RenderType.endGateway());
        for (int i = 0; i < SEGMENTS; i++) {
            float a0 = (float) (i * Math.PI * 2 / SEGMENTS), a1 = (float) ((i + 1) * Math.PI * 2 / SEGMENTS);
            FbRender.quad(dark, m, 0, 0, 0, Mth.cos(a0) * r, Mth.sin(a0) * r, 0, Mth.cos(a1) * r, Mth.sin(a1) * r, 0, 0, 0, 0);
        }

        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
        // Coroa contínua (anel dourado logo na borda)
        for (int i = 0; i < SEGMENTS; i++) {
            float a0 = (float) (i * Math.PI * 2 / SEGMENTS), a1 = (float) ((i + 1) * Math.PI * 2 / SEGMENTS);
            float r0 = r * 0.97F, r1 = r * 1.35F, r2 = r * 2.1F;
            int gold = FbRender.alpha(0xFFE7A0, 0.95F * open);
            int pink = FbRender.alpha(0xE08AC0, 0.45F * open);
            int none = FbRender.alpha(0xC06AA0, 0F);
            FbRender.colorQuad(glow, m, Mth.cos(a0) * r0, Mth.sin(a0) * r0, 0, gold, Mth.cos(a0) * r1, Mth.sin(a0) * r1, 0, pink,
                    Mth.cos(a1) * r1, Mth.sin(a1) * r1, 0, pink, Mth.cos(a1) * r0, Mth.sin(a1) * r0, 0, gold);
            FbRender.colorQuad(glow, m, Mth.cos(a0) * r1, Mth.sin(a0) * r1, 0, pink, Mth.cos(a0) * r2, Mth.sin(a0) * r2, 0, none,
                    Mth.cos(a1) * r2, Mth.sin(a1) * r2, 0, none, Mth.cos(a1) * r1, Mth.sin(a1) * r1, 0, pink);
        }
        // Raios da coroa, de comprimentos que pulsam
        for (int i = 0; i < RAYS; i++) {
            float a = (float) (i * Math.PI * 2 / RAYS) + t * 0.004F;
            float len = r * (1.7F + 0.6F * Mth.sin(t * 0.08F + i * 1.9F) + 0.3F * Mth.sin(i * 3.1F));
            float w = 0.09F;
            float ca = Mth.cos(a), sa = Mth.sin(a);
            float px = -sa * w * r, py = ca * w * r;
            int base = FbRender.alpha(0xFFF0C0, 0.8F * open);
            int tip = FbRender.alpha(0xFFB060, 0F);
            FbRender.colorQuad(glow, m,
                    ca * r - px, sa * r - py, 0, base,
                    ca * len, sa * len, 0, tip,
                    ca * len, sa * len, 0, tip,
                    ca * r + px, sa * r + py, 0, base);
        }
        pose.popPose();
        super.render(e, yaw, pt, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(EclipseFieldEntity e) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
