package com.renanpontes.isekaimahou.client.notify;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import net.minecraft.Util;
import net.minecraft.network.chat.Component;

/**
 * Registro de AVISOS do mod (cliente, v1.1): tudo o que saiu do chat ({@link ChatRouter}), os toasts de recompensa
 * ({@code HudToasts}) e as faixas de sistema ficam guardados aqui — os últimos {@link #MAX} — e aparecem na aba
 * "Avisos" do menu (B). Avisos iguais seguidos (até {@link #MERGE_MS}) viram uma linha só com "×N".
 * Só na thread principal do cliente.
 */
public final class NoticeLog {
    public static final int MAX = 100;
    private static final long MERGE_MS = 10_000L;

    /** Tipo do aviso (cor e ícone na lista). */
    public enum Kind { INFO, REWARD, WARNING, IMPORTANT, MENU }

    public static final class Entry {
        public final Component text;
        public final Kind kind;
        public final long realTime;
        long lastMillis;
        int count = 1;

        Entry(Component text, Kind kind) {
            this.text = text;
            this.kind = kind;
            this.realTime = System.currentTimeMillis();
            this.lastMillis = Util.getMillis();
        }

        public int count() { return count; }
        public long lastMillis() { return lastMillis; }
    }

    private static final Deque<Entry> ENTRIES = new ArrayDeque<>();
    private static int unread;
    private static int version;

    private NoticeLog() {}

    /** Guarda um aviso (o mais novo fica no topo da lista). */
    public static void add(Component text, Kind kind) {
        if (text == null) return;
        String plain = text.getString().strip();
        if (plain.isEmpty()) return;
        long now = Util.getMillis();
        Entry first = ENTRIES.peekFirst();
        if (first != null && now - first.lastMillis <= MERGE_MS && first.text.getString().strip().equals(plain)) {
            first.count++;
            first.lastMillis = now;
        } else {
            ENTRIES.addFirst(new Entry(text.copy(), kind));
            while (ENTRIES.size() > MAX) ENTRIES.removeLast();
        }
        unread++;
        version++;
    }

    /** Título + subtítulo numa linha só ("Título — subtítulo"). */
    public static void add(Component title, Component subtitle, Kind kind) {
        if (subtitle == null || subtitle.getString().isBlank()) {
            add(title, kind);
            return;
        }
        add(Component.empty().append(title).append(Component.literal(" — ")).append(subtitle), kind);
    }

    public static List<Entry> entries() {
        return new ArrayList<>(ENTRIES);
    }

    public static int size() { return ENTRIES.size(); }

    /** Avisos novos desde a última vez que a aba "Avisos" foi aberta. */
    public static int unread() { return Math.min(unread, 99); }

    /** O último aviso já foi visto (ficou no chat): não conta como novo. */
    public static void markOneRead() {
        if (unread > 0) unread--;
    }

    public static void markRead() {
        unread = 0;
    }

    /** Muda a cada aviso novo (telas abertas sabem quando redesenhar a lista). */
    public static int version() { return version; }

    public static void clear() {
        ENTRIES.clear();
        unread = 0;
        version++;
    }
}
