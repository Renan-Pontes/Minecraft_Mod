package com.renanpontes.isekaimahou.feature.ui_grimoire.client;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.renanpontes.isekaimahou.client.CoreKeys;
import com.renanpontes.isekaimahou.client.GlyphArt;
import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.client.menu.MenuTab;
import com.renanpontes.isekaimahou.client.menu.StatusScreen;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphKind;
import com.renanpontes.isekaimahou.glyph.GlyphMastery;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.TriggerGlyph;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * Aba "Glifos": coleção de TODOS os glifos registrados, agrupados por elemento, com os ícones de verdade.
 * Conhecidos coloridos, desconhecidos em silhueta com "?". Filtros por elemento (lista com runas), Origem por
 * CATEGORIA de classe (Mágicas / Físicas / Destreza), tipo (ícones), tier e busca; painel de detalhes à direita com
 * o ícone em 2×. Glifos de origem de OUTRA classe levam o selo de pergaminho (só se aprendem por Lição).
 */
public class GlyphsTab implements MenuTab {
    private static final String K = "screen.isekai_mahou.glyphs.";
    private static final int CELL = 20;
    private static final int ICON = GlyphIcons.SIZE;
    private static final int HEADER = 14;
    private static final int KIND_W = 18;
    /** Nomes dos tipos (as mesmas traduções das abas de tipo do Grimório). */
    private static final String KIND_FILTER = "screen.isekai_mahou.grimoire.filter.";
    /**
     * Filtro de elemento: -1 todos, 0..N-1 Element.values(), N = toda a Magia de Origem, N+1.. = Origem de uma
     * categoria de classe ({@link MagicPath.Category#values()} na ordem).
     */
    private static int elementFilter = -1;
    private static GlyphKind kindFilter;
    private static int tierFilter;
    private static String search = "";
    private static ResourceLocation detail;
    private static double scrollMemory;

    /** Seção da grade: elemento (ou classe de Origem), com os glifos e quantos são conhecidos. */
    private record Section(Component title, int color, Element element, MagicPath origin, List<Glyph> glyphs, int known) {}

    private StatusScreen screen;
    private int x, y, w, h;
    private int gridW, cols, detailX, detailW;
    private int elemX, elemW, kindsX;
    private EditBox searchBox;
    private final ScrollState scroll = new ScrollState();
    private final HotZones zones = new HotZones();
    private final Picker picker = new Picker();
    private List<Section> sections = List.of();
    private String stamp = "";
    private int totalKnown, total;
    /** Painel estreito: a contagem conhecidos/total é desenhada no rodapé do painel de detalhes. */
    private boolean countInDetail;

    private static MutableComponent tr(String key, Object... args) {
        return Component.translatable(K + key, args);
    }

    @Override
    public void init(StatusScreen screen, int x, int y, int w, int h) {
        this.screen = screen;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        Font font = Gfx.font();
        detailW = Mth.clamp(w * 32 / 100, 118, 160);
        detailX = x + w - detailW;
        gridW = w - detailW - 6;
        cols = Math.max(1, (gridW - 6) / CELL);

        int kindsW = 6 * KIND_W;
        elemX = x;
        elemW = Mth.clamp(gridW - kindsW - 70, 60, 150);
        kindsX = elemX + elemW + 4;
        int searchX = kindsX + kindsW + 4;
        searchBox = new EditBox(font, searchX, y + 1, x + w - searchX - 1, 13, tr("search"));
        searchBox.setHint(tr("search").withStyle(ChatFormatting.DARK_GRAY));
        searchBox.setMaxLength(40);
        searchBox.setValue(search);
        searchBox.setResponder(s -> {
            search = s;
            stamp = "";
        });
        screen.addTabWidget(searchBox);
        scroll.bounds(x, y + 34, gridW, h - 34);
        rebuild(ClientMagic.data());
        scroll.set(scrollMemory);
    }

    // =====================================================================================
    //  Dados
    // =====================================================================================
    /** Índice do filtro "toda a Origem". */
    private static int originFilter() {
        return Element.values().length;
    }

    /** Categoria do filtro (Origem de Mágicas/Físicas/Destreza), ou null. */
    private static MagicPath.Category categoryOf(int filter) {
        int i = filter - originFilter() - 1;
        MagicPath.Category[] cats = MagicPath.Category.values();
        return i >= 0 && i < cats.length ? cats[i] : null;
    }

