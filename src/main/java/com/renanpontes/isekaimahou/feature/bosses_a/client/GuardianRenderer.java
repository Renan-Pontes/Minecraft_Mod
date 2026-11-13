package com.renanpontes.isekaimahou.feature.bosses_a.client;

import com.renanpontes.isekaimahou.feature.bosses_a.entity.ElementalGuardian;
import com.renanpontes.isekaimahou.feature.bosses_a.polish.BossShow;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.util.Color;

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

    /** v1.0: nos últimos ticks da morte o corpo do chefe se desfaz (1 = inteiro, 0 = sumiu). */
    private float deathAlpha(T animatable, float partialTick) {
        if (animatable.deathTime <= 0) return 1F;
        float left = ElementalGuardian.DEATH_TICKS - (animatable.deathTime + partialTick);
        return Mth.clamp(left / BossShow.DISSOLVE_TICKS, 0F, 1F);
    }

    @Override
    public Color getRenderColor(T animatable, float partialTick, int packedLight) {
        float a = deathAlpha(animatable, partialTick);
        if (a >= 1F) return super.getRenderColor(animatable, partialTick, packedLight);
        return Color.ofRGBA(1F, 1F, 1F, a);
    }

    @Override
    public RenderType getRenderType(T animatable, ResourceLocation texture, MultiBufferSource bufferSource, float partialTick) {
        if (deathAlpha(animatable, partialTick) < 1F) return RenderType.entityTranslucent(texture);
        return super.getRenderType(animatable, texture, bufferSource, partialTick);
    }
}
