package com.renanpontes.isekaimahou.feature.mana_core.client;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.automation.WirelessMana;
import com.renanpontes.isekaimahou.feature.automation.client.RuneButton;
import com.renanpontes.isekaimahou.feature.mana_core.Overcharge;
import com.renanpontes.isekaimahou.feature.mana_core.block.ManaCoreBlockEntity;
import com.renanpontes.isekaimahou.feature.mana_core.block.ManaCoreMenu;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

/**
 * Tela do Núcleo de Mana: a reserva (barra grande), torres que usaram no último minuto e o consumo, o SEU mana (com a
 * sobrecarga em magenta) e os botões Depositar (100 · 1000 · Tudo) e Retirar (100 · 1000 · Encher · +300 ⚠).
 */
public class ManaCoreScreen extends AbstractContainerScreen<ManaCoreMenu> {
    private static final ResourceLocation BG = IsekaiMahou.id("textures/gui/mana_core.png");
    private static final String K = "gui.isekai_mahou.mana_core.";
    private static final int LABEL = 0xFFCFC3E8, VALUE = 0xFFFFFFFF, GOLD = 0xFFF2D06B, DIM = 0xFF8E84A6;
    private static final int OVER = 0xFF000000 | Overcharge.RGB;
    // Geometria (igual à textura gerada por tools/gen/mana_core_assets.py)
    private static final int CORE_BAR_X = 9, CORE_BAR_Y = 19, CORE_BAR_W = 158, CORE_BAR_H = 12;
    private static final int STATS_Y = 36, YOU_Y = 52, YOU_BAR_Y = 62, YOU_BAR_H = 4;
    private static final int DEP_LABEL_Y = 72, DEP_Y = 81, WD_LABEL_Y = 97, WD_Y = 106, HINT_Y = 123;

    private final List<RuneButton> withdrawButtons = new ArrayList<>();

    public ManaCoreScreen(ManaCoreMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 144;
        titleLabelY = 6;
    }

    @Override
    protected void init() {
        super.init();
        withdrawButtons.clear();
        int x = leftPos, y = topPos;
        // Depositar: 3 botões
        button(x + 8, y + DEP_Y, 52, "deposit.small", "deposit.tooltip", ManaCoreBlockEntity.B_DEPOSIT_SMALL, false);
        button(x + 62, y + DEP_Y, 52, "deposit.big", "deposit.tooltip", ManaCoreBlockEntity.B_DEPOSIT_BIG, false);
        button(x + 116, y + DEP_Y, 52, "deposit.all", "deposit.all.tooltip", ManaCoreBlockEntity.B_DEPOSIT_ALL, false);
        // Retirar: 4 botões
        button(x + 8, y + WD_Y, 38, "withdraw.small", "withdraw.tooltip", ManaCoreBlockEntity.B_WITHDRAW_SMALL, true);
        button(x + 48, y + WD_Y, 38, "withdraw.big", "withdraw.tooltip", ManaCoreBlockEntity.B_WITHDRAW_BIG, true);
        button(x + 88, y + WD_Y, 38, "withdraw.fill", "withdraw.fill.tooltip", ManaCoreBlockEntity.B_WITHDRAW_FILL, true);
        button(x + 128, y + WD_Y, 40, "withdraw.over", "withdraw.over.tooltip", ManaCoreBlockEntity.B_WITHDRAW_OVER, true);
    }

    private void button(int x, int y, int w, String label, String tooltip, int id, boolean withdraw) {
        boolean over = id == ManaCoreBlockEntity.B_WITHDRAW_OVER;
        RuneButton b = addRenderableWidget(new RuneButton(x, y, w, 12,
                () -> over ? Component.translatable(K + label).withColor(OVER) : Component.translatable(K + label),
                btn -> send(id)));
        b.setTooltip(Tooltip.create(Component.translatable(K + tooltip, Overcharge.STEP)));
        if (withdraw) withdrawButtons.add(b);
    }

