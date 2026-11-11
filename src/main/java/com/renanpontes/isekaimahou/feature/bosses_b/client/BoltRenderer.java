package com.renanpontes.isekaimahou.feature.bosses_b.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.bosses_b.entity.BossBolt;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Projéteis dos chefes: modelo GeckoLib totalmente iluminado, alinhado com o movimento (a lança é de ponta dupla). */
public class BoltRenderer extends GeoEntityRenderer<BossBolt> {
    public BoltRenderer(EntityRendererProvider.Context ctx, String id) {
        super(ctx, new DefaultedEntityGeoModel<>(IsekaiMahou.id(id)));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 0F;
    }

    @Override
    public void render(BossBolt bolt, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        Vec3 v = bolt.getDeltaMovement();
        float h = bolt.getBbHeight() / 2F;
        pose.pushPose();
        pose.translate(0, h, 0);
        if (v.lengthSqr() > 1.0E-6) {
            pose.mulPose(Axis.YP.rotation((float) Math.atan2(v.x, v.z)));
            pose.mulPose(Axis.XP.rotation((float) -Math.atan2(v.y, v.horizontalDistance())));
        }
        pose.translate(0, -h, 0);
        super.render(bolt, yaw, partialTick, pose, buffers, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }
}
