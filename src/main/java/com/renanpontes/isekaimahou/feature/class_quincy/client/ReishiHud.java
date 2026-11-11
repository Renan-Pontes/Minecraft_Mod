package com.renanpontes.isekaimahou.feature.class_quincy.client;

import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_quincy.Quincy;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Reishi guardado: um arco discreto de pontos à direita da mira (só para Quincy com Reishi &gt; 0). Fica colado à
 * mira — fora das regiões fixas do HudLayout (cartão, minimapa, rastreador, Pilha).
 */
public final class ReishiHud {
    static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.player.isSpectator()) return;
        MagicData d = ClientMagic.data();
        if (d == null || d.path() != MagicPath.QUINCY) return;
        int stored = Quincy.reishi(d);
        if (stored <= 0) return;
        int cap = Quincy.reishiCap(d);
        int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2;
        float t = (mc.player.tickCount + delta.getGameTimeDeltaPartialTick(true)) * 0.15F;
        for (int i = 0; i < cap; i++) {
            double a = Math.toRadians(-55 + 110.0 * i / Math.max(1, cap - 1));
            int x = cx + (int) Math.round(Math.cos(a) * 13);
            int y = cy + (int) Math.round(Math.sin(a) * 13);
            boolean full = i < stored;
            int color;
            if (full) {
                int glow = (int) (200 + 55 * Math.sin(t + i * 0.5));
                color = (glow << 24) | (Quincy.REISHI_RGB & 0xFFFFFF);
            } else {
                color = 0x40FFFFFF;
            }
            g.fill(x, y, x + 2, y + 2, color);
            if (full && stored == cap) g.fill(x, y, x + 1, y + 1, 0xFFFFFFFF);
        }
    }

    private ReishiHud() {}
}
