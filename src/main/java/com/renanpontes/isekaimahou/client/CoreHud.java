package com.renanpontes.isekaimahou.client;

import com.renanpontes.isekaimahou.core.ClassKits;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.glyph.SpellSlot;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;

/**
 * HUD básico do núcleo (o módulo ui_hud pode substituir pela camada dele):
 * card no canto, barra de mana vertical, loadout (v1.0: "Loadout N/9", sem Roda) e slots Z X C G ao lado da hotbar,
 * barra de conjuração. A cor de cada slot vem do elemento da magia (glifos); a carga de ultimate é única.
 */
public class CoreHud implements LayeredDraw.Layer {
    /** Se false, o HUD do núcleo não desenha (o módulo ui_hud desliga quando tem o próprio HUD). */
    public static boolean enabled = true;
    private static float shownMana = -1;

    @Override
    public void render(GuiGraphics g, DeltaTracker dt) {
        if (!enabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.player.isSpectator()) return;
        Font font = mc.font;
        MagicData d = ClientMagic.data();

        float target = Math.min(ClientMagic.mana, ClientMagic.maxMana);
        if (shownMana < 0) shownMana = target;
        shownMana += (target - shownMana) * Math.min(1F, dt.getRealtimeDeltaTicks() * 0.35F);

        renderSlots(g, font, d);
        renderCastBar(g, font, dt.getGameTimeDeltaPartialTick(true));
        if (mc.getDebugOverlay().showDebugScreen()) return;

        // Card
        int x = 4, y = 4, w = 132, h = 40;
        g.fill(x, y, x + w, y + h, 0xC0101018);
        g.renderOutline(x, y, w, h, 0x80FFFFFF);
        g.fill(x, y, x + 2, y + h, d.path().argb());
        g.renderItem(ClassKits.icon(d.path()), x + 5, y + 5);
        g.drawString(font, d.path().displayName().withStyle(ChatFormatting.BOLD), x + 25, y + 4, 0xFFFFFFFF, true);
        Component lv = Component.translatable("hud.isekai_mahou.level_circle", d.level(), d.circle()).withStyle(ChatFormatting.GOLD);
        g.drawString(font, lv, x + 25, y + 14, 0xFFFFFFFF, true);
        float xpFrac = d.atLevelCap() ? 1F : Math.min(1F, (float) d.xp() / Progression.xpToNext(d.level()));
        g.fill(x + 25, y + 26, x + w - 5, y + 29, 0xFF000000);
        g.fill(x + 25, y + 26, x + 25 + (int) ((w - 30) * xpFrac), y + 29, d.atLevelCap() ? 0xFFFFD86B : 0xFF4DE1FF);
        if (ClientMagic.meditating) g.drawString(font, Component.translatable("hud.isekai_mahou.meditating"), x + 25, y + 31, 0xFFFFD86B, false);

        // Barra de mana vertical
        int bx = 10, bh = 110, bw = 10;
        int top = Math.max(y + h + 18, g.guiHeight() / 2 - bh / 2);
        float frac = ClientMagic.maxMana > 0 ? Math.max(0, Math.min(1F, shownMana / ClientMagic.maxMana)) : 0;
        g.drawString(font, "MP", bx + bw / 2 - font.width("MP") / 2, top - 11, 0xFFB98CFF, true);
        // v1.0: fadiga (regen reduzida depois de esvaziar) deixa a barra acinzentada
        boolean fatigued = mc.level != null && d.fatigued(mc.level.getGameTime());
        g.fill(bx - 2, top - 2, bx + bw + 2, top + bh + 2, ClientMagic.meditating ? 0xFFFFD86B : fatigued ? 0xFF555566 : 0xFF6A4BA0);
        g.fill(bx - 1, top - 1, bx + bw + 1, top + bh + 1, 0xFF000000);
        g.fill(bx, top, bx + bw, top + bh, 0xFF1A1030);
        int fh = Math.round(bh * frac);
        if (fh > 0) g.fillGradient(bx, top + bh - fh, bx + bw, top + bh, fatigued ? 0xFF9A90B0 : 0xFFC9A0FF, fatigued ? 0xFF4A4060 : 0xFF5A1EBE);
        String cur = String.valueOf((int) ClientMagic.mana);
        g.drawString(font, cur, bx + bw / 2 - font.width(cur) / 2, top + bh + 5, 0xFFFFFFFF, true);
    }

