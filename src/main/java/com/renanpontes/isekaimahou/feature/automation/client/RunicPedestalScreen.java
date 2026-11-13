package com.renanpontes.isekaimahou.feature.automation.client;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.glyph.SpellParser;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.feature.automation.ManaNet;
import com.renanpontes.isekaimahou.feature.automation.WirelessMana;
import com.renanpontes.isekaimahou.feature.automation.block.PedestalStatus;
import com.renanpontes.isekaimahou.feature.automation.block.RedstoneMode;
import com.renanpontes.isekaimahou.feature.automation.block.RunicPedestalBlockEntity;
import com.renanpontes.isekaimahou.feature.automation.block.RunicPedestalMenu;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Tela do Pedestal Rúnico: pergaminho e FILTRO à esquerda (com custo, botões Liga/Desliga e Testar e a barra da próxima
 * conjuração), configuração à direita (intervalo, direção, alvo X/Y/Z, redstone), mana (baterias + Núcleo ligado) e o
 * estado com uma dica.
 */
public class RunicPedestalScreen extends AbstractContainerScreen<RunicPedestalMenu> {
    private static final ResourceLocation BG = IsekaiMahou.id("textures/gui/automation_pedestal.png");
    private static final String K = "gui.isekai_mahou.pedestal.";
    /** Escala mínima da barra de mana (v0.8: 2000; ×3 com a mana da v1.0). */
    private static final int BAR_FLOOR = Math.round(2000 * com.renanpontes.isekaimahou.core.classes.ResourceTraining.MANA_SCALE);
    private static final int LABEL = 0xFFCFC3E8, VALUE = 0xFFFFFFFF, GOLD = 0xFFF2D06B, DIM = 0xFF8E84A6;
    private static final int ROW_X = 54, CTRL_X = 108, ROW_Y = 18, ROW_H = 14;
    private static final int MANA_Y = 104, BAR_Y = 114, STATUS_Y = 124;
    /** v1.5: coluna da esquerda (pergaminho + filtro em cima, custo, Liga/Desliga, Testar, barra do tempo). */
    private static final int SLOT_LABEL_Y = 39, COST_Y = 47, TOGGLE_Y = 58, TEST_Y = 74;
    private static final int ON = 0xFF6BE38A, OFF = 0xFF9A93AC;

    public RunicPedestalScreen(RunicPedestalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 236;
        inventoryLabelY = 144;
        titleLabelY = 6;
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos, y = topPos;
        // Intervalo
        minusPlus(y + ROW_Y - 2, RunicPedestalBlockEntity.B_INTERVAL_DOWN, RunicPedestalBlockEntity.B_INTERVAL_UP,
                RunicPedestalBlockEntity.B_INTERVAL_DOWN10, RunicPedestalBlockEntity.B_INTERVAL_UP10, K + "interval.tooltip");
        // Direção
        RuneButton dir = addRenderableWidget(new RuneButton(x + CTRL_X, y + ROW_Y + ROW_H - 2, 58, 12,
                () -> Component.translatable(menu.direction().key()),
                b -> send(b == 1 ? RunicPedestalBlockEntity.B_DIRECTION_PREV : RunicPedestalBlockEntity.B_DIRECTION_NEXT)));
        dir.setTooltip(Tooltip.create(Component.translatable(K + "direction.tooltip")));
        // Alvo X/Y/Z
        String offsetTip = K + "offset.tooltip";
        minusPlus(y + ROW_Y + ROW_H * 2 - 2, RunicPedestalBlockEntity.B_DX_DOWN, RunicPedestalBlockEntity.B_DX_UP, -1, -1, offsetTip);
        minusPlus(y + ROW_Y + ROW_H * 3 - 2, RunicPedestalBlockEntity.B_DY_DOWN, RunicPedestalBlockEntity.B_DY_UP, -1, -1, offsetTip);
        minusPlus(y + ROW_Y + ROW_H * 4 - 2, RunicPedestalBlockEntity.B_DZ_DOWN, RunicPedestalBlockEntity.B_DZ_UP, -1, -1, offsetTip);
        RuneButton reset = addRenderableWidget(new RuneButton(x + CTRL_X - 15, y + ROW_Y + ROW_H * 3 - 2, 12, 12,
                () -> Component.translatable(K + "reset"), b -> send(RunicPedestalBlockEntity.B_RESET_OFFSET)));
        reset.setTooltip(Tooltip.create(Component.translatable(K + "reset.tooltip")));
        // Redstone
        RuneButton red = addRenderableWidget(new RuneButton(x + CTRL_X, y + ROW_Y + ROW_H * 5 - 2, 58, 12,
                () -> Component.translatable(menu.redstone().key()),
                b -> send(b == 1 ? RunicPedestalBlockEntity.B_REDSTONE_PREV : RunicPedestalBlockEntity.B_REDSTONE_NEXT)));
        red.setTooltip(Tooltip.create(Component.translatable(K + "redstone.tooltip")));
        // v1.5: Liga/Desliga
        RuneButton toggle = addRenderableWidget(new RuneButton(x + 8, y + TOGGLE_Y, 40, 12,
                () -> menu.enabled() ? Component.translatable(K + "on").withColor(ON) : Component.translatable(K + "off").withColor(OFF),
                b -> send(RunicPedestalBlockEntity.B_TOGGLE)));
        toggle.setTooltip(Tooltip.create(Component.translatable(K + "toggle.tooltip")));
        // Testar
        RuneButton test = addRenderableWidget(new RuneButton(x + 8, y + TEST_Y, 40, 12,
                () -> Component.translatable(K + "test"), b -> send(RunicPedestalBlockEntity.B_TEST)));
        test.setTooltip(Tooltip.create(Component.translatable(K + "test.tooltip")));
    }

