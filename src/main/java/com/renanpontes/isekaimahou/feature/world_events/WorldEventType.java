package com.renanpontes.isekaimahou.feature.world_events;

import java.util.Locale;

import javax.annotation.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Os eventos do mundo. Só UM acontece por vez, sempre no mundo normal (overworld).
 * {@code local} = tem um lugar (cratera, fendas); os outros valem para o céu inteiro.
 */
public enum WorldEventType {
    /** Queda de Meteoro: cratera pequena no ermo, invasores e minério estelar. */
    METEOR("meteor", 0xFF8A3A, 20 * 60 * 12, true),
    /** Lua de Sangue: mais monstros à noite, um pouco mais de loot e XP. Dura até amanhecer. */
    BLOOD_MOON("blood_moon", 0xD0202A, 20 * 60 * 10, false),
    /** Tempestade Arcana: magias de água e vento mais fortes. */
    ARCANE_STORM("arcane_storm", 0x6FB6FF, 20 * 60 * 5, false),
    /** Terremoto: tremor, fendas pequenas no ermo com minérios expostos. */
    EARTHQUAKE("earthquake", 0xC09A62, 20 * 45, true),
    /** Tempestade de Mana: toda magia mais forte, cristais de mana caem do céu. */
    MANA_STORM("mana_storm", 0xB98CFF, 20 * 60 * 4, false);

    public final String id;
    public final int color;
    /** Duração padrão (ticks). A Lua de Sangue acaba antes se amanhecer. */
    public final int duration;
    public final boolean local;

    WorldEventType(String id, int color, int duration, boolean local) {
        this.id = id;
        this.color = color;
        this.duration = duration;
        this.local = local;
    }

    public MutableComponent displayName() {
        return Component.translatable("world_event.isekai_mahou." + id);
    }

    /** Aviso curto de começo (uma linha). */
    public MutableComponent startText() {
        return Component.translatable("world_event.isekai_mahou." + id + ".start");
    }

    public MutableComponent endText() {
        return Component.translatable("world_event.isekai_mahou." + id + ".end");
    }

    /** Id da dica de primeira vez ({@code hint.isekai_mahou.world_events.<id>}). */
    public String hintId() {
        return "world_events." + id;
    }

    @Nullable
    public static WorldEventType byId(String id) {
        if (id == null) return null;
        String k = id.toLowerCase(Locale.ROOT);
        for (WorldEventType t : values()) if (t.id.equals(k)) return t;
        return null;
    }
}
