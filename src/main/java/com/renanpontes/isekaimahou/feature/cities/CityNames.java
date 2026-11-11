package com.renanpontes.isekaimahou.feature.cities;

import com.renanpontes.isekaimahou.feature.cities.world.CityKingdom;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;

/**
 * Nomes de fantasia das cidades: escolhidos pela semente do mundo + posição (a mesma cidade tem sempre o mesmo nome).
 * Cada reino tem a sua lista (v0.9): Aldória mantém os 24 nomes da v0.8 ({@code place.isekai_mahou.cities.city.<n>}),
 * Sahra e Nordheim têm 16 cada ({@code place.isekai_mahou.cities.city.<reino>.<n>}). As capitais se chamam Aldória,
 * Sahra e Nordheim. Corações de mundos da v0.8 ("nomes antigos") continuam com o nome que já tinham.
 */
public final class CityNames {
    public static final int CITY_NAMES = 24;
    public static final int KINGDOM_CITY_NAMES = 16;

    /** Nome de uma cidade do reino (ver {@link com.renanpontes.isekaimahou.feature.cities.block.CityHeartBlockEntity#cityName}). */
    public static MutableComponent cityName(ServerLevel level, BlockPos heart, boolean capital, CityKingdom kingdom, boolean legacyNames) {
        if (capital) return CityRealm.shortName(kingdom);
        long h = level.getSeed() ^ ((long) heart.getX() * 341873128712L) ^ ((long) heart.getZ() * 132897987541L);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        if (legacyNames || kingdom == CityKingdom.ALDORIA) {
            return CityLines.tr("place.isekai_mahou.cities.city." + (int) Math.floorMod(h, (long) CITY_NAMES));
        }
        int idx = (int) Math.floorMod(h, (long) KINGDOM_CITY_NAMES);
        return CityLines.tr("place.isekai_mahou.cities.city." + kingdom.id + "." + idx);
    }

    private CityNames() {}
}
