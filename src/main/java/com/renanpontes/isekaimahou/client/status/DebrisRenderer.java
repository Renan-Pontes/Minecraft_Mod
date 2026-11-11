package com.renanpontes.isekaimahou.client.status;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.core.status.DebrisEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/** Escombro: o bloco em miniatura girando e encolhendo no fim. */
public class DebrisRenderer extends EntityRenderer<DebrisEntity> {
    public DebrisRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.2F;
    }

    @Override
    public void render(DebrisEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        BlockState st = e.blockState();
        if (st.getRenderShape() != RenderShape.MODEL) return;
        float age = e.tickCount + pt;
        float shrink = Mth.clamp((DebrisEntity.LIFETIME - age) / 12F, 0F, 1F);
        float s = 0.62F * shrink;
        if (s <= 0.01F) return;
        ps.pushPose();
        ps.translate(0, 0.3, 0);
        ps.mulPose(Axis.XP.rotationDegrees(age * e.spinX));
        ps.mulPose(Axis.ZP.rotationDegrees(age * e.spinZ));
        ps.scale(s, s, s);
        ps.translate(-0.5, -0.5, -0.5);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(st, ps, buffers, light, OverlayTexture.NO_OVERLAY);
        ps.popPose();
        super.render(e, yaw, pt, ps, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(DebrisEntity e) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