    private static Component filterName(int f) {
        if (f < 0) return tr("element.all");
        if (f == originFilter()) return tr("element.origin");
        MagicPath.Category cat = categoryOf(f);
        if (cat != null) return tr("element.origin_cat", cat.displayName());
        return Component.literal(Element.values()[f].displayName().getString());
    }

    private static Component elementFilterName() {
        return filterName(elementFilter);
    }

    private static int filterColor(MagicData d, int f) {
        if (f < 0) return Gfx.FRAME_LIGHT;
        if (f == originFilter()) return d.path() == MagicPath.NONE ? 0xFFE0A85C : d.path().argb();
        MagicPath.Category cat = categoryOf(f);
        if (cat != null) return GlyphIcons.categoryColor(cat);
        return Element.values()[f].argb();
    }

    private static int elementFilterColor(MagicData d) {
        return filterColor(d, elementFilter);
    }

    private static void elementFilterIcon(GuiGraphics g, MagicData d, int filter, int ix, int iy, int s) {
        MagicPath.Category cat = categoryOf(filter);
        if (filter < 0) GlyphArt.tab(g, "glyphs", ix, iy, s);
        else if (filter == originFilter()) GlyphIcons.originIcon(g, d.path(), ix, iy, s);
        else if (cat != null) GlyphIcons.categoryIcon(g, cat, ix, iy, s);
        else GlyphArt.element(g, Element.values()[filter], ix, iy, s);
    }

    private void setElementFilter(int f) {
        elementFilter = f;
        stamp = "";
        Gfx.sound(SoundEvents.UI_BUTTON_CLICK, 1.2F, 0.25F);
    }

    private void cycleElement(int dir) {
        int n = Element.values().length + 2 + MagicPath.Category.values().length; // + "todos" + Origem + categorias
        setElementFilter(Math.floorMod(elementFilter + 1 + dir, n) - 1);
    }

    private boolean passes(Glyph gl) {
        if (kindFilter != null && gl.kind() != kindFilter) return false;
        if (tierFilter > 0 && gl.tier() != tierFilter) return false;
        String q = Gfx.norm(search.trim());
        return q.isEmpty() || GrimoireTab.matches(gl, q);
    }

    private void rebuild(MagicData d) {
        String lang = Minecraft.getInstance().getLanguageManager().getSelected();
        String st = ClientMagic.syncVersion + "|" + d.glyphs().size() + "|" + elementFilter + "|" + kindFilter + "|" + tierFilter + "|" + search + "|" + lang;
        if (st.equals(stamp)) return;
        stamp = st;
        List<Section> out = new ArrayList<>();
        Element[] els = Element.values();
        boolean all = elementFilter < 0;
        boolean origin = elementFilter >= els.length;
        MagicPath.Category cat = categoryOf(elementFilter);
        if (!origin) {
            for (int i = 0; i < els.length; i++) {
                if (!all && i != elementFilter) continue;
                Element e = els[i];
                List<Glyph> list = new ArrayList<>();
                for (Glyph gl : GlyphRegistry.byElement(e)) if (gl.origin() == null && passes(gl) && GlyphIcons.listed(gl, d)) list.add(gl);
                addSection(out, d, Component.literal(e.displayName().getString()), e.argb(), e, null, list);
            }
        }
        if (all || origin) {
            // a própria classe primeiro; depois as outras (técnicas que só se aprendem por Lição), na ordem da tela
            List<MagicPath> order = new ArrayList<>();
            if (d.path() != MagicPath.NONE) order.add(d.path());
            for (MagicPath p : MagicPath.values()) if (!order.contains(p)) order.add(p);
            for (MagicPath p : order) {
                if (cat != null && p.category() != cat) continue;
                List<Glyph> list = new ArrayList<>();
                for (Glyph gl : GlyphRegistry.all()) if (gl.origin() == p && passes(gl) && GlyphIcons.listed(gl, d)) list.add(gl);
                boolean foreign = p != d.path() && p != MagicPath.NONE;
                Component title = foreign ? tr("section.origin_lesson", p.displayName()) : tr("section.origin", p.displayName());
                addSection(out, d, title, p.argb(), null, p, list);
            }
        }
        sections = out;
        totalKnown = 0;
        total = 0;
        for (Glyph gl : GlyphRegistry.all()) {
            if (!GlyphIcons.listed(gl, d)) continue;
            total++;
            if (d.knowsGlyph(gl.id())) totalKnown++;
        }
        scroll.content(contentHeight());
    }

