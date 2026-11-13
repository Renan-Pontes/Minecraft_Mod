package com.renanpontes.isekaimahou.feature.journal.bestiary;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicData;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;

/**
 * Bestiário: quais entidades do mod entram, em que categoria, e se o jogador já as "registrou".
 * Classe comum (sem cliente): o servidor usa para avisar "nova entrada" e a aba usa para listar.
 *
 * Registro de uma criatura: abate ({@code kill.<ns>.<id>}), participação na morte de um chefe ({@code boss.<id>})
 * ou encontro pacífico ({@code bestiary.<ns>.<id>}: clicar numa criatura). Aliados (NPCs, invocações, familiares)
 * já começam registrados.
 */
public final class Bestiary {
    public enum Category {
        WORLD(0x8FD16A), ELEMENTAL(0x7FD4FF), DIMENSION(0xC48CFF), BOSS(0xFF6A5A), ALLY(0xFFD86B), OTHER(0xA0A0A0);

        public final int color;

        Category(int color) {
            this.color = color;
        }

        public MutableComponent displayName() {
            String key = "screen.isekai_mahou.bestiary.category." + name().toLowerCase(java.util.Locale.ROOT);
            return Component.translatable(key);
        }

        /** Aliados já são conhecidos (não faz sentido exigir abate). */
        public boolean alwaysKnown() {
            return this == ALLY;
        }
    }

    /** Catálogo do contrato (id → categoria), na ordem de exibição dentro de cada categoria. */
    private static final Map<String, Category> CATALOG = new LinkedHashMap<>();

    static {
        put(Category.WORLD, "slime", "fire_slime", "water_slime", "stone_slime", "wind_slime", "poison_jelly",
                "goblin", "goblin_archer", "goblin_shaman", "goblin_wolf_rider", "hobgoblin",
                "kobold", "orc", "orc_berserker", "ogre", "troll", "lizardman", "werewolf", "cultist",
                "dire_wolf", "harpy", "basilisk", "mimic", "gargoyle", "fairy",
                "griffin", "sky_ray", "pegasus", "wyvern");
        put(Category.ELEMENTAL, "salamander", "ember_imp", "undine", "tide_serpent", "moss_golem", "crystal_mole", "sylph",
                "storm_hawk", "lumen_sprite", "solar_sentinel", "creeping_shade", "void_bat", "wandering_spirit", "arcane_golem");
        put(Category.DIMENSION, "greater_fire_elemental", "greater_water_elemental", "greater_earth_elemental",
                "greater_air_elemental", "lesser_phoenix", "storm_wyvern", "void_crawler", "watcher_eye", "young_kraken",
                "angel_sentinel", "cherub", "crystal_golem",
                // v1.0: Mundo Oco e Reino das Fadas (hueco_mundo)
                "hollow_crawler", "hollow_brute", "hollow_wraith", "menos_giant", "arrancar", "fey_pixie", "bramble_hound");
        put(Category.BOSS, "king_slime", "goblin_king", "orc_lord", "minotaur",
                "renegade_archmage", "corrupted_witch", "imprisoned_patron", "corrupted_ent", "lich",
                "patron_ember_lord", "patron_void_lady", "patron_drowned_king",
                "ignis", "tide_guardian", "primordial_golem", "zephyr", "abyss_sovereign", "seraph", "twilight_herald",
                "trial_avatar", "trial_sentinel",
                // v1.0: chefes dos reinos novos, o Dragão e o duelo do Pistoleiro
                "primordial_dragon", "sand_empress", "hollow_king", "thorn_queen", "gunslinger_outlaw");
        put(Category.ALLY, "wandering_mage", "guild_receptionist", "guild_master", "adventurer",
                "familiar_crow", "familiar_cat", "summoned_imp", "summoned_demon", "undead_servant", "bone_warrior",
                "animal_spirit", "summoned_salamander", "summoned_undine", "summoned_stone_golem", "summoned_sylph",
                "summoned_guardian_fairy", "living_shadow", "summoned_ent",
                // v1.0: companheiros e mestres das classes
                "ranger_wolf", "ranger_eagle", "ranger_steed", "steelball_mentor", "zanpakuto_spirit");
        put(Category.WORLD, "quincy_npc");

        // ---- v1.0 release: tudo o que entrou da v1.6 até as ondas 1–4 e ainda estava em "Outros" (ou fora do livro).
        // Montarias novas, os invasores da Queda de Meteoro, as Profundezas e os lacaios de chefe.
        put(Category.WORLD, "land_drake", "nightmare", "aurora_stag",
                "world_events_alien_larva", "world_events_alien_grunt", "world_events_alien_spitter",
                "depths_burrower", "depths_lost_delver", "vault_mimic", "hex_cat", "archmage_illusion", "false_hydra_head",
                "afterlife_reaper");
        // Cidadela Demoníaca (Nether), Biblioteca do Vazio (End) e as Câmaras de Provação.
        put(Category.DIMENSION, "demon_knight", "demon_succubus", "void_scribe", "star_wisp", "trial_guard");
        // Titãs, horrores, chefes de masmorra/torre, os Espada, a Sombra da Ascensão e o Arauto Estelar.
        put(Category.BOSS, "ascension_shadow", "swamp_hydra", "desert_basilisk", "ruin_colossus", "wyvern_king",
                "false_hydra", "mimic_king", "manticore", "depths_worm_king", "tower_kobold_lord", "demon_lord",
                "star_devourer", "grimmjow", "barragan", "ulquiorra", "world_events_alien_overseer",
                "trial_champion", "city_champion");
        // O Familiar, o povo das cidades (dos reinos e do jogador), os anões e as invocações lendárias.
        put(Category.ALLY, "familiar_spirit",
                "citizen", "city_guard", "city_merchant", "city_lord",
                "settlement_resident", "settlement_builder", "settlement_guard", "settlement_militia", "settlement_merchant", "settlement_tradesman",
                "dwarf_merchant", "dwarf_smith",
                "hellfire_imp", "twilight_clone", "dark_young", "kodama", "forest_god", "shadow_soldier", "spectral_colossus");
    }

