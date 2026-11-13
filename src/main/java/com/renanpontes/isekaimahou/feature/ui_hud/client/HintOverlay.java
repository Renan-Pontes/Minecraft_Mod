package com.renanpontes.isekaimahou.feature.ui_hud.client;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.renanpontes.isekaimahou.client.hud.HudLayout;
import com.renanpontes.isekaimahou.client.notify.NoticeLog;
import com.renanpontes.isekaimahou.core.MahouConfig;
import com.renanpontes.isekaimahou.feature.ui_hud.HintPayloads;

import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Dicas de primeira vez na tela (cliente) — "tem que ser simples pra não explodir na tela do cara":
 * <ul>
 *   <li>UMA linha discreta (no máximo três, se o texto for longo) acima da barra de itens, sem moldura chamativa;</li>
 *   <li>uma por vez, com um bom intervalo entre duas ({@link #GAP_MS}); fila curta — o que não couber volta outro dia;</li>
 *   <li>some sozinha; espera telas, diálogos e conjurações terminarem antes de aparecer;</li>
 *   <li>só quando aparece de verdade o servidor é avisado ({@link HintPayloads.Seen}) e ela não repete mais;</li>
 *   <li>fica guardada na aba "Avisos" do menu (B) para quem quiser reler.</li>
 * </ul>
 * A opção "Dicas de primeira vez" desliga tudo (as dicas não são gastas: aparecem quando a opção voltar).
 */
public final class HintOverlay implements LayeredDraw.Layer {
    private static final int MAX_QUEUE = 3;
    private static final long FADE_MS = 450L;
    private static final long BASE_LIFE_MS = 6000L;
    private static final long PER_CHAR_MS = 45L;
    private static final long MAX_LIFE_MS = 11000L;
    /** Intervalo mínimo entre o fim de uma dica e o começo da próxima. */
    private static final long GAP_MS = 25_000L;
    /** Depois de entrar no mundo: nada de dica na cara logo de saída. */
    private static final long LOGIN_QUIET_MS = 10_000L;
    private static final int MAX_LINES = 3;
    private static final int TEXT_COLOR = 0xE8E4F4;
    private static final int LABEL_COLOR = 0xE8C56A;

    private record Pending(String id, Component text, boolean force) {}

    private static final Deque<Pending> QUEUE = new ArrayDeque<>();
    /** Dicas "client.*" já pedidas ao servidor nesta sessão. */
    private static final Set<String> REQUESTED = new HashSet<>();
    private static Pending current;
    /** Tempo de exibição já contado da dica atual (para de contar enquanto ela está escondida). */
    private static long shownMs;
    private static long lastFrame;
    private static long nextAllowedAt;
    private static List<FormattedCharSequence> lines = List.of();
    private static int linesWidth = -1;

    // =====================================================================================
    //  Entrada
    // =====================================================================================
    /** Pacote do servidor. */
    public static void receive(HintPayloads.Show p) {
        if (!p.force() && !MahouConfig.hintsEnabled()) return;
        if (current != null && current.id().equals(p.id())) return;
        for (Pending q : QUEUE) if (q.id().equals(p.id())) return;
        // Fila cheia: a dica é descartada sem ser gasta (o servidor tenta de novo numa próxima sessão)
        if (!p.force() && QUEUE.size() >= MAX_QUEUE) return;
        Component text = Component.translatable("hint.isekai_mahou." + p.id(), p.args().toArray());
        QUEUE.add(new Pending(p.id(), text, p.force()));
    }

    /**
     * Cliente: algo que só o cliente percebe (uma tela aberta, uma tecla) pede uma dica. O id precisa começar com
     * {@code client.}; o servidor confere se o jogador já viu. Texto: {@code hint.isekai_mahou.client.<...>}.
     */
    public static void request(String id) {
        Minecraft mc = Minecraft.getInstance();
        if (id == null || !id.startsWith("client.") || mc.getConnection() == null || !MahouConfig.hintsEnabled()) return;
        if (REQUESTED.add(id)) PacketDistributor.sendToServer(new HintPayloads.Request(id));
    }

    static void onLogin() {
        clear();
        nextAllowedAt = Util.getMillis() + LOGIN_QUIET_MS;
    }

    static void clear() {
        QUEUE.clear();
        REQUESTED.clear();
        current = null;
        shownMs = 0;
        lastFrame = 0;
        lines = List.of();
        linesWidth = -1;
    }

    // =====================================================================================
    //  Desenho
    // =====================================================================================
    @Override
    public void render(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        long now = Util.getMillis();
        long frameMs = lastFrame == 0 ? 0 : Mth.clamp(now - lastFrame, 0L, 100L);
        lastFrame = now;
        if (mc.player == null) return;
        if (current == null && QUEUE.isEmpty()) return;

        // Hora ruim (tela aberta, diálogo, conjurando, F1): a dica espera — e a que já está na tela se esconde sem
        // gastar o tempo dela
        boolean busy = mc.options.hideGui || mc.screen != null || !mc.player.isAlive()
                || HudLayout.lastDrawn(HudLayout.Region.DIALOGUE) != null;
        if (current == null) {
            if (busy || HudState.casting || now < nextAllowedAt) return;
            start(mc, QUEUE.poll());
            if (current == null) return;
        }
        if (busy) return;

        shownMs += frameMs;
        long life = Mth.clamp(BASE_LIFE_MS + PER_CHAR_MS * current.text().getString().length(), BASE_LIFE_MS, MAX_LIFE_MS);
        if (shownMs >= life) {
            nextAllowedAt = now + (current.force() ? 600L : GAP_MS);
            current = null;
            return;
        }
        float alpha = Math.min(1F, shownMs / (float) FADE_MS) * Math.min(1F, (life - shownMs) / (float) FADE_MS);
        draw(g, mc.font, alpha);
    }

    private static void start(Minecraft mc, Pending p) {
        if (p == null) return;
        // Desligaram as dicas com esta na fila: joga fora sem gastar
        if (!p.force() && !MahouConfig.hintsEnabled()) return;
        current = p;
        shownMs = 0;
        linesWidth = -1;
        if (!p.force() && mc.getConnection() != null) PacketDistributor.sendToServer(new HintPayloads.Seen(p.id()));
        NoticeLog.add(label().append(p.text()), NoticeLog.Kind.INFO);
        // Já está na tela: não conta como aviso "novo" na aba Avisos
        NoticeLog.markOneRead();
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.3F, 0.35F));
    }

    private static MutableComponent label() {
        return Component.translatable("hint.isekai_mahou.label").withColor(LABEL_COLOR);
    }

    private static void draw(GuiGraphics g, Font font, float alpha) {
        int gw = g.guiWidth(), gh = g.guiHeight();
        int maxW = Math.max(120, Math.min(320, gw - 60));
        if (linesWidth != maxW) {
            List<FormattedCharSequence> split = font.split(label().append(current.text().copy().withColor(TEXT_COLOR)), maxW);
            lines = split.size() > MAX_LINES ? split.subList(0, MAX_LINES) : split;
            linesWidth = maxW;
        }
        if (lines.isEmpty()) return;
        int w = 0;
        for (FormattedCharSequence l : lines) w = Math.max(w, font.width(l));
        int h = lines.size() * 10;
        // Acima da linha de mensagens da barra de ação (que fica em altura-72): as duas nunca se cobrem
        int bottom = gh - 84;
        int top = Math.max(4, bottom - h);
        int cx = gw / 2;
        int a = Mth.clamp(Math.round(alpha * 255F), 0, 255);
        if (a < 8) return;
        // Faixa escura bem suave, que some nas pontas (sem moldura: é só uma linha de texto legível)
        int bg = Math.round(alpha * 120F) << 24;
        int x0 = cx - w / 2 - 6, x1 = cx + w / 2 + 6;
        g.fill(x0, top - 3, x1, top + h + 1, bg);
        g.fill(x0 - 2, top - 2, x0, top + h, bg >>> 1 & 0x7F000000);
        g.fill(x1, top - 2, x1 + 2, top + h, bg >>> 1 & 0x7F000000);
        // Fio dourado embaixo: marca que é uma dica sem chamar atenção
        g.fill(cx - w / 4, top + h + 1, cx + w / 4, top + h + 2, (Math.round(alpha * 150F) << 24) | LABEL_COLOR);
        int y = top;
        for (FormattedCharSequence l : lines) {
            g.drawString(font, l, cx - font.width(l) / 2, y, (a << 24) | 0xFFFFFF, true);
            y += 10;
        }
    }
}
