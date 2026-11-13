package com.renanpontes.isekaimahou.feature.gear_plus;

import java.util.Locale;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/** Textos compartilhados do módulo (servidor e cliente). */
public final class GearText {
    /** "+8% Poder Mágico" / "+4 Vida Máxima" (o nome vem da tradução do próprio atributo). */
    public static MutableComponent attribute(Holder<Attribute> attribute, AttributeModifier.Operation op, double amount) {
        boolean percent = op != AttributeModifier.Operation.ADD_VALUE;
        String value = fmt(percent ? amount * 100 : amount) + (percent ? "%" : "");
        return Component.translatable("gear_plus.isekai_mahou.bonus_line", value,
                Component.translatable(attribute.value().getDescriptionId()));
    }

    /** 15.000000000000002 vira "15"; 7.5 continua "7.5". */
    public static String fmt(double v) {
        double r = Math.round(v * 10.0) / 10.0;
        return r == Math.rint(r) ? String.valueOf((long) r) : String.format(Locale.ROOT, "%.1f", r);
    }

    /** "12 min" / "45 s" a partir de ticks. */
    public static MutableComponent time(long ticks) {
        long seconds = Math.max(0, ticks) / 20;
        return seconds >= 60
                ? Component.translatable("gear_plus.isekai_mahou.time.minutes", (seconds + 59) / 60)
                : Component.translatable("gear_plus.isekai_mahou.time.seconds", seconds);
    }

    private GearText() {}
}
