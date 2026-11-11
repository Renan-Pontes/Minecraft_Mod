package com.renanpontes.isekaimahou.feature.ultimates.client;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Tipos de render do módulo: brilho ADITIVO (soma luz, nunca escurece), sem cull, testa profundidade mas não escreve —
 * camadas de brilho não se escondem umas atrás das outras. Uma versão só com cor (anéis, cortes) e outra com
 * textura branca tingida pela cor do vértice (círculo do céu, auréola, asas, rastro de fogo).
 */
final class UltRenderTypes {
    static final ResourceLocation GLOW_TEX = tex("glow");
    static final ResourceLocation FIRE_TAIL = tex("fire_tail");
    static final ResourceLocation SKY_SIGIL = tex("sky_sigil");
    static final ResourceLocation HALO = tex("halo");
    static final ResourceLocation WING = tex("wing");

    static final RenderType GLOW = RenderType.create("isekai_mahou_ult_glow", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 65536, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setOutputState(RenderStateShard.MAIN_TARGET)
                    .createCompositeState(false));

    private static final Map<ResourceLocation, RenderType> TEXTURED = new HashMap<>();

    /** Textura branca tingida pela cor do vértice, somada à cena. */
    static RenderType additive(ResourceLocation texture) {
        return TEXTURED.computeIfAbsent(texture, t -> RenderType.create("isekai_mahou_ult_tex_" + t.getPath().replace('/', '_'),
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
        return IsekaiMahou.id("textures/entity/ultimates/" + name + ".png");
    }

    /** ARGB a partir de RGB + intensidade 0..1 (a mistura é SRC_ALPHA, ONE: o alfa é a força do brilho). */
    static int argb(int rgb, float a) {
        return ((int) (Mth.clamp(a, 0F, 1F) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    static int lighten(int rgb, float f) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        return ((int) (r + (255 - r) * f) << 16) | ((int) (g + (255 - g) * f) << 8) | (int) (b + (255 - b) * f);
    }

    private UltRenderTypes() {}
}
