package com.renanpontes.isekaimahou.feature.lore.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.client.menu.MenuTab;
import com.renanpontes.isekaimahou.client.menu.StatusScreen;
import com.renanpontes.isekaimahou.feature.lore.LoreBook;
import com.renanpontes.isekaimahou.feature.lore.LoreData;
import com.renanpontes.isekaimahou.feature.lore.LoreFragments;
import com.renanpontes.isekaimahou.feature.lore.LoreNetwork;
import com.renanpontes.isekaimahou.feature.lore.LoreService;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * Seção <b>Enciclopédia</b> do Guia de Viagem (menu B), ao lado do Diário: à esquerda as categorias (clicar abre/fecha)
 * com as entradas e, no fim, os Fragmentos por coleção; à direita o texto. Num fragmento com lugar revelado aparecem as
 * coordenadas, a distância e os botões "Rastrear" / "Procurar de novo". Nada aqui é obrigatório — é "pra quem quiser
 * saber".
 */
public class LoreTab implements MenuTab {
    public static final String TAB_ID = "lore";
    private static final int ROW_H = 13, HEAD_H = 14;
    private static final int PANEL = 0xC0181220, BORDER = 0xFF6F5A9C, BORDER_SOFT = 0x606F5A9C;
    private static final int TEXT = 0xFFEDE4D0, MUTED = 0xFFA79BB8, FAINT = 0xFF6E6580;
    private static final String FRAG_PREFIX = "set:";

    /** Categorias/coleções abertas e a escolha, lembradas enquanto o jogo está aberto. */
    private static final Set<String> OPEN = new HashSet<>(Set.of("world"));
    /** Escolha: id de entrada ou de fragmento (os dois conjuntos de ids não se cruzam). */
    private static String selected = "world_asteria";

    /** Linha da lista: cabeçalho (group != null, id == null) ou item. */
    private record Row(@Nullable String group, @Nullable String id, Component label, int color, int top, int height, boolean dim) {}

    /** Botão desenhado no painel da direita. */
    private record Chip(int x, int y, int w, int h, int op) {}

    private int x, y, w, h, listW;
    private int listScroll, listMax, textScroll, textMax;
    private List<Row> rows = List.of();
    private int builtVersion = -1;
    private final List<Chip> chips = new ArrayList<>();

    /** Abre o menu B na Enciclopédia, já na entrada ou no fragmento {@code id} (para outros módulos e telas). */
    public static void open(String id) {
        if (id != null && (LoreBook.entry(id) != null || LoreFragments.get(id) != null)) {
            selected = id;
            LoreBook.Entry e = LoreBook.entry(id);
            LoreFragments.Fragment f = LoreFragments.get(id);
            if (e != null) OPEN.add(e.category());
            if (f != null) OPEN.add(FRAG_PREFIX + f.set());
        }
        StatusScreen.open(TAB_ID);
    }

    @Override
    public void init(StatusScreen screen, int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.listW = Mth.clamp(w * 2 / 5, 96, 150);
        builtVersion = -1;
    }

