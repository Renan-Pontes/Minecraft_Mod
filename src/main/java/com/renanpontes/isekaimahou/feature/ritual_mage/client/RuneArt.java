package com.renanpontes.isekaimahou.feature.ritual_mage.client;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.ritual_mage.research.ThesisPuzzle;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

/** Sprites das runas (textures/gui/mage_runes.png) e utilidades de cor para desenhar em pergaminho. */
public final class RuneArt {
    public static final ResourceLocation RUNES = IsekaiMahou.id("textures/gui/mage_runes.png");
    private static final int TW = 256, TH = 64;

    /** Cor de "tinta" no papel (texto escuro). */
    public static final int INK = 0xFF3B2A1A;
    public static final int INK_SOFT = 0xFF6E5638;
    public static final int INK_FADED = 0xFF9C8762;
    public static final int GOOD = 0xFF2E7D32;
    public static final int BAD = 0xFFB3261E;
    public static final int WARN = 0xFFB86E00;

    public static void rune(GuiGraphics g, Element e, int x, int y) {
        int i = e.ordinal();
        g.blit(RUNES, x, y, (i % 16) * 16, (i / 16) * 16, 16, 16, TW, TH);
    }

    /** Runa em meio tamanho (8×8). */
    public static void smallRune(GuiGraphics g, Element e, int x, int y) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.5F, 0.5F, 1F);
        rune(g, e, 0, 0);
        g.pose().popPose();
    }

    public static void fissure(GuiGraphics g, int x, int y) { g.blit(RUNES, x, y, 0, 32, 19, 19, TW, TH); }
    public static void entryFrame(GuiGraphics g, int x, int y) { g.blit(RUNES, x, y, 20, 32, 19, 19, TW, TH); }
    public static void exitFrame(GuiGraphics g, int x, int y) { g.blit(RUNES, x, y, 40, 32, 19, 19, TW, TH); }
    public static void lock(GuiGraphics g, int x, int y) { g.blit(RUNES, x, y, 60, 32, 7, 7, TW, TH); }
    public static void select(GuiGraphics g, int x, int y) { g.blit(RUNES, x, y, 70, 32, 18, 18, TW, TH); }
    public static void conflict(GuiGraphics g, int x, int y) { g.blit(RUNES, x, y, 90, 32, 9, 9, TW, TH); }

    /** Cor do elemento escurecida para ficar legível no pergaminho. */
    public static int ink(Element e) {
        return scale(e.color, 0.55F);
    }

    public static int scale(int rgb, float f) {
        int r = (int) (((rgb >> 16) & 255) * f), gg = (int) (((rgb >> 8) & 255) * f), b = (int) ((rgb & 255) * f);
        return 0xFF000000 | (r << 16) | (gg << 8) | b;
    }

    /** Mistura de duas cores RGB, com alpha. */
    public static int blend(int a, int b, int alpha) {
        int r = (((a >> 16) & 255) + ((b >> 16) & 255)) / 2;
        int gg = (((a >> 8) & 255) + ((b >> 8) & 255)) / 2;
        int bl = ((a & 255) + (b & 255)) / 2;
        return (alpha << 24) | (r << 16) | (gg << 8) | bl;
    }

    /** "Combina com: ..." para a dica de uma runa. */
    public static MutableComponent compatibility(Element e) {
        if (e == Element.ARCANE) return Component.translatable("screen.isekai_mahou.research_table.compat_any");
        List<Component> parts = new ArrayList<>();
        if (e.isBase()) {
            parts.add(Component.translatable("screen.isekai_mahou.research_table.compat_bases"));
            for (Element f : Element.FUSIONS) if (f.parentA == e || f.parentB == e) parts.add(f.displayName());
        } else {
            for (Element other : Element.values()) {
                if (other != Element.ARCANE && ThesisPuzzle.compatible(e, other)) parts.add(other.displayName());
            }
        }
        parts.add(Element.ARCANE.displayName());
        MutableComponent out = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) out.append(", ");
            out.append(parts.get(i));
        }
        return out;
    }

    private RuneArt() {}
}
