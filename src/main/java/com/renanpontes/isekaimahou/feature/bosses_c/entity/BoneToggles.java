package com.renanpontes.isekaimahou.feature.bosses_c.entity;

import java.util.List;

/**
 * Entidades cujo modelo tem ossos que aparecem/somem conforme o estado (escudo do Arquimago, vassoura da Bruxa,
 * flores corrompidas do Ent). Só lê dados sincronizados, então funciona igual no cliente; o renderer consulta isto.
 */
public interface BoneToggles {
    /** Ossos controlados por {@link #isBoneVisible}. */
    default List<String> toggledBones() {
        return List.of();
    }

    default boolean isBoneVisible(String bone) {
        return true;
    }
}
