package com.renanpontes.isekaimahou.feature.forms_b.client;

import com.renanpontes.isekaimahou.feature.forms_b.entity.SpellIllusionEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.util.Color;

/** Ilusão: clone translúcido e escurecido, tingido pela cor da magia, que tremeluz. */
public class IllusionRenderer extends GeoEntityRenderer<SpellIllusionEntity> {

    public IllusionRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new IllusionModel());
        this.shadowRadius = 0.4F;
    }

    @Override
    public RenderType getRenderType(SpellIllusionEntity e, ResourceLocation texture, MultiBufferSource buffers, float partialTick) {
        return RenderType.entityTranslucent(texture);
    }

    @Override
    public Color getRenderColor(SpellIllusionEntity e, float partialTick, int packedLight) {
        float t = e.tickCount + partialTick;
        float flicker = 0.85F + 0.15F * Mth.sin(t * 0.35F) * Mth.sin(t * 0.13F);
        int a = Mth.clamp((int) (190 * flicker), 0, 255);
        // Sombra: cor da magia misturada com preto (a skin aparece por baixo, escurecida)
        int tint = QuadFx.darken(QuadFx.lighten(e.color(), 0.2F), 0.35F);
        return new Color((a << 24) | tint);
    }
}
