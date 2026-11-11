package com.renanpontes.isekaimahou.feature.class_shinigami.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.class_shinigami.entity.ReishiWaveEntity;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Projéteis de Reiatsu: a Presa Lunar é uma meia-lua emissiva deitada na direção do voo (duas camadas, uma levemente
 * inclinada, para ter volume) na cor do arquétipo; o Canhão Rubro é uma esfera que sempre encara a câmera, pulsando.
 * O Getsuga Tenshō é uma lua crescente enorme inclinada na diagonal (três camadas: núcleo, borda e brilho) — branca,
 * ou negra com borda rubra; a escuridão do Mugetsu é uma parede de trevas em pé, com o brilho violeta tremendo.
 */
public class ReishiWaveRenderer extends EntityRenderer<ReishiWaveEntity> {
    private static final ResourceLocation MOON = IsekaiMahou.id("textures/entity/shinigami_moon_fang.png");
    private static final ResourceLocation ORB = IsekaiMahou.id("textures/entity/shinigami_kido_orb.png");
    private static final ResourceLocation GETSUGA = IsekaiMahou.id("textures/entity/shinigami_getsuga.png");
    private static final ResourceLocation GETSUGA_BLACK = IsekaiMahou.id("textures/entity/shinigami_getsuga_black.png");
    private static final ResourceLocation MUGETSU = IsekaiMahou.id("textures/entity/shinigami_mugetsu.png");

    public ReishiWaveRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public boolean shouldRender(ReishiWaveEntity e, net.minecraft.client.renderer.culling.Frustum frustum, double camX, double camY, double camZ) {
        // ondas enormes (Getsuga/Mugetsu): a caixa da entidade é pequena, então confere pela largura
        if (e.kind() == ReishiWaveEntity.MOON || e.kind() == ReishiWaveEntity.CANNON) return super.shouldRender(e, frustum, camX, camY, camZ);
        return frustum.isVisible(e.getBoundingBox().inflate(e.width() * 0.6F));
    }

