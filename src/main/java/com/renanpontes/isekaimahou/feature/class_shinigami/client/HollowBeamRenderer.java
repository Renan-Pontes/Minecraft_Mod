package com.renanpontes.isekaimahou.feature.class_shinigami.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.class_shinigami.entity.HollowBeamEntity;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * O feixe do Cero: dois planos cruzados ao longo do raio (a textura corre ao longo do feixe), uma casca rubra larga e um
 * núcleo negro estreito; nasce em 2 ticks (o feixe "estica") e some aos poucos.
 */
public class HollowBeamRenderer extends EntityRenderer<HollowBeamEntity> {
    private static final ResourceLocation TEXTURE = IsekaiMahou.id("textures/entity/shinigami_cero_beam.png");

    public HollowBeamRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0F;
    }

    @Override
    public boolean shouldRender(HollowBeamEntity e, Frustum frustum, double camX, double camY, double camZ) {
        Vec3 from = e.position();
        float yaw = e.getYRot() * Mth.DEG_TO_RAD, pitch = e.getXRot() * Mth.DEG_TO_RAD;
        Vec3 dir = new Vec3(Mth.sin(yaw) * Mth.cos(pitch), Mth.sin(pitch), Mth.cos(yaw) * Mth.cos(pitch));
        Vec3 to = from.add(dir.scale(e.length()));
        return frustum.isVisible(new AABB(from, to).inflate(e.width() + 1));
    }

    @Override
    public void render(HollowBeamEntity e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float t = e.tickCount + partialTick;
        float life = HollowBeamEntity.LIFE;
        float alpha = Mth.clamp(1F - Math.max(0F, t - life * 0.4F) / (life * 0.6F), 0F, 1F);
        if (alpha <= 0.01F) return;
        float len = e.length() * Mth.clamp(t / 2F, 0.1F, 1F);
        float w = e.width() * (1F + 0.15F * Mth.sin(t * 2.1F));
        int rgb = e.color();
        float r = ((rgb >> 16) & 0xFF) / 255F, g = ((rgb >> 8) & 0xFF) / 255F, b = (rgb & 0xFF) / 255F;
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(e.getYRot()));
        pose.mulPose(Axis.XP.rotationDegrees(-e.getXRot()));
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
        float scroll = t * 0.35F;
        // casca rubra (dois planos cruzados) e o núcleo negro
        for (int k = 0; k < 2; k++) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(k * 90F + t * 6F));
            plane(vc, pose, w, len, scroll, r, g, b, 0.85F * alpha);
            plane(vc, pose, w * 0.38F, len, -scroll, 0.08F, 0.0F, 0.02F, 0.95F * alpha);
            pose.popPose();
        }
        // brilho aditivo: casca larga ao longo do feixe + clarão na saída e no impacto
        VertexConsumer add = buffers.getBuffer(ShinigamiRenderTypes.additive(ShinigamiRenderTypes.GLOW));
        org.joml.Matrix4f mat = pose.last().pose();
        int halo = ShinigamiRenderTypes.argb(rgb, 0.45F * alpha);
        float gw = w * 1.9F;
        for (int k = 0; k < 2; k++) {
            float c = k == 0 ? 1F : 0F, s = k == 0 ? 0F : 1F;
            float ax = c * gw / 2, ay = s * gw / 2;
            add.addVertex(mat, -ax, -ay, 0).setUv(0, 0.5F).setColor(halo);
            add.addVertex(mat, ax, ay, 0).setUv(1, 0.5F).setColor(halo);
            add.addVertex(mat, ax, ay, len).setUv(1, 0.5F).setColor(halo);
            add.addVertex(mat, -ax, -ay, len).setUv(0, 0.5F).setColor(halo);
        }
        float flare = w * (1.6F + 1.4F * Mth.clamp(1F - t / 6F, 0F, 1F));
        flare(add, mat, 0F, flare, ShinigamiRenderTypes.argb(rgb, 0.9F * alpha));
        flare(add, mat, 0F, flare * 0.45F, ShinigamiRenderTypes.argb(0xFFFFFF, 0.8F * alpha));
        flare(add, mat, len, flare * 1.3F, ShinigamiRenderTypes.argb(rgb, 0.8F * alpha));
        pose.popPose();
        super.render(e, yaw, partialTick, pose, buffers, light);
    }

    /** Clarão esférico falso: três quads ortogonais centrados em z. */
    private static void flare(VertexConsumer vc, org.joml.Matrix4f m, float z, float r, int argb) {
        float h = r / 2;
        vc.addVertex(m, -h, -h, z).setUv(0, 0).setColor(argb);
        vc.addVertex(m, h, -h, z).setUv(1, 0).setColor(argb);
        vc.addVertex(m, h, h, z).setUv(1, 1).setColor(argb);
        vc.addVertex(m, -h, h, z).setUv(0, 1).setColor(argb);
        vc.addVertex(m, -h, 0, z - h).setUv(0, 0).setColor(argb);
        vc.addVertex(m, h, 0, z - h).setUv(1, 0).setColor(argb);
        vc.addVertex(m, h, 0, z + h).setUv(1, 1).setColor(argb);
        vc.addVertex(m, -h, 0, z + h).setUv(0, 1).setColor(argb);
        vc.addVertex(m, 0, -h, z - h).setUv(0, 0).setColor(argb);
        vc.addVertex(m, 0, h, z - h).setUv(1, 0).setColor(argb);
        vc.addVertex(m, 0, h, z + h).setUv(1, 1).setColor(argb);
        vc.addVertex(m, 0, -h, z + h).setUv(0, 1).setColor(argb);
    }

    /** Um plano de largura {@code w} (eixo x) ao longo de +z até {@code len}. */
    private static void plane(VertexConsumer vc, PoseStack pose, float w, float len, float scroll, float r, float g, float b, float a) {
        PoseStack.Pose p = pose.last();
        float hw = w / 2;
        float u0 = scroll, u1 = scroll + len / Math.max(0.5F, w);
        vertex(vc, p, -hw, 0, 0, u0, 0, r, g, b, a);
        vertex(vc, p, hw, 0, 0, u0, 1, r, g, b, a);
        vertex(vc, p, hw, 0, len, u1, 1, r, g, b, a);
        vertex(vc, p, -hw, 0, len, u1, 0, r, g, b, a);
        vertex(vc, p, -hw, 0, len, u1, 0, r, g, b, a);
        vertex(vc, p, hw, 0, len, u1, 1, r, g, b, a);
        vertex(vc, p, hw, 0, 0, u0, 1, r, g, b, a);
        vertex(vc, p, -hw, 0, 0, u0, 0, r, g, b, a);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float u, float v, float r, float g, float b, float a) {
        vc.addVertex(p, x, y, z).setColor(r, g, b, a).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(p, 0F, 1F, 0F);
    }

    @Override
    public ResourceLocation getTextureLocation(HollowBeamEntity e) {
        return TEXTURE;
    }
}
