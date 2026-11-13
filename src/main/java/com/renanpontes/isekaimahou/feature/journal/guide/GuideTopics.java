package com.renanpontes.isekaimahou.feature.journal.guide;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

/**
 * "Onde Conseguir" (v1.0 release): o índice do Guia de Viagem que diz ONDE fica e COMO se consegue cada sistema do
 * jogo — a chave do Lar, os altares das capitais, veículos, gemas, o Familiar, os quadros, talentos, o Abismo...
 * <p>
 * Palavra do dono: <i>"o ideal era ser no caminho do jogo, nas progressões"</i> — em vez de uma dica em cada item, o
 * Diário responde num lugar só, com os assuntos que já servem para o nível do jogador no topo.
 * <p>
 * Classe comum: só ids. Textos em {@code src/main/lang/story} ({@code tools/gen/journal_story.py}):
 * {@code guide.isekai_mahou.<id>} (nome), {@code .where} (onde) e {@code .how} (como).
 */
public final class GuideTopics {
    /** Um grupo de assuntos (cabeçalho da lista): {@code guide.isekai_mahou.group.<id>}. */
    public record Group(String id, int color) {
        public MutableComponent title() {
            return Component.translatable("guide.isekai_mahou.group." + id);
        }
    }

    /**
     * Um assunto.
     *
     * @param icon     item mostrado na lista (se não existir neste jogo, um livro)
     * @param minLevel nível em que o assunto passa a servir (0 = desde o começo); abaixo dele aparece apagado, com o nível
     */
    public record Topic(String id, String group, ResourceLocation icon, int minLevel) {
        public String key() {
            return "guide.isekai_mahou." + id;
        }

        public MutableComponent title() {
            return Component.translatable(key());
        }

        public MutableComponent where() {
            return Component.translatable(key() + ".where");
        }

        public MutableComponent how() {
            return Component.translatable(key() + ".how");
        }
    }

    private static final List<Group> GROUPS = List.of(
            new Group("city", 0xFFD86B),
            new Group("town", 0x8FD16A),
            new Group("power", 0xB98CFF),
            new Group("combat", 0xFF7A6B),
            new Group("world", 0x7FD4FF));

    private static final List<Topic> TOPICS = new ArrayList<>();

    static {
        // ---- A sua cidade (a mecânica mais importante depois das magias)
        add("city_found", "city", "isekai_mahou:founding_charter", 0);
        add("city_build", "city", "minecraft:bricks", 0);
        add("city_happiness", "city", "minecraft:cake", 0);
        add("city_festival", "city", "minecraft:firework_rocket", 0);
        add("city_caravans", "city", "minecraft:chest", 0);
        add("city_defense", "city", "minecraft:shield", 0);
        add("city_magic", "city", "isekai_mahou:mana_crystal", 0);
        // ---- Nas cidades dos reinos
        add("home_key", "town", "isekai_mahou:home_key", 0);
        add("inn", "town", "minecraft:red_bed", 0);
        add("altars", "town", "isekai_mahou:new_beginnings_altar", 10);
        add("market_board", "town", "isekai_mahou:market_board", 5);
        add("renown_board", "town", "isekai_mahou:renown_board", 0);
        add("mounts", "town", "isekai_mahou:land_drake_reins", 0);
        add("vehicles", "town", "isekai_mahou:arcane_broom", 0);
        // ---- Poder e progresso
        add("talents", "power", "minecraft:experience_bottle", 2);
        add("glyph_mastery", "power", "isekai_mahou:glyph_scroll", 0);
        add("transformation", "power", "isekai_mahou:essence_arcane", 50);
        add("summons", "power", "isekai_mahou:spell_scroll", 30);
        add("familiar", "power", "isekai_mahou:bond_incense", 10);
        add("gems", "power", "isekai_mahou:gem_ruby", 10);
        add("gear", "power", "minecraft:iron_chestplate", 0);
        add("endless_abyss", "power", "isekai_mahou:abyss_shard", 100);
        // ---- Combate
        add("lock_guard", "combat", "minecraft:iron_sword", 0);
        add("finisher_combo", "combat", "minecraft:blaze_powder", 0);
        // ---- Mundo
        add("portals", "world", "minecraft:ender_eye", 0);
        add("puzzles", "world", "isekai_mahou:depths_puzzle_core", 0);
        add("towers", "world", "isekai_mahou:tower_teleport_crystal", 0);
        add("magic_biomes", "world", "isekai_mahou:luminous_crystal_cluster", 0);
        add("lore_pages", "world", "isekai_mahou:lore_page", 0);
        add("world_events", "world", "isekai_mahou:world_events_star_metal", 0);
    }

    private static void add(String id, String group, String icon, int minLevel) {
        TOPICS.add(new Topic(id, group, ResourceLocation.parse(icon), minLevel));
    }

    public static List<Group> groups() {
        return GROUPS;
    }

    public static List<Topic> topics() {
        return List.copyOf(TOPICS);
    }

    /** Assuntos de um grupo, na ordem do índice. */
    public static List<Topic> in(Group group) {
        List<Topic> out = new ArrayList<>();
        for (Topic t : TOPICS) if (t.group().equals(group.id())) out.add(t);
        return out;
    }

    private GuideTopics() {}
}
