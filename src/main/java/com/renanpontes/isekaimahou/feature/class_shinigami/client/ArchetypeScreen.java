package com.renanpontes.isekaimahou.feature.class_shinigami.client;

import java.util.List;

import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.feature.class_shinigami.Archetype;
import com.renanpontes.isekaimahou.feature.class_shinigami.ShinigamiNetwork;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * "Qual é o meu nome?" — a escolha do arquétipo da Shikai (rank II, aberta pelo espírito no Mundo Interior). Cinco
 * cartões lado a lado (ou em duas fileiras em telas estreitas) com o nome do espírito, o arquétipo, o que a Shikai e a
 * Bankai fazem e a paisagem. Clicar escolhe (definitivo). Não pausa o jogo.
 */
public class ArchetypeScreen extends Screen {
    private int hovered = -1;
    private final int[][] cards = new int[5][4];

    public ArchetypeScreen() {
        super(Component.translatable("screen.isekai_mahou.shinigami.choose_title"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        super.render(g, mx, my, partialTick);
        Font font = this.font;
        g.drawCenteredString(font, this.title.copy().withStyle(ChatFormatting.BOLD), width / 2, 14, MahouUi.GOLD);
        g.drawCenteredString(font, Component.translatable("screen.isekai_mahou.shinigami.choose_sub"), width / 2, 27, MahouUi.TEXT_DIM);
        Archetype[] all = Archetype.values();
        int cols = width >= 560 ? 5 : 3;
        int gap = 6;
        int cw = Math.min(150, (width - 20 - gap * (cols - 1)) / cols);
        int rows = (all.length + cols - 1) / cols;
        int ch = Math.min(rows == 1 ? 230 : 150, (height - 60 - gap * (rows - 1)) / rows);
        hovered = -1;
        for (int i = 0; i < all.length; i++) {
            int row = i / cols, col = i % cols;
            int inRow = Math.min(cols, all.length - row * cols);
            int rowW = inRow * cw + (inRow - 1) * gap;
            int x = (width - rowW) / 2 + col * (cw + gap);
            int y = 42 + row * (ch + gap);
            cards[i] = new int[]{x, y, cw, ch};
            boolean hover = mx >= x && mx < x + cw && my >= y && my < y + ch;
            if (hover) hovered = i;
            card(g, font, all[i], x, y, cw, ch, hover);
        }
        if (hovered >= 0) {
            Archetype a = all[hovered];
            g.renderTooltip(font, List.of(a.spiritName().getVisualOrderText(),
                    Component.translatable("screen.isekai_mahou.shinigami.choose_click").withStyle(ChatFormatting.GRAY).getVisualOrderText()), mx, my);
        }
    }

    private void card(GuiGraphics g, Font font, Archetype a, int x, int y, int w, int h, boolean hover) {
        int color = 0xFF000000 | a.rgb;
        MahouUi.roundRect(g, x, y, w, h, MahouUi.alpha(MahouUi.mix(color, 0xFF000000, 0.65F), hover ? 0xF0 : 0xD0), 0xE0100C16);
        MahouUi.roundOutline(g, x, y, w, h, hover ? MahouUi.lighten(color, 0.3F) : MahouUi.alpha(color, 0xB0));
        int ty = y + 6;
        g.drawCenteredString(font, a.displayName().withStyle(ChatFormatting.BOLD), x + w / 2, ty, color);
        ty += 11;
        g.drawCenteredString(font, a.spiritName().withStyle(ChatFormatting.ITALIC), x + w / 2, ty, MahouUi.TEXT);
        ty += 13;
        ty = wrap(g, font, a.description(), x + 5, ty, w - 10, MahouUi.TEXT_DIM, y + h - 4);
        ty += 3;
        ty = label(g, font, Component.translatable("screen.isekai_mahou.shinigami.shikai_label"), x + 5, ty, y + h - 4);
        ty = wrap(g, font, a.shikaiPassive(), x + 5, ty, w - 10, MahouUi.TEXT, y + h - 4);
        ty += 3;
        ty = label(g, font, Component.translatable("screen.isekai_mahou.shinigami.bankai_label", a.bankaiName()), x + 5, ty, y + h - 4);
        ty = wrap(g, font, a.bankaiPassive(), x + 5, ty, w - 10, MahouUi.TEXT, y + h - 4);
        ty += 3;
        wrap(g, font, Component.translatable("screen.isekai_mahou.shinigami.landscape", a.landscape()).withStyle(ChatFormatting.ITALIC),
                x + 5, ty, w - 10, MahouUi.TEXT_DIM, y + h - 4);
    }

    private static int label(GuiGraphics g, Font font, Component c, int x, int y, int limit) {
        if (y + 9 > limit) return y;
        g.drawString(font, c, x, y, MahouUi.GOLD, false);
        return y + 10;
    }

    private static int wrap(GuiGraphics g, Font font, Component text, int x, int y, int width, int color, int limit) {
        for (FormattedCharSequence line : font.split(text, width)) {
            if (y + 9 > limit) break;
            g.drawString(font, line, x, y, color, false);
            y += 10;
        }
        return y;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (int i = 0; i < cards.length; i++) {
                int[] c = cards[i];
                if (mx >= c[0] && mx < c[0] + c[2] && my >= c[1] && my < c[1] + c[3]) {
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    PacketDistributor.sendToServer(new ShinigamiNetwork.ChooseArchetype(i));
                    onClose();
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }
}
