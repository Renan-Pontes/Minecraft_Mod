package com.renanpontes.isekaimahou.feature.ui_grimoire.client;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.client.GlyphArt;
import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.api.Legends;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellParser;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.network.ClientMagic;
import com.renanpontes.isekaimahou.network.CorePayloads;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Coluna da esquerda do Grimório nos modos Biblioteca (magias guardadas pelo jogador) e Lendas (galeria das
 * Magias Lendárias do núcleo: ícone, nome, descrição, glifos que faltam em vermelho e botão "Carregar").
 */
final class LibraryPanel {
    private static final String K = "screen.isekai_mahou.grimoire.";
    private static final int LIB_ROW = 25;
    private static final int CARD_H = 50;
    private static final int CARD_GAP = 3;
    private static final int MINI = 12;

    /** Quem usa o painel (a aba do Grimório): carrega receitas no editor. */
    interface Host {
        void loadFromLibrary(SpellRecipe r);

        void loadLegend(Legends.Legend legend);
    }

    private final Host host;
    final ScrollState libScroll = new ScrollState();
    final ScrollState legendScroll = new ScrollState();
    private int x, y, w, h;
    private int armedDelete = -1;
    private long armedAt;

    LibraryPanel(Host host) {
        this.host = host;
    }

    private static MutableComponent tr(String key, Object... args) {
        return Component.translatable(K + key, args);
    }

    void bounds(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        libScroll.bounds(x, y, w, h);
        legendScroll.bounds(x, y, w, h);
    }

    void tick() {
        if (armedDelete >= 0 && Util.getMillis() - armedAt > 3000) armedDelete = -1;
    }

    void disarm() {
        armedDelete = -1;
    }

    // =====================================================================================
    //  Biblioteca
    // =====================================================================================
    void renderLibrary(GuiGraphics g, HotZones zones, MagicData d, Element el, String query, int mx, int my) {
        Font font = Gfx.font();
        List<SpellRecipe> lib = d.library();
        List<Integer> shown = new ArrayList<>();
        for (int i = 0; i < lib.size(); i++) if (query.isEmpty() || matches(lib.get(i), query)) shown.add(i);
        libScroll.content(shown.size() * LIB_ROW);
        int off = libScroll.offset();
        int rw = w - 5;
        g.enableScissor(x, y, x + w, y + h);
        for (int k = 0; k < shown.size(); k++) {
            int ry = y + k * LIB_ROW - off;
            if (ry + LIB_ROW < y || ry > y + h) continue;
            final int index = shown.get(k);
            SpellRecipe r = lib.get(index);
            Element nat = GlyphIcons.naturalElement(r, el);
            int col = GlyphIcons.recipeColor(r, nat);
            boolean hov = Gfx.in(mx, my, x, ry, rw, LIB_ROW - 2) && libScroll.over(mx, my);
            MahouUi.roundRect(g, x, ry, rw, LIB_ROW - 2, hov ? 0xE02A2140 : 0xC0181424, hov ? 0xE0181226 : 0xC0100C18);
            MahouUi.roundOutline(g, x, ry, rw, LIB_ROW - 2, hov ? Gfx.alpha(col, 0xFF) : Gfx.alpha(col, 0x70));
            GlyphArt.spell(g, r, nat, x + 3, ry + 2, 19);
            int tx = x + 26;
            int tw = rw - 26 - 14;
            Component title = GlyphIcons.recipeTitle(r);
            List<FormattedCharSequence> tl = font.split(title, tw);
            if (tl.size() == 1) {
                g.drawString(font, tl.get(0), tx, ry + 3, MahouUi.lighten(col, 0.55F), false);
                g.drawString(font, MahouUi.fit(font, subtitle(r, el, d), tw), tx, ry + 12, Gfx.TEXT_FAINT, false);
            } else {
                // nome comprido: usa as duas linhas para ele (os detalhes ficam na dica)
                g.drawString(font, tl.get(0), tx, ry + 3, MahouUi.lighten(col, 0.55F), false);
                g.drawString(font, MahouUi.fit(font, Component.literal(title.getString().substring(Math.min(title.getString().length(), lengthOf(tl.get(0)))).trim()), tw),
                        tx, ry + 12, MahouUi.lighten(col, 0.55F), false);
            }
            int dx = x + rw - 12;
            int dy = ry + 6;
            boolean armed = armedDelete == index;
            boolean dh = Gfx.in(mx, my, dx, dy, 10, 10);
            if (hov || armed) {
                Gfx.round(g, dx, dy, 10, 10, armed ? 0xFFB03030 : dh ? 0xFF6A2A36 : 0x60000000);
                String s = armed ? "?" : "✕";
                g.drawString(font, s, dx + 5 - font.width(s) / 2, dy + 1, 0xFFFFFFFF, false);
            }
            zones.addClipped(x, ry, rw, LIB_ROW - 2, x, y, w, h, b -> host.loadFromLibrary(r), () -> libraryTip(r, el, d));
            zones.addClipped(dx, dy, 10, 10, x, y, w, h, b -> delete(index),
                    () -> List.of(armed ? tr("library.confirm").withStyle(ChatFormatting.RED) : tr("library.delete")));
        }
        g.disableScissor();
        libScroll.renderBar(g);
        if (shown.isEmpty()) emptyText(g, font, lib.isEmpty() ? tr("library.empty") : tr("search.none"));
    }

