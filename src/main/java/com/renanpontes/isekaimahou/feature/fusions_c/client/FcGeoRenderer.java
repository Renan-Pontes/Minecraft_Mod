package com.renanpontes.isekaimahou.feature.fusions_c.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.fusions_c.entity.FcSpellEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Renderer GeckoLib das estruturas de magia (Vulcão, Raiz do Mundo, Fonte Sagrada): modelo + camada que brilha
 * (textura {@code _glowmask}: lava, seiva, água benta). Com {@code scaleByEntity}, o tamanho segue o {@code scale()}
 * sincronizado da entidade (vulcão maior com Amplificar/Expandir).
 */
public class FcGeoRenderer<T extends FcSpellEntity & GeoEntity> extends GeoEntityRenderer<T> {
    private final boolean scaleByEntity;

    public FcGeoRenderer(EntityRendererProvider.Context ctx, String id, float shadow) {
        this(ctx, id, shadow, true);
    }

    public FcGeoRenderer(EntityRendererProvider.Context ctx, String id, float shadow, boolean scaleByEntity) {
        super(ctx, new DefaultedEntityGeoModel<>(IsekaiMahou.id(id), false));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = shadow;
        this.scaleByEntity = scaleByEntity;
    }

    @Override
    public void render(T entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        if (scaleByEntity) {
            float s = entity.scale();
            pose.scale(s, s, s);
        }
        super.render(entity, yaw, partialTick, pose, buffers, light);
        pose.popPose();
    }
}
