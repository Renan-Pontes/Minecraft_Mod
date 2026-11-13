package com.renanpontes.isekaimahou.feature.lore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Tabela dos <b>fragmentos de lore</b> colecionáveis (v1.0): 37 fragmentos em 8 coleções. Cada fragmento fala de um
 * LUGAR — ler o fragmento revela esse lugar no mapa ("pra justificar explorar a lore, ela passar coordenadas de coisas
 * no mapa"): a estrutura mais próxima de uma lista (por id em texto — se nenhuma existir, o fragmento só conta a
 * história) ou um tesouro enterrado.
 * <p>
 * Textos: {@code lore.isekai_mahou.frag.<id>} (título) e {@code .text}; coleção {@code lore.isekai_mahou.set.<id>};
 * lugar {@code lore.isekai_mahou.place.<place>}. Gerados por {@code tools/gen/lore_lang.py} (o gerador confere esta
 * tabela). Nunca renomeie um id: fica salvo no jogador.
 */
public final class LoreFragments {
    /** O que o fragmento revela. */
    public enum Kind { STRUCTURE, TREASURE }

    /**
     * @param id         único ({@code <coleção>_<n>})
     * @param set        id da coleção
     * @param index      posição na coleção (1..n) — as páginas saem em ordem
     * @param place      chave do nome do lugar revelado
     * @param dim        dimensão do lugar
     * @param structures estruturas do mod que servem (caminho do id), vazio para tesouro
     */
    public record Fragment(String id, String set, int index, Kind kind, String place, ResourceKey<Level> dim,
                           List<String> structures) {
        public MutableComponent title() {
            return Component.translatable("lore." + IsekaiMahou.MODID + ".frag." + id);
        }

        public String textKey() {
            return "lore." + IsekaiMahou.MODID + ".frag." + id + ".text";
        }

        public MutableComponent placeName() {
            return Component.translatable("lore." + IsekaiMahou.MODID + ".place." + place);
        }
    }

    /**
     * Uma coleção.
     *
     * @param color cor RGB (lista, rastreador)
     * @param truth entrada da Enciclopédia liberada ao completar ("" = nenhuma)
     */
    public record LoreSet(String id, int color, String truth, List<Fragment> fragments) {
        public MutableComponent title() {
            return Component.translatable("lore." + IsekaiMahou.MODID + ".set." + id);
        }
    }

    private static final Map<String, LoreSet> SETS = new LinkedHashMap<>();
    private static final Map<String, Fragment> BY_ID = new LinkedHashMap<>();

    private static final ResourceKey<Level> NETHER = Level.NETHER, END = Level.END, OVERWORLD = Level.OVERWORLD;

    static {
        // Crônicas de Velmora — o império antigo e o que ele deixou de pé
        set("velmora", 0xD8B45A, "truth_towers",
                at("tower", "tower_stone", "tower_frost", "tower_sand", "tower_forest", "tower_sunken", "tower_floating"),
                at("sanctum", "ascension_sanctum"),
                at("colossus", "colossus_ruins"),
                at("arcane_ruins", "arcane_ruins"),
                at("labyrinth", "minotaur_labyrinth"),
                at("rupture", "twilight_rupture"));
        // Diários dos Seis Sábios — um por santuário
        set("sages", 0x7FD0FF, "truth_seventh",
                at("shrine_fire", "shrine_fire"),
                at("shrine_water", "shrine_water"),
                at("shrine_earth", "shrine_earth"),
                at("shrine_wind", "shrine_wind"),
                at("shrine_light", "shrine_light"),
                at("shrine_shadow", "shrine_shadow"));
        // Confissões do Rei Demônio
        set("demon", 0xE0503C, "truth_king",
                at("profane_altar", "profane_altar"),
                at("abyssal_chasm", "abyssal_chasm"),
                in(NETHER, "nether_citadel", "nether_citadel"),
                treasure());
        // Cartas do Primeiro Invocado
        set("herald", 0xB98CFF, "truth_call",
                at("elemental_gate", "elemental_gate"),
                at("celestial_stairway", "celestial_stairway"),
                at("crypt", "forgotten_crypt"),
                at("fey_ring", "fey_ring"),
                treasure());
        // Caderno do Caçador — covis dos grandes monstros
        set("hunter", 0x86E08A, "truth_beasts",
                at("hydra_lair", "hydra_lair"),
                at("wyvern_peak", "wyvern_peak"),
                at("basilisk_tomb", "basilisk_tomb"),
                at("manticore_den", "manticore_den"),
                at("false_hydra_lair", "false_hydra_lair"),
                at("mimic_vault", "mimic_vault"));
        // Mapas do Contrabandista — só tesouros
        set("smuggler", 0xFFD86B, "",
                treasure(), treasure(), treasure(), treasure());
        // Runas das Profundezas
        set("deep", 0xC9A27A, "truth_dwarves",
                at("dwarf_hold", "dwarf_hold"),
                at("depths", "depths_dungeon"),
                at("kobold_den", "kobold_den"));
        // Cantos do Vazio
        set("void", 0x9AA8E8, "truth_moon",
                at("garganta", "garganta_site"),
                in(END, "end_library", "end_library"),
                at("mage_tower", "mage_tower", "renegade_tower"));
    }

    /** Peça de montagem: o lugar de um fragmento (o id e a posição saem da ordem dentro da coleção). */
    private record Spot(Kind kind, String place, ResourceKey<Level> dim, List<String> structures) {}

    private static Spot at(String place, String... structures) {
        return new Spot(Kind.STRUCTURE, place, OVERWORLD, List.of(structures));
    }

    private static Spot in(ResourceKey<Level> dim, String place, String... structures) {
        return new Spot(Kind.STRUCTURE, place, dim, List.of(structures));
    }

    private static Spot treasure() {
        return new Spot(Kind.TREASURE, "treasure", OVERWORLD, List.of());
    }

    private static void set(String id, int color, String truth, Spot... spots) {
        List<Fragment> list = new ArrayList<>();
        for (int i = 0; i < spots.length; i++) {
            Spot s = spots[i];
            Fragment f = new Fragment(id + "_" + (i + 1), id, i + 1, s.kind(), s.place(), s.dim(), s.structures());
            list.add(f);
            BY_ID.put(f.id(), f);
        }
        SETS.put(id, new LoreSet(id, color, truth, Collections.unmodifiableList(list)));
    }

    public static List<LoreSet> sets() {
        return List.copyOf(SETS.values());
    }

    public static List<Fragment> all() {
        return List.copyOf(BY_ID.values());
    }

    public static int total() {
        return BY_ID.size();
    }

    @Nullable
    public static Fragment get(String id) {
        return id == null ? null : BY_ID.get(id);
    }

    @Nullable
    public static LoreSet set(String id) {
        return id == null ? null : SETS.get(id);
    }

    /** Coleção que libera a entrada {@code truthId} da Enciclopédia (null se nenhuma). */
    @Nullable
    public static LoreSet setOfTruth(String truthId) {
        for (LoreSet s : SETS.values()) if (!s.truth().isEmpty() && s.truth().equals(truthId)) return s;
        return null;
    }

    /** Chave de dimensão a partir do texto salvo (null se inválido). */
    @Nullable
    public static ResourceKey<Level> dim(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? null : ResourceKey.create(Registries.DIMENSION, rl);
    }

    private LoreFragments() {}
}