    /** Par de botões − / + numa linha (Shift = passo grande, se houver). */
    private void minusPlus(int y, int down, int up, int downBig, int upBig, String tooltipKey) {
        RuneButton minus = addRenderableWidget(new RuneButton(leftPos + CTRL_X, y, 12, 12, () -> Component.translatable(K + "minus"),
                b -> send(Screen.hasShiftDown() && downBig >= 0 ? downBig : down)));
        RuneButton plus = addRenderableWidget(new RuneButton(leftPos + CTRL_X + 46, y, 12, 12, () -> Component.translatable(K + "plus"),
                b -> send(Screen.hasShiftDown() && upBig >= 0 ? upBig : up)));
        minus.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
        plus.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
    }

    private void send(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        int mx = mouseX - leftPos, my = mouseY - topPos;
        int coreW = font.width(coreLabel());
        if (mx >= 167 - coreW && mx < 168 && my >= MANA_Y - 1 && my < MANA_Y + 9) {
            g.renderComponentTooltip(font, List.of(coreTooltip()), mouseX, mouseY);
        } else if (mx >= 8 && mx < 168 && my >= MANA_Y - 1 && my < BAR_Y + 7) {
            g.renderComponentTooltip(font, List.of(Component.translatable(K + "mana.tooltip", ManaNet.BATTERY_RANGE)), mouseX, mouseY);
        } else if (hoveredSlot != null && hoveredSlot.index == RunicPedestalBlockEntity.SLOT_FILTER && !hoveredSlot.hasItem()) {
            g.renderComponentTooltip(font, List.of(Component.translatable(K + "filter").withColor(GOLD),
                    Component.translatable(K + "filter.tooltip")), mouseX, mouseY);
        } else if (hoveredSlot != null && hoveredSlot.index == RunicPedestalBlockEntity.SLOT_SCROLL && !hoveredSlot.hasItem()) {
            g.renderComponentTooltip(font, List.of(Component.translatable(K + "scroll").withColor(GOLD),
                    Component.translatable(K + "scroll.tooltip")), mouseX, mouseY);
        } else if (mx >= 8 && mx < 48 && my >= COST_Y - 1 && my < COST_Y + 9 && menu.hasScroll()) {
            g.renderComponentTooltip(font, List.of(menu.element().displayName(),
                    Component.translatable(K + "cost.tooltip", menu.cost())), mouseX, mouseY);
        } else if (mx >= 8 && mx < 48 && my >= 90 && my < 98) {
            g.renderComponentTooltip(font, List.of(Component.translatable(K + "timer.tooltip", Mth.ceil(menu.timer() / 20F))), mouseX, mouseY);
        } else if (mx >= 8 && mx < 168 && my >= STATUS_Y - 1 && my < STATUS_Y + 18) {
            PedestalStatus st = menu.status();
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable(st.key()).withColor(st.rgb));
            lines.add(Component.translatable(st.hintKey(), RunicPedestalBlockEntity.maxPerChunk(), ManaNet.BATTERY_RANGE));
            Component error = st == PedestalStatus.INVALID ? recipeError() : null;
            if (error != null) lines.add(error.copy().withStyle(ChatFormatting.RED));
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }

