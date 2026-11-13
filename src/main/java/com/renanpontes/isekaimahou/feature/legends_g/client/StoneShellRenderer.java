package com.renanpontes.isekaimahou.feature.legends_g.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.legends_g.entity.StoneShellEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Bala do Canhão de Pedra: dois blocos de pedra esticados no eixo do voo (corpo + ponta menor = ogiva), girando no
 * próprio eixo — devagar enquanto se forma na mão (e crescendo), MUITO rápido em voo.
 */
public class StoneShellRenderer extends EntityRenderer<StoneShellEntity> {
    private static final BlockState BODY = Blocks.POLISHED_ANDESITE.defaultBlockState();
    private static final BlockState TIP = Blocks.SMOOTH_STONE.defaultBlockState();
    private final BlockRenderDispatcher blocks;

    public StoneShellRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.blocks = ctx.getBlockRenderDispatcher();
        this.shadowRadius = 0.15F;
    }

    @Override
    public void render(StoneShellEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        float t = e.tickCount + pt;
        boolean forming = e.phase() == StoneShellEntity.FORM;
        float grow = forming ? Mth.clamp(t / StoneShellEntity.FORM_TICKS, 0.2F, 1F) : 1F;
        float ry = Mth.rotLerp(pt, e.yRotO, e.getYRot());
        float rx = Mth.lerp(pt, e.xRotO, e.getXRot());
        ps.pushPose();
        ps.translate(0, 0.2, 0);
        ps.mulPose(Axis.YP.rotationDegrees(-ry));
        ps.mulPose(Axis.XP.rotationDegrees(rx));
        ps.mulPose(Axis.ZP.rotationDegrees(forming ? t * t * 2.5F : t * 75F));
        ps.scale(grow, grow, grow);
        // corpo
        ps.pushPose();
        ps.scale(0.42F, 0.42F, 0.8F);
        ps.translate(-0.5, -0.5, -0.5);
        blocks.renderSingleBlock(BODY, ps, buffers, light, OverlayTexture.NO_OVERLAY);
        ps.popPose();
        // ponta
        ps.pushPose();
        ps.translate(0, 0, 0.48);
        ps.mulPose(Axis.ZP.rotationDegrees(45F));
        ps.scale(0.26F, 0.26F, 0.3F);
        ps.translate(-0.5, -0.5, -0.5);
        blocks.renderSingleBlock(TIP, ps, buffers, light, OverlayTexture.NO_OVERLAY);
        ps.popPose();
        ps.popPose();
        super.render(e, yaw, pt, ps, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(StoneShellEntity e) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
