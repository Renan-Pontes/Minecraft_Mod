package com.renanpontes.isekaimahou.feature.legends_d.client;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Tipos de render do legends_d: brilho ADITIVO (soma luz), textura aditiva (círculos mágicos) e o NEGATIVO:
 * a cor que já está na tela é invertida (1 − destino). Duas camadas de negativo se anulam — é assim que a onda do
 * "Parar o Tempo" inverte só o que está DENTRO da esfera.
 */
final class LdRenderTypes {
    static final RenderType GLOW = RenderType.create("isekai_mahou_ld_glow", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 65536, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setOutputState(RenderStateShard.MAIN_TARGET)
                    .createCompositeState(false));

    private static final RenderStateShard.TransparencyStateShard INVERT_BLEND = new RenderStateShard.TransparencyStateShard(
            "isekai_mahou_ld_invert", () -> {
                RenderSystem.enableBlend();
                RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR, GlStateManager.DestFactor.ZERO,
                        GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
            }, () -> {
                RenderSystem.disableBlend();
                RenderSystem.defaultBlendFunc();
            });

    /** Negativo com teste de profundidade (cascas de esfera no mundo). Cor do vértice: branco. */
    static final RenderType INVERT = invert("isekai_mahou_ld_invert", RenderStateShard.LEQUAL_DEPTH_TEST);
    /** Negativo sem profundidade (a tela inteira, para quem está dentro da esfera). */
    static final RenderType INVERT_SCREEN = invert("isekai_mahou_ld_invert_screen", RenderStateShard.NO_DEPTH_TEST);

    private static final Map<ResourceLocation, RenderType> TEXTURED = new HashMap<>();

    private static RenderType invert(String name, RenderStateShard.DepthTestStateShard depth) {
        return RenderType.create(name, DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 65536, false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                        .setTransparencyState(INVERT_BLEND)
                        .setCullState(RenderStateShard.NO_CULL)
                        .setDepthTestState(depth)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .setOutputState(RenderStateShard.MAIN_TARGET)
                        .createCompositeState(false));
    }

    /** Textura branca tingida pela cor do vértice, somada à cena. */
    static RenderType additiveTexture(ResourceLocation texture) {
        return TEXTURED.computeIfAbsent(texture, t -> RenderType.create("isekai_mahou_ld_tex_" + t.getPath().replace('/', '_'),
                DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 16384, false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionTexColorShader))
                        .setTextureState(new RenderStateShard.TextureStateShard(t, false, false))
                        .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                        .setCullState(RenderStateShard.NO_CULL)
                        .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .setOutputState(RenderStateShard.MAIN_TARGET)
                        .createCompositeState(false)));
    }

    static ResourceLocation tex(String name) {
        return IsekaiMahou.id("textures/entity/legends_d/" + name + ".png");
    }

    private LdRenderTypes() {}
}
