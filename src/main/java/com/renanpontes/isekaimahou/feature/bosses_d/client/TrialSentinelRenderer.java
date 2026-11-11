package com.renanpontes.isekaimahou.feature.bosses_d.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.bosses_d.entity.TrialSentinel;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.util.Color;

/** Renderer da Sentinela: pedra clara tingida pelo elemento + runas/viseira/escudo brilhando na cor do elemento. */
public class TrialSentinelRenderer extends GeoEntityRenderer<TrialSentinel> {
    public TrialSentinelRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(IsekaiMahou.id("trial_sentinel")));
        addRenderLayer(new TintedGlowLayer<>(this, TrialSentinel::glowRgb, s -> s.isStunned()));
        this.shadowRadius = 0.7F;
    }

    @Override
    public Color getRenderColor(TrialSentinel animatable, float partialTick, int packedLight) {
        // Mantém o alfa do GeckoLib (invisível = translúcido para quem pode ver) e só troca a cor
        int alpha = super.getRenderColor(animatable, partialTick, packedLight).getAlpha();
        int rgb = animatable.bodyRgb();
        return Color.ofARGB(alpha, (rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255);
    }

    @Override
    protected float getDeathMaxRotation(TrialSentinel animatable) {
        return 0F;
    }

    @Override
    public int getPackedOverlay(TrialSentinel animatable, float u, float partialTick) {
        return OverlayTexture.pack(OverlayTexture.u(u), OverlayTexture.v(animatable.hurtTime > 0 && !animatable.isDeadOrDying()));
    }
}
