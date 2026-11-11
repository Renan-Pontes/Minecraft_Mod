package com.renanpontes.isekaimahou.feature.creatures_d.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.creatures_d.entity.ElementalCreature;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.util.Color;

/**
 * Renderer GeckoLib genérico das criaturas do módulo. Opções por criatura: camada brilhante
 * (textura _glowmask), translucidez (espíritos/sombra, com o alpha vindo da entidade),
 * luz máxima (seres feitos de luz) e escala.
 */
public class ElementalRenderer<T extends ElementalCreature> extends GeoEntityRenderer<T> {
    private boolean translucent;
    private boolean fullBright;

    public ElementalRenderer(EntityRendererProvider.Context ctx, String id, boolean turnsHead, float shadow) {
        super(ctx, new DefaultedEntityGeoModel<>(IsekaiMahou.id(id), turnsHead));
        this.shadowRadius = shadow;
    }

    public ElementalRenderer<T> glowing() {
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        return this;
    }

    public ElementalRenderer<T> translucent() {
        this.translucent = true;
        return this;
    }

    public ElementalRenderer<T> fullBright() {
        this.fullBright = true;
        return this;
    }

    public ElementalRenderer<T> scaled(float scale) {
        withScale(scale);
        return this;
    }

    @Override
    public RenderType getRenderType(T animatable, ResourceLocation texture, MultiBufferSource bufferSource, float partialTick) {
        return translucent ? RenderType.entityTranslucent(texture) : super.getRenderType(animatable, texture, bufferSource, partialTick);
    }

    @Override
    public Color getRenderColor(T animatable, float partialTick, int packedLight) {
        float alpha = animatable.renderAlpha(partialTick);
        return alpha >= 1.0F ? super.getRenderColor(animatable, partialTick, packedLight) : Color.ofRGBA(1.0F, 1.0F, 1.0F, alpha);
    }

    @Override
    protected int getBlockLightLevel(T entity, BlockPos pos) {
        return fullBright ? 15 : super.getBlockLightLevel(entity, pos);
    }
}
