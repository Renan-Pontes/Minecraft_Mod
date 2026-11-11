package com.renanpontes.isekaimahou.client.menu;

import com.renanpontes.isekaimahou.core.ClassKits;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Aba "Status" padrão (o módulo ui_hud pode substituir registrando o id "status"). */
public class DefaultStatusTab implements MenuTab {
    private int x, y, w, h;

    @Override
    public void init(StatusScreen screen, int x, int y, int w, int h) {
        this.x = x; this.y = y; this.w = w; this.h = h;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        MagicData d = ClientMagic.data();
        g.renderItem(ClassKits.icon(d.path()), x, y);
        g.drawString(font, d.path().displayName().withStyle(ChatFormatting.BOLD), x + 20, y + 4, 0xFFFFFFFF, true);
        int ly = y + 22;
        g.drawString(font, Component.translatable("screen.isekai_mahou.status.level", d.level(), d.levelCap()), x, ly, 0xFFE0C2FF, false);
        g.drawString(font, Component.translatable("screen.isekai_mahou.status.circle", d.circle()), x + 140, ly, 0xFFFFD86B, false);
        ly += 12;
        g.drawString(font, Component.translatable("screen.isekai_mahou.status.xp", d.xp(), Progression.xpToNext(d.level())), x, ly, 0xFF9AA4B0, false);
        g.drawString(font, Component.translatable("screen.isekai_mahou.status.mana", (int) ClientMagic.mana, ClientMagic.maxMana), x + 140, ly, 0xFFB98CFF, false);
        ly += 12;
        g.drawString(font, Component.translatable("screen.isekai_mahou.status.glyphs", d.glyphs().size()), x, ly, 0xFF9FF0FF, false);
        ly += 18;
        g.drawString(font, Component.translatable("screen.isekai_mahou.status.elements").withStyle(ChatFormatting.GOLD), x, ly, 0xFFFFFFFF, true);
        ly += 12;
        int col = 0;
        for (Element e : Element.values()) {
            if (!d.isAwakened(e)) continue;
            int cx = x + (col % 3) * (w / 3);
            int cy = ly + (col / 3) * 11;
            Component line = e.displayName().append(Component.literal(" Nv " + d.elementLevel(e)).withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(" (").append(d.path().affinity(e).displayName()).append(")").withStyle(ChatFormatting.DARK_GRAY));
            g.drawString(font, line, cx, cy, 0xFFFFFFFF, false);
            col++;
        }
    }
}
