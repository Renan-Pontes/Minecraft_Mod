package com.renanpontes.isekaimahou.feature.transform_laws.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.renanpontes.isekaimahou.client.GlyphArt;
import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.feature.transform_laws.ForgeNetwork;
import com.renanpontes.isekaimahou.feature.transform_laws.TlawNetwork;
import com.renanpontes.isekaimahou.feature.transform_laws.TransformForge;
import com.renanpontes.isekaimahou.feature.ui_grimoire.client.GlyphIcons;
import com.renanpontes.isekaimahou.feature.ui_grimoire.client.GrimoireDraft;
import com.renanpontes.isekaimahou.feature.ui_grimoire.client.GrimoireTransform;
import com.renanpontes.isekaimahou.feature.ui_grimoire.client.UiToast;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphKind;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellParser;
import com.renanpontes.isekaimahou.glyph.SpellPreset;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.glyph.SpellSlot;
import com.renanpontes.isekaimahou.glyph.TransformLawGlyph;
import com.renanpontes.isekaimahou.glyph.TransformTargets;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Onda 5 — <b>Moldar</b>: o lugar único onde o jogador edita TODAS as transformações que tem (palavra do Renan:
 * <i>"Transformações tem que ser editáveis pros efeitos que o player quiser"</i>).
 * <ul>
 *   <li>à esquerda, a lista de transformações do jogador (tecla V, liberações e formas de classe, transformações
 *       fixas que ele conhece), cada uma com o estado da receita dela;</li>
 *   <li>à direita, o editor da transformação escolhida: a fita da receita (o CORPO + o que foi posto), a paleta com
 *       todos os glifos de EFEITO, AUMENTO e LEI que o jogador conhece (sem lista fechada), o resumo do que ganha e do
 *       que custa, e os botões Salvar / Limpar / trazer uma receita do Grimório.</li>
 * </ul>
 * Clique numa peça da paleta: ela entra depois da peça marcada na fita (aumentos valem para a peça antes deles; no
 * corpo mexem em duração, recarga e alcance da aura). Clique direito na fita tira a peça. A validação é a mesma do
 * servidor ({@link TransformForge#validate}); nada vale até Salvar.
 * <p>
 * Onda 9 — mora DENTRO do Grimório ({@link TransformPanel}). A 3ª aba da paleta é <b>Votos</b>: a lista de todas as
 * Leis de Transformação, cada uma com caixinha. Na Transformação Racial e na Resurrección o voto marcado é o voto
 * "solto" de sempre (marcas {@code tlaw.race.*}/{@code tlaw.hollow.*}: vale na hora, sem Salvar, e não ocupa glifo);
 * nas outras transformações o voto entra na receita (precisa Salvar). Votos já postos na receita aparecem marcados em
 * qualquer transformação.
 */
final class ShaperView {
    private static final int ROW = 24;
    private static final int CELL = 20;
    /** Altura de uma linha da lista de Votos. */
    private static final int VROW = 22;
    private static final String PICK_RACE = "tlaw.race.";
    private static final String PICK_HOLLOW = "tlaw.hollow.";
    private static final int FORGE = 0xFFB98CFF;
    private static final String K = "screen.isekai_mahou.tforge.";

    /** Transformação em edição e o rascunho dela (sem o corpo), lembrados entre aberturas. */
    private static String selected = TransformForge.RACE_SLOT;
    private static String draftSlot;
    private static final List<ResourceLocation> draft = new ArrayList<>();
    /** Peça marcada na fita: -1 = o corpo; senão o índice no rascunho. */
    private static int cursor = -1;
    private static int paletteKind;
    private static int listScroll, paletteScroll;
    private static int candIndex = -1;

    private static SpellParser.Result draftResult;
    private static List<ResourceLocation> draftChecked;
    private static int draftVersion = -1;
    private static final Map<String, Object[]> STATUS = new HashMap<>();
    private static List<Glyph>[] palettes;
    private static int paletteVersion = -1;
    private static List<SpellRecipe> candCache;
    private static int candVersion = -1;
    private static List<ResourceLocation> candDraft;

    private int x, y, w, h;
    private int lx, lw, ex, ew;
    private Font font;
    private final List<int[]> zones = new ArrayList<>();
    private final List<Runnable[]> actions = new ArrayList<>();
    private List<Component> tip;

    void layout(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.font = Minecraft.getInstance().font;
        lx = x + 4;
        lw = Mth.clamp(w * 34 / 100, 110, 190);
        ex = lx + lw + 6;
        ew = x + w - 4 - ex;
    }

    static void select(String slot) {
        if (TransformForge.slot(slot) != null) selected = slot;
    }

    private static void click(float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), pitch, 0.6F));
    }

    // =====================================================================================
    //  Dados
    // =====================================================================================
    private static SpellRecipe stored(String slot) {
        return Minecraft.getInstance().player == null ? SpellRecipe.EMPTY : TransformForge.recipe(Minecraft.getInstance().player, slot);
    }

    /** Glifos da receita guardada sem o corpo. */
    private static List<ResourceLocation> parts(SpellRecipe r) {
        List<ResourceLocation> out = new ArrayList<>();
        for (int i = 1; i < r.glyphs().size(); i++) out.add(r.glyphs().get(i));
        return out;
    }

    private static SpellRecipe build(String slot) {
        if (draft.isEmpty()) return SpellRecipe.EMPTY;
        List<ResourceLocation> all = new ArrayList<>(draft.size() + 1);
        all.add(TransformForge.FORM_ID);
        all.addAll(draft);
        SpellRecipe base = stored(slot);
        return (base.isEmpty() ? SpellRecipe.EMPTY : base).withGlyphs(List.copyOf(all));
    }

    private static void load(String slot) {
        draft.clear();
        draft.addAll(parts(stored(slot)));
        draftSlot = slot;
        cursor = draft.isEmpty() ? -1 : firstLaw() - 1;
        paletteScroll = 0;
        draftResult = null;
    }

    private static boolean dirty(String slot) {
        return !draft.equals(parts(stored(slot)));
    }

    private static int firstLaw() {
        for (int i = 0; i < draft.size(); i++) {
            Glyph g = GlyphRegistry.get(draft.get(i));
            if (g != null && g.kind() == GlyphKind.LAW) return i;
        }
        return draft.size();
    }

    private static SpellParser.Result check(String slot, MagicData d) {
        if (draftResult == null || draftVersion != ClientMagic.syncVersion || !draft.equals(draftChecked)) {
            draftChecked = List.copyOf(draft);
            draftVersion = ClientMagic.syncVersion;
            draftResult = draft.isEmpty() ? null : TransformForge.validate(build(slot), creative() ? null : d);
        }
        return draftResult;
    }

    private static boolean creative() {
        return Minecraft.getInstance().player != null && Minecraft.getInstance().player.getAbilities().instabuild;
    }

    /** Validação da receita GUARDADA de um espaço (para a lista), refeita só quando algo muda. */
    private static SpellParser.Result status(String slot, SpellRecipe r, MagicData d) {
        Object[] c = STATUS.get(slot);
        if (c == null || (int) c[0] != ClientMagic.syncVersion || !r.equals(c[1])) {
            c = new Object[] {ClientMagic.syncVersion, r, TransformForge.validate(r, creative() ? null : d)};
            STATUS.put(slot, c);
        }
        return (SpellParser.Result) c[2];
    }

    /**
     * Receitas de Transformação que o jogador tem à mão, sem repetição: primeiro o rascunho aberto no Grimório (se ele
     * começar com o glifo Transformação), depois Biblioteca, modelos e teclas. Refeito só quando algo muda.
     */
    static List<SpellRecipe> candidates(MagicData d) {
        List<ResourceLocation> grim = GrimoireDraft.recipe().glyphs();
        if (candCache != null && candVersion == ClientMagic.syncVersion && grim.equals(candDraft)) return candCache;
        List<SpellRecipe> out = new ArrayList<>();
        addCandidate(out, GrimoireDraft.recipe());
        for (SpellRecipe r : d.library()) addCandidate(out, r);
        for (SpellPreset p : d.presets()) addCandidate(out, p.recipe());
        for (int l = 0; l < d.loadoutCount(); l++) {
            for (int s = 0; s < Progression.SLOTS; s++) {
                SpellSlot sl = d.peekSlot(l, s);
                if (sl == null) continue;
                for (SpellRecipe r : sl.all().values()) addCandidate(out, r);
            }
        }
        candCache = out;
        candVersion = ClientMagic.syncVersion;
        candDraft = List.copyOf(grim);
        return out;
    }

    private static void addCandidate(List<SpellRecipe> out, SpellRecipe r) {
        // só receitas que COMEÇAM com a forma (o editor de espaço não aceita gatilhos antes dela)
        if (r != null && !r.isEmpty() && r.glyphs().get(0).equals(TransformForge.FORM_ID) && !out.contains(r) && out.size() < 40) out.add(r);
    }

    /** Votos soltos (sem Salvar) existem só na Transformação Racial (0) e na Resurrección (1); -1 nas outras. */
    private static int looseSlot(String slot) {
        if (TransformForge.RACE_SLOT.equals(slot)) return 0;
        if (TransformForge.HOLLOW_SLOT.equals(slot)) return 1;
        return -1;
    }

    /** Todas as Leis de Transformação, por Círculo e nível. */
    private static List<TransformLawGlyph> laws() {
        List<TransformLawGlyph> out = new ArrayList<>();
        for (Glyph g : GlyphRegistry.byKind(GlyphKind.LAW)) if (g instanceof TransformLawGlyph t && !t.isHidden()) out.add(t);
        out.sort((a, b) -> a.minCircle() != b.minCircle() ? Integer.compare(a.minCircle(), b.minCircle())
                : Integer.compare(a.minLevel(), b.minLevel()));
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<Glyph> palette(int kind, MagicData d) {
        if (palettes == null || paletteVersion != ClientMagic.syncVersion) {
            palettes = new List[] {new ArrayList<Glyph>(), new ArrayList<Glyph>(), new ArrayList<Glyph>()};
            GlyphKind[] kinds = {GlyphKind.EFFECT, GlyphKind.AUGMENT, GlyphKind.LAW};
            for (int k = 0; k < 3; k++) {
                for (Glyph g : GlyphRegistry.byKind(kinds[k])) {
                    // Sem lista fechada: tudo o que o jogador CONHECE entra (leis: só as de Transformação)
                    if (g.isHidden() || !d.knowsGlyph(g.id())) continue;
                    if (k == 2 && !(g instanceof TransformLawGlyph)) continue;
                    palettes[k].add(g);
                }
                palettes[k].sort(GlyphIcons.ORDER);
            }
            paletteVersion = ClientMagic.syncVersion;
        }
        return palettes[kind];
    }

    // =====================================================================================
    //  Desenho
    // =====================================================================================
    private void zone(int zx, int zy, int zw, int zh, Runnable left, Runnable right) {
        zones.add(new int[] {zx, zy, zw, zh});
        actions.add(new Runnable[] {left, right});
    }

    private static boolean in(int mx, int my, int zx, int zy, int zw, int zh) {
        return mx >= zx && mx < zx + zw && my >= zy && my < zy + zh;
    }

    List<Component> render(GuiGraphics g, int mx, int my) {
        MagicData d = ClientMagic.data();
        zones.clear();
        actions.clear();
        tip = null;
        List<TransformForge.Slot> slots = TransformForge.slotsFor(d);
        boolean found = false;
        for (TransformForge.Slot s : slots) if (s.id().equals(selected)) found = true;
        if (!found && !slots.isEmpty()) selected = slots.get(0).id();
        if (!selected.equals(draftSlot)) load(selected);

        // ---- lista de transformações
        MahouUi.roundRect(g, lx, y, lw, h, 0xC0141020, 0xC00B0912);
        int contentH = slots.size() * ROW;
        listScroll = Mth.clamp(listScroll, 0, Math.max(0, contentH - h));
        g.enableScissor(lx, y, lx + lw, y + h);
        for (int i = 0; i < slots.size(); i++) {
            TransformForge.Slot s = slots.get(i);
            int ry = y + i * ROW - listScroll;
            if (ry + ROW < y || ry > y + h) continue;
            boolean sel = s.id().equals(selected);
            boolean hover = in(mx, my, lx, Math.max(ry, y), lw, ROW) && my < y + h;
            if (sel) g.fill(lx + 1, ry, lx + lw - 1, ry + ROW - 1, 0x403A2A60);
            else if (hover) g.fill(lx + 1, ry, lx + lw - 1, ry + ROW - 1, 0x30FFFFFF);
            if (sel) g.fill(lx + 1, ry, lx + 3, ry + ROW - 1, FORGE);
            g.drawString(font, MahouUi.fit(font, s.title().get(), lw - 12), lx + 7, ry + 3, sel ? 0xFFFFFFFF : MahouUi.TEXT, false);
            SpellRecipe r = stored(s.id());
            Component sub;
            int color;
            if (r.isEmpty()) {
                sub = Component.translatable(K + "list_none");
                color = 0xFF6A6080;
            } else {
                SpellParser.Result res = status(s.id(), r, d);
                if (res.ok()) {
                    sub = Component.translatable(K + "list_set", r.glyphs().size() - 1, TransformForge.slotMana(res.spell()));
                    color = 0xFF7CE08A;
                } else {
                    sub = Component.translatable(K + "recipe_broken");
                    color = 0xFFE07070;
                }
            }
            MahouUi.smallText(g, font, Component.literal("").append(sub), lx + 7, ry + 14, 0.75F, color, false);
            String id = s.id();
            int top = Math.max(ry, y), bottom = Math.min(ry + ROW, y + h);
            if (bottom > top) zone(lx, top, lw, bottom - top, () -> {
                selected = id;
                click(1.1F);
            }, null);
        }
        g.disableScissor();
        if (contentH > h) {
            int barH = Math.max(12, h * h / contentH);
            int barY = y + (h - barH) * listScroll / Math.max(1, contentH - h);
            g.fill(lx + lw - 2, barY, lx + lw, barY + barH, 0x80FFFFFF);
        }
        if (ew < 90) return tip;

        // ---- editor
        MahouUi.roundRect(g, ex, y, ew, h, 0xC0141020, 0xC00B0912);
        TransformForge.Slot cur = TransformForge.slot(selected);
        if (cur == null) return tip;
        int tx = ex + 6, tw = ew - 12, ty = y + 5;
        int max = Progression.maxGlyphs(d.level(), d.circle());
        boolean dirty = dirty(selected);
        g.drawString(font, MahouUi.fit(font, Component.translatable(K + "shape_title", cur.title().get()).withStyle(ChatFormatting.BOLD), tw - 44),
                tx, ty, FORGE, false);
        String count = (draft.size() + 1) + "/" + max;
        g.drawString(font, count, ex + ew - 6 - font.width(count), ty, draft.size() + 1 > max ? 0xFFE07070 : MahouUi.TEXT_DIM, false);
        ty += 12;

        // botões: Salvar · Limpar · Do Grimório · Virar magia
        int bw = Math.min(64, (tw - 12) / 4);
        button(g, mx, my, tx, ty, bw, Component.translatable(K + "btn_save"), dirty, this::save,
                Component.translatable(dirty ? K + "btn_save_tip" : K + "btn_saved_tip"));
        button(g, mx, my, tx + bw + 4, ty, bw, Component.translatable(K + "btn_clear"), !draft.isEmpty(), () -> {
            draft.clear();
            cursor = -1;
        }, Component.translatable(K + "btn_clear_tip"));
        List<SpellRecipe> cands = candidates(d);
        button(g, mx, my, tx + 2 * (bw + 4), ty, bw, Component.translatable(K + "btn_book"), !cands.isEmpty(), () -> {
            candIndex = (candIndex + 1) % cands.size();
            draft.clear();
            draft.addAll(parts(cands.get(candIndex)));
            cursor = firstLaw() - 1;
        }, Component.translatable(cands.isEmpty() ? K + "btn_book_none" : K + "btn_book_tip", cands.size()));
        // Onda 9: leva estas peças para o Grimório como magia de Transformação (para pôr numa tecla e conjurar)
        boolean castable = d.knowsGlyph(TransformForge.FORM_ID) || creative();
        button(g, mx, my, tx + 3 * (bw + 4), ty, bw, Component.translatable(K + "btn_spell"), castable, this::toSpell,
                castable ? Component.translatable(K + "btn_spell_tip") : Component.translatable(K + "btn_spell_locked", TransformForge.MIN_CIRCLE));
        ty += 17;

        // fita: o corpo + as peças
        int perRow = Math.max(4, tw / CELL);
        int cells = draft.size() + 1;
        for (int i = 0; i < cells; i++) {
            int cx = tx + (i % perRow) * CELL, cy = ty + (i / perRow) * CELL;
            int idx = i - 1;
            Glyph gl = GlyphRegistry.get(idx < 0 ? TransformForge.FORM_ID : draft.get(idx));
            boolean hover = in(mx, my, cx, cy, 18, 18);
            int flags = (cursor == idx ? GlyphIcons.SELECTED : 0) | (hover ? GlyphIcons.HOVER : 0);
            if (gl != null) GlyphIcons.draw(g, gl, cx + 1, cy + 1, 16, flags);
            if (idx < 0) MahouUi.roundOutline(g, cx, cy, 18, 18, cursor < 0 ? MahouUi.GOLD : 0xFF5A5070);
            if (hover) {
                tip = new ArrayList<>();
                if (idx < 0) {
                    tip.add(Component.translatable(K + "body_name", cur.title().get()).withStyle(ChatFormatting.BOLD));
                    tip.add(Component.translatable(K + "body_tip").withStyle(ChatFormatting.GRAY));
                } else if (gl != null) {
                    tip.addAll(GlyphIcons.tooltip(gl, d, false));
                    tip.add(Component.translatable(K + "strip_tip").withStyle(ChatFormatting.DARK_GRAY));
                }
            }
            zone(cx, cy, 18, 18, () -> {
                cursor = idx;
                click(1.3F);
            }, idx < 0 ? null : () -> removeAt(idx));
        }
        ty += ((cells - 1) / perRow + 1) * CELL + 2;

        // resumo (embaixo) — a paleta fica com o que sobrar
        List<FormattedCharSequence> sum = new ArrayList<>();
        List<Integer> sumColors = new ArrayList<>();
        SpellParser.Result res = check(selected, d);
        if (draft.isEmpty()) {
            addLines(sum, sumColors, Component.translatable(stored(selected).isEmpty() ? K + "shape_empty" : K + "shape_cleared"), tw, MahouUi.TEXT_DIM);
        } else if (res != null && res.ok()) {
            for (Component c : TransformForge.summary(res.spell(), false)) addLines(sum, sumColors, c, tw, 0xFFD8C8F0);
            if (dirty) addLines(sum, sumColors, Component.translatable(K + "shape_unsaved"), tw, 0xFFFFD86B);
        } else if (res != null) {
            addLines(sum, sumColors, res.error(), tw, 0xFFE07070);
        }
        int sumH = Math.min(sum.size(), Math.max(2, (h - (ty - y) - 60) / 10)) * 10;
        int sumY = y + h - 4 - sumH;
        for (int i = 0; i * 10 < sumH && i < sum.size(); i++) g.drawString(font, sum.get(i), tx, sumY + i * 10, sumColors.get(i), false);

        // paleta: Efeitos / Aumentos / Votos (a 3ª é uma lista, não uma grade)
        String[] names = {"pal_effects", "pal_augments", "pal_vows"};
        int px = tx;
        for (int k = 0; k < 3; k++) {
            Component label = Component.translatable(K + names[k]);
            int lwid = font.width(label) + 8;
            boolean on = paletteKind == k;
            boolean hover = in(mx, my, px, ty, lwid, 12);
            g.fill(px, ty, px + lwid, ty + 12, on ? 0xFF3A3050 : hover ? 0xFF2A2238 : 0xFF1A1622);
            g.drawString(font, label, px + 4, ty + 2, on ? 0xFFFFFFFF : MahouUi.TEXT_DIM, false);
            int kk = k;
            zone(px, ty, lwid, 12, () -> {
                paletteKind = kk;
                paletteScroll = 0;
                click(1.1F);
            }, null);
            px += lwid + 3;
        }
        ty += 15;
        int ph = sumY - 4 - ty;
        if (ph < CELL) return tip;
        if (paletteKind == 2) {
            vows(g, d, mx, my, tx, ty, tw, ph);
            return tip;
        }
        List<Glyph> pal = palette(paletteKind, d);
        int cols = Math.max(4, tw / CELL);
        int rows = (pal.size() + cols - 1) / cols;
        paletteScroll = Mth.clamp(paletteScroll, 0, Math.max(0, rows * CELL - ph));
        palRect = new int[] {tx, ty, tw, ph};
        if (pal.isEmpty()) {
            g.drawString(font, MahouUi.fit(font, Component.translatable(K + "pal_none"), tw), tx, ty + 2, 0xFF6A6080, false);
            return tip;
        }
        g.enableScissor(tx, ty, tx + tw, ty + ph);
        for (int i = 0; i < pal.size(); i++) {
            Glyph gl = pal.get(i);
            int cx = tx + (i % cols) * CELL, cy = ty + (i / cols) * CELL - paletteScroll;
            if (cy + CELL < ty || cy > ty + ph) continue;
            boolean hover = in(mx, my, cx, cy, 18, 18) && my >= ty && my < ty + ph;
            boolean locked = d.circle() < gl.minCircle() && !creative();
            boolean used = gl.kind() == GlyphKind.LAW && draft.contains(gl.id());
            int flags = (hover ? GlyphIcons.HOVER : 0) | (locked ? GlyphIcons.LOCKED : 0) | (used ? GlyphIcons.DIM : 0);
            GlyphIcons.draw(g, gl, cx + 1, cy + 1, 16, flags);
            if (hover) {
                tip = new ArrayList<>(GlyphIcons.tooltip(gl, d, false));
                tip.add(roleLine(gl));
            }
            int top = Math.max(cy, ty), bottom = Math.min(cy + 18, ty + ph);
            if (bottom > top) zone(cx, top, 18, bottom - top, () -> add(gl), null);
        }
        g.disableScissor();
        if (rows * CELL > ph) {
            int barH = Math.max(10, ph * ph / (rows * CELL));
            int barY = ty + (ph - barH) * paletteScroll / Math.max(1, rows * CELL - ph);
            g.fill(tx + tw - 2, barY, tx + tw, barY + barH, 0x80FFFFFF);
        }
        return tip;
    }

    private int[] palRect;

    /**
     * Onda 9 — aba <b>Votos</b>: todas as Leis de Transformação, uma por linha (caixinha, ícone, nome, custo e o bônus).
     * Desconhecidas ficam em cinza (a dica diz como aprender).
     */
    private void vows(GuiGraphics g, MagicData d, int mx, int my, int tx, int ty, int tw, int ph) {
        int loose = looseSlot(selected);
        String pre = loose == 1 ? PICK_HOLLOW : PICK_RACE;
        int max = TransformTargets.maxLaws(d.level(), d.circle());
        List<TransformLawGlyph> laws = laws();
        int count = 0;
        for (TransformLawGlyph l : laws) {
            if (draft.contains(l.id()) || (loose >= 0 && d.hasFlag(pre + l.id().getPath()))) count++;
        }
        Component head = Component.translatable(K + (loose >= 0 ? "vows_head_loose" : "vows_head_recipe"), count, max);
        MahouUi.smallText(g, font, head, tx, ty, 0.75F, count > max ? 0xFFE07070 : 0xFFFFD86B, false);
        ty += 9;
        ph -= 9;
        if (ph < VROW) return;
        palRect = new int[] {tx, ty, tw, ph};
        int contentH = laws.size() * VROW;
        paletteScroll = Mth.clamp(paletteScroll, 0, Math.max(0, contentH - ph));
        g.enableScissor(tx, ty, tx + tw, ty + ph);
        for (int i = 0; i < laws.size(); i++) {
            TransformLawGlyph l = laws.get(i);
            int ry = ty + i * VROW - paletteScroll;
            if (ry + VROW < ty || ry > ty + ph) continue;
            String path = l.id().getPath();
            boolean known = d.knowsGlyph(l.id()) || creative();
            boolean usable = known && (l.usableAt(d.level(), d.circle()) || creative());
            boolean inDraft = draft.contains(l.id());
            boolean picked = loose >= 0 && d.hasFlag(pre + path);
            boolean on = inDraft || picked;
            int color = 0xFF000000 | l.rgbColor();
            int top = Math.max(ry, ty), bottom = Math.min(ry + VROW, ty + ph);
            boolean hover = in(mx, my, tx, top, tw, bottom - top) && bottom > top;
            if (hover) g.fill(tx, ry, tx + tw, ry + VROW - 1, 0x30FFFFFF);
            // caixinha do voto
            int bx = tx + 3, by = ry + 6;
            g.fill(bx, by, bx + 10, by + 10, usable ? 0xFF2A2238 : 0xFF1A1622);
            MahouUi.roundOutline(g, bx - 1, by - 1, 12, 12, on ? color : 0xFF5A5070);
            if (on) g.fill(bx + 2, by + 2, bx + 8, by + 8, color);
            if (!known) g.setColor(0.35F, 0.32F, 0.4F, 1F);
            GlyphArt.glyph(g, l, tx + 17, ry + 3, 16);
            g.setColor(1F, 1F, 1F, 1F);
            int nx = tx + 37, nw = tw - 42;
            String cost = "+" + l.cost();
            g.drawString(font, MahouUi.fit(font, l.displayName(), nw - font.width(cost) - 4), nx, ry + 2, known ? color : 0xFF6A6080, false);
            g.drawString(font, cost, tx + tw - 4 - font.width(cost), ry + 2, known ? 0xFF7FB8FF : 0xFF50506A, false);
            Component sub = !known ? Component.translatable("screen.isekai_mahou.tlaw.unknown")
                    : !usable ? (l.minLevel() > d.level() ? Component.translatable("screen.isekai_mahou.tlaw.need_level", l.minLevel())
                    : Component.translatable("screen.isekai_mahou.tlaw.need_circle", l.minCircle()))
                    : inDraft && loose >= 0 ? Component.translatable(K + "vow_in_recipe").append(" · ").append(l.boon())
                    : l.boon();
            MahouUi.smallText(g, font, Component.literal("").append(sub), nx, ry + 12, 0.75F,
                    !known ? 0xFF5A5070 : !usable ? 0xFFE07070 : 0xFF7CE08A, false);
            if (hover) {
                tip = new ArrayList<>(GlyphIcons.tooltip(l, d, false));
                String ck = !known ? "vow_click_unknown"
                        : inDraft ? "vow_click_off"
                        : loose >= 0 ? (picked ? "vow_click_off_loose" : "vow_click_on_loose")
                        : "vow_click_on";
                tip.add(Component.translatable(K + ck).withStyle(ChatFormatting.DARK_GRAY));
            }
            if (bottom > top) zone(tx, top, tw, bottom - top, () -> toggleVow(l, loose, inDraft, picked), null);
        }
        g.disableScissor();
        if (contentH > ph) {
            int barH = Math.max(10, ph * ph / contentH);
            int barY = ty + (ph - barH) * paletteScroll / Math.max(1, contentH - ph);
            g.fill(tx + tw - 2, barY, tx + tw, barY + barH, 0x80FFFFFF);
        }
    }

    /** Clique num voto: tira da receita, ou (racial/Resurrección) marca/desmarca o voto solto, ou põe na receita. */
    private static void toggleVow(TransformLawGlyph l, int loose, boolean inDraft, boolean picked) {
        MagicData d = ClientMagic.data();
        if (!d.knowsGlyph(l.id()) && !creative()) {
            click(0.6F);
            return;
        }
        if (inDraft) {
            removeAt(draft.indexOf(l.id()));
            return;
        }
        if (loose >= 0) {
            // voto solto: o servidor confere limite, Círculo e se você não está transformado agora
            PacketDistributor.sendToServer(new TlawNetwork.SetRacePick(loose, l.id().getPath(), !picked));
            click(picked ? 0.9F : 1.2F);
            return;
        }
        add(l);
    }

    /** Tira a peça {@code idx} do rascunho e ajeita a peça marcada. */
    private static void removeAt(int idx) {
        if (idx < 0 || idx >= draft.size()) return;
        draft.remove(idx);
        cursor = Math.min(cursor, draft.size() - 1);
        if (cursor >= idx) cursor = idx - 1;
        click(0.9F);
    }

    private void addLines(List<FormattedCharSequence> out, List<Integer> colors, Component c, int width, int fallback) {
        for (FormattedCharSequence line : font.split(c, width)) {
            out.add(line);
            colors.add(fallback);
        }
    }

    /** Para onde a peça vai na transformação (linha extra na dica da paleta). */
    private static Component roleLine(Glyph gl) {
        String key;
        if (gl.kind() == GlyphKind.AUGMENT) key = cursor < 0 ? "role_augment_body" : "role_augment";
        else if (gl.kind() == GlyphKind.LAW) key = "role_law";
        else if (TransformTargets.isEffect(gl.id())) key = "role_body";
        else key = gl instanceof EffectGlyph e && e.isBeneficial() ? "role_aura" : "role_strike";
        return Component.translatable(K + key).withStyle(ChatFormatting.GOLD);
    }

    private void button(GuiGraphics g, int mx, int my, int bx, int by, int bw, Component label, boolean enabled, Runnable run, Component hint) {
        boolean hover = in(mx, my, bx, by, bw, 13);
        MahouUi.roundRect(g, bx, by, bw, 13, enabled ? (hover ? 0xFF4A3A70 : 0xFF2F2548) : 0xFF1A1622, enabled ? 0xFF241C38 : 0xFF14101C);
        FormattedCharSequence text = MahouUi.fit(font, label, bw - 4);
        g.drawString(font, text, bx + (bw - font.width(text)) / 2, by + 3, enabled ? 0xFFFFFFFF : 0xFF6A6080, false);
        if (hover) tip = new ArrayList<>(List.of(hint.copy().withStyle(ChatFormatting.GRAY)));
        zone(bx, by, bw, 13, enabled ? () -> {
            run.run();
            click(1.2F);
        } : null, null);
    }

    // =====================================================================================
    //  Ações
    // =====================================================================================
    private static void add(Glyph gl) {
        MagicData d = ClientMagic.data();
        int max = Progression.maxGlyphs(d.level(), d.circle());
        if (draft.size() + 1 >= max && !creative()) {
            click(0.6F);
            return;
        }
        int laws = firstLaw();
        if (gl.kind() == GlyphKind.LAW) {
            if (draft.contains(gl.id())) return;
            draft.add(gl.id());
        } else {
            int idx = Math.min(cursor + 1, laws);
            if (gl.kind() == GlyphKind.EFFECT) {
                // um efeito novo entra depois dos aumentos da peça marcada (não rouba os aumentos dela)
                while (idx < laws && kind(idx) == GlyphKind.AUGMENT) idx++;
            }
            draft.add(idx, gl.id());
            cursor = idx;
        }
        click(1.25F);
    }

    private static GlyphKind kind(int idx) {
        Glyph g = GlyphRegistry.get(draft.get(idx));
        return g == null ? GlyphKind.EFFECT : g.kind();
    }

    private void save() {
        SpellRecipe r = build(selected);
        PacketDistributor.sendToServer(new ForgeNetwork.SetRecipe(selected, r.isEmpty() ? Optional.empty() : Optional.of(r)));
    }

    /**
     * Onda 9: põe "Transformação + estas peças" no rascunho do Grimório e volta para o editor de magias — de lá o
     * jogador salva numa tecla (Z/X/C/G) e conjura como qualquer magia. O rascunho anterior vai para o desfazer.
     */
    private void toSpell() {
        List<ResourceLocation> all = new ArrayList<>(draft.size() + 1);
        all.add(TransformForge.FORM_ID);
        all.addAll(draft);
        SpellRecipe base = stored(selected);
        GrimoireDraft.load((base.isEmpty() ? SpellRecipe.EMPTY : base).withGlyphs(List.copyOf(all)));
        GrimoireTransform.close();
        UiToast.ok(Component.translatable(K + "to_spell_done"));
    }

    boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 && button != 1) return false;
        for (int i = zones.size() - 1; i >= 0; i--) {
            int[] z = zones.get(i);
            if (!in((int) mx, (int) my, z[0], z[1], z[2], z[3])) continue;
            Runnable r = actions.get(i)[button];
            if (r != null) r.run();
            return true;
        }
        return false;
    }

    boolean mouseScrolled(double mx, double my, double sy) {
        if (in((int) mx, (int) my, lx, y, lw, h)) {
            listScroll -= (int) (sy * ROW);
            return true;
        }
        if (palRect != null && in((int) mx, (int) my, palRect[0], palRect[1], palRect[2], palRect[3])) {
            paletteScroll -= (int) (sy * (paletteKind == 2 ? VROW : CELL));
            return true;
        }
        return false;
    }

}
