package com.renanpontes.isekaimahou.feature.origins_b.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.origins_b.entity.ImpFireballEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;

/** Bola de fogo dos diabretes/demônio: a carga de fogo girando, sempre acesa, maior na versão do Demônio. */
public class ImpFireballRenderer extends EntityRenderer<ImpFireballEntity> {
    private final ItemRenderer itemRenderer;

    public ImpFireballRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.itemRenderer = ctx.getItemRenderer();
    }

    @Override
    protected int getBlockLightLevel(ImpFireballEntity entity, BlockPos pos) {
        return 15;
    }

    @Override
    public void render(ImpFireballEntity entity, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light) {
        if (entity.tickCount < 2 && entityRenderDispatcher.camera.getEntity().distanceToSqr(entity) < 12.25) return;
        float scale = entity.isBig() ? 1.15F : 0.55F;
        pose.pushPose();
        pose.scale(scale, scale, scale);
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        pose.mulPose(Axis.ZP.rotationDegrees((entity.tickCount + partialTicks) * 25F));
        itemRenderer.renderStatic(entity.getItem(), ItemDisplayContext.GROUND, light, OverlayTexture.NO_OVERLAY, pose, buffers,
                entity.level(), entity.getId());
        pose.popPose();
        super.render(entity, yaw, partialTicks, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(ImpFireballEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
