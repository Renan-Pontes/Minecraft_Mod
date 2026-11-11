package com.renanpontes.isekaimahou.feature.class_knight;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Chaves de tradução montadas por partes ({@code <tipo>.isekai_mahou.class_knight.<partes>}), para textos que
 * dependem de um rank, grau ou espada (todas as combinações existem em src/main/lang/class_knight).
 */
public final class KnightText {
    public static String key(String kind, Object... parts) {
        StringBuilder b = new StringBuilder(kind).append(".isekai_mahou.class_knight");
        for (Object p : parts) b.append('.').append(p);
        return b.toString();
    }

    public static MutableComponent tr(String kind, Object... parts) {
        return Component.translatable(key(kind, parts));
    }

    /** Nome de um item do mod pelo id. */
    public static MutableComponent item(String id) {
        return Component.translatable(String.join(".", "item", "isekai_mahou", id));
    }

    private KnightText() {}
}
