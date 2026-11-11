package com.renanpontes.isekaimahou.feature.ritual_mage.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.ritual_mage.block.ResearchTableBlockEntity;
import com.renanpontes.isekaimahou.feature.ritual_mage.block.ResearchTableMenu;
import com.renanpontes.isekaimahou.feature.ritual_mage.network.ClientResearch;
import com.renanpontes.isekaimahou.feature.ritual_mage.research.Thesis;
import com.renanpontes.isekaimahou.feature.ritual_mage.research.ThesisPuzzle;
import com.renanpontes.isekaimahou.feature.ritual_mage.research.ThesisService;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

/**
 * Tela da Mesa de Pesquisa: lista de teses (esquerda), grade rúnica 5×5 (centro), detalhes e runas
 * disponíveis (direita), espaços de notas/papel e o inventário (embaixo).
 */
public class ResearchTableScreen extends AbstractContainerScreen<ResearchTableMenu> {
    private static final ResourceLocation BG = IsekaiMahou.id("textures/gui/mage_research_table.png");
    private static final String K = "screen.isekai_mahou.research_table.";
    private static final int LIST_X = 7, LIST_Y = 19, ROW_H = 11, LIST_W = 102;
    private static final int GRID_X = 114, GRID_Y = 19, CELL = 19;
    private static final int PANEL_X = 218, PANEL_Y = 19, PANEL_W = 113;
    private static final int PALETTE_Y = 96, PALETTE_COLS = 7, PALETTE_ROWS = 2;
    private static final int HELP_X = 322, HELP_Y = 19;

    private Element selected;
    private int paletteScroll;
    private int dragCell = -1;
    private boolean dragging;
    private Button completeButton;
    private Button rerollButton;

    // Estado calculado a cada quadro
    private Thesis thesis;
    private ThesisPuzzle puzzle;
    private Element[] placed = new Element[ThesisPuzzle.CELLS];
    private ThesisPuzzle.Evaluation eval;
    private final int[] used = new int[Element.values().length];
    private final List<Element> palette = new ArrayList<>();
    private Component status = Component.empty();
    private int statusColor = RuneArt.INK;

    public ResearchTableScreen(ResearchTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 340;
        imageHeight = 232;
    }

    @Override
    protected void init() {
        super.init();
        completeButton = addRenderableWidget(Button.builder(Component.translatable(K + "complete"),
                b -> send(ResearchTableBlockEntity.BTN_COMPLETE)).bounds(leftPos + GRID_X, topPos + 119, 95, 15).build());
        addRenderableWidget(Button.builder(Component.translatable(K + "clear"),
                b -> send(ResearchTableBlockEntity.BTN_CLEAR)).bounds(leftPos + 178, topPos + 207, 60, 16).build());
        rerollButton = addRenderableWidget(Button.builder(Component.translatable(K + "reroll"),
                        b -> send(ResearchTableBlockEntity.BTN_REROLL)).bounds(leftPos + 240, topPos + 207, 66, 16)
                .tooltip(Tooltip.create(Component.translatable(K + "reroll.tooltip"))).build());
    }

