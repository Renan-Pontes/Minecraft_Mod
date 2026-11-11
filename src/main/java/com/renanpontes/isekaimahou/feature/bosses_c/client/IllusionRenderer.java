package com.renanpontes.isekaimahou.feature.bosses_c.client;

import com.renanpontes.isekaimahou.feature.bosses_c.entity.ArchmageIllusion;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.util.Color;

/**
 * Ilusão do Arquimago: o mesmo modelo dele, levemente translúcido e tremeluzindo, SEM sombra e sem a camada brilhante
 * (olhos e cristal do cajado apagados) — detalhes que deixam o verdadeiro reconhecível para quem prestar atenção.
 */
public class IllusionRenderer extends BossGeoRenderer<ArchmageIllusion> {

    public IllusionRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, "renegade_archmage", true, 1.0F, 0.0F, false);
    }

    @Override
    public RenderType getRenderType(ArchmageIllusion animatable, ResourceLocation texture, MultiBufferSource buffers, float partialTick) {
        return RenderType.entityTranslucent(texture);
    }

    @Override
    public Color getRenderColor(ArchmageIllusion animatable, float partialTick, int packedLight) {
        float flicker = 0.86F + 0.08F * (float) Math.sin((animatable.tickCount + partialTick) * 0.35F);
        return Color.ofRGBA(0.95F, 0.92F, 1.0F, flicker);
    }
}
