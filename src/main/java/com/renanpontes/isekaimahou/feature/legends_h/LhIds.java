package com.renanpontes.isekaimahou.feature.legends_h;

import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.resources.ResourceLocation;

/** Ids dos glifos de anime do legends_h (todos comuns: aprendidos como qualquer glifo do mesmo elemento/tier). */
public final class LhIds {
    // JoJo
    public static final ResourceLocation KING_CRIMSON = IsekaiMahou.id("king_crimson");
    public static final ResourceLocation EPITAPH = IsekaiMahou.id("epitaph");
    public static final ResourceLocation BITES_THE_DUST = IsekaiMahou.id("bites_the_dust");
    public static final ResourceLocation KILLER_QUEEN = IsekaiMahou.id("killer_queen");
    public static final ResourceLocation SHEER_HEART_ATTACK = IsekaiMahou.id("sheer_heart_attack");
    public static final ResourceLocation ORA_RUSH = IsekaiMahou.id("ora_rush");
    // High School DxD
    public static final ResourceLocation BOOST_GEAR = IsekaiMahou.id("boost_gear");
    public static final ResourceLocation BOOST_TRANSFER = IsekaiMahou.id("boost_transfer");
    public static final ResourceLocation BALANCE_BREAKER = IsekaiMahou.id("balance_breaker");
    // My Hero Academia
    public static final ResourceLocation FULL_COWL = IsekaiMahou.id("full_cowl");
    public static final ResourceLocation ONE_FOR_ALL = IsekaiMahou.id("one_for_all");
    public static final ResourceLocation DELAWARE_SMASH = IsekaiMahou.id("delaware_smash");
    public static final ResourceLocation DETROIT_SMASH = IsekaiMahou.id("detroit_smash");
    public static final ResourceLocation BLACKWHIP = IsekaiMahou.id("blackwhip");
    // Outros
    public static final ResourceLocation PSYCHO_HUNDRED = IsekaiMahou.id("psycho_hundred");
    public static final ResourceLocation GENKI_DAMA = IsekaiMahou.id("genki_dama");
    public static final ResourceLocation CURSED_SPEECH = IsekaiMahou.id("cursed_speech");

    public static final List<ResourceLocation> ALL = List.of(KING_CRIMSON, EPITAPH, BITES_THE_DUST, KILLER_QUEEN,
            SHEER_HEART_ATTACK, ORA_RUSH, BOOST_GEAR, BOOST_TRANSFER, BALANCE_BREAKER, FULL_COWL, ONE_FOR_ALL,
            DELAWARE_SMASH, DETROIT_SMASH, BLACKWHIP, PSYCHO_HUNDRED, GENKI_DAMA, CURSED_SPEECH);

    private LhIds() {}
}
