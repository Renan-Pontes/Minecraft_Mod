package com.renanpontes.isekaimahou.feature.bosses_g.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.bosses_g.entity.FalseHydraHead;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.Color;

/**
 * Cabeça da Falsa Hidra: <b>some quando você olha direto para ela</b> e aparece no canto do olho. A transparência vem do
 * ângulo entre a direção da câmera e a cabeça (≤ 30° = invisível, ≥ 48° = inteira) e também do "alguém está olhando"
 * sincronizado pelo servidor (o olhar dos outros jogadores esconde a cabeça para todos).
 */
public class HydraHeadRenderer extends GeoEntityRenderer<FalseHydraHead> {
    private static final ResourceLocation GLOW = IsekaiMahou.id("textures/entity/false_hydra_head_glowmask.png");
    private static final double HIDE_DOT = 0.86;
    private static final double SHOW_DOT = 0.67;
    private float alpha = 1F;

    public HydraHeadRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new BgGeoRenderer.ToggleModel<>(IsekaiMahou.id("false_hydra_head"), false));
        addRenderLayer(new FadingGlow(this));
        this.shadowRadius = 0F;
    }

    @Override
    public void render(FalseHydraHead head, float yaw, float partialTick, PoseStack ps, MultiBufferSource buffers, int light) {
        if (head.isSunk() && head.deathTime <= 0) return;
        alpha = computeAlpha(head, partialTick);
        if (alpha < 0.02F) return;
        super.render(head, yaw, partialTick, ps, buffers, light);
    }

    private static float computeAlpha(FalseHydraHead head, float partialTick) {
        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 eye = cam.getPosition();
        Vec3 look = new Vec3(cam.getLookVector());
        Vec3 mid = head.getPosition(partialTick).add(0, head.getBbHeight() * 0.6, 0);
        Vec3 to = mid.subtract(eye);
        double dist = to.length();
        float byLook = 1F;
        if (dist > 1.0E-3) {
            double dot = look.dot(to.scale(1.0 / dist));
            byLook = (float) Mth.clamp((HIDE_DOT - dot) / (HIDE_DOT - SHOW_DOT), 0.0, 1.0);
            byLook = byLook * byLook * (3F - 2F * byLook);
        }
        float watched = Mth.lerp(partialTick, head.prevClientWatchFade, head.clientWatchFade);
        // Morrendo: aparece de vez (a cabeça cortada cai à vista de todos).
        if (head.deathTime > 0) return 1F;
        return Math.min(byLook, 1F - watched);
    }

    @Override
    public RenderType getRenderType(FalseHydraHead head, ResourceLocation texture, MultiBufferSource buffers, float partialTick) {
        return RenderType.entityTranslucent(texture);
    }

    @Override
    public Color getRenderColor(FalseHydraHead head, float partialTick, int packedLight) {
        return Color.ofRGBA(1F, 1F, 1F, alpha);
    }

    @Override
    protected float getDeathMaxRotation(FalseHydraHead animatable) {
        return 0.0F;
    }

    /** Olhos e a garganta brilhando, apagando junto com a cabeça. */
    private final class FadingGlow extends GeoRenderLayer<FalseHydraHead> {
        FadingGlow(GeoRenderer<FalseHydraHead> renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack ps, FalseHydraHead head, BakedGeoModel model, RenderType renderType, MultiBufferSource buffers,
                           VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
            RenderType eyes = RenderType.eyes(GLOW);
            int v = (int) (255 * alpha);
            int argb = 0xFF000000 | (v << 16) | (v << 8) | v;
            getRenderer().reRender(model, ps, buffers, head, eyes, buffers.getBuffer(eyes), partialTick, packedLight,
                    OverlayTexture.NO_OVERLAY, argb);
        }
    }
}
