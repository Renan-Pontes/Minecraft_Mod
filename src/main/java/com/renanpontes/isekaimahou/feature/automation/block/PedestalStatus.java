package com.renanpontes.isekaimahou.feature.automation.block;

/**
 * Estado do Pedestal Rúnico mostrado na tela (texto + dica) e no mundo (anel girando ou parado).
 * Traduções: gui.isekai_mahou.pedestal.status.&lt;id&gt; e gui.isekai_mahou.pedestal.hint.&lt;id&gt;.
 */
public enum PedestalStatus {
    ACTIVE("active", 0x6BE38A, true),
    WAITING_PULSE("waiting_pulse", 0x8FD3FF, true),
    PAUSED("paused", 0xC9C2D8, false),
    NO_SCROLL("no_scroll", 0xF2D06B, false),
    EMPTY_SCROLL("empty_scroll", 0xF2D06B, false),
    INVALID("invalid", 0xFF6A6A, false),
    ULTIMATE("ultimate", 0xFF6A6A, false),
    NO_BATTERY("no_battery", 0xFF9F5A, false),
    NO_MANA("no_mana", 0xFF9F5A, false),
    CHUNK_LIMIT("chunk_limit", 0xFF6A6A, false),
    DISABLED("disabled", 0xFF6A6A, false),
    NO_OWNER("no_owner", 0xFF6A6A, false),
    /** A magia deu erro de código ao conjurar: pausado até trocar o pergaminho (detalhes no log). */
    ERROR("error", 0xFF6A6A, false);

    public final String id;
    public final int rgb;
    /** Pronto para conjurar (o anel gira). */
    public final boolean running;

    PedestalStatus(String id, int rgb, boolean running) {
        this.id = id;
        this.rgb = rgb;
        this.running = running;
    }

    public String key() {
        return "gui.isekai_mahou.pedestal.status." + id;
    }

    public String hintKey() {
        return "gui.isekai_mahou.pedestal.hint." + id;
    }

    public static PedestalStatus byIndex(int i) {
        PedestalStatus[] v = values();
        return i >= 0 && i < v.length ? v[i] : NO_SCROLL;
    }
}
