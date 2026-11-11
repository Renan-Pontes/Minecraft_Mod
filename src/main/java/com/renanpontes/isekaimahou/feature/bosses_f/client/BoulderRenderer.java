package com.renanpontes.isekaimahou.feature.bosses_f.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.bosses_f.entity.ColossusBoulder;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Rocha do Colosso: um pedaço de alvenaria musgosa girando (dois blocos sobrepostos para parecer irregular). */
public class BoulderRenderer extends EntityRenderer<ColossusBoulder> {
    private final BlockRenderDispatcher blocks;

    public BoulderRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.blocks = ctx.getBlockRenderDispatcher();
        this.shadowRadius = 0.7F;
    }

    @Override
    public void render(ColossusBoulder entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float s = entity.size();
        float spin = (entity.tickCount + partialTick) * 11F;
        BlockState main = entity.mossy() ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
        pose.pushPose();
        pose.translate(0, entity.getBbHeight() * 0.5, 0);
        pose.mulPose(Axis.YP.rotationDegrees(spin + entity.getId() * 37F));
        pose.mulPose(Axis.XP.rotationDegrees(spin * 0.7F));
        pose.pushPose();
        pose.scale(s, s, s);
        pose.translate(-0.5, -0.5, -0.5);
        blocks.renderSingleBlock(main, pose, buffers, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
        pose.popPose();
        pose.mulPose(Axis.ZP.rotationDegrees(45F));
        float t = s * 0.7F;
        pose.scale(t, t, t);
        pose.translate(-0.5, -0.2, -0.5);
        blocks.renderSingleBlock(Blocks.MOSSY_COBBLESTONE.defaultBlockState(), pose, buffers, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(ColossusBoulder entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
