package com.renanpontes.isekaimahou.feature.ui_grimoire.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/** Rolagem vertical simples de uma área (roda do mouse + arrastar a barra). */
public final class ScrollState {
    public int x, y, w, h;
    private int content;
    /** A altura do conteúdo já foi informada? Antes disso {@link #set} guarda o valor sem limitar (restaurar a rolagem no init). */
    private boolean known;
    private double offset;
    private boolean dragging;
    private double dragStartY, dragStartOffset;

    public void bounds(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public void content(int contentHeight) {
        this.content = contentHeight;
        this.known = true;
        clamp();
    }

    /** Deslocamento atual (antes de a altura ser conhecida, o valor restaurado — assim salvar de novo não o perde). */
    public int offset() {
        return (int) Math.round(offset);
    }

    public void set(double v) {
        offset = v;
        if (known) clamp();
    }

    public int max() {
        return Math.max(0, content - h);
    }

    private void clamp() {
        offset = Mth.clamp(offset, 0, max());
    }

    public boolean over(double mx, double my) {
        return Gfx.in(mx, my, x, y, w, h);
    }

    public boolean scroll(double mx, double my, double amount, int step) {
        if (!over(mx, my) || max() <= 0) return false;
        offset -= amount * step;
        clamp();
        return true;
    }

    /** Garante que o intervalo [top, top+size) (em coordenadas do conteúdo) fique visível. */
    public void reveal(int top, int size) {
        if (top < offset) offset = top;
        else if (top + size > offset + h) offset = top + size - h;
        clamp();
    }

    // ------------------------------------------------------------------ barra
    private int barX() {
        return x + w - 3;
    }

    private int thumbH() {
        return Math.max(10, (int) ((float) h * h / Math.max(1, content)));
    }

    private int thumbY() {
        int m = max();
        return y + (m <= 0 ? 0 : (int) ((h - thumbH()) * (offset / m)));
    }

    public void renderBar(GuiGraphics g) {
        if (max() <= 0) return;
        g.fill(barX(), y, barX() + 2, y + h, 0x40FFFFFF);
        int ty = thumbY();
        g.fill(barX(), ty, barX() + 2, ty + thumbH(), dragging ? 0xFFFFFFFF : 0xFFB9A8E8);
    }

    public boolean clickBar(double mx, double my) {
        if (max() <= 0 || !Gfx.in(mx, my, barX() - 2, y, 5, h)) return false;
        int ty = thumbY();
        if (my < ty || my >= ty + thumbH()) {
            // clique fora do "polegar": pula para a posição
            offset = (my - y - thumbH() / 2.0) / Math.max(1, h - thumbH()) * max();
            clamp();
        }
        dragging = true;
        dragStartY = my;
        dragStartOffset = offset;
        return true;
    }

    public boolean drag(double my) {
        if (!dragging) return false;
        double per = (double) max() / Math.max(1, h - thumbH());
        offset = dragStartOffset + (my - dragStartY) * per;
        clamp();
        return true;
    }

    public boolean release() {
        boolean was = dragging;
        dragging = false;
        return was;
    }
}
