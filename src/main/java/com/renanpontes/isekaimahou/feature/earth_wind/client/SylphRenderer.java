package com.renanpontes.isekaimahou.feature.earth_wind.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.earth_wind.entity.SummonedSylph;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.util.Color;

/** Silfide (GeckoLib): semitransparente (asas e cauda de vento) e sempre iluminada, como um espirito. */
public class SylphRenderer extends GeoEntityRenderer<SummonedSylph> {
    public SylphRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(IsekaiMahou.id("summoned_sylph"), true));
        this.shadowRadius = 0.3F;
    }

    @Override
    public RenderType getRenderType(SummonedSylph animatable, ResourceLocation texture, MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityTranslucent(texture);
    }

    @Override
    protected int getBlockLightLevel(SummonedSylph entity, BlockPos pos) {
        return 15;
    }

    /**
     * Forja de Invocações (forms_b): corpo tingido com a cor do elemento da receita. O forms_b grava a cor no
     * persistentData da criatura no cliente; lemos por texto (sem depender das classes de lá). 0 = cor natural.
     */
    @Override
    public Color getRenderColor(SummonedSylph animatable, float partialTick, int packedLight) {
        int tint = animatable.getPersistentData().getInt("isekai_mahou_forge_color");
        return tint == 0 ? super.getRenderColor(animatable, partialTick, packedLight) : new Color(0xFF000000 | tint);
    }
}
