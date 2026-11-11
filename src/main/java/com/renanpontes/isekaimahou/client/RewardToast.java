package com.renanpontes.isekaimahou.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * Toast de recompensa com moldura arcana (cliente): fundo escuro em gradiente, borda dupla dourada + cor de
 * destaque, cantoneiras, ícone num medalhão brilhante e um reflexo de luz que atravessa ao aparecer.
 * Enviado pelo servidor via {@code core.api.Rewards.toast}.
 */
public class RewardToast implements Toast {
    private static final long DISPLAY_MS = 6000L;
    private final Component title;
    private final Component subtitle;
    private final ItemStack icon;
    private final int accent;
    private final int width;
    private boolean playedSound;

    public RewardToast(Component title, Component subtitle, ItemStack icon, int rgb) {
        this.title = title;
        this.subtitle = subtitle;
        this.icon = icon == null ? ItemStack.EMPTY : icon;
        this.accent = 0xFF000000 | (rgb & 0xFFFFFF);
        Font font = Minecraft.getInstance().font;
        int text = Math.max(font.width(title), font.width(subtitle));
        this.width = Mth.clamp(text + 40, 160, 240);
    }

    @Override
    public int width() {
        return width;
    }

    @Override
    public Visibility render(GuiGraphics g, ToastComponent toasts, long time) {
        Font font = toasts.getMinecraft().font;
        int w = width(), h = height();

        // Moldura
        g.fillGradient(0, 0, w, h, 0xF4201830, 0xF40C0914);
        g.renderOutline(0, 0, w, h, MahouUi.EDGE_DARK);
        g.renderOutline(1, 1, w - 2, h - 2, MahouUi.GOLD_DIM);
        g.renderOutline(3, 3, w - 6, h - 6, MahouUi.alpha(accent, 0x70));
        MahouUi.brackets(g, 1, 1, w - 2, h - 2, 5, MahouUi.GOLD);
        MahouUi.diamond(g, w - 8, h / 2, 2, MahouUi.alpha(accent, 0xC0));

        // Medalhão do ícone com brilho pulsante
        float pulse = 0.5F + 0.5F * Mth.sin(time / 260F);
        int glow = MahouUi.alpha(accent, (int) (40 + 50 * pulse));
        g.fill(5, 5, 27, 27, glow);
        MahouUi.roundRect(g, 6, 6, 20, 20, 0xFF2A2040, 0xFF100C18);
        MahouUi.roundOutline(g, 6, 6, 20, 20, accent);
        if (!icon.isEmpty()) g.renderFakeItem(icon, 8, 8);
        else MahouUi.diamond(g, 16, 16, 4, accent);

        // Textos
        int tx = 32, tw = w - tx - 12;
        g.drawString(font, MahouUi.fit(font, title, tw), tx, 7, MahouUi.lighten(accent, 0.25F), true);
        g.drawString(font, MahouUi.fit(font, subtitle, tw), tx, 18, MahouUi.TEXT, false);

        // Reflexo de luz atravessando o toast no começo
        if (time < 1400L) {
            float t = time / 1400F;
            int sx = (int) (-20 + (w + 40) * t);
            for (int i = 0; i < 6; i++) {
                int x0 = sx + i * 2;
                int a = 0x26 - Math.abs(i - 3) * 6;
                if (a > 0 && x0 > 1 && x0 < w - 2) g.fill(x0, 2, Math.min(w - 2, x0 + 2), h - 2, MahouUi.alpha(0xFFFFFF, a));
            }
        }

        if (!playedSound && time > 0) {
            playedSound = true;
            toasts.getMinecraft().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.4F, 1.0F));
        }
        return time >= DISPLAY_MS * toasts.getNotificationDisplayTimeMultiplier() ? Visibility.HIDE : Visibility.SHOW;
    }
}
