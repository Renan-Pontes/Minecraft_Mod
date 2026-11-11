package com.renanpontes.isekaimahou.feature.ui_grimoire.client;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.lwjgl.glfw.GLFW;

import com.renanpontes.isekaimahou.client.CoreKeys;
import com.renanpontes.isekaimahou.client.GlyphArt;
import com.renanpontes.isekaimahou.client.LoadoutInput;
import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.client.menu.MenuTab;
import com.renanpontes.isekaimahou.client.menu.StatusScreen;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.MoveTrigger;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.SpellBreakdown;
import com.renanpontes.isekaimahou.glyph.SpellParser;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.glyph.SpellSlot;
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
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Aba "Loadouts" (v1.0): os <b>9 loadouts</b> fixos, cada um com Z/X/C/G — acabou a Roda Elemental (o elemento de
 * cada magia vem dos glifos). À direita, a grade 9 × 4 (clique edita no Grimório, botão direito 2× limpa, ARRASTAR
 * move/troca magias entre slots); à esquerda, o cartão do loadout escolhido com as 4 magias (custo, dano estimado,
 * elementos), Ativar, Copiar para outro loadout e Limpar. Troca no jogo: Alt+Scroll, Mouse 5+Scroll, Alt+1..9.
 */
public class LoadoutsTab implements MenuTab {
    private static final String K = "screen.isekai_mahou.loadouts.";
    private static final int DOUBLE_CLICK_MS = 350;
    /** Loadout mostrado no cartão (persiste entre aberturas; -1 = o ativo). */
    private static int selected = -1;

