package com.renanpontes.isekaimahou.feature.legends_c.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Tipos de render das Técnicas Malditas: brilho ADITIVO (soma luz, nunca escurece), sem cull, testa profundidade mas
 * não escreve — cascas de esfera, cortes e ondas não se escondem umas atrás das outras.
 */
final class LcRenderTypes {
    /** Brilho aditivo por vértice, no passe das entidades. */
    static final RenderType GLOW = glow("isekai_mahou_lc_glow");
    /** O mesmo, desenhado no estágio AFTER_PARTICLES (cortes, ondas, clarões vindos por pacote). */
    static final RenderType GLOW_FX = glow("isekai_mahou_lc_glow_fx");
    /**
     * Escurecer (multiplica a cor da cena): o miolo do Azul é um "buraco" que suga a luz.
     * Mistura DST_COLOR×SRC: branco = nada, preto = apaga.
     */
    static final RenderType DARKEN = RenderType.create("isekai_mahou_lc_darken", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 16384, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(new RenderStateShard.TransparencyStateShard("isekai_mahou_lc_multiply", () -> {
                        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
                        com.mojang.blaze3d.systems.RenderSystem.blendFunc(
                                com.mojang.blaze3d.platform.GlStateManager.SourceFactor.DST_COLOR,
                                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ZERO);
                    }, () -> {
                        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
                        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
                    }))
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));

    private static RenderType glow(String name) {
        return RenderType.create(name, DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 65536, false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                        .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                        .setCullState(RenderStateShard.NO_CULL)
                        .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .setOutputState(RenderStateShard.MAIN_TARGET)
                        .createCompositeState(false));
    }

    static ResourceLocation tex(String name) {
        return IsekaiMahou.id("textures/entity/legends_c/" + name + ".png");
    }

    private LcRenderTypes() {}
}
