package com.renanpontes.isekaimahou.feature.creatures_e.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.creatures_e.entity.DimensionalCreature;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Renderizador GeckoLib generico das criaturas das dimensoes.
 * Arquivos: geo/entity/ID.geo.json, animations/entity/ID.animation.json, textures/entity/ID.png
 * (+ ID_glowmask.png quando tem partes que brilham no escuro).
 */
public class CreatureRenderer<T extends DimensionalCreature> extends GeoEntityRenderer<T> {
    private boolean translucent;
    private int minLight;
    private boolean dissolves;

    public CreatureRenderer(EntityRendererProvider.Context ctx, String id, float shadow) {
        super(ctx, new DefaultedEntityGeoModel<>(IsekaiMahou.id(id), true));
        this.shadowRadius = shadow;
    }

    /** Corpo semitransparente (agua, ar, cristal). */
    public CreatureRenderer<T> translucent() {
        this.translucent = true;
        return this;
    }

    /** Luz minima (seres de fogo/luz brilham por conta propria). */
    public CreatureRenderer<T> light(int min) {
        this.minLight = min;
        return this;
    }

    /** Camada que brilha no escuro (textura _glowmask). */
    public CreatureRenderer<T> glowing() {
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        return this;
    }

    /** Ao morrer se desfaz no lugar (sem tombar de lado). */
    public CreatureRenderer<T> dissolves() {
        this.dissolves = true;
        return this;
    }

    @Override
    public RenderType getRenderType(T animatable, ResourceLocation texture, MultiBufferSource bufferSource, float partialTick) {
        return translucent ? RenderType.entityTranslucent(texture) : super.getRenderType(animatable, texture, bufferSource, partialTick);
    }

    @Override
    protected int getBlockLightLevel(T entity, BlockPos pos) {
        return Math.max(minLight, super.getBlockLightLevel(entity, pos));
    }

    @Override
    protected float getDeathMaxRotation(T animatable) {
        return dissolves ? 0.0F : super.getDeathMaxRotation(animatable);
    }
}
