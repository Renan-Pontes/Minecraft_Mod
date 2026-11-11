package com.renanpontes.isekaimahou.feature.farm.client;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.farm.ManaLink;
import com.renanpontes.isekaimahou.feature.farm.machine.FarmMachineBlockEntity;
import com.renanpontes.isekaimahou.feature.farm.machine.FarmMachineMenu;
import com.renanpontes.isekaimahou.feature.farm.machine.MachineKind;
import com.renanpontes.isekaimahou.feature.farm.machine.Produce;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * Tela das fazendas mágicas: entrada (com dica do que colocar), combustível, seta de progresso, 3×3 de produção,
 * a reserva de mana (barra vertical) e uma linha de estado ("Trabalhando", "Sem mana", "Produção cheia"...).
 */
public class FarmMachineScreen extends AbstractContainerScreen<FarmMachineMenu> {
    private static final ResourceLocation BG = IsekaiMahou.id("textures/gui/farm_machine.png");
    private static final String K = "farm.isekai_mahou.machine.";
    private static final int GOLD = 0xFFF2D06B, LABEL = 0xFFCFC3E8;
    private static final int BAR_X = 12, BAR_Y = 18, BAR_W = 10, BAR_H = 52;
    private static final int ARROW_X = 68, ARROW_Y = 36, ARROW_W = 24, ARROW_H = 16;

    public FarmMachineScreen(FarmMachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 176;
        inventoryLabelY = 83;
        titleLabelY = 6;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        int mx = mouseX - leftPos, my = mouseY - topPos;
        if (in(mx, my, BAR_X - 1, BAR_Y - 1, BAR_W + 2, BAR_H + 2)) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable(K + "mana", menu.mana(), menu.capacity()).withColor(0xB896FF));
            lines.add(Component.translatable(K + "batteries", menu.batteries(), ManaLink.RANGE).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable(K + "channel_hint").withStyle(ChatFormatting.DARK_GRAY));
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
        } else if (in(mx, my, FarmMachineMenu.INPUT_X - 1, FarmMachineMenu.INPUT_Y - 1, 18, 18) && menu.inputStack().isEmpty()) {
            g.renderComponentTooltip(font, List.of(Component.translatable(K + "input_title").withColor(menu.kind.rgb),
                    inputHint().withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
        } else if (in(mx, my, FarmMachineMenu.FUEL_X - 1, FarmMachineMenu.FUEL_Y - 1, 18, 18) && menu.getSlot(1).getItem().isEmpty()) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable(K + "fuel_title").withColor(0xB896FF));
            lines.add(Component.translatable(K + "fuel_list").withStyle(ChatFormatting.GRAY));
            if (menu.kind == MachineKind.ACCELERATED_TREE) lines.add(Component.translatable(K + "fuel_bonemeal").withStyle(ChatFormatting.GREEN));
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
        } else if (in(mx, my, ARROW_X, ARROW_Y, ARROW_W, ARROW_H) && menu.length() > 0) {
            int left = Math.max(0, menu.length() - menu.progress()) / 20;
            g.renderTooltip(font, Component.translatable(K + "time_left", left), mouseX, mouseY);
        }
    }

    private net.minecraft.network.chat.MutableComponent inputHint() {
        return menu.kind == MachineKind.ESSENCE ? Component.translatable(K + "elemental_pedestal.input") : menu.kind.inputHint();
    }

    private static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.blit(BG, x, y, 0, 0, imageWidth, imageHeight, 256, 256);
        // Reserva de mana
        int mana = menu.mana(), cap = menu.capacity();
        if (mana > 0) {
            int h = Math.max(1, Math.round(BAR_H * Math.min(1F, mana / (float) cap)));
            g.fill(x + BAR_X, y + BAR_Y + BAR_H - h, x + BAR_X + BAR_W, y + BAR_Y + BAR_H, 0xFF7A4FD8);
            g.fill(x + BAR_X, y + BAR_Y + BAR_H - h, x + BAR_X + 3, y + BAR_Y + BAR_H, 0xFFB896FF);
        }
        // Seta de progresso (cor da máquina)
        int len = menu.length();
        if (len > 0) {
            int w = Math.round(ARROW_W * Math.min(1F, menu.progress() / (float) len));
            g.blit(BG, x + ARROW_X, y + ARROW_Y, 176, 0, w, ARROW_H, 256, 256);
            g.fill(x + ARROW_X, y + ARROW_Y + ARROW_H, x + ARROW_X + w, y + ARROW_Y + ARROW_H + 1, 0xFF000000 | menu.kind.rgb);
        }
        // Cargas do catalisador (gerador)
        if (menu.kind == MachineKind.STONE_GENERATOR && menu.charges() > 0) {
            for (int i = 0; i < Produce.CATALYST_CHARGES; i++) {
                int c = i < menu.charges() ? 0xFFE0703A : 0xFF3A3048;
                g.fill(x + 64 + i * 5, y + 58, x + 67 + i * 5, y + 61, c);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, GOLD, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL, false);
        FarmMachineBlockEntity.Status st = menu.status();
        Component line = Component.translatable(K + "status", st.text());
        g.drawString(font, line, 8, 74, 0xFF000000 | st.color(), false);
    }
}
