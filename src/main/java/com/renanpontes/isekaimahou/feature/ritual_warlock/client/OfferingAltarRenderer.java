package com.renanpontes.isekaimahou.feature.ritual_warlock.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.ritual_warlock.block.OfferingAltarBlockEntity;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Desenha a oferenda girando e flutuando sobre o Altar de Oferendas. */
public class OfferingAltarRenderer implements BlockEntityRenderer<OfferingAltarBlockEntity> {
    private final ItemRenderer items;

    public OfferingAltarRenderer(BlockEntityRendererProvider.Context ctx) {
        this.items = ctx.getItemRenderer();
    }

    @Override
    public void render(OfferingAltarBlockEntity altar, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        ItemStack stack = altar.getOffering();
        Level level = altar.getLevel();
        if (stack.isEmpty() || level == null) return;
        float t = level.getGameTime() + partialTick;
        pose.pushPose();
        pose.translate(0.5, 1.05 + Mth.sin(t / 12.0F) * 0.06, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(t * 2.0F));
        pose.scale(1.1F, 1.1F, 1.1F);
        int lit = LevelRenderer.getLightColor(level, altar.getBlockPos().above());
        items.renderStatic(stack, ItemDisplayContext.GROUND, lit, OverlayTexture.NO_OVERLAY, pose, buffers, level, (int) altar.getBlockPos().asLong());
        pose.popPose();
    }
}
