package com.renanpontes.isekaimahou.feature.earth_wind.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.earth_wind.entity.SummonedStoneGolem;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.util.Color;

/** Golem de Pedra (GeckoLib) com as runas e os olhos brilhando (camada _glowmask). */
public class StoneGolemRenderer extends GeoEntityRenderer<SummonedStoneGolem> {
    public StoneGolemRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(IsekaiMahou.id("summoned_stone_golem"), true));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 0.9F;
    }

    /**
     * Forja de Invocações (forms_b): corpo tingido com a cor do elemento da receita. O forms_b grava a cor no
     * persistentData da criatura no cliente; lemos por texto (sem depender das classes de lá). 0 = cor natural.
     */
    @Override
    public Color getRenderColor(SummonedStoneGolem animatable, float partialTick, int packedLight) {
        int tint = animatable.getPersistentData().getInt("isekai_mahou_forge_color");
        return tint == 0 ? super.getRenderColor(animatable, partialTick, packedLight) : new Color(0xFF000000 | tint);
    }
}
