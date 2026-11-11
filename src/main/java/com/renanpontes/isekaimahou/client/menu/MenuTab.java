package com.renanpontes.isekaimahou.client.menu;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Uma aba do menu (tecla B, "Janela de Status"). Registrada em {@link MenuTabs}.
 * A área da aba é (x, y, w, h) recebida em {@link #init}. Widgets (botões, caixas de texto) devem ser
 * adicionados com {@code screen.addTabWidget(...)} dentro do init (são recriados ao trocar de aba).
 */
public interface MenuTab {
    void init(StatusScreen screen, int x, int y, int w, int h);

    /** Desenha a aba (chamado depois dos widgets). */
    void render(GuiGraphics g, int mouseX, int mouseY, float partialTick);

    default void tick() {}
    default boolean mouseClicked(double mx, double my, int button) { return false; }
    default boolean mouseReleased(double mx, double my, int button) { return false; }
    default boolean mouseDragged(double mx, double my, int button, double dx, double dy) { return false; }
    default boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) { return false; }
    default boolean keyPressed(int key, int scan, int modifiers) { return false; }
    default boolean charTyped(char c, int modifiers) { return false; }
    default void removed() {}
}
