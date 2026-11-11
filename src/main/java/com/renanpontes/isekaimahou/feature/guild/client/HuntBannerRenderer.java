package com.renanpontes.isekaimahou.feature.guild.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.guild.hunt.HuntBanner;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Estandarte da Área de Caça (GeckoLib): modelo/animações/textura {@code hunt_banner}, camada brilhante (runas do círculo,
 * lanternas e o emblema) e o círculo rúnico do chão girando devagar (mais rápido em batalha).
 */
public class HuntBannerRenderer extends GeoEntityRenderer<HuntBanner> {
    public HuntBannerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new Model());
        this.shadowRadius = 0.6F;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    static final class Model extends DefaultedEntityGeoModel<HuntBanner> {
        Model() {
            super(IsekaiMahou.id("hunt_banner"), false);
        }

        @Override
        public void setCustomAnimations(HuntBanner banner, long instanceId, AnimationState<HuntBanner> state) {
            super.setCustomAnimations(banner, instanceId, state);
            float t = banner.tickCount + state.getPartialTick();
            float speed = banner.bannerState() == HuntBanner.BATTLE ? 0.06F : 0.015F;
            // Os círculos do chão (48 e 24 px) crescem até ~7 e ~3 blocos; ao recolher, encolhem até sumir.
            float fold = banner.bannerState() == HuntBanner.FOLD
                    ? Mth.clamp(1F - (banner.clientFoldTicks() + state.getPartialTick()) / 24F, 0F, 1F) : 1F;
            float pulse = 1F + Mth.sin(t * 0.08F) * 0.03F;
            getBone("rune_circle").ifPresent(b -> {
                b.setRotY(t * speed);
                b.setScaleX(2.4F * fold * pulse);
                b.setScaleZ(2.4F * fold * pulse);
            });
            getBone("rune_inner").ifPresent(b -> {
                b.setRotY(-t * speed * 1.6F);
                b.setScaleX(2.0F * fold);
                b.setScaleZ(2.0F * fold);
            });
            getBone("pennant").ifPresent(b -> b.setRotY(Mth.sin(t * 0.05F) * 0.4F));
        }
    }
}