    // =====================================================================================
    //  Lista
    // =====================================================================================
    private void rebuild() {
        CompoundTag d = ClientLore.data();
        List<Row> out = new ArrayList<>();
        int top = 0;
        for (LoreBook.Category c : LoreBook.categories()) {
            boolean open = OPEN.contains(c.id());
            out.add(new Row(c.id(), null, arrow(open).append(c.title()), c.color(), top, HEAD_H, false));
            top += HEAD_H;
            if (!open) continue;
            for (LoreBook.Entry e : c.entries()) {
                boolean known = LoreData.entryOpen(d, e);
                out.add(new Row(c.id(), e.id(), known ? e.title() : Component.literal("? ? ?"), c.color(), top, ROW_H, !known));
                top += ROW_H;
            }
        }
        // Fragmentos: um cabeçalho geral (só texto) e uma coleção por grupo
        out.add(new Row("", null, Component.translatable("screen.isekai_mahou.lore.fragments", LoreData.count(d), LoreFragments.total()),
                MahouUi.GOLD & 0xFFFFFF, top, HEAD_H + 2, false));
        top += HEAD_H + 2;
        for (LoreFragments.LoreSet set : LoreFragments.sets()) {
            String key = FRAG_PREFIX + set.id();
            boolean open = OPEN.contains(key);
            int have = LoreData.countInSet(d, set);
            out.add(new Row(key, null, arrow(open).append(set.title()).append(" " + have + "/" + set.fragments().size()),
                    set.color(), top, HEAD_H, false));
            top += HEAD_H;
            if (!open) continue;
            for (LoreFragments.Fragment f : set.fragments()) {
                boolean known = LoreData.has(d, f.id());
                out.add(new Row(key, f.id(), known ? f.title() : Component.literal(f.index() + ". ? ? ?"), set.color(), top, ROW_H, !known));
                top += ROW_H;
            }
        }
        rows = out;
        listMax = Math.max(0, top - (h - 4));
        listScroll = Mth.clamp(listScroll, 0, listMax);
        builtVersion = ClientLore.version;
    }

    private static MutableComponent arrow(boolean open) {
        return Component.literal(open ? "▾ " : "▸ ");
    }

    @Nullable
    private Row rowAt(double mx, double my) {
        if (mx < x || mx >= x + listW - 4 || my < y + 2 || my >= y + h - 2) return null;
        int ly = (int) my - y - 2 + listScroll;
        for (Row r : rows) if (ly >= r.top() && ly < r.top() + r.height()) return r;
        return null;
    }

