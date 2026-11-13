package com.renanpontes.isekaimahou.feature.light_shadow.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.light_shadow.entity.GuardianFairyEntity;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.util.Color;

/** Fada Guardiã: sempre iluminada (é feita de luz), asas translúcidas e auréola brilhante (glowmask). */
public class GuardianFairyRenderer extends GeoEntityRenderer<GuardianFairyEntity> {
    public GuardianFairyRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(IsekaiMahou.id("summoned_guardian_fairy")));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        shadowRadius = 0.2F;
    }

    @Override
    public RenderType getRenderType(GuardianFairyEntity animatable, ResourceLocation texture, MultiBufferSource buffers, float partialTick) {
        return RenderType.entityTranslucent(texture);
    }

    @Override
    public void render(GuardianFairyEntity entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        super.render(entity, yaw, partialTick, poseStack, buffers, LightTexture.FULL_BRIGHT);
    }

    /**
     * Forja de Invocações (forms_b): corpo tingido com a cor do elemento da receita. O forms_b grava a cor no
     * persistentData da criatura no cliente; lemos por texto (sem depender das classes de lá). 0 = cor natural.
     */
    @Override
    public Color getRenderColor(GuardianFairyEntity animatable, float partialTick, int packedLight) {
        int tint = animatable.getPersistentData().getInt("isekai_mahou_forge_color");
        return tint == 0 ? super.getRenderColor(animatable, partialTick, packedLight) : new Color(0xFF000000 | tint);
    }
}
