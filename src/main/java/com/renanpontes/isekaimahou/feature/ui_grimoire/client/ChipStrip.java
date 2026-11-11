package com.renanpontes.isekaimahou.feature.ui_grimoire.client;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.client.MahouUi;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Fileira de "chips" (abas/filtros) com ícone + rótulo que se adapta à largura SEM cortar texto:
 * rótulos completos numa linha → grade/linhas corridas com rótulos completos → o mesmo com rótulos curtos →
 * só o ativo com rótulo → só ícones. A dica (tooltip) de cada chip sempre traz o nome completo.
 */
final class ChipStrip {
    record Chip(@Nullable Picker.Icon icon, Component label, @Nullable Component shortLabel, int accent, boolean on,
                IntConsumer click, Supplier<List<Component>> tip) {}

    static final int H = 14;
    private static final int GAP = 2;
    private static final int ICON = 12;
    /** Quanto um chip pode "esticar" para preencher a linha (na última linha / linha única). */
    private static final int MAX_STRETCH = 14;

    /**
     * Arranjo escolhido: quantas linhas, que rótulos e se as linhas são "corridas" (cada chip com a sua largura,
     * quebrando quando a linha enche) em vez de uma grade de células iguais.
     */
    record Plan(int rows, int labels, boolean flow) {
        static final int FULL = 0, SHORT = 1, ACTIVE = 2, NONE = 3;

        Plan(int rows, int labels) {
            this(rows, labels, false);
        }
    }

    @Nullable
    private static Component label(Chip c, int labels) {
        return switch (labels) {
            case Plan.FULL -> c.label();
            case Plan.SHORT -> c.shortLabel() != null ? c.shortLabel() : c.label();
            case Plan.ACTIVE -> c.on() || c.icon() == null ? (c.shortLabel() != null ? c.shortLabel() : c.label()) : null;
            default -> c.icon() == null ? c.label() : null;
        };
    }

    /** Largura natural do chip: ícone+rótulo, só rótulo ou só ícone. */
    private static int chipW(Font font, Chip c, int labels) {
        Component l = label(c, labels);
        if (l == null) return ICON + 4;
        return c.icon() != null ? font.width(l) + ICON + 8 : font.width(l) + 8;
    }

    /** Escolhe o arranjo que cabe em {@code w} com no máximo {@code maxRows} linhas (o "só ícones" pode quebrar mais). */
    static Plan plan(List<Chip> chips, int w, int maxRows) {
        Font font = Gfx.font();
        int n = chips.size();
        if (n == 0) return new Plan(1, Plan.FULL);
        for (int labels : new int[]{Plan.FULL, Plan.SHORT}) {
            if (rowWidth(font, chips, labels) <= w) return new Plan(1, labels);
            // grade de células iguais (mais arrumada)...
            for (int rows = 2; rows <= Math.min(maxRows, n); rows++) {
                int cols = (n + rows - 1) / rows;
                int cell = (w - GAP * (cols - 1)) / cols;
                boolean fits = true;
                for (Chip c : chips) {
                    if (chipW(font, c, labels) > cell) {
                        fits = false;
                        break;
                    }
                }
                if (fits) return new Plan(rows, labels);
            }
            // ...ou linhas corridas (cada chip com a sua largura): cabe mais rótulo inteiro
            if (maxRows > 1) {
                int rows = flowRows(font, chips, w, labels);
                if (rows <= maxRows) return new Plan(rows, labels, true);
            }
        }
        if (rowWidth(font, chips, Plan.ACTIVE) <= w) return new Plan(1, Plan.ACTIVE);
        return new Plan(flowRows(font, chips, w, Plan.NONE), Plan.NONE, true);
    }

    private static int rowWidth(Font font, List<Chip> chips, int labels) {
        int sum = GAP * (chips.size() - 1);
        for (Chip c : chips) sum += chipW(font, c, labels);
        return sum;
    }

