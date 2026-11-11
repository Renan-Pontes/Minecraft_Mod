package com.renanpontes.isekaimahou.feature.class_shinigami.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.class_shinigami.entity.ZanpakutoSpiritEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Espírito da zanpakutō (GeckoLib): um modelo e 7 texturas (sem nome + os 5 arquétipos + o Vazio Interior, branco com
 * olhos negros e dourados), cada uma com glowmask (olhos, a lâmina e os detalhes de Reiatsu brilham).
 */
public class ZanpakutoSpiritRenderer extends GeoEntityRenderer<ZanpakutoSpiritEntity> {
    private static final ResourceLocation[] TEXTURES = {
            IsekaiMahou.id("textures/entity/zanpakuto_spirit.png"),
            IsekaiMahou.id("textures/entity/zanpakuto_spirit_weight.png"),
            IsekaiMahou.id("textures/entity/zanpakuto_spirit_petals.png"),
            IsekaiMahou.id("textures/entity/zanpakuto_spirit_frost.png"),
            IsekaiMahou.id("textures/entity/zanpakuto_spirit_flame.png"),
            IsekaiMahou.id("textures/entity/zanpakuto_spirit_brush.png"),
            IsekaiMahou.id("textures/entity/zanpakuto_spirit_hollow.png")};

    public ZanpakutoSpiritRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<ZanpakutoSpiritEntity>(IsekaiMahou.id("zanpakuto_spirit"), true) {
            @Override
            public ResourceLocation getTextureResource(ZanpakutoSpiritEntity spirit) {
                int i = spirit.archetypeIndex();
                return TEXTURES[i >= 0 && i < TEXTURES.length ? i : 0];
            }
        });
        this.shadowRadius = 0.5F;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }
}
