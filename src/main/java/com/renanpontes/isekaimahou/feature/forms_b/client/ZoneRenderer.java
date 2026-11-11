package com.renanpontes.isekaimahou.feature.forms_b.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.forms_b.entity.SpellZoneEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Zona: círculo mágico girando no chão (com um selo interno girando ao contrário).
 * Pulso: núcleo pequeno pulsando + onda que se expande até o raio a cada disparo.
 * O selo interno e a onda usam a cor de destaque (2º elemento em magias multi-elemento).
 */
public class ZoneRenderer extends EntityRenderer<SpellZoneEntity> {
    private static final ResourceLocation CIRCLE = QuadFx.tex("spell_zone_circle");
    private static final ResourceLocation INNER = QuadFx.tex("spell_zone_inner");
    private static final ResourceLocation CORE = QuadFx.tex("spell_pulse_core");
    private static final ResourceLocation RING = QuadFx.tex("spell_pulse_ring");

    public ZoneRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0;
    }

    @Override
    public void render(SpellZoneEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        float t = e.tickCount + pt;
        float fade = e.fade(pt);
        float r = e.radius();
        int rgb = e.color();
        ps.pushPose();
        ps.translate(0, 0.03, 0);
        if (e.variant() == SpellZoneEntity.ZONE) {
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(t * 0.9F));
            QuadFx.flat(ps, QuadFx.glow(buffers, CIRCLE), r, rgb, 0.85F * fade);
            ps.popPose();
            ps.pushPose();
            ps.translate(0, 0.01, 0);
            ps.mulPose(Axis.YP.rotationDegrees(-t * 1.8F));
            QuadFx.flat(ps, QuadFx.glow(buffers, INNER), r * 0.55F, e.accentColor(), 0.7F * fade);
            ps.popPose();
        } else {
            float beat = 1.0F + 0.08F * Mth.sin(t * 0.3F);
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(t * 2.5F));
            QuadFx.flat(ps, QuadFx.glow(buffers, CORE), 1.2F * beat, QuadFx.lighten(rgb, 0.2F), 0.9F * fade);
            ps.popPose();
            // Borda fraca mostrando o alcance
            ps.pushPose();
            ps.translate(0, 0.005, 0);
            QuadFx.flat(ps, QuadFx.glow(buffers, RING), r, rgb, 0.22F * fade);
            ps.popPose();
            float p = e.pulseProgress(pt);
            if (p < 1F) {
                ps.pushPose();
                ps.translate(0, 0.015, 0);
                float eased = 1F - (1F - p) * (1F - p);
                QuadFx.flat(ps, QuadFx.glow(buffers, RING), Math.max(0.3F, r * eased), e.accentColor(), (1F - p) * fade);
                ps.popPose();
            }
        }
        ps.popPose();
        super.render(e, yaw, pt, ps, buffers, light);
    }

    @Override
    public boolean shouldRender(SpellZoneEntity e, Frustum frustum, double camX, double camY, double camZ) {
        if (!e.shouldRender(camX, camY, camZ)) return false;
        float r = e.radius();
        return frustum.isVisible(e.getBoundingBox().inflate(r, 0.5, r));
    }

    @Override
    public ResourceLocation getTextureLocation(SpellZoneEntity e) {
        return CIRCLE;
    }
}
