package com.renanpontes.isekaimahou.feature.light_shadow.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.light_shadow.entity.LivingShadowEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.util.Color;

/** Sombra Viva: silhueta translúcida que tremula, com olhos e runas violeta brilhando (glowmask). */
public class LivingShadowRenderer extends GeoEntityRenderer<LivingShadowEntity> {
    public LivingShadowRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(IsekaiMahou.id("living_shadow"), true));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        shadowRadius = 0.5F;
    }

    @Override
    public RenderType getRenderType(LivingShadowEntity animatable, ResourceLocation texture, MultiBufferSource buffers, float partialTick) {
        return RenderType.entityTranslucent(texture);
    }

    @Override
    public Color getRenderColor(LivingShadowEntity animatable, float partialTick, int packedLight) {
        // Opacidade oscilando: a sombra "respira"
        float t = animatable.tickCount + partialTick;
        int alpha = Mth.clamp((int) (215 + 30 * Mth.sin(t * 0.15F)), 0, 255);
        // Forja de Invocações (forms_b): cor do elemento da receita, gravada no cliente por texto (0 = natural)
        int tint = animatable.getPersistentData().getInt("isekai_mahou_forge_color");
        return new Color((alpha << 24) | (tint == 0 ? 0xFFFFFF : tint & 0xFFFFFF));
    }
}
