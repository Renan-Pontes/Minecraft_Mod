package com.renanpontes.isekaimahou.feature.bosses_a.client;

import java.util.function.Function;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Desenha um projétil como um bloco girando (meteoro de magma do Ignis, rocha do Golem). */
public class BlockProjectileRenderer<T extends Entity> extends EntityRenderer<T> {
    private final BlockRenderDispatcher blocks;
    private final Function<T, BlockState> state;
    private final Function<T, Float> size;
    private final boolean fullBright;

    public BlockProjectileRenderer(EntityRendererProvider.Context ctx, Function<T, BlockState> state, Function<T, Float> size,
                                   boolean fullBright) {
        super(ctx);
        this.blocks = ctx.getBlockRenderDispatcher();
        this.state = state;
        this.size = size;
        this.fullBright = fullBright;
        this.shadowRadius = 0.5F;
    }

    @Override
    public void render(T entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float s = size.apply(entity);
        float spin = (entity.tickCount + partialTick) * 14F;
        pose.pushPose();
        pose.translate(0, entity.getBbHeight() * 0.5, 0);
        pose.mulPose(Axis.YP.rotationDegrees(spin + entity.getId() * 37F));
        pose.mulPose(Axis.XP.rotationDegrees(spin * 0.7F));
        pose.scale(s, s, s);
        pose.translate(-0.5, -0.5, -0.5);
        blocks.renderSingleBlock(state.apply(entity), pose, buffers, fullBright ? LightTexture.FULL_BRIGHT : light,
                OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
