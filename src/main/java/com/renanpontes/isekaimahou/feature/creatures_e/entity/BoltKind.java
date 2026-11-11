package com.renanpontes.isekaimahou.feature.creatures_e.entity;

/** Tipos de projetil das criaturas (visual, gravidade e efeito no impacto). */
public enum BoltKind {
    /** Bola de magma do Elemental de Fogo: incendeia e respinga fogo. */
    MAGMA(0.0, 0.9F),
    /** Dardo de chamas da Fenix: persegue levemente o alvo. */
    PHOENIX(0.0, 0.7F),
    /** Jato d'agua: encharca e empurra. */
    WATER(0.0, 0.85F),
    /** Pedregulho do Elemental de Terra: arco com gravidade e impacto em area. */
    BOULDER(0.045, 1.0F),
    /** Rajada de vento: arremessa o alvo para cima. */
    GUST(0.0, 0.8F),
    /** Lanca de luz do Anjo Sentinela: deixa Exposto. */
    LIGHT_SPEAR(0.0, 1.0F),
    /** Estilhaco do Golem de Cristal. */
    CRYSTAL_SHARD(0.012, 0.6F),
    /** Fagulha de luz do Querubim: cega por um instante. */
    LIGHT_ORB(0.0, 0.5F);

    public final double gravity;
    /** Escala visual no renderizador. */
    public final float size;

    BoltKind(double gravity, float size) {
        this.gravity = gravity;
        this.size = size;
    }

    public static BoltKind byId(int id) {
        BoltKind[] v = values();
        return id >= 0 && id < v.length ? v[id] : MAGMA;
    }
}
