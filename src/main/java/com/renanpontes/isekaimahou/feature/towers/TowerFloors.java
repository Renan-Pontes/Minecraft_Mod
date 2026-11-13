package com.renanpontes.isekaimahou.feature.towers;

import java.util.List;
import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;

/**
 * Quem aparece em cada torre. Tudo por id em texto (criaturas de outros módulos); se faltar, cai num mob vanilla.
 * Níveis: 1 (andares 1–2), 2 (3–4), 3 (5–7). O guardião do pináculo depende da variante da torre
 * (pedra, gelo, areia e, desde a v1.0, floresta, submersa e flutuante).
 */
public final class TowerFloors {
    private static final Map<String, List<List<String>>> MOBS = Map.of(
            "stone", List.of(
                    List.of("goblin", "goblin_archer", "kobold", "minecraft:zombie", "minecraft:skeleton"),
                    List.of("hobgoblin", "orc", "goblin_shaman", "kobold", "minecraft:skeleton"),
                    List.of("orc_berserker", "ogre", "werewolf", "troll", "hobgoblin")),
            "frost", List.of(
                    List.of("minecraft:stray", "water_slime", "wind_slime", "minecraft:skeleton"),
                    List.of("minecraft:stray", "werewolf", "harpy", "crystal_mole"),
                    List.of("crystal_golem", "troll", "werewolf", "greater_water_elemental")),
            "sand", List.of(
                    List.of("minecraft:husk", "goblin", "kobold", "fire_slime"),
                    List.of("minecraft:husk", "lizardman", "salamander", "goblin_archer"),
                    List.of("lizardman", "ogre", "gargoyle", "greater_earth_elemental")),
            // v1.0: variantes novas
            "forest", List.of(
                    List.of("goblin", "goblin_archer", "poison_jelly", "kobold", "minecraft:spider"),
                    List.of("moss_golem", "goblin_shaman", "hobgoblin", "harpy", "minecraft:cave_spider"),
                    List.of("troll", "werewolf", "moss_golem", "ogre", "orc_berserker")),
            "sunken", List.of(
                    List.of("minecraft:drowned", "water_slime", "kobold", "minecraft:zombie"),
                    List.of("lizardman", "undine", "water_slime", "minecraft:drowned"),
                    List.of("lizardman", "greater_water_elemental", "troll", "undine")),
            "floating", List.of(
                    List.of("wind_slime", "harpy", "lumen_sprite", "minecraft:skeleton"),
                    List.of("harpy", "sylph", "gargoyle", "storm_hawk"),
                    List.of("gargoyle", "angel_sentinel", "greater_air_elemental", "solar_sentinel")));

    /** Guardiões possíveis do pináculo (sorteado ao despertar). */
    private static final Map<String, List<String>> GUARDIANS = Map.of(
            "stone", List.of("tower_kobold_lord"),
            "frost", List.of("tower_kobold_lord", "wyvern_king"),
            "sand", List.of("tower_kobold_lord", "desert_basilisk"),
            "forest", List.of("tower_kobold_lord"),
            "sunken", List.of("tower_kobold_lord"),
            "floating", List.of("tower_kobold_lord", "wyvern_king"));

    public static int tier(int floor) {
        return floor <= 1 ? 1 : floor <= 3 ? 2 : 3;
    }

    public static EntityType<?> pickMob(String variant, int tier, RandomSource random) {
        List<String> list = MOBS.getOrDefault(variant, MOBS.get("stone")).get(Math.max(0, Math.min(2, tier - 1)));
        for (int i = 0; i < 4; i++) {
            EntityType<?> type = byId(list.get(random.nextInt(list.size())));
            if (type != null) return type;
        }
        return switch (tier) {
            case 1 -> switch (variant) {
                case "frost" -> EntityType.STRAY;
                case "sand" -> EntityType.HUSK;
                case "sunken" -> EntityType.DROWNED;
                case "forest" -> EntityType.SPIDER;
                default -> EntityType.ZOMBIE;
            };
            case 2 -> EntityType.SKELETON;
            default -> EntityType.VINDICATOR;
        };
    }

    public static EntityType<?> pickGuardian(String variant, RandomSource random) {
        List<String> list = GUARDIANS.getOrDefault(variant, GUARDIANS.get("stone"));
        EntityType<?> type = byId(list.get(random.nextInt(list.size())));
        return type != null ? type : TwRegistries.KOBOLD_LORD.get();
    }

    private static EntityType<?> byId(String id) {
        ResourceLocation rl = id.contains(":") ? ResourceLocation.parse(id) : ResourceLocation.fromNamespaceAndPath("isekai_mahou", id);
        return BuiltInRegistries.ENTITY_TYPE.getOptional(rl).orElse(null);
    }

    private TowerFloors() {}
}
