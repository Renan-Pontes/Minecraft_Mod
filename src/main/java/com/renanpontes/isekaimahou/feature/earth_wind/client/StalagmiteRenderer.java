package com.renanpontes.isekaimahou.feature.earth_wind.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.earth_wind.entity.StalagmiteSpike;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Desenha a Estalagmite como um feixe de estalagmites de pedra (o bloco de espeleotema do jogo) brotando do chao:
 * um espinho central grosso e dois menores inclinados, subindo/afundando conforme {@link StalagmiteSpike#rise}.
 */
public class StalagmiteRenderer extends EntityRenderer<StalagmiteSpike> {
    private static final BlockState[] CENTER = {
            spike(DripstoneThickness.BASE), spike(DripstoneThickness.MIDDLE), spike(DripstoneThickness.FRUSTUM), spike(DripstoneThickness.TIP)};
    private static final BlockState[] SMALL = {spike(DripstoneThickness.FRUSTUM), spike(DripstoneThickness.TIP)};
    private final BlockRenderDispatcher blocks;

    public StalagmiteRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.blocks = ctx.getBlockRenderDispatcher();
        this.shadowRadius = 0.0F;
    }

    private static BlockState spike(DripstoneThickness t) {
        return Blocks.POINTED_DRIPSTONE.defaultBlockState()
                .setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.UP)
                .setValue(PointedDripstoneBlock.THICKNESS, t);
    }

    @Override
    public void render(StalagmiteSpike e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float rise = e.rise(partialTick);
        if (rise <= 0.01F) return;
        float s = e.scale();
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-e.getYRot()));
        drawColumn(pose, buffers, light, CENTER, 1.7F * s, 1.0F * s, rise, 0, 0, 0);
        drawColumn(pose, buffers, light, SMALL, 1.1F * s, 0.9F * s, rise, 0.55F * s, 0.1F * s, 18);
        drawColumn(pose, buffers, light, SMALL, 1.0F * s, 0.8F * s, rise, -0.5F * s, -0.25F * s, -22);
        pose.popPose();
        super.render(e, yaw, partialTick, pose, buffers, light);
    }

    /** Uma pilha de blocos de espeleotema (largura/altura em escala), emergindo do chao. */
    private void drawColumn(PoseStack pose, MultiBufferSource buffers, int light, BlockState[] parts, float width, float heightScale,
            float rise, float offX, float offZ, float tilt) {
        pose.pushPose();
        pose.translate(offX, 0, offZ);
        pose.mulPose(Axis.ZP.rotationDegrees(tilt));
        float total = parts.length * heightScale;
        pose.translate(0, (rise - 1.0F) * total, 0);
        pose.scale(width, heightScale, width);
        pose.translate(-0.5, 0, -0.5);
        for (BlockState part : parts) {
            blocks.renderSingleBlock(part, pose, buffers, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
            pose.translate(0, 1, 0);
        }
        pose.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(StalagmiteSpike entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
