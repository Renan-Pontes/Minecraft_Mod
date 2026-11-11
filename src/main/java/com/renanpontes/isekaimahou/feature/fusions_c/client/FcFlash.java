package com.renanpontes.isekaimahou.feature.fusions_c.client;

import com.renanpontes.isekaimahou.feature.fusions_c.FcNet;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/** Clarão de tela das Misturas C (Supernova, Canhão Elétrico, Zero Absoluto): a tela fica da cor e desvanece. */
final class FcFlash {
    private static int rgb = 0xFFFFFF;
    private static float strength;
    private static int total = 1;
    private static int left;

    static void onFlash(FcNet.Flash p) {
        // Um clarão mais forte por cima de um mais fraco ganha; um fraco não apaga um forte em andamento
        float now = current(0);
        if (p.strength() < now) return;
        rgb = p.rgb() & 0xFFFFFF;
        strength = Mth.clamp(p.strength(), 0F, 1F);
        total = Math.max(1, p.ticks());
        left = total;
    }

    static void tick() {
        if (left > 0) left--;
    }

    static void reset() {
        left = 0;
    }

    private static float current(float pt) {
        if (left <= 0) return 0F;
        float t = Mth.clamp((left - pt) / total, 0F, 1F);
        return strength * t * t;
    }

    static void render(GuiGraphics g, DeltaTracker delta) {
        if (Minecraft.getInstance().options.hideGui && left <= 0) return;
        float f = current(delta.getGameTimeDeltaPartialTick(false));
        if (f < 0.01F) return;
        int a = Mth.clamp((int) (f * 235), 0, 255);
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), (a << 24) | rgb);
    }

    private FcFlash() {}
}
