package com.renanpontes.isekaimahou.feature.creatures_b.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.creatures_b.entity.BossThrownAxe;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Arma arremessada de chefe: desenha o próprio item (cutelo/lábris) grande, em pé no plano do voo e girando
 * como uma roda na direção do movimento.
 */
public class BossThrownAxeRenderer extends EntityRenderer<BossThrownAxe> {
    private final ItemRenderer items;

    public BossThrownAxeRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.items = ctx.getItemRenderer();
        this.shadowRadius = 0.3F;
    }

    @Override
    public void render(BossThrownAxe axe, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        ItemStack stack = axe.getItem();
        if (stack.isEmpty()) return;
        Vec3 v = axe.getDeltaMovement();
        float heading = (float) Mth.atan2(v.x, v.z);
        float spin = (axe.tickCount + partialTick) * 42.0F;
        float s = axe.renderScale();
        pose.pushPose();
        pose.translate(0.0, axe.getBbHeight() * 0.5, 0.0);
        // +Z local = direção do voo; o item (plano XY) gira 90° para ficar em pé ao longo do voo e roda no próprio eixo.
        pose.mulPose(Axis.YP.rotation(heading));
        pose.mulPose(Axis.YP.rotationDegrees(90.0F));
        pose.mulPose(Axis.ZP.rotationDegrees(spin));
        pose.scale(s, s, s);
        items.renderStatic(stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers, axe.level(), axe.getId());
        pose.popPose();
        super.render(axe, yaw, partialTick, pose, buffers, light);
    }

    @Override
    @SuppressWarnings("deprecation")
    public ResourceLocation getTextureLocation(BossThrownAxe axe) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