    private static void put(Category c, String... ids) {
        for (String id : ids) CATALOG.put(id, c);
    }

    /** Ids do catálogo (para conferir as descrições). */
    public static List<String> catalogIds() {
        return List.copyOf(CATALOG.keySet());
    }

    public static Category category(EntityType<?> type) {
        ResourceLocation id = EntityType.getKey(type);
        return CATALOG.getOrDefault(id.getPath(), Category.OTHER);
    }

    /**
     * Entra no Bestiário? Do mod, viva (tem atributos), não é magia ({@code spell_*}/{@code domain_*}) nem o Espírito Guia.
     * Fora do catálogo, só criaturas de verdade (categoria de spawn diferente de MISC: projéteis, campos, sombras de ritual...).
     */
    public static boolean isCandidate(EntityType<?> type) {
        ResourceLocation id = EntityType.getKey(type);
        if (!id.getNamespace().equals(IsekaiMahou.MODID)) return false;
        String path = id.getPath();
        if (path.startsWith("spell_") || path.startsWith("domain_") || path.equals("guide_spirit")) return false;
        if (!DefaultAttributes.hasSupplier(type)) return false;
        return CATALOG.containsKey(path) || type.getCategory() != MobCategory.MISC;
    }

    /** Todas as entradas, por categoria e depois pela ordem do catálogo (extras por id). */
    public static List<EntityType<?>> entries() {
        List<EntityType<?>> list = new ArrayList<>();
        for (EntityType<?> t : BuiltInRegistries.ENTITY_TYPE) if (isCandidate(t)) list.add(t);
        List<String> order = new ArrayList<>(CATALOG.keySet());
        list.sort(Comparator.<EntityType<?>>comparingInt(t -> category(t).ordinal())
                .thenComparingInt(t -> {
                    int i = order.indexOf(EntityType.getKey(t).getPath());
                    return i < 0 ? Integer.MAX_VALUE : i;
                })
                .thenComparing(t -> EntityType.getKey(t).getPath()));
        return list;
    }

    public static String killKey(ResourceLocation id) {
        return "kill." + id.getNamespace() + "." + id.getPath();
    }

    public static String bossKey(ResourceLocation id) {
        return "boss." + id.getPath();
    }

    public static String encounterKey(ResourceLocation id) {
        return "bestiary." + id.getNamespace() + "." + id.getPath();
    }

    public static int kills(MagicData d, EntityType<?> type) {
        return d.counter(killKey(EntityType.getKey(type)));
    }

    public static int bossDefeats(MagicData d, EntityType<?> type) {
        return d.counter(bossKey(EntityType.getKey(type)));
    }

    /** Registrada por abate/chefe/encontro, sem contar a regra dos aliados. */
    public static boolean recorded(MagicData d, ResourceLocation id) {
        return d.counter(killKey(id)) > 0 || d.counter(bossKey(id)) > 0 || d.counter(encounterKey(id)) > 0;
    }

    public static boolean isRevealed(MagicData d, EntityType<?> type) {
        return category(type).alwaysKnown() || recorded(d, EntityType.getKey(type));
    }

    public static String descriptionKey(EntityType<?> type) {
        ResourceLocation id = EntityType.getKey(type);
        return "bestiary." + id.getNamespace() + "." + id.getPath() + ".desc";
    }

    private Bestiary() {}
}
