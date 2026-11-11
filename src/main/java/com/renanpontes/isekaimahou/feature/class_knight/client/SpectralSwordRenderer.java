package com.renanpontes.isekaimahou.feature.class_knight.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.class_knight.KnightRegistries;
import com.renanpontes.isekaimahou.feature.class_knight.entity.SpectralSwordEntity;
import com.renanpontes.isekaimahou.feature.class_knight.item.LegendarySwordItem;
import com.renanpontes.isekaimahou.feature.class_knight.item.RunicBladeItem;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Desenha a espada espectral: o modelo 3D da lâmina copiada (ponta para baixo, gigante e com luz cheia). Lâminas do
 * Cavaleiro têm modelo 3D vertical; espadas comuns (ícone diagonal) giram 135° para a ponta ficar para baixo.
 * Ao cravar, afunda um pouco e treme.
 */
public class SpectralSwordRenderer extends EntityRenderer<SpectralSwordEntity> {
    private final ItemRenderer items;

    public SpectralSwordRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.items = ctx.getItemRenderer();
    }

    @Override
    public void render(SpectralSwordEntity e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        ItemStack stack = e.sword();
        if (stack.isEmpty()) stack = new ItemStack(KnightRegistries.RUNIC_BLADE.get());
        boolean vertical = stack.getItem() instanceof RunicBladeItem || stack.getItem() instanceof LegendarySwordItem;
        float s = e.scale();
        pose.pushPose();
        int landed = e.landedTicks();
        float sink = landed >= 0 ? Math.min(0.35F, landed * 0.08F) * s : 0F;
        float shake = landed >= 0 && landed < 6 ? (float) Math.sin((e.tickCount + partialTick) * 3.0) * 2.5F * (6 - landed) / 6F : 0F;
        pose.translate(0, (vertical ? 1.42F : 0.72F) * s - sink, 0);
        pose.mulPose(Axis.YP.rotationDegrees(-e.getYRot()));
        pose.mulPose(Axis.XP.rotationDegrees(shake));
        pose.mulPose(Axis.ZP.rotationDegrees(vertical ? 180F : -135F));
        pose.scale(s, s, s);
        items.renderStatic(stack, ItemDisplayContext.NONE, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, pose, buffers,
                e.level(), e.getId());
        pose.popPose();
        super.render(e, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(SpectralSwordEntity e) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
