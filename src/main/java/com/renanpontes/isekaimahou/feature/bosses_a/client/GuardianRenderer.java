package com.renanpontes.isekaimahou.feature.bosses_a.client;

import com.renanpontes.isekaimahou.feature.bosses_a.entity.ElementalGuardian;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Renderer dos Guardiões: modelo GeckoLib + camada brilhante (textura _glowmask: lava, runas, olhos).
 * A morte é animada (não tomba de lado) e não fica vermelha durante a animação de morte.
 */
public class GuardianRenderer<T extends ElementalGuardian> extends GeoEntityRenderer<T> {
    public GuardianRenderer(EntityRendererProvider.Context ctx, GeoModel<T> model, float scale, float shadow) {
        super(ctx, model);
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        withScale(scale);
        this.shadowRadius = shadow;
    }

    @Override
    protected float getDeathMaxRotation(T animatable) {
        return 0F;
    }

    @Override
    public int getPackedOverlay(T animatable, float u, float partialTick) {
        return OverlayTexture.pack(OverlayTexture.u(u), OverlayTexture.v(animatable.hurtTime > 0 && !animatable.isDeadOrDying()));
    }
}
