package com.renanpontes.isekaimahou.client.onboard;

import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.core.onboard.OnboardPayloads;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * Clarão de viagem: na partida a tela acende rápido na cor do destino; na chegada começa clara e se dissolve (esconde
 * o carregamento dos chunks do destino).
 */
final class TravelFlash {
    private static final int DEPART_TICKS = 8, ARRIVE_TICKS = 26;
    private static int rgb = 0xFFFFFF;
    private static int left;
    private static int total;
    private static boolean arrival;

    static void show(OnboardPayloads.TravelFx p) {
        rgb = p.rgb() & 0xFFFFFF;
        arrival = p.arrival();
        total = arrival ? ARRIVE_TICKS : DEPART_TICKS;
        left = total;
    }

    static void tick() {
        if (left > 0) left--;
    }

    static void render(GuiGraphics g, DeltaTracker dt) {
        if (left <= 0) return;
        float f = Mth.clamp((left - dt.getGameTimeDeltaPartialTick(false)) / total, 0F, 1F);
        // Partida: sobe até forte; chegada: começa forte e desce
        float a = arrival ? f * f * 0.85F : (1F - f) * 0.7F;
        int color = MahouUi.mix(0xFFFFFFFF, 0xFF000000 | rgb, 0.45F);
        g.pose().pushPose();
        g.pose().translate(0, 0, 500);
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), MahouUi.alpha(color, (int) (a * 255)));
        g.pose().popPose();
    }

    static void clear() {
        left = 0;
    }

    private TravelFlash() {}
}
