package com.renanpontes.isekaimahou.feature.end_library.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.end_library.entity.TreasureBlade;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * Arma do Portão do Tesouro: o item 3D apontado na direção do voo (a ponta da espada vai na frente), maior que o
 * normal e sempre bem iluminado (brilho dourado do tesouro).
 */
public class TreasureBladeRenderer extends EntityRenderer<TreasureBlade> {
    private final ItemRenderer items;

    public TreasureBladeRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.items = ctx.getItemRenderer();
    }

    @Override
    public void render(TreasureBlade blade, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        float y = Mth.lerp(partialTick, blade.yRotO, blade.getYRot());
        float x = Mth.lerp(partialTick, blade.xRotO, blade.getXRot());
        pose.mulPose(Axis.YP.rotationDegrees(y - 90.0F));
        pose.mulPose(Axis.ZP.rotationDegrees(x));
        // Item "deitado" com a ponta (canto superior direito do ícone) para a frente.
        pose.mulPose(Axis.ZP.rotationDegrees(-45.0F));
        pose.scale(1.6F, 1.6F, 1.6F);
        items.renderStatic(blade.getItem(), ItemDisplayContext.FIXED, 0xF000F0, OverlayTexture.NO_OVERLAY, pose, buffers,
                blade.level(), blade.getId());
        pose.popPose();
        super.render(blade, yaw, partialTick, pose, buffers, light);
    }

    @Override
    @SuppressWarnings("deprecation")
    public ResourceLocation getTextureLocation(TreasureBlade blade) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
