package com.renanpontes.isekaimahou.feature.cinematic.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/** Utilitários de desenho 2D compartilhados pelos anúncios e cutscenes (texto com contorno, brilho, ornamentos). */
final class CinematicDraw {
    static final ResourceLocation VIGNETTE = IsekaiMahou.id("textures/gui/cinematic_vignette.png");
    static final ResourceLocation GLOW = IsekaiMahou.id("textures/gui/cinematic_glow.png");
    static final ResourceLocation CORNER = IsekaiMahou.id("textures/gui/cinematic_corner.png");
    static final ResourceLocation DIAMOND = IsekaiMahou.id("textures/gui/cinematic_diamond.png");
    static final Style BOLD = Style.EMPTY.withBold(true);

    // ------------------------------------------------------------ cores e curvas

    /** RGB + alpha (0..1) → ARGB. */
    static int argb(int rgb, float alpha) {
        int a = Mth.clamp((int) (alpha * 255F), 0, 255);
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    /** Mistura dois RGB (t = 0 → a, 1 → b). */
    static int mix(int a, int b, float t) {
        t = Mth.clamp(t, 0F, 1F);
        int r = (int) Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
        int g = (int) Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
        int bl = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
        return (r << 16) | (g << 8) | bl;
    }

    /** Escurece um RGB (f = 0 → igual, 1 → preto). */
    static int darken(int rgb, float f) {
        return mix(rgb, 0x000000, f);
    }

    static float smooth(float x) {
        x = Mth.clamp(x, 0F, 1F);
        return x * x * (3F - 2F * x);
    }

    static float easeOut(float x) {
        x = Mth.clamp(x, 0F, 1F);
        float k = 1F - x;
        return 1F - k * k * k;
    }

    /** "Pulo" no fim (passa um pouco de 1 e volta), bom para textos que aparecem. */
    static float easeOutBack(float x) {
        x = Mth.clamp(x, 0F, 1F);
        float c1 = 1.70158F, c3 = c1 + 1F;
        float k = x - 1F;
        return 1F + c3 * k * k * k + c1 * k * k;
    }

    // ------------------------------------------------------------ texto

    /**
     * Texto com contorno de 1 unidade (8 cópias deslocadas + o texto). Com escala no PoseStack o contorno engrossa junto,
     * o que dá o visual "de anime" dos anúncios. Alpha muito baixo é ignorado (o Font trata alpha < 4 como opaco).
     */
    static void outlined(GuiGraphics g, Font font, FormattedCharSequence text, float x, float y, int color, int outline) {
        if ((color >>> 24) < 6) return;
        // As 9 cópias vão num lote só e são enviadas de uma vez (mesmo z + ordenação estável = ordem mantida)
        Matrix4f pose = g.pose().last().pose();
        MultiBufferSource.BufferSource buffer = g.bufferSource();
        if ((outline >>> 24) >= 6) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx != 0 || dy != 0) {
                        font.drawInBatch(text, x + dx, y + dy, outline, false, pose, buffer, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
                    }
                }
            }
        }
        font.drawInBatch(text, x, y, color, false, pose, buffer, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        g.flush();
    }

    /** Texto simples com sombra (ignora alpha muito baixo). */
    static void shadowed(GuiGraphics g, Font font, FormattedCharSequence text, float x, float y, int color) {
        if ((color >>> 24) < 6) return;
        g.drawString(font, text, x, y, color, true);
    }

    // ------------------------------------------------------------ formas e texturas

    /** Retângulo com degradê HORIZONTAL (o fillGradient do vanilla só faz vertical: giramos 90°). */
    static void fillGradientH(GuiGraphics g, float x0, float y0, float x1, float y1, int left, int right) {
        int w = Math.round(x1 - x0), h = Math.round(y1 - y0);
        if (w <= 0 || h <= 0) return;
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(x0, y1, 0F);
        ps.mulPose(Axis.ZP.rotationDegrees(-90F));
        g.fillGradient(0, 0, h, w, left, right);
        ps.popPose();
    }

    /** Linha decorativa que some nas pontas: do centro (cor cheia) para fora (transparente). */
    static void fadingLine(GuiGraphics g, float x0, float x1, float y, float thickness, int rgb, float alpha, boolean fadeToLeft) {
        int solid = argb(rgb, alpha), clear = argb(rgb, 0F);
        if (fadeToLeft) fillGradientH(g, x0, y, x1, y + thickness, clear, solid);
        else fillGradientH(g, x0, y, x1, y + thickness, solid, clear);
    }

    /** Brilho suave aditivo (textura de glow tingida), centrado em (cx, cy). */
    static void glow(GuiGraphics g, float cx, float cy, float w, float h, int rgb, float alpha) {
        if (alpha <= 0.01F || w < 1 || h < 1) return;
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);
        tinted(g, GLOW, cx - w / 2F, cy - h / 2F, w, h, 128, 64, rgb, alpha);
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /** Losango decorativo tingido, centrado em (cx, cy). */
    static void diamond(GuiGraphics g, float cx, float cy, float size, int rgb, float alpha) {
        blend(() -> tinted(g, DIAMOND, cx - size / 2F, cy - size / 2F, size, size, 9, 9, rgb, alpha));
    }

    /**
     * Ornamento de canto 16×16 com a "quina" em (x, y). quadrant: 0 = sup. esquerdo, 1 = sup. direito,
     * 2 = inf. direito, 3 = inf. esquerdo. Gira em vez de espelhar (espelhar inverte a face e o quad some com o culling).
     */
    static void corner(GuiGraphics g, float x, float y, float size, int quadrant, int rgb, float alpha) {
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(x, y, 0F);
        ps.mulPose(Axis.ZP.rotationDegrees(90F * quadrant));
        blend(() -> tinted(g, CORNER, 0F, 0F, size, size, 16, 16, rgb, alpha));
        ps.popPose();
    }

    /** Escurecimento radial da tela inteira (cutscenes). */
    static void vignette(GuiGraphics g, float alpha) {
        if (alpha <= 0.01F) return;
        blend(() -> tinted(g, VIGNETTE, 0F, 0F, g.guiWidth(), g.guiHeight(), 256, 256, 0xFFFFFF, alpha));
    }

    private static void blend(Runnable draw) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        draw.run();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    /** Desenha a textura inteira esticada no retângulo, multiplicada pela cor. */
    private static void tinted(GuiGraphics g, ResourceLocation tex, float x, float y, float w, float h, int texW, int texH,
                               int rgb, float alpha) {
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(x, y, 0F);
        ps.scale(w / texW, h / texH, 1F);
        g.setColor(((rgb >> 16) & 0xFF) / 255F, ((rgb >> 8) & 0xFF) / 255F, (rgb & 0xFF) / 255F, Mth.clamp(alpha, 0F, 1F));
        g.blit(tex, 0, 0, 0F, 0F, texW, texH, texW, texH);
        g.setColor(1F, 1F, 1F, 1F);
        ps.popPose();
    }

    private CinematicDraw() {}
}
