package com.renanpontes.isekaimahou.feature.class_hollow.client;

import java.util.List;

import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.feature.class_hollow.Hollow;
import com.renanpontes.isekaimahou.feature.class_hollow.HollowNetwork;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * SÓ CLIENTE. Virar Arrancar: escolher UMA VEZ a forma da Resurrección (Pantera, Murciélago, Serpiente). Três cartas com
 * nome, comando de liberação e o que a forma faz; clicar seleciona, o botão confirma.
 */
public class ReleaseScreen extends Screen {
    private static final int CARD_W = 118, CARD_H = 150, GAP = 8;
    private int selected = -1;
    private Button confirm;

    public ReleaseScreen() {
        super(Component.translatable("screen.isekai_mahou.hollow.release.title"));
    }

    private int left() {
        return (width - (CARD_W * 3 + GAP * 2)) / 2;
    }

    private int top() {
        return (height - CARD_H) / 2;
    }

    @Override
    protected void init() {
        confirm = addRenderableWidget(Button.builder(Component.translatable("screen.isekai_mahou.hollow.release.confirm"), b -> {
            if (selected < 0) return;
            PacketDistributor.sendToServer(new HollowNetwork.Choice(HollowNetwork.CHOICE_RELEASE, selected));
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WITHER_SPAWN, 1.0F, 0.4F));
            onClose();
        }).bounds(width / 2 - 80, top() + CARD_H + 10, 160, 20).build());
        confirm.active = selected >= 0;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int l = left(), t = top();
        for (int i = 0; i < 3; i++) {
            int x = l + i * (CARD_W + GAP);
            if (mx >= x && mx < x + CARD_W && my >= t && my < t + CARD_H) {
                selected = i;
                confirm.active = true;
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 0.6F));
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        Font font = Minecraft.getInstance().font;
        int l = left(), t = top();
        g.drawCenteredString(font, title.copy().withColor(Hollow.RGB), width / 2, t - 26, 0xFFFFFFFF);
        g.drawCenteredString(font, Component.translatable("screen.isekai_mahou.hollow.release.sub"), width / 2, t - 14, MahouUi.TEXT_DIM);
        for (int i = 0; i < 3; i++) {
            Hollow.Release r = Hollow.Release.values()[i];
            int x = l + i * (CARD_W + GAP);
            boolean hover = mx >= x && mx < x + CARD_W && my >= t && my < t + CARD_H;
            int accent = 0xFF000000 | r.rgb;
            MahouUi.frame(g, x, t, CARD_W, CARD_H, selected == i ? accent : hover ? MahouUi.lighten(accent, 0.2F) : MahouUi.darken(accent, 0.5F));
            if (selected == i) g.fill(x + 2, t + 2, x + CARD_W - 2, t + 4, accent);
            g.drawCenteredString(font, r.displayName(), x + CARD_W / 2, t + 8, accent);
            int y = t + 22;
            for (FormattedCharSequence line : font.split(Component.literal("“").append(r.command()).append("”"), CARD_W - 12)) {
                g.drawString(font, line, x + 6, y, 0xFFE0D0B0, false);
                y += 10;
            }
            y += 4;
            List<FormattedCharSequence> desc = font.split(r.description(), CARD_W - 12);
            for (FormattedCharSequence line : desc) {
                if (y > t + CARD_H - 10) break;
                g.drawString(font, line, x + 6, y, MahouUi.TEXT_DIM, false);
                y += 10;
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
