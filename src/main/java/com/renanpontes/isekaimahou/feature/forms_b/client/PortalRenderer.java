package com.renanpontes.isekaimahou.feature.forms_b.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.forms_b.entity.SpellPortalEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Portal: anel oval em pé, virado para a direção em que foi aberto, com um redemoinho girando dentro.
 * A saída é desenhada num tom mais claro.
 */
public class PortalRenderer extends EntityRenderer<SpellPortalEntity> {
    private static final ResourceLocation RING = QuadFx.tex("spell_portal_ring");
    private static final ResourceLocation SWIRL = QuadFx.tex("spell_portal_swirl");

    public PortalRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0;
    }

    @Override
    public void render(SpellPortalEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        float t = e.tickCount + pt;
        float fade = e.fade(pt);
        int rgb = e.isExit() ? QuadFx.lighten(e.color(), 0.3F) : e.color();
        // Mínimo > 0: escala zero gera normais infinitas (NaN) e o quad some/pisca
        float open = Mth.clamp((e.age() + pt) / 12F, 0.02F, 1F);
        float h = SpellPortalEntity.HEIGHT / 2F;
        ps.pushPose();
        ps.translate(0, h, 0);
        ps.mulPose(Axis.YP.rotationDegrees(-e.facing()));
        ps.scale(open, open, 1F);
        // Redemoinho interno (duas camadas girando em sentidos opostos)
        ps.pushPose();
        ps.scale(0.62F, 0.98F, 1F);
        ps.pushPose();
        ps.mulPose(Axis.ZP.rotationDegrees(t * 4F));
        QuadFx.upright(ps, QuadFx.glow(buffers, SWIRL), 1F, 1F, QuadFx.darken(rgb, 0.25F), 0.75F * fade);
        ps.popPose();
        ps.translate(0, 0, 0.01);
        ps.mulPose(Axis.ZP.rotationDegrees(-t * 2.5F));
        QuadFx.upright(ps, QuadFx.glow(buffers, SWIRL), 0.8F, 0.8F, QuadFx.lighten(rgb, 0.3F), 0.45F * fade);
        ps.popPose();
        // Anel
        ps.translate(0, 0, 0.02);
        float beat = 1.0F + 0.03F * Mth.sin(t * 0.25F);
        QuadFx.upright(ps, QuadFx.glow(buffers, RING), 0.85F * beat, h * beat, QuadFx.lighten(rgb, 0.15F), 0.95F * fade);
        ps.popPose();
        super.render(e, yaw, pt, ps, buffers, light);
    }

    @Override
    public boolean shouldRender(SpellPortalEntity e, Frustum frustum, double camX, double camY, double camZ) {
        return e.shouldRender(camX, camY, camZ) && frustum.isVisible(e.getBoundingBox().inflate(0.5));
    }

    @Override
    public ResourceLocation getTextureLocation(SpellPortalEntity e) {
        return RING;
    }
}