    private static void addSection(List<Section> out, MagicData d, Component title, int color, Element e, MagicPath p, List<Glyph> list) {
        if (list.isEmpty()) return;
        list.sort(GlyphIcons.ORDER);
        int known = 0;
        for (Glyph gl : list) if (d.knowsGlyph(gl.id())) known++;
        out.add(new Section(title, color, e, p, list, known));
    }

    private int contentHeight() {
        int hgt = 0;
        for (Section s : sections) hgt += HEADER + (s.glyphs().size() + cols - 1) / cols * CELL + 4;
        return hgt;
    }

    // =====================================================================================
    //  Desenho
    // =====================================================================================
    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        zones.clear();
        MagicData d = ClientMagic.data();
        rebuild(d);
        renderFilters(g, d, mx, my);
        Glyph hovered = renderGrid(g, d, mx, my);
        Glyph shown = hovered != null ? hovered : detail == null ? null : GlyphRegistry.get(detail);
        renderDetail(g, d, shown);
        scrollMemory = scroll.offset();
        UiToast.render(g, x + gridW / 2, y + h - 4, gridW);
        picker.render(g, zones, mx, my);
        searchBox.visible = !picker.intersects(searchBox.getX(), searchBox.getY(), searchBox.getWidth(), searchBox.getHeight());
        zones.renderTooltip(g, mx, my);
    }

    private void renderFilters(GuiGraphics g, MagicData d, int mx, int my) {
        Font font = Gfx.font();
        // ---- elemento: botão com runa + nome que abre a lista
        int ec = elementFilterColor(d);
        boolean eh = Gfx.in(mx, my, elemX, y, elemW, 15);
        Gfx.toggle(g, elemX, y, elemW, 15, elementFilter >= 0, eh, ec);
        elementFilterIcon(g, d, elementFilter, elemX + 3, y + 1, 13);
        Component name = elementFilterName();
        int room = elemW - 3 - 13 - 4 - 10;
        if (font.width(name) > room && elementFilter < 0) name = tr("element.all_short");
        if (font.width(name) <= room) g.drawString(font, name, elemX + 20, y + 4, 0xFFFFFFFF, false);
        g.drawString(font, "▾", elemX + elemW - 8, y + 4, eh ? 0xFFFFFFFF : Gfx.TEXT_DIM, false);
        zones.add(elemX, y, elemW, 15, b -> {
            if (b == 1) cycleElement(-1);
            else openElementPicker(d);
        }, () -> List.of(tr("element.current", elementFilterName()).withStyle(ChatFormatting.BOLD), tr("element.tip").withStyle(ChatFormatting.GRAY)));

        // ---- tipos (ícone; nome na dica)
        for (int i = 0; i < 6; i++) {
            GlyphKind kind = i == 0 ? null : GlyphIcons.KIND_ORDER[i - 1];
            boolean trig = kind == GlyphKind.TRIGGER;
            int bx = kindsX + i * KIND_W;
            boolean on = kindFilter == kind;
            boolean hov = Gfx.in(mx, my, bx, y, KIND_W - 1, 15);
            int accent = kind == null ? Gfx.FRAME_LIGHT : GlyphArt.kindColor(kind, trig);
            Gfx.toggle(g, bx, y, KIND_W - 1, 15, on, hov, accent);
            if (kind == null) {
                if (!on && !hov) g.setColor(0.7F, 0.7F, 0.75F, 1F);
                GlyphArt.tab(g, "glyphs", bx + 2, y + 1, 13);
                g.setColor(1F, 1F, 1F, 1F);
            } else {
                GlyphIcons.kindIcon(g, kind, trig, bx + 2, y + 1, 13, on || hov);
            }
            zones.add(bx, y, KIND_W - 1, 15, b -> {
                kindFilter = kind;
                stamp = "";
                Gfx.sound(SoundEvents.UI_BUTTON_CLICK, 1.2F, 0.25F);
            }, () -> List.of(kind == null ? Component.translatable("screen.isekai_mahou.grimoire.filter.all").withStyle(ChatFormatting.BOLD)
                    : Component.translatable(KIND_FILTER + kindKey(kind)).withColor(accent & 0xFFFFFF).withStyle(ChatFormatting.BOLD),
                    tr("kind.tip").withStyle(ChatFormatting.DARK_GRAY)));
        }

        // ---- tiers
        int ty = y + 18;
        int tx = x;
        for (int t = 0; t <= 5; t++) {
            Component label = t == 0 ? tr("tier.all") : tr("tier.short", t);
            int tw = font.width(label) + 8;
            boolean on = tierFilter == t;
            boolean hov = Gfx.in(mx, my, tx, ty, tw, 12);
            int accent = t == 5 ? Gfx.GOLD : 0xFF8A6FD0;
            Gfx.toggle(g, tx, ty, tw, 12, on, hov, accent);
            g.drawString(font, label, tx + 4, ty + 2, on ? 0xFFFFFFFF : Gfx.TEXT_DIM, false);
            final int ft = t;
            zones.add(tx, ty, tw, 12, b -> {
                tierFilter = ft;
                stamp = "";
                Gfx.sound(SoundEvents.UI_BUTTON_CLICK, 1.2F, 0.25F);
            }, () -> List.of(ft == 0 ? tr("tier.all") : tr("tier", ft).append(" — ").append(GlyphIcons.nodeName(ft)),
                    tr("tier.tip").withStyle(ChatFormatting.DARK_GRAY)));
            tx += tw + 2;
        }
        // ---- contagem (se nem a forma curta cabe ao lado dos tiers, vai para o rodapé do painel de detalhes)
        Component count = tr("known", totalKnown, total);
        if (x + gridW - font.width(count) < tx + 4) count = Component.literal(totalKnown + "/" + total);
        int cw = font.width(count);
        int cx = x + gridW - cw;
        countInDetail = cx < tx + 2;
        if (countInDetail) return;
        g.drawString(font, count, cx, ty + 2, Gfx.GOLD, false);
        int barX = tx + 4;
        int barW = cx - 6 - barX;
        if (barW > 20) MahouUi.bar(g, barX, ty + 4, barW, 4, total == 0 ? 0 : (float) totalKnown / total, 0xFFB98CFF);
    }

    private static String kindKey(GlyphKind kind) {
        return switch (kind) {
            case FORM -> "form";
            case EFFECT -> "effect";
            case AUGMENT -> "augment";
            case TRIGGER -> "trigger";
            case LAW -> "law";
        };
    }

    private void openElementPicker(MagicData d) {
        List<Picker.Option> opts = new ArrayList<>();
        int n = Element.values().length;
        int last = n + MagicPath.Category.values().length;
        for (int f = -1; f <= last; f++) {
            final int ff = f;
            Component label = filterName(f);
            int color = filterColor(d, f);
            boolean awake = f < 0 || f >= n || d.isAwakened(Element.values()[f]);
            opts.add(new Picker.Option((g, ix, iy, s) -> elementFilterIcon(g, d, ff, ix, iy, s), label, f < 0 ? tr("element.all_short") : null,
                    color, true, f == elementFilter,
                    () -> setElementFilter(ff), () -> {
                        List<Component> t = new ArrayList<>();
                        t.add(label.copy().withStyle(ChatFormatting.BOLD));
                        if (!awake) t.add(tr("element.not_awakened").withStyle(ChatFormatting.DARK_GRAY));
                        if (ff >= n) t.add(tr("element.origin.tip").withStyle(ChatFormatting.GRAY));
                        return t;
                    }));
        }
        picker.toggle(elemX, y, elemW, 15, false, x, x + w, y + 16, y + h, tr("element.pick"), opts);
    }

    /** Desenha a grade e devolve o glifo sob o mouse (ou null). */
    private Glyph renderGrid(GuiGraphics g, MagicData d, int mx, int my) {
        Font font = Gfx.font();
        Glyph hovered = null;
        int off = scroll.offset();
        int cy = scroll.y - off;
        int gx = x + 2 + Math.max(0, (gridW - 6 - cols * CELL) / 2);
        g.enableScissor(scroll.x, scroll.y, scroll.x + scroll.w, scroll.y + scroll.h);
        for (Section s : sections) {
            int rows = (s.glyphs().size() + cols - 1) / cols;
            int secH = HEADER + rows * CELL + 4;
            if (cy + secH >= scroll.y && cy <= scroll.y + scroll.h) {
                // Cabeçalho da seção: runa (ou símbolo da classe) + nome + conhecidos
                if (s.element() != null) GlyphArt.element(g, s.element(), x + 1, cy + 1, 11);
                else GlyphIcons.originIcon(g, s.origin(), x + 1, cy + 1, 11);
                Component title = s.title().copy().withStyle(ChatFormatting.BOLD);
                g.drawString(font, title, x + 15, cy + 3, Gfx.lighten(s.color(), 0.25F), false);
                String cnt = s.known() + "/" + s.glyphs().size();
                int tw = font.width(title);
                g.drawString(font, cnt, x + 15 + tw + 6, cy + 3, s.known() == s.glyphs().size() ? Gfx.GOLD : Gfx.TEXT_DIM, false);
                int lineX = x + 15 + tw + 10 + font.width(cnt);
                if (lineX < x + gridW - 6) g.fill(lineX, cy + 7, x + gridW - 6, cy + 8, Gfx.alpha(s.color(), 0x60));
                for (int i = 0; i < s.glyphs().size(); i++) {
                    Glyph gl = s.glyphs().get(i);
                    int px = gx + (i % cols) * CELL + 1;
                    int py = cy + HEADER + (i / cols) * CELL + 1;
                    if (py + ICON < scroll.y || py > scroll.y + scroll.h) continue;
                    boolean known = d.knowsGlyph(gl.id());
                    boolean hov = Gfx.in(mx, my, px - 1, py - 1, ICON + 2, ICON + 2) && scroll.over(mx, my) && !picker.isOpen();
                    if (hov) hovered = gl;
                    int flags = (known ? 0 : GlyphIcons.UNKNOWN) | (hov ? GlyphIcons.HOVER : 0)
                            | (gl.id().equals(detail) ? GlyphIcons.SELECTED : 0)
                            | (GlyphIcons.isLesson(gl, d) ? GlyphIcons.LESSON : 0)
                            | (known && d.circle() < gl.minCircle() ? GlyphIcons.LOCKED : 0);
                    GlyphIcons.draw(g, gl, px, py, ICON, flags);
                    // Maestria: barrinha fina embaixo do ícone (caminho I→X); só aparece depois do primeiro uso real
                    GlyphIcons.masteryBar(g, d, gl, px, py, ICON);
                    zones.addClipped(px - 1, py - 1, ICON + 2, ICON + 2, scroll.x, scroll.y, scroll.w, scroll.h,
                            b -> clickGlyph(d, gl), () -> {
                                List<Component> lines = GlyphIcons.tooltip(gl, d, true);
                                if (d.knowsGlyph(gl.id())) lines.add(tr("click_hint").withStyle(ChatFormatting.DARK_GRAY));
                                return lines;
                            });
                }
            }
            cy += secH;
        }
        g.disableScissor();
        scroll.renderBar(g);
        if (sections.isEmpty()) {
            Component empty = tr("empty");
            g.drawString(font, empty, x + (gridW - font.width(empty)) / 2, scroll.y + 12, Gfx.TEXT_FAINT, false);
        }
        return hovered;
    }

    private void clickGlyph(MagicData d, Glyph gl) {
        detail = gl.id();
        if (Screen.hasShiftDown() && d.knowsGlyph(gl.id())) {
            if (gl instanceof TriggerGlyph t && !t.isPlaceable() && !t.isLink()) {
                UiToast.error(Component.translatable("spell_error.isekai_mahou.trigger_not_placeable", gl.displayName()));
            } else if (GrimoireDraft.add(gl.id())) {
                UiToast.ok(tr("added", gl.displayName()));
                Gfx.sound(SoundEvents.BOOK_PAGE_TURN, 1.3F, 0.7F);
            } else {
                UiToast.error(Component.translatable("screen.isekai_mahou.grimoire.sequence.full", GrimoireDraft.MAX_EDIT));
            }
            return;
        }
        Gfx.sound(SoundEvents.UI_BUTTON_CLICK, 1.5F, 0.2F);
    }

    private void renderDetail(GuiGraphics g, MagicData d, Glyph gl) {
        Font font = Gfx.font();
        int px = detailX, py = y + 18, pw = detailW, ph = h - 18;
        Gfx.box(g, px, py, pw, ph, Gfx.FRAME);
        int textBottom = py + ph - 3;
        if (countInDetail) {
            Component count = tr("known", totalKnown, total);
            g.fill(px + 3, py + ph - 13, px + pw - 3, py + ph - 12, Gfx.FRAME);
            g.drawString(font, Gfx.fit(font, count, pw - 8), px + 5, py + ph - 10, Gfx.GOLD, false);
            textBottom -= 12;
        }
        if (gl == null) {
            int ty = py + 8;
            for (FormattedCharSequence line : font.split(tr("detail.hint"), pw - 12)) {
                g.drawString(font, line, px + 6, ty, Gfx.TEXT_FAINT, false);
                ty += 10;
            }
            return;
        }
        boolean known = d.knowsGlyph(gl.id());
        int flags = (known ? 0 : GlyphIcons.UNKNOWN) | (known && d.circle() < gl.minCircle() ? GlyphIcons.LOCKED : 0)
                | (GlyphIcons.isLesson(gl, d) ? GlyphIcons.LESSON : 0);
        // brilho atrás do ícone grande (2× = 32px)
        int base = GlyphIcons.baseColor(gl);
        Gfx.circle(g, px + 22, py + 22, 20, Gfx.alpha(known ? base : 0x808080, 0x30));
        GlyphIcons.draw(g, gl, px + 6, py + 6, ICON * 2, flags);
        int tx = px + 44;
        int tw = pw - 48;
        int ty = py + 6;
        // nome inteiro (quebra em linhas, nunca corta)
        List<FormattedCharSequence> nameLines = font.split(gl.displayName().withStyle(ChatFormatting.BOLD), tw);
        for (FormattedCharSequence line : nameLines) {
            g.drawString(font, line, tx, ty, Gfx.lighten(base, 0.35F), false);
            ty += 9;
        }
        ty = Math.max(ty + 1, py + 16);
        GlyphIcons.kindIcon(g, gl.kind(), GlyphIcons.isLink(gl), tx, ty, 9, true);
        Component kind = GlyphIcons.kindName(gl);
        List<FormattedCharSequence> kl = font.split(kind, tw - 11);
        g.drawString(font, kl.isEmpty() ? FormattedCharSequence.EMPTY : kl.get(0), tx + 11, ty + 1, GlyphIcons.kindColor(gl), false);
        ty += 11;
        Component status = known ? tr("status.known") : tr("status.unknown");
        g.drawString(font, status, tx, ty, known ? Gfx.GOOD : Gfx.TEXT_FAINT, false);
        ty += 10;
        // Maestria do glifo: nível + barra do nível atual (os números e bônus ficam nas linhas abaixo)
        int masteryBarY = -1;
        if (known) {
            int mxp = d.glyphXp(gl.id());
            int mlv = GlyphMastery.level(mxp);
            int mcol = GlyphIcons.masteryColor(mlv);
            masteryBarY = Math.max(ty + 2, py + 42);
            g.drawString(font, Component.translatable("screen.isekai_mahou.glyph.mastery", GlyphMastery.roman(mlv)), px + 5, masteryBarY, mcol, false);
            int bw = pw - 10;
            MahouUi.bar(g, px + 5, masteryBarY + 10, bw, 4, GlyphMastery.progress(mxp), mcol);
        }

        List<Component> lines = new ArrayList<>();
        lines.add(gl.origin() != null ? Component.translatable("screen.isekai_mahou.glyph.origin_tag", gl.origin().displayName())
                : tr("detail.element", gl.element().displayName()));
        if (GlyphIcons.isLesson(gl, d)) lines.add(GlyphIcons.lessonLine(gl, d).withColor(0xE7A6FF));
        MutableComponent tier = GlyphIcons.tierLabel(gl);
        if (gl.isLegendary()) tier.append(" ").append(Component.translatable("screen.isekai_mahou.glyph.legendary").withColor(Gfx.GOLD & 0xFFFFFF));
        lines.add(tier.withStyle(ChatFormatting.GRAY));
        int cost = gl.cost();
        lines.add(Component.translatable("screen.isekai_mahou.glyph.cost", cost > 0 ? "+" + cost : String.valueOf(cost)).withColor(Gfx.MANA & 0xFFFFFF));
        lines.add(Component.translatable("screen.isekai_mahou.glyph.min_circle", gl.minCircle())
                .withStyle(d.circle() >= gl.minCircle() ? ChatFormatting.GRAY : ChatFormatting.RED));
        lines.addAll(GlyphIcons.details(gl));
        lines.add(Component.empty());
        lines.add(gl.description().withStyle(ChatFormatting.ITALIC).withColor(0xBFE6FF));
        if (known) {
            // números da maestria (a barra já foi desenhada em cima): progresso, bônus e como evoluir
            int mxp = d.glyphXp(gl.id());
            int mlv = GlyphMastery.level(mxp);
            lines.add(Component.empty());
            // maestria infinita: só o teto do número salvo é "máxima"; depois do X o texto fica dourado
            MutableComponent prog = GlyphIcons.masteryProgressText(mxp);
            if (mlv >= GlyphMastery.LEVEL_CAP) prog.withColor(Gfx.GOLD & 0xFFFFFF);
            else prog.withStyle(mlv >= GlyphMastery.MAX_LEVEL ? ChatFormatting.GOLD : ChatFormatting.GRAY);
            lines.add(prog);
            MutableComponent bonus = GlyphIcons.masteryBonus(gl, mlv);
            if (bonus != null) lines.add(bonus.withColor(Gfx.GOOD & 0xFFFFFF));
            if (mlv < GlyphMastery.MAX_LEVEL) {
                lines.add(Component.translatable("screen.isekai_mahou.glyph.mastery.how").withStyle(ChatFormatting.DARK_GRAY));
            } else {
                lines.add(Component.translatable("screen.isekai_mahou.glyph.mastery.how_infinite").withStyle(ChatFormatting.DARK_GRAY));
            }
            // mutações despertas (com descrição) + a próxima como mistério
            List<Component> muts = GlyphIcons.mutationLines(gl, mlv, true);
            if (!muts.isEmpty()) {
                lines.add(Component.empty());
                lines.addAll(muts);
            }
        }
        if (!known) {
            lines.add(Component.empty());
            lines.add(tr("detail.how").withStyle(ChatFormatting.GOLD));
            lines.add(GlyphIcons.source(gl).copy().withStyle(ChatFormatting.GRAY));
        }
        int ly = masteryBarY >= 0 ? masteryBarY + 18 : Math.max(ty + 2, py + 42);
        for (Component c : lines) {
            if (c.getString().isEmpty()) {
                ly += 4;
                continue;
            }
            for (FormattedCharSequence line : font.split(c, pw - 10)) {
                if (ly + 9 > textBottom) {
                    // não cabe: o resto fica na dica do painel
                    zones.add(px, py, pw, ph, null, () -> GlyphIcons.tooltip(gl, d, true));
                    return;
                }
                g.drawString(font, line, px + 5, ly, Gfx.TEXT, false);
                ly += 9;
            }
        }
    }

    // =====================================================================================
    //  Entrada
    // =====================================================================================
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        screen.setFocused(null);
        if (picker.isOpen()) {
            if (picker.contains(mx, my)) zones.click(mx, my, button);
            else picker.close();
            return true;
        }
        if (scroll.clickBar(mx, my)) return true;
        return zones.click(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return scroll.drag(my);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        return scroll.release();
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (picker.isOpen() && key == GLFW.GLFW_KEY_ESCAPE) {
            picker.close();
            return true;
        }
        // Fecha com a tecla do menu mesmo com um botão de aba focado (abertura pelo teclado); nunca ao digitar na busca
        if (!searchBox.isFocused() && CoreKeys.MENU.matches(key, scan)) {
            screen.onClose();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (picker.isOpen()) return true;
        if (Gfx.in(mx, my, elemX, y, elemW, 15)) {
            cycleElement(sy > 0 ? -1 : 1);
            return true;
        }
        return scroll.scroll(mx, my, sy, CELL);
    }

    @Override
    public void removed() {
        scrollMemory = scroll.offset();
    }
}
