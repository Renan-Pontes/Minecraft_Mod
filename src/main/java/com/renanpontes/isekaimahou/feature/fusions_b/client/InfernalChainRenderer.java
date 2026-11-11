package com.renanpontes.isekaimahou.feature.fusions_b.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.feature.fusions_b.entity.InfernalChainEntity;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Corrente Infernal: desenha correntes de verdade (modelo do bloco de corrente, em brasa = sempre iluminadas)
 * saindo de três pontos do selo no chão até o peito do alvo. As correntes "crescem" ao aparecer e se
 * recolhem no fim.
 */
public class InfernalChainRenderer extends EntityRenderer<InfernalChainEntity> {
    private static final BlockState CHAIN = Blocks.CHAIN.defaultBlockState();
    private static final float LINK = 0.75F;

    private final BlockRenderDispatcher blocks;

    public InfernalChainRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.blocks = ctx.getBlockRenderDispatcher();
    }

    @Override
    public void render(InfernalChainEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        Entity t = e.target();
        if (t == null) return;
        float open = e.openness(pt, 6);
        if (open <= 0) return;
        Vec3 base = e.getPosition(pt);
        Vec3 body = t.getPosition(pt).add(0, t.getBbHeight() * 0.55, 0);
        for (int i = 0; i < InfernalChainEntity.CHAINS; i++) {
            Vec3 a = e.anchor(i).subtract(e.position()).add(base);
            Vec3 d = body.subtract(a);
            double len = d.length();
            if (len < 0.1) continue;
            float yawRad = (float) Math.atan2(d.x, d.z);
            float pitch = (float) Math.acos(Math.max(-1, Math.min(1, d.y / len)));
            pose.pushPose();
            pose.translate(a.x - base.x, a.y - base.y, a.z - base.z);
            pose.mulPose(Axis.YP.rotation(yawRad));
            pose.mulPose(Axis.XP.rotation(pitch));
            double shown = len * open;
            int links = (int) Math.ceil(shown / LINK);
            for (int s = 0; s < links; s++) {
                float segLen = (float) Math.min(LINK, shown - s * LINK);
                pose.pushPose();
                pose.translate(0, s * LINK, 0);
                // Elos alternados giram 90° (como uma corrente de verdade)
                pose.mulPose(Axis.YP.rotationDegrees(s % 2 == 0 ? 0 : 90));
                pose.scale(0.9F, segLen, 0.9F);
                pose.translate(-0.5, 0, -0.5);
                blocks.renderSingleBlock(CHAIN, pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
                pose.popPose();
            }
            pose.popPose();
        }
        super.render(e, yaw, pt, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(InfernalChainEntity e) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
