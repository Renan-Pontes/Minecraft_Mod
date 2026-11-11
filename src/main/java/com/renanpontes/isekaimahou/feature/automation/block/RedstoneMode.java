package com.renanpontes.isekaimahou.feature.automation.block;

/** Como o Pedestal Rúnico obedece à redstone. */
public enum RedstoneMode {
    /** Ignora a redstone: conjura sempre. */
    ALWAYS("always"),
    /** Só conjura enquanto recebe sinal. */
    WITH_SIGNAL("with_signal"),
    /** Só conjura enquanto NÃO recebe sinal. */
    WITHOUT_SIGNAL("without_signal"),
    /** Conjura uma vez a cada pulso (subida do sinal). */
    PULSE("pulse");

    public final String id;

    RedstoneMode(String id) {
        this.id = id;
    }

    public String key() {
        return "gui.isekai_mahou.pedestal.redstone." + id;
    }

    public RedstoneMode cycle(int step) {
        RedstoneMode[] v = values();
        return v[Math.floorMod(ordinal() + step, v.length)];
    }

    public static RedstoneMode byIndex(int i) {
        RedstoneMode[] v = values();
        return i >= 0 && i < v.length ? v[i] : ALWAYS;
    }
}
