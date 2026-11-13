package com.renanpontes.isekaimahou.feature.lore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Índice da <b>Enciclopédia</b> (v1.0) — "a lore do mundo, pra quem quiser saber": categorias e entradas, na ordem em
 * que aparecem. O texto mora nas traduções ({@code lore.isekai_mahou.entry.<id>} e {@code .text}), geradas por
 * {@code tools/gen/lore_lang.py} a partir de {@code docs/LORE.md}. Tudo é aberto desde o começo, menos as
 * <b>Verdades Ocultas</b>: cada uma abre quando o jogador completa a coleção de fragmentos dela
 * ({@link LoreFragments.LoreSet#truth}).
 * <p>
 * Só tipos comuns (sem classe de cliente): o servidor também consulta (comandos, API).
 */
public final class LoreBook {
    /** Categoria das Verdades Ocultas. */
    public static final String TRUTHS = "truths";

    /**
     * @param icon id de um item (texto) para o ícone; se não existir, o cliente usa um livro
     */
    public record Entry(String id, String category, String icon) {
        public MutableComponent title() {
            return Component.translatable("lore." + IsekaiMahou.MODID + ".entry." + id);
        }

        public String textKey() {
            return "lore." + IsekaiMahou.MODID + ".entry." + id + ".text";
        }

        /** Verdade Oculta (precisa de uma coleção completa)? */
        public boolean secret() {
            return TRUTHS.equals(category);
        }
    }

    public record Category(String id, int color, List<Entry> entries) {
        public MutableComponent title() {
            return Component.translatable("lore." + IsekaiMahou.MODID + ".cat." + id);
        }
    }

    private static final Map<String, Category> CATEGORIES = new LinkedHashMap<>();
    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();

    static {
        cat("world", 0xB98CFF,
                "world_asteria", "minecraft:filled_map",
                "world_mana", "isekai_mahou:mana_crystal",
                "world_glyphs", "isekai_mahou:glyph_scroll",
                "world_elements", "isekai_mahou:essence_arcane",
                "world_circles", "minecraft:nether_star",
                "world_moons", "minecraft:clock",
                "world_summoned", "minecraft:ender_eye",
                "world_souls", "minecraft:soul_lantern");
        cat("history", 0xD8B45A,
                "history_dragon", "minecraft:dragon_head",
                "history_velmora", "minecraft:chiseled_stone_bricks",
                "history_sages", "minecraft:enchanted_book",
                "history_sealing", "minecraft:chain",
                "history_rupture", "minecraft:crying_obsidian",
                "history_ashen", "minecraft:campfire",
                "history_demon_king", "isekai_mahou:demon_lord_sigil",
                "history_now", "minecraft:compass");
        cat("realms", 0x6FA8FF,
                "realm_aldoria", "minecraft:wheat",
                "realm_sahra", "minecraft:sandstone",
                "realm_nordheim", "minecraft:snow_block",
                "faction_guild", "isekai_mahou:adventurer_card",
                "faction_academy", "minecraft:lectern",
                "faction_church", "minecraft:sunflower",
                "faction_underworld", "minecraft:tripwire_hook",
                "faction_wildfolk", "minecraft:red_mushroom",
                "faction_horde", "minecraft:iron_axe",
                "faction_dwarves", "minecraft:iron_pickaxe");
        cat("dimensions", 0x7FE0D0,
                "dim_nether", "minecraft:netherrack",
                "dim_end", "minecraft:end_stone",
                "dim_elemental_plane", "isekai_mahou:essence_fire",
                "dim_abyss", "isekai_mahou:essence_shadow",
                "dim_celestial", "isekai_mahou:essence_light",
                "dim_fey", "minecraft:glow_berries",
                "dim_hollow", "minecraft:bone_block",
                "dim_twilight", "minecraft:amethyst_shard",
                "dim_trials", "minecraft:iron_sword",
                "dim_inner", "minecraft:cherry_sapling",
                "dim_endless_abyss", "minecraft:sculk",
                "dim_home", "isekai_mahou:home_key");
        cat("races", 0xF0A070,
                "race_human", "minecraft:bread",
                "race_elf", "minecraft:bow",
                "race_dwarf", "minecraft:anvil",
                "race_half_orc", "minecraft:stone_axe",
                "race_beastfolk", "minecraft:rabbit_foot",
                "race_dragonborn", "minecraft:fire_charge",
                "race_demon", "minecraft:blaze_powder",
                "race_angel", "minecraft:feather",
                "race_phantom", "minecraft:phantom_membrane",
                "race_arachnid", "minecraft:cobweb",
                "race_slime", "minecraft:slime_ball");
        cat("classes", 0x86E08A,
                "class_mage", "minecraft:book",
                "class_witch", "minecraft:cauldron",
                "class_warlock", "minecraft:candle",
                "class_druid", "minecraft:oak_sapling",
                "class_necromancer", "minecraft:skeleton_skull",
                "class_smith", "minecraft:smithing_table",
                "class_rogue", "minecraft:leather",
                "class_knight", "minecraft:iron_sword",
                "class_enchanter", "minecraft:enchanting_table",
                "class_shinigami", "minecraft:netherite_sword",
                "class_quincy", "minecraft:spectral_arrow",
                "class_gunslinger", "minecraft:gunpowder",
                "class_ranger", "minecraft:arrow",
                "class_gambler", "minecraft:gold_nugget",
                "class_steelball", "minecraft:iron_nugget");
        cat("bosses", 0xFF7A6B,
                "boss_horde", "minecraft:golden_helmet",
                "boss_minotaur", "minecraft:goat_horn",
                "boss_dark_masters", "minecraft:wither_rose",
                "boss_horrors", "minecraft:chest",
                "boss_titans", "minecraft:turtle_scute",
                "boss_worm_king", "minecraft:amethyst_cluster",
                "boss_guardians", "isekai_mahou:guardian_core_fire",
                "boss_sovereign", "minecraft:wither_skeleton_skull",
                "boss_seraph", "minecraft:glowstone_dust",
                "boss_las_noches", "minecraft:bone",
                "boss_demon_lord", "minecraft:netherite_ingot",
                "boss_star_devourer", "minecraft:end_crystal",
                "boss_herald", "minecraft:echo_shard",
                "boss_dragon", "minecraft:dragon_egg");
        cat(TRUTHS, 0xFFF2B0,
                "truth_towers", "minecraft:stone_bricks",
                "truth_seventh", "minecraft:writable_book",
                "truth_king", "minecraft:golden_helmet",
                "truth_call", "minecraft:ender_pearl",
                "truth_beasts", "minecraft:sniffer_egg",
                "truth_moon", "minecraft:end_stone_bricks",
                "truth_dwarves", "minecraft:blast_furnace");
    }

    /** {@code idsAndIcons}: pares (id da entrada, id do item do ícone). */
    private static void cat(String id, int color, String... idsAndIcons) {
        List<Entry> list = new ArrayList<>();
        for (int i = 0; i + 1 < idsAndIcons.length; i += 2) {
            Entry e = new Entry(idsAndIcons[i], id, idsAndIcons[i + 1]);
            list.add(e);
            ENTRIES.put(e.id(), e);
        }
        CATEGORIES.put(id, new Category(id, color, List.copyOf(list)));
    }

    public static List<Category> categories() {
        return List.copyOf(CATEGORIES.values());
    }

    @Nullable
    public static Entry entry(String id) {
        return id == null ? null : ENTRIES.get(id);
    }

    @Nullable
    public static Category category(String id) {
        return id == null ? null : CATEGORIES.get(id);
    }

    private LoreBook() {}
}
