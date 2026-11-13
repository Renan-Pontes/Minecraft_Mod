package com.renanpontes.isekaimahou.feature.mounts.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.mounts.entity.GroundMount;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Renderer GeckoLib das montarias de chão (v1.0): esconde o osso "saddle" sem sela e gira o osso vazio "look"
 * (pai da cabeça) com o olhar — o GeckoLib SUBSTITUI a rotação do osso de olhar, então girar a própria "head"
 * apagaria a pose e as animações dela.
 */
public class GroundMountRenderer<T extends GroundMount> extends GeoEntityRenderer<T> {

    public GroundMountRenderer(EntityRendererProvider.Context ctx, String id, boolean glowing, float shadow) {
        super(ctx, new DefaultedEntityGeoModel<>(IsekaiMahou.id(id), "look"));
        if (glowing) addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = shadow;
    }

    @Override
    public void preRender(PoseStack poseStack, T mount, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer,
                          boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        model.getBone("saddle").ifPresent(b -> b.setHidden(!mount.isMountSaddled()));
        super.preRender(poseStack, mount, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
    }
}
