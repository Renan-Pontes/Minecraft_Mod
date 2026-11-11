package com.renanpontes.isekaimahou.feature.legends_f.client;

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
 * Tipos de render das técnicas do legends_f: brilho ADITIVO (soma luz: auras, anéis, olhos, hexágonos) e "sombra"
 * TRANSLÚCIDA (escurece: chamas negras do Monarca, mãos da Inveja). Sem cull, testam profundidade sem escrevê-la.
 * As peças sólidas (vassoura, Kodama, motosserras, cervo) usam os tipos de entidade do vanilla com {@link #WHITE}.
 */
final class LfRenderTypes {
    static final RenderType GLOW = RenderType.create("isekai_mahou_lf_glow", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 65536, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));

    static final RenderType DARK = RenderType.create("isekai_mahou_lf_dark", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 65536, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));

    private static final Map<ResourceLocation, RenderType> ADDITIVE = new HashMap<>();

    /** Textura branca tingida pela cor do vértice, somada à cena (hexágonos, selos, runas). */
    static RenderType additive(ResourceLocation texture) {
        return ADDITIVE.computeIfAbsent(texture, t -> RenderType.create("isekai_mahou_lf_add_" + t.getPath().replace('/', '_'),
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
        return IsekaiMahou.id("textures/entity/legends_f/" + name + ".png");
    }

    /** Branco liso (peças sólidas coloridas por vértice, com luz e sombra do mundo). */
    static final ResourceLocation WHITE = tex("white");
    /** Veios de madeira em tons de cinza (cabo da vassoura, galhadas). */
    static final ResourceLocation GRAIN = tex("grain");
    /** Ladrilho de hexágonos (Barreira, Detectar Mana). */
    static final ResourceLocation HEX = tex("hex");
    /** Selo do Monarca (anel de runas e coroa). */
    static final ResourceLocation MONARCH_SIGIL = tex("monarch_sigil");
    /** Selo do contrato (pentagrama e escrita infernal). */
    static final ResourceLocation CONTRACT_SEAL = tex("contract_seal");
    /** Pergaminho do contrato (com assinatura). */
    static final ResourceLocation CONTRACT_PAGE = tex("contract_page");
    /** "Z" do sono. */
    static final ResourceLocation SLEEP_Z = tex("sleep_z");

    private LfRenderTypes() {}
}
