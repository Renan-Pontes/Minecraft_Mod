package com.renanpontes.isekaimahou.feature.ui_hud.client;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.client.GlyphArt;
import com.renanpontes.isekaimahou.client.hud.HudLayout;
import com.renanpontes.isekaimahou.core.MahouConfig;
import com.renanpontes.isekaimahou.glyph.MoveTrigger;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Barra de conjuração central (abaixo da mira) com o nome da magia; o cântico surge palavra por palavra
 * ACIMA da mira (assim cânticos de várias linhas não cobrem a mira).
 * Canalizar: depois de encher, a barra fica pulsando enquanto a magia se repete.
 * Ao terminar: lampejo branco (completa) ou vermelho (interrompida) que some em alguns ticks.
 */
final class HudCastBar {
    private static final int W = 112;
    private static final int H = 5;
    private static final int MAX_CHANT_WIDTH = 260;
    private static final int CHANT_COLOR = 0xFFE4CEFF;

    /** Layout do cântico em cache (refeito só quando o texto ou a largura mudam). */
    private static String layoutFor = null;
    private static int layoutWidth = -1;
    private static final List<List<String>> LINES = new ArrayList<>();
    private static int wordCount;

    /** Reserva na região ARENA do {@link HudLayout} (o aviso "Limite da arena" desce enquanto a barra aparece). */
    static final String RESERVE_ID = "ui_hud_cast";
    /** Altura da barra + nome da magia. */
    private static final int BLOCK_H = 26;

    private HudCastBar() {}

    /** Altura ocupada abaixo da mira agora (0 = sem conjuração). */
    static int reservedHeight() {
        if (!HudDraw.config(MahouConfig.HUD_CAST_BAR)) return 0;
        return HudState.casting || HudState.castEndTicks > 0 ? HudScale.toReal(BLOCK_H) : 0;
    }

    static void render(GuiGraphics g, Minecraft mc, float partialTick) {
        boolean active = HudState.casting;
        if (!active && HudState.castEndTicks <= 0) return;
        Font font = mc.font;

        int gw = HudScale.w(g), gh = HudScale.h(g);
        int cx = gw / 2;
        // Logo abaixo da mira, na região ARENA do HudLayout (depois do que reservou antes; o aviso da arena desce)
        int x = cx - W / 2, y = gh / 2 + 24 + HudScale.v(HudLayout.reservedBefore(HudLayout.Region.ARENA, RESERVE_ID));
        float progress, alpha = 1F;
        int color = HudState.castColor;
        boolean channeling = HudState.channelingPhase();
        if (active) {
            progress = HudState.castProgress(partialTick);
        } else {
            alpha = Mth.clamp((HudState.castEndTicks - partialTick) / HudState.CAST_END_TICKS, 0F, 1F);
            progress = HudState.castEndProgress;
            if (!HudState.castEndOk) color = HudDraw.RED;
        }
        if (alpha <= 0.02F) return;
        String chant = HudState.castChant;

        // Moldura com pontas em losango (pulsa na canalização)
        float pulse = channeling ? HudDraw.pulse(700) : 0F;
        int frame = HudDraw.fade(HudDraw.lerp(0.35F + 0.4F * pulse, color, 0xFFFFFFFF), alpha);
        if (channeling) HudDraw.glow(g, x - 2, y - 2, W + 4, H + 4, color, 0.4F + 0.6F * pulse, 3);
        g.fill(x - 2, y - 2, x + W + 2, y + H + 2, HudDraw.fade(0xE0080A12, alpha));
        g.renderOutline(x - 2, y - 2, W + 4, H + 4, frame);
        HudDraw.diamond(g, x - 4, y + H / 2, 3, frame);
        HudDraw.diamond(g, x + W + 3, y + H / 2, 3, frame);
        // Marcas de quarto
        for (int q = 1; q < 4; q++) g.fill(x + W * q / 4, y + H, x + W * q / 4 + 1, y + H + 2, HudDraw.fade(frame, 0.6F));

        int fw = Math.round(W * progress);
        if (fw > 0) {
            g.fillGradient(x, y, x + fw, y + H, HudDraw.fade(HudDraw.brighten(color, 0.35F), alpha),
                    HudDraw.fade(HudDraw.darken(color, 0.25F), alpha));
            g.fill(x, y, x + fw, y + 1, HudDraw.fade(0x60FFFFFF, alpha));
        }
        if (channeling) {
            // Ondas de energia correndo pela barra cheia
            float t = (Util.getMillis() % 900) / 900F;
            for (int k = 0; k < 2; k++) {
                int sx = x + Math.round(((t + k * 0.5F) % 1F) * (W + 12)) - 8;
                int s0 = Math.max(x, sx), s1 = Math.min(x + W, sx + 8);
                if (s1 > s0) g.fill(s0, y, s1, y + H, 0x80FFFFFF);
            }
        } else if (active && fw > 0 && fw < W) {
            g.fill(x + fw - 1, y - 1, x + fw + 1, y + H + 1, 0xFFFFFFFF);
            HudDraw.glow(g, x + fw - 1, y - 1, 2, H + 2, color, 0.8F, 2);
        }
        if (!active) {
            // Lampejo de fim: branco se completou, vermelho se foi interrompida
            int flash = HudState.castEndOk ? 0xFFFFFFFF : HudDraw.RED;
            g.fill(x, y, x + W, y + H, HudDraw.fade(flash, alpha * 0.7F));
            int spread = Math.round((1F - alpha) * 5F);
            HudDraw.glow(g, x - 2 - spread, y - 2 - spread, W + 4 + spread * 2, H + 4 + spread * 2, flash, alpha, 2);
        }

        // Nome da magia: ícone dela à esquerda e, se não for a versão normal, o ícone do movimento à direita
        Component name = HudState.castName;
        if (!name.getString().isEmpty()) {
            int nameColor = HudDraw.fade(HudDraw.brighten(color, 0.45F), alpha);
            boolean icon = HudState.castRecipe != null;
            boolean move = HudState.castTrigger != MoveTrigger.NORMAL;
            int nw = font.width(name);
            int total = nw + (icon ? 13 : 0) + (move ? 10 : 0);
            int ny = y + H + 5;
            int nx = cx - total / 2;
            if (icon) {
                g.setColor(1F, 1F, 1F, alpha);
                GlyphArt.spell(g, HudState.castRecipe, HudState.castElement, nx, ny - 2, 11);
                g.setColor(1F, 1F, 1F, 1F);
                nx += 13;
            }
            HudDraw.text(g, font, name, nx, ny, nameColor, true);
            if (move) {
                HudDraw.sprite(g, HudSpellBar.triggerIcon(HudState.castTrigger), nx + nw + 3, ny,
                        HudDraw.fade(HudSpellBar.triggerColor(HudState.castTrigger), alpha));
            }
        }

        // Cântico palavra por palavra, acima da mira (as linhas crescem para cima)
        if (chant != null && !chant.isBlank()) {
            float wordsProgress = active && !channeling ? Math.min(1F, progress / 0.85F) : 1F;
            int width = Math.min(MAX_CHANT_WIDTH, gw - 40);
            renderChant(g, font, chant, width, cx, gh / 2 - 12, wordsProgress, alpha);
        }
    }

