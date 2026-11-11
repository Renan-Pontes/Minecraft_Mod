package com.renanpontes.isekaimahou.feature.cities.world;

import java.util.Locale;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

/**
 * Os três reinos da v0.9 (docs/V09_DESIGN.md §4). Cada um tem a sua capital ({@code capital_city} = Aldória,
 * {@code capital_sahra}, {@code capital_nordheim}) e a sua paleta; as cidades comuns pertencem ao reino da paleta
 * (planície → Aldória, deserto → Sahra, neve → Nordheim). O valor {@link #id} é o que vai no NBT
 * {@code Kingdom} do {@code city_heart} (gravado nos moldes por tools/gen/city_build.py).
 */
public enum CityKingdom implements StringRepresentable {
    ALDORIA("aldoria", CityPalette.PLAINS, "kingdom"),
    SAHRA("sahra", CityPalette.DESERT, "kingdom_sahra"),
    NORDHEIM("nordheim", CityPalette.SNOWY, "kingdom_nordheim");

    public static final Codec<CityKingdom> CODEC = StringRepresentable.fromEnum(CityKingdom::values);

    public final String id;
    /** Paleta da capital e das cidades do reino. */
    public final CityPalette palette;
    /** Id da facção do reino (módulo cities): Aldória mantém o "kingdom" da v0.8. */
    public final String factionId;

    CityKingdom(String id, CityPalette palette, String factionId) {
        this.id = id;
        this.palette = palette;
        this.factionId = factionId;
    }

    /** Reino de uma cidade comum pela paleta (também serve de reserva para corações de mundos antigos sem "Kingdom"). */
    public static CityKingdom forPalette(CityPalette palette) {
        return switch (palette) {
            case DESERT -> SAHRA;
            case SNOWY -> NORDHEIM;
            default -> ALDORIA;
        };
    }

    /**
     * Reino de um {@code city_heart} a partir do NBT dele: o {@code Kingdom} gravado (v0.9) quando existe; senão (coração
     * de mundo da v0.8) a capital é Aldória — a única capital que existia, mesmo que tenha saído com a paleta do bioma —
     * e a cidade comum é o reino da paleta.
     */
    public static CityKingdom forHeart(@Nullable String kingdom, @Nullable String cityType, @Nullable String palette) {
        if (kingdom != null && !kingdom.isEmpty()) return byId(kingdom);
        if ("capital".equals(cityType)) return ALDORIA;
        return forPalette(CityPalette.byId(palette == null ? "" : palette));
    }

    /** Pelo id ("aldoria", "sahra", "nordheim"); qualquer outro valor vira Aldória. */
    public static CityKingdom byId(String id) {
        String key = id == null ? "" : id.toLowerCase(Locale.ROOT);
        for (CityKingdom k : values()) {
            if (k.id.equals(key)) return k;
        }
        return ALDORIA;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
