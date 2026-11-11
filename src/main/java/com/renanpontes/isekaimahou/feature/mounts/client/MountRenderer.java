package com.renanpontes.isekaimahou.feature.mounts.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.mounts.entity.FlyingMount;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Mob;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Renderer GeckoLib comum das montarias: esconde o osso "saddle" sem sela e, no ar, inclina o corpo
 * (nariz para baixo ao mergulhar, para cima ao subir) e rola nas curvas — valores suavizados em
 * {@link com.renanpontes.isekaimahou.feature.mounts.entity.MountFlight}.
 *
 * <p>O olhar gira o osso vazio "look" (pai da cabeça): o GeckoLib SUBSTITUI a rotação do osso de olhar,
 * então girar a própria "head" apagaria a pose e as animações dela.
 */
public class MountRenderer<T extends Mob & GeoEntity & FlyingMount> extends GeoEntityRenderer<T> {
    private final float pivotY;

    public MountRenderer(EntityRendererProvider.Context ctx, String id, boolean turnsHead, boolean glowing, float shadow, float pivotY) {
        super(ctx, turnsHead ? new DefaultedEntityGeoModel<>(IsekaiMahou.id(id), "look") : new DefaultedEntityGeoModel<>(IsekaiMahou.id(id)));
        if (glowing) addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = shadow;
        this.pivotY = pivotY;
    }

    @Override
    public void preRender(PoseStack poseStack, T mount, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer,
                          boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        model.getBone("saddle").ifPresent(b -> b.setHidden(!mount.isMountSaddled()));
        super.preRender(poseStack, mount, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
    }

    @Override
    protected void applyRotations(T mount, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick, float nativeScale) {
        super.applyRotations(mount, poseStack, ageInTicks, rotationYaw, partialTick, nativeScale);
        float tilt = mount.flight().tilt(partialTick);
        float bank = mount.flight().bank(partialTick);
        if (Math.abs(tilt) < 0.01F && Math.abs(bank) < 0.01F) return;
        poseStack.translate(0.0F, pivotY, 0.0F);
        poseStack.mulPose(Axis.XP.rotationDegrees(-tilt));
        poseStack.mulPose(Axis.ZP.rotationDegrees(bank));
        poseStack.translate(0.0F, -pivotY, 0.0F);
    }
}