    /** Quantas linhas o arranjo corrido ocupa, quebrando quando a linha enche (MAX_VALUE se um chip sozinho não cabe). */
    private static int flowRows(Font font, List<Chip> chips, int w, int labels) {
        int rows = 1, x = 0;
        for (Chip c : chips) {
            int cw = chipW(font, c, labels);
            if (cw > w && labels != Plan.NONE) return Integer.MAX_VALUE;
            if (x > 0 && x + cw > w) {
                rows++;
                x = 0;
            }
            x += cw + GAP;
        }
        return rows;
    }

    static int height(Plan p) {
        return p.rows() * H + (p.rows() - 1) * GAP;
    }

    /** Desenha os chips em (x, y, w) segundo o plano e registra cliques/dicas. Devolve a altura usada. */
    static int render(GuiGraphics g, HotZones zones, int x, int y, int w, List<Chip> chips, Plan plan, int mx, int my) {
        Font font = Gfx.font();
        int n = chips.size();
        if (n == 0) return 0;
        int[] widths = new int[n];
        int[] rowOf = new int[n];
        int lastRow = 0;
        if (plan.rows() > 1 && !plan.flow()) {
            // grade: células iguais
            int perRow = (n + plan.rows() - 1) / plan.rows();
            int cell = (w - GAP * (perRow - 1)) / perRow;
            for (int i = 0; i < n; i++) {
                widths[i] = cell;
                rowOf[i] = i / perRow;
            }
            lastRow = (n - 1) / perRow;
        } else {
            // largura natural, quebrando a linha quando enche (arranjo corrido)
            int cx = 0;
            for (int i = 0; i < n; i++) {
                widths[i] = chipW(font, chips.get(i), plan.labels());
                if (plan.flow() && cx > 0 && cx + widths[i] > w) {
                    lastRow++;
                    cx = 0;
                }
                rowOf[i] = lastRow;
                cx += widths[i] + GAP;
            }
            // com rótulos: linhas cheias se esticam para preencher a largura; a última (ou única) estica pouco
            if (plan.labels() != Plan.NONE) {
                for (int r = 0; r <= lastRow; r++) {
                    int sum = -GAP, count = 0, last = -1;
                    for (int i = 0; i < n; i++) {
                        if (rowOf[i] != r) continue;
                        sum += widths[i] + GAP;
                        count++;
                        last = i;
                    }
                    if (count == 0) continue;
                    int free = Math.max(0, w - sum);
                    boolean justify = r < lastRow;
                    int extra = justify ? free / count : Math.min(MAX_STRETCH, free / count);
                    for (int i = 0; i < n; i++) if (rowOf[i] == r) widths[i] += extra;
                    if (justify || extra < MAX_STRETCH) widths[last] += free - extra * count;
                }
            }
        }
        int cx = x;
        int row = 0;
        for (int i = 0; i < n; i++) {
            Chip c = chips.get(i);
            int cw = widths[i];
            if (rowOf[i] != row) {
                row = rowOf[i];
                cx = x;
            }
            int cy = y + row * (H + GAP);
            boolean hov = Gfx.in(mx, my, cx, cy, cw, H);
            Gfx.toggle(g, cx, cy, cw, H, c.on(), hov, c.accent());
            if (c.on()) g.fill(cx + 2, cy + H - 1, cx + cw - 2, cy + H, MahouUi.GOLD);
            Component l = label(c, plan.labels());
            int contentW = (c.icon() != null ? ICON : 0) + (l != null ? (c.icon() != null ? 2 : 0) + font.width(l) : 0);
            int ix = cx + Math.max(2, (cw - contentW) / 2);
            if (c.icon() != null) {
                if (!c.on() && !hov) g.setColor(0.78F, 0.78F, 0.82F, 1F);
                c.icon().draw(g, ix, cy + 1, ICON);
                g.setColor(1F, 1F, 1F, 1F);
                ix += ICON + 2;
            }
            if (l != null) {
                int color = c.on() ? 0xFFFFFFFF : hov ? MahouUi.lighten(c.accent(), 0.6F) : Gfx.TEXT_DIM;
                g.drawString(font, l, ix, cy + 3, color, false);
            }
            zones.add(cx, cy, cw, H, c.click(), c.tip());
            cx += cw + GAP;
        }
        return lastRow * (H + GAP) + H;
    }

    private ChipStrip() {}
}
