package com.renanpontes.isekaimahou.client.stack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.SpellStack;
import com.renanpontes.isekaimahou.core.stack.StackPayloads;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Cópia da Pilha no cliente: as conjurações HOSTIS pendentes perto do jogador (o servidor só manda essas).
 * Leitura pública ({@link #entries()}) para quem quiser desenhar a Pilha de outro jeito (ui_hud).
 */
public final class ClientStack {
    /** Ticks da animação de saída: contrariada (fica um tempo com "CONTRARIADA") e o resto. */
    static final int LEAVE_COUNTERED = 34;
    static final int LEAVE_NORMAL = 8;
    /** Se o servidor não avisar o fim, a linha some este tanto depois do tempo previsto. */
    private static final int STALE_AFTER_END = 60;

    /** Uma linha da Pilha. Tempos em ticks do cliente ({@link #ticks()}). */
    public static final class Entry {
        public final int id;
        public int casterId = -1;
        public Component casterName = Component.empty();
        public Component castName = Component.empty();
        @Nullable public Element element;
        public SpellStack.Tier tier = SpellStack.Tier.MINOR;
        public int total = 1;
        public double endTick;
        public boolean counterable;
        public boolean boss;
        public boolean player;
        public double addedTick;
        public double leaveTick = -1;
        public byte leaveReason;
        public boolean countered;

        Entry(int id) {
            this.id = id;
        }

        public boolean leaving() { return leaveTick >= 0; }

        /** Fração do tempo que ainda falta (1 → 0). */
        public float remainingFraction(double now) {
            return (float) Math.max(0, Math.min(1, (endTick - now) / Math.max(1, total)));
        }

        public double ticksLeft(double now) { return Math.max(0, endTick - now); }

        /** Nome do golpe, ou "Golpe" quando não tem nome próprio. */
        public Component spellName() {
            return castName.getString().isEmpty() ? Component.translatable("stack.isekai_mahou.attack") : castName;
        }
    }

    private static final Map<Integer, Entry> ENTRIES = new LinkedHashMap<>();
    private static long ticks;

    /** Ticks do cliente (param junto com a pausa do jogo). */
    public static long ticks() { return ticks; }

    /** Cópia das linhas (inclui as que estão saindo, com {@link Entry#leaving()}). */
    public static List<Entry> entries() {
        return Collections.unmodifiableList(new ArrayList<>(ENTRIES.values()));
    }

    /** Há alguma pendente hostil ativa por perto? */
    public static boolean any() {
        for (Entry e : ENTRIES.values()) if (!e.leaving()) return true;
        return false;
    }

    // ------------------------------------------------------------------ pacotes

    public static void onAdd(StackPayloads.Add p) {
        Entry e = ENTRIES.get(p.id());
        if (e == null || e.leaving()) {
            if (e == null) {
                e = new Entry(p.id());
                ENTRIES.put(p.id(), e);
            }
            e.addedTick = ticks;
            e.leaveTick = -1;
            e.countered = false;
        }
        e.casterId = p.casterId();
        e.casterName = p.casterName();
        e.castName = p.castName();
        Element[] els = Element.values();
        e.element = p.element() >= 0 && p.element() < els.length ? els[p.element()] : null;
        e.tier = SpellStack.Tier.byOrdinal(p.tier());
        e.total = Math.max(1, p.total());
        e.endTick = ticks + Math.max(0, p.remaining());
        e.counterable = (p.flags() & StackPayloads.FLAG_COUNTERABLE) != 0;
        e.boss = (p.flags() & StackPayloads.FLAG_BOSS) != 0;
        e.player = (p.flags() & StackPayloads.FLAG_PLAYER) != 0;
    }

    public static void onRemove(StackPayloads.Remove p) {
        Entry e = ENTRIES.get(p.id());
        if (e == null || e.leaving()) return;
        e.leaveTick = ticks;
        e.leaveReason = p.reason();
        if (p.reason() == StackPayloads.REASON_COUNTERED) e.countered = true;
    }

    public static void onCountered(StackPayloads.Countered p) {
        Entry e = ENTRIES.get(p.id());
        if (e != null) {
            e.countered = true;
            if (!e.leaving()) {
                e.leaveTick = ticks;
                e.leaveReason = StackPayloads.REASON_COUNTERED;
            }
        }
        StackBanner.trigger(p);
        CounterShatter.spawn(p);
    }

    // ------------------------------------------------------------------ ciclo

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused()) return;
        ticks++;
        Iterator<Entry> it = ENTRIES.values().iterator();
        while (it.hasNext()) {
            Entry e = it.next();
            if (e.leaving()) {
                int dur = e.countered ? LEAVE_COUNTERED : LEAVE_NORMAL;
                if (ticks - e.leaveTick > dur) it.remove();
            } else if (ticks > e.endTick + STALE_AFTER_END) {
                e.leaveTick = ticks;
                e.leaveReason = StackPayloads.REASON_RESOLVED;
            }
        }
    }

    static void clear() {
        ENTRIES.clear();
        ticks = 0;
    }

    private ClientStack() {}
}
