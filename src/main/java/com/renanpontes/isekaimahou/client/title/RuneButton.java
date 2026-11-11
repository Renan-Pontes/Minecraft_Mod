package com.renanpontes.isekaimahou.client.title;

import javax.annotation.Nullable;

import com.mojang.blaze3d.systems.RenderSystem;
import com.renanpontes.isekaimahou.client.MahouUi;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

/**
 * Botão do menu principal do Isekai Mahou: placa escura translúcida com borda dourada, losangos nas pontas que se
 * abrem ao passar o mouse, um brilho que atravessa a placa e um carrilhão de ametista suave ao clicar (além do clique
 * do vanilla). Pode ser só um ícone (sprite do vanilla, ex.: idioma/acessibilidade). Narração e foco pelo teclado iguais
 * aos do vanilla ({@link AbstractButton}).
 */
public class RuneButton extends AbstractButton {
    private final Runnable action;
    private final boolean primary;
    @Nullable
    private final ResourceLocation icon;
    private float hover;
    private long hoverStart;

    public RuneButton(int x, int y, int w, int h, Component message, Runnable action, boolean primary) {
        this(x, y, w, h, message, action, primary, null);
    }

    /** Botão só com ícone (a mensagem fica para a narração e a dica). */
    public RuneButton(int x, int y, int w, int h, Component message, Runnable action, boolean primary, @Nullable ResourceLocation icon) {
        super(x, y, w, h, message);
        this.action = action;
        this.primary = primary;
        this.icon = icon;
    }

    @Override
    public void onPress() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.6F, 0.6F));
        action.run();
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean hot = active && isHoveredOrFocused();
        if (hot && hover < 0.05F) hoverStart = Util.getMillis();
        hover = Mth.clamp(hover + (hot ? 0.18F : -0.12F), 0F, 1F);
        float a = Mth.clamp(alpha, 0F, 1F);
        if (a <= 0.02F) return;
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        int al = Math.round(a * 255);

        // Placa
        int top = active ? MahouUi.mix(0xFF1C1430, 0xFF3A2462, hover * 0.8F) : 0xFF16131C;
        int bottom = active ? MahouUi.mix(0xFF0B0812, 0xFF1A0F2C, hover * 0.8F) : 0xFF0C0B10;
        MahouUi.roundRect(g, x, y, w, h, MahouUi.alpha(top, Math.round(a * (primary ? 0xE6 : 0xD2))),
                MahouUi.alpha(bottom, Math.round(a * (primary ? 0xE6 : 0xD2))));
        int edge = !active ? 0xFF4A4552 : MahouUi.mix(primary ? MahouUi.GOLD_DIM : 0xFF6A5A86, MahouUi.GOLD, hover);
        MahouUi.roundOutline(g, x, y, w, h, MahouUi.alpha(edge, al));
        // filete interno de brilho no topo
        if (w > 12) g.fill(x + 3, y + 1, x + w - 3, y + 2, MahouUi.alpha(active ? 0xFFB98CFF : 0xFF3A3540, Math.round(a * (0x40 + 0x50 * hover))));

        // Brilho atravessando enquanto o mouse está em cima
        if (hover > 0.05F && w > 24) {
            float t = ((Util.getMillis() - hoverStart) % 1600L) / 1600F;
            int sx = x + Math.round(-16 + (w + 32) * t);
            for (int i = 0; i < 8; i++) {
                int px = sx + i * 2;
                int ga = Math.round((0x2A - Math.abs(i - 4) * 6) * hover * a);
                if (ga > 0 && px > x + 1 && px < x + w - 2) g.fill(px, y + 2, Math.min(x + w - 2, px + 2), y + h - 2, MahouUi.alpha(0xFFFFFFFF, ga));
            }
        }

        if (icon != null) {
            RenderSystem.enableBlend();
            g.setColor(1F, 1F, 1F, a);
            int s = Math.min(15, Math.min(w, h) - 4);
            g.blitSprite(icon, x + (w - s) / 2, y + (h - s) / 2, s, s);
            g.setColor(1F, 1F, 1F, 1F);
            RenderSystem.disableBlend();
            return;
        }

        // Losangos nas pontas (abrem ao passar o mouse)
        if (w >= 60) {
            int cy = y + h / 2;
            int spread = Math.round(3 * hover);
            int dc = MahouUi.alpha(active ? MahouUi.mix(MahouUi.GOLD_DIM, MahouUi.GOLD_PALE, hover) : 0xFF4A4552, al);
            MahouUi.diamond(g, x + 7 + spread, cy, 2, dc);
            MahouUi.diamond(g, x + w - 8 - spread, cy, 2, dc);
        }

        // Texto
        Font font = Minecraft.getInstance().font;
        int color = !active ? 0xFF7A7484 : MahouUi.mix(primary ? MahouUi.GOLD_PALE : MahouUi.TEXT, 0xFFFFFFFF, hover);
        int textAlpha = Math.max(5, al);
        // centralizado; rola se não couber
        renderScrollingString(g, font, getMessage(), x + 14, y, x + w - 14, y + h, MahouUi.alpha(color, textAlpha));
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
