package com.renanpontes.isekaimahou.feature.bosses_a.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.bosses_a.entity.GolemSlab;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Placa de terra do Golem: um bloco largo e achatado (terra por baixo, pedra musgosa em cima). Segurada ela treme
 * de leve; arremessada gira como um disco.
 */
public class SlabRenderer extends EntityRenderer<GolemSlab> {
    private static final BlockState BOTTOM = Blocks.COARSE_DIRT.defaultBlockState();
    private static final BlockState TOP = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
    private final BlockRenderDispatcher blocks;

    public SlabRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.blocks = ctx.getBlockRenderDispatcher();
        this.shadowRadius = 1.0F;
    }

    @Override
    public void render(GolemSlab slab, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float age = slab.tickCount + partialTick;
        pose.pushPose();
        pose.translate(0, 0.45, 0);
        if (slab.isFlying()) {
            pose.mulPose(Axis.YP.rotationDegrees(age * 24F));
        } else {
            pose.mulPose(Axis.YP.rotationDegrees(-slab.getYRot()));
            pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(age * 0.9F) * 3F));
        }
        layer(pose, buffers, light, BOTTOM, 2.6F, 0.45F, 1.8F, -0.45F);
        layer(pose, buffers, light, TOP, 2.4F, 0.45F, 1.6F, 0.0F);
        pose.popPose();
        super.render(slab, yaw, partialTick, pose, buffers, light);
    }

    private void layer(PoseStack pose, MultiBufferSource buffers, int light, BlockState state, float sx, float sy, float sz, float y) {
        pose.pushPose();
        pose.translate(-sx * 0.5F, y, -sz * 0.5F);
        pose.scale(sx, sy, sz);
        blocks.renderSingleBlock(state, pose, buffers, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
        pose.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(GolemSlab entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
