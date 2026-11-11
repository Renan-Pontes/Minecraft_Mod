package com.renanpontes.isekaimahou.feature.fusions_c.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.fusions_c.entity.LavaBombEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

/** Bomba de lava: rocha de magma girando (textura do bloco de magma, brilhando) com halo de calor laranja. */
public class LavaBombRenderer extends EntityRenderer<LavaBombEntity> {
    public LavaBombRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(LavaBombEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        float t = e.tickCount + pt;
        float r = e.scale();
        ps.pushPose();
        ps.translate(0, r * 0.5, 0);
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(t * 17));
        ps.mulPose(Axis.XP.rotationDegrees(t * 11));
        TextureAtlasSprite magma = FcQuads.sprite("magma");
        FcQuads.sphere(ps, FcQuads.blocksGlow(buffers), r, r * 0.9F, magma.getU0(), magma.getV0(), magma.getU1(), magma.getV1(),
                0F, 0xFFFFFF, 1F, 6, 10);
        ps.popPose();
        ps.mulPose(this.entityRenderDispatcher.cameraOrientation());
        FcQuads.billboard(ps, FcQuads.additive(buffers, FcQuads.GLOW), r * 2.6F, t * 8, 0xFF6A10, 0.8F);
        ps.popPose();
        super.render(e, yaw, pt, ps, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(LavaBombEntity e) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
