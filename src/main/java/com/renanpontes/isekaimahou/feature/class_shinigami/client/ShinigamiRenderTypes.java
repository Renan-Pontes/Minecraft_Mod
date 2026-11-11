package com.renanpontes.isekaimahou.feature.class_shinigami.client;

import java.util.HashMap;
import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.ParticleStatus;
import net.minecraft.util.Mth;

/**
 * Tipos de render dos efeitos do Shinigami: textura branca tingida pela cor do vértice, sem cull, testa profundidade
 * mas não escreve. {@link #additive} soma luz (brilhos, anéis, cortes); {@link #translucent} mistura normal (pétalas,
 * lâminas, imagem residual, cicatrizes escuras). Também os ajudantes de quad virado para a câmera.
 */
public final class ShinigamiRenderTypes {
    public static final ResourceLocation PETAL = tex("shinigami_fx_petal");
    public static final ResourceLocation GLOW = tex("shinigami_fx_glow");
    public static final ResourceLocation RING = tex("shinigami_fx_ring");
    public static final ResourceLocation SWORD = tex("shinigami_fx_sword");
    public static final ResourceLocation GHOST = tex("shinigami_fx_ghost");
    public static final ResourceLocation SLASH = tex("shinigami_fx_slash");
    public static final ResourceLocation MASK = tex("shinigami_fx_mask");
    public static final ResourceLocation SPIKE = tex("shinigami_fx_spike");
    public static final ResourceLocation FLAME = tex("shinigami_fx_flame");

    private static final Map<ResourceLocation, RenderType> ADD = new HashMap<>();
    private static final Map<ResourceLocation, RenderType> TRANS = new HashMap<>();

    public static RenderType additive(ResourceLocation texture) {
        return ADD.computeIfAbsent(texture, t -> RenderType.create("isekai_mahou_shinigami_add_" + t.getPath().replace('/', '_'),
                DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 32768, false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionTexColorShader))
                        .setTextureState(new RenderStateShard.TextureStateShard(t, false, false))
                        .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                        .setCullState(RenderStateShard.NO_CULL)
                        .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .createCompositeState(false)));
    }

    public static RenderType translucent(ResourceLocation texture) {
        return TRANS.computeIfAbsent(texture, t -> RenderType.create("isekai_mahou_shinigami_trans_" + t.getPath().replace('/', '_'),
                DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 65536, false, true,
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
        return IsekaiMahou.id("textures/entity/" + name + ".png");
    }

    /** Fator da configuração de partículas (Todas 1 · Reduzidas 0,5 · Mínimas 0,25). */
    public static float particleScale() {
        ParticleStatus s = Minecraft.getInstance().options.particles().get();
        return s == ParticleStatus.ALL ? 1F : s == ParticleStatus.DECREASED ? 0.5F : 0.25F;
    }

    public static int argb(int rgb, float a) {
        return ((int) (Mth.clamp(a, 0F, 1F) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    public static int mix(int a, int b, float t) {
        int r = (int) Mth.lerp(t, (a >> 16) & 255, (b >> 16) & 255);
        int g = (int) Mth.lerp(t, (a >> 8) & 255, (b >> 8) & 255);
        int bl = (int) Mth.lerp(t, a & 255, b & 255);
        return (r << 16) | (g << 8) | bl;
    }

    /** Quad virado para a câmera, girado {@code rot} no plano, com a largura achatada por {@code squash}. */
    public static void billboard(VertexConsumer vc, Matrix4f m, Vector3f left, Vector3f up, float x, float y, float z, float w, float h,
                                 float rot, int argb) {
        // x/y/z já são relativos à câmera: quad colado na lente (1ª pessoa) vira um borrão gigante — não desenha
        if (x * x + y * y + z * z < 0.64F) return;
        float c = Mth.cos(rot), s = Mth.sin(rot);
        float ax = (left.x() * c + up.x() * s) * w, ay = (left.y() * c + up.y() * s) * w, az = (left.z() * c + up.z() * s) * w;
        float bx = (-left.x() * s + up.x() * c) * h, by = (-left.y() * s + up.y() * c) * h, bz = (-left.z() * s + up.z() * c) * h;
        vc.addVertex(m, x - ax - bx, y - ay - by, z - az - bz).setUv(0, 1).setColor(argb);
        vc.addVertex(m, x - ax + bx, y - ay + by, z - az + bz).setUv(0, 0).setColor(argb);
        vc.addVertex(m, x + ax + bx, y + ay + by, z + az + bz).setUv(1, 0).setColor(argb);
        vc.addVertex(m, x + ax - bx, y + ay - by, z + az - bz).setUv(1, 1).setColor(argb);
    }

    /** Quad entre quatro cantos (a-b embaixo, d-c em cima), textura inteira. */
    public static void quad(VertexConsumer vc, Matrix4f m, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy,
                            float cz, float dx, float dy, float dz, int argb) {
        vc.addVertex(m, ax, ay, az).setUv(0, 1).setColor(argb);
        vc.addVertex(m, bx, by, bz).setUv(1, 1).setColor(argb);
        vc.addVertex(m, cx, cy, cz).setUv(1, 0).setColor(argb);
        vc.addVertex(m, dx, dy, dz).setUv(0, 0).setColor(argb);
    }

    /** Quad horizontal centrado (meia-largura {@code r}), girado {@code rot} radianos. */
    public static void flat(VertexConsumer vc, Matrix4f m, float x, float y, float z, float r, float rot, int argb) {
        float cs = Mth.cos(rot) * r, sn = Mth.sin(rot) * r;
        vc.addVertex(m, x + (-cs + sn), y, z + (-sn - cs)).setUv(0, 0).setColor(argb);
        vc.addVertex(m, x + (cs + sn), y, z + (sn - cs)).setUv(1, 0).setColor(argb);
        vc.addVertex(m, x + (cs - sn), y, z + (sn + cs)).setUv(1, 1).setColor(argb);
        vc.addVertex(m, x + (-cs - sn), y, z + (-sn + cs)).setUv(0, 1).setColor(argb);
    }

    private ShinigamiRenderTypes() {}
}