    private static Component subtitle(SpellRecipe r, Element el, MagicData d) {
        SpellParser.Result res = SpellParser.parse(r, d);
        MutableComponent c = tr("library.glyphs", r.glyphs().size());
        if (res.ok()) {
            int stages = res.spell().stageCount();
            if (stages > 1) c.append(" · ").append(tr("library.stages", stages));
            c.append(" · ").append(tr("library.mana", res.spell().manaCost()));
        }
        return c;
    }

    private List<Component> libraryTip(SpellRecipe r, Element el, MagicData d) {
        List<Component> lines = new ArrayList<>();
        lines.add(GlyphIcons.recipeTitle(r).copy().withStyle(ChatFormatting.BOLD));
        lines.add(GlyphIcons.glyphList(r));
        SpellParser.Result res = SpellParser.parse(r, d);
        if (res.ok()) {
            lines.addAll(BreakdownView.summaryLines(res.spell()));
        } else {
            lines.add(tr("library.invalid_here").withStyle(ChatFormatting.GOLD));
            lines.add(res.error().copy().withStyle(ChatFormatting.RED));
        }
        if (!r.chant().isEmpty()) lines.add(Component.literal("« " + r.chant() + " »").withStyle(ChatFormatting.ITALIC, ChatFormatting.LIGHT_PURPLE));
        lines.add(tr("library.hint").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    private void delete(int index) {
        MagicData d = ClientMagic.data();
        if (index < 0 || index >= d.library().size()) return;
        if (armedDelete != index || Util.getMillis() - armedAt > 3000) {
            armedDelete = index;
            armedAt = Util.getMillis();
            Gfx.sound(SoundEvents.UI_BUTTON_CLICK, 0.8F, 0.3F);
            return;
        }
        PacketDistributor.sendToServer(new CorePayloads.LibraryEdit(1, index, SpellRecipe.EMPTY));
        d.removeFromLibrary(index);
        armedDelete = -1;
        UiToast.info(tr("library.deleted"));
        Gfx.sound(SoundEvents.BOOK_PUT, 0.7F, 0.6F);
    }

    // =====================================================================================
    //  Lendas
    // =====================================================================================
    void renderLegends(GuiGraphics g, HotZones zones, MagicData d, String query, int mx, int my) {
        Font font = Gfx.font();
        List<Legends.Legend> all = Legends.all();
        List<Legends.Legend> shown = new ArrayList<>();
        for (Legends.Legend l : all) if (query.isEmpty() || matches(l, query)) shown.add(l);
        legendScroll.content(shown.size() * (CARD_H + CARD_GAP));
        int off = legendScroll.offset();
        int cw = w - 5;
        g.enableScissor(x, y, x + w, y + h);
        for (int k = 0; k < shown.size(); k++) {
            int cy = y + k * (CARD_H + CARD_GAP) - off;
            if (cy + CARD_H < y || cy > y + h) continue;
            renderCard(g, zones, font, d, shown.get(k), x, cy, cw, mx, my);
        }
        g.disableScissor();
        legendScroll.renderBar(g);
        if (shown.isEmpty()) emptyText(g, font, all.isEmpty() ? tr("legends.none") : tr("search.none"));
    }

    private void renderCard(GuiGraphics g, HotZones zones, Font font, MagicData d, Legends.Legend l, int cx, int cy, int cw, int mx, int my) {
        int col = 0xFF000000 | l.color();
        boolean hov = Gfx.in(mx, my, cx, cy, cw, CARD_H) && legendScroll.over(mx, my);
        List<ResourceLocation> missing = missing(l, d);
        boolean circleOk = d.circle() >= l.minCircle();
        // moldura dourada (lendária) com um toque da cor da magia
        MahouUi.roundRect(g, cx, cy, cw, CARD_H, MahouUi.mix(0xF0241A10, col, 0.12F), 0xF0100C14);
        MahouUi.roundOutline(g, cx, cy, cw, CARD_H, hov ? MahouUi.GOLD : MahouUi.GOLD_DIM);
        g.fill(cx + 2, cy + 1, cx + cw - 2, cy + 2, MahouUi.alpha(col, 0x80));
        GlyphArt.spell(g, l.recipe(), l.element(), cx + 3, cy + 4, 22);
        int tx = cx + 29;
        int tw = cw - 29 - 3;
        // título e descrição dividem 3 linhas: título comprido quebra em 2 (descrição em 1); senão a descrição usa 2.
        // O que ainda não couber termina em "…" e aparece inteiro na dica do cartão.
        Component title = l.title().withStyle(ChatFormatting.BOLD);
        List<FormattedCharSequence> tl = font.split(title, tw);
        int titleLines = Math.min(2, tl.size());
        int titleColor = MahouUi.lighten(col, 0.4F);
        for (int i = 0; i < titleLines; i++) {
            FormattedCharSequence line = i == 1 && tl.size() > 2 ? MahouUi.fit(font, Component.literal(rest(tl, 1)).withStyle(ChatFormatting.BOLD), tw) : tl.get(i);
            g.drawString(font, line, tx, cy + 4 + i * 9, titleColor, true);
        }
        Component desc = description(l);
        List<FormattedCharSequence> dl = font.split(desc, tw);
        int descLines = Math.min(3 - titleLines, dl.size());
        for (int i = 0; i < descLines; i++) {
            FormattedCharSequence line = i == descLines - 1 && dl.size() > descLines
                    ? MahouUi.fit(font, Component.literal(rest(dl, i)).withStyle(desc.getStyle()), tw) : dl.get(i);
            g.drawString(font, line, tx, cy + 4 + (titleLines + i) * 9 + 1, Gfx.TEXT_DIM, false);
        }

        // fileira de glifos (os que faltam em vermelho) + requisito de Círculo + botão Carregar
        int rowY = cy + CARD_H - 16;
        Component load = tr("legends.load");
        int bw = font.width(load) + 10;
        int bx = cx + cw - bw - 3;
        Component circle = tr("legends.circle_short", l.minCircle());
        int cwid = font.width(circle) + 14;
        int badgeX = bx - cwid - 4;
        GlyphArt.element(g, l.element(), badgeX, rowY + 1, 11);
        g.drawString(font, circle, badgeX + 13, rowY + 3, circleOk ? Gfx.TEXT_DIM : Gfx.BAD, false);
        int gx = cx + 4;
        List<ResourceLocation> ids = l.recipe().glyphs();
        int maxIcons = Math.max(1, (badgeX - 4 - gx) / (MINI + 1));
        int shownIcons = ids.size() > maxIcons ? maxIcons - 1 : ids.size();
        for (int i = 0; i < shownIcons; i++) {
            ResourceLocation id = ids.get(i);
            Glyph gl = GlyphRegistry.get(id);
            int ix = gx + i * (MINI + 1);
            if (gl == null) {
                Gfx.round(g, ix, rowY + 1, MINI, MINI, 0xFF601818);
                g.drawString(font, "?", ix + 3, rowY + 3, 0xFFFFFFFF, false);
                continue;
            }
            boolean miss = missing.contains(id);
            GlyphIcons.draw(g, gl, ix, rowY + 1, MINI, miss ? GlyphIcons.LOCKED : 0);
            if (miss) MahouUi.roundOutline(g, ix - 1, rowY, MINI + 2, MINI + 2, 0xFFFF5050);
        }
        if (shownIcons < ids.size()) {
            String more = "+" + (ids.size() - shownIcons);
            g.drawString(font, more, gx + shownIcons * (MINI + 1) + 1, rowY + 3, missingAfter(ids, shownIcons, missing) ? Gfx.BAD : Gfx.TEXT_DIM, false);
        }
        boolean bh = Gfx.in(mx, my, bx, rowY, bw, 14) && legendScroll.over(mx, my);
        MahouUi.roundRect(g, bx, rowY, bw, 14, bh ? 0xFF6A5420 : 0xFF3E3014, bh ? 0xFF3A2C10 : 0xFF221A0A);
        MahouUi.roundOutline(g, bx, rowY, bw, 14, bh ? MahouUi.GOLD_PALE : MahouUi.GOLD);
        g.drawString(font, load, bx + 5, rowY + 3, bh ? 0xFFFFFFFF : MahouUi.GOLD, true);

        // a área do cartão é registrada antes: as do botão e dos ícones ficam por cima dela (HotZones pega a última)
        zones.addClipped(cx, cy, cw, CARD_H, x, y, w, h, b -> host.loadLegend(l), () -> legendTip(l, d, missing));
        zones.addClipped(bx, rowY, bw, 14, x, y, w, h, b -> host.loadLegend(l), () -> List.of(tr("legends.load.tip")));
        for (int i = 0; i < shownIcons; i++) {
            Glyph gl = GlyphRegistry.get(ids.get(i));
            if (gl == null) continue;
            int ix = gx + i * (MINI + 1);
            final Glyph fg = gl;
            zones.addClipped(ix, rowY + 1, MINI, MINI, x, y, w, h, null, () -> {
                List<Component> t = GlyphIcons.tooltip(fg, d, false);
                if (missing.contains(fg.id())) t.add(tr("legends.missing_one").withStyle(ChatFormatting.RED));
                return t;
            });
        }
    }

    private static boolean missingAfter(List<ResourceLocation> ids, int from, List<ResourceLocation> missing) {
        for (int i = from; i < ids.size(); i++) if (missing.contains(ids.get(i))) return true;
        return false;
    }

    /** Glifos da lenda que o jogador ainda não conhece (ou que nem existem nesta instalação). */
    static List<ResourceLocation> missing(Legends.Legend l, MagicData d) {
        List<ResourceLocation> out = new ArrayList<>();
        for (ResourceLocation id : l.recipe().glyphs()) {
            if ((GlyphRegistry.get(id) == null || !d.knowsGlyph(id)) && !out.contains(id)) out.add(id);
        }
        return out;
    }

    /** Descrição da lenda: "legend.isekai_mahou.<id>.desc" se existir; senão o cântico; senão uma frase padrão. */
    static Component description(Legends.Legend l) {
        Component desc = Component.translatableWithFallback("legend.isekai_mahou." + l.id() + ".desc", "");
        if (!desc.getString().isEmpty()) return desc.copy().withStyle(ChatFormatting.ITALIC);
        if (!l.recipe().chant().isBlank()) return Component.literal("« " + l.recipe().chant() + " »").withStyle(ChatFormatting.ITALIC);
        return tr("legends.default_desc", l.element().displayName());
    }

    private List<Component> legendTip(Legends.Legend l, MagicData d, List<ResourceLocation> missing) {
        List<Component> t = new ArrayList<>();
        t.add(l.title().withStyle(ChatFormatting.BOLD).withColor(l.color()));
        t.add(tr("legends.header").withColor(MahouUi.GOLD & 0xFFFFFF));
        t.add(description(l).copy().withStyle(ChatFormatting.GRAY));
        t.add(tr("legends.element", l.element().displayName()).withStyle(ChatFormatting.GRAY));
        t.add(tr("legends.circle", l.minCircle()).withStyle(d.circle() >= l.minCircle() ? ChatFormatting.GRAY : ChatFormatting.RED));
        if (!d.isAwakened(l.element())) t.add(tr("legends.not_awakened", l.element().displayName()).withStyle(ChatFormatting.GOLD));
        t.add(GlyphIcons.glyphList(l.recipe()));
        if (missing.isEmpty()) {
            t.add(tr("legends.all_known").withStyle(ChatFormatting.GREEN));
        } else {
            MutableComponent names = Component.empty();
            for (int i = 0; i < missing.size(); i++) {
                if (i > 0) names.append(", ");
                Glyph gl = GlyphRegistry.get(missing.get(i));
                names.append(gl != null ? gl.displayName() : Component.literal(missing.get(i).getPath()));
            }
            t.add(tr("legends.missing", missing.size(), names).withStyle(ChatFormatting.RED));
        }
        t.add(tr("legends.hint").withStyle(ChatFormatting.DARK_GRAY));
        return t;
    }

    // =====================================================================================
    //  Busca / util
    // =====================================================================================
    private static boolean matches(SpellRecipe r, String q) {
        if (Gfx.norm(GlyphIcons.recipeTitle(r).getString()).contains(q)) return true;
        for (ResourceLocation id : r.glyphs()) {
            Glyph g = GlyphRegistry.get(id);
            if (g != null && Gfx.norm(g.displayName().getString()).contains(q)) return true;
        }
        return false;
    }

    private static boolean matches(Legends.Legend l, String q) {
        return Gfx.norm(l.title().getString()).contains(q) || Gfx.norm(l.element().displayName().getString()).contains(q) || matches(l.recipe(), q);
    }

    private void emptyText(GuiGraphics g, Font font, Component text) {
        int ty = y + 10;
        for (FormattedCharSequence line : font.split(text, w - 12)) {
            g.drawString(font, line, x + (w - font.width(line)) / 2, ty, Gfx.TEXT_FAINT, false);
            ty += 10;
        }
    }

    /** Texto das linhas já quebradas a partir de {@code from} (para a última linha visível terminar com "…"). */
    private static String rest(List<FormattedCharSequence> lines, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < lines.size(); i++) {
            lines.get(i).accept((idx, style, cp) -> {
                sb.appendCodePoint(cp);
                return true;
            });
            sb.append(' ');
        }
        return sb.toString().trim();
    }

    /** Quantos caracteres uma linha já quebrada contém (para continuar o texto na linha seguinte). */
    static int lengthOf(FormattedCharSequence seq) {
        int[] n = {0};
        seq.accept((index, style, codePoint) -> {
            n[0] += Character.charCount(codePoint);
            return true;
        });
        return n[0];
    }
}
