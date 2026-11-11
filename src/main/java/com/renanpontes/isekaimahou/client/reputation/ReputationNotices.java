package com.renanpontes.isekaimahou.client.reputation;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.client.hud.HudLayout;
import com.renanpontes.isekaimahou.core.MahouConfig;
import com.renanpontes.isekaimahou.core.api.Reputation;
import com.renanpontes.isekaimahou.core.api.Reputation.Faction;
import com.renanpontes.isekaimahou.core.api.Reputation.Standing;
import com.renanpontes.isekaimahou.core.reputation.ReputationPayloads;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Avisos laterais "+15 Guilda dos Aventureiros". v1.0: ficam na região de avisos da ESQUERDA-CENTRO do
 * {@link HudLayout} (o canto superior direito é do minimapa e do rastreador) e entram deslizando pela esquerda.
 * Mudanças seguidas da mesma facção se juntam num aviso só; até 3 visíveis, o resto espera na fila.
 * Ocupam a região {@link HudLayout.Region#TOASTS} ({@link HudLayout#reserve}): os outros avisos (recompensas) vêm abaixo.
 * Desligável pela config {@code reputationToasts}.
 */
public final class ReputationNotices implements LayeredDraw.Layer {
    public static final String RESERVE_ID = "reputation_notices";
    private static final int MAX_VISIBLE = 3;
    private static final int MAX_QUEUE = 8;
    private static final int H = 24;
    private static final int GAP = 2;
    private static final long LIFE_MS = 4500;
    private static final long IN_MS = 240;
    private static final long OUT_MS = 300;
    /** Mudanças da mesma facção dentro desta janela somam no mesmo aviso. */
    private static final long MERGE_MS = 2500;

    private static final class Notice {
        final String faction;
        int delta;
        int value;
        Component reason;
        long born;
        long touched;
        boolean shown;

        Notice(String faction, int delta, int value, Component reason, long now) {
            this.faction = faction;
            this.delta = delta;
            this.value = value;
            this.reason = reason;
            this.born = now;
            this.touched = now;
        }

        /** Valor antes de TODAS as mudanças somadas neste aviso. */
        int before() { return value - delta; }

        boolean expired(long now) { return now - touched > LIFE_MS; }
    }

    private static final List<Notice> NOTICES = new ArrayList<>();
    /** Altura ocupada na coluna de avisos (animada para quem vem abaixo não "pular"). */
    private static float reserved;

    /** Pacote do servidor (thread do cliente). */
    public static void push(ReputationPayloads.Notify n) {
        if (!enabled() || n.delta() == 0) return;
        long now = Util.getMillis();
        Component reason = n.reason().orElse(null);
        for (Notice a : NOTICES) {
            if (a.faction.equals(n.faction()) && now - a.touched < MERGE_MS && !a.expired(now)) {
                a.delta += n.delta();
                a.value = n.value();
                if (reason != null) a.reason = reason;
                a.touched = now;
                return;
            }
        }
        NOTICES.add(new Notice(n.faction(), n.delta(), n.value(), reason, now));
        while (NOTICES.size() > MAX_QUEUE) NOTICES.remove(MAX_VISIBLE); // descarta os mais antigos da fila, nunca os visíveis
    }

    /** A cada tick do cliente: tira os vencidos e "segura o relógio" dos que ainda esperam na fila. */
    public static void tick() {
        long now = Util.getMillis();
        NOTICES.removeIf(n -> n.shown && n.expired(now));
        for (int i = 0; i < NOTICES.size(); i++) {
            Notice n = NOTICES.get(i);
            if (i < MAX_VISIBLE) {
                if (!n.shown) {
                    n.shown = true;
                    n.born = now;
                    n.touched = now;
                }
            } else {
                n.born = now;
                n.touched = now;
            }
        }
        float target = visibleCount() == 0 ? 0 : visibleCount() * (H + GAP) + 2;
        reserved += (target - reserved) * 0.35F;
        if (Math.abs(target - reserved) < 0.5F) reserved = target;
    }

    public static void clear() {
        NOTICES.clear();
        reserved = 0;
    }

    /**
     * Altura reservada no topo do rastreador de objetivos: desde a v1.0 os avisos ficam à esquerda, então nada
     * (mantido porque o {@code ReputationCoreClient} ainda registra a reserva).
     */
    static int reservedHeight() {
        return 0;
    }

    /** Altura ocupada na região de avisos da esquerda ({@link HudLayout.Region#TOASTS}). */
    public static int columnHeight() {
        return Math.round(reserved);
    }

    private static int visibleCount() {
        int c = 0;
        for (Notice n : NOTICES) if (n.shown) c++;
        return Math.min(MAX_VISIBLE, c);
    }

    private static boolean enabled() {
        try {
            return MahouConfig.REPUTATION_TOASTS.get();
        } catch (RuntimeException e) {
            return MahouConfig.REPUTATION_TOASTS.getDefault();
        }
    }

    // =====================================================================================
    //  Desenho
    // =====================================================================================
    @Override
    public void render(GuiGraphics g, DeltaTracker dt) {
        if (NOTICES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) return;
        if (mc.screen != null && !(mc.screen instanceof ChatScreen)) return;
        Font font = mc.font;
        HudLayout.Rect area = HudLayout.toasts();
        int w = area.w();
        int x0 = area.x();
        // abaixo de quem reservou a coluna antes
        int y = area.y() + HudLayout.reservedBefore(HudLayout.Region.TOASTS, RESERVE_ID);
        int top = y;
        long now = Util.getMillis();
        int drawn = 0;
        for (Notice n : NOTICES) {
            if (!n.shown || drawn >= MAX_VISIBLE) continue;
            Faction f = Reputation.faction(n.faction);
            if (f == null) continue;
            float slide = slide(n, now);
            int x = x0 - Math.round(slide * (w + x0 + 8));
            drawNotice(g, font, f, n, x, y, w, now);
            y += H + GAP;
            drawn++;
        }
        if (drawn > 0) HudLayout.drawn(HudLayout.Region.TOASTS, new HudLayout.Rect(x0, top, w, y - top));
    }

    /** 1 = fora da tela (esquerda), 0 = no lugar. */
    private static float slide(Notice n, long now) {
        long age = now - n.born;
        if (age < IN_MS) {
            float t = age / (float) IN_MS;
            float e = 1F - (1F - t) * (1F - t) * (1F - t);
            return 1F - e;
        }
        long left = LIFE_MS - (now - n.touched);
        if (left < OUT_MS) {
            float t = 1F - Math.max(0, left) / (float) OUT_MS;
            return t * t;
        }
        return 0F;
    }

    private static void drawNotice(GuiGraphics g, Font font, Faction f, Notice n, int x, int y, int w, long now) {
        Standing before = Standing.of(n.before());
        Standing after = Standing.of(n.value);
        boolean crossed = before != after;
        boolean up = n.delta > 0;
        int accent = f.argb();

        g.fillGradient(x, y, x + w, y + H, 0xD0181226, 0xD00C0A14);
        g.fill(x, y, x + 2, y + H, accent);
        // pulso ao mudar de patamar: borda na cor do novo patamar
        if (crossed) {
            float pulse = 0.5F + 0.5F * (float) Math.sin((now - n.touched) / 140.0);
            g.renderOutline(x, y, w, H, MahouUi.alpha(after.argb(), 0x60 + Math.round(0x9F * pulse)));
        } else {
            g.fill(x + 2, y + H - 1, x + w, y + H, MahouUi.alpha(accent, 0x50));
        }
        FactionArt.emblem(g, f, x + 4, y + 4, 16);

        int tx = x + 24, right = x + w - 4;
        MutableComponent delta = Component.literal((up ? "+" : "") + n.delta)
                .withStyle(ChatFormatting.BOLD).withColor(up ? 0x7CFF8A : 0xFF6B6B);
        Component line1 = Component.empty().append(delta).append(" ").append(f.name().withColor(MahouUi.lighten(accent, 0.25F) & 0xFFFFFF));
        g.drawString(font, MahouUi.fit(font, line1, right - tx), tx, y + 3, MahouUi.TEXT, true);

        MutableComponent line2;
        if (crossed) {
            line2 = Component.translatable(up ? "screen.isekai_mahou.reputation.notice.up" : "screen.isekai_mahou.reputation.notice.down",
                    after.displayName());
        } else {
            line2 = Component.empty().append(after.displayName()).append(Component.literal(" · " + n.value).withColor(0xC8C0D8));
            if (n.reason != null) line2.append(Component.literal(" · ").withColor(0x8A82A0)).append(n.reason.copy().withColor(0x8A82A0));
        }
        int maxW = Math.round((right - tx) / 0.75F);
        g.pose().pushPose();
        g.pose().translate(tx, y + 13, 0);
        g.pose().scale(0.75F, 0.75F, 1F);
        g.drawString(font, MahouUi.fit(font, line2, maxW), 0, 0, MahouUi.TEXT_DIM, false);
        g.pose().popPose();

        MahouUi.bar(g, tx, y + H - 4, right - tx, 2, fraction(n.value), after.argb());
    }

    /** Fração percorrida dentro do patamar do valor. */
    static float fraction(int value) {
        Standing s = Standing.of(value);
        return (value - s.min + 1) / (float) (s.max() - s.min + 1);
    }
}
