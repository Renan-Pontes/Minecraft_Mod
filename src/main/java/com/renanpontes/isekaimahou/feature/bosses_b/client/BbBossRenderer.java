package com.renanpontes.isekaimahou.feature.bosses_b.client;

import com.renanpontes.isekaimahou.feature.bosses_b.entity.BbBoss;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Renderer dos chefes: modelo GeckoLib + camada brilhante (textura "_glowmask"). Sem o tombo/vermelho de morte do
 * vanilla — a morte é a animação "death" e o espetáculo de partículas.
 */
public class BbBossRenderer<T extends BbBoss> extends GeoEntityRenderer<T> {
    public BbBossRenderer(EntityRendererProvider.Context ctx, GeoModel<T> model, float scale, float shadow) {
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