    private StatusScreen screen;
    private int x, y, w, h;
    private int WL, gx, WR, footer;
    private final HotZones zones = new HotZones();
    private final Picker picker = new Picker();
    private int armedCell = -1;
    private long armedAt;
    private boolean armedClear;
    private long armedClearAt;
    private int lastRowClick = -1;
    private long lastRowClickAt;
    // arrastar célula
    private int pressCell = -1;
    private boolean dragging;
    private double pressX, pressY;
    private int mouseX, mouseY;
    /** Geometria das células da grade (para soltar o arrasto): x, y, w, h por célula. */
    private final int[][] cellRects = new int[MagicData.LOADOUTS * Progression.SLOTS][];

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
        WL = Mth.clamp(w * 36 / 100, 124, 176);
        gx = x + WL + 7;
        WR = w - WL - 7;
        footer = y + h - 22;
    }

    private static int selected(MagicData d) {
        if (selected < 0 || selected >= d.loadoutCount()) selected = d.activeLoadout();
        return selected;
    }

    @Override
    public void tick() {
        if (armedCell >= 0 && Util.getMillis() - armedAt > 3000) armedCell = -1;
        if (armedClear && Util.getMillis() - armedClearAt > 3000) armedClear = false;
    }

    // =====================================================================================
    //  Ações
    // =====================================================================================
    private void select(int l) {
        if (selected != l) Gfx.sound(SoundEvents.UI_BUTTON_CLICK, 1.4F, 0.25F);
        selected = l;
        armedClear = false;
    }

    private void activate(MagicData d, int l) {
        if (d.activeLoadout() == l) {
            UiToast.info(tr("already_active", l + 1));
            return;
        }
        // mesmo caminho das teclas (Alt+1..9): previsão local, pacote e aviso na barra de ação
        LoadoutInput.select(l);
        UiToast.ok(tr("activated", l + 1, d.loadoutCount()));
        Gfx.sound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.3F, 0.6F);
    }

    private void openEditor(MagicData d, int l, int s) {
        SpellSlot ss = d.peekSlot(l, s);
        MoveTrigger t = MoveTrigger.NORMAL;
        if (ss.get(MoveTrigger.NORMAL) == null) {
            for (MoveTrigger mt : MoveTrigger.values()) {
                if (ss.get(mt) != null && GrimoireDraft.triggerUnlocked(d, mt)) {
                    t = mt;
                    break;
                }
            }
        }
        GrimoireDraft.openSlot(d, l, s, t);
        Gfx.sound(SoundEvents.BOOK_PAGE_TURN, 1.0F, 0.7F);
        screen.switchTab("grimoire");
    }

    /** Grava todas as versões {@code versions} no slot (apagando as que não estão no mapa). */
    private static void writeSlot(MagicData d, int l, int s, Map<MoveTrigger, SpellRecipe> versions) {
        SpellSlot cur = d.peekSlot(l, s);
        for (MoveTrigger t : MoveTrigger.values()) {
            SpellRecipe want = versions.get(t);
            SpellRecipe have = cur.get(t);
            if (want == null && have == null) continue;
            if (want != null && want.equals(have)) continue;
            PacketDistributor.sendToServer(want == null ? CorePayloads.SetLoadoutSlot.clear(l, s, t) : CorePayloads.SetLoadoutSlot.set(l, s, t, want));
            d.setSlotRecipe(l, s, t, want); // previsão local
        }
    }

    private static Map<MoveTrigger, SpellRecipe> versions(MagicData d, int l, int s) {
        Map<MoveTrigger, SpellRecipe> m = new EnumMap<>(MoveTrigger.class);
        SpellSlot ss = d.peekSlot(l, s);
        for (MoveTrigger t : MoveTrigger.values()) if (ss.get(t) != null) m.put(t, ss.get(t));
        return m;
    }

    /** A receita tem forma de Ultimate (só vai no slot G)? */
    static boolean isUltimate(SpellRecipe r) {
        if (r == null) return false;
        for (ResourceLocation id : r.glyphs()) {
            Glyph g = GlyphRegistry.get(id);
            if (g instanceof FormGlyph f) return f.isUltimate();
        }
        return false;
    }

    /** As versões cabem no slot {@code s}? (G só aceita Ultimates; os outros não aceitam) */
    private static boolean fits(Map<MoveTrigger, SpellRecipe> versions, int s) {
        boolean ult = s == Progression.ULTIMATE_SLOT;
        for (SpellRecipe r : versions.values()) if (isUltimate(r) != ult) return false;
        return true;
    }

    /** Arrastou a célula {@code from} para {@code to}: move (destino vazio) ou troca (destino ocupado). */
    private void dropCell(MagicData d, int from, int to) {
        if (from == to) return;
        int lf = from / Progression.SLOTS, sf = from % Progression.SLOTS;
        int lt = to / Progression.SLOTS, st = to % Progression.SLOTS;
        Map<MoveTrigger, SpellRecipe> a = versions(d, lf, sf);
        Map<MoveTrigger, SpellRecipe> b = versions(d, lt, st);
        if (a.isEmpty()) return;
        if (!fits(a, st) || !fits(b, sf)) {
            UiToast.error(tr("move.ultimate_rule"));
            Gfx.sound(SoundEvents.VILLAGER_NO, 1.2F, 0.4F);
            return;
        }
        writeSlot(d, lt, st, a);
        writeSlot(d, lf, sf, b);
        Component where = tr("cell_name", lt + 1, CoreKeys.SLOTS[st].getTranslatedKeyMessage());
        UiToast.ok(b.isEmpty() ? tr("move.moved", where) : tr("move.swapped", where));
        Gfx.sound(SoundEvents.BOOK_PAGE_TURN, 1.3F, 0.7F);
    }

    private void rightClickCell(MagicData d, int l, int s) {
        SpellSlot ss = d.peekSlot(l, s);
        if (ss.isEmpty()) return;
        int id = l * Progression.SLOTS + s;
        if (armedCell != id || Util.getMillis() - armedAt > 3000) {
            armedCell = id;
            armedAt = Util.getMillis();
            UiToast.show(tr("cell.confirm"), Gfx.WARN);
            Gfx.sound(SoundEvents.UI_BUTTON_CLICK, 0.8F, 0.3F);
            return;
        }
        writeSlot(d, l, s, Map.of());
        armedCell = -1;
        UiToast.info(tr("cell.cleared"));
        Gfx.sound(SoundEvents.BOOK_PUT, 0.8F, 0.6F);
    }

    private void clearLoadout(MagicData d, int l) {
        if (d.loadoutEmpty(l)) return;
        if (!armedClear || Util.getMillis() - armedClearAt > 3000) {
            armedClear = true;
            armedClearAt = Util.getMillis();
            UiToast.show(tr("clear.confirm", l + 1), Gfx.WARN);
            Gfx.sound(SoundEvents.UI_BUTTON_CLICK, 0.8F, 0.3F);
            return;
        }
        for (int s = 0; s < Progression.SLOTS; s++) writeSlot(d, l, s, Map.of());
        armedClear = false;
        UiToast.info(tr("clear.done", l + 1));
        Gfx.sound(SoundEvents.BOOK_PUT, 0.8F, 0.6F);
    }

    private void copyLoadout(MagicData d, int from, int to) {
        if (from == to) return;
        for (int s = 0; s < Progression.SLOTS; s++) writeSlot(d, to, s, versions(d, from, s));
        UiToast.ok(tr("copy.done", from + 1, to + 1));
        Gfx.sound(SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 1.1F, 0.6F);
    }

    private void openCopyPicker(MagicData d, int from, int ax, int ay, int aw) {
        List<Picker.Option> opts = new ArrayList<>();
        for (int l = 0; l < d.loadoutCount(); l++) {
            if (l == from) continue;
            final int fl = l;
            boolean empty = d.loadoutEmpty(l);
            MutableComponent label = tr("row.tip", l + 1);
            if (!empty) label.append(Component.literal(" ⚠").withColor(Gfx.WARN & 0xFFFFFF));
            opts.add(new Picker.Option((g, ix, iy, s) -> numberBadge(g, ix, iy, s, fl, fl == d.activeLoadout(), !d.loadoutEmpty(fl)), label,
                    Component.literal(String.valueOf(l + 1)), Gfx.GOLD, true, false, () -> copyLoadout(d, from, fl),
                    () -> List.of(tr("copy.to", from + 1, fl + 1).withStyle(ChatFormatting.BOLD),
                            (empty ? tr("copy.to_empty") : tr("copy.to_replace")).withStyle(empty ? ChatFormatting.GRAY : ChatFormatting.GOLD))));
        }
        picker.toggle(ax, ay, aw, 14, true, x, x + w, y, footer, tr("copy.pick"), opts);
    }

    // =====================================================================================
    //  Desenho
    // =====================================================================================
    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        mouseX = mx;
        mouseY = my;
        zones.clear();
        MagicData d = ClientMagic.data();
        int sel = selected(d);
        renderCard(g, d, sel, mx, my);
        renderGrid(g, d, sel, mx, my);
        renderFooter(g);
        if (dragging) renderDrag(g, d, mx, my);
        UiToast.render(g, x + w / 2, footer - 2, w);
        picker.render(g, zones, mx, my);
        if (!dragging) zones.renderTooltip(g, mx, my);
    }

    private static void numberBadge(GuiGraphics g, int ix, int iy, int s, int l, boolean active, boolean filled) {
        int c = active ? Gfx.GOLD : 0xFF8A6FD0;
        MahouUi.roundRect(g, ix, iy, s, s, MahouUi.darken(c, 0.35F), MahouUi.darken(c, 0.7F));
        MahouUi.roundOutline(g, ix, iy, s, s, MahouUi.darken(c, 0.9F));
        String n = String.valueOf(l + 1);
        g.drawString(Gfx.font(), n, ix + (s - Gfx.font().width(n)) / 2 + 1, iy + (s - 8) / 2 + 1, 0xFFFFFFFF, true);
        if (filled) g.fill(ix + s - 3, iy + s - 3, ix + s - 1, iy + s - 1, Gfx.GOOD);
    }

    // ------------------------------------------------------------------ cartão do loadout
    private void renderCard(GuiGraphics g, MagicData d, int l, int mx, int my) {
        Font font = Gfx.font();
        // título da aba
        g.drawString(font, tr("title").withStyle(ChatFormatting.BOLD), x, y, Gfx.GOLD, false);
        int used = 0;
        for (int i = 0; i < d.loadoutCount(); i++) if (!d.loadoutEmpty(i)) used++;
        String cnt = used + "/" + d.loadoutCount();
        g.drawString(font, cnt, x + WL - font.width(cnt), y, Gfx.TEXT_DIM, false);
        final int fu = used;
        zones.add(x + WL - font.width(cnt), y, font.width(cnt), 9, null, () -> List.of(tr("used", fu, d.loadoutCount())));

        int cy = y + 13;
        int ch = footer - 3 - cy;
        boolean active = d.activeLoadout() == l;
        Gfx.box(g, x, cy, WL, ch, active ? Gfx.alpha(Gfx.GOLD, 0xB0) : Gfx.FRAME);
        // cabeçalho: medalhão com o número + "Loadout N" + estado
        int mcx = x + 12, mcy = cy + 12;
        double time = (Util.getMillis() % 60000L) / 1000.0;
        if (active) {
            float pulse = (float) (0.5 + 0.5 * Math.sin(time * 3));
            Gfx.ring(g, mcx, mcy, 10, 1, Gfx.alpha(Gfx.GOLD, (int) (110 + 140 * pulse)));
        }
        Gfx.circle(g, mcx, mcy, 8, active ? 0xFF4A3A14 : 0xFF241C38);
        Gfx.ring(g, mcx, mcy, 8, 1, active ? Gfx.GOLD : Gfx.FRAME_LIGHT);
        String n = String.valueOf(l + 1);
        g.drawString(font, n, mcx - font.width(n) / 2 + 1, mcy - 3, active ? Gfx.GOLD : 0xFFFFFFFF, true);
        g.drawString(font, tr("row.tip", l + 1).withStyle(ChatFormatting.BOLD), x + 25, cy + 4, 0xFFFFFFFF, false);
        Component state = active ? tr("active") : tr("inactive");
        g.drawString(font, MahouUi.fit(font, state, WL - 30), x + 25, cy + 14, active ? Gfx.GOLD : Gfx.TEXT_FAINT, false);

        // as 4 magias
        int rowH = Mth.clamp((ch - 26 - 34) / Progression.SLOTS, 18, 26);
        int ry = cy + 26;
        Set<Element> elements = new LinkedHashSet<>();
        for (int s = 0; s < Progression.SLOTS; s++) {
            renderCardSlot(g, d, l, s, x + 3, ry, WL - 6, rowH - 2, mx, my, elements);
            ry += rowH;
        }

        // elementos usados no loadout
        int ey = ry + 1;
        if (ey + 10 <= footer - 30) {
            g.drawString(font, tr("elements"), x + 4, ey + 1, Gfx.TEXT_FAINT, false);
            int ex = x + 6 + font.width(tr("elements"));
            for (Element e : elements) {
                if (ex + 10 > x + WL - 3) break;
                GlyphArt.element(g, e, ex, ey, 10);
                ex += 11;
            }
            if (elements.isEmpty()) g.drawString(font, "—", ex, ey + 1, Gfx.TEXT_FAINT, false);
            zones.add(x + 3, ey, WL - 6, 11, null, () -> {
                List<Component> t = new ArrayList<>();
                t.add(tr("elements.tip").withStyle(ChatFormatting.GRAY));
                for (Element e : elements) t.add(Component.literal("• ").append(e.displayName()).withColor(e.color));
                return t;
            });
        }

        // botões: Ativar · Copiar · Limpar
        int by = footer - 3 - 17;
        int bw = (WL - 6 - 4) / 3;
        int bx = x + 3;
        boolean empty = d.loadoutEmpty(l);
        cardButton(g, bx, by, bw, active ? tr("btn.active") : tr("btn.activate"), Gfx.GOLD, !active, mx, my,
                b -> activate(d, l), () -> List.of(active ? tr("btn.active.tip") : tr("btn.activate.tip", l + 1),
                        tr("keys.hint", CoreKeys.LOADOUT_MODIFIER.getTranslatedKeyMessage(), CoreKeys.LOADOUT_MOUSE.getTranslatedKeyMessage())
                                .withStyle(ChatFormatting.DARK_GRAY)));
        bx += bw + 2;
        final int cbx = bx;
        cardButton(g, bx, by, bw, tr("btn.copy"), 0xFF5C9CE0, !empty, mx, my,
                b -> openCopyPicker(d, l, cbx, by, bw), () -> List.of(empty ? tr("btn.copy.empty") : tr("btn.copy.tip", l + 1)));
        bx += bw + 2;
        cardButton(g, bx, by, x + WL - 3 - bx, armedClear ? tr("btn.clear_q") : tr("btn.clear"), armedClear ? Gfx.BAD : 0xFFB05A6A, !empty, mx, my,
                b -> clearLoadout(d, l), () -> List.of(empty ? tr("btn.copy.empty") : armedClear ? tr("clear.confirm", l + 1).withStyle(ChatFormatting.RED)
                        : tr("btn.clear.tip", l + 1)));
    }

    private void cardButton(GuiGraphics g, int bx, int by, int bw, Component label, int accent, boolean enabled, int mx, int my,
                            java.util.function.IntConsumer click, java.util.function.Supplier<List<Component>> tip) {
        Font font = Gfx.font();
        boolean hov = enabled && Gfx.in(mx, my, bx, by, bw, 15);
        Gfx.toggle(g, bx, by, bw, 15, false, hov, enabled ? accent : 0xFF4A4458);
        Component shown = font.width(label) <= bw - 4 ? label : Component.literal(Gfx.fit(font, label.getString(), bw - 4));
        g.drawString(font, shown, bx + (bw - font.width(shown)) / 2 + 1, by + 4, enabled ? (hov ? 0xFFFFFFFF : accent) : Gfx.TEXT_FAINT, false);
        zones.add(bx, by, bw, 15, enabled ? click : null, tip);
    }

    private void renderCardSlot(GuiGraphics g, MagicData d, int l, int s, int rx, int ry, int rw, int rh, int mx, int my, Set<Element> elements) {
        Font font = Gfx.font();
        SpellRecipe r = shown(d.peekSlot(l, s));
        boolean ult = s == Progression.ULTIMATE_SLOT;
        boolean hov = Gfx.in(mx, my, rx, ry, rw, rh);
        Gfx.round(g, rx, ry, rw, rh, hov ? 0x50302848 : 0x40100C18);
        // tecla
        int kw = 13;
        Component key = CoreKeys.SLOTS[s].getTranslatedKeyMessage();
        Gfx.round(g, rx + 1, ry + 1, kw, rh - 2, ult ? 0xFF4A3A14 : 0xFF241C38);
        String ks = key.getString();
        int kc = ult ? Gfx.GOLD : Gfx.TEXT;
        if (font.width(ks) <= kw - 2) g.drawString(font, ks, rx + 1 + (kw - font.width(ks)) / 2 + 1, ry + (rh - 8) / 2 + 1, kc, false);
        else Gfx.small(g, ks, rx + 2, ry + (rh - 5) / 2F, kc, Math.max(0.45F, (kw - 2) / (float) font.width(ks)));
        int tx = rx + kw + 4;
        if (r == null) {
            g.drawString(font, tr("cell.empty"), tx, ry + (rh - 8) / 2 + 1, Gfx.TEXT_FAINT, false);
            zones.add(rx, ry, rw, rh, b -> {
                if (b == 0) openEditor(d, l, s);
            }, () -> List.of(tr("cell.empty").withStyle(ChatFormatting.GRAY), tr("cell.hint").withStyle(ChatFormatting.DARK_GRAY)));
            return;
        }
        Element el = GlyphIcons.elementOf(r);
        elements.add(el);
        for (ResourceLocation id : r.glyphs()) {
            Glyph gl = GlyphRegistry.get(id);
            if (gl != null && gl.origin() == null && gl.element() != Element.ARCANE) elements.add(gl.element());
        }
        int icon = Math.min(16, rh - 2);
        GlyphArt.spell(g, r, el, tx, ry + (rh - icon) / 2, icon);
        tx += icon + 3;
        int tw = rx + rw - tx - 2;
        int col = GlyphIcons.recipeColor(r, el);
        SpellParser.Result res = SpellParser.parse(r, d);
        boolean two = rh >= 18;
        g.drawString(font, MahouUi.fit(font, GlyphIcons.recipeTitle(r), tw), tx, two ? ry + 1 : ry + (rh - 8) / 2 + 1, MahouUi.lighten(col, 0.5F), false);
        if (two) {
            Component sub;
            int subCol = Gfx.TEXT_FAINT;
            if (res.ok()) {
                SpellBreakdown b = res.spell().breakdown();
                MutableComponent c = tr("card.mana", b.manaCost());
                if (b.damage() > 0) c.append(Component.literal(" · ")).append(tr("card.damage", SpellBreakdown.num(b.damage())));
                else if (b.heal() > 0) c.append(Component.literal(" · ")).append(tr("card.heal", SpellBreakdown.num(b.heal())));
                sub = c;
                subCol = b.manaCost() > ClientMagic.maxMana ? Gfx.BAD : b.heavyCopies() ? Gfx.WARN : Gfx.TEXT_DIM;
            } else {
                sub = tr("card.invalid");
                subCol = Gfx.BAD;
            }
            g.drawString(font, MahouUi.fit(font, sub, tw), tx, ry + rh - 9, subCol, false);
        }
        SpellSlot ss = d.peekSlot(l, s);
        zones.add(rx, ry, rw, rh, b -> {
            if (b == 1) rightClickCell(d, l, s);
            else openEditor(d, l, s);
        }, () -> cellTip(d, ss, r, res));
    }

    /** A magia mostrada de um slot: a Normal, senão a primeira versão de movimento. */
    private static SpellRecipe shown(SpellSlot ss) {
        SpellRecipe show = ss.main();
        if (show != null) return show;
        for (MoveTrigger t : MoveTrigger.values()) if (ss.get(t) != null) return ss.get(t);
        return null;
    }

    // ------------------------------------------------------------------ grade 9 × 4
    private void renderGrid(GuiGraphics g, MagicData d, int sel, int mx, int my) {
        Font font = Gfx.font();
        g.fill(gx - 4, y, gx - 3, footer, Gfx.FRAME);
        int labelW = 16;
        int gap = 2;
        int colW = (WR - labelW - gap * 4) / 4;
        int headY = y;
        // colunas Z X C G
        for (int s = 0; s < Progression.SLOTS; s++) {
            int cxp = gx + labelW + gap + s * (colW + gap);
            Component key = CoreKeys.SLOTS[s].getTranslatedKeyMessage();
            boolean ult = s == Progression.ULTIMATE_SLOT;
            MutableComponent head = Component.empty().append(key.copy().withStyle(ChatFormatting.BOLD));
            if (ult) head.append(Component.literal(" ").append(tr("ultimate")).withStyle(ChatFormatting.GOLD));
            Component shownHead = font.width(head) <= colW ? head : key.copy().withStyle(ChatFormatting.BOLD);
            int hw = font.width(shownHead);
            if (hw <= colW) {
                g.drawString(font, shownHead, cxp + (colW - hw) / 2, headY, ult ? Gfx.GOLD : Gfx.TEXT, false);
            } else {
                float sc = Math.max(0.5F, colW / (float) hw);
                MahouUi.smallText(g, font, shownHead, cxp + (colW - hw * sc) / 2F, headY + (8 - 8 * sc) / 2F, sc, ult ? Gfx.GOLD : Gfx.TEXT, false);
            }
            final int fs = s;
            zones.add(cxp, headY, colW, 9, null, () -> List.of(
                    fs == Progression.ULTIMATE_SLOT ? tr("ultimate.tip") : tr("slot.tip", CoreKeys.SLOTS[fs].getTranslatedKeyMessage())));
        }

        int rows = d.loadoutCount();
        int gridTop = headY + 11;
        int cellH = Mth.clamp((footer - 3 - gridTop - (rows - 1) * gap) / Math.max(1, rows), 12, 30);
        int activeL = d.activeLoadout();
        for (int l = 0; l < rows; l++) {
            int ry = gridTop + l * (cellH + gap);
            final int fl = l;
            boolean act = l == activeL;
            boolean isSel = l == sel;
            boolean lh = Gfx.in(mx, my, gx, ry, labelW, cellH);
            if (isSel) Gfx.round(g, gx - 2, ry - 1, WR + 2, cellH + 2, 0x30B98CFF);
            Gfx.toggle(g, gx, ry, labelW, cellH, act, lh, act ? Gfx.GOLD : isSel ? 0xFFB98CFF : Gfx.FRAME_LIGHT);
            String lab = String.valueOf(l + 1);
            g.drawString(font, lab, gx + (labelW - font.width(lab)) / 2 + 1, ry + (cellH - 8) / 2 + 1, act ? Gfx.GOLD : isSel ? 0xFFFFFFFF : Gfx.TEXT_DIM, false);
            if (act) g.fill(gx + 1, ry + 1, gx + 3, ry + 3, Gfx.GOLD);
            zones.add(gx, ry, labelW, cellH, b -> rowClick(d, fl, b), () -> rowTip(d, fl));
            for (int s = 0; s < Progression.SLOTS; s++) {
                int cxp = gx + labelW + gap + s * (colW + gap);
                renderCell(g, d, l, s, cxp, ry, colW, cellH, mx, my);
            }
        }
    }

    private void rowClick(MagicData d, int l, int button) {
        if (button == 1) {
            activate(d, l);
            return;
        }
        long now = Util.getMillis();
        if (lastRowClick == l && now - lastRowClickAt < DOUBLE_CLICK_MS) {
            activate(d, l);
            lastRowClick = -1;
            return;
        }
        lastRowClick = l;
        lastRowClickAt = now;
        select(l);
    }

    private List<Component> rowTip(MagicData d, int l) {
        List<Component> t = new ArrayList<>();
        t.add(tr("row.tip", l + 1).withStyle(ChatFormatting.BOLD));
        if (l == d.activeLoadout()) t.add(tr("row.active").withColor(Gfx.GOLD & 0xFFFFFF));
        if (d.loadoutEmpty(l)) t.add(tr("row.empty").withStyle(ChatFormatting.DARK_GRAY));
        t.add(tr("row.hint").withStyle(ChatFormatting.DARK_GRAY));
        return t;
    }

    private void renderCell(GuiGraphics g, MagicData d, int l, int s, int cx, int cy, int cw, int ch, int mx, int my) {
        Font font = Gfx.font();
        int id = l * Progression.SLOTS + s;
        cellRects[id] = new int[]{cx, cy, cw, ch};
        SpellSlot ss = d.peekSlot(l, s);
        boolean hov = Gfx.in(mx, my, cx, cy, cw, ch);
        boolean armed = armedCell == id;
        boolean dropTarget = dragging && hov && pressCell != id;
        SpellRecipe show = shown(ss);
        if (show == null) {
            Gfx.round(g, cx, cy, cw, ch, dropTarget ? 0x7040A060 : hov ? 0x50302848 : 0x50100C18);
            Gfx.frame(g, cx, cy, cw, ch, dropTarget ? Gfx.GOOD : hov ? Gfx.FRAME_LIGHT : 0x70483E62);
            String plus = "+";
            g.drawString(font, plus, cx + (cw - font.width(plus)) / 2 + 1, cy + (ch - 8) / 2 + 1, hov ? Gfx.TEXT : Gfx.TEXT_FAINT, false);
            zones.add(cx, cy, cw, ch, b -> {
                if (b == 0) openEditor(d, l, s);
            }, () -> List.of(tr("cell.empty").withStyle(ChatFormatting.GRAY), tr("cell.hint").withStyle(ChatFormatting.DARK_GRAY)));
            return;
        }
        Element el = GlyphIcons.elementOf(show);
        int col = GlyphIcons.recipeColor(show, el);
        SpellParser.Result res = SpellParser.parse(show, d);
        boolean source = dragging && pressCell == id;
        Gfx.round(g, cx, cy, cw, ch, dropTarget ? Gfx.GOOD : hov ? Gfx.lighten(col, 0.5F) : armed ? Gfx.BAD : Gfx.darken(col, 0.85F));
        Gfx.roundGradient(g, cx + 1, cy + 1, cw - 2, ch - 2, Gfx.darken(col, hov ? 0.42F : 0.32F), Gfx.darken(col, 0.12F));
        if (source) g.setColor(1F, 1F, 1F, 0.35F);
        int icon = Math.max(8, Math.min(ch - 4, 18));
        int ix = cx + 2, iy = cy + (ch - icon) / 2;
        GlyphArt.spell(g, show, el, ix, iy, icon);
        g.setColor(1F, 1F, 1F, 1F);
        int tx = ix + icon + 3;
        String mana = res.ok() ? String.valueOf(res.spell().manaCost()) : "!";
        float msc = 0.75F;
        int manaW = Math.round(font.width(mana) * msc) + 3;
        boolean tall = ch >= 22;
        int tw = cx + cw - tx - 2 - (tall ? 0 : manaW);
        Component title = GlyphIcons.recipeTitle(show);
        g.drawString(font, Gfx.fit(font, title, tw), tx, tall ? cy + 3 : cy + (ch - 8) / 2 + 1, 0xFFFFFFFF, false);
        int manaCol = !res.ok() ? Gfx.BAD : res.spell().manaCost() > ClientMagic.maxMana ? Gfx.BAD : Gfx.MANA;
        if (tall) {
            int vx = tx;
            int vy = cy + ch - 9;
            for (MoveTrigger t : MoveTrigger.values()) {
                if (ss.get(t) == null) continue;
                GlyphIcons.triggerIcon(g, t, vx, vy, 8, true);
                vx += 9;
            }
            Gfx.small(g, mana, cx + cw - 3 - font.width(mana) * msc, vy + 1, manaCol, msc);
        } else {
            Gfx.small(g, mana, cx + cw - 2 - font.width(mana) * msc, cy + (ch - 6) / 2F, manaCol, msc);
        }
        if (armed) {
            g.fill(cx + 1, cy + 1, cx + cw - 1, cy + ch - 1, 0x90300808);
            Component c = font.width(tr("cell.clear_q")) <= cw - 4 ? tr("cell.clear_q") : Component.literal("?");
            g.drawString(font, c, cx + (cw - font.width(c)) / 2, cy + (ch - 8) / 2, Gfx.BAD, true);
        }
        zones.add(cx, cy, cw, ch, b -> {
            if (b == 1) rightClickCell(d, l, s);
        }, () -> cellTip(d, ss, show, res));
    }

    private List<Component> cellTip(MagicData d, SpellSlot ss, SpellRecipe shown, SpellParser.Result res) {
        List<Component> t = new ArrayList<>();
        Element el = GlyphIcons.elementOf(shown);
        t.add(GlyphIcons.recipeTitle(shown).copy().withStyle(ChatFormatting.BOLD).withColor(GlyphIcons.recipeColor(shown, el) & 0xFFFFFF));
        t.add(tr("cell.element", el.displayName()).withColor(el.color));
        t.add(GlyphIcons.glyphList(shown));
        if (res.ok()) {
            ParsedSpell sp = res.spell();
            t.addAll(BreakdownView.summaryLines(sp));
        } else {
            t.add(res.error().copy().withStyle(ChatFormatting.RED));
        }
        if (!shown.chant().isEmpty()) t.add(Component.literal("« " + shown.chant() + " »").withStyle(ChatFormatting.ITALIC, ChatFormatting.LIGHT_PURPLE));
        if (ss.all().size() > 1 || ss.main() == null) {
            t.add(tr("cell.versions").withStyle(ChatFormatting.GRAY));
            for (MoveTrigger mt : MoveTrigger.values()) {
                SpellRecipe r = ss.get(mt);
                if (r == null) continue;
                MutableComponent line = Component.literal(" • ").withStyle(ChatFormatting.DARK_GRAY)
                        .append(mt.displayName().withColor(GlyphIcons.triggerColor(mt) & 0xFFFFFF))
                        .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
                        .append(GlyphIcons.recipeTitle(r));
                if (!GrimoireDraft.triggerUnlocked(d, mt)) line.append(Component.literal(" ").append(tr("cell.locked")).withStyle(ChatFormatting.RED));
                t.add(line);
            }
        }
        t.add(tr("cell.hint").withStyle(ChatFormatting.DARK_GRAY));
        t.add(tr("cell.drag_hint").withStyle(ChatFormatting.DARK_GRAY));
        t.add(tr("cell.clear_hint").withStyle(ChatFormatting.DARK_GRAY));
        return t;
    }

    private void renderDrag(GuiGraphics g, MagicData d, int mx, int my) {
        if (pressCell < 0) return;
        SpellRecipe r = shown(d.peekSlot(pressCell / Progression.SLOTS, pressCell % Progression.SLOTS));
        if (r == null) return;
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        GlyphArt.spell(g, r, GlyphIcons.elementOf(r), mx - 9, my - 9, 18);
        g.pose().popPose();
    }

    // ------------------------------------------------------------------ rodapé
    private void renderFooter(GuiGraphics g) {
        Font font = Gfx.font();
        g.fill(x, footer, x + w, footer + 1, Gfx.FRAME);
        Component c1 = tr("controls", CoreKeys.LOADOUT_MODIFIER.getTranslatedKeyMessage(), CoreKeys.LOADOUT_MOUSE.getTranslatedKeyMessage());
        Component c2 = tr("controls.keys", CoreKeys.SLOTS[0].getTranslatedKeyMessage(), CoreKeys.SLOTS[1].getTranslatedKeyMessage(),
                CoreKeys.SLOTS[2].getTranslatedKeyMessage(), CoreKeys.SLOTS[3].getTranslatedKeyMessage(), CoreKeys.MENU.getTranslatedKeyMessage());
        footerLine(g, font, c1, x, footer + 3, w, Gfx.TEXT_DIM);
        footerLine(g, font, c2, x, footer + 12, w, Gfx.TEXT_FAINT);
        zones.add(x, footer + 2, w, 20, null, () -> List.of(tr("controls.title").withStyle(ChatFormatting.BOLD), c1, c2,
                tr("controls.direct", CoreKeys.LOADOUT_MODIFIER.getTranslatedKeyMessage()).withStyle(ChatFormatting.GRAY)));
    }

    /** Linha de rodapé inteira: se não cabe, encolhe a letra (até 60%) em vez de cortar o texto. */
    private static void footerLine(GuiGraphics g, Font font, Component text, int fx, int fy, int fw, int color) {
        int tw = font.width(text);
        if (tw <= fw) {
            g.drawString(font, text, fx, fy, color, false);
            return;
        }
        float sc = Math.max(0.6F, fw / (float) tw);
        if (tw * sc <= fw) {
            MahouUi.smallText(g, font, text, fx, fy + (8 - 8 * sc) / 2F, sc, color, false);
        } else {
            g.drawString(font, MahouUi.fit(font, text, fw), fx, fy, color, false);
        }
    }

    // =====================================================================================
    //  Entrada
    // =====================================================================================
    private int cellAt(double mx, double my) {
        for (int i = 0; i < cellRects.length; i++) {
            int[] r = cellRects[i];
            if (r != null && Gfx.in(mx, my, r[0], r[1], r[2], r[3])) return i;
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (picker.isOpen()) {
            if (picker.contains(mx, my)) zones.click(mx, my, button);
            else picker.close();
            return true;
        }
        if (button == 0) {
            int c = cellAt(mx, my);
            MagicData d = ClientMagic.data();
            if (c >= 0 && !d.peekSlot(c / Progression.SLOTS, c % Progression.SLOTS).isEmpty()) {
                pressCell = c;
                pressX = mx;
                pressY = my;
                dragging = false;
                return true;
            }
        }
        return zones.click(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (pressCell >= 0) {
            if (!dragging && Math.abs(mx - pressX) + Math.abs(my - pressY) > 4) dragging = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (pressCell < 0 || button != 0) return false;
        MagicData d = ClientMagic.data();
        int from = pressCell;
        pressCell = -1;
        if (dragging) {
            dragging = false;
            int to = cellAt(mx, my);
            if (to >= 0) dropCell(d, from, to);
        } else if (cellAt(mx, my) == from) {
            openEditor(d, from / Progression.SLOTS, from % Progression.SLOTS);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (picker.isOpen() && key == GLFW.GLFW_KEY_ESCAPE) {
            picker.close();
            return true;
        }
        // Ao abrir pelo teclado o vanilla foca o 1º botão de aba e o StatusScreen só fecha sem foco: fecha aqui
        if (CoreKeys.MENU.matches(key, scan)) {
            screen.onClose();
            return true;
        }
        MagicData d = ClientMagic.data();
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9) {
            int l = key - GLFW.GLFW_KEY_1;
            if (l < d.loadoutCount()) {
                select(l);
                return true;
            }
        }
        if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
            select(Math.floorMod(selected(d) + (key == GLFW.GLFW_KEY_UP ? -1 : 1), d.loadoutCount()));
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            activate(d, selected(d));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (picker.isOpen()) return true;
        // roda do mouse sobre o cartão ou a grade: percorre os loadouts
        if (Gfx.in(mx, my, x, y, w, footer - y)) {
            MagicData d = ClientMagic.data();
            select(Math.floorMod(selected(d) + (sy > 0 ? -1 : 1), d.loadoutCount()));
            return true;
        }
        return false;
    }
}
