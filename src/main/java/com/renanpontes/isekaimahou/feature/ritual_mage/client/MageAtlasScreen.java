package com.renanpontes.isekaimahou.feature.ritual_mage.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.ritual_mage.MageRegistries;
import com.renanpontes.isekaimahou.feature.ritual_mage.network.ClientResearch;
import com.renanpontes.isekaimahou.feature.ritual_mage.network.MagePayloads.PhenomenonInfo;
import com.renanpontes.isekaimahou.feature.ritual_mage.research.Phenomenon;
import com.renanpontes.isekaimahou.feature.ritual_mage.research.Thesis;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Atlas Arcano: livro aberto com marcadores por categoria. Página esquerda = grade de fenômenos
 * (silhueta se ainda não observado), página direita = detalhes. A aba "Teses" lista o progresso da Pesquisa.
 */
public class MageAtlasScreen extends Screen {
    private static final ResourceLocation TEX = IsekaiMahou.id("textures/gui/mage_atlas.png");
    private static final String K = "screen.isekai_mahou.mage_atlas.";
    private static final String CAT = "mage_category.isekai_mahou.";
    private static final int W = 292, H = 184, TEX_W = 512, TEX_H = 256;
    private static final List<String> TABS = List.of("all", "block", "creature", "biome", "sky", "dimension", "structure", "boss", "theses");
    private static final int COLS = 6, ROWS = 6, SLOT = 20, PER_PAGE = COLS * ROWS;
    private static final int GRID_X = 16, GRID_Y = 36;
    private static final int LEFT_CX = 76, RIGHT_CX = 216, RIGHT_X = 154, RIGHT_W = 124;

    private static String lastTab = "all";
    private static final Map<ResourceLocation, ItemStack> ICONS = new HashMap<>();

    private String tab = lastTab;
    private int page;
    private ResourceLocation selected;
    private int selectedThesis = -1;
    private int left, top;

    public MageAtlasScreen() {
        super(Component.translatable("screen.isekai_mahou.mage_atlas.title"));
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new MageAtlasScreen());
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = Math.max(24, (height - H) / 2 + 8);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------------------------------------------------------- Dados
    private List<PhenomenonInfo> entries() {
        List<PhenomenonInfo> out = new ArrayList<>();
        for (PhenomenonInfo p : ClientResearch.phenomena()) if (tab.equals("all") || p.category().equals(tab)) out.add(p);
        return out;
    }

    private int pages(int size) {
        return Math.max(1, (size + PER_PAGE - 1) / PER_PAGE);
    }

    private static ItemStack icon(PhenomenonInfo p) {
        return ICONS.computeIfAbsent(p.id(), id -> {
            for (ResourceLocation rl : p.icons()) {
                if (BuiltInRegistries.ITEM.containsKey(rl)) return new ItemStack(BuiltInRegistries.ITEM.get(rl));
            }
            return new ItemStack(Items.BOOK);
        });
    }

    private static ItemStack tabIcon(String tab) {
        return switch (tab) {
            case "block" -> new ItemStack(Items.GRASS_BLOCK);
            case "creature" -> new ItemStack(Items.CREEPER_HEAD);
            case "biome" -> new ItemStack(Items.OAK_SAPLING);
            case "sky" -> new ItemStack(Items.CLOCK);
            case "dimension" -> new ItemStack(Items.ENDER_EYE);
            case "structure" -> new ItemStack(Items.BRICKS);
            case "boss" -> new ItemStack(Items.DRAGON_HEAD);
            case "theses" -> new ItemStack(Items.WRITABLE_BOOK);
            default -> new ItemStack(MageRegistries.MAGE_ATLAS.get());
        };
    }

    private static List<Element> elements(PhenomenonInfo p) {
        List<Element> out = new ArrayList<>();
        for (String s : p.elements()) {
            Element e = Element.byId(s);
            if (e != null) out.add(e);
        }
        return out;
    }

    private static Phenomenon.Rarity rarity(PhenomenonInfo p) {
        Phenomenon.Rarity[] v = Phenomenon.Rarity.values();
        return v[Math.max(0, Math.min(v.length - 1, p.rarity()))];
    }

    // ---------------------------------------------------------------- Desenho
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int selTab = TABS.indexOf(tab);
        for (int i = 0; i < TABS.size(); i++) if (i != selTab) renderTab(g, i, false);
        g.blit(TEX, left, top, 0, 0, W, H, TEX_W, TEX_H);
        if (selTab >= 0) renderTab(g, selTab, true);

