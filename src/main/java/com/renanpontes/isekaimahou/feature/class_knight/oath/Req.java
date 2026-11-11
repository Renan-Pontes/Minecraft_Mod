package com.renanpontes.isekaimahou.feature.class_knight.oath;

import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.feature.class_knight.KnightCounters;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Um requisito de Juramento ou de despertar de espada, lido dos dados do jogador (vale no servidor e na cópia do
 * cliente, para a aba mostrar o progresso).
 *
 * @param kind   o que é medido
 * @param key    contador/flag (para {@link Kind#COUNTER}/{@link Kind#FLAG})
 * @param amount quanto é preciso
 * @param label  chave de tradução do texto ("%s/%s" recebe atual/necessário)
 */
public record Req(Kind kind, String key, int amount, String label) {
    public enum Kind { CIRCLE, OATH, COUNTER, FLAG }

    public static Req circle(int c) {
        return new Req(Kind.CIRCLE, "", c, "req.isekai_mahou.class_knight.circle");
    }

    public static Req oath(int rank) {
        return new Req(Kind.OATH, KnightCounters.OATH, rank, "req.isekai_mahou.class_knight.oath");
    }

    public static Req counter(String key, int amount, String label) {
        return new Req(Kind.COUNTER, key, amount, label);
    }

    public static Req flag(String key, String label) {
        return new Req(Kind.FLAG, key, 1, label);
    }

    /** Valor atual (flag: 0/1). */
    public int current(MagicData d) {
        if (d == null) return 0;
        return switch (kind) {
            case CIRCLE -> d.circle();
            case OATH -> d.counter(KnightCounters.OATH);
            case COUNTER -> d.counter(key);
            case FLAG -> d.hasFlag(key) ? 1 : 0;
        };
    }

    public boolean met(MagicData d) {
        return current(d) >= amount;
    }

    public float fraction(MagicData d) {
        return amount <= 0 ? 1F : Math.min(1F, current(d) / (float) amount);
    }

    /** "Círculo 3/5", "Golpes rúnicos 120/200"... */
    public MutableComponent text(MagicData d) {
        if (kind == Kind.FLAG) return Component.translatable(label);
        if (kind == Kind.OATH) return Component.translatable(label, KnightOaths.roman(amount));
        return Component.translatable(label, Math.min(current(d), amount), amount);
    }
}
