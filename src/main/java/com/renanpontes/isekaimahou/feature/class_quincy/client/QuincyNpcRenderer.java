package com.renanpontes.isekaimahou.feature.class_quincy.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.class_quincy.entity.QuincyNpcEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * NPC Quincy (GeckoLib): um modelo e 3 texturas (Arqueiro, Sentinela, Ancião), cada uma com a sua glowmask (cruz no
 * peito, estrela da capa e o arco espiritual brilham).
 */
public class QuincyNpcRenderer extends GeoEntityRenderer<QuincyNpcEntity> {
    private static final ResourceLocation[] TEXTURES = {
            IsekaiMahou.id("textures/entity/quincy_npc.png"),
            IsekaiMahou.id("textures/entity/quincy_npc_sentinel.png"),
            IsekaiMahou.id("textures/entity/quincy_npc_elder.png")};

    public QuincyNpcRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<QuincyNpcEntity>(IsekaiMahou.id("quincy_npc"), true) {
            @Override
            public ResourceLocation getTextureResource(QuincyNpcEntity npc) {
                return TEXTURES[npc.variant()];
            }
        });
        this.shadowRadius = 0.5F;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }
}
