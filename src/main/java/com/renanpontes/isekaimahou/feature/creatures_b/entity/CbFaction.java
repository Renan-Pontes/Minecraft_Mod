package com.renanpontes.isekaimahou.feature.creatures_b.entity;

/**
 * Facções dos humanoides: membros da mesma facção são aliados (não se ferem com ataques em área,
 * respondem ao chamado uns dos outros e recebem os buffs do Grito de Guerra).
 */
public enum CbFaction {
    KOBOLD,
    /** Orcs, berserkers, o Lorde Orc e os ogros (que servem aos orcs). */
    HORDE,
    TROLL,
    LIZARDFOLK,
    MINOTAUR,
    /** Lobisomens e os lobos selvagens que eles chamam. */
    BEAST,
    CULT
}