    private void send(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        boolean can = menu.canWithdraw();
        for (RuneButton b : withdrawButtons) {
            b.active = can;
            if (!can) b.setTooltip(Tooltip.create(Component.translatable(K + "withdraw.denied")));
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        int mx = mouseX - leftPos, my = mouseY - topPos;
        if (mx >= CORE_BAR_X - 1 && mx < CORE_BAR_X + CORE_BAR_W + 1 && my >= CORE_BAR_Y - 1 && my < STATS_Y + 9) {
            g.renderComponentTooltip(font, List.of(Component.translatable(K + "core.tooltip", WirelessMana.RANGE,
                    WirelessMana.LOSS_STEP, WirelessMana.LOSS_PERCENT)), mouseX, mouseY);
        } else if (mx >= CORE_BAR_X - 1 && mx < CORE_BAR_X + CORE_BAR_W + 1 && my >= YOU_Y - 1 && my < YOU_BAR_Y + YOU_BAR_H + 1) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable(K + "you.tooltip"));
            float over = Overcharge.clientAmount;
            if (over > 0) lines.add(Component.translatable(K + "you.over", (int) over, Overcharge.level(over)).withColor(OVER));
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.blit(BG, x, y, 0, 0, imageWidth, imageHeight, 256, 256);

        // Reserva do núcleo (com brilho que corre quando há torres usando)
        int mana = menu.mana();
        float frac = Math.min(1F, mana / (float) ManaCoreBlockEntity.CAPACITY);
        int w = Math.round(CORE_BAR_W * frac);
        int bx = x + CORE_BAR_X, by = y + CORE_BAR_Y;
        if (w > 0) {
            g.fillGradient(bx, by, bx + w, by + CORE_BAR_H, 0xFF9E74FF, 0xFF4B2A9E);
            g.fill(bx, by, bx + w, by + 1, 0xFFD9C6FF);
            if (menu.towers() > 0) {
                long t = net.minecraft.Util.getMillis() % 1600;
                int sx = bx + Math.round(w * t / 1600F);
                g.fill(Math.max(bx, sx - 6), by + 1, Math.min(bx + w, sx), by + CORE_BAR_H - 1, 0x55FFFFFF);
            }
        }
        // Marcas de 10%
        for (int i = 1; i < 10; i++) {
            int mx = bx + Math.round(CORE_BAR_W * i / 10F);
            g.fill(mx, by + (i == 5 ? 0 : 7), mx + 1, by + CORE_BAR_H, 0x50000000);
        }

        // Seu mana (+ sobrecarga em magenta por cima, da direita para a esquerda)
        int max = Math.max(1, ClientMagic.maxMana);
        float you = Math.max(0F, Math.min(ClientMagic.mana, max));
        int yw = Math.round(CORE_BAR_W * you / max);
        int yy = y + YOU_BAR_Y;
        if (yw > 0) g.fillGradient(bx, yy, bx + yw, yy + YOU_BAR_H, 0xFF7FB4FF, 0xFF3A62C8);
        float over = Overcharge.clientAmount;
        if (over > 0) {
            int ow = Math.max(2, Math.round(CORE_BAR_W * Math.min(1F, over / max)));
            float pulse = 0.6F + 0.4F * (float) Math.sin(net.minecraft.Util.getMillis() / 220.0);
            int a = Math.round(255 * pulse);
            g.fill(bx + CORE_BAR_W - ow, yy - 1, bx + CORE_BAR_W, yy + YOU_BAR_H + 1, (a << 24) | (Overcharge.RGB & 0xFFFFFF));
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, GOLD, false);
        // Números da reserva por cima da barra
        Component amount = Component.translatable(K + "amount", menu.mana(), ManaCoreBlockEntity.CAPACITY);
        g.drawString(font, amount, CORE_BAR_X + (CORE_BAR_W - font.width(amount)) / 2, CORE_BAR_Y + 2, VALUE, true);
        // Torres e consumo
        Component stats = menu.towers() > 0
                ? Component.translatable(K + "stats", menu.towers(), menu.usedPerMinute())
                : Component.translatable(K + "stats.idle");
        fitted(g, stats, 9, STATS_Y, 158, menu.towers() > 0 ? LABEL : DIM);

        // Seu mana
        int max = ClientMagic.maxMana;
        float over = Overcharge.clientAmount;
        Component you = Component.translatable(K + "you", (int) Math.floor(ClientMagic.mana), max);
        g.drawString(font, you, 9, YOU_Y, LABEL, false);
        if (over > 0) {
            Component o = Component.translatable(K + "you.over_short", (int) over, Overcharge.level(over));
            g.drawString(font, o, 167 - font.width(o), YOU_Y, OVER, false);
        }

        g.drawString(font, Component.translatable(K + "deposit"), 9, DEP_LABEL_Y, GOLD, false);
        g.drawString(font, Component.translatable(K + "withdraw"), 9, WD_LABEL_Y, GOLD, false);
        if (!menu.canWithdraw()) {
            Component d = Component.translatable(K + "withdraw.owner_only");
            g.drawString(font, d, 167 - font.width(d), WD_LABEL_Y, DIM, false);
        }
        fitted(g, Component.translatable(K + "hint", Overcharge.STEP), 9, HINT_Y, 158, DIM);
        fitted(g, Component.translatable(K + "hint2"), 9, HINT_Y + 10, 158, DIM);
    }

    /** Uma linha: encolhe até 75% se não couber (depois corta). */
    private void fitted(GuiGraphics g, Component text, int x, int y, int maxWidth, int color) {
        int w = font.width(text);
        float s = w > maxWidth ? Math.max(0.75F, maxWidth / (float) w) : 1F;
        List<FormattedCharSequence> lines = font.split(text, (int) (maxWidth / s));
        if (lines.isEmpty()) return;
        g.pose().pushPose();
        g.pose().translate(x, y + (1F - s) * 4F, 0);
        g.pose().scale(s, s, 1F);
        g.drawString(font, lines.get(0), 0, 0, color, false);
        g.pose().popPose();
    }
}
