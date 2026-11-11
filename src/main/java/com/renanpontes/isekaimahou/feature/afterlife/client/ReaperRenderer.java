package com.renanpontes.isekaimahou.feature.afterlife.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.afterlife.entity.ReaperEntity;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Ceifador translúcido e escuro, flutuando, com olhos brilhantes (camada emissiva). */
public class ReaperRenderer extends EntityRenderer<ReaperEntity> {
    private static final ResourceLocation TEX = IsekaiMahou.id("textures/entity/afterlife_reaper.png");
    private static final ResourceLocation EYES = IsekaiMahou.id("textures/entity/afterlife_reaper_eyes.png");
    private final ReaperModel model;

    public ReaperRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.model = new ReaperModel(ctx.bakeLayer(ReaperModel.LAYER));
        this.shadowRadius = 0F;
    }

    @Override
    public void render(ReaperEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        float age = e.tickCount + pt;
        float appear = Mth.clamp(age / 15F, 0F, 1F);
        ps.pushPose();
        ps.translate(0.0, Mth.sin(age * 0.1F) * 0.08 + 0.1, 0.0);
        ps.mulPose(Axis.YP.rotationDegrees(180F - Mth.rotLerp(pt, e.yRotO, e.getYRot())));
        ps.scale(-1.1F, -1.1F, 1.1F);
        ps.translate(0.0F, -1.501F, 0.0F);
        model.setupAnim(e, 0F, 0F, age, 0F, 0F);
        int alpha = (int) (215 * appear);
        model.renderToBuffer(ps, buffers.getBuffer(RenderType.entityTranslucent(TEX)), light, OverlayTexture.NO_OVERLAY,
                (alpha << 24) | 0xFFFFFF);
        model.renderToBuffer(ps, buffers.getBuffer(RenderType.eyes(EYES)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                ((int) (255 * appear) << 24) | 0xFFFFFF);
        ps.popPose();
        super.render(e, yaw, pt, ps, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(ReaperEntity e) {
        return TEX;
    }
}
