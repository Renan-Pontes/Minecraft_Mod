package com.renanpontes.isekaimahou.feature.forms_b.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.forms_b.entity.SpellOrbEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Orbe: brilho colorido virado para a câmera, com um núcleo claro e um halo girando. */
public class OrbRenderer extends EntityRenderer<SpellOrbEntity> {
    private static final ResourceLocation GLOW = QuadFx.tex("spell_orb");
    private static final ResourceLocation HALO = QuadFx.tex("spell_pulse_ring");

    public OrbRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0;
    }

    @Override
    public void render(SpellOrbEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        float t = e.tickCount + pt;
        float fade = e.fade(pt);
        int rgb = e.color();
        float beat = 1.0F + 0.12F * Mth.sin(t * 0.4F);
        ps.pushPose();
        ps.translate(0, e.getBbHeight() / 2, 0);
        ps.mulPose(entityRenderDispatcher.cameraOrientation());
        QuadFx.upright(ps, QuadFx.glow(buffers, GLOW), 0.45F * beat, 0.45F * beat, rgb, 0.95F * fade);
        QuadFx.upright(ps, QuadFx.glow(buffers, GLOW), 0.16F, 0.16F, QuadFx.lighten(rgb, 0.8F), fade);
        ps.mulPose(Axis.ZP.rotationDegrees(t * 6F));
        QuadFx.upright(ps, QuadFx.glow(buffers, HALO), 0.32F, 0.32F, QuadFx.lighten(rgb, 0.4F), 0.6F * fade);
        ps.popPose();
        super.render(e, yaw, pt, ps, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(SpellOrbEntity e) {
        return GLOW;
    }
}
