package com.renanpontes.isekaimahou.feature.world_v09;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * As estruturas do world_v09 (ids iguais aos de {@code data/isekai_mahou/worldgen/structure}).
 * {@code ground} = altura da primeira camada de ar no molde (o chão onde se pisa), para o centro do lugar.
 * Gerado e conferido junto com {@code tools/gen/world_v09.py} (mesmos números).
 */
public enum V09Site {
    HYDRA_LAIR("hydra_lair", Kind.LAIR, 0x6FBF4A, 2),
    WYVERN_PEAK("wyvern_peak", Kind.LAIR, 0x7FB8FF, 15),
    BASILISK_TOMB("basilisk_tomb", Kind.LAIR, 0xE8B850, 17),
    COLOSSUS_RUINS("colossus_ruins", Kind.LAIR, 0xB8B2A2, 1),
    ELEMENTAL_GATE("elemental_gate", Kind.PORTAL, 0xFFB347, 1),
    ABYSSAL_CHASM("abyssal_chasm", Kind.PORTAL, 0x9A5CFF, 31),
    CELESTIAL_STAIRWAY("celestial_stairway", Kind.PORTAL, 0xFFF1A8, 1),
    TWILIGHT_RUPTURE("twilight_rupture", Kind.PORTAL, 0xD070FF, 21),
    ASCENSION_SANCTUM("ascension_sanctum", Kind.SANCTUM, 0xA98BFF, 1);

    /** Tipo de lugar ({@code Places.Place.type}) e se é zona segura. */
    public enum Kind {
        LAIR("boss_lair", false), PORTAL("portal_site", false), SANCTUM("sanctum", true);

        public final String placeType;
        public final boolean safe;

        Kind(String placeType, boolean safe) {
            this.placeType = placeType;
            this.safe = safe;
        }
    }

    public final String id;
    public final Kind kind;
    public final int color;
    public final int ground;

    V09Site(String id, Kind kind, int color, int ground) {
        this.id = id;
        this.kind = kind;
        this.color = color;
        this.ground = ground;
    }

    public ResourceKey<Structure> key() {
        return ResourceKey.create(Registries.STRUCTURE, IsekaiMahou.id(id));
    }

    /** Nome do lugar ("Covil da Hidra"), o mesmo do letreiro. */
    public Component displayName() {
        String key = "structure.isekai_mahou." + id;
        return Component.translatable(key);
    }

    /** Dica curta mostrada ao entrar (como ativar o portal / o que espera no covil). */
    public Component hint() {
        String key = "message.isekai_mahou.world_v09.hint." + id;
        return Component.translatable(key);
    }

    @Nullable
    public static V09Site byId(String id) {
        for (V09Site s : values()) if (s.id.equals(id)) return s;
        return null;
    }
}
