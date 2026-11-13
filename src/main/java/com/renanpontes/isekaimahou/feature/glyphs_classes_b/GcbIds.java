package com.renanpontes.isekaimahou.feature.glyphs_classes_b;

import java.util.List;
import java.util.Set;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.resources.ResourceLocation;

/**
 * Ids dos 14 glifos novos da v1.4 (aumento + gatilho de Encantador, Shinigami, Quincy, Pistoleiro, Patrulheiro,
 * Apostador e Steel Ball). Todos são glifos de ORIGEM da classe: a própria classe aprende pelo rank (ver
 * {@link ClassUnlocks}); as outras só por Lição.
 */
public final class GcbIds {
    public static final String MODULE = "glyphs_classes_b";

    // ------------------------------------------------------------------ Encantador
    public static final ResourceLocation ENGRAVED_RUNE = IsekaiMahou.id("enchanter_engraved_rune");
    public static final ResourceLocation ON_ENCHANTED_STRIKE = IsekaiMahou.id("enchanter_on_enchanted_strike");
    // ------------------------------------------------------------------ Shinigami
    public static final ResourceLocation SHUNPO_CAST = IsekaiMahou.id("shinigami_shunpo_cast");
    public static final ResourceLocation ON_RELEASED_CUT = IsekaiMahou.id("shinigami_on_released_cut");
    // ------------------------------------------------------------------ Quincy
    public static final ResourceLocation SKLAVEREI = IsekaiMahou.id("quincy_sklaverei");
    public static final ResourceLocation ON_REISHI = IsekaiMahou.id("quincy_on_reishi");
    // ------------------------------------------------------------------ Pistoleiro
    public static final ResourceLocation TRICK_RICOCHET = IsekaiMahou.id("gun_trick_ricochet");
    public static final ResourceLocation ON_HEADSHOT = IsekaiMahou.id("gun_on_headshot");
    // ------------------------------------------------------------------ Patrulheiro
    public static final ResourceLocation COORDINATED = IsekaiMahou.id("ranger_coordinated_strike");
    public static final ResourceLocation ON_MARK = IsekaiMahou.id("ranger_on_mark");
    // ------------------------------------------------------------------ Apostador
    public static final ResourceLocation RUSSIAN_ROULETTE = IsekaiMahou.id("gambler_russian_roulette");
    public static final ResourceLocation ON_BIG_WIN = IsekaiMahou.id("gambler_on_big_win");
    // ------------------------------------------------------------------ Steel Ball
    public static final ResourceLocation GOLDEN_SPIN = IsekaiMahou.id("steelball_golden_spin");
    public static final ResourceLocation ON_BALL_HIT = IsekaiMahou.id("steelball_on_ball_hit");

    /** Os 7 gatilhos (a tecla dos slots que começam com eles não conjura). */
    public static final Set<ResourceLocation> TRIGGERS = Set.of(ON_ENCHANTED_STRIKE, ON_RELEASED_CUT, ON_REISHI, ON_HEADSHOT,
            ON_MARK, ON_BIG_WIN, ON_BALL_HIT);

    /** Todos os 14 (comando de teste). */
    public static final List<ResourceLocation> ALL = List.of(ENGRAVED_RUNE, ON_ENCHANTED_STRIKE, SHUNPO_CAST, ON_RELEASED_CUT,
            SKLAVEREI, ON_REISHI, TRICK_RICOCHET, ON_HEADSHOT, COORDINATED, ON_MARK, RUSSIAN_ROULETTE, ON_BIG_WIN, GOLDEN_SPIN,
            ON_BALL_HIT);

    /** Prefixo das mensagens do módulo. */
    public static final String MSG = "message.isekai_mahou.glyphs_classes_b.";

    private GcbIds() {}
}
