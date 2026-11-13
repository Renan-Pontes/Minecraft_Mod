package com.renanpontes.isekaimahou.feature.legends_j.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.legends_j.entity.MeteorRockEntity;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A rocha gigante do Tengai Shinsei: um bloco de pedra escura grande, girando devagar, com dois "calombos" de tufo e a
 * barriga em brasa (magma, sempre acesa). Tudo com blocos do vanilla — nenhuma textura nova.
 */
public class MeteorRockRenderer extends EntityRenderer<MeteorRockEntity> {
    private static final BlockState CORE = Blocks.COBBLED_DEEPSLATE.defaultBlockState();
    private static final BlockState LUMP = Blocks.TUFF.defaultBlockState();
    private static final BlockState HOT = Blocks.MAGMA_BLOCK.defaultBlockState();
    private final BlockRenderDispatcher blocks;

    public MeteorRockRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.blocks = ctx.getBlockRenderDispatcher();
        this.shadowRadius = 1.5F;
    }

    @Override
    public void render(MeteorRockEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        float s = e.size();
        float t = e.tickCount + pt;
        ps.pushPose();
        ps.translate(0, s * 0.5, 0);
        ps.mulPose(Axis.YP.rotationDegrees(t * 2.0F));
        ps.mulPose(Axis.XP.rotationDegrees(t * 1.3F));
        // núcleo
        cube(ps, buffers, CORE, s, 0, 0, 0, light);
        // calombos
        cube(ps, buffers, LUMP, s * 0.55F, s * 0.38F, s * 0.2F, -s * 0.1F, light);
        cube(ps, buffers, LUMP, s * 0.5F, -s * 0.3F, -s * 0.15F, s * 0.33F, light);
        // barriga em brasa (sempre acesa)
        cube(ps, buffers, HOT, s * 0.6F, 0, -s * 0.33F, 0, LightTexture.FULL_BRIGHT);
        ps.popPose();
        super.render(e, yaw, pt, ps, buffers, light);
    }

    private void cube(PoseStack ps, MultiBufferSource buffers, BlockState state, float size, float x, float y, float z, int light) {
        ps.pushPose();
        ps.translate(x, y, z);
        ps.scale(size, size, size);
        ps.translate(-0.5, -0.5, -0.5);
        blocks.renderSingleBlock(state, ps, buffers, light, OverlayTexture.NO_OVERLAY);
        ps.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(MeteorRockEntity e) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