        if (tab.equals("theses")) {
            renderThesisList(g, mouseX, mouseY);
            renderThesisDetails(g);
        } else {
            renderGrid(g, mouseX, mouseY);
            renderDetails(g);
        }
        renderTooltips(g, mouseX, mouseY);
    }

    private void renderTab(GuiGraphics g, int i, boolean selectedTab) {
        int x = left + 10 + i * 27, y = top - (selectedTab ? 20 : 17);
        g.blit(TEX, x, y, 300, selectedTab ? 22 : 0, 24, 20, TEX_W, TEX_H);
        g.renderItem(tabIcon(TABS.get(i)), x + 4, y + 2);
    }

    private void centered(GuiGraphics g, Component text, int cx, int y, int color) {
        g.drawString(font, text, cx - font.width(text) / 2, y, color, false);
    }

    private int wrapped(GuiGraphics g, Component text, int x, int y, int width, int color, int maxLines) {
        int n = 0;
        for (FormattedCharSequence line : font.split(text, width)) {
            if (n++ >= maxLines) break;
            g.drawString(font, line, x, y, color, false);
            y += 9;
        }
        return y;
    }

    private void renderGrid(GuiGraphics g, int mouseX, int mouseY) {
        List<PhenomenonInfo> list = entries();
        int seen = 0;
        for (PhenomenonInfo p : list) if (ClientResearch.isDiscovered(p.id())) seen++;
        centered(g, Component.translatable(CAT + tab).withStyle(ChatFormatting.BOLD), left + LEFT_CX, top + 12, RuneArt.INK);
        centered(g, Component.translatable(K + "progress", seen, list.size()), left + LEFT_CX, top + 23, RuneArt.INK_SOFT);
        if (list.isEmpty()) {
            wrapped(g, Component.translatable(K + "empty"), left + 20, top + 80, 110, RuneArt.INK_FADED, 3);
            return;
        }
        int pages = pages(list.size());
        page = Math.max(0, Math.min(page, pages - 1));
        int start = page * PER_PAGE;
        for (int k = 0; k < PER_PAGE && start + k < list.size(); k++) {
            PhenomenonInfo p = list.get(start + k);
            int x = left + GRID_X + (k % COLS) * SLOT, y = top + GRID_Y + (k / COLS) * SLOT;
            boolean hover = mouseX >= x && mouseY >= y && mouseX < x + SLOT && mouseY < y + SLOT;
            boolean sel = p.id().equals(selected);
            g.blit(TEX, x + 1, y + 1, 330, hover || sel ? 20 : 0, 18, 18, TEX_W, TEX_H);
            boolean known = ClientResearch.isDiscovered(p.id());
            if (known) {
                g.renderItem(icon(p), x + 2, y + 2);
            } else {
                // Silhueta: o ícone desenhado em tinta escura
                g.setColor(0.22F, 0.16F, 0.10F, 1F);
                g.renderItem(icon(p), x + 2, y + 2);
                g.setColor(1F, 1F, 1F, 1F);
            }
            int rc = rarity(p).argb;
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            g.fill(x + 15, y + 2, x + 18, y + 5, rc);
            g.pose().popPose();
        }
        if (pages > 1) {
            int ay = top + 162;
            boolean hl = mouseX >= left + 18 && mouseX < left + 28 && mouseY >= ay && mouseY < ay + 9;
            boolean hr = mouseX >= left + 124 && mouseX < left + 134 && mouseY >= ay && mouseY < ay + 9;
            if (page > 0) g.blit(TEX, left + 18, ay, 360, hl ? 10 : 0, 10, 9, TEX_W, TEX_H);
            if (page < pages - 1) g.blit(TEX, left + 124, ay, 372, hr ? 10 : 0, 10, 9, TEX_W, TEX_H);
            centered(g, Component.translatable(K + "page", page + 1, pages), left + LEFT_CX, ay + 1, RuneArt.INK_SOFT);
        }
    }

    private void renderDetails(GuiGraphics g) {
        PhenomenonInfo p = selected == null ? null : ClientResearch.get(selected);
        if (p == null) {
            int y = wrapped(g, Component.translatable(K + "select"), left + RIGHT_X + 4, top + 50, RIGHT_W - 8, RuneArt.INK_SOFT, 4);
            centered(g, Component.translatable(K + "total", ClientResearch.discoveredCount(), ClientResearch.phenomena().size()),
                    left + RIGHT_CX, y + 16, RuneArt.INK);
            centered(g, Component.translatable(K + "theses_progress", ClientResearch.thesisCount(), Thesis.ALL.size()),
                    left + RIGHT_CX, y + 28, RuneArt.INK);
            return;
        }
        boolean known = ClientResearch.isDiscovered(p.id());
        g.pose().pushPose();
        g.pose().translate(left + RIGHT_CX - 16, top + 12, 0);
        g.pose().scale(2F, 2F, 1F);
        if (!known) g.setColor(0.22F, 0.16F, 0.10F, 1F);
        g.renderItem(icon(p), 0, 0);
        g.setColor(1F, 1F, 1F, 1F);
        g.pose().popPose();

        Component name = known ? Phenomenon.displayName(p.id()).withStyle(ChatFormatting.BOLD)
                : Component.translatable(K + "unknown").withStyle(ChatFormatting.BOLD);
        centered(g, name, left + RIGHT_CX, top + 50, RuneArt.INK);
        centered(g, rarity(p).displayName(), left + RIGHT_CX, top + 61, 0xFFFFFFFF);
        centered(g, Component.translatable(CAT + p.category()), left + RIGHT_CX, top + 71, RuneArt.INK_FADED);
        centered(g, Component.translatable(K + (known ? "observed" : "unobserved")), left + RIGHT_CX, top + 81,
                known ? RuneArt.GOOD : RuneArt.BAD);
        Component desc = Component.translatableWithFallback(Phenomenon.translationKey(p.id()) + ".desc", "");
        wrapped(g, desc, left + RIGHT_X + 2, top + 94, RIGHT_W - 4, RuneArt.INK_SOFT, 4);
        g.drawString(font, Component.translatable(K + "elements"), left + RIGHT_X + 2, top + 132, RuneArt.INK, false);
        List<Element> els = elements(p);
        for (int i = 0; i < els.size() && i < 4; i++) {
            Element e = els.get(i);
            int x = left + RIGHT_X + 2 + (i % 2) * 62, y = top + 142 + (i / 2) * 17;
            RuneArt.rune(g, e, x, y);
            g.drawString(font, e.displayName().getString(), x + 18, y + 4, RuneArt.ink(e), false);
        }
    }

    private void renderThesisList(GuiGraphics g, int mouseX, int mouseY) {
        centered(g, Component.translatable("mage_category.isekai_mahou.theses").withStyle(ChatFormatting.BOLD), left + LEFT_CX, top + 12, RuneArt.INK);
        centered(g, Component.translatable(K + "theses_progress", ClientResearch.thesisCount(), Thesis.ALL.size()),
                left + LEFT_CX, top + 23, RuneArt.INK_SOFT);
        for (int i = 0; i < Thesis.ALL.size(); i++) {
            Thesis t = Thesis.ALL.get(i);
            int x = left + 14, y = top + 38 + i * 13;
            boolean done = ClientResearch.hasThesis(t.id());
            boolean unlocked = ClientResearch.thesisCount() >= t.requiredTheses();
            boolean hover = mouseX >= x - 2 && mouseX < x + 124 && mouseY >= y - 2 && mouseY < y + 11;
            if (i == selectedThesis) g.fill(x - 2, y - 2, x + 124, y + 11, 0x60D6AC4A);
            else if (hover) g.fill(x - 2, y - 2, x + 124, y + 11, 0x30FFFFFF);
            if (done) g.drawString(font, "✔", x, y, RuneArt.GOOD, false);
            else if (!unlocked) RuneArt.lock(g, x, y + 1);
            else RuneArt.smallRune(g, t.entry(), x - 1, y);
            String label = t.numeral() + " " + t.displayName().getString();
            if (font.width(label) > 108) label = font.plainSubstrByWidth(label, 104) + "…";
            g.drawString(font, label, x + 11, y, done ? RuneArt.GOOD : unlocked ? RuneArt.INK : RuneArt.INK_FADED, false);
        }
    }

    private void renderThesisDetails(GuiGraphics g) {
        Thesis t = Thesis.byIndex(selectedThesis);
        if (t == null) {
            wrapped(g, Component.translatable(K + "select"), left + RIGHT_X + 4, top + 60, RIGHT_W - 8, RuneArt.INK_SOFT, 4);
            return;
        }
        int x = left + RIGHT_X + 2;
        centered(g, Component.literal(t.numeral()).withStyle(ChatFormatting.BOLD), left + RIGHT_CX, top + 12, RuneArt.ink(t.entry()));
        int y = wrapped(g, t.displayName().withStyle(ChatFormatting.BOLD), x, top + 24, RIGHT_W - 4, RuneArt.INK, 2);
        y = wrapped(g, t.description(), x, y + 2, RIGHT_W - 4, RuneArt.INK_SOFT, 5);
        // Circuito: entrada → saída
        int cy = Math.max(y + 4, top + 82);
        RuneArt.rune(g, t.entry(), left + RIGHT_CX - 26, cy);
        g.drawString(font, "→", left + RIGHT_CX - 3, cy + 4, RuneArt.INK, false);
        RuneArt.rune(g, t.exit(), left + RIGHT_CX + 10, cy);
        // Requisitos
        int ry = cy + 20;
        for (int i = 0; i < t.reqs().size(); i++) {
            Thesis.Req r = t.reqs().get(i);
            int rx = x + (i % 3) * 41, yy = ry + (i / 3) * 17;
            RuneArt.rune(g, r.element() == null ? Element.ARCANE : r.element(), rx, yy);
            g.drawString(font, "×" + r.count(), rx + 17, yy + 4, RuneArt.INK, false);
        }
        int ly = ry + ((t.reqs().size() + 2) / 3) * 17 + 2;
        g.drawString(font, Component.translatable("screen.isekai_mahou.research_table.thesis.costs", t.paper(), t.shelves()),
                x, ly, RuneArt.INK_SOFT, false);
        g.drawString(font, Component.translatable("screen.isekai_mahou.research_table.reward", t.xp()), x, ly + 10, 0xFF7A3FA0, false);
        Component state;
        int color;
        if (ClientResearch.hasThesis(t.id())) {
            state = Component.translatable("screen.isekai_mahou.research_table.thesis.done");
            color = RuneArt.GOOD;
        } else if (ClientResearch.thesisCount() < t.requiredTheses()) {
            state = Component.translatable("screen.isekai_mahou.research_table.thesis.requires", t.requiredTheses());
            color = RuneArt.BAD;
        } else {
            state = Component.translatable("screen.isekai_mahou.research_table.thesis.available");
            color = RuneArt.GOOD;
        }
        wrapped(g, state, x, ly + 21, RIGHT_W - 4, color, 2);
    }

    private void renderTooltips(GuiGraphics g, int mouseX, int mouseY) {
        for (int i = 0; i < TABS.size(); i++) {
            int x = left + 10 + i * 27, y = top - 20;
            if (mouseX >= x && mouseX < x + 24 && mouseY >= y && mouseY < y + 20) {
                g.renderTooltip(font, Component.translatable(CAT + TABS.get(i)), mouseX, mouseY);
                return;
            }
        }
        if (tab.equals("theses")) return;
        PhenomenonInfo p = entryAt(mouseX, mouseY);
        if (p != null) {
            boolean known = ClientResearch.isDiscovered(p.id());
            List<Component> lines = new ArrayList<>();
            lines.add(known ? Phenomenon.displayName(p.id()) : Component.translatable(K + "unknown"));
            lines.add(rarity(p).displayName());
            lines.add(Component.translatable(K + "notes", rarity(p).notes).withStyle(ChatFormatting.GRAY));
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }

    // ---------------------------------------------------------------- Entrada
    private PhenomenonInfo entryAt(double mx, double my) {
        int gx = left + GRID_X, gy = top + GRID_Y;
        if (mx < gx || my < gy || mx >= gx + COLS * SLOT || my >= gy + ROWS * SLOT) return null;
        int idx = page * PER_PAGE + (int) ((my - gy) / SLOT) * COLS + (int) ((mx - gx) / SLOT);
        List<PhenomenonInfo> list = entries();
        return idx < list.size() ? list.get(idx) : null;
    }

    private void click() {
        if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F, 0.8F));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        for (int i = 0; i < TABS.size(); i++) {
            int x = left + 10 + i * 27, y = top - 20;
            if (mx >= x && mx < x + 24 && my >= y && my < y + 20) {
                if (!TABS.get(i).equals(tab)) {
                    tab = TABS.get(i);
                    lastTab = tab;
                    page = 0;
                    click();
                }
                return true;
            }
        }
        if (tab.equals("theses")) {
            for (int i = 0; i < Thesis.ALL.size(); i++) {
                int x = left + 14, y = top + 38 + i * 13;
                if (mx >= x - 2 && mx < x + 124 && my >= y - 2 && my < y + 11) {
                    selectedThesis = i;
                    click();
                    return true;
                }
            }
            return super.mouseClicked(mx, my, button);
        }
        PhenomenonInfo p = entryAt(mx, my);
        if (p != null) {
            selected = p.id();
            click();
            return true;
        }
        int pages = pages(entries().size());
        int ay = top + 162;
        if (my >= ay && my < ay + 9) {
            if (mx >= left + 18 && mx < left + 28 && page > 0) { page--; click(); return true; }
            if (mx >= left + 124 && mx < left + 134 && page < pages - 1) { page++; click(); return true; }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (!tab.equals("theses") && sy != 0) {
            int pages = pages(entries().size());
            int next = Math.max(0, Math.min(pages - 1, page + (sy > 0 ? -1 : 1)));
            if (next != page) {
                page = next;
                click();
            }
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }
}