    /** v1.5: indicador do Núcleo no fim da linha do mana. */
    private Component coreLabel() {
        return switch (menu.coreState()) {
            case RunicPedestalBlockEntity.CORE_OK -> Component.translatable(K + "core.ok", menu.coreLoss()).withColor(ON);
            case RunicPedestalBlockEntity.CORE_NONE -> Component.translatable(K + "core.none").withColor(DIM);
            default -> Component.translatable(K + "core.bad").withColor(0xFFFF9F5A);
        };
    }

    private Component coreTooltip() {
        return switch (menu.coreState()) {
            case RunicPedestalBlockEntity.CORE_OK -> Component.translatable(K + "core.tooltip.ok", menu.coreDistance(), menu.coreLoss());
            case RunicPedestalBlockEntity.CORE_UNLOADED -> Component.translatable(K + "core.tooltip.unloaded");
            case RunicPedestalBlockEntity.CORE_MISSING -> Component.translatable(K + "core.tooltip.missing");
            case RunicPedestalBlockEntity.CORE_FAR -> Component.translatable(K + "core.tooltip.far", WirelessMana.RANGE);
            default -> Component.translatable(K + "core.tooltip.none", WirelessMana.RANGE);
        };
    }

    /** Erro da receita do pergaminho no espaço (o cliente tem o item, então dá para dizer O QUE está errado). */
    @Nullable
    private Component recipeError() {
        ItemStack scroll = menu.getSlot(0).getItem();
        SpellRecipe recipe = scroll.get(CoreRegistries.SPELL.get());
        if (recipe == null || recipe.isEmpty()) return null;
        SpellParser.Result r = SpellParser.parse(recipe, RunicPedestalBlockEntity.primaryElement(recipe), null);
        return r.ok() ? null : r.error();
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.blit(BG, x, y, 0, 0, imageWidth, imageHeight, 256, 256);
        int rgb = 0xFF000000 | menu.element().color;

        // Barra de mana das baterias (enche até o custo de 50 conjurações)
        int mana = menu.mana();
        int cost = Math.max(1, menu.cost());
        if (mana > 0) {
            float frac = Math.min(1F, mana / (float) Math.max(cost * 50, BAR_FLOOR));
            int w = Math.max(1, Math.round(158 * frac));
            g.fill(x + 9, y + BAR_Y + 1, x + 9 + w, y + BAR_Y + 5, 0xFF7A4FD8);
            g.fill(x + 9, y + BAR_Y + 1, x + 9 + w, y + BAR_Y + 2, 0xFFB896FF);
        }
        // Marca do custo de UMA conjuração
        if (menu.hasScroll() && menu.cost() > 0) {
            int cx = x + 9 + Math.min(157, Math.round(158F * cost / Math.max(cost * 50, BAR_FLOOR)));
            g.fill(cx, y + BAR_Y, cx + 1, y + BAR_Y + 6, GOLD);
        }

        // Barra da próxima conjuração
        PedestalStatus st = menu.status();
        int total = menu.redstone() == RedstoneMode.PULSE ? 10 : menu.interval() * 20;
        float progress = st.running && menu.redstone() != RedstoneMode.PULSE ? 1F - Mth.clamp(menu.timer() / (float) total, 0F, 1F) : 0F;
        if (progress > 0) g.fill(x + 9, y + 92, x + 9 + Math.round(38 * progress), y + 95, rgb);

        // Pergaminho: brilho em volta do espaço quando ativo
        int sx = x + RunicPedestalMenu.SCROLL_X, sy = y + RunicPedestalMenu.SCROLL_Y;
        if (menu.hasScroll() && st.running) {
            int a = (int) (90 + 60 * Math.sin((minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() + partialTick : 0) / 6.0));
            int glow = (a << 24) | (menu.element().color & 0xFFFFFF);
            g.fill(sx - 2, sy - 2, sx + 18, sy - 1, glow);
            g.fill(sx - 2, sy + 17, sx + 18, sy + 18, glow);
            g.fill(sx - 2, sy - 1, sx - 1, sy + 17, glow);
            g.fill(sx + 17, sy - 1, sx + 18, sy + 17, glow);
        }
        // v1.5: espaços vazios mostram um "fantasma" do que vai neles (pergaminho / semente)
        if (!menu.hasScroll()) ghost(g, new ItemStack(CoreRegistries.SPELL_SCROLL.get()), sx, sy);
        if (menu.filterItem().isEmpty()) ghost(g, new ItemStack(Items.WHEAT_SEEDS), x + RunicPedestalMenu.FILTER_X, y + RunicPedestalMenu.FILTER_Y);
    }