    private static void renderSlots(GuiGraphics g, Font font, MagicData d) {
        int loadout = d.activeLoadout();
        int size = 22;
        int x0 = g.guiWidth() / 2 + 91 + 8;
        int y = g.guiHeight() - size - 1;

        // "Loadout N/9" + 9 marcadores (branco = ativo; cheio = tem magia)
        Component head = Component.translatable("hud.isekai_mahou.loadout_short", loadout + 1, MagicData.LOADOUTS)
                .withStyle(ChatFormatting.GOLD);
        g.drawString(font, head, x0, y - 20, 0xFFFFFFFF, true);
        for (int i = 0; i < MagicData.LOADOUTS; i++) {
            int cx = x0 + font.width(head) + 6 + i * 6;
            g.fill(cx, y - 18, cx + 4, y - 14, i == loadout ? 0xFFFFFFFF : 0xFF000000);
            if (i != loadout) g.fill(cx + 1, y - 17, cx + 3, y - 15, d.loadoutEmpty(i) ? 0xFF333333 : 0xFFB98CFF);
        }

        for (int s = 0; s < Progression.SLOTS; s++) {
            int x = x0 + s * (size + 2);
            SpellSlot slot = d.peekSlot(loadout, s);
            SpellRecipe r = slot.main();
            int border = r != null ? LoadoutInput.color(r) : 0xFF444444;
            if (ClientMagic.castSlot == s) border = 0xFFFFFFFF;
            g.fill(x, y, x + size, y + size, 0xC0101018);
            g.renderOutline(x, y, size, size, border);
            if (r != null) {
                String abbr = abbreviation(r);
                g.drawString(font, abbr, x + size / 2 - font.width(abbr) / 2, y + 8, border, true);
                String key = MagicData.slotKey(loadout, s);
                int left = ClientMagic.cooldownLeft.getOrDefault(key, 0);
                if (left > 0) {
                    float f = (float) left / ClientMagic.cooldownTotal.getOrDefault(key, 1);
                    g.fill(x + 1, y + 1, x + size - 1, y + 1 + (int) ((size - 2) * f), 0xB0000000);
                }
            }
            if (s == Progression.ULTIMATE_SLOT) {
                float c = d.charge() / Progression.MAX_CHARGE;
                g.fill(x, y + size + 1, x + size, y + size + 3, 0xFF000000);
                g.fill(x, y + size + 1, x + (int) (size * c), y + size + 3, c >= 1 ? 0xFFFFD86B : 0xFFB98CFF);
            }
            Component key = CoreKeys.SLOTS[s].getTranslatedKeyMessage();
            g.drawString(font, key, x + size / 2 - font.width(key) / 2, y - 9, 0xFFBBBBBB, true);
        }
    }

    /** Duas letras para o ícone do slot: do nome da magia, ou da forma. */
    public static String abbreviation(SpellRecipe r) {
        String src = r.name();
        if (src.isEmpty() && !r.glyphs().isEmpty()) {
            Glyph form = GlyphRegistry.get(r.glyphs().get(0));
            src = form != null ? form.displayName().getString() : "?";
        }
        src = src.trim();
        if (src.isEmpty()) return "?";
        String[] parts = src.split("\\s+");
        if (parts.length >= 2) return ("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase();
        return src.substring(0, Math.min(2, src.length())).toUpperCase();
    }

    private static void renderCastBar(GuiGraphics g, Font font, float pt) {
        if (ClientMagic.castSlot < 0) return;
        int w = 100;
        int x = g.guiWidth() / 2 - w / 2;
        int y = g.guiHeight() / 2 + 20;
        float p = Math.min(1F, (ClientMagic.castElapsed + pt) / ClientMagic.castTotal);
        g.fill(x - 1, y - 1, x + w + 1, y + 5, 0xFF000000);
        g.fill(x, y, x + w, y + 4, 0xFF2A2A2A);
        MagicData d = ClientMagic.data();
        SpellRecipe casting = d.activeSlot(Math.max(0, Math.min(Progression.SLOTS - 1, ClientMagic.castSlot))).main();
        g.fill(x, y, x + (int) (w * p), y + 4, casting != null ? LoadoutInput.color(casting) : Element.ARCANE.argb());
        if (!ClientMagic.castChant.isEmpty()) {
            String[] words = ClientMagic.castChant.split("\\s+");
            int shown = Math.max(1, Math.min(words.length, (int) Math.ceil(words.length * p)));
            String text = String.join(" ", java.util.Arrays.copyOf(words, shown));
            g.drawCenteredString(font, Component.literal("« " + text + " »").withStyle(ChatFormatting.ITALIC), g.guiWidth() / 2, y - 12, 0xFFE0C2FF);
        }
    }
}
