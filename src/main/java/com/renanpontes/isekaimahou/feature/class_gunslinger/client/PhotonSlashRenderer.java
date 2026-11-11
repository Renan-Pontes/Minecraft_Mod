package com.renanpontes.isekaimahou.feature.class_gunslinger.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.class_gunslinger.entity.PhotonSlashEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * Corte da Espada de Fótons: um crescente de luz (aditivo, brilha no escuro) com halo na cor do golpe e núcleo branco,
 * mais grosso no meio e afinando nas pontas. O arco "varre" de uma ponta à outra e se apaga. Sem textura.
 */
public class PhotonSlashRenderer extends EntityRenderer<PhotonSlashEntity> {
    private static final ResourceLocation TEXTURE = IsekaiMahou.id("textures/entity/mana_bullet.png");
    private static final int SEGMENTS = 18;
    private static final float ARC = (float) Math.toRadians(150);

    public PhotonSlashRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0F;
    }

    @Override
    public void render(PhotonSlashEntity e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float p = e.progress(partialTick);
        float sweep = Math.min(1F, p * 2.2F);          // quanto do arco já apareceu
        float fade = p < 0.5F ? 1F : Math.max(0F, 1F - (p - 0.5F) * 2F);
        if (fade <= 0.01F) return;
        int rgb = e.color();
        float r = ((rgb >> 16) & 255) / 255F, g = ((rgb >> 8) & 255) / 255F, b = (rgb & 255) / 255F;
        float radius = e.size();

        pose.pushPose();
        pose.mulPose(Axis.YP.rotation(e.slashYaw()));
        pose.mulPose(Axis.XP.rotation(-e.slashPitch()));
        pose.mulPose(Axis.ZP.rotationDegrees(e.roll()));
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = pose.last().pose();
        int segs = Math.max(2, Math.round(SEGMENTS * sweep));
        // halo largo (cor) e lâmina fina (branca); as duas faces
        band(vc, m, radius, 0.42F * radius, segs, r, g, b, 0.55F * fade);
        band(vc, m, radius, 0.16F * radius, segs, 0.75F + r * 0.25F, 0.75F + g * 0.25F, 0.75F + b * 0.25F, 0.95F * fade);
        pose.popPose();
        super.render(e, yaw, partialTick, pose, buffers, light);
    }

    /** Faixa em arco no plano XZ (horizontal antes do roll), centrada na frente (+z), com espessura que afina nas pontas. */
    private static void band(VertexConsumer vc, Matrix4f m, float radius, float width, int segs, float r, float g, float b, float a) {
        for (int i = 0; i < segs; i++) {
            float t0 = i / (float) SEGMENTS, t1 = (i + 1) / (float) SEGMENTS;
            float a0 = -ARC / 2 + ARC * t0, a1 = -ARC / 2 + ARC * t1;
            float w0 = width * (float) Math.sin(Math.PI * t0), w1 = width * (float) Math.sin(Math.PI * t1);
            float ia0 = 1F - 0.25F * (1F - (float) Math.sin(Math.PI * t0));
            float ia1 = 1F - 0.25F * (1F - (float) Math.sin(Math.PI * t1));
            float ox0 = (float) Math.sin(a0) * radius, oz0 = (float) Math.cos(a0) * radius;
            float ox1 = (float) Math.sin(a1) * radius, oz1 = (float) Math.cos(a1) * radius;
            float k0 = (radius - w0) / radius, k1 = (radius - w1) / radius;
            // quad: borda de fora (alfa cheio) e borda de dentro (some)
            vertex(vc, m, ox0, 0, oz0, r, g, b, a * ia0);
            vertex(vc, m, ox1, 0, oz1, r, g, b, a * ia1);
            vertex(vc, m, ox1 * k1, 0, oz1 * k1, r, g, b, 0F);
            vertex(vc, m, ox0 * k0, 0, oz0 * k0, r, g, b, 0F);
            vertex(vc, m, ox0 * k0, 0, oz0 * k0, r, g, b, 0F);
            vertex(vc, m, ox1 * k1, 0, oz1 * k1, r, g, b, 0F);
            vertex(vc, m, ox1, 0, oz1, r, g, b, a * ia1);
            vertex(vc, m, ox0, 0, oz0, r, g, b, a * ia0);
        }
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float z, float r, float g, float b, float a) {
        vc.addVertex(m, x, y, z).setColor(r, g, b, a);
    }

    @Override
    public boolean shouldRender(PhotonSlashEntity e, Frustum frustum, double camX, double camY, double camZ) {
        return e.shouldRender(camX, camY, camZ) && frustum.isVisible(e.getBoundingBox().inflate(e.size() + 1));
    }

    @Override
    public ResourceLocation getTextureLocation(PhotonSlashEntity entity) {
        return TEXTURE;
    }
}
