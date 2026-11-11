package com.renanpontes.isekaimahou.feature.fusions_b.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.fusions_b.entity.CrystalPrisonEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Prisão de Cristal: um bloco de vidro magenta translúcido do tamanho do alvo (que continua visível lá dentro)
 * com aglomerados de ametista brotando dos cantos e do topo. Cresce do chão ao aparecer.
 */
public class CrystalPrisonRenderer extends EntityRenderer<CrystalPrisonEntity> {
    private static final BlockState GLASS = Blocks.MAGENTA_STAINED_GLASS.defaultBlockState();
    private static final BlockState CLUSTER = Blocks.AMETHYST_CLUSTER.defaultBlockState();
    private static final BlockState BUD = Blocks.LARGE_AMETHYST_BUD.defaultBlockState();

    private final BlockRenderDispatcher blocks;

    public CrystalPrisonRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.blocks = ctx.getBlockRenderDispatcher();
        this.shadowRadius = 0.6F;
    }

    @Override
    public void render(CrystalPrisonEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        float age = e.maxLife() - e.lifeLeft() + pt;
        float grow = Math.min(1F, Math.max(0.05F, age / 5F));
        float w = e.prisonWidth() + 0.35F;
        float h = e.prisonHeight() + 0.25F;

        // Bloco de cristal envolvendo o alvo
        pose.pushPose();
        pose.translate(-w / 2, 0, -w / 2);
        pose.scale(w, h * grow, w);
        blocks.renderSingleBlock(GLASS, pose, buffers, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
        pose.popPose();

        // Aglomerados de ametista nos quatro cantos, inclinados para fora
        float s = Math.min(1.2F, 0.45F + w * 0.35F) * grow;
        for (int i = 0; i < 4; i++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(45 + i * 90 + e.getId() * 13));
            pose.translate(0, 0, w * 0.62F);
            pose.mulPose(Axis.XP.rotationDegrees(28));
            pose.scale(s, s, s);
            pose.translate(-0.5, -0.1, -0.5);
            blocks.renderSingleBlock(i % 2 == 0 ? CLUSTER : BUD, pose, buffers, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
            pose.popPose();
        }
        // Coroa de cristal no topo
        pose.pushPose();
        pose.translate(0, h * grow - 0.05, 0);
        pose.scale(s * 0.9F, s * 0.9F, s * 0.9F);
        pose.translate(-0.5, 0, -0.5);
        blocks.renderSingleBlock(CLUSTER, pose, buffers, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
        pose.popPose();

        super.render(e, yaw, pt, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(CrystalPrisonEntity e) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
