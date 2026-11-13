package com.renanpontes.isekaimahou.feature.ui_grimoire.client;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.client.GlyphArt;
import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphKind;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.LinkGlyph;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.SpellCost;
import com.renanpontes.isekaimahou.glyph.SpellParser;
import com.renanpontes.isekaimahou.glyph.SpellStats;
import com.renanpontes.isekaimahou.glyph.TransformLawGlyph;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/**
 * Vista de PROGRAMA do Grimório: cada estágio da magia numa linha (medalhão com o número + forma e efeitos com
 * seus aumentos agrupados), as ligações desenhadas como conectores com rótulo ("Ao Acertar ➜") entre as linhas,
 * Saltar como um laço no fim e as Leis numa linha própria. Funciona com a receita incompleta (estrutura pela
 * posição das ligações) e destaca o(s) glifo(s) culpado(s) pelo erro do parser.
 */
final class ProgramView {
    static final int ICON = GlyphIcons.SIZE;
    private static final int PITCH = ICON + 2;   // passo entre chips do mesmo grupo
    private static final int GROUP_GAP = 6;      // espaço entre grupos
    private static final int ROW_H = 21;         // linha de chips
    private static final int LINK_H = 20;        // linha de ligação
    private static final int MED = 13;           // medalhão do estágio
    private static final int TOP = 4;
    private static final int LINK_COLOR = GlyphArt.kindColor(GlyphKind.TRIGGER, true);
    private static final String K = "screen.isekai_mahou.grimoire.";

    enum Kind { STAGE, PLACEHOLDER, LINK, LEAP, LAWS }

    /** Uma linha do desenho; {@code y} é coordenada de conteúdo (sem rolagem). */
    private record Line(Kind kind, int y, int h, int stage, int link) {
        int medY() {
            return y + 2 + (ICON - MED) / 2;
        }
    }

    // caixa na tela
    private int bx, by, bw, bh;
    final ScrollState scroll = new ScrollState();

    // layout
    private int[] px = new int[0], py = new int[0];
    private final List<Line> lines = new ArrayList<>();
    /** Halos dos grupos: x0, y0, x1, y1, índice da cabeça. */
    private final List<int[]> halos = new ArrayList<>();
    /** Pontos de inserção: x, y (conteúdo), índice. */
    private final List<int[]> inserts = new ArrayList<>();
    private int start, medX, right;
    private ProgramModel model;
    private List<ResourceLocation> laidOut = List.of();
    private int laidW = -1;

    private static MutableComponent tr(String key, Object... args) {
        return Component.translatable(K + key, args);
    }

    void bounds(int x, int y, int w, int h) {
        bx = x;
        by = y;
        bw = w;
        bh = h;
        scroll.bounds(x, y + 1, w, h - 2);
        laidW = -1;
    }

    boolean over(double mx, double my) {
        return Gfx.in(mx, my, bx, by, bw, bh);
    }

