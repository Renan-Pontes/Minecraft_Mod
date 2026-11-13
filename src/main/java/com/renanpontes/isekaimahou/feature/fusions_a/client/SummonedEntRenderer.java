package com.renanpontes.isekaimahou.feature.fusions_a.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.fusions_a.entity.SummonedEnt;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.util.Color;

/** Ent invocado: modelo GeckoLib de árvore viva; olhos e veios de seiva brilham (textura _glowmask). */
public class SummonedEntRenderer extends GeoEntityRenderer<SummonedEnt> {
    public SummonedEntRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(IsekaiMahou.id("summoned_ent"), true));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 0.9F;
    }

    /**
     * Forja de Invocações (forms_b): corpo tingido com a cor do elemento da receita. O forms_b grava a cor no
     * persistentData da criatura no cliente; lemos por texto (sem depender das classes de lá). 0 = cor natural.
     */
    @Override
    public Color getRenderColor(SummonedEnt animatable, float partialTick, int packedLight) {
        int tint = animatable.getPersistentData().getInt("isekai_mahou_forge_color");
        return tint == 0 ? super.getRenderColor(animatable, partialTick, packedLight) : new Color(0xFF000000 | tint);
    }
}
