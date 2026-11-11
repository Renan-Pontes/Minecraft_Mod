package com.renanpontes.isekaimahou.feature.hueco_mundo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.hueco_mundo.boss.HmBoss;
import com.renanpontes.isekaimahou.feature.hueco_mundo.entity.Arrancar;
import com.renanpontes.isekaimahou.feature.hueco_mundo.entity.HollowCrawler;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Renderer GeckoLib das criaturas e chefes do módulo: modelo/animações pelo id, camada brilhante ({@code _glowmask}),
 * pele por variação (Arrancar) ou por fase (Ressurreição dos chefes), ossos que aparecem por estado e o Rastejante
 * afundado na areia quando enterrado. Chefes não "tombam" ao morrer (a animação de morte faz isso).
 */
public class HmGeoRenderer<T extends Entity & GeoAnimatable> extends GeoEntityRenderer<T> {
    private final boolean boss;

    public HmGeoRenderer(EntityRendererProvider.Context ctx, String modelId, boolean turnsHead, float scale, float shadow) {
        super(ctx, new Model<>(modelId, turnsHead));
        withScale(scale);
        this.shadowRadius = shadow;
        this.boss = modelId.equals("hollow_king") || modelId.equals("sand_empress") || modelId.equals("thorn_queen");
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    protected float getDeathMaxRotation(T animatable) {
        return boss ? 0.0F : super.getDeathMaxRotation(animatable);
    }

    @Override
    public int getPackedOverlay(T animatable, float u, float partialTick) {
        if (boss && animatable instanceof LivingEntity living && living.deathTime > 0 && living.hurtTime <= 0) return OverlayTexture.NO_OVERLAY;
        return super.getPackedOverlay(animatable, u, partialTick);
    }

    @Override
    public void preRender(PoseStack poseStack, T animatable, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer,
                          boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        // Rastejante enterrado: só a crista da máscara fica acima da areia.
        if (!isReRender && animatable instanceof HollowCrawler c && c.isBurrowed()) poseStack.translate(0, -0.7, 0);
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
    }

    /** Modelo padrão + pele por variação/fase + ossos por estado. */
    static final class Model<T extends GeoAnimatable> extends DefaultedEntityGeoModel<T> {
        private final String id;

        Model(String id, boolean turnsHead) {
            super(IsekaiMahou.id(id), turnsHead);
            this.id = id;
        }

        @Override
        public ResourceLocation getTextureResource(T animatable) {
            if (animatable instanceof Arrancar a) return IsekaiMahou.id("textures/entity/" + id + "_" + a.variant() + ".png");
            if (animatable instanceof HmBoss b && !b.textureSuffix().isEmpty()) {
                return IsekaiMahou.id("textures/entity/" + id + b.textureSuffix() + ".png");
            }
            return super.getTextureResource(animatable);
        }

        @Override
        public void setCustomAnimations(T animatable, long instanceId, AnimationState<T> state) {
            super.setCustomAnimations(animatable, instanceId, state);
            if (animatable instanceof HmBoss b) {
                for (String name : b.toggledBones()) getBone(name).ifPresent(bone -> bone.setHidden(!b.isBoneVisible(name)));
            }
            if (animatable instanceof Arrancar a) {
                int v = a.variant();
                for (int k = 0; k < Arrancar.VARIANTS; k++) {
                    final boolean show = k == v;
                    getBone("mask_" + k).ifPresent(bone -> bone.setHidden(!show));
                }
            }
        }
    }
}
