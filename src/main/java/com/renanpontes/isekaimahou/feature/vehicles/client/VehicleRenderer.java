package com.renanpontes.isekaimahou.feature.vehicles.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.vehicles.VehicleKind;
import com.renanpontes.isekaimahou.feature.vehicles.entity.MagicVehicle;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Renderer GeckoLib dos veículos mágicos. O GeckoLib só gira sozinho as entidades VIVAS; aqui o modelo acompanha o
 * giro do veículo e ganha uma leve rolagem nas curvas e inclinação ao subir/descer (valores suavizados na entidade).
 */
public class VehicleRenderer extends GeoEntityRenderer<MagicVehicle> {
    private final float pivotY;

    public VehicleRenderer(EntityRendererProvider.Context ctx, VehicleKind kind) {
        super(ctx, new DefaultedEntityGeoModel<>(IsekaiMahou.id(kind.id)));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = kind.width * 0.45F;
        this.pivotY = kind.height * 0.5F;
    }

    @Override
    protected void applyRotations(MagicVehicle vehicle, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick,
                                  float nativeScale) {
        float yaw = Mth.rotLerp(partialTick, vehicle.yRotO, vehicle.getYRot());
        super.applyRotations(vehicle, poseStack, ageInTicks, yaw, partialTick, nativeScale);
        float tilt = vehicle.tilt(partialTick);
        float bank = vehicle.bank(partialTick) + vehicle.shake(partialTick);
        if (Math.abs(tilt) < 0.01F && Math.abs(bank) < 0.01F) return;
        poseStack.translate(0.0F, pivotY, 0.0F);
        poseStack.mulPose(Axis.XP.rotationDegrees(-tilt));
        poseStack.mulPose(Axis.ZP.rotationDegrees(bank));
        poseStack.translate(0.0F, -pivotY, 0.0F);
    }
}
