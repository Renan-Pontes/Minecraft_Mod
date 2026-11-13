package com.renanpontes.isekaimahou.feature.legends_h.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.legends_h.entity.SheerHeartAttackEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * O tanquinho do Sheer Heart Attack, montado com peças do jogo (sem modelo próprio): casco cinza-escuro, duas
 * esteiras pretas, uma torreta e a CAVEIRA na frente. Balança um pouco enquanto anda.
 */
public class SheerHeartAttackRenderer extends EntityRenderer<SheerHeartAttackEntity> {
    private static final BlockState HULL = Blocks.GRAY_CONCRETE.defaultBlockState();
    private static final BlockState TRACK = Blocks.BLACK_CONCRETE.defaultBlockState();
    private static final BlockState TURRET = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
    private static final ItemStack SKULL = new ItemStack(Items.SKELETON_SKULL);

    public SheerHeartAttackRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.35F;
    }

    @Override
    public void render(SheerHeartAttackEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        BlockRenderDispatcher blocks = Minecraft.getInstance().getBlockRenderer();
        float time = e.tickCount + pt;
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(pt, e.yRotO, e.getYRot())));
        ps.translate(0, 0.02 + Math.abs(Mth.sin(time * 0.9F)) * 0.025, 0);
        // Esteiras
        block(blocks, ps, buffers, light, TRACK, -0.32F, 0F, -0.34F, 0.16F, 0.18F, 0.68F);
        block(blocks, ps, buffers, light, TRACK, 0.16F, 0F, -0.34F, 0.16F, 0.18F, 0.68F);
        // Casco e torreta
        block(blocks, ps, buffers, light, HULL, -0.22F, 0.1F, -0.3F, 0.44F, 0.2F, 0.6F);
        block(blocks, ps, buffers, light, TURRET, -0.13F, 0.3F, -0.16F, 0.26F, 0.1F, 0.3F);
        // A caveira na frente
        ps.pushPose();
        ps.translate(0, 0.3, 0.3);
        ps.scale(0.42F, 0.42F, 0.42F);
        Minecraft.getInstance().getItemRenderer().renderStatic(SKULL, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, ps,
                buffers, e.level(), e.getId());
        ps.popPose();
        ps.popPose();
        super.render(e, yaw, pt, ps, buffers, light);
    }

    private static void block(BlockRenderDispatcher blocks, PoseStack ps, MultiBufferSource buffers, int light, BlockState state,
                              float x, float y, float z, float sx, float sy, float sz) {
        ps.pushPose();
        ps.translate(x, y, z);
        ps.scale(sx, sy, sz);
        blocks.renderSingleBlock(state, ps, buffers, light, OverlayTexture.NO_OVERLAY);
        ps.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(SheerHeartAttackEntity e) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
