package com.renanpontes.isekaimahou.feature.renown.client;

import com.renanpontes.isekaimahou.feature.renown.RenownNetwork.Timer;

import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;

/**
 * Cronômetro da luta de chefe: um relógio pequeno ao lado da barra do chefe (ou no topo, se o chefe não tem barra),
 * com o melhor tempo do jogador depois da barra ("1:23.4 / 2:10.0"). Ao vencer, o tempo final fica alguns segundos —
 * dourado com ★ se foi recorde pessoal. Nada pisca, nada cresce: é só um número.
 */
final class FightTimerHud {
    private static final int TEXT = 0xFFD8D8D8, DIM = 0xFF8C8C8C, GOLD = 0xFFFFD86B, CLOCK = 0xFFB8B8B8;
    /** Quanto tempo (ticks) o tempo final fica na tela. */
    private static final int SHOW_FINAL = 160;
    /** Luta sem notícias do servidor por tanto tempo (ticks): o cronômetro some sozinho (rede caiu, mudou de dimensão). */
    private static final int MAX_RUN = 20 * 60 * 60;

    private static boolean active, finished;
    private static int ticks, personalBest, hideIn;
    /** Y da primeira barra de chefe do quadro e quando foi vista (ms). */
    private static int barY = -1;
    private static long barSeen;
    private static boolean barThisFrame;

    private FightTimerHud() {}

    static void onPayload(Timer t) {
        switch (t.state()) {
            case Timer.RUNNING -> {
                active = true;
                finished = false;
                ticks = t.ticks();
                personalBest = t.personalBest();
            }
            case Timer.FINISHED -> {
                active = true;
                finished = true;
                ticks = t.ticks();
                personalBest = t.personalBest();
                hideIn = SHOW_FINAL;
            }
            default -> clear();
        }
    }

    static void tick() {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            clear();
            return;
        }
        if (mc.isPaused()) return;
        if (finished) {
            if (--hideIn <= 0) clear();
        } else if (++ticks > MAX_RUN) {
            clear();
        }
    }

    static void clear() {
        active = false;
        finished = false;
        ticks = 0;
        personalBest = 0;
        barY = -1;
    }

    /** A primeira barra de chefe de cada quadro diz onde o relógio fica. */
    static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress e) {
        if (barThisFrame) return;
        barThisFrame = true;
        barY = e.getY();
        barSeen = Util.getMillis();
    }

    static void render(GuiGraphics g, DeltaTracker delta) {
        barThisFrame = false;
        if (!active || !com.renanpontes.isekaimahou.core.MahouConfig.safe(com.renanpontes.isekaimahou.core.MahouConfig.SHOW_FIGHT_TIMER)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) return;
        Font font = mc.font;
        String time = format(ticks);
        // Recorde pessoal: primeira vitória válida (0) ou tempo menor; -1 = a luta não valeu para recorde
        boolean record = finished && personalBest >= 0 && (personalBest == 0 || ticks < personalBest);
        String best = !finished && personalBest > 0 ? " / " + format(personalBest) : record ? " ★" : "";
        int w = 10 + font.width(time) + font.width(best);
        int sw = g.guiWidth();
        boolean bar = barY >= 0 && Util.getMillis() - barSeen < 400;
        int x, y;
        if (bar && sw / 2 + 91 + 6 + w <= sw - 2) {
            x = sw / 2 + 91 + 6;
            y = barY - 2;
        } else {
            // Sem barra (ou sem espaço ao lado): canto de cima, à direita
            x = sw - w - 4;
            y = bar ? barY - 2 : 4;
        }
        clock(g, x, y);
        int color = record ? GOLD : TEXT;
        g.drawString(font, time, x + 10, y, color, true);
        if (!best.isEmpty()) g.drawString(font, best, x + 10 + font.width(time), y, record ? GOLD : DIM, true);
    }

    /** Reloginho de 7×7 desenhado com retângulos (não depende de a fonte ter o símbolo). */
    private static void clock(GuiGraphics g, int x, int y) {
        g.fill(x + 2, y, x + 5, y + 1, CLOCK);
        g.fill(x + 2, y + 6, x + 5, y + 7, CLOCK);
        g.fill(x, y + 2, x + 1, y + 5, CLOCK);
        g.fill(x + 6, y + 2, x + 7, y + 5, CLOCK);
        g.fill(x + 1, y + 1, x + 2, y + 2, CLOCK);
        g.fill(x + 5, y + 1, x + 6, y + 2, CLOCK);
        g.fill(x + 1, y + 5, x + 2, y + 6, CLOCK);
        g.fill(x + 5, y + 5, x + 6, y + 6, CLOCK);
        // ponteiros
        g.fill(x + 3, y + 2, x + 4, y + 4, 0xFFFFFFFF);
        g.fill(x + 4, y + 3, x + 5, y + 4, 0xFFFFFFFF);
    }

    /** {@code m:ss.d} (décimos de segundo). */
    static String format(int t) {
        int tenths = Math.max(0, t) / 2;
        int sec = tenths / 10;
        return sec / 60 + ":" + (sec % 60 < 10 ? "0" : "") + sec % 60 + "." + tenths % 10;
    }
}
