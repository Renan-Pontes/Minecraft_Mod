package com.renanpontes.isekaimahou.feature.bosses_g.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.bosses_g.entity.ManticoreSpike;

import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/** Espinho da Manticora: desenhado como uma flecha (formato da textura de flecha do vanilla), verde-osso e farpado. */
public class ManticoreSpikeRenderer extends ArrowRenderer<ManticoreSpike> {
    private static final ResourceLocation TEXTURE = IsekaiMahou.id("textures/entity/manticore_spike.png");

    public ManticoreSpikeRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public ResourceLocation getTextureLocation(ManticoreSpike spike) {
        return TEXTURE;
    }
}
