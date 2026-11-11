package com.renanpontes.isekaimahou.feature.class_smith.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.class_smith.entity.SmithSparkEntity;
import com.renanpontes.isekaimahou.feature.class_smith.entity.StarHammerEntity;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Renderers das entidades do Ferreiro: o martelo da Forja Estelar (GeckoLib) e as fagulhas (quad emissivo). */
public final class SmithEntityRenderers {

    /** Martelo colossal: modelo/animação GeckoLib + máscara de brilho (runas e núcleo estelar). */
    public static class StarHammer extends GeoEntityRenderer<StarHammerEntity> {
        public StarHammer(EntityRendererProvider.Context ctx) {
            super(ctx, new DefaultedEntityGeoModel<>(IsekaiMahou.id("smith_star_hammer")));
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
            this.shadowRadius = 1.2F;
        }
    }

    /** Fagulha: dois quads cruzados emissivos virados para a câmera, pulsando. */
    public static class Spark extends EntityRenderer<SmithSparkEntity> {
        private static final ResourceLocation TEX = IsekaiMahou.id("textures/entity/smith_spark.png");

        public Spark(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        public void render(SmithSparkEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
            int rgb = e.color();
            float pulse = 0.85F + 0.15F * Mth.sin((e.tickCount + pt) * 1.3F);
            float s = 0.22F * pulse;
            ps.pushPose();
            ps.translate(0, e.getBbHeight() / 2, 0);
            ps.mulPose(this.entityRenderDispatcher.cameraOrientation());
            ps.mulPose(Axis.ZP.rotationDegrees((e.tickCount + pt) * 25F));
            VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(TEX));
            PoseStack.Pose pose = ps.last();
            int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
            v(vc, pose, -s, -s, 0, 1, r, g, b);
            v(vc, pose, s, -s, 1, 1, r, g, b);
            v(vc, pose, s, s, 1, 0, r, g, b);
            v(vc, pose, -s, s, 0, 0, r, g, b);
            ps.popPose();
            super.render(e, yaw, pt, ps, buffers, light);
        }

        private static void v(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float u, float v, int r, int g, int b) {
            vc.addVertex(pose, x, y, 0).setColor(r, g, b, 235).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0, 0, 1);
        }

        @Override
        public ResourceLocation getTextureLocation(SmithSparkEntity e) {
            return TEX;
        }
    }

    private SmithEntityRenderers() {}
}
