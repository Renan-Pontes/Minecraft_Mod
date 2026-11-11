package com.renanpontes.isekaimahou.feature.cities;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.cities.world.CityKingdom;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Os reinos vistos pela VIDA das cidades (v0.9, docs/V09_DESIGN.md §5 "cities"): nome curto (o da capital), cores do
 * letreiro e do cristal do coração, chaves de tradução com variante por reino e a ligação facção ↔ reino.
 * <ul>
 *   <li>Aldória ({@code aldoria}) usa a facção {@code kingdom} da v0.8 e as chaves SEM sufixo (mundos salvos e
 *       textos antigos continuam iguais);</li>
 *   <li>Sahra ({@code kingdom_sahra}) e Nordheim ({@code kingdom_nordheim}) usam {@code <chave>.<reino>}.</li>
 * </ul>
 */
public final class CityRealm {
    /** Reino de uma facção ({@code kingdom}, {@code kingdom_sahra}, {@code kingdom_nordheim}); null se não for de reino. */
    @Nullable
    public static CityKingdom byFaction(@Nullable String faction) {
        if (faction == null || faction.isEmpty()) return null;
        for (CityKingdom k : CityKingdom.values()) {
            if (k.factionId.equals(faction)) return k;
        }
        return null;
    }

    /** A facção é a de um reino? */
    public static boolean isKingdomFaction(@Nullable String faction) {
        return byFaction(faction) != null;
    }

    /** Chave com a variante do reino: Aldória = a própria chave; os outros = {@code <chave>.<id>}. */
    public static String key(String base, CityKingdom k) {
        return k == CityKingdom.ALDORIA ? base : base + "." + k.id;
    }

    /** "Aldória", "Sahra", "Nordheim" (o nome da capital é o nome curto do reino). */
    public static MutableComponent shortName(CityKingdom k) {
        return Component.translatable(key("place.isekai_mahou.cities.capital", k));
    }

    /** Cor RGB do letreiro do lugar. */
    public static int placeColor(CityKingdom k, boolean capital) {
        return switch (k) {
            case SAHRA -> capital ? 0xFFB347 : 0xF0A050;
            case NORDHEIM -> capital ? 0xD8F0FF : 0x8EC8F0;
            default -> capital ? 0xFFD86B : 0x6FA8FF;
        };
    }

    /** Cor RGB do reino na interface (avisos, banners, toasts). */
    public static int color(CityKingdom k) {
        return switch (k) {
            case SAHRA -> 0xE07B2A;
            case NORDHEIM -> 0x7FC4E8;
            default -> 0x3F6FD8;
        };
    }

    private CityRealm() {}
}
