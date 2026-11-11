package com.renanpontes.isekaimahou.feature.origins_a.shape;

/**
 * Formas da Metamorfose (Druida). Cada forma é um efeito de status próprio ({@link ShapeshiftEffect}) com
 * atributos; o cliente desenha o jogador como o animal correspondente (manequim do vanilla).
 * {@code id} é usado nos ids do efeito/glifo ("wolf" → wolf_form) e no pacote de sincronização (ordinal + 1).
 */
public enum Shape {
    WOLF("wolf", 1800, 0x9C9C9C),
    BEAR("bear", 1200, 0xF2F2EC),
    FALCON("falcon", 1800, 0x8A7B66),
    DOLPHIN("dolphin", 2400, 0x6A9FD8),
    SPIDER("spider", 1800, 0x3B2F2F),
    BEAST("elemental_beast", 1200, 0xB98CFF);

    public final String id;
    /** Duração base em ticks (Estender/Encurtar ajustam). */
    public final int baseDuration;
    /** Cor do efeito de status. */
    public final int color;

    Shape(String id, int baseDuration, int color) {
        this.id = id;
        this.baseDuration = baseDuration;
        this.color = color;
    }

    public String effectId() {
        return id + "_form";
    }

    /** Índice no pacote (0 = forma humana). */
    public int netId() {
        return ordinal() + 1;
    }

    public static Shape byNetId(int id) {
        Shape[] v = values();
        return id >= 1 && id <= v.length ? v[id - 1] : null;
    }
}
