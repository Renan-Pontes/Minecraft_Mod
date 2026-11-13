package com.renanpontes.isekaimahou.feature.forms_b.summon;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Ordem de uma invocação (Forja de Invocações). O número ({@link #ordinal()}) é gravado na criatura e lido por TEXTO
 * pelos módulos dos corpos: 0 seguir, 1 guardar um ponto, 2 caçar o alvo marcado, 3 ficar parado. Nunca reordene.
 */
public enum SummonOrder {
    FOLLOW("follow"),
    GUARD("guard"),
    HUNT("hunt"),
    STAY("stay");

    public final String id;

    SummonOrder(String id) {
        this.id = id;
    }

    public static SummonOrder byId(String id) {
        for (SummonOrder o : values()) if (o.id.equals(id)) return o;
        return null;
    }

    public static SummonOrder byIndex(int i) {
        SummonOrder[] v = values();
        return i >= 0 && i < v.length ? v[i] : FOLLOW;
    }

    /** A próxima ordem do ciclo (tecla de ordem / agachar + clicar na invocação). */
    public SummonOrder next() {
        return byIndex((ordinal() + 1) % values().length);
    }

    /** Guardar e Ficar prendem a criatura a um ponto. */
    public boolean anchored() {
        return this == GUARD || this == STAY;
    }

    public MutableComponent displayName() {
        return Component.translatable("summon_order.isekai_mahou." + id);
    }
}