    // =====================================================================================
    //  Layout
    // =====================================================================================
    /**
     * Recalcula as posições se a receita (ou a largura) mudou. A estrutura vem do {@link ProgramModel} (vale também
     * com a magia incompleta); com a magia válida, o tipo de cada conector vem do próprio {@link ParsedSpell}
     * ({@code stages()}, {@code hasNext()}, {@code leap()} — no Saltar, {@code next().spell()} é null).
     */
    void layout(List<ResourceLocation> glyphs, ProgramModel m, @Nullable ParsedSpell spell) {
        if (laidW == bw && model == m && laidOut.equals(glyphs)) return;
        List<ParsedSpell> parsed = spell != null ? spell.stages() : List.of();
        laidOut = List.copyOf(glyphs);
        laidW = bw;
        model = m;
        int n = glyphs.size();
        px = new int[n];
        py = new int[n];
        lines.clear();
        halos.clear();
        inserts.clear();
        medX = bx + 4;
        start = bx + 4 + MED + 6;
        right = bx + bw - 6;
        int y = TOP;
        for (int s = 0; s < m.stages.size(); s++) {
            ProgramModel.Stage st = m.stages.get(s);
            if (st.isEmpty()) {
                int idx = s > 0 ? m.stages.get(s - 1).link + 1 : 0;
                lines.add(new Line(Kind.PLACEHOLDER, y, ROW_H, s, -1));
                inserts.add(new int[]{start - 2, y + 2, idx});
                y += ROW_H;
            } else {
                y = layoutGroups(st.groups, y, Kind.STAGE, s);
            }
            if (st.link >= 0) {
                px[st.link] = start;
                py[st.link] = y + 2;
                ParsedSpell ps = s < parsed.size() ? parsed.get(s) : null;
                boolean leap = ps != null ? ps.leap() && !ps.hasNext() : st.leap();
                lines.add(new Line(leap ? Kind.LEAP : Kind.LINK, y, LINK_H, s, st.link));
                y += LINK_H;
            }
        }
        if (!m.laws.isEmpty()) {
            List<ProgramModel.Group> lawGroups = new ArrayList<>();
            for (int idx : m.laws) lawGroups.add(new ProgramModel.Group(List.of(idx)));
            y = layoutGroups(lawGroups, y, Kind.LAWS, -1);
        }
        for (int i = 0; i < n; i++) {
            inserts.add(new int[]{px[i] - 2, py[i], i});
            inserts.add(new int[]{px[i] + ICON + 1, py[i], i + 1});
        }
        if (n == 0) inserts.add(new int[]{start - 2, TOP + 2, 0});
        scroll.content(y + 4);
    }

    private int layoutGroups(List<ProgramModel.Group> groups, int top, Kind kind, int stage) {
        int x = start;
        int rowY = top + 2;
        for (ProgramModel.Group gr : groups) {
            List<Integer> mem = gr.members();
            int gw = mem.size() * PITCH - 2;
            if (x > start && x + gw > right) {
                x = start;
                rowY += ROW_H;
            }
            int segX = x, segY = rowY;
            for (int k = 0; k < mem.size(); k++) {
                if (k > 0 && x + ICON > right) {
                    // grupo maior que a linha: o halo continua na linha de baixo
                    halos.add(new int[]{segX - 2, segY - 2, x - PITCH + ICON + 2, segY + ICON + 2, mem.get(0)});
                    x = start;
                    rowY += ROW_H;
                    segX = x;
                    segY = rowY;
                }
                px[mem.get(k)] = x;
                py[mem.get(k)] = rowY;
                x += PITCH;
            }
            halos.add(new int[]{segX - 2, segY - 2, x - PITCH + ICON + 2, segY + ICON + 2, mem.get(0)});
            x += GROUP_GAP - 2;
        }
        int h = rowY + ICON + 4 - top;
        lines.add(new Line(kind, top, h, stage, -1));
        return top + h;
    }

    // =====================================================================================
    //  Consultas
    // =====================================================================================
    private int off() {
        return scroll.offset();
    }

    int chipX(int i) {
        return px[i];
    }

    int chipY(int i) {
        return by + py[i] - off();
    }

    /** Glifo sob o mouse (visível dentro da caixa), ou -1. */
    int chipAt(double mx, double my) {
        if (!over(mx, my)) return -1;
        for (int i = 0; i < px.length; i++) {
            if (Gfx.in(mx, my, chipX(i), chipY(i), ICON, ICON)) return i;
        }
        return -1;
    }

    /** Parte do chip sob o mouse: -1 mover para trás, 0 remover, 1 mover para frente. */
    int chipZone(int i, double mx) {
        double dx = mx - chipX(i);
        if (dx < 5 && i > 0) return -1;
        if (dx >= ICON - 5 && i < px.length - 1) return 1;
        return 0;
    }

