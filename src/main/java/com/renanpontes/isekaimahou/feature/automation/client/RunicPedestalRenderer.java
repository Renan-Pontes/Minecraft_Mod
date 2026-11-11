package com.renanpontes.isekaimahou.feature.automation.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.automation.block.RunicPedestalBlock;
import com.renanpontes.isekaimahou.feature.automation.block.RunicPedestalBlockEntity;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Pedestal Rúnico no mundo: o pergaminho flutua girando sobre o tampo; ativo, um círculo de runas na cor do
 * elemento gira no tampo e um halo menor gira ao contrário em volta do pergaminho. Ao conjurar, um anel se
 * expande e some (brilho do blockEvent).
 */
public class RunicPedestalRenderer implements BlockEntityRenderer<RunicPedestalBlockEntity> {
    private static final ResourceLocation RING = IsekaiMahou.id("textures/block/runic_pedestal_ring.png");
    private static final ResourceLocation HALO = IsekaiMahou.id("textures/block/runic_pedestal_halo.png");
    private static final float TOP = 14.3F / 16F; // um pouco acima do tampo (sem z-fighting)

    private final ItemRenderer items;

    public RunicPedestalRenderer(BlockEntityRendererProvider.Context ctx) {
        this.items = ctx.getItemRenderer();
    }

    @Override
    public void render(RunicPedestalBlockEntity be, float partialTick, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        float t = (level != null ? level.getGameTime() : 0) + partialTick;
        boolean active = be.getBlockState().getValue(RunicPedestalBlock.ACTIVE);
        int rgb = be.element().color;
        float flash = be.flash(partialTick);
        ItemStack scroll = be.scroll();
        int seed = (int) be.getBlockPos().asLong();

        // Pergaminho flutuando
        if (!scroll.isEmpty()) {
            float bob = Mth.sin(t / 12F + (seed & 15)) * 0.05F;
            int lightAbove = level != null ? LevelRenderer.getLightColor(level, be.getBlockPos().above()) : light;
            ps.pushPose();
            ps.translate(0.5, 1.12 + bob, 0.5);
            ps.mulPose(Axis.YP.rotationDegrees((t * (active ? 3.0F : 0.8F)) % 360F));
            ps.scale(0.75F, 0.75F, 0.75F);
            items.renderStatic(scroll, ItemDisplayContext.GROUND, active ? LightTexture.FULL_BRIGHT : lightAbove,
                    OverlayTexture.NO_OVERLAY, ps, buffers, level, seed);
            ps.popPose();
        }

        // Círculo de runas no tampo (aceso quando ativo; apagadinho com pergaminho parado)
        float ringAlpha = active ? 0.85F : scroll.isEmpty() ? 0F : 0.22F;
        ringAlpha = Math.min(1F, ringAlpha + flash * 0.5F);
        if (ringAlpha > 0.01F) {
            ps.pushPose();
            ps.translate(0.5, TOP, 0.5);
            ps.mulPose(Axis.YP.rotationDegrees(active ? (t * 1.6F) % 360F : 0F));
            quad(buffers.getBuffer(RenderType.entityTranslucent(RING)), ps.last(), 0.78F, rgb, ringAlpha, LightTexture.FULL_BRIGHT);
            ps.popPose();
        }

        // Halo girando ao contrário em volta do pergaminho
        if (active && !scroll.isEmpty()) {
            ps.pushPose();
            ps.translate(0.5, 1.12 + Mth.sin(t / 12F + (seed & 15)) * 0.05F, 0.5);
            ps.mulPose(Axis.YP.rotationDegrees((-t * 2.4F) % 360F));
            ps.mulPose(Axis.XP.rotationDegrees(14F));
            quad(buffers.getBuffer(RenderType.entityTranslucent(HALO)), ps.last(), 0.42F, rgb, 0.75F, LightTexture.FULL_BRIGHT);
            ps.popPose();
        }

        // Pulso ao conjurar: um anel que cresce e some
        if (flash > 0F) {
            float grow = 1F - flash;
            ps.pushPose();
            ps.translate(0.5, TOP + 0.02, 0.5);
            ps.mulPose(Axis.YP.rotationDegrees((t * 4F) % 360F));
            quad(buffers.getBuffer(RenderType.entityTranslucent(RING)), ps.last(), 0.6F + grow * 1.4F, rgb, flash * 0.9F, LightTexture.FULL_BRIGHT);
            ps.popPose();
        }
    }

    /** Quadrado horizontal de lado 2·half, centrado na origem atual, com a cor/alpha dados. */
    private static void quad(VertexConsumer vc, PoseStack.Pose pose, float half, int rgb, float alpha, int light) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255, a = Mth.clamp((int) (alpha * 255), 0, 255);
        vertex(vc, pose, -half, -half, 0, 0, r, g, b, a, light);
        vertex(vc, pose, -half, half, 0, 1, r, g, b, a, light);
        vertex(vc, pose, half, half, 1, 1, r, g, b, a, light);
        vertex(vc, pose, half, -half, 1, 0, r, g, b, a, light);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float z, float u, float v, int r, int g, int b, int a, int light) {
        vc.addVertex(pose, x, 0F, z).setColor(r, g, b, a).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose, 0F, 1F, 0F);
    }

    @Override
    public AABB getRenderBoundingBox(RunicPedestalBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(1.2, 0.0, 1.2).expandTowards(0, 1.0, 0);
    }
}
