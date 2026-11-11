package com.renanpontes.isekaimahou.feature.fusions_b.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.fusions_b.entity.OrbitingDebrisEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Destroço em órbita: o bloco copiado do chão, menor, girando devagar (rápido quando é arremessado). */
public class OrbitingDebrisRenderer extends EntityRenderer<OrbitingDebrisEntity> {
    private final BlockRenderDispatcher blocks;

    public OrbitingDebrisRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.blocks = ctx.getBlockRenderDispatcher();
        this.shadowRadius = 0.25F;
    }

    @Override
    public void render(OrbitingDebrisEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        float t = e.tickCount + pt;
        float spin = t * (e.launched() ? 25F : 4F) + e.getId() * 47F;
        float s = 0.55F * Math.min(1F, (e.maxLife() - e.lifeLeft() + pt) / 6F + 0.2F);
        pose.pushPose();
        pose.translate(0, 0.3, 0);
        pose.mulPose(Axis.YP.rotationDegrees(spin));
        pose.mulPose(Axis.XP.rotationDegrees(spin * 0.6F));
        pose.mulPose(Axis.ZP.rotationDegrees(e.getId() * 31F));
        pose.scale(s, s, s);
        pose.translate(-0.5, -0.5, -0.5);
        blocks.renderSingleBlock(e.blockState(), pose, buffers, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
        pose.popPose();
        super.render(e, yaw, pt, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(OrbitingDebrisEntity e) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
