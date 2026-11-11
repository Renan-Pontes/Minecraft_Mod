package com.renanpontes.isekaimahou.feature.cities;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.api.Places;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * A cidade (lugar do tipo "city"/"capital") que contém uma posição. {@code Places.at} devolve o MENOR lugar que
 * contém a posição — um lugar menor dentro da cidade (criado por comando ou por outro módulo, ex.: um bairro) faria
 * os moradores não se ligarem à cidade, os baús deixarem de contar como crime e os guardas ignorarem monstros.
 */
public final class CityPlaces {
    public static boolean isCity(@Nullable Places.Place p) {
        return p != null && ("city".equals(p.type()) || "capital".equals(p.type()));
    }

    @Nullable
    public static Places.Place cityAt(ServerLevel level, BlockPos pos) {
        Places.Place here = Places.at(level, pos);
        if (here == null) return null;   // nenhum lugar contém a posição
        if (isCity(here)) return here;
        for (Places.Place p : Places.all(level)) {
            if (isCity(p) && p.dim() == level.dimension() && p.contains(pos)) return p;
        }
        return null;
    }

    private CityPlaces() {}
}
