package com.renanpontes.isekaimahou.feature.class_hollow.client;

import java.util.HashMap;
import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Tipos de render dos efeitos do Hollow: textura branca tingida pela cor do vértice, sem cull, sem escrever profundidade.
 * {@link #additive} soma luz (Cero, brilhos, anéis); {@link #translucent} mistura normal (rasgos negros da Garganta,
 * imagem residual); {@link #xray} ignora a profundidade (os marcadores da Pesquisa, vistos através das paredes).
 * Usa as texturas de efeito já existentes do mod (brilho, anel, fantasma, espinho).
 */
public final class HollowRenderTypes {
    public static final ResourceLocation GLOW = tex("shinigami_fx_glow");
    public static final ResourceLocation RING = tex("shinigami_fx_ring");
    public static final ResourceLocation GHOST = tex("shinigami_fx_ghost");
    public static final ResourceLocation SPIKE = tex("shinigami_fx_spike");
    public static final ResourceLocation FLAME = tex("shinigami_fx_flame");

    private static final Map<ResourceLocation, RenderType> ADD = new HashMap<>();
    private static final Map<ResourceLocation, RenderType> TRANS = new HashMap<>();
    private static final Map<ResourceLocation, RenderType> XRAY = new HashMap<>();

    public static RenderType additive(ResourceLocation texture) {
        return ADD.computeIfAbsent(texture, t -> create("add_", t, RenderStateShard.LIGHTNING_TRANSPARENCY, RenderStateShard.LEQUAL_DEPTH_TEST));
    }

    public static RenderType translucent(ResourceLocation texture) {
        return TRANS.computeIfAbsent(texture, t -> create("trans_", t, RenderStateShard.TRANSLUCENT_TRANSPARENCY, RenderStateShard.LEQUAL_DEPTH_TEST));
    }

    public static RenderType xray(ResourceLocation texture) {
        return XRAY.computeIfAbsent(texture, t -> create("xray_", t, RenderStateShard.LIGHTNING_TRANSPARENCY, RenderStateShard.NO_DEPTH_TEST));
    }

    private static RenderType create(String prefix, ResourceLocation t, RenderStateShard.TransparencyStateShard blend,
                                     RenderStateShard.DepthTestStateShard depth) {
        return RenderType.create("isekai_mahou_hollow_" + prefix + t.getPath().replace('/', '_'),
                DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 32768, false, true,
                RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionTexColorShader))
                        .setTextureState(new RenderStateShard.TextureStateShard(t, false, false))
                        .setTransparencyState(blend)
                        .setCullState(RenderStateShard.NO_CULL)
                        .setDepthTestState(depth)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .createCompositeState(false));
    }

    static ResourceLocation tex(String name) {
        return IsekaiMahou.id("textures/entity/" + name + ".png");
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

    /** Quad virado para a câmera (posição já relativa à câmera), girado {@code rot}. */
    public static void billboard(VertexConsumer vc, Matrix4f m, Vector3f left, Vector3f up, float x, float y, float z, float w, float h,
                                 float rot, int argb) {
        // colado na lente (1ª pessoa) vira um borrão gigante: não desenha
        if (x * x + y * y + z * z < 0.5F) return;
        float c = Mth.cos(rot), s = Mth.sin(rot);
        float ax = (left.x() * c + up.x() * s) * w, ay = (left.y() * c + up.y() * s) * w, az = (left.z() * c + up.z() * s) * w;
        float bx = (-left.x() * s + up.x() * c) * h, by = (-left.y() * s + up.y() * c) * h, bz = (-left.z() * s + up.z() * c) * h;
        vc.addVertex(m, x - ax - bx, y - ay - by, z - az - bz).setUv(0, 1).setColor(argb);
        vc.addVertex(m, x - ax + bx, y - ay + by, z - az + bz).setUv(0, 0).setColor(argb);
        vc.addVertex(m, x + ax + bx, y + ay + by, z + az + bz).setUv(1, 0).setColor(argb);
        vc.addVertex(m, x + ax - bx, y + ay - by, z + az - bz).setUv(1, 1).setColor(argb);
    }

    /**
     * Fita de a até b virada para a câmera (posições relativas à câmera), meia-largura {@code w}. A textura é amostrada
     * na faixa do meio (v = 0,5): com a textura de brilho, o centro da fita fica forte e as bordas somem.
     */
    public static void ribbon(VertexConsumer vc, Matrix4f m, float ax, float ay, float az, float bx, float by, float bz, float wa, float wb,
                              int argbA, int argbB) {
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        // lado = direção × (vetor até a câmera, no meio da fita)
        float mx = -(ax + bx) * 0.5F, my = -(ay + by) * 0.5F, mz = -(az + bz) * 0.5F;
        float sx = dy * mz - dz * my, sy = dz * mx - dx * mz, sz = dx * my - dy * mx;
        float len = Mth.sqrt(sx * sx + sy * sy + sz * sz);
        if (len < 1.0E-5F) return;
        sx /= len;
        sy /= len;
        sz /= len;
        vc.addVertex(m, ax - sx * wa, ay - sy * wa, az - sz * wa).setUv(0, 0.5F).setColor(argbA);
        vc.addVertex(m, ax + sx * wa, ay + sy * wa, az + sz * wa).setUv(1, 0.5F).setColor(argbA);
        vc.addVertex(m, bx + sx * wb, by + sy * wb, bz + sz * wb).setUv(1, 0.5F).setColor(argbB);
        vc.addVertex(m, bx - sx * wb, by - sy * wb, bz - sz * wb).setUv(0, 0.5F).setColor(argbB);
    }

    /** Quad horizontal centrado (meia-largura {@code r}), girado {@code rot}. */
    public static void flat(VertexConsumer vc, Matrix4f m, float x, float y, float z, float r, float rot, int argb) {
        float cs = Mth.cos(rot) * r, sn = Mth.sin(rot) * r;
        vc.addVertex(m, x + (-cs + sn), y, z + (-sn - cs)).setUv(0, 0).setColor(argb);
        vc.addVertex(m, x + (cs + sn), y, z + (sn - cs)).setUv(1, 0).setColor(argb);
        vc.addVertex(m, x + (cs - sn), y, z + (sn + cs)).setUv(1, 1).setColor(argb);
        vc.addVertex(m, x + (-cs - sn), y, z + (-sn + cs)).setUv(0, 1).setColor(argb);
    }

    /** Quad vertical que gira só em Y para olhar a câmera (rasgos, colunas). Base em y, altura h, meia-largura w. */
    public static void pillar(VertexConsumer vc, Matrix4f m, float x, float y, float z, float w, float h, int argbBottom, int argbTop) {
        float dx = -x, dz = -z;
        float len = Mth.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-4F) return;
        float sx = -dz / len * w, sz = dx / len * w;
        vc.addVertex(m, x - sx, y, z - sz).setUv(0, 1).setColor(argbBottom);
        vc.addVertex(m, x + sx, y, z + sz).setUv(1, 1).setColor(argbBottom);
        vc.addVertex(m, x + sx, y + h, z + sz).setUv(1, 0).setColor(argbTop);
        vc.addVertex(m, x - sx, y + h, z - sz).setUv(0, 0).setColor(argbTop);
    }

    private HollowRenderTypes() {}
}