    /** Item apagado dentro de um espaço vazio (desenha o item e cobre com o fundo do espaço, translúcido). */
    private static void ghost(GuiGraphics g, ItemStack stack, int x, int y) {
        g.renderFakeItem(stack, x, y);
        g.pose().pushPose();
        g.pose().translate(0, 0, 250);
        g.fill(x, y, x + 16, y + 16, 0xB8100C17);
        g.pose().popPose();
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, GOLD, false);
        Component casts = Component.translatable(K + "casts", menu.casts());
        g.drawString(font, casts, imageWidth - 8 - font.width(casts), titleLabelY, DIM, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL, false);

        // Coluna da esquerda: nomes dos espaços e custo por conjuração (ou a dica do espaço vazio)
        tiny(g, Component.translatable(K + "scroll"), RunicPedestalMenu.SCROLL_X + 8, SLOT_LABEL_Y, DIM);
        tiny(g, Component.translatable(K + "filter"), RunicPedestalMenu.FILTER_X + 8, SLOT_LABEL_Y, menu.filterItem().isEmpty() ? DIM : GOLD);
        if (menu.hasScroll()) {
            drawFitted(g, centered(Component.translatable(K + "cost", menu.cost()), 40), 8, COST_Y, 40, VALUE);
        } else {
            drawFitted(g, centered(Component.translatable(K + "slot_hint"), 40), 8, COST_Y, 40, DIM);
        }

        // Linhas da direita
        row(g, 0, Component.translatable(K + "interval"), Component.translatable(K + "seconds", menu.interval()));
        row(g, 1, Component.translatable(K + "direction"), null);
        row(g, 2, Component.translatable(K + "offset_x"), signed(menu.dx()));
        row(g, 3, Component.translatable(K + "offset_y"), signed(menu.dy()));
        row(g, 4, Component.translatable(K + "offset_z"), signed(menu.dz()));
        row(g, 5, Component.translatable(K + "redstone"), null);
        // Sinal de redstone agora (bolinha vermelha acesa/apagada)
        int dot = menu.powered() ? 0xFFFF3B3B : 0xFF4A2A2A;
        g.fill(ROW_X + 46, ROW_Y + ROW_H * 5 + 2, ROW_X + 49, ROW_Y + ROW_H * 5 + 5, dot);

        // Mana (baterias + Núcleo) e, no fim da linha, o indicador do Núcleo ligado
        Component core = coreLabel();
        int coreW = font.width(core);
        g.drawString(font, core, 167 - coreW, MANA_Y, 0xFFFFFFFF, false);
        drawFitted(g, Component.translatable(K + "mana", menu.mana(), menu.batteries()), 9, MANA_Y, 154 - coreW, LABEL);

        // Estado + dica
        PedestalStatus st = menu.status();
        g.drawString(font, Component.translatable(K + "status", Component.translatable(st.key())), 9, STATUS_Y, 0xFF000000 | st.rgb, false);
        MutableComponent hint = Component.translatable(st.hintKey(), RunicPedestalBlockEntity.maxPerChunk(), ManaNet.BATTERY_RANGE);
        drawFitted(g, hint, 9, STATUS_Y + 9, 158, DIM);
    }

    /**
     * Uma linha só (não há espaço para duas): texto que não cabe encolhe até 75%; se nem assim couber, corta
     * (o texto inteiro aparece no tooltip do estado).
     */
    private void drawFitted(GuiGraphics g, Component text, int x, int y, int maxWidth, int color) {
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

    /** Texto pequeno (60%) centrado em cx. */
    private void tiny(GuiGraphics g, Component text, int cx, int y, int color) {
        float s = 0.6F;
        g.pose().pushPose();
        g.pose().translate(cx - font.width(text) * s / 2F, y, 0);
        g.pose().scale(s, s, 1F);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    /** Põe espaços à esquerda para o texto ficar mais ou menos centrado numa largura (o drawFitted desenha da esquerda). */
    private Component centered(Component text, int width) {
        int w = font.width(text);
        if (w >= width) return text;
        int pad = (width - w) / 2 / Math.max(1, font.width(" "));
        return Component.literal(" ".repeat(pad)).append(text);
    }

    private void row(GuiGraphics g, int i, Component label, Component value) {
        int y = ROW_Y + ROW_H * i;
        g.drawString(font, label, ROW_X, y, LABEL, false);
        if (value != null) g.drawCenteredString(font, value, CTRL_X + 29, y, VALUE);
    }

    private static Component signed(int v) {
        return Component.literal(v > 0 ? "+" + v : String.valueOf(v));
    }
}
