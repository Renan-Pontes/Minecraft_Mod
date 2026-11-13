package com.renanpontes.isekaimahou.feature.combat_feel.client;

import com.renanpontes.isekaimahou.client.fx.FxQuality;
import com.renanpontes.isekaimahou.core.MahouConfig;
import com.renanpontes.isekaimahou.feature.combat_feel.FeelConfig;
import com.renanpontes.isekaimahou.feature.combat_feel.FeelPayloads;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.TickRateManager;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * <b>Micro-pausa (hit-stop)</b> — só visual, só neste cliente: por 2 a 4 quadros o mundo na tela "segura" no impacto e
 * continua. Feita com o próprio relógio de ticks do cliente ({@link TickRateManager} do mundo do cliente, o mesmo que o
 * {@code /tick rate} do Minecraft usa): durante a pausa ele vai ao mínimo, então ticks e interpolação param juntos, sem
 * engasgo; no fim volta ao valor de antes. O servidor não sabe de nada e a câmera continua respondendo ao mouse.
 * <p>
 * O fim é contado em tempo REAL e checado a cada quadro (os ticks do cliente estão quase parados durante a pausa).
 * Proteções: nunca pausa com o jogo pausado, com o mundo já congelado/lento ({@code /tick}), nem duas vezes coladas;
 * se o mundo mudar no meio, só esquece; só devolve o relógio se ninguém mexeu nele no meio.
 * <p>
 * Junto vai um "soco" de câmera minúsculo (menos de 1 grau, ~0,15 s), multiplicado pela opção de tremor de tela.
 */
public final class HitStop {
    /** O mínimo que o relógio do Minecraft aceita (1 tick por segundo ≈ parado nos poucos quadros da pausa). */
    private static final float FROZEN_RATE = TickRateManager.MIN_TICKRATE;
    /** Duração (ms) por tipo, na ordem de {@link FeelPayloads}: crítico, levar pancada, pesado, aparo, explosão, finalização. */
    private static final int[] DURATION_MS = {38, 42, 55, 68, 72, 85};
    /** Força do soco de câmera (graus) por tipo. */
    private static final float[] KICK = {0.2F, 0.45F, 0.4F, 0.45F, 0.75F, 0.65F};
    private static final long MIN_GAP_MS = 170L;
    private static final long MAX_TOTAL_MS = 170L;
    private static final long KICK_MS = 160L;

    private static TickRateManager held;
    private static float heldRate;
    private static long startMs, endMs;
    private static long lastEndMs;

    private static long kickStart;
    private static float kickAmp;
    private static float kickSide = 1F;

    /** Pede uma micro-pausa do tipo {@code kind} (pacote do servidor, aparo local ou teste). */
    public static void trigger(byte kind) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused() || !FeelConfig.on(FeelConfig.HIT_STOP)) return;
        int k = Mth.clamp(kind, 0, DURATION_MS.length - 1);
        long now = Util.getMillis();
        long ms = Mth.clamp((long) DURATION_MS[k] * FeelConfig.of(FeelConfig.HIT_STOP_STRENGTH) / 100L, 10L, MAX_TOTAL_MS);
        if (held != null) {
            // Já pausado: um impacto mais forte só estica um pouco (com teto), nunca empilha
            endMs = Math.min(startMs + MAX_TOTAL_MS, Math.max(endMs, now + ms));
            kick(k, now);
            return;
        }
        if (now - lastEndMs < MIN_GAP_MS) return;
        TickRateManager clock = mc.level.tickRateManager();
        if (!clock.runsNormally() || clock.tickrate() < 10F) return;
        held = clock;
        heldRate = clock.tickrate();
        startMs = now;
        endMs = now + ms;
        clock.setTickRate(FROZEN_RATE);
        kick(k, now);
    }

    private static void kick(int k, long now) {
        float amp = KICK[k];
        if (now - kickStart < KICK_MS && amp <= kickAmp) return;
        kickAmp = amp;
        kickStart = now;
        kickSide = -kickSide;
    }

    /** A cada quadro e a cada tick: terminou? devolve o relógio. */
    static void update() {
        if (held == null) return;
        Minecraft mc = Minecraft.getInstance();
        long now = Util.getMillis();
        if (mc.level == null || mc.level.tickRateManager() != held) {
            // Trocou de mundo/desconectou: aquele relógio não existe mais para este cliente
            held = null;
            lastEndMs = now;
            return;
        }
        if (now >= endMs || mc.isPaused()) release(now);
    }

    private static void release(long now) {
        // Só devolve se o valor ainda é o nosso (o servidor pode ter mandado outro ritmo no meio)
        if (held.tickrate() == FROZEN_RATE) held.setTickRate(heldRate);
        held = null;
        lastEndMs = now;
    }

    /** Ao sair do mundo: nada fica preso. */
    static void reset() {
        if (held != null) release(Util.getMillis());
        kickAmp = 0F;
    }

    static void onCamera(ViewportEvent.ComputeCameraAngles e) {
        if (kickAmp <= 0F) return;
        long t = Util.getMillis() - kickStart;
        if (t < 0 || t >= KICK_MS) {
            kickAmp = 0F;
            return;
        }
        float fade = 1F - t / (float) KICK_MS;
        // Tremor NOVO: sempre multiplicado pela opção "tremor de tela" e pelo preset gráfico
        float k = kickAmp * fade * fade * MahouConfig.shakeScale() * FxQuality.shake();
        if (k < 0.01F) return;
        double phase = t * 0.085;
        e.setPitch(e.getPitch() + (float) Math.sin(phase) * k);
        e.setRoll(e.getRoll() + (float) Math.cos(phase * 1.3) * k * 0.6F * kickSide);
    }

    private HitStop() {}
}
