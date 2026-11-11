package com.renanpontes.isekaimahou.feature.fusions_d.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.fusions_d.entity.RubbleEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/** Bloco arrancado: o próprio bloco girando, encolhendo ao ser engolido ou ao se desfazer depois de cair. */
public class RubbleRenderer extends EntityRenderer<RubbleEntity> {
    public RubbleRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.25F;
    }

    @Override
    public void render(RubbleEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        BlockState st = e.blockState();
        if (st.getRenderShape() != RenderShape.MODEL) return;
        float s = 0.8F * e.visualScale(pt);
        if (s <= 0.02F) return;
        float age = e.tickCount + pt;
        ps.pushPose();
        ps.translate(0, 0.4, 0);
        ps.mulPose(Axis.XP.rotationDegrees(age * e.spinX));
        ps.mulPose(Axis.ZP.rotationDegrees(age * e.spinZ));
        ps.scale(s, s, s);
        ps.translate(-0.5, -0.5, -0.5);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(st, ps, buffers, light, OverlayTexture.NO_OVERLAY);
        ps.popPose();
        super.render(e, yaw, pt, ps, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(RubbleEntity e) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
