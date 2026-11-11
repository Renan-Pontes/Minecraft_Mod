package com.renanpontes.isekaimahou.feature.creatures_c.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.creatures_c.entity.Fairy;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Fada: sempre iluminada (brilha no escuro), asas translúcidas e detalhes que reluzem. */
public class FairyRenderer extends GeoEntityRenderer<Fairy> {
    public FairyRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(IsekaiMahou.id("fairy"), true));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 0.2F;
    }

    @Override
    public RenderType getRenderType(Fairy animatable, ResourceLocation texture, MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityTranslucent(texture);
    }

    @Override
    protected int getBlockLightLevel(Fairy fairy, BlockPos pos) {
        return 15;
    }
}
