package com.renanpontes.isekaimahou.client.hud;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.client.notify.NoticeLog;

import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * Avisos de recompensa do núcleo ({@code core.api.Rewards.toast}) — v1.0: em vez do toast do vanilla (canto superior
 * direito, por cima do minimapa) ficam na região de avisos da ESQUERDA-CENTRO do {@link HudLayout}, abaixo dos avisos
 * de reputação. Mesma moldura arcana: borda dourada dupla, medalhão do ícone pulsando, reflexo de luz ao aparecer e o
 * carrilhão de ametista. Módulos podem usar {@link #push} direto (cliente).
 * <p>
 * v1.1 (menos poluição): <b>um aviso por vez</b> e fila curta. Avisos com o mesmo título em até {@link #MERGE_MS}
 * viram UM só com contador ("Missão concluída ×7" + os primeiros itens); uma rajada de títulos diferentes vira
 * "Novidades ×N". Com a fila cheia, o excesso se junta ao último da fila. Tudo vai também para o registro
 * {@link NoticeLog} (aba "Avisos" do menu B). Com outros esperando, o aviso da vez fica menos tempo.
 */
public final class HudToasts implements LayeredDraw.Layer {
    public static final String RESERVE_ID = "reward_toasts";
    private static final int MAX_VISIBLE = 1;
    /** Avisos esperando a vez (o resto se junta ao último deles). */
    private static final int MAX_WAITING = 2;
    private static final long MERGE_MS = 4000L;
    private static final int H = 32;
    private static final int GAP = 3;
    private static final long LIFE_MS = 5000;
    /** Com outros esperando, o atual fica só isto (e cede a vez). */
    private static final long BUSY_LIFE_MS = 2900;
    private static final long IN_MS = 260;
    private static final long OUT_MS = 320;
    private static final int LIST_ITEMS = 3;

    private static final class Toast {
        Component title, subtitle;
        final Component baseTitle;
        final String key;
        ItemStack icon;
        final int accent;
        long born;
        long updated;
        boolean shown;
        int count = 1;
        boolean mixed;
        final List<Component> items = new ArrayList<>();

        Toast(Component title, Component subtitle, ItemStack icon, int rgb) {
            this.title = title == null ? Component.empty() : title;
            this.baseTitle = this.title;
            this.subtitle = subtitle == null ? Component.empty() : subtitle;
            this.icon = icon == null ? ItemStack.EMPTY : icon.copy();
            this.accent = 0xFF000000 | (rgb & 0xFFFFFF);
            this.key = this.title.getString();
            this.updated = Util.getMillis();
            if (!this.subtitle.getString().isBlank()) items.add(this.subtitle);
        }

        /** Junta outro aviso a este: contador no título e os primeiros itens no subtítulo. */
        void merge(Component t, Component sub, ItemStack ic, boolean differentTitle) {
            count++;
            updated = Util.getMillis();
            Component item;
            if (differentTitle) {
                item = sub == null || sub.getString().isBlank() ? t
                        : Component.empty().append(t).append(Component.literal(": ")).append(sub);
                mixed = true;
            } else {
                item = sub;
            }
            if (item != null && !item.getString().isBlank()) items.add(item);
            if (icon.isEmpty() && ic != null && !ic.isEmpty()) icon = ic.copy();
            Component head = mixed ? Component.translatable("hud.isekai_mahou.notice.mixed") : baseTitle;
            title = Component.empty().append(head).append(Component.literal(" ×" + count));
            subtitle = list(items);
        }

        private static Component list(List<Component> items) {
            MutableComponent out = Component.empty();
            int n = Math.min(LIST_ITEMS, items.size());
            for (int i = 0; i < n; i++) {
                if (i > 0) out.append(Component.literal(", "));
                out.append(items.get(i));
            }
            if (items.size() > n) out.append(Component.literal(" +" + (items.size() - n)));
            return out;
        }
    }

    private static final List<Toast> TOASTS = new ArrayList<>();
    private static float reserved;
    private static long lastPush;

    /** Novo aviso (cliente, thread principal). */
    public static void push(Component title, Component subtitle, ItemStack icon, int rgb) {
        Component t0 = title == null ? Component.empty() : title;
        NoticeLog.add(t0, subtitle, NoticeLog.Kind.REWARD);
        long now = Util.getMillis();
        String key = t0.getString();
        boolean burst = now - lastPush <= MERGE_MS;
        lastPush = now;
        // 1) Mesmo título: junta com o que está na tela (se ainda é recente) ou com o que espera na fila
        for (Toast t : TOASTS) {
            if (t.mixed || !t.key.equals(key)) continue;
            if (!t.shown || now - t.updated <= MERGE_MS) {
                t.merge(t0, subtitle, icon, false);
                // o da tela fica mais um pouco para dar tempo de ler o contador
                if (t.shown) t.born = Math.max(t.born, now - 1500L);
                return;
            }
        }
        Toast fresh = new Toast(t0, subtitle, icon, rgb);
        int waiting = 0;
        Toast lastWaiting = null;
        for (Toast t : TOASTS) {
            if (!t.shown) {
                waiting++;
                lastWaiting = t;
            }
        }
        // 2) Fila cheia ou rajada de títulos diferentes: junta ao último da fila ("Novidades ×N")
        if (lastWaiting != null && (waiting >= MAX_WAITING || burst && now - lastWaiting.updated <= MERGE_MS)) {
            lastWaiting.merge(t0, subtitle, icon, !lastWaiting.key.equals(key));
            return;
        }
        TOASTS.add(fresh);
    }

    private static long life() {
        double mult = 1D;
        try {
            mult = Minecraft.getInstance().options.notificationDisplayTime().get();
        } catch (RuntimeException e) {
            // opções ainda não carregadas
        }
        return Math.round(LIFE_MS * Math.max(0.5D, mult));
    }

    /** Tempo do aviso da vez: menor quando há outros esperando. */
    private static long currentLife(long life) {
        for (Toast o : TOASTS) if (!o.shown) return Math.min(life, BUSY_LIFE_MS);
        return life;
    }

    /** A cada tick do cliente: tira os vencidos, mostra o próximo da fila, toca o som de quem apareceu. */
    public static void tick() {
        long now = Util.getMillis();
        long life = currentLife(life());
        TOASTS.removeIf(t -> t.shown && now - t.born > life);
        for (int i = 0; i < TOASTS.size() && i < MAX_VISIBLE; i++) {
            Toast t = TOASTS.get(i);
            if (!t.shown) {
                t.shown = true;
                t.born = now;
                t.updated = now;
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.4F, 1.0F));
            }
        }
        int visible = Math.min(MAX_VISIBLE, (int) TOASTS.stream().filter(t -> t.shown).count());
        float target = visible == 0 ? 0 : visible * (H + GAP) + 2;
        reserved += (target - reserved) * 0.35F;
        if (Math.abs(target - reserved) < 0.5F) reserved = target;
    }

    /** Altura ocupada na coluna de avisos ({@link HudLayout.Region#TOASTS}). */
    public static int columnHeight() {
        return Math.round(reserved);
    }

    public static void clear() {
        TOASTS.clear();
        reserved = 0;
        lastPush = 0;
    }

    @Override
    public void render(GuiGraphics g, DeltaTracker dt) {
        if (TOASTS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) return;
        if (mc.screen != null && !(mc.screen instanceof ChatScreen)) return;
        Font font = mc.font;
        HudLayout.Rect area = HudLayout.toasts();
        int w = Mth.clamp(area.w() + 20, 150, 200);
        int x0 = area.x();
        int y = area.y() + HudLayout.reservedBefore(HudLayout.Region.TOASTS, RESERVE_ID);
        long now = Util.getMillis();
        long life = currentLife(life());
        int drawn = 0;
        for (Toast t : TOASTS) {
            if (!t.shown || drawn >= MAX_VISIBLE) continue;
            float slide = slide(t, now, life);
            int x = x0 - Math.round(slide * (w + x0 + 8));
            draw(g, font, t, x, y, w, now - t.born);
            y += H + GAP;
            drawn++;
        }
    }

    /** 1 = fora da tela (esquerda), 0 = no lugar. */
    private static float slide(Toast t, long now, long life) {
        long age = now - t.born;
        if (age < IN_MS) {
            float k = age / (float) IN_MS;
            return (1F - k) * (1F - k) * (1F - k);
        }
        long left = life - age;
        if (left < OUT_MS) {
            float k = 1F - Math.max(0, left) / (float) OUT_MS;
            return k * k;
        }
        return 0F;
    }

    private static void draw(GuiGraphics g, Font font, Toast t, int x, int y, int w, long time) {
        int accent = t.accent;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        // Moldura
        g.fillGradient(0, 0, w, H, 0xF4201830, 0xF40C0914);
        g.renderOutline(0, 0, w, H, MahouUi.EDGE_DARK);
        g.renderOutline(1, 1, w - 2, H - 2, MahouUi.GOLD_DIM);
        g.renderOutline(3, 3, w - 6, H - 6, MahouUi.alpha(accent, 0x70));
        MahouUi.brackets(g, 1, 1, w - 2, H - 2, 5, MahouUi.GOLD);
        MahouUi.diamond(g, w - 8, H / 2, 2, MahouUi.alpha(accent, 0xC0));

        // Medalhão do ícone com brilho pulsante
        float pulse = 0.5F + 0.5F * Mth.sin(time / 260F);
        g.fill(5, 5, 27, 27, MahouUi.alpha(accent, (int) (40 + 50 * pulse)));
        MahouUi.roundRect(g, 6, 6, 20, 20, 0xFF2A2040, 0xFF100C18);
        MahouUi.roundOutline(g, 6, 6, 20, 20, accent);
        if (!t.icon.isEmpty()) g.renderFakeItem(t.icon, 8, 8);
        else MahouUi.diamond(g, 16, 16, 4, accent);

        // Textos
        int tx = 32, tw = w - tx - 12;
        g.drawString(font, MahouUi.fit(font, t.title, tw), tx, 7, MahouUi.lighten(accent, 0.25F), true);
        g.drawString(font, MahouUi.fit(font, t.subtitle, tw), tx, 18, MahouUi.TEXT, false);

        // Reflexo de luz atravessando ao aparecer
        if (time < 1400L) {
            float k = time / 1400F;
            int sx = (int) (-20 + (w + 40) * k);
            for (int i = 0; i < 6; i++) {
                int px = sx + i * 2;
                int a = 0x26 - Math.abs(i - 3) * 6;
                if (a > 0 && px > 1 && px < w - 2) g.fill(px, 2, Math.min(w - 2, px + 2), H - 2, MahouUi.alpha(0xFFFFFF, a));
            }
        }
        g.pose().popPose();
    }
}
