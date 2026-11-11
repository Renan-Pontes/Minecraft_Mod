package com.renanpontes.isekaimahou.feature.arcane_duel.client;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Tipos de render do duelo arcano: brilho ADITIVO (soma luz, nunca escurece), sem cull, testa profundidade mas não a
 * escreve — vidro, cascas e sigilos não se escondem uns atrás dos outros.
 */
final class DuelRenderTypes {
    static final RenderType GLOW = RenderType.create("isekai_mahou_duel_glow", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 65536, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));

    private static final Map<ResourceLocation, RenderType> ADDITIVE = new HashMap<>();

    /** Textura branca tingida pela cor do vértice, somada à cena (círculos rúnicos, hexágonos, cartas, relógio). */
    static RenderType additive(ResourceLocation texture) {
        return ADDITIVE.computeIfAbsent(texture, t -> RenderType.create("isekai_mahou_duel_tex_" + t.getPath().replace('/', '_'),
                DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 16384, false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionTexColorShader))
                        .setTextureState(new RenderStateShard.TextureStateShard(t, false, false))
                        .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                        .setCullState(RenderStateShard.NO_CULL)
                        .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .createCompositeState(false)));
    }

    static ResourceLocation tex(String name) {
        return IsekaiMahou.id("textures/entity/arcane_duel/" + name + ".png");
    }

    private DuelRenderTypes() {}
}