    // =====================================================================================
    //  Desenho
    // =====================================================================================
    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        if (builtVersion != ClientLore.version) rebuild();
        Font font = Minecraft.getInstance().font;
        g.fill(x, y, x + listW, y + h, PANEL);
        g.renderOutline(x, y, listW, h, BORDER_SOFT);
        Row hover = rowAt(mx, my);
        g.enableScissor(x + 1, y + 1, x + listW - 1, y + h - 1);
        for (Row r : rows) {
            int ry = y + 2 + r.top() - listScroll;
            if (ry + r.height() < y || ry > y + h) continue;
            boolean header = r.id() == null;
            if (header) {
                if (!r.group().isEmpty()) {
                    g.fill(x + 2, ry, x + listW - 5, ry + r.height() - 1, r == hover ? 0x60443860 : 0x40251C3A);
                    g.fill(x + 2, ry, x + 4, ry + r.height() - 1, 0xFF000000 | r.color());
                }
                g.drawString(font, MahouUi.fit(font, r.label(), listW - 14), x + 7, ry + 3,
                        r.group().isEmpty() ? MahouUi.GOLD : 0xFF000000 | MahouUi.lighten(r.color(), 0.35F), false);
            } else {
                boolean sel = r.id().equals(selected);
                if (sel) g.fill(x + 6, ry, x + listW - 5, ry + r.height(), 0x70FFD86B);
                else if (r == hover) g.fill(x + 6, ry, x + listW - 5, ry + r.height(), 0x30FFFFFF);
                g.drawString(font, MahouUi.fit(font, r.label(), listW - 22), x + 12, ry + 2, r.dim() ? FAINT : sel ? 0xFFFFFFFF : TEXT, false);
            }
        }
        g.disableScissor();
        if (listMax > 0) {
            int vh = h - 4, barH = Math.max(12, vh * vh / (vh + listMax));
            int by = y + 2 + (int) ((vh - barH) * (listScroll / (float) listMax));
            g.fill(x + listW - 4, by, x + listW - 2, by + barH, BORDER);
        }
        renderPage(g, font, mx, my);
    }

    private void renderPage(GuiGraphics g, Font font, int mx, int my) {
        int px = x + listW + 4, pw = w - listW - 4;
        g.fill(px, y, px + pw, y + h, PANEL);
        g.renderOutline(px, y, pw, h, BORDER);
        chips.clear();
        CompoundTag d = ClientLore.data();
        LoreBook.Entry entry = LoreBook.entry(selected);
        LoreFragments.Fragment frag = LoreFragments.get(selected);
        int tx = px + 8, tw = pw - 18, ty = y + 7;

        if (entry != null) {
            LoreBook.Category cat = LoreBook.category(entry.category());
            boolean known = LoreData.entryOpen(d, entry);
            g.renderItem(known ? ClientLore.icon(entry.icon()) : ItemStack.EMPTY, tx, ty - 1);
            g.drawString(font, MahouUi.fit(font, known ? entry.title() : Component.literal("? ? ?"), tw - 20), tx + 20, ty, MahouUi.GOLD, false);
            if (cat != null) g.drawString(font, cat.title(), tx + 20, ty + 10, MUTED, false);
            ty += 24;
            if (!known) {
                LoreFragments.LoreSet set = LoreFragments.setOfTruth(entry.id());
                Component lock = set == null ? Component.translatable("screen.isekai_mahou.lore.locked")
                        : Component.translatable("screen.isekai_mahou.lore.locked_set", set.title(), LoreData.countInSet(d, set), set.fragments().size());
                drawText(g, font, lock, tx, ty, tw, y + h - 6, MUTED);
                return;
            }
            drawText(g, font, Component.translatable(entry.textKey()), tx, ty, tw, y + h - 6, TEXT);
            return;
        }
        if (frag == null) return;
        LoreFragments.LoreSet set = LoreFragments.set(frag.set());
        boolean known = LoreData.has(d, frag.id());
        g.renderItem(ClientLore.pageIcon(), tx, ty - 1);
        g.drawString(font, MahouUi.fit(font, known ? frag.title() : Component.literal("? ? ?"), tw - 20), tx + 20, ty, MahouUi.GOLD, false);
        if (set != null) {
            g.drawString(font, MahouUi.fit(font, Component.translatable("screen.isekai_mahou.lore.page_of", set.title(), frag.index(),
                    set.fragments().size()), tw - 20), tx + 20, ty + 10, 0xFF000000 | MahouUi.lighten(set.color(), 0.3F), false);
        }
        ty += 24;
        if (!known) {
            drawText(g, font, Component.translatable("screen.isekai_mahou.lore.not_found"), tx, ty, tw, y + h - 6, MUTED);
            return;
        }
        // Rodapé com o lugar revelado
        int footH = 40, footY = y + h - footH - 4;
        drawText(g, font, Component.translatable(frag.textKey()), tx, ty, tw, footY - 4, TEXT);
        g.fill(px + 6, footY - 2, px + pw - 6, footY - 1, BORDER_SOFT);
        LoreData.Spot spot = LoreData.spot(d, frag.id());
        if (spot == null) {
            g.drawString(font, MahouUi.fit(font, Component.translatable("screen.isekai_mahou.lore.no_place"), tw), tx, footY + 2, MUTED, false);
            chip(g, font, tx, footY + 24, Component.translatable("screen.isekai_mahou.lore.search_again"), LoreNetwork.RELOCATE, mx, my);
            return;
        }
        boolean visited = spot.state() == LoreData.VISITED;
        g.drawString(font, MahouUi.fit(font, Component.translatable(visited ? "screen.isekai_mahou.lore.place_visited"
                : "screen.isekai_mahou.lore.place_revealed", frag.placeName()), tw), tx, footY + 2, visited ? 0xFF86E08A : MahouUi.GOLD_PALE, false);
        MutableComponent coords = Component.translatable("screen.isekai_mahou.lore.coords", spot.pos().getX(), spot.pos().getZ());
        Component dist = ClientLore.distanceText(spot);
        coords.append("  ·  ").append(dist != null ? dist : LoreService.dimName(spot.dim().location().getPath()));
        g.drawString(font, MahouUi.fit(font, coords, tw), tx, footY + 13, MUTED, false);
        if (visited) return;
        boolean tracking = frag.id().equals(LoreData.tracked(d));
        int cx = chip(g, font, tx, footY + 24, Component.translatable(tracking ? "screen.isekai_mahou.lore.untrack" : "screen.isekai_mahou.lore.track"),
                tracking ? LoreNetwork.UNTRACK : LoreNetwork.TRACK, mx, my);
        if (frag.kind() == LoreFragments.Kind.STRUCTURE) {
            chip(g, font, cx + 4, footY + 24, Component.translatable("screen.isekai_mahou.lore.search_again"), LoreNetwork.RELOCATE, mx, my);
        }
    }

    /** Desenha um botão e o registra para o clique; devolve o X do fim. */
    private int chip(GuiGraphics g, Font font, int cx, int cy, Component label, int op, int mx, int my) {
        int cw = font.width(label) + 12, ch = 13;
        boolean hov = mx >= cx && mx < cx + cw && my >= cy && my < cy + ch;
        MahouUi.roundRect(g, cx, cy, cw, ch, hov ? 0xFF4E3C14 : 0xFF2A2440, hov ? 0xFF2C2210 : 0xFF14101E);
        MahouUi.roundOutline(g, cx, cy, cw, ch, hov ? MahouUi.GOLD : MahouUi.GOLD_DIM);
        g.drawString(font, label, cx + 6, cy + 3, hov ? MahouUi.GOLD_PALE : TEXT, false);
        chips.add(new Chip(cx, cy, cw, ch, op));
        return cx + cw;
    }

    /** Texto quebrado com rolagem, recortado entre {@code top} e {@code bottom}. */
    private void drawText(GuiGraphics g, Font font, Component text, int tx, int top, int tw, int bottom, int color) {
        List<FormattedCharSequence> lines = font.split(text, tw);
        int vh = bottom - top;
        textMax = Math.max(0, lines.size() * 10 - vh);
        textScroll = Mth.clamp(textScroll, 0, textMax);
        g.enableScissor(tx - 2, top, tx + tw + 8, bottom);
        for (int i = 0; i < lines.size(); i++) {
            int ly = top + i * 10 - textScroll;
            if (ly > top - 10 && ly < bottom) g.drawString(font, lines.get(i), tx, ly, color, false);
        }
        g.disableScissor();
        if (textMax > 0) {
            int barH = Math.max(12, vh * vh / (lines.size() * 10));
            int by = top + (int) ((vh - barH) * (textScroll / (float) textMax));
            g.fill(tx + tw + 5, top, tx + tw + 7, bottom, 0x40000000);
            g.fill(tx + tw + 5, by, tx + tw + 7, by + barH, BORDER);
        }
    }

    // =====================================================================================
    //  Entrada
    // =====================================================================================
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;
        for (Chip c : chips) {
            if (mx >= c.x() && mx < c.x() + c.w() && my >= c.y() && my < c.y() + c.h()) {
                ClientLore.send(c.op(), selected);
                click(1.0F);
                return true;
            }
        }
        Row r = rowAt(mx, my);
        if (r == null) return false;
        if (r.id() == null) {
            if (r.group().isEmpty()) return true;
            if (!OPEN.remove(r.group())) OPEN.add(r.group());
            builtVersion = -1;
            click(1.2F);
            return true;
        }
        if (!r.id().equals(selected)) {
            selected = r.id();
            textScroll = 0;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.2F, 0.6F));
        }
        return true;
    }

    private static void click(float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), pitch, 0.35F));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (scrollY == 0 || my < y || my >= y + h) return false;
        int step = (int) Math.signum(scrollY) * 20;
        if (mx >= x && mx < x + listW) {
            listScroll = Mth.clamp(listScroll - step, 0, listMax);
            return true;
        }
        if (mx >= x + listW && mx < x + w) {
            textScroll = Mth.clamp(textScroll - step, 0, textMax);
            return true;
        }
        return false;
    }
}
