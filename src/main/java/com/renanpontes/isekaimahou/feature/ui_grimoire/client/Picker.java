package com.renanpontes.isekaimahou.feature.ui_grimoire.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.client.MahouUi;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Lista suspensa (dropdown) desenhada por cima de tudo: ícone + nome completo de cada opção, em colunas quando
 * não cabe na altura disponível. Fecha ao escolher, ao clicar fora ou com Esc. As áreas clicáveis são
 * registradas no fim do quadro (ficam por cima das outras) — ver {@link HotZones#at}.
 */
final class Picker {
    /** Desenha o ícone de uma opção. */
    interface Icon {
        void draw(GuiGraphics g, int x, int y, int size);
    }

    /** Uma opção; {@code shortLabel} (opcional) é usado quando o rótulo completo não cabe nas colunas. */
    record Option(Icon icon, Component label, @Nullable Component shortLabel, int color, boolean enabled, boolean current, Runnable pick,
                  Supplier<List<Component>> tip) {
        Component label(boolean shortForm) {
            return shortForm && shortLabel != null ? shortLabel : label;
        }
    }

    private static final int ROW = 14;
    private static final int PAD = 3;

    private final List<Option> options = new ArrayList<>();
    private Component title = Component.empty();
    private boolean open;
    private int ax, ay, aw, ah;          // âncora (o botão que abriu)
    private int minY, maxY, minX, maxX;  // área permitida
    private boolean up;                  // abrir para cima
    // geometria calculada no desenho
    private int px, py, pw, ph;
    private boolean shortForm;

    boolean isOpen() {
        return open;
    }

    void close() {
        open = false;
    }

    /** Abre (ou fecha, se já estava aberto com a mesma âncora) abaixo/acima do retângulo âncora. */
    void toggle(int ax, int ay, int aw, int ah, boolean up, int minX, int maxX, int minY, int maxY, Component title, List<Option> options) {
        if (open && this.ax == ax && this.ay == ay) {
            open = false;
            return;
        }
        this.ax = ax;
        this.ay = ay;
        this.aw = aw;
        this.ah = ah;
        this.up = up;
        this.minX = minX;
        this.maxX = maxX;
        this.minY = minY;
        this.maxY = maxY;
        this.title = title;
        this.options.clear();
        this.options.addAll(options);
        this.open = true;
        this.pw = 0;
        Gfx.sound(SoundEvents.UI_BUTTON_CLICK, 1.5F, 0.25F);
    }

    private int itemWidth(Font font, boolean shortForm) {
        int w = font.width(title) + 8;
        for (Option o : options) w = Math.max(w, 12 + 4 + font.width(o.label(shortForm)) + 8);
        return w;
    }

    boolean contains(double mx, double my) {
        return open && Gfx.in(mx, my, px, py, pw, ph);
    }

    /** A lista aberta cobre o retângulo? (para esconder widgets que roubariam o clique) */
    boolean intersects(int rx, int ry, int rw, int rh) {
        return open && pw > 0 && rx < px + pw && rx + rw > px && ry < py + ph && ry + rh > py;
    }

    /** Desenha e registra as áreas (chame por último no render). */
    void render(GuiGraphics g, HotZones zones, int mx, int my) {
        if (!open || options.isEmpty()) return;
        Font font = Gfx.font();
        int titleH = title.getString().isEmpty() ? 0 : 11;
        int avail = up ? (ay - 2 - minY) : (maxY - (ay + ah + 2));
        int rowsMax = Math.max(1, (avail - PAD * 2 - titleH) / ROW);
        int cols = Math.max(1, (options.size() + rowsMax - 1) / rowsMax);
        int maxW = maxX - minX;
        // rótulo completo; se as colunas não cabem, o curto; em último caso encolhe (o nome inteiro fica na dica)
        shortForm = false;
        int itemW = itemWidth(font, false);
        if (cols * itemW + PAD * 2 > maxW) {
            shortForm = true;
            itemW = itemWidth(font, true);
        }
        if (cols * itemW + PAD * 2 > maxW) itemW = Math.max(30, (maxW - PAD * 2) / cols);
        int rows = (options.size() + cols - 1) / cols;
        pw = cols * itemW + PAD * 2;
        ph = rows * ROW + PAD * 2 + titleH;
        px = Math.max(minX, Math.min(maxX - pw, ax));
        py = up ? Math.max(minY, ay - 2 - ph) : Math.min(maxY - ph, ay + ah + 2);

        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, 0x80000000);
        MahouUi.roundRect(g, px, py, pw, ph, 0xF4221A34, 0xF4100C18);
        MahouUi.roundOutline(g, px, py, pw, ph, MahouUi.GOLD_DIM);
        // bloqueia cliques/dicas do que está atrás (registrada antes das opções: fica por baixo delas)
        zones.add(px, py, pw, ph, b -> {}, null);
        if (titleH > 0) {
            g.drawString(font, MahouUi.fit(font, title, pw - 8), px + 4, py + PAD, MahouUi.GOLD, false);
            g.fill(px + 3, py + PAD + 9, px + pw - 3, py + PAD + 10, MahouUi.alpha(MahouUi.GOLD_DIM, 0xA0));
        }
        for (int i = 0; i < options.size(); i++) {
            Option o = options.get(i);
            int col = i / rows, row = i % rows;
            int ox = px + PAD + col * itemW;
            int oy = py + PAD + titleH + row * ROW;
            boolean hov = o.enabled() && Gfx.in(mx, my, ox, oy, itemW, ROW);
            if (o.current()) MahouUi.roundRect(g, ox, oy, itemW - 1, ROW - 1, MahouUi.alpha(o.color(), 0x60), MahouUi.alpha(o.color(), 0x30));
            else if (hov) g.fill(ox, oy, ox + itemW - 1, oy + ROW - 1, 0x30FFFFFF);
            if (o.current()) MahouUi.roundOutline(g, ox, oy, itemW - 1, ROW - 1, MahouUi.GOLD);
            if (!o.enabled()) g.setColor(0.45F, 0.45F, 0.5F, 1F);
            if (o.icon() != null) o.icon().draw(g, ox + 2, oy + 1, 12);
            g.setColor(1F, 1F, 1F, 1F);
            int textColor = !o.enabled() ? 0xFF6E6680 : hov || o.current() ? 0xFFFFFFFF : MahouUi.lighten(o.color(), 0.45F);
            g.drawString(font, MahouUi.fit(font, o.label(shortForm), itemW - 20), ox + 17, oy + 3, textColor, false);
            final Option fo = o;
            zones.add(ox, oy, itemW - 1, ROW - 1, b -> {
                if (!fo.enabled()) {
                    Gfx.sound(SoundEvents.NOTE_BLOCK_BASS, 0.7F, 0.4F);
                    return;
                }
                open = false;
                fo.pick().run();
            }, o.tip());
        }
        g.pose().popPose();
    }
}