    @Override
    public void render(ReishiWaveEntity e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        int rgb = e.color();
        float r = ((rgb >> 16) & 0xFF) / 255F, g = ((rgb >> 8) & 0xFF) / 255F, b = (rgb & 0xFF) / 255F;
        pose.pushPose();
        if (e.kind() == ReishiWaveEntity.CANNON) {
            float s = e.width() * (1.0F + 0.12F * Mth.sin((e.tickCount + partialTick) * 0.8F));
            pose.mulPose(this.entityRenderDispatcher.cameraOrientation());
            pose.scale(s, s, s);
            VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(ORB));
            quad(vc, pose, -0.5F, -0.5F, 0.5F, 0.5F, 1F, 1F, 1F, 1F);
        } else if (e.kind() != ReishiWaveEntity.MOON) {
            renderBig(e, partialTick, pose, buffers);
        } else {
            float yRot = Mth.lerp(partialTick, e.yRotO, e.getYRot());
            float xRot = Mth.lerp(partialTick, e.xRotO, e.getXRot());
            pose.mulPose(Axis.YP.rotationDegrees(yRot));
            pose.mulPose(Axis.XP.rotationDegrees(-xRot));
            float w = e.width();
            VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(MOON));
            // deitada (no plano lado × frente); a curva aponta para a frente
            flat(vc, pose, w, w * 0.6F, r, g, b, 0.95F);
            pose.mulPose(Axis.ZP.rotationDegrees(18F));
            flat(vc, pose, w * 0.9F, w * 0.55F, Math.min(1F, r + 0.3F), Math.min(1F, g + 0.3F), Math.min(1F, b + 0.3F), 0.6F);
        }
        pose.popPose();
        super.render(e, yaw, partialTick, pose, buffers, light);
    }

    /** Getsuga (diagonal, 55°) e Mugetsu (em pé, 90°): três camadas sem tingir, a do meio pulsando. */
    private void renderBig(ReishiWaveEntity e, float partialTick, PoseStack pose, MultiBufferSource buffers) {
        float yRot = Mth.lerp(partialTick, e.yRotO, e.getYRot());
        float xRot = Mth.lerp(partialTick, e.xRotO, e.getXRot());
        pose.mulPose(Axis.YP.rotationDegrees(yRot));
        pose.mulPose(Axis.XP.rotationDegrees(-xRot));
        boolean mugetsu = e.kind() == ReishiWaveEntity.MUGETSU;
        pose.mulPose(Axis.ZP.rotationDegrees(mugetsu ? 90F : 55F));
        ResourceLocation tex = mugetsu ? MUGETSU : e.kind() == ReishiWaveEntity.GETSUGA_BLACK ? GETSUGA_BLACK : GETSUGA;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(tex));
        float t = e.tickCount + partialTick;
        float w = e.width();
        // na saída a onda "abre" em 3 ticks
        float open = Math.min(1F, t / 3F);
        float pulse = 1.0F + 0.06F * Mth.sin(t * 1.3F);
        flat(vc, pose, w * open, w * 0.55F, 1F, 1F, 1F, 0.95F);
        pose.pushPose();
        pose.mulPose(Axis.ZP.rotationDegrees(14F));
        flat(vc, pose, w * 0.92F * open * pulse, w * 0.5F, 1F, 1F, 1F, 0.55F);
        pose.popPose();
        pose.pushPose();
        pose.mulPose(Axis.ZP.rotationDegrees(-14F));
        pose.translate(0, 0, -w * 0.08F);
        flat(vc, pose, w * 1.12F * open, w * 0.62F, 1F, 1F, 1F, mugetsu ? 0.75F : 0.35F);
        pose.popPose();
        // imagens residuais: a lua deixa cópias esmaecendo para trás (sensação de velocidade)
        for (int k = 1; k <= 3; k++) {
            pose.pushPose();
            pose.translate(0, 0, -w * 0.32F * k);
            float s = 1F - 0.06F * k;
            flat(vc, pose, w * s * open, w * 0.55F * s, 1F, 1F, 1F, (mugetsu ? 0.4F : 0.32F) / k);
            pose.popPose();
        }
        // brilho aditivo em volta (azul-branco; rubro no Getsuga negro; violeta no Mugetsu)
        int glow = mugetsu ? 0x5A1E8A : e.kind() == ReishiWaveEntity.GETSUGA_BLACK ? 0xC0102A : 0x8FE0FF;
        VertexConsumer add = buffers.getBuffer(ShinigamiRenderTypes.additive(ShinigamiRenderTypes.GLOW));
        PoseStack.Pose gp = pose.last();
        float gw = w * 0.75F * open, gd = w * 0.5F;
        int col = ShinigamiRenderTypes.argb(glow, 0.55F * pulse);
        add.addVertex(gp.pose(), -gw, 0, -gd).setUv(0, 1).setColor(col);
        add.addVertex(gp.pose(), gw, 0, -gd).setUv(1, 1).setColor(col);
        add.addVertex(gp.pose(), gw, 0, gd).setUv(1, 0).setColor(col);
        add.addVertex(gp.pose(), -gw, 0, gd).setUv(0, 0).setColor(col);
    }

    /** Quadrado no plano XZ (x = largura, z = profundidade para a frente). */
    private static void flat(VertexConsumer vc, PoseStack pose, float w, float d, float r, float g, float b, float a) {
        PoseStack.Pose p = pose.last();
        float hw = w / 2, hd = d / 2;
        vertex(vc, p, -hw, 0, -hd, 0, 1, r, g, b, a);
        vertex(vc, p, hw, 0, -hd, 1, 1, r, g, b, a);
        vertex(vc, p, hw, 0, hd, 1, 0, r, g, b, a);
        vertex(vc, p, -hw, 0, hd, 0, 0, r, g, b, a);
        // verso (visível de baixo)
        vertex(vc, p, -hw, 0, hd, 0, 0, r, g, b, a);
        vertex(vc, p, hw, 0, hd, 1, 0, r, g, b, a);
        vertex(vc, p, hw, 0, -hd, 1, 1, r, g, b, a);
        vertex(vc, p, -hw, 0, -hd, 0, 1, r, g, b, a);
    }

    private static void quad(VertexConsumer vc, PoseStack pose, float x0, float y0, float x1, float y1, float r, float g, float b, float a) {
        PoseStack.Pose p = pose.last();
        vertex(vc, p, x0, y0, 0, 0, 1, r, g, b, a);
        vertex(vc, p, x1, y0, 0, 1, 1, r, g, b, a);
        vertex(vc, p, x1, y1, 0, 1, 0, r, g, b, a);
        vertex(vc, p, x0, y1, 0, 0, 0, r, g, b, a);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float u, float v, float r, float g, float b, float a) {
        vc.addVertex(p, x, y, z).setColor(r, g, b, a).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(p, 0F, 1F, 0F);
    }

    @Override
    public ResourceLocation getTextureLocation(ReishiWaveEntity e) {
        return switch (e.kind()) {
            case ReishiWaveEntity.CANNON -> ORB;
            case ReishiWaveEntity.GETSUGA -> GETSUGA;
            case ReishiWaveEntity.GETSUGA_BLACK -> GETSUGA_BLACK;
            case ReishiWaveEntity.MUGETSU -> MUGETSU;
            default -> MOON;
        };
    }
}
