package com.renanpontes.isekaimahou.feature.dimensions.client;

import com.renanpontes.isekaimahou.feature.dimensions.block.TwilightRiftBlockEntity;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.TheEndPortalRenderer;

/** A Fenda do Crepúsculo é um cubo inteiro com o céu estrelado do portal de passagem do End. */
public class TwilightRiftRenderer extends TheEndPortalRenderer<TwilightRiftBlockEntity> {
    public TwilightRiftRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected float getOffsetUp() {
        return 1.0F;
    }

    @Override
    protected float getOffsetDown() {
        return 0.0F;
    }

    @Override
    protected RenderType renderType() {
        return RenderType.endGateway();
    }
}