    /** Ponto de inserção mais próximo do mouse: {x, yTela, índice}. */
    int[] insertionAt(double mx, double my) {
        int[] best = null;
        double bestD = Double.MAX_VALUE;
        for (int[] p : inserts) {
            double d = Math.abs(p[0] - mx) + 3 * Math.abs(by + p[1] - off() + ICON / 2.0 - my);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        if (best == null) return new int[]{start - 2, by + TOP + 2, 0};
        return new int[]{best[0], by + best[1] - off(), best[2]};
    }

    /** Rola para mostrar o glifo {@code i}. */
    void reveal(int i) {
        if (i < 0 || i >= py.length) return;
        scroll.reveal(py[i] - 3, ICON + 6);
    }

    // =====================================================================================
    //  Desenho
    // =====================================================================================
    /**
     * @param dragFrom glifo sendo arrastado (fica apagado), ou -1
     * @param hoverOk  se o mouse pode realçar chips (falso durante arrastos)
     */
    void render(GuiGraphics g, HotZones zones, MagicData d, SpellCheck check, List<ResourceLocation> glyphs,
                int dragFrom, boolean hoverOk, int mx, int my) {
        Font font = Gfx.font();
        Element el = check.primary;
        int n = glyphs.size();
        int border = n == 0 ? Gfx.FRAME : check.ok() ? Gfx.alpha(Gfx.GOOD, 0x90) : Gfx.alpha(Gfx.BAD, 0xA0);
        Gfx.box(g, bx, by, bw, bh, border);
        if (n == 0) {
            renderEmpty(g, font);
            return;
        }
        int off = off();
        int top = by + 1, bottom = by + bh - 1;
        g.enableScissor(bx + 1, top, bx + bw - 1, bottom);

        // ---- espinha (linha vertical entre os medalhões) e conectores
        for (int i = 0; i < lines.size(); i++) {
            Line ln = lines.get(i);
            if (ln.kind() != Kind.LINK && ln.kind() != Kind.LEAP) continue;
            Line prev = i > 0 ? lines.get(i - 1) : null;
            int sx = medX + MED / 2;
            int y0 = by + (prev != null ? prev.medY() + MED : ln.y()) - off;
            int midY = by + ln.y() + 2 + ICON / 2 - off;
            if (ln.kind() == Kind.LINK) {
                Line next = i + 1 < lines.size() ? lines.get(i + 1) : null;
                int y1 = by + (next != null ? next.medY() : ln.y() + ln.h()) - off;
                g.fill(sx - 1, y0, sx + 1, y1 - 1, Gfx.alpha(LINK_COLOR, 0xB0));
                // ponta da seta entrando no próximo medalhão
                g.fill(sx - 3, y1 - 4, sx + 3, y1 - 3, LINK_COLOR);
                g.fill(sx - 2, y1 - 3, sx + 2, y1 - 2, LINK_COLOR);
                g.fill(sx - 1, y1 - 2, sx + 1, y1 - 1, LINK_COLOR);
            } else {
                g.fill(sx - 1, y0, sx + 1, midY + 1, Gfx.alpha(LINK_COLOR, 0xB0));
            }
            // braço até o chip da ligação
            g.fill(sx + 1, midY, start - 3, midY + 1, Gfx.alpha(LINK_COLOR, 0xB0));
        }

        // ---- halos dos grupos
        for (int[] hl : halos) {
            Glyph head = GlyphRegistry.get(glyphs.get(hl[4]));
            int hc = head == null ? Gfx.BAD : GlyphIcons.kindColor(head);
            int y0 = by + hl[1] - off, y1 = by + hl[3] - off;
            Gfx.round(g, hl[0], y0, hl[2] - hl[0], y1 - y0, Gfx.alpha(hc, 0x2C));
            Gfx.frame(g, hl[0], y0, hl[2] - hl[0], y1 - y0, Gfx.alpha(hc, 0x70));
        }

        // ---- medalhões, espaços vazios e rótulos das ligações
        for (Line ln : lines) {
            int ly = by + ln.y() - off;
            if (ly > bottom || ly + ln.h() < top) continue;
            switch (ln.kind()) {
                case STAGE, PLACEHOLDER -> renderMedallion(g, zones, d, check, glyphs, ln, mx, my);
                case LAWS -> renderLawMedallion(g, zones, ln, transformLaws(glyphs));
                case LINK, LEAP -> renderLinkLabel(g, zones, check, glyphs, ln, mx, my);
            }
            if (ln.kind() == Kind.PLACEHOLDER) renderPlaceholder(g, font, ln);
        }

        // ---- chips
        for (int i = 0; i < n; i++) {
            int cx = chipX(i), cy = chipY(i);
            if (cy > bottom || cy + ICON < top) continue;
            Glyph gl = GlyphRegistry.get(glyphs.get(i));
            boolean hov = hoverOk && Gfx.in(mx, my, cx, cy, ICON, ICON) && over(mx, my);
            if (gl == null) {
                Gfx.round(g, cx, cy, ICON, ICON, 0xFF601818);
                g.drawString(font, "!", cx + 6, cy + 4, 0xFFFFFFFF, false);
                final ResourceLocation id = glyphs.get(i);
                zones.addClipped(cx, cy, ICON, ICON, bx, top, bw, bottom - top, null, () -> List.of(
                        Component.translatable("spell_error.isekai_mahou.unknown", id.toString()).withStyle(ChatFormatting.RED),
                        tr("sequence.chip_hint").withStyle(ChatFormatting.DARK_GRAY)));
                continue;
            }
            boolean bad = !d.knowsGlyph(gl.id()) || d.circle() < gl.minCircle() || !SpellParser.canUse(el, gl, d);
            int flags = (bad ? GlyphIcons.LOCKED : 0) | (check.culprits.contains(i) ? GlyphIcons.ERROR : 0)
                    | (GlyphIcons.isLesson(gl, d) ? GlyphIcons.LESSON : 0)
                    | (i == dragFrom ? GlyphIcons.DIM : 0) | (hov ? GlyphIcons.HOVER : 0);
            GlyphIcons.draw(g, gl, cx, cy, ICON, flags);
            if (hov) renderChipActions(g, i, n, mx);
            final int fi = i;
            zones.addClipped(cx, cy, ICON, ICON, bx, top, bw, bottom - top, null, () -> chipTip(d, el, check, gl, fi));
        }
        g.disableScissor();
        scroll.renderBar(g);
    }

    private void renderEmpty(GuiGraphics g, Font font) {
        List<FormattedCharSequence> lines1 = font.split(tr("program.empty"), bw - 16);
        List<FormattedCharSequence> lines2 = font.split(tr("program.empty2"), bw - 16);
        int total = (lines1.size() + lines2.size()) * 10 + 4;
        int ty = by + Math.max(4, (bh - total) / 2);
        for (FormattedCharSequence line : lines1) {
            g.drawString(font, line, bx + (bw - font.width(line)) / 2, ty, Gfx.TEXT_DIM, false);
            ty += 10;
        }
        ty += 4;
        for (FormattedCharSequence line : lines2) {
            g.drawString(font, line, bx + (bw - font.width(line)) / 2, ty, MahouUi.alpha(LINK_COLOR, 0xC0), false);
            ty += 10;
        }
    }

    private static final String[] PX_LEFT = {"..#", ".##", "###", ".##", "..#"};
    private static final String[] PX_RIGHT = {"#..", "##.", "###", "##.", "#.."};
    private static final String[] PX_X = {"#...#", ".#.#.", "..#..", ".#.#.", "#...#"};

    /** Setinhas e ✕ sobre o chip sob o mouse (mover/remover); a parte sob o mouse acende. */
    private void renderChipActions(GuiGraphics g, int i, int n, int mx) {
        int cx = chipX(i), cy = chipY(i);
        int zone = chipZone(i, mx);
        g.fill(cx, cy, cx + ICON, cy + ICON, 0x80100C18);
        if (zone == -1) g.fill(cx, cy, cx + 5, cy + ICON, 0x40FFFFFF);
        else if (zone == 1) g.fill(cx + ICON - 5, cy, cx + ICON, cy + ICON, 0x40FFFFFF);
        if (i > 0) GlyphIcons.pixels(g, PX_LEFT, cx + 1, cy + 6, 1, zone == -1 ? 0xFFFFFFFF : 0xFF9A93AD);
        if (i < n - 1) GlyphIcons.pixels(g, PX_RIGHT, cx + ICON - 4, cy + 6, 1, zone == 1 ? 0xFFFFFFFF : 0xFF9A93AD);
        GlyphIcons.pixels(g, PX_X, cx + 6, cy + 6, 1, zone == 0 ? 0xFFFF6B6B : 0xFFB06060);
    }

    private void renderMedallion(GuiGraphics g, HotZones zones, MagicData d, SpellCheck check, List<ResourceLocation> glyphs,
                                 Line ln, int mx, int my) {
        Font font = Gfx.font();
        int cx = medX + MED / 2;
        int cy = by + ln.medY() - off() + MED / 2;
        boolean err = stageHasCulprit(check, ln.stage());
        boolean hov = Gfx.in(mx, my, medX, cy - MED / 2, MED, MED);
        int ring = err ? Gfx.BAD : ln.kind() == Kind.PLACEHOLDER ? Gfx.FRAME_LIGHT : MahouUi.GOLD;
        Gfx.circle(g, cx, cy, MED / 2, hov ? 0xFF3A2C5A : 0xFF1C1428);
        Gfx.ring(g, cx, cy, MED / 2, 1, ring);
        String num = String.valueOf(ln.stage() + 1);
        g.drawString(font, num, cx - font.width(num) / 2 + 1, cy - 3, err ? 0xFFFFB0B0 : MahouUi.GOLD_PALE, false);
        zones.addClipped(medX, cy - MED / 2, MED, MED, bx, by + 1, bw, bh - 2, null, () -> stageTip(check, glyphs, ln.stage()));
    }

    /** v1.4: as leis da receita são Leis de Transformação (e não de Domínio)? */
    private static boolean transformLaws(List<ResourceLocation> glyphs) {
        for (ResourceLocation id : glyphs) if (GlyphRegistry.get(id) instanceof TransformLawGlyph) return true;
        return false;
    }

    private void renderLawMedallion(GuiGraphics g, HotZones zones, Line ln, boolean transform) {
        int y = by + ln.medY() - off();
        int lc = GlyphIcons.kindColor(GlyphKind.LAW);
        Gfx.circle(g, medX + MED / 2, y + MED / 2, MED / 2, 0xFF1C1428);
        Gfx.ring(g, medX + MED / 2, y + MED / 2, MED / 2, 1, lc);
        GlyphIcons.kindIcon(g, GlyphKind.LAW, false, medX + 2, y + 2, MED - 4, true);
        String key = transform ? "program.tlaws" : "program.laws";
        zones.addClipped(medX, y, MED, MED, bx, by + 1, bw, bh - 2, null,
                () -> List.of(tr(key).withColor(lc & 0xFFFFFF).withStyle(ChatFormatting.BOLD),
                        tr(key + ".tip").withStyle(ChatFormatting.GRAY)));
    }

    private void renderPlaceholder(GuiGraphics g, Font font, Line ln) {
        int y = by + ln.y() + 2 - off();
        int w = Math.min(right - start + 2, 150);
        // contorno tracejado
        int c = Gfx.alpha(LINK_COLOR, 0x90);
        for (int x = start - 2; x < start - 2 + w; x += 4) {
            g.fill(x, y, Math.min(x + 2, start - 2 + w), y + 1, c);
            g.fill(x, y + ICON - 1, Math.min(x + 2, start - 2 + w), y + ICON, c);
        }
        for (int yy = y; yy < y + ICON; yy += 4) {
            g.fill(start - 2, yy, start - 1, Math.min(yy + 2, y + ICON), c);
            g.fill(start - 3 + w, yy, start - 2 + w, Math.min(yy + 2, y + ICON), c);
        }
        Component full = tr("program.placeholder", ln.stage() + 1);
        Component shown = font.width(full) <= w - 8 ? full : tr("program.placeholder_short");
        if (font.width(shown) <= w - 8) g.drawString(font, shown, start + 2, y + 4, Gfx.alpha(LINK_COLOR, 0xD0), false);
    }

    private void renderLinkLabel(GuiGraphics g, HotZones zones, SpellCheck check, List<ResourceLocation> glyphs, Line ln, int mx, int my) {
        Font font = Gfx.font();
        Glyph gl = GlyphRegistry.get(glyphs.get(ln.link()));
        if (!(gl instanceof LinkGlyph link)) return;
        int y = by + ln.y() + 2 - off();
        int lx = start + ICON + 5;
        boolean leap = link.linkKind() == LinkGlyph.LinkKind.LEAP;
        Component name = leap ? Component.literal("↻ ").append(link.displayName())
                : link.displayName().copy().append(" ➜");
        g.drawString(font, name, lx, y + 4, LINK_COLOR, true);
        int nameW = font.width(name);
        Component role = GlyphIcons.linkRole(link.linkKind());
        int avail = right - (lx + nameW + 6);
        if (font.width(role) <= avail) g.drawString(font, role, lx + nameW + 6, y + 4, Gfx.TEXT_DIM, false);
        int zoneW = Math.min(right - lx, nameW + 6 + (font.width(role) <= avail ? font.width(role) : 0));
        zones.addClipped(lx, y, zoneW, ICON, bx, by + 1, bw, bh - 2, null, () -> linkTip(check, link));
    }

    // =====================================================================================
    //  Dicas
    // =====================================================================================
    private boolean stageHasCulprit(SpellCheck check, int stage) {
        for (int i : check.culprits) if (model != null && model.stageOf(i) == stage) return true;
        return false;
    }

    private List<Component> chipTip(MagicData d, Element el, SpellCheck check, Glyph gl, int index) {
        List<Component> lines = GlyphIcons.tooltip(gl, d, false);
        if (!SpellParser.canUse(el, gl, d)) {
            lines.add(Component.translatable("spell_error.isekai_mahou.wrong_element", gl.displayName(), el.displayName()).withStyle(ChatFormatting.RED));
            lines.add(tr("chip.awaken", gl.element().displayName()).withStyle(ChatFormatting.GRAY));
        }
        if (check.culprits.contains(index) && !check.ok()) {
            lines.add(Component.empty().append(Component.literal("✖ ")).append(check.result.error()).withStyle(ChatFormatting.RED));
            Component hint = check.hint();
            if (hint != null) lines.add(hint.copy().withStyle(ChatFormatting.GOLD));
        }
        lines.add(tr("sequence.chip_hint").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    private List<Component> stageTip(SpellCheck check, List<ResourceLocation> glyphs, int s) {
        List<Component> t = new ArrayList<>();
        t.add(tr("program.stage", s + 1).withStyle(ChatFormatting.BOLD).withColor(MahouUi.GOLD & 0xFFFFFF));
        ProgramModel.Stage st = model == null ? null : model.stage(s + 1);
        if (s > 0) {
            ProgramModel.Stage prev = model.stage(s);
            if (prev != null && prev.linkGlyph != null) {
                t.add(tr("program.born", GlyphIcons.linkRole(prev.linkGlyph.linkKind())).withColor(LINK_COLOR & 0xFFFFFF));
            }
        } else {
            t.add(tr("program.born_caster").withColor(LINK_COLOR & 0xFFFFFF));
        }
        ParsedSpell ps = check.stage(s);
        Component form = null;
        List<Component> effects = new ArrayList<>();
        if (ps != null) {
            form = ps.form().displayName();
            for (ParsedSpell.EffectEntry e : ps.effects()) effects.add(e.effect().displayName());
        } else if (st != null) {
            if (st.form >= 0) {
                Glyph f = GlyphRegistry.get(glyphs.get(st.form));
                if (f != null) form = f.displayName();
            }
            for (ProgramModel.Group gr : st.groups) {
                Glyph h = GlyphRegistry.get(glyphs.get(gr.head()));
                if (h instanceof EffectGlyph) effects.add(h.displayName());
            }
        }
        t.add(tr("program.form", form != null ? form : tr("program.none").withStyle(ChatFormatting.RED))
                .withColor(GlyphIcons.kindColor(GlyphKind.FORM) & 0xFFFFFF));
        MutableComponent eff = Component.empty();
        for (int i = 0; i < effects.size(); i++) {
            if (i > 0) eff.append(", ");
            eff.append(effects.get(i));
        }
        t.add(tr("program.effects", effects.isEmpty() ? tr("program.none").withStyle(ChatFormatting.RED) : eff)
                .withColor(GlyphIcons.kindColor(GlyphKind.EFFECT) & 0xFFFFFF));
        if (st != null && st.leap()) t.add(tr("program.ends_leap").withColor(LINK_COLOR & 0xFFFFFF));
        else if (st != null && st.linkGlyph != null) t.add(tr("program.then", st.linkGlyph.displayName()).withColor(LINK_COLOR & 0xFFFFFF));
        if (stageHasCulprit(check, s)) t.add(Component.literal("✖ ").append(check.result.error()).withStyle(ChatFormatting.RED));
        return t;
    }

    private List<Component> linkTip(SpellCheck check, LinkGlyph link) {
        List<Component> t = new ArrayList<>();
        t.add(link.displayName().withStyle(ChatFormatting.BOLD).withColor(LINK_COLOR & 0xFFFFFF));
        t.add(GlyphIcons.linkRole(link.linkKind()).withStyle(ChatFormatting.GRAY));
        t.add(link.description().withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_AQUA));
        ParsedSpell sp = check.spell();
        if (link.linkKind() == LinkGlyph.LinkKind.LEAP && sp != null) {
            t.add(tr("program.hop_cost", hopText(sp)).withColor(Gfx.MANA & 0xFFFFFF));
        }
        return t;
    }

    /**
     * Custo de cada salto do Saltar/Insaciável: 15% do custo da magia, mínimo 2 × COST_SCALE (= 6) — exatamente a conta
     * de {@code SpellContext.hopCost()} (que usa o custo da RAIZ: {@code sp} aqui é sempre a raiz).
     */
    static float hopCost(ParsedSpell sp) {
        return Math.max(2.0F * SpellCost.COST_SCALE, sp.manaCost() * 0.15F);
    }

    /** Custo de salto para mostrar: inteiro quando é inteiro, senão com uma casa (mesmo formato dos segundos). */
    static String hopText(ParsedSpell sp) {
        float c = hopCost(sp);
        if (Math.abs(c - Math.round(c)) < 0.05F) return String.valueOf(Math.round(c));
        return String.format(java.util.Locale.ROOT, "%.1f", c);
    }

    /** Há algum Saltar ou Insaciável na magia? (para mostrar o custo por salto) */
    static boolean hops(@Nullable ParsedSpell sp) {
        if (sp == null) return false;
        for (ParsedSpell st : sp.stages()) if (st.leap()) return true;
        return sp.anyStats(SpellStats::relentless);
    }
}
