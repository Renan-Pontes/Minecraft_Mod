package com.renanpontes.isekaimahou.client.social;

import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.core.social.TradeMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Tela da troca segura: à esquerda a SUA oferta (12 espaços), à direita a do outro (só ver), luzes de confirmação dos
 * dois, botões Confirmar/Cancelar e o inventário embaixo. Qualquer mudança desconfirma os dois e trava por 2 s.
 */
public class TradeScreen extends AbstractContainerScreen<TradeMenu> {
    private static final int W = 236, H = 222;
    private static final int GREEN = 0xFF7CFF8A, GOLD = MahouUi.GOLD, MUTED = 0xFF8A82A0;
    private Button confirm;
    private Button cancel;

    public TradeScreen(TradeMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = W;
        imageHeight = H;
        inventoryLabelX = TradeMenu.INV_X;
        inventoryLabelY = TradeMenu.INV_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        int by = topPos + TradeMenu.GRID_Y + 3 * 18 + 16;
        confirm = addRenderableWidget(Button.builder(Component.empty(), b -> press(TradeMenu.BUTTON_CONFIRM))
                .bounds(leftPos + W / 2 - 84, by, 100, 20).build());
        cancel = addRenderableWidget(Button.builder(Component.translatable("screen.isekai_mahou.trade.cancel"),
                b -> press(TradeMenu.BUTTON_CANCEL)).bounds(leftPos + W / 2 + 20, by, 64, 20).build());
        refreshButtons();
    }

    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refreshButtons();
    }

    private void refreshButtons() {
        if (confirm == null) return;
        int lock = menu.lockTicks();
        if (menu.iConfirmed()) {
            confirm.setMessage(Component.translatable("screen.isekai_mahou.trade.unconfirm"));
            confirm.active = true;
        } else if (lock > 0) {
            confirm.setMessage(Component.translatable("screen.isekai_mahou.trade.wait_short", (lock + 19) / 20));
            confirm.active = false;
        } else {
            confirm.setMessage(Component.translatable("screen.isekai_mahou.trade.confirm"));
            confirm.active = menu.partnerPresent();
        }
        cancel.active = true;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        renderTooltip(g, mx, my);
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = leftPos, y = topPos;
        MahouUi.frame(g, x, y, W, H, GOLD);
        // Ofertas
        grid(g, x + TradeMenu.MY_X, y + TradeMenu.GRID_Y, menu.iConfirmed());
        grid(g, x + TradeMenu.THEIR_X, y + TradeMenu.GRID_Y, menu.partnerConfirmed());
        // Inventário
        for (int r = 0; r < 4; r++) {
            int sy = y + TradeMenu.INV_Y + (r == 3 ? 58 : r * 18);
            for (int c = 0; c < 9; c++) slot(g, x + TradeMenu.INV_X + c * 18, sy);
        }
        // Setas de troca no meio
        int mx0 = x + TradeMenu.MY_X + 4 * 18, mx1 = x + TradeMenu.THEIR_X;
        int midX = (mx0 + mx1) / 2, midY = y + TradeMenu.GRID_Y + 27;
        float pulse = 0.5F + 0.5F * (float) Math.sin(System.currentTimeMillis() / 260.0);
        boolean both = menu.iConfirmed() && menu.partnerConfirmed();
        int arrow = both ? GREEN : MahouUi.mix(MahouUi.GOLD_DIM, GOLD, pulse);
        arrow(g, midX, midY - 6, true, arrow);
        arrow(g, midX, midY + 6, false, arrow);
    }

    private static void grid(GuiGraphics g, int gx, int gy, boolean confirmed) {
        int w = 4 * 18, h = 3 * 18;
        int edge = confirmed ? GREEN : 0xFF4A3F66;
        g.fill(gx - 3, gy - 3, gx + w + 3, gy + h + 3, confirmed ? 0x3048FF70 : 0x30000000);
        g.renderOutline(gx - 3, gy - 3, w + 6, h + 6, edge);
        for (int i = 0; i < TradeMenu.OFFER_SLOTS; i++) slot(g, gx + (i % TradeMenu.COLS) * 18, gy + (i / TradeMenu.COLS) * 18);
    }

    private static void slot(GuiGraphics g, int sx, int sy) {
        g.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF0B0912);
        g.fill(sx, sy, sx + 16, sy + 16, 0xFF2A2340);
        g.fill(sx, sy, sx + 16, sy + 1, 0xFF1A1528);
    }

    /** Seta horizontal de 18 px (para a direita ou para a esquerda). */
    private static void arrow(GuiGraphics g, int cx, int cy, boolean right, int color) {
        g.fill(cx - 9, cy, cx + 9, cy + 1, color);
        for (int i = 0; i < 4; i++) {
            int tip = right ? cx + 9 - i : cx - 9 + i;
            g.fill(tip - (right ? 1 : 0), cy - i, tip + (right ? 0 : 1), cy + i + 1, color);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        Component t = Component.translatable("screen.isekai_mahou.trade.title", menu.partnerName);
        g.drawString(font, t, (W - font.width(t)) / 2, 7, GOLD, true);
        Component mine = Component.translatable("screen.isekai_mahou.trade.your_offer");
        g.drawString(font, MahouUi.fit(font, mine, 76), TradeMenu.MY_X, TradeMenu.GRID_Y - 12, MahouUi.TEXT, false);
        Component theirs = Component.translatable("screen.isekai_mahou.trade.their_offer", menu.partnerName);
        g.drawString(font, MahouUi.fit(font, theirs, 78), TradeMenu.THEIR_X, TradeMenu.GRID_Y - 12, MahouUi.TEXT, false);

        // Luzes de confirmação embaixo de cada oferta
        int ly = TradeMenu.GRID_Y + 3 * 18 + 5;
        light(g, TradeMenu.MY_X, ly, menu.iConfirmed(), Component.translatable(menu.iConfirmed()
                ? "screen.isekai_mahou.trade.confirmed" : "screen.isekai_mahou.trade.not_confirmed"));
        light(g, TradeMenu.THEIR_X, ly, menu.partnerConfirmed(), Component.translatable(!menu.partnerPresent()
                ? "screen.isekai_mahou.trade.partner_gone" : menu.partnerConfirmed()
                ? "screen.isekai_mahou.trade.confirmed" : "screen.isekai_mahou.trade.not_confirmed"));

        // Linha de estado
        Component status;
        int color;
        if (!menu.partnerPresent()) {
            status = Component.translatable("screen.isekai_mahou.trade.status_waiting_open", menu.partnerName);
            color = MUTED;
        } else if (menu.lockTicks() > 0) {
            status = Component.translatable("screen.isekai_mahou.trade.status_changed");
            color = 0xFFFFC857;
        } else if (menu.iConfirmed() && !menu.partnerConfirmed()) {
            status = Component.translatable("screen.isekai_mahou.trade.status_waiting", menu.partnerName);
            color = MahouUi.TEXT_DIM;
        } else if (!menu.iConfirmed() && menu.partnerConfirmed()) {
            status = Component.translatable("screen.isekai_mahou.trade.status_partner_ready", menu.partnerName);
            color = GREEN;
        } else {
            status = Component.translatable("screen.isekai_mahou.trade.status_hint");
            color = MUTED;
        }
        int sy = TradeMenu.GRID_Y + 3 * 18 + 38;
        MahouUi.smallText(g, font, status, (W - font.width(status) * 0.75F) / 2F, sy, 0.75F, color, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, MahouUi.TEXT_DIM, false);
    }

    private void light(GuiGraphics g, int x, int y, boolean on, Component text) {
        int c = on ? GREEN : 0xFF5A5070;
        g.fill(x, y + 1, x + 6, y + 7, 0xFF07050C);
        g.fill(x + 1, y + 2, x + 5, y + 6, c);
        MahouUi.smallText(g, font, text, x + 9, y + 1.5F, 0.75F, on ? GREEN : MUTED, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