    private void send(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    private void sound(SoundEvent sound, float pitch) {
        if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, 0.6F));
    }

    // =====================================================================================
    //  Estado
    // =====================================================================================
    private void computeState() {
        thesis = menu.thesis();
        puzzle = menu.puzzle();
        placed = menu.placed();
        java.util.Arrays.fill(used, 0);
        for (Element e : placed) if (e != null) used[e.ordinal()]++;
        palette.clear();
        for (Element e : Element.values()) if (menu.notesOf(e) > 0 || used[e.ordinal()] > 0) palette.add(e);
        if (selected == null || !palette.contains(selected)) selected = palette.isEmpty() ? null : palette.get(0);
        int maxScroll = Math.max(0, (palette.size() + PALETTE_COLS - 1) / PALETTE_COLS - PALETTE_ROWS);
        paletteScroll = Mth.clamp(paletteScroll, 0, maxScroll);

        eval = thesis == null || puzzle == null ? null : puzzle.evaluate(placed, thesis);
        boolean ready = false;
        if (thesis == null) {
            setStatus(K + "choose", RuneArt.INK_SOFT);
        } else if (ClientResearch.hasThesis(thesis.id())) {
            setStatus(K + "status.done", RuneArt.GOOD);
        } else if (ClientResearch.thesisCount() < thesis.requiredTheses()) {
            setStatus(K + "status.locked", RuneArt.BAD);
        } else if (!eval.connected) {
            setStatus(K + "status.open", RuneArt.INK_SOFT);
        } else if (!eval.conflictPairs.isEmpty()) {
            setStatus(K + "status.conflict", RuneArt.BAD);
        } else if (eval.anyFloating) {
            setStatus(K + "status.floating", RuneArt.WARN);
        } else if (!eval.requirementsMet) {
            setStatus(K + "status.reqs", RuneArt.WARN);
        } else if (!notesSuffice()) {
            setStatus(K + "status.notes", RuneArt.BAD);
        } else if (!creative() && menu.paper() < thesis.paper()) {
            setStatus(K + "status.paper", RuneArt.BAD);
        } else if (menu.shelves() < thesis.shelves()) {
            setStatus(K + "status.shelves", RuneArt.BAD);
        } else {
            setStatus(K + "status.ready", RuneArt.GOOD);
            ready = true;
        }
        completeButton.active = ready;
        rerollButton.active = thesis != null;
    }

    private void setStatus(String key, int color) {
        status = Component.translatable(key);
        statusColor = color;
    }

    private boolean notesSuffice() {
        for (Element e : Element.values()) if (used[e.ordinal()] > menu.notesOf(e)) return false;
        return true;
    }

    private boolean creative() {
        return minecraft != null && minecraft.player != null && minecraft.player.getAbilities().instabuild;
    }

    private int available(Element e) {
        return menu.notesOf(e) - used[e.ordinal()];
    }

    // =====================================================================================
    //  Desenho
    // =====================================================================================
    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        computeState();
        g.blit(BG, leftPos, topPos, 0, 0, imageWidth, imageHeight, 512, 256);
        renderThesisList(g, mouseX, mouseY);
        renderGrid(g, mouseX, mouseY);
        renderPanel(g);
        renderPalette(g);
        // Ícone de ajuda "?"
        int hx = leftPos + HELP_X, hy = topPos + HELP_Y;
        boolean hover = inside(mouseX, mouseY, hx, hy, 10, 10);
        g.fill(hx, hy, hx + 10, hy + 10, hover ? 0xFFD6AC4A : 0xFFB49D70);
        g.renderOutline(hx, hy, 10, 10, 0xFF6B5236);
        g.drawString(font, "?", hx + 3, hy + 1, RuneArt.INK, false);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 4, 0xFFF3D98A, true);
        Component rank = Component.translatable(K + "rank", ClientResearch.thesisCount(), Thesis.ALL.size());
        g.drawString(font, rank, imageWidth - 8 - font.width(rank), 4, 0xFFE8D7B0, true);
        g.drawString(font, playerInventoryTitle, 8, 140, RuneArt.INK, false);
        g.drawString(font, Component.translatable(K + "notes"), 178, 140, RuneArt.INK, false);
        g.drawString(font, Component.translatable(K + "paper"), 296, 140, RuneArt.INK, false);
        if (thesis != null) {
            int have = menu.paper();
            String s = have + "/" + thesis.paper();
            g.drawString(font, s, 308 - font.width(s) / 2, 170, creative() || have >= thesis.paper() ? RuneArt.GOOD : RuneArt.BAD, false);
        }
    }

    private void renderThesisList(GuiGraphics g, int mouseX, int mouseY) {
        for (int i = 0; i < Thesis.ALL.size(); i++) {
            Thesis t = Thesis.ALL.get(i);
            int x = leftPos + LIST_X + 2, y = topPos + LIST_Y + i * ROW_H;
            boolean done = ClientResearch.hasThesis(t.id());
            boolean unlocked = ClientResearch.thesisCount() >= t.requiredTheses();
            boolean sel = menu.thesisIndex() == i;
            boolean hover = inside(mouseX, mouseY, leftPos + LIST_X, y, LIST_W, ROW_H);
            if (sel) {
                g.fill(x - 1, y, x + LIST_W - 4, y + ROW_H - 1, 0x60D6AC4A);
                g.renderOutline(x - 1, y, LIST_W - 3, ROW_H - 1, 0xFF8F6A28);
            } else if (hover && unlocked && !done) {
                g.fill(x - 1, y, x + LIST_W - 4, y + ROW_H - 1, 0x35FFFFFF);
            }
            if (done) g.drawString(font, "✔", x, y + 2, RuneArt.GOOD, false);
            else if (!unlocked) RuneArt.lock(g, x, y + 2);
            else RuneArt.smallRune(g, t.entry(), x - 1, y + 1);
            String label = ellipsize(t.numeral() + " " + t.displayName().getString(), LIST_W - 16);
            g.drawString(font, label, x + 10, y + 2, done ? RuneArt.GOOD : unlocked ? RuneArt.INK : RuneArt.INK_FADED, false);
        }
    }

    private void renderGrid(GuiGraphics g, int mouseX, int mouseY) {
        int gx = leftPos + GRID_X, gy = topPos + GRID_Y;
        if (puzzle == null) {
            g.fill(gx, gy, gx + CELL * 5, gy + CELL * 5, 0x90101018);
            List<FormattedCharSequence> lines = font.split(Component.translatable(K + "choose"), CELL * 5 - 10);
            int y = gy + CELL * 5 / 2 - lines.size() * 5;
            for (FormattedCharSequence line : lines) {
                g.drawString(font, line, gx + (CELL * 5 - font.width(line)) / 2, y, 0xFFE8D7B0, true);
                y += 10;
            }
            return;
        }
        float pulse = 0.5F + 0.5F * Mth.sin(Util.getMillis() / 220F);
        boolean valid = eval.valid();
        // Ligações do circuito (por baixo das runas)
        for (int[] link : eval.links) {
            int ax = gx + ThesisPuzzle.col(link[0]) * CELL + 9, ay = gy + ThesisPuzzle.row(link[0]) * CELL + 9;
            int bx = gx + ThesisPuzzle.col(link[1]) * CELL + 9, by = gy + ThesisPuzzle.row(link[1]) * CELL + 9;
            Element ea = puzzle.runeAt(link[0], placed), eb = puzzle.runeAt(link[1], placed);
            int alpha = eval.connected ? (int) (140 + 115 * pulse) : 170;
            int color = RuneArt.blend(ea.color, eb.color, alpha);
            if (ay == by) g.fill(Math.min(ax, bx), ay - 1, Math.max(ax, bx) + 1, ay + 2, color);
            else g.fill(ax - 1, Math.min(ay, by), ax + 2, Math.max(ay, by) + 1, color);
        }
        int hoverCell = cellAt(mouseX, mouseY);
        for (int c = 0; c < ThesisPuzzle.CELLS; c++) {
            int x = gx + ThesisPuzzle.col(c) * CELL, y = gy + ThesisPuzzle.row(c) * CELL;
            switch (puzzle.type[c]) {
                case ThesisPuzzle.BLOCKED -> RuneArt.fissure(g, x, y);
                case ThesisPuzzle.ENTRY -> {
                    RuneArt.rune(g, puzzle.element[c], x + 1, y + 1);
                    RuneArt.entryFrame(g, x, y);
                }
                case ThesisPuzzle.EXIT -> {
                    if (eval.connected) g.fill(x + 1, y + 1, x + 18, y + 18, (int) (90 * pulse) << 24 | 0xFFE27A);
                    RuneArt.rune(g, puzzle.element[c], x + 1, y + 1);
                    RuneArt.exitFrame(g, x, y);
                }
                case ThesisPuzzle.FIXED -> {
                    if (eval.inCircuit[c]) g.fill(x + 1, y + 1, x + 18, y + 18, (int) (110 * pulse) << 24 | 0x9FF0D0);
                    RuneArt.rune(g, puzzle.element[c], x + 1, y + 1);
                    RuneArt.lock(g, x + 12, y + 12);
                }
                default -> {
                    Element e = placed[c];
                    if (e != null) {
                        if (eval.conflict[c]) g.fill(x + 1, y + 1, x + 18, y + 18, (int) (60 + 120 * pulse) << 24 | 0xFF2020);
                        else if (eval.floating[c]) g.fill(x + 1, y + 1, x + 18, y + 18, 0x50FFA020);
                        RuneArt.rune(g, e, x + 1, y + 1);
                        if (used[e.ordinal()] > menu.notesOf(e)) g.renderOutline(x + 1, y + 1, 17, 17, 0xFFFF4040);
                    } else if (c == hoverCell && selected != null) {
                        RenderSystem.enableBlend();
                        g.setColor(1F, 1F, 1F, 0.45F);
                        RuneArt.rune(g, selected, x + 1, y + 1);
                        g.setColor(1F, 1F, 1F, 1F);
                        RenderSystem.disableBlend();
                    }
                    if (c == hoverCell) g.fill(x + 1, y + 1, x + 18, y + 18, 0x28FFFFFF);
                }
            }
        }
        for (int[] pair : eval.conflictPairs) {
            int ax = gx + ThesisPuzzle.col(pair[0]) * CELL + 9, ay = gy + ThesisPuzzle.row(pair[0]) * CELL + 9;
            int bx = gx + ThesisPuzzle.col(pair[1]) * CELL + 9, by = gy + ThesisPuzzle.row(pair[1]) * CELL + 9;
            RuneArt.conflict(g, (ax + bx) / 2 - 4, (ay + by) / 2 - 4);
        }
        if (valid) {
            int glow = (int) (120 + 135 * pulse) << 24 | 0xF3D98A;
            g.renderOutline(gx - 2, gy - 2, CELL * 5 + 3, CELL * 5 + 3, glow);
        }
        // Aviso de tese concluída
        if (Util.getMillis() - ClientResearch.thesisCompletedAt < 3500) {
            g.fill(gx - 2, gy + 36, gx + CELL * 5 + 1, gy + 58, 0xD0100C18);
            g.renderOutline(gx - 2, gy + 36, CELL * 5 + 3, 22, 0xFFD6AC4A);
            Component banner = Component.translatable(K + "completed_banner");
            g.drawString(font, banner, gx + (CELL * 5 - font.width(banner)) / 2, gy + 43, 0xFFF3D98A, true);
        }
    }

    private void renderPanel(GuiGraphics g) {
        int px = leftPos + PANEL_X, py = topPos + PANEL_Y;
        if (thesis == null) {
            List<FormattedCharSequence> lines = font.split(Component.translatable(K + "choose"), PANEL_W - 14);
            int y = py;
            for (FormattedCharSequence line : lines) {
                g.drawString(font, line, px, y, RuneArt.INK_SOFT, false);
                y += 10;
            }
            return;
        }
        g.drawString(font, ellipsize(thesis.displayName().getString(), PANEL_W - 14), px, py, RuneArt.INK, false);
        // Requisitos (3 por linha): runa pequena + "tem/precisa"
        for (int i = 0; i < thesis.reqs().size(); i++) {
            Thesis.Req r = thesis.reqs().get(i);
            int x = px + (i % 3) * 38, y = py + 11 + (i / 3) * 10;
            RuneArt.smallRune(g, r.element() == null ? Element.ARCANE : r.element(), x, y);
            int have = eval.reqHave[i];
            g.drawString(font, have + "/" + r.count(), x + 10, y, have >= r.count() ? RuneArt.GOOD : RuneArt.INK, false);
        }
        int paperHave = menu.paper();
        Component paper = Component.translatable(K + "paper_need", paperHave, thesis.paper());
        g.drawString(font, paper, px, py + 32, creative() || paperHave >= thesis.paper() ? RuneArt.GOOD : RuneArt.BAD, false);
        Component shelves = Component.translatable(K + "shelves", menu.shelves(), thesis.shelves());
        g.drawString(font, shelves, px + PANEL_W - font.width(shelves), py + 32,
                menu.shelves() >= thesis.shelves() ? RuneArt.GOOD : RuneArt.BAD, false);
        int xp = thesis.xp() * (100 + ThesisService.BRIDGE_BONUS_PERCENT * eval.bridges) / 100;
        g.drawString(font, Component.translatable(K + "reward", xp), px, py + 43, RuneArt.INK_SOFT, false);
        if (eval.bridges > 0) {
            Component b = Component.translatable(K + "bridges", eval.bridges, ThesisService.BRIDGE_BONUS_PERCENT * eval.bridges);
            if (font.width(b) < PANEL_W) g.drawString(font, b, px, py + 53, 0xFF2A7A8A, false);
        }
        int y = py + (eval.bridges > 0 ? 64 : 55);
        for (FormattedCharSequence line : font.split(status, PANEL_W)) {
            if (y > topPos + PALETTE_Y - 9) break;
            g.drawString(font, line, px, y, statusColor, false);
            y += 9;
        }
    }

    private void renderPalette(GuiGraphics g) {
        int x0 = leftPos + PANEL_X, y0 = topPos + PALETTE_Y;
        if (palette.isEmpty()) {
            List<FormattedCharSequence> lines = font.split(Component.translatable(K + "palette_empty"), PALETTE_COLS * 16 - 4);
            int y = y0 + 4;
            for (FormattedCharSequence line : lines) {
                g.drawString(font, line, x0 + 2, y, RuneArt.INK_FADED, false);
                y += 10;
            }
            return;
        }
        int start = paletteScroll * PALETTE_COLS;
        for (int k = 0; k < PALETTE_COLS * PALETTE_ROWS && start + k < palette.size(); k++) {
            Element e = palette.get(start + k);
            int x = x0 + (k % PALETTE_COLS) * 16, y = y0 + (k / PALETTE_COLS) * 16;
            RuneArt.rune(g, e, x, y);
            int left = available(e);
            String count = String.valueOf(left);
            g.pose().pushPose();
            g.pose().translate(0, 0, 50);
            g.drawString(font, count, x + 17 - font.width(count), y + 9, left > 0 ? 0xFFFFFFFF : 0xFFFF5555, true);
            g.pose().popPose();
            if (e == selected) RuneArt.select(g, x - 1, y - 1);
        }
        int rows = (palette.size() + PALETTE_COLS - 1) / PALETTE_COLS;
        if (rows > PALETTE_ROWS) {
            String more = (paletteScroll + 1) + "/" + (rows - PALETTE_ROWS + 1);
            g.drawString(font, more, x0 + PALETTE_COLS * 16 - font.width(more), y0 - 9, RuneArt.INK_FADED, false);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        if (menu.getCarried().isEmpty() && hoveredSlot == null) renderCustomTooltips(g, mouseX, mouseY);
    }

    private void renderCustomTooltips(GuiGraphics g, int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>();
        // Teses
        for (int i = 0; i < Thesis.ALL.size(); i++) {
            if (inside(mouseX, mouseY, leftPos + LIST_X, topPos + LIST_Y + i * ROW_H, LIST_W, ROW_H)) thesisTooltip(Thesis.ALL.get(i), lines);
        }
        // Células
        int cell = cellAt(mouseX, mouseY);
        if (cell >= 0 && puzzle != null) cellTooltip(cell, lines);
        // Requisitos
        if (thesis != null && eval != null) {
            for (int i = 0; i < thesis.reqs().size(); i++) {
                int x = leftPos + PANEL_X + (i % 3) * 38, y = topPos + PANEL_Y + 11 + (i / 3) * 10;
                if (inside(mouseX, mouseY, x, y, 34, 9)) {
                    Thesis.Req r = thesis.reqs().get(i);
                    lines.add(Component.translatable(K + "req", r.label(), eval.reqHave[i], r.count()));
                }
            }
        }
        // Paleta
        Element pe = paletteAt(mouseX, mouseY);
        if (pe != null) {
            lines.add(pe.displayName());
            lines.add(Component.translatable(K + "palette").append(": " + available(pe) + "/" + menu.notesOf(pe)).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable(K + "cell.compat", RuneArt.compatibility(pe)).withStyle(ChatFormatting.DARK_GRAY));
        }
        // Ajuda
        if (inside(mouseX, mouseY, leftPos + HELP_X, topPos + HELP_Y, 10, 10)) {
            lines.add(Component.translatable(K + "help").withStyle(ChatFormatting.GOLD));
            for (int i = 1; i <= 5; i++) lines.add(Component.translatable(K + "help" + i).withStyle(ChatFormatting.GRAY));
        }
        if (lines.isEmpty()) return;
        List<FormattedCharSequence> seq = new ArrayList<>();
        for (Component c : lines) seq.addAll(font.split(c, 220));
        g.renderTooltip(font, seq, mouseX, mouseY);
    }

    private void thesisTooltip(Thesis t, List<Component> lines) {
        lines.add(Component.literal(t.numeral() + " · ").append(t.displayName()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        lines.add(t.description().withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable(K + "thesis.circuit", t.entry().displayName(), t.exit().displayName()));
        MutableComponent notes = Component.empty();
        for (int i = 0; i < t.reqs().size(); i++) {
            if (i > 0) notes.append(", ");
            notes.append(t.reqs().get(i).label()).append(" ×" + t.reqs().get(i).count());
        }
        lines.add(Component.translatable(K + "thesis.notes", notes));
        lines.add(Component.translatable(K + "thesis.costs", t.paper(), t.shelves()).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable(K + "reward", t.xp()).withStyle(ChatFormatting.LIGHT_PURPLE));
        int after = ClientResearch.thesisCount() + 1;
        if (after <= 5) lines.add(Component.translatable(K + "thesis.runes", Thesis.runeTierFor(after)).withStyle(ChatFormatting.AQUA));
        else lines.add(Component.translatable(K + "thesis.essence").withStyle(ChatFormatting.AQUA));
        if (t.awakens() != null && ClientMagic.data().path() == MagicPath.MAGE && !ClientMagic.data().isAwakened(t.awakens())) {
            lines.add(Component.translatable(K + "thesis.awakens", t.awakens().displayName()).withStyle(ChatFormatting.YELLOW));
        }
        if (ClientResearch.hasThesis(t.id())) {
            lines.add(Component.translatable(K + "thesis.done").withStyle(ChatFormatting.GREEN));
        } else if (ClientResearch.thesisCount() < t.requiredTheses()) {
            lines.add(Component.translatable(K + "thesis.requires", t.requiredTheses()).withStyle(ChatFormatting.RED));
        } else {
            lines.add(Component.translatable(K + "thesis.available").withStyle(ChatFormatting.GREEN));
        }
    }

    private void cellTooltip(int cell, List<Component> lines) {
        Element e = puzzle.runeAt(cell, placed);
        switch (puzzle.type[cell]) {
            case ThesisPuzzle.BLOCKED -> lines.add(Component.translatable(K + "cell.blocked").withStyle(ChatFormatting.GRAY));
            case ThesisPuzzle.ENTRY -> lines.add(Component.translatable(K + "cell.entry", e.displayName()));
            case ThesisPuzzle.EXIT -> lines.add(Component.translatable(K + "cell.exit", e.displayName()));
            case ThesisPuzzle.FIXED -> {
                lines.add(Component.translatable(K + "cell.fixed", e.displayName()));
                lines.add(Component.translatable(K + "cell.fixed_hint").withStyle(ChatFormatting.DARK_AQUA));
            }
            default -> {
                if (e != null) lines.add(Component.translatable(K + "cell.placed", e.displayName()));
            }
        }
        if (e != null) lines.add(Component.translatable(K + "cell.compat", RuneArt.compatibility(e)).withStyle(ChatFormatting.GRAY));
    }

    // =====================================================================================
    //  Entrada
    // =====================================================================================
    private int cellAt(double mx, double my) {
        int gx = leftPos + GRID_X, gy = topPos + GRID_Y;
        if (mx < gx || my < gy || mx >= gx + CELL * 5 || my >= gy + CELL * 5) return -1;
        return ThesisPuzzle.cell((int) ((my - gy) / CELL), (int) ((mx - gx) / CELL));
    }

    private Element paletteAt(double mx, double my) {
        int x0 = leftPos + PANEL_X, y0 = topPos + PALETTE_Y;
        if (mx < x0 || my < y0 || mx >= x0 + PALETTE_COLS * 16 || my >= y0 + PALETTE_ROWS * 16) return null;
        int idx = paletteScroll * PALETTE_COLS + (int) ((my - y0) / 16) * PALETTE_COLS + (int) ((mx - x0) / 16);
        return idx >= 0 && idx < palette.size() ? palette.get(idx) : null;
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int i = 0; i < Thesis.ALL.size(); i++) {
            if (button == 0 && inside(mx, my, leftPos + LIST_X, topPos + LIST_Y + i * ROW_H, LIST_W, ROW_H)) {
                Thesis t = Thesis.ALL.get(i);
                if (!ClientResearch.hasThesis(t.id()) && ClientResearch.thesisCount() >= t.requiredTheses()) {
                    send(i);
                    sound(SoundEvents.BOOK_PAGE_TURN, 1.0F);
                } else {
                    sound(SoundEvents.VILLAGER_NO, 1.2F);
                }
                return true;
            }
        }
        int cell = cellAt(mx, my);
        if (cell >= 0 && puzzle != null) {
            dragging = button == 0;
            dragCell = cell;
            clickCell(cell, button, true);
            return true;
        }
        Element pe = paletteAt(mx, my);
        if (pe != null && button == 0) {
            selected = pe;
            sound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.9F + pe.ordinal() * 0.03F);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    /** Escreve/apaga uma runa. {@code toggle}: clicar numa runa igual apaga (não vale ao arrastar). */
    private void clickCell(int cell, int button, boolean toggle) {
        if (!puzzle.isWritable(cell)) return;
        Element current = placed[cell];
        if (button == 1) {
            if (current != null) {
                send(ResearchTableBlockEntity.BTN_REMOVE + cell);
                sound(SoundEvents.BOOK_PAGE_TURN, 1.4F);
            }
            return;
        }
        if (button != 0 || selected == null) return;
        if (current == selected) {
            if (toggle) {
                send(ResearchTableBlockEntity.BTN_REMOVE + cell);
                sound(SoundEvents.BOOK_PAGE_TURN, 1.4F);
            }
            return;
        }
        if (available(selected) <= 0) {
            if (toggle) sound(SoundEvents.VILLAGER_NO, 1.2F);
            return;
        }
        send(ResearchTableBlockEntity.BTN_PLACE + cell * 32 + selected.ordinal());
        sound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F + selected.ordinal() * 0.03F);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging && puzzle != null) {
            int cell = cellAt(mx, my);
            if (cell >= 0 && cell != dragCell) {
                dragCell = cell;
                if (placed[cell] == null) clickCell(cell, 0, false);
            }
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging) {
            dragging = false;
            dragCell = -1;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int x0 = leftPos + PANEL_X, y0 = topPos + PALETTE_Y;
        if (inside(mx, my, x0, y0, PALETTE_COLS * 16, PALETTE_ROWS * 16) && sy != 0) {
            paletteScroll += sy > 0 ? -1 : 1;
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    private String ellipsize(String s, int width) {
        if (font.width(s) <= width) return s;
        return font.plainSubstrByWidth(s, width - font.width("…")) + "…";
    }
}
