package com.renanpontes.isekaimahou.client.boss;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Tipos de render das telegrafias: translúcidos, sem cull (vê dos dois lados), testam profundidade mas NÃO escrevem
 * (o decalque nunca esconde o que está por cima), com polygon offset contra z-fighting no chão.
 * Saída no alvo de partículas: no gráfico "Fabuloso" entra na composição certa; nos outros é o alvo principal.
 */
final class TelegraphRenderTypes {
    static final ResourceLocation RUNES_TEXTURE = IsekaiMahou.id("textures/misc/telegraph_runes.png");
    static final ResourceLocation CHEVRON_TEXTURE = IsekaiMahou.id("textures/misc/telegraph_chevron.png");

    static final RenderType COLOR = RenderType.create("isekai_mahou_telegraph",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 262144, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setLayeringState(RenderStateShard.POLYGON_OFFSET_LAYERING)
                    .setOutputState(RenderStateShard.PARTICLES_TARGET)
                    .createCompositeState(false));

    static final RenderType RUNES = textured("isekai_mahou_telegraph_runes", RUNES_TEXTURE);
    static final RenderType CHEVRONS = textured("isekai_mahou_telegraph_chevrons", CHEVRON_TEXTURE);

    private static RenderType textured(String name, ResourceLocation texture) {
        return RenderType.create(name, DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 65536, false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionTexColorShader))
                        .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setCullState(RenderStateShard.NO_CULL)
                        .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .setLayeringState(RenderStateShard.POLYGON_OFFSET_LAYERING)
                        .setOutputState(RenderStateShard.PARTICLES_TARGET)
                        .createCompositeState(false));
    }

    private TelegraphRenderTypes() {}
}
