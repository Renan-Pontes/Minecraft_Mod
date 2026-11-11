package com.renanpontes.isekaimahou.feature.automation.client;

import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.automation.ManaNet;
import com.renanpontes.isekaimahou.feature.automation.block.ManaCondenserBlockEntity;
import com.renanpontes.isekaimahou.feature.automation.block.ManaCondenserMenu;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * Tela do Condensador: 3×3 de combustível, a "chama" de mana queimando, a reserva (barra vertical) e quantas
 * baterias estão recebendo.
 */
public class ManaCondenserScreen extends AbstractContainerScreen<ManaCondenserMenu> {
    private static final ResourceLocation BG = IsekaiMahou.id("textures/gui/automation_condenser.png");
    private static final String K = "gui.isekai_mahou.condenser.";
    private static final int LABEL = 0xFFCFC3E8, GOLD = 0xFFF2D06B, DIM = 0xFF8E84A6;
    private static final int FLAME_X = 94, FLAME_Y = 37, FLAME_W = 14, FLAME_H = 16;
    private static final int BAR_X = 134, BAR_Y = 19, BAR_W = 10, BAR_H = 52;

    public ManaCondenserScreen(ManaCondenserMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 181;
        inventoryLabelY = 88;
        titleLabelY = 6;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        int mx = mouseX - leftPos, my = mouseY - topPos;
        if (mx >= BAR_X - 1 && mx < BAR_X + BAR_W + 1 && my >= BAR_Y - 1 && my < BAR_Y + BAR_H + 1) {
            g.renderComponentTooltip(font, List.of(
                    Component.translatable(K + "buffer", menu.buffer(), ManaCondenserBlockEntity.BUFFER_CAP).withColor(0xB896FF),
                    Component.translatable(K + "batteries", menu.batteries(), ManaNet.BATTERY_RANGE).withStyle(ChatFormatting.GRAY),
                    Component.translatable(K + "stored", menu.stored()).withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
        } else if (mx >= FLAME_X - 2 && mx < FLAME_X + FLAME_W + 2 && my >= FLAME_Y - 2 && my < FLAME_Y + FLAME_H + 2) {
            g.renderComponentTooltip(font, List.of(
                    Component.translatable(K + "fuels").withColor(0xF2D06B),
                    Component.translatable(K + "fuels.list1").withStyle(ChatFormatting.GRAY),
                    Component.translatable(K + "fuels.list2").withStyle(ChatFormatting.GRAY),
                    Component.translatable(K + "fuels.list3").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.blit(BG, x, y, 0, 0, imageWidth, imageHeight, 256, 256);
        // Chama de mana: enche de baixo para cima enquanto o item queima
        int total = menu.burnTotal(), left = menu.burnLeft();
        if (left > 0 && total > 0) {
            int h = Math.max(1, Math.round(FLAME_H * left / (float) total));
            g.fill(x + FLAME_X, y + FLAME_Y + FLAME_H - h, x + FLAME_X + FLAME_W, y + FLAME_Y + FLAME_H, 0xC09B6BFF);
            g.fill(x + FLAME_X + 4, y + FLAME_Y + FLAME_H - h, x + FLAME_X + FLAME_W - 4, y + FLAME_Y + FLAME_H, 0xE0E2D2FF);
        }
        // Reserva
        int buffer = menu.buffer();
        if (buffer > 0) {
            int h = Math.max(1, Math.round(BAR_H * buffer / (float) ManaCondenserBlockEntity.BUFFER_CAP));
            g.fill(x + BAR_X, y + BAR_Y + BAR_H - h, x + BAR_X + BAR_W, y + BAR_Y + BAR_H, 0xFF7A4FD8);
            g.fill(x + BAR_X, y + BAR_Y + BAR_H - h, x + BAR_X + 2, y + BAR_Y + BAR_H, 0xFFB896FF);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, GOLD, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL, false);
        Component line = menu.batteries() == 0
                ? Component.translatable(K + "no_battery", ManaNet.BATTERY_RANGE).withColor(0xFF9F5A)
                : Component.translatable(K + "status", menu.batteries(), menu.stored());
        g.drawString(font, line, 8, 77, menu.batteries() == 0 ? 0xFFFF9F5A : DIM, false);
    }
}
