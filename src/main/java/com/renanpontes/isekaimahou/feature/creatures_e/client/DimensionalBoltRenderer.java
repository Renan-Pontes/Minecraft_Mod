package com.renanpontes.isekaimahou.feature.creatures_e.client;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.creatures_e.entity.BoltKind;
import com.renanpontes.isekaimahou.feature.creatures_e.entity.DimensionalBolt;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;

/**
 * Projeteis das criaturas: orbes (sprite virado para a camera, girando), lanca/estilhaco (faixas cruzadas
 * orientadas pelo movimento, como a flecha) e pedregulho (bloco de pedra girando).
 */
public class DimensionalBoltRenderer extends EntityRenderer<DimensionalBolt> {
    private static final Map<BoltKind, ResourceLocation> TEXTURES = new EnumMap<>(BoltKind.class);

    static {
        for (BoltKind k : BoltKind.values()) {
            TEXTURES.put(k, IsekaiMahou.id("textures/entity/creatures_e/bolt_" + k.name().toLowerCase(Locale.ROOT) + ".png"));
        }
    }

    private final BlockRenderDispatcher blocks;

    public DimensionalBoltRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.blocks = ctx.getBlockRenderDispatcher();
        this.shadowRadius = 0.15F;
    }

    @Override
    protected int getBlockLightLevel(DimensionalBolt bolt, BlockPos pos) {
        return bolt.kind() == BoltKind.BOULDER ? super.getBlockLightLevel(bolt, pos) : 15;
    }

    @Override
    public void render(DimensionalBolt bolt, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light) {
        BoltKind kind = bolt.kind();
        float age = bolt.tickCount + partialTicks;
        pose.pushPose();
        pose.translate(0.0F, bolt.getBbHeight() * 0.5F, 0.0F);
        switch (kind) {
            case BOULDER -> {
                pose.mulPose(Axis.XP.rotationDegrees(age * 18.0F));
                pose.mulPose(Axis.YP.rotationDegrees(age * 11.0F));
                pose.scale(0.8F, 0.8F, 0.8F);
                pose.translate(-0.5F, -0.5F, -0.5F);
                blocks.renderSingleBlock(Blocks.MOSSY_COBBLESTONE.defaultBlockState(), pose, buffers, light, OverlayTexture.NO_OVERLAY);
            }
            case LIGHT_SPEAR, CRYSTAL_SHARD -> {
                pose.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTicks, bolt.yRotO, bolt.getYRot()) - 90.0F));
                pose.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partialTicks, bolt.xRotO, bolt.getXRot())));
                float len = kind == BoltKind.LIGHT_SPEAR ? 1.6F : 0.7F;
                float half = kind == BoltKind.LIGHT_SPEAR ? 0.2F : 0.17F;
                VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURES.get(kind)));
                for (int i = 0; i < 2; i++) {
                    pose.mulPose(Axis.XP.rotationDegrees(90.0F));
                    PoseStack.Pose p = pose.last();
                    vertex(vc, p, -len / 2, -half, 0, 1, light);
                    vertex(vc, p, len / 2, -half, 1, 1, light);
                    vertex(vc, p, len / 2, half, 1, 0, light);
                    vertex(vc, p, -len / 2, half, 0, 0, light);
                }
            }
            default -> {
                float s = kind.size;
                pose.mulPose(entityRenderDispatcher.cameraOrientation());
                pose.mulPose(Axis.ZP.rotationDegrees(age * 12.0F));
                float pulse = s * (1.0F + Mth.sin(age * 0.6F) * 0.08F);
                pose.scale(pulse, pulse, pulse);
                VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURES.get(kind)));
                PoseStack.Pose p = pose.last();
                vertex(vc, p, -0.5F, -0.5F, 0, 1, light);
                vertex(vc, p, 0.5F, -0.5F, 1, 1, light);
                vertex(vc, p, 0.5F, 0.5F, 1, 0, light);
                vertex(vc, p, -0.5F, 0.5F, 0, 0, light);
            }
        }
        pose.popPose();
        super.render(bolt, yaw, partialTicks, pose, buffers, light);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float u, float v, int light) {
        vc.addVertex(pose, x, y, 0.0F).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(DimensionalBolt bolt) {
        return TEXTURES.get(bolt.kind());
    }
}
