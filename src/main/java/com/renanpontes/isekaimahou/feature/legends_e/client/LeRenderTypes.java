package com.renanpontes.isekaimahou.feature.legends_e.client;

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
 * Tipos de render das Magias de Nível Supremo: brilho ADITIVO (soma luz, para círculos, feixes e pilares) e "sombra"
 * TRANSLÚCIDA comum (para o vento negro, o fundo do relógio e a rocha do meteoro, que precisam escurecer). Sem cull,
 * testam profundidade mas não a escrevem (camadas não se escondem umas atrás das outras).
 */
final class LeRenderTypes {
    static final RenderType GLOW = RenderType.create("isekai_mahou_le_glow", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 65536, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));

    static final RenderType DARK = RenderType.create("isekai_mahou_le_dark", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 65536, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));

    private static final Map<ResourceLocation, RenderType> ADDITIVE = new HashMap<>();
    private static final Map<ResourceLocation, RenderType> SHADE = new HashMap<>();

    /** Textura branca tingida pela cor do vértice, somada à cena (círculos mágicos, runas). */
    static RenderType additive(ResourceLocation texture) {
        return ADDITIVE.computeIfAbsent(texture, t -> RenderType.create("isekai_mahou_le_add_" + t.getPath().replace('/', '_'),
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

    /** Textura tingida e misturada por alfa (pode escurecer: o selo negro da Cabra). */
    static RenderType shade(ResourceLocation texture) {
        return SHADE.computeIfAbsent(texture, t -> RenderType.create("isekai_mahou_le_shade_" + t.getPath().replace('/', '_'),
                DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 16384, false, true,
                RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionTexColorShader))
                        .setTextureState(new RenderStateShard.TextureStateShard(t, false, false))
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setCullState(RenderStateShard.NO_CULL)
                        .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .createCompositeState(false)));
    }

    static ResourceLocation tex(String name) {
        return IsekaiMahou.id("textures/entity/legends_e/" + name + ".png");
    }

    static final ResourceLocation RING_OUTER = tex("supreme_ring_outer");
    static final ResourceLocation RING_MID = tex("supreme_ring_mid");
    static final ResourceLocation SIGIL_CORE = tex("supreme_sigil_core");
    static final ResourceLocation RUNE_BAND = tex("supreme_rune_band");
    static final ResourceLocation CLOCK_FACE = tex("clock_face");
    static final ResourceLocation GOAT_SIGIL = tex("goat_sigil");

    private LeRenderTypes() {}
}
