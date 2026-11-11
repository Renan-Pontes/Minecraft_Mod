package com.renanpontes.isekaimahou.feature.bosses_e.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.bosses_e.entity.PrimordialChain;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Âncora das Correntes do Abismo: obelisco de obsidiana chorona com dois anéis de corrente girando e um olho violeta
 * que brilha no escuro (_glowmask). Os elos até o preso são partículas do próprio entity (cliente).
 */
public class PrimordialChainRenderer extends GeoEntityRenderer<PrimordialChain> {
    public PrimordialChainRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(IsekaiMahou.id(PrimordialChain.ID)));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 0.6F;
    }

    @Override
    protected float getDeathMaxRotation(PrimordialChain animatable) {
        return 0F;
    }

    @Override
    public boolean shouldShowName(PrimordialChain animatable) {
        return false;
    }
}
