package com.renanpontes.isekaimahou.feature.fusions_c.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.renanpontes.isekaimahou.feature.fusions_c.entity.SacredSpringEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;

/**
 * Fonte Sagrada: o modelo GeckoLib (fonte de mármore com lótus de luz) + uma coluna de luz dourada subindo do lótus e
 * o círculo sagrado no chão marcando o raio da cura, girando devagar.
 */
public class SacredSpringRenderer extends FcGeoRenderer<SacredSpringEntity> {
    public SacredSpringRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, "sacred_spring_fountain", 0.8F, false);
    }

    /** Translúcido: a água benta da bacia e da taça deixa ver o fundo. */
    @Override
    public RenderType getRenderType(SacredSpringEntity e, ResourceLocation texture, MultiBufferSource buffers, float partialTick) {
        return RenderType.entityTranslucent(texture);
    }

    @Override
    public boolean shouldRender(SacredSpringEntity e, Frustum frustum, double x, double y, double z) {
        return frustum.isVisible(e.getBoundingBox().inflate(e.scale() + 1, 10, e.scale() + 1));
    }

    @Override
    public void render(SacredSpringEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        super.render(e, yaw, pt, ps, buffers, light);
        float t = e.tickCount + pt;
        float open = e.openness(pt, SacredSpringEntity.RISE_TICKS);
        if (open <= 0) return;
        float r = e.scale();
        ps.pushPose();
        ps.translate(0, 0.05, 0);
        FcQuads.flat(ps, FcQuads.additive(buffers, FcQuads.RUNE), r * open, t * 0.6F, 0xFFE9A8, 0.55F * open);
        FcQuads.ring(ps, FcQuads.additive(buffers, FcQuads.RING), r - 0.4F, r, 0xBFE8FF, 0.5F * open, 40);
        // Coluna de luz que "respira" a cada pulso de cura (1 s)
        float pulse = 0.6F + 0.4F * Mth.cos((e.age() + pt - SacredSpringEntity.RISE_TICKS) * Mth.TWO_PI / 20F);
        ps.translate(0, 2.2, 0);
        FcQuads.beam(ps, FcQuads.additive(buffers, FcQuads.BEAM), 0.35F * open, 9F * open, -t * 0.04F, 3F, 0xFFF2C0, 0.55F * pulse);
        FcQuads.beam(ps, FcQuads.additive(buffers, FcQuads.BEAM), 0.12F * open, 9F * open, -t * 0.08F, 2F, 0xFFFFFF, 0.8F * pulse);
        ps.popPose();
    }
}
