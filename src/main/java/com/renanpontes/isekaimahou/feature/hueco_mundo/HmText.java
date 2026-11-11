package com.renanpontes.isekaimahou.feature.hueco_mundo;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Texto traduzido montado por partes ("message.isekai_mahou.hueco_mundo" + reino + "need_night"). */
public final class HmText {

    public static MutableComponent of(String... parts) {
        return Component.translatable(String.join(".", parts));
    }

    private HmText() {}
}
