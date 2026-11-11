package com.renanpontes.isekaimahou.feature.class_enchanter.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.class_enchanter.entity.SovereignSanctumEntity;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Santuário do Soberano: o modelo (feito para raio 5) é escalado para o raio real, sempre iluminado, com glowmask.
 * O teste de visibilidade usa a área do círculo inteiro (a entidade em si é pequena).
 */
public class SanctumRenderer extends GeoEntityRenderer<SovereignSanctumEntity> {
    /** Raio (em blocos) do desenho no arquivo de modelo. */
    private static final float MODEL_RADIUS = 5F;

    public SanctumRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(IsekaiMahou.id("enchanter_sanctum")));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 0F;
    }

    @Override
    public boolean shouldRender(SovereignSanctumEntity e, Frustum frustum, double x, double y, double z) {
        float r = e.radius();
        AABB box = new AABB(e.getX() - r, e.getY() - 1, e.getZ() - r, e.getX() + r, e.getY() + 6, e.getZ() + r);
        return e.shouldRender(x, y, z) && frustum.isVisible(box);
    }

    @Override
    public void render(SovereignSanctumEntity e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float s = e.radius() / MODEL_RADIUS;
        pose.pushPose();
        pose.scale(s, Math.min(1.6F, 0.6F + s * 0.4F), s);
        super.render(e, 0F, partialTick, pose, buffers, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }
}
