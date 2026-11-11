package com.renanpontes.isekaimahou.feature.bosses_g.entity;

import java.util.List;

/**
 * Entidades do módulo cujo modelo tem ossos que aparecem/somem conforme o estado (carne da Falsa Hidra só quando ela
 * emerge, língua do Rei Mímico, asas abertas da Manticora). Só lê dados sincronizados: o renderer consulta isto.
 */
public interface BgBones {
    default List<String> toggledBones() {
        return List.of();
    }

    default boolean isBoneVisible(String bone) {
        return true;
    }
}
