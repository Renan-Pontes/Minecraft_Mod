package com.renanpontes.isekaimahou.feature.bosses_a.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.bosses_a.entity.GolemPillar;

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

/**
 * Pilar dos Pilares Ascendentes: duas pedras musgosas empilhadas com uma ponta de espeleotema no topo, que saem do
 * chão de uma vez e afundam devagar (a parte "enterrada" fica escondida pelo terreno).
 */
public class PillarRenderer extends EntityRenderer<GolemPillar> {
    private static final BlockState[] STACK = {
            Blocks.MOSSY_COBBLESTONE.defaultBlockState(),
            Blocks.COBBLESTONE.defaultBlockState(),
            Blocks.POINTED_DRIPSTONE.defaultBlockState()
    };
    private final BlockRenderDispatcher blocks;

    public PillarRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.blocks = ctx.getBlockRenderDispatcher();
        this.shadowRadius = 0.6F;
    }

    @Override
    public void render(GolemPillar pillar, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float rise = pillar.rise(partialTick);
        if (rise <= 0.01F) return;
        pose.pushPose();
        pose.translate(0, -STACK.length * (1F - rise), 0);
        pose.mulPose(Axis.YP.rotationDegrees(-pillar.getYRot()));
        for (int i = 0; i < STACK.length; i++) {
            float w = i == STACK.length - 1 ? 1.0F : 1.1F - i * 0.1F;
            pose.pushPose();
            pose.translate(-w * 0.5F, i, -w * 0.5F);
            pose.scale(w, 1F, w);
            blocks.renderSingleBlock(STACK[i], pose, buffers, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
            pose.popPose();
        }
        pose.popPose();
        super.render(pillar, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(GolemPillar entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
