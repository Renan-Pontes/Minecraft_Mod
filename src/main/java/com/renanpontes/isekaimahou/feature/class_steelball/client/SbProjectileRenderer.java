package com.renanpontes.isekaimahou.feature.class_steelball.client;

import java.util.function.Function;
import java.util.function.Predicate;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Bolas e unhas: modelo GeckoLib alinhado com o voo (o giro vem da animação); as douradas e as unhas brilham no escuro.
 */
public class SbProjectileRenderer<T extends Entity & GeoAnimatable> extends GeoEntityRenderer<T> {
    private final Predicate<T> bright;

    public SbProjectileRenderer(EntityRendererProvider.Context ctx, String id, Function<T, String> texture, Predicate<T> bright) {
        super(ctx, new SbGeoModel<>(id, texture));
        this.bright = bright;
        this.shadowRadius = 0F;
    }

    @Override
    public void render(T e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        Vec3 v = e.getDeltaMovement();
        float h = e.getBbHeight() / 2F;
        pose.pushPose();
        pose.translate(0, h, 0);
        if (v.lengthSqr() > 1.0E-6) {
            pose.mulPose(Axis.YP.rotation((float) Math.atan2(v.x, v.z)));
            pose.mulPose(Axis.XP.rotation((float) -Math.atan2(v.y, v.horizontalDistance())));
        }
        pose.translate(0, -h, 0);
        super.render(e, yaw, partialTick, pose, buffers, bright.test(e) ? LightTexture.FULL_BRIGHT : light);
        pose.popPose();
    }
}
