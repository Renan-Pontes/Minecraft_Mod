package com.renanpontes.isekaimahou.client.reputation;

import java.util.Locale;

import com.renanpontes.isekaimahou.client.GlyphArt;
import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.core.api.Reputation.Faction;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Emblemas das facções (cliente). Usa {@code textures/gui/faction/<id>.png} (32×32) e, se faltar, desenha um brasão
 * na cor da facção com a inicial do nome — a interface nunca fica com um buraco.
 */
public final class FactionArt {

    /** Emblema {@code size}×{@code size} em (x, y). */
    public static void emblem(GuiGraphics g, Faction faction, int x, int y, int size) {
        ResourceLocation tex = faction.emblem();
        if (GlyphArt.exists(tex)) {
            GlyphArt.icon(g, tex, x, y, size);
        } else {
            shield(g, faction, x, y, size);
        }
    }

    /** Brasão de reserva: escudo com borda clara, degradê da cor da facção, faixa de brilho e a inicial. */
    static void shield(GuiGraphics g, Faction faction, int x, int y, int size) {
        int base = faction.argb();
        int edge = MahouUi.lighten(base, 0.45F);
        int top = MahouUi.lighten(base, 0.1F);
        int bottom = MahouUi.darken(base, 0.55F);
        int body = Math.round(size * 0.58F);
        for (int i = 0; i < size; i++) {
            int inset = inset(i, size, body);
            g.fill(x + inset, y + i, x + size - inset, y + i + 1, edge);
        }
        for (int i = 1; i < size - 1; i++) {
            int inset = inset(i, size, body) + 1;
            if (inset * 2 >= size) continue;
            g.fill(x + inset, y + i, x + size - inset, y + i + 1, MahouUi.mix(top, bottom, i / (float) size));
        }
        // faixa de brilho diagonal no topo
        if (size >= 12) g.fill(x + 2, y + 2, x + size / 2, y + 3, MahouUi.alpha(0xFFFFFFFF, 0x50));
        // inicial
        Font font = Minecraft.getInstance().font;
        String name = faction.name().getString();
        String letter = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT);
        float scale = Math.max(0.5F, size / 18F);
        float tw = font.width(letter) * scale;
        g.pose().pushPose();
        g.pose().translate(x + (size - tw) / 2F + 0.5F, y + size * 0.42F - 4F * scale, 0);
        g.pose().scale(scale, scale, 1F);
        g.drawString(font, Component.literal(letter), 0, 0, MahouUi.GOLD_PALE, true);
        g.pose().popPose();
    }

    /** Recuo da linha i: retângulo em cima, ponta embaixo. */
    private static int inset(int row, int size, int body) {
        if (row < body) return 0;
        float t = (row - body + 1) / (float) Math.max(1, size - body);
        return Math.round(t * (size / 2F - 1));
    }

    private FactionArt() {}
}
