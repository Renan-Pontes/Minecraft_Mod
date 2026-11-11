package com.renanpontes.isekaimahou.feature.class_shinigami.client;

import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.feature.class_shinigami.Archetype;
import com.renanpontes.isekaimahou.feature.class_shinigami.item.ZanpakutoItem;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Marcador pequeno logo abaixo da mira enquanto a zanpakutō está liberada: "Shikai · Chiribana 0:42" (ou Bankai) com
 * uma barrinha do tempo restante na cor do arquétipo. Só aparece com a espada liberada na mão.
 */
public final class ShinigamiHud {
    private static long total;
    private static long seenUntil;

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        if (p == null || mc.options.hideGui || mc.level == null) return;
        ItemStack held = p.getMainHandItem();
        if (!(held.getItem() instanceof ZanpakutoItem)) held = p.getOffhandItem();
        if (!(held.getItem() instanceof ZanpakutoItem)) return;
        int rel = ZanpakutoItem.release(held);
        if (rel == 0) return;
        long until = ZanpakutoItem.until(held);
        long now = mc.level.getGameTime();
        long left = Math.max(0, until - now);
        if (until != seenUntil) {
            seenUntil = until;
            total = Math.max(1, left);
        }
        Archetype a = Archetype.byIndex(ZanpakutoItem.archetype(held) - 1);
        int rgb = a == null ? 0xD9483B : a.rgb;
        Font font = mc.font;
        int secs = (int) ((left + 19) / 20);
        Component name = a == null ? Component.empty() : a.spiritName();
        Component text = Component.translatable(rel == 2 ? "hud.isekai_mahou.shinigami.bankai" : "hud.isekai_mahou.shinigami.shikai", name,
                String.format("%d:%02d", secs / 60, secs % 60));
        int cx = g.guiWidth() / 2, y = g.guiHeight() / 2 + 14;
        int w = Math.max(60, Math.min(140, font.width(text) + 8));
        MahouUi.smallText(g, font, text, cx - font.width(text) * 0.4F, y, 0.8F, 0xFF000000 | rgb, true);
        MahouUi.bar(g, cx - w / 2, y + 9, w, 2, left / (float) total, 0xFF000000 | rgb);
    }

    private ShinigamiHud() {}
}
