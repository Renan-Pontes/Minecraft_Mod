package com.renanpontes.isekaimahou.feature.creatures_b.client;

import java.util.function.Function;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.util.Color;

/**
 * Renderer GeckoLib genérico dos humanoides: modelo/animação por id, textura alternativa opcional,
 * camada brilhante (_glowmask) opcional, escala, sombra e tingimento por estado (fúria, fase 2...).
 */
public class CbRenderer<T extends Entity & GeoAnimatable> extends GeoEntityRenderer<T> {
    private final Function<T, Color> tint;

    public CbRenderer(EntityRendererProvider.Context ctx, GeoModel<T> model, boolean glow, float shadow, Function<T, Color> tint) {
        super(ctx, model);
        this.tint = tint;
        this.shadowRadius = shadow;
        if (glow) addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    /** Modelo "geo/entity/<model>", animações do mesmo id e textura "textures/entity/<texture>". */
    public static <T extends Entity & GeoAnimatable> GeoModel<T> model(String model, String texture) {
        DefaultedEntityGeoModel<T> m = new DefaultedEntityGeoModel<>(IsekaiMahou.id(model), true);
        return model.equals(texture) ? m : m.withAltTexture(IsekaiMahou.id(texture));
    }

    @Override
    public Color getRenderColor(T animatable, float partialTick, int packedLight) {
        Color c = tint == null ? null : tint.apply(animatable);
        return c != null ? c : super.getRenderColor(animatable, partialTick, packedLight);
    }
}
