package com.renanpontes.isekaimahou.feature.ascension.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.ascension.block.AscensionPedestalBlockEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
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
import net.minecraft.world.phys.Vec3;

/** Item do pedestal flutuando e girando (mais cópias quanto maior a pilha) + a quantidade quando você chega perto. */
public class AscensionPedestalRenderer implements BlockEntityRenderer<AscensionPedestalBlockEntity> {
    private final ItemRenderer items;
    private final Font font;

    public AscensionPedestalRenderer(BlockEntityRendererProvider.Context ctx) {
        this.items = ctx.getItemRenderer();
        this.font = ctx.getFont();
    }

    @Override
    public void render(AscensionPedestalBlockEntity be, float partialTick, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        ItemStack stack = be.getItem();
        if (stack.isEmpty()) return;
        Level level = be.getLevel();
        float t = (level != null ? level.getGameTime() : 0) + partialTick;
        int seed = (int) be.getBlockPos().asLong();
        float bob = Mth.sin(t / 10F + (seed & 31)) * 0.06F;
        int lightAbove = level != null ? LevelRenderer.getLightColor(level, be.getBlockPos().above()) : light;
        int copies = stack.getCount() >= 48 ? 4 : stack.getCount() >= 16 ? 3 : stack.getCount() > 1 ? 2 : 1;

        ps.pushPose();
        ps.translate(0.5, 0.95 + bob, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees((t * 2.5F) % 360F));
        for (int i = 0; i < copies; i++) {
            ps.pushPose();
            if (i > 0) {
                // Cópias levemente deslocadas, como um montinho de itens.
                float ox = ((seed * 31 + i * 17) % 7 - 3) * 0.03F;
                float oz = ((seed * 13 + i * 29) % 7 - 3) * 0.03F;
                ps.translate(ox, i * 0.05F, oz);
            }
            ps.scale(1.2F, 1.2F, 1.2F);
            items.renderStatic(stack, ItemDisplayContext.GROUND, lightAbove, OverlayTexture.NO_OVERLAY, ps, buffers, level, seed + i);
            ps.popPose();
        }
        ps.popPose();

        if (stack.getCount() > 1 && closeToCamera(be)) {
            String text = "×" + stack.getCount();
            ps.pushPose();
            ps.translate(0.5, 1.55 + bob, 0.5);
            ps.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
            ps.scale(0.02F, -0.02F, 0.02F);
            Matrix4f m = ps.last().pose();
            float x = -font.width(text) / 2F;
            font.drawInBatch(text, x, 0, 0xFFF4E3A3, false, m, buffers, Font.DisplayMode.NORMAL, 0x40000000, lightAbove);
            ps.popPose();
        }
    }

    private static boolean closeToCamera(AscensionPedestalBlockEntity be) {
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        return Vec3.atCenterOf(be.getBlockPos()).distanceToSqr(cam) < 8 * 8;
    }
}
