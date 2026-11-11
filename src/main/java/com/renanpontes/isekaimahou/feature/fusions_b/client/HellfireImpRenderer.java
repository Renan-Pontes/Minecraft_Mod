package com.renanpontes.isekaimahou.feature.fusions_b.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.fusions_b.entity.HellfireImpEntity;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/** Diabrete Infernal: modelo próprio + olhos e brasas que brilham no escuro (camada "eyes"). */
public class HellfireImpRenderer extends MobRenderer<HellfireImpEntity, HellfireImpModel> {
    private static final ResourceLocation TEXTURE = IsekaiMahou.id("textures/entity/hellfire_imp.png");
    private static final RenderType GLOW = RenderType.eyes(IsekaiMahou.id("textures/entity/hellfire_imp_glow.png"));

    public HellfireImpRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HellfireImpModel(ctx.bakeLayer(HellfireImpModel.LAYER)), 0.3F);
        addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return GLOW;
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(HellfireImpEntity e) {
        return TEXTURE;
    }

    /** Feito de fogo: sempre aceso. */
    @Override
    protected int getBlockLightLevel(HellfireImpEntity e, BlockPos pos) {
        return 12;
    }
}
