package com.renanpontes.isekaimahou.feature.potions;

/** Vida (cura) ou Mana (restaura o recurso da classe: Mana, Vigor ou Foco — é o mesmo frasco). */
public enum PotionKind {
    HEALTH("health"),
    MANA("mana");

    public final String id;

    PotionKind(String id) {
        this.id = id;
    }
}