    /** Quebra o cântico em linhas UMA vez (com o texto completo) para as palavras não "pularem" ao aparecer. */
    private static void layout(Font font, String chant, int width) {
        if (chant.equals(layoutFor) && width == layoutWidth) return;
        layoutFor = chant;
        layoutWidth = width;
        LINES.clear();
        wordCount = 0;
        List<String> line = new ArrayList<>();
        int lineW = 0;
        int space = font.width(" ");
        for (String w : chant.trim().split("\\s+")) {
            int ww = font.width(italic(w));
            if (!line.isEmpty() && lineW + space + ww > width) {
                LINES.add(line);
                line = new ArrayList<>();
                lineW = 0;
            }
            lineW += (line.isEmpty() ? 0 : space) + ww;
            line.add(w);
            wordCount++;
        }
        if (!line.isEmpty()) LINES.add(line);
    }

    private static Component italic(String word) {
        return Component.literal(word).withStyle(ChatFormatting.ITALIC);
    }

    private static void renderChant(GuiGraphics g, Font font, String chant, int width, int cx, int bottomY,
                                    float wordsProgress, float alpha) {
        layout(font, chant, width);
        if (wordCount == 0) return;
        float revealed = wordsProgress * wordCount;
        int space = font.width(" ");
        int lineH = 10;
        int top = bottomY - LINES.size() * lineH;

        // Fundo suave atrás de todo o cântico (fica estável enquanto as palavras surgem)
        int maxW = 0;
        for (List<String> line : LINES) maxW = Math.max(maxW, lineWidth(font, line, space));
        g.fillGradient(cx - maxW / 2 - 6, top - 3, cx + maxW / 2 + 6, bottomY + 1,
                HudDraw.fade(0x30000000, alpha), HudDraw.fade(0x70000000, alpha));

        int index = 0;
        for (int li = 0; li < LINES.size(); li++) {
            List<String> line = LINES.get(li);
            int lx = cx - lineWidth(font, line, space) / 2;
            int ly = top + li * lineH;
            for (String w : line) {
                float shown = Mth.clamp(revealed - index, 0F, 1F);
                float a = shown * alpha;
                Component word = italic(w);
                if (a > 0.03F) {
                    // a palavra mais nova surge clareando a partir do dourado e "assenta" 2 px
                    int col = HudDraw.lerp(shown, HudDraw.GOLD_LIGHT, CHANT_COLOR);
                    g.drawString(font, word, lx, ly + Math.round((1F - shown) * 2F), HudDraw.fade(col, a), true);
                }
                lx += font.width(word) + space;
                index++;
            }
        }
    }

    private static int lineWidth(Font font, List<String> line, int space) {
        int w = 0;
        for (String s : line) w += font.width(italic(s));
        return w + Math.max(0, line.size() - 1) * space;
    }
}
