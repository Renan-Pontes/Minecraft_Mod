package com.renanpontes.isekaimahou.feature.fusions_b.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.fusions_b.entity.AbyssTentacleEntity;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Tentáculos do Abismo: desenha N tentáculos em círculo em volta da vítima, cada um virado para ela e
 * enrolando conforme a magia avança. Brotam do chão ao aparecer e afundam no fim. Pontos bioluminescentes
 * brilham no escuro (camada "eyes").
 */
public class AbyssTentacleRenderer extends EntityRenderer<AbyssTentacleEntity> {
    private static final ResourceLocation TEXTURE = IsekaiMahou.id("textures/entity/abyss_tentacle.png");
    private static final RenderType GLOW = RenderType.eyes(IsekaiMahou.id("textures/entity/abyss_tentacle_glow.png"));

    private final AbyssTentacleModel model;

    public AbyssTentacleRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.model = new AbyssTentacleModel(ctx.bakeLayer(AbyssTentacleModel.LAYER));
        this.shadowRadius = 0.8F;
    }

    @Override
    public void render(AbyssTentacleEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        float open = e.openness(pt, 8);
        if (open <= 0) return;
        float age = e.tickCount + pt;
        Entity t = e.target();
        Vec3 center = t != null ? t.getPosition(pt) : e.getPosition(pt);
        Vec3 base = e.getPosition(pt);
        float targetH = t != null ? t.getBbHeight() : 1.8F;
        float scale = Math.min(1.3F, 0.5F + targetH * 0.3F);
        int n = e.count();
        float spread = e.spread();
        // Enrola mais com o tempo de aperto (depois de brotar)
        float grip = Math.min(1F, (e.maxLife() - e.lifeLeft() + pt) / 14F);
        for (int i = 0; i < n; i++) {
            float ang = (float) (i * Math.PI * 2 / n) + e.getYRot() * ((float) Math.PI / 180F);
            Vec3 at = new Vec3(center.x + Math.cos(ang) * spread, base.y, center.z + Math.sin(ang) * spread);
            Vec3 toTarget = new Vec3(center.x - at.x, 0, center.z - at.z);
            float face = (float) Math.atan2(-toTarget.x, -toTarget.z);
            pose.pushPose();
            pose.translate(at.x - base.x, (open - 1F) * 2.2F * scale, at.z - base.z);
            pose.mulPose(Axis.YP.rotation(face));
            pose.scale(scale, scale, scale);
            // Mesmo espaço de modelo de uma entidade viva (y para baixo, pés em y=24)
            pose.scale(-1F, -1F, 1F);
            pose.translate(0, -1.501F, 0);
            model.pose(age, 0.6F + 1.4F * grip, i * 2.3F + e.getId());
            model.renderToBuffer(pose, buffers.getBuffer(model.renderType(TEXTURE)), light, OverlayTexture.NO_OVERLAY, -1);
            model.renderToBuffer(pose, buffers.getBuffer(GLOW), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, -1);
            pose.popPose();
        }
        super.render(e, yaw, pt, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(AbyssTentacleEntity e) {
        return TEXTURE;
    }
}
