package com.renanpontes.isekaimahou.feature.ui_grimoire.client;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.client.CoreKeys;
import com.renanpontes.isekaimahou.client.GlyphArt;
import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.MoveTrigger;
import com.renanpontes.isekaimahou.glyph.SpellParser;
import com.renanpontes.isekaimahou.glyph.SpellPreset;
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
 * Coluna da esquerda do Grimório no modo PRESETS: até {@link MagicData#MAX_PRESETS} rascunhos nomeados guardados no
 * personagem (pedido do jogador: "presets de magias, salvar rascunhos para testar"). Clique carrega no editor; os
 * botões da linha gravam direto no slot de destino ("Salvar em"), renomeiam ou apagam (com confirmação). A caixa de
 * nome + botão "Salvar rascunho" ficam no rodapé (widgets criados pela aba). Tudo vai ao servidor por
 * {@link CorePayloads.PresetEdit}, com previsão local até a sincronização.
 */
final class PresetPanel {
    private static final String K = "screen.isekai_mahou.grimoire.presets.";
    static final int ROW = 25;
    /** Altura do rodapé (caixa de nome + botão). */
    static final int FOOTER = 17;
    private static final int BTN = 10;

    /** Quem usa o painel: carrega receitas no editor e conhece o rascunho. */
    interface Host {
        void loadPreset(SpellPreset p);
    }

    private final Host host;
    final ScrollState scroll = new ScrollState();
    private int x, y, w, h;
    private int armedDelete = -1;
    private long armedAt;
    /** Índice do preset sendo renomeado (-1 = a caixa de nome salva um novo). */
    int renaming = -1;

    PresetPanel(Host host) {
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
        scroll.bounds(x, y + 12, w, h - 12 - FOOTER);
    }

    void tick() {
        if (armedDelete >= 0 && Util.getMillis() - armedAt > 3000) armedDelete = -1;
        if (renaming >= ClientMagic.data().presets().size()) renaming = -1;
    }

    void disarm() {
        armedDelete = -1;
        renaming = -1;
    }

    // =====================================================================================
    //  Ações (chamadas pela aba e pelos botões das linhas)
    // =====================================================================================
    /** Salva o rascunho como preset (nome digitado, senão o nome da magia). Mesmo nome = substitui. */
    static void saveDraft(String typedName) {
        MagicData d = ClientMagic.data();
        if (GrimoireDraft.glyphs.isEmpty()) {
            UiToast.error(Component.translatable("screen.isekai_mahou.grimoire.need_glyphs"));
            return;
        }
        SpellRecipe r = GrimoireDraft.recipe();
        String name = SpellPreset.cleanName(typedName);
        if (name.isEmpty()) name = SpellPreset.cleanName(GlyphIcons.recipeTitle(r).getString());
        if (name.isEmpty()) name = d.uniquePresetName("#" + (d.presets().size() + 1));
        int existing = d.presetIndex(name);
        if (existing < 0 && d.presets().size() >= MagicData.MAX_PRESETS) {
            UiToast.error(tr("full", MagicData.MAX_PRESETS));
            return;
        }
        PacketDistributor.sendToServer(CorePayloads.PresetEdit.save(name, r));
        d.savePreset(name, r); // previsão local
        UiToast.ok(existing >= 0 ? tr("updated", name) : tr("saved", name, d.presets().size(), MagicData.MAX_PRESETS));
        Gfx.sound(SoundEvents.VILLAGER_WORK_CARTOGRAPHER, 1.3F, 0.6F);
    }

    /** Renomeia o preset {@code index}. */
    void rename(int index, String typedName) {
        MagicData d = ClientMagic.data();
        String name = SpellPreset.cleanName(typedName);
        if (index < 0 || index >= d.presets().size()) {
            renaming = -1;
            return;
        }
        if (name.isEmpty()) {
            UiToast.error(tr("rename.empty"));
            return;
        }
        int other = d.presetIndex(name);
        if (other >= 0 && other != index) {
            UiToast.error(tr("rename.taken", name));
            return;
        }
        PacketDistributor.sendToServer(CorePayloads.PresetEdit.rename(index, name));
        d.renamePreset(index, name);
        renaming = -1;
        UiToast.ok(tr("renamed", name));
        Gfx.sound(SoundEvents.BOOK_PAGE_TURN, 1.2F, 0.6F);
    }

    private void delete(int index) {
        MagicData d = ClientMagic.data();
        if (index < 0 || index >= d.presets().size()) return;
        if (armedDelete != index || Util.getMillis() - armedAt > 3000) {
            armedDelete = index;
            armedAt = Util.getMillis();
            UiToast.show(tr("delete.confirm"), Gfx.WARN);
            Gfx.sound(SoundEvents.UI_BUTTON_CLICK, 0.8F, 0.3F);
            return;
        }
        String name = d.presets().get(index).name();
        PacketDistributor.sendToServer(CorePayloads.PresetEdit.delete(index));
        d.deletePreset(index);
        armedDelete = -1;
        if (renaming == index) renaming = -1;
        else if (renaming > index) renaming--;
        UiToast.info(tr("deleted", name));
        Gfx.sound(SoundEvents.BOOK_PUT, 0.7F, 0.6F);
    }

    /** Grava o preset direto no slot de destino do Grimório (Salvar em: Loadout N, slot Z/X/C/G, versão). */
    private void toSlot(int index) {
        MagicData d = ClientMagic.data();
        SpellPreset p = d.preset(index);
        if (p == null) return;
        SpellParser.Result res = SpellParser.parse(p.recipe(), d);
        if (!res.ok()) {
            UiToast.error(tr("to_slot.invalid", res.error()));
            return;
        }
        int l = GrimoireDraft.loadout(d);
        int s = GrimoireDraft.slot();
        MoveTrigger t = GrimoireDraft.trigger(d);
        boolean ult = s == Progression.ULTIMATE_SLOT;
        if (ult && !res.spell().isUltimate()) {
            UiToast.error(Component.translatable("message.isekai_mahou.g_needs_ultimate"));
            return;
        }
        if (!ult && res.spell().isUltimate()) {
            UiToast.error(Component.translatable("message.isekai_mahou.ultimate_only_g"));
            return;
        }
        PacketDistributor.sendToServer(CorePayloads.PresetEdit.load(index, l, s, t));
        d.setSlotRecipe(l, s, t, p.recipe()); // previsão local
        UiToast.ok(tr("to_slot.done", p.name(), destText(l, s, t)));
        Gfx.sound(SoundEvents.ENCHANTMENT_TABLE_USE, 1.3F, 0.5F);
    }

    static Component destText(int l, int s, MoveTrigger t) {
        MutableComponent c = Component.translatable("screen.isekai_mahou.grimoire.dest.loadout", l + 1)
                .append(Component.literal(" · ").withStyle(ChatFormatting.GRAY))
                .append(CoreKeys.SLOTS[s].getTranslatedKeyMessage().copy());
        if (t != MoveTrigger.NORMAL) c.append(Component.literal(" · ").withStyle(ChatFormatting.GRAY)).append(t.displayName());
        return c;
    }

    // =====================================================================================
    //  Desenho
    // =====================================================================================
    void render(GuiGraphics g, HotZones zones, MagicData d, String query, int mx, int my) {
        Font font = Gfx.font();
        List<SpellPreset> all = d.presets();
        // cabeçalho: contagem + dica
        Component head = tr("count", all.size(), MagicData.MAX_PRESETS);
        g.drawString(font, head, x + 1, y + 1, all.size() >= MagicData.MAX_PRESETS ? Gfx.WARN : Gfx.GOLD, false);
        int lineX = x + font.width(head) + 6;
        if (lineX < x + w - 4) g.fill(lineX, y + 5, x + w - 4, y + 6, Gfx.alpha(Gfx.GOLD, 0x50));
        zones.add(x, y, w, 10, null, () -> List.of(tr("title").withStyle(ChatFormatting.BOLD).withColor(Gfx.GOLD & 0xFFFFFF),
                tr("title.tip1").withStyle(ChatFormatting.GRAY), tr("title.tip2").withStyle(ChatFormatting.GRAY),
                tr("title.tip3").withStyle(ChatFormatting.DARK_GRAY)));

        List<Integer> shown = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) if (query.isEmpty() || matches(all.get(i), query)) shown.add(i);
        scroll.content(shown.size() * ROW);
        int off = scroll.offset();
        int rw = w - 5;
        g.enableScissor(scroll.x, scroll.y, scroll.x + scroll.w, scroll.y + scroll.h);
        for (int k = 0; k < shown.size(); k++) {
            int ry = scroll.y + k * ROW - off;
            if (ry + ROW < scroll.y || ry > scroll.y + scroll.h) continue;
            renderRow(g, zones, font, d, shown.get(k), all.get(shown.get(k)), ry, rw, mx, my);
        }
        g.disableScissor();
        scroll.renderBar(g);
        if (shown.isEmpty()) {
            int ty = scroll.y + 8;
            for (FormattedCharSequence line : font.split(all.isEmpty() ? tr("empty") : Component.translatable("screen.isekai_mahou.grimoire.search.none"), w - 12)) {
                g.drawString(font, line, x + (w - font.width(line)) / 2, ty, Gfx.TEXT_FAINT, false);
                ty += 10;
            }
        }
    }

    private void renderRow(GuiGraphics g, HotZones zones, Font font, MagicData d, int index, SpellPreset p, int ry, int rw, int mx, int my) {
        SpellRecipe r = p.recipe();
        Element el = GlyphIcons.elementOf(r);
        int col = GlyphIcons.recipeColor(r, el);
        SpellParser.Result res = SpellParser.parse(r, d);
        boolean hov = Gfx.in(mx, my, x, ry, rw, ROW - 2) && scroll.over(mx, my);
        boolean editing = renaming == index;
        boolean same = r.equals(GrimoireDraft.recipe());
        MahouUi.roundRect(g, x, ry, rw, ROW - 2, hov ? 0xE02A2140 : 0xC0181424, hov ? 0xE0181226 : 0xC0100C18);
        int outline = editing ? Gfx.GOLD : same ? Gfx.alpha(Gfx.GOOD, 0xC0) : hov ? Gfx.alpha(col, 0xFF) : Gfx.alpha(col, 0x70);
        MahouUi.roundOutline(g, x, ry, rw, ROW - 2, outline);
        GlyphArt.spell(g, r, el, x + 3, ry + 2, 19);
        int buttons = hov || armedDelete == index || editing ? 3 * (BTN + 2) : 0;
        int tx = x + 26;
        int tw = rw - 26 - 4 - buttons;
        Component title = Component.literal(p.name());
        g.drawString(font, MahouUi.fit(font, title, tw), tx, ry + 3, MahouUi.lighten(col, 0.55F), false);
        Component sub;
        int subCol;
        if (res.ok()) {
            MutableComponent c = tr("row.glyphs", r.glyphs().size());
            c.append(" · ").append(tr("row.mana", res.spell().manaCost()));
            if (res.spell().stageCount() > 1) c.append(" · ").append(tr("row.stages", res.spell().stageCount()));
            sub = c;
            subCol = res.spell().manaCost() > ClientMagic.maxMana ? Gfx.BAD : Gfx.TEXT_FAINT;
        } else {
            sub = tr("row.draft", r.glyphs().size());
            subCol = 0xFFD9A066;
        }
        g.drawString(font, MahouUi.fit(font, sub, tw), tx, ry + 12, subCol, false);

        // a área da linha é registrada antes: os botões ficam por cima (HotZones pega a última)
        zones.addClipped(x, ry, rw, ROW - 2, scroll.x, scroll.y, scroll.w, scroll.h, b -> {
            if (b == 1) delete(index);
            else host.loadPreset(p);
        }, () -> rowTip(d, p, res));
        if (buttons == 0) return;
        int bx = x + rw - 3 - BTN;
        int by = ry + (ROW - 2 - BTN) / 2;
        // apagar
        boolean armed = armedDelete == index;
        button(g, zones, font, bx, by, armed ? "?" : "✕", armed ? 0xFFB03030 : 0xFF6A2A36, mx, my, b -> delete(index),
                () -> List.of(armed ? tr("delete.confirm").withStyle(ChatFormatting.RED) : tr("delete")));
        bx -= BTN + 2;
        // renomear
        button(g, zones, font, bx, by, "✎", editing ? 0xFF8A6A20 : 0xFF3A3058, mx, my, b -> {
            renaming = editing ? -1 : index;
            Gfx.sound(SoundEvents.UI_BUTTON_CLICK, 1.3F, 0.3F);
        }, () -> List.of(editing ? tr("rename.cancel") : tr("rename"), tr("rename.tip").withStyle(ChatFormatting.DARK_GRAY)));
        bx -= BTN + 2;
        // gravar no slot de destino
        button(g, zones, font, bx, by, "⇩", res.ok() ? 0xFF2E5A3A : 0xFF3A3040, mx, my, b -> toSlot(index), () -> {
            List<Component> t = new ArrayList<>();
            t.add(tr("to_slot").withStyle(ChatFormatting.BOLD));
            t.add(tr("to_slot.tip", destText(GrimoireDraft.loadout(d), GrimoireDraft.slot(), GrimoireDraft.trigger(d))).withStyle(ChatFormatting.GRAY));
            if (!res.ok()) t.add(res.error().copy().withStyle(ChatFormatting.RED));
            return t;
        });
    }

    private void button(GuiGraphics g, HotZones zones, Font font, int bx, int by, String s, int bg, int mx, int my,
                        java.util.function.IntConsumer click, java.util.function.Supplier<List<Component>> tip) {
        boolean hov = Gfx.in(mx, my, bx, by, BTN, BTN);
        Gfx.round(g, bx, by, BTN, BTN, hov ? Gfx.lighten(bg, 0.25F) : bg);
        g.drawString(font, s, bx + BTN / 2 - font.width(s) / 2 + 1, by + 1, 0xFFFFFFFF, false);
        zones.addClipped(bx, by, BTN, BTN, scroll.x, scroll.y, scroll.w, scroll.h, click, tip);
    }

    private List<Component> rowTip(MagicData d, SpellPreset p, SpellParser.Result res) {
        List<Component> t = new ArrayList<>();
        t.add(Component.literal(p.name()).withStyle(ChatFormatting.BOLD));
        if (!p.recipe().name().isBlank() && !p.recipe().name().equals(p.name())) {
            t.add(tr("row.spell_name", p.recipe().name()).withStyle(ChatFormatting.GRAY));
        }
        t.add(GlyphIcons.glyphList(p.recipe()));
        if (res.ok()) {
            t.addAll(BreakdownView.summaryLines(res.spell()));
        } else {
            t.add(tr("row.invalid").withStyle(ChatFormatting.GOLD));
            t.add(res.error().copy().withStyle(ChatFormatting.RED));
        }
        if (!p.recipe().chant().isEmpty()) t.add(Component.literal("« " + p.recipe().chant() + " »").withStyle(ChatFormatting.ITALIC, ChatFormatting.LIGHT_PURPLE));
        t.add(tr("row.hint").withStyle(ChatFormatting.DARK_GRAY));
        return t;
    }

    private static boolean matches(SpellPreset p, String q) {
        if (Gfx.norm(p.name()).contains(q) || Gfx.norm(GlyphIcons.recipeTitle(p.recipe()).getString()).contains(q)) return true;
        for (ResourceLocation id : p.recipe().glyphs()) {
            Glyph g = GlyphRegistry.get(id);
            if (g != null && Gfx.norm(g.displayName().getString()).contains(q)) return true;
        }
        return false;
    }
}
