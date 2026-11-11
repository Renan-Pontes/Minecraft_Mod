package com.renanpontes.isekaimahou.feature.bosses_f.entity;

import java.util.List;

/** Ossos do modelo GeckoLib que a entidade esconde/mostra (cabeças da Hidra, brilho do olhar...). */
public interface BfBones {
    default List<String> toggledBones() {
        return List.of();
    }

    default boolean isBoneVisible(String bone) {
        return true;
    }
}
