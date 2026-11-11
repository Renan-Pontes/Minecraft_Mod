package com.renanpontes.isekaimahou.feature.legends_b.client;

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
 * Tipos de render das lendas: brilho ADITIVO (soma luz, nunca escurece), sem cull, testa profundidade mas não
 * escreve — camadas de esfera/halo não se escondem umas atrás das outras e não cortam o que vem depois.
 */
final class LbRenderTypes {
    /** Brilho aditivo de cor por vértice (esferas de brilho, halos, anéis), no passe das entidades. */
    static final RenderType GLOW = glow("isekai_mahou_lb_glow", RenderStateShard.MAIN_TARGET);
    /**
     * O mesmo, desenhado no estágio AFTER_PARTICLES (cortes, ondas de choque, clarões). Saída "main" (não troca de alvo):
     * no gráfico Fabuloso o alvo das partículas já está ligado nesse estágio, e PARTICLES_TARGET o desligaria ao terminar
     * (quem desenhasse depois de nós cairia no alvo errado).
     */
    static final RenderType GLOW_FX = glow("isekai_mahou_lb_glow_fx", RenderStateShard.MAIN_TARGET);

    private static final Map<ResourceLocation, RenderType> TEXTURED = new HashMap<>();

    private static RenderType glow(String name, RenderStateShard.OutputStateShard output) {
        return RenderType.create(name, DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 65536, false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                        .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                        .setCullState(RenderStateShard.NO_CULL)
                        .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .setOutputState(output)
                        .createCompositeState(false));
    }

    /** Textura branca tingida pela cor do vértice, somada à cena (círculos mágicos). */
    static RenderType additiveTexture(ResourceLocation texture) {
        return TEXTURED.computeIfAbsent(texture, t -> RenderType.create("isekai_mahou_lb_tex_" + t.getPath().replace('/', '_'),
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
        return IsekaiMahou.id("textures/entity/legends_b/" + name + ".png");
    }

    private LbRenderTypes() {}
}
