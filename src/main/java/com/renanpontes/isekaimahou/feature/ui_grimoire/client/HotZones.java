package com.renanpontes.isekaimahou.feature.ui_grimoire.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Áreas clicáveis "modo imediato": registradas durante o desenho de cada quadro e usadas pelos
 * cliques/dicas seguintes. Evita duplicar a geometria entre render e mouseClicked.
 */
public final class HotZones {
    public record Zone(int x, int y, int w, int h, IntConsumer click, Supplier<List<Component>> tip) {
        boolean contains(double mx, double my) {
            return Gfx.in(mx, my, x, y, w, h);
        }
    }

    private final List<Zone> zones = new ArrayList<>();

    public void clear() {
        zones.clear();
    }

    /** Registra uma área (click recebe o botão do mouse: 0 esquerdo, 1 direito). */
    public void add(int x, int y, int w, int h, IntConsumer click, Supplier<List<Component>> tip) {
        if (w > 0 && h > 0) zones.add(new Zone(x, y, w, h, click, tip));
    }

    /** Registra uma área recortada por um retângulo visível (listas com rolagem). */
    public void addClipped(int x, int y, int w, int h, int cx, int cy, int cw, int ch, IntConsumer click, Supplier<List<Component>> tip) {
        int x0 = Math.max(x, cx), y0 = Math.max(y, cy);
        int x1 = Math.min(x + w, cx + cw), y1 = Math.min(y + h, cy + ch);
        add(x0, y0, x1 - x0, y1 - y0, click, tip);
    }

    /** Última área registrada sob o mouse (as de cima são registradas por último). */
    public Zone at(double mx, double my) {
        for (int i = zones.size() - 1; i >= 0; i--) {
            if (zones.get(i).contains(mx, my)) return zones.get(i);
        }
        return null;
    }

    public boolean click(double mx, double my, int button) {
        Zone z = at(mx, my);
        if (z == null || z.click() == null) return false;
        z.click().accept(button);
        return true;
    }

    /** Desenha a dica da área sob o mouse (se houver). */
    public void renderTooltip(GuiGraphics g, int mx, int my) {
        Zone z = at(mx, my);
        if (z == null || z.tip() == null) return;
        List<Component> lines = z.tip().get();
        if (lines == null || lines.isEmpty()) return;
        g.renderTooltip(Gfx.font(), GlyphIcons.wrap(lines, 220), mx, my);
    }
}
