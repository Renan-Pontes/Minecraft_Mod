package com.renanpontes.isekaimahou.feature.ui_grimoire.client;

import java.util.HashSet;
import java.util.Set;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.glyph.CircleStyle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Desenho do círculo mágico por magia ({@link CircleStyle}) nas telas do Grimório: prévia girando e ícones das
 * listas. Usa as MESMAS artes do círculo de conjuração do mundo ({@code textures/particle/fx_circle_*.png}),
 * tingidas com a cor escolhida.
 */
final class CircleArt {
    private static final Set<ResourceLocation> SMOOTHED = new HashSet<>();

    /** Arte da forma: 0 = o círculo padrão; com símbolo, o padrão vira a forma 1 (centro livre) — igual ao mundo. */
    static ResourceLocation shapeTexture(int shape, boolean hasSymbol) {
        if (shape <= 0) return IsekaiMahou.id(hasSymbol ? "textures/particle/fx_circle_s1.png" : "textures/particle/fx_circle_0.png");
        return IsekaiMahou.id("textures/particle/fx_circle_s" + Math.min(CircleStyle.SHAPES, shape) + ".png");
    }

    static ResourceLocation symbolTexture(int symbol) {
        return IsekaiMahou.id("textures/particle/fx_circle_y" + Math.max(1, Math.min(CircleStyle.SYMBOLS, symbol)) + ".png");
    }

    /**
     * O círculo inteiro (forma girando + símbolo parado no centro).
     * @param argb   cor já resolvida (a escolhida ou a da magia)
     * @param radius raio em pixels da tela
     * @param angle  rotação da forma em radianos
     */
    static void draw(GuiGraphics g, CircleStyle style, int argb, float cx, float cy, float radius, float angle) {
        boolean sym = style.symbol() > 0;
        quad(g, shapeTexture(style.shape(), sym), argb, cx, cy, radius, angle);
        if (sym) quad(g, symbolTexture(style.symbol()), Gfx.lighten(argb, 0.25F), cx, cy, radius * 0.34F, 0F);
    }

    /** Ícone parado de uma forma (listas e botões). */
    static void shapeIcon(GuiGraphics g, int shape, int argb, int x, int y, int size) {
        quad(g, shapeTexture(shape, false), argb, x + size / 2F, y + size / 2F, size / 2F, 0F);
    }

    /** Ícone de um símbolo; 0 = um tracinho ("nenhum"). */
    static void symbolIcon(GuiGraphics g, int symbol, int argb, int x, int y, int size) {
        if (symbol <= 0) {
            g.fill(x + size / 2 - 3, y + size / 2, x + size / 2 + 3, y + size / 2 + 1, argb);
            return;
        }
        quad(g, symbolTexture(symbol), argb, x + size / 2F, y + size / 2F, size / 2F - 0.5F, 0F);
    }

    private static void quad(GuiGraphics g, ResourceLocation tex, int argb, float cx, float cy, float radius, float angle) {
        smooth(tex);
        g.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        if (angle != 0F) g.pose().mulPose(Axis.ZP.rotation(angle));
        float s = radius / 32F;
        g.pose().scale(s, s, 1F);
        g.setColor(((argb >> 16) & 255) / 255F, ((argb >> 8) & 255) / 255F, (argb & 255) / 255F, 1F);
        g.blit(tex, -32, -32, 0F, 0F, 64, 64, 64, 64);
        g.setColor(1F, 1F, 1F, 1F);
        g.pose().popPose();
    }

    /** Filtro linear (uma vez por textura): as artes têm 128 px e são desenhadas pequenas — sem isso ficam serrilhadas. */
    private static void smooth(ResourceLocation tex) {
        if (!SMOOTHED.add(tex)) return;
        try {
            AbstractTexture t = Minecraft.getInstance().getTextureManager().getTexture(tex);
            t.setFilter(true, false);
        } catch (RuntimeException ignored) {
            // textura ausente num pacote de recursos: desenha o "sem textura" do jogo, sem quebrar a tela
        }
    }

    private CircleArt() {}
}
