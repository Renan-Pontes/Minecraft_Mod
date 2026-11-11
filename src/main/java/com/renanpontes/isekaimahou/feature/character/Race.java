package com.renanpontes.isekaimahou.feature.character;

import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

/**
 * Raças jogáveis. A ORDEM é salva (pacotes usam o ordinal; o save usa o id): raças novas só no FIM.
 * <p>
 * v1.0.1 (pedido do jogador): <b>todas as raças são escolhidas livremente no início</b> (sem bloqueio nem feito
 * secreto). Cada raça começa com uma passiva FRACA que cresce em 5 <b>níveis de raça</b> (I–V, {@link RaceProgress}):
 * os níveis II–IV fortalecem a passiva e despertam a 2ª skill racial (glifos montáveis no Grimório, {@link RaceGlyphs});
 * o nível V (nível 90 + o feito da raça) libera a <b>Transformação</b> racial ({@link RaceForms}), a ultimate da raça.
 * <p>
 * Textos: {@code race.isekai_mahou.<id>} (+ {@code .desc}), {@code character.isekai_mahou.passive.<id>} (+ {@code .lv1}
 * … {@code .lv4}), {@code character.isekai_mahou.form.<id>} (+ {@code .desc}, {@code .power}, {@code .power_desc}),
 * {@code character.isekai_mahou.feat.<id>} e {@code character.isekai_mahou.trait.<t>} (+ {@code .desc}).
 */
public enum Race {
    //          id            cor       escala  visão no escuro        skills raciais (ordem de despertar)
    HUMAN("human",          0xE8C77A, 0.0,   Darkvision.NONE,        List.of("race_resolve", "race_adaptive"),
            List.of("human_ambition")),
    ELF("elf",              0x7BD88F, 0.0,   Darkvision.DARK,        List.of("race_moon_arrow", "race_forest_step"),
            List.of("elf_darkvision", "elf_attunement")),
    DWARF("dwarf",          0xC98A4B, -0.15, Darkvision.UNDERGROUND, List.of("race_stone_skin", "race_forge_shout"),
            List.of("dwarf_darkvision", "dwarf_stout")),
    HALF_ORC("half_orc",    0x8DBF5A, 0.06,  Darkvision.NONE,        List.of("race_blood_fury", "race_savage_cleave"),
            List.of("orc_hot_blood", "orc_build")),
    DEMON("demon",          0xD8455B, 0.0,   Darkvision.DARK,        List.of("race_hellfire", "race_shadow_wings"),
            List.of("demon_rebuke", "demon_darkvision")),
    DRAGONBORN("dragonborn", 0xE0A030, 0.08, Darkvision.NONE,        List.of("race_dragon_breath", "race_dragon_scales"),
            List.of("dragon_ancestry", "dragon_build")),
    BEASTFOLK("beastfolk",  0xD08A3A, 0.0,   Darkvision.NONE,        List.of("race_predator_leap", "race_wild_instinct"),
            List.of("beast_speed", "beast_fall")),
    // ---- v1.0.1: novas ----
    ARACHNID("arachnid",    0x9A6AD8, 0.0,   Darkvision.DARK,        List.of("race_web_shot", "race_venom_fang"),
            List.of("arachnid_eyes", "arachnid_climb")),
    PHANTOM("phantom",      0x9FD8E8, 0.0,   Darkvision.DARK,        List.of("race_spectral_touch", "race_phase_shift"),
            List.of("phantom_eyes", "phantom_float")),
    ANGEL("angel",          0xFFE08A, 0.0,   Darkvision.NONE,        List.of("race_holy_light", "race_divine_shield"),
            List.of("angel_halo", "angel_wings")),
    SLIME("slime",          0x8FE05A, 0.0,   Darkvision.NONE,        List.of("race_acid_splash", "race_jelly_body"),
            List.of("slime_bounce", "slime_jelly"));

    public static final Race[] ALL = values();
    private static final String KEY = "race.isekai_mahou.";
    private static final String TRAIT_KEY = "character.isekai_mahou.trait.";
    private static final String PASSIVE_KEY = "character.isekai_mahou.passive.";
    private static final String FORM_KEY = "character.isekai_mahou.form.";
    private static final String FEAT_KEY = "character.isekai_mahou.feat.";

    /** Como a raça enxerga no escuro. */
    public enum Darkvision { NONE, DARK, UNDERGROUND }

    public final String id;
    public final int rgb;
    /** Modificador do atributo de tamanho (multiplica a base: −0,15 = 85% da altura). */
    public final double scale;
    public final Darkvision darkvision;
    /** Ids (caminho, sem namespace) dos glifos raciais, na ordem em que despertam. */
    public final List<String> skills;
    /** Traços menores mostrados na tela (chaves {@code character.isekai_mahou.trait.<id>} e {@code .desc}). */
    public final List<String> traits;

    Race(String id, int rgb, double scale, Darkvision darkvision, List<String> skills, List<String> traits) {
        this.id = id;
        this.rgb = rgb;
        this.scale = scale;
        this.darkvision = darkvision;
        this.skills = skills;
        this.traits = traits;
    }

    public int argb() {
        return 0xFF000000 | rgb;
    }

    public int bit() {
        return 1 << ordinal();
    }

    public MutableComponent displayName() {
        return Component.translatable(KEY + id).withColor(rgb);
    }

    public MutableComponent description() {
        return Component.translatable(KEY + id + ".desc");
    }

    /** Nome da passiva da raça (cresce com o nível de raça). */
    public MutableComponent passiveName() {
        return Component.translatable(PASSIVE_KEY + id);
    }

    /** Resumo da passiva (o que faz no nível I). */
    public MutableComponent passiveDesc() {
        return passiveLevel(1);
    }

    /** O que a passiva ganha no nível de raça {@code lv} (1..4; o V é a Transformação). */
    public MutableComponent passiveLevel(int lv) {
        return Component.translatable(PASSIVE_KEY + id + ".lv" + Math.max(1, Math.min(4, lv)));
    }

    /** Nome da Transformação racial (nível V). */
    public MutableComponent formName() {
        return Component.translatable(FORM_KEY + id);
    }

    public MutableComponent formDesc() {
        return Component.translatable(FORM_KEY + id + ".desc");
    }

    /** Nome do golpe da forma (tecla racial durante a Transformação). */
    public MutableComponent formPower() {
        return Component.translatable(FORM_KEY + id + ".power");
    }

    public MutableComponent formPowerDesc() {
        return Component.translatable(FORM_KEY + id + ".power_desc");
    }

    /** Feito da raça (exigido no nível V), com o progresso "x/meta". */
    public MutableComponent featDesc(int goal) {
        return Component.translatable(FEAT_KEY + id, goal);
    }

    /** Nome de um traço ({@link #traits}). */
    public static MutableComponent traitName(String trait) {
        return Component.translatable(TRAIT_KEY + trait);
    }

    public static MutableComponent traitDesc(String trait) {
        return Component.translatable(TRAIT_KEY + trait + ".desc");
    }

    /** Medalhão 32×32 da raça ({@code textures/gui/race/<id>.png}). */
    public ResourceLocation emblem() {
        return IsekaiMahou.id("textures/gui/race/" + id + ".png");
    }

    public static Race byIndex(int i) {
        return i >= 0 && i < ALL.length ? ALL[i] : HUMAN;
    }

    public static Race byId(String id) {
        for (Race r : ALL) if (r.id.equals(id)) return r;
        return null;
    }
}
