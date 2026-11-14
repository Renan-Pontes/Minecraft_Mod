package com.renanpontes.isekaimahou.feature.stands;

import static com.renanpontes.isekaimahou.feature.stands.Perk.arcs;
import static com.renanpontes.isekaimahou.feature.stands.Perk.berserk;
import static com.renanpontes.isekaimahou.feature.stands.Perk.blackFlame;
import static com.renanpontes.isekaimahou.feature.stands.Perk.buff;
import static com.renanpontes.isekaimahou.feature.stands.Perk.dmg;
import static com.renanpontes.isekaimahou.feature.stands.Perk.freeze;
import static com.renanpontes.isekaimahou.feature.stands.Perk.pierce;
import static com.renanpontes.isekaimahou.feature.stands.Perk.shock;
import static com.renanpontes.isekaimahou.feature.stands.Perk.sky;
import static com.renanpontes.isekaimahou.feature.stands.Perk.volley;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.glyph.MasteryMutations;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;

/**
 * v1.1 — CATÁLOGO das EVOLUÇÕES PRÓPRIAS ("como os Atos do Tusk"). Cada glifo de anime aqui ganha estágios com nome,
 * cor e comportamento novos em níveis de maestria (os nomes/níveis vão para o núcleo em
 * {@link MasteryMutations#evolve}; as peças de comportamento ficam aqui e são executadas por {@link EvolutionEffects}).
 * <p>
 * Os glifos do legends_h com regras próprias (Killer Queen, King Crimson, ORA, Full Cowl) declaram só o nome aqui e
 * consultam {@link MasteryMutations#stageOf} dentro do próprio código.
 * <p>
 * Níveis (curva v1.1, "acertos" = acertos em inimigo): V ≈ 500, VI ≈ 800, VII ≈ 1,3 mil, XII ≈ 6,7 mil, XV ≈ 11,5 mil,
 * XVIII ≈ 17 mil, XX ≈ 21,5 mil, XXII ≈ 26 mil, XXV ≈ 35 mil, ★28 ≈ 45 mil.
 */
public final class Evolutions {
    /** glifo → peças de cada estágio (índice 0 = estágio 1). */
    private static final Map<ResourceLocation, List<List<Perk>>> PERKS = new HashMap<>();

    static void register() {
        // High School DxD — Boost (aumento): Boosted Gear → Balance Breaker → Juggernaut Drive
        def("boost_gear")
                .st("boosted_gear", 5, 0xFF3A3A, dmg(1.10F))
                .st("balance_breaker", 12, 0xFF1E1E, dmg(1.2F), buff(MobEffects.DAMAGE_RESISTANCE, 0, 80, 12))
                .st("juggernaut_drive", 25, 0x8A0010, dmg(1.35F), berserk(), shock(0.25F, 0.9F, 3F))
                .end();
        // Naruto — Chidori → Raikiri → Chidori Nagashi → Kirin
        def("chidori")
                .st("raikiri", 5, 0x9FE8FF, dmg(1.15F), arcs(1, 0.3F))
                .st("chidori_nagashi", 12, 0x6FC8FF, dmg(1.2F), arcs(2, 0.3F), freeze(10))
                .st("kirin", 22, 0xEAF8FF, dmg(1.25F), arcs(2, 0.3F), sky(1.0F, 3F, 200))
                .end();
        // Uchiha — Amaterasu → Enton → Kagutsuchi
        def("amaterasu")
                .st("enton", 6, 0x3A0A3A, blackFlame(0.12F, 2F, 60))
                .st("kagutsuchi", 18, 0x50002A, dmg(1.2F), blackFlame(0.15F, 3F, 100))
                .end();
        // Uchiha — Susanoo: costelas → armadura → Susanoo Perfeito
        def("susanoo")
                .st("susanoo_armored", 7, 0x8A6CFF, buff(MobEffects.ABSORPTION, 1, 200, 30))
                .st("perfect_susanoo", 20, 0x4E7BFF, dmg(1.3F), buff(MobEffects.ABSORPTION, 1, 200, 30),
                        buff(MobEffects.DAMAGE_RESISTANCE, 0, 120, 30), shock(0.3F, 1.0F, 3F))
                .end();
        // JoJo — Killer Queen → Sheer Heart Attack → Bites the Dust (regras em legends_h/jojo/Bombs)
        def("killer_queen")
                .st("sheer_heart_attack", 5, 0xFF8A3A)
                .st("bites_the_dust", 15, 0xFF6EB4)
                .end();
        // JoJo — King Crimson → Epitáfio Desperto → Reino Apagado (regras em legends_h/jojo/TimeSkip)
        def("king_crimson")
                .st("epitaph_awakened", 6, 0xFF3060)
                .st("erased_kingdom", 18, 0x8A0020)
                .end();
        // JoJo — ORA ORA → rajada longa → Star Finger → The World (regras em JojoGlyphs.OraRush)
        def("ora_rush")
                .st("ora_barrage", 5, 0xA07CFF)
                .st("star_finger", 12, 0x8A6CFF, volley(1, 0.5F))
                .st("the_world", 22, 0x5040C0, volley(1, 0.5F))
                .end();
        // One Piece — Gomu Gomu no Pistol → Gear 2 → Gear 3 → Gear 4
        def("gomu_pistol")
                .st("jet_pistol", 5, 0xFF7A7A, dmg(1.2F))
                .st("gigant_pistol", 12, 0xE9B48C, dmg(1.2F), shock(0.3F, 1.0F, 2.5F))
                .st("kong_gun", 22, 0xC02050, dmg(1.4F), shock(0.4F, 1.6F, 3.5F))
                .end();
        // One Piece — Haki do Armamento (aumento): Endurecimento → Ryou (emissão) → Emissão Total
        def("haki_armament")
                .st("koka", 5, 0x2A2A30, dmg(1.12F))
                .st("ryou", 15, 0x8A1020, dmg(1.15F), pierce(0.25F))
                .st("ryou_burst", 28, 0x600010, dmg(1.2F), pierce(0.35F), shock(0.2F, 0.8F, 2.5F))
                .end();
        // Frieren — Zoltraak → refinado → Rajada da Fern → Tempestade
        def("zoltraak")
                .st("refined_zoltraak", 5, 0xD8C8FF, dmg(1.1F), volley(1, 0.4F))
                .st("fern_volley", 12, 0xB8A0FF, dmg(1.1F), volley(3, 0.4F))
                .st("zoltraak_storm", 22, 0xF0EAFF, dmg(1.15F), volley(5, 0.45F))
                .end();
        // My Hero Academia — Full Cowl: o teto seguro sobe por estágio (regras em legends_h/mha/FullCowl)
        def("full_cowl")
                .st("shoot_style", 5, 0x7CFF9A)
                .st("forged_body", 12, 0x5CFF7A)
                .st("full_bullet", 20, 0x30FF60)
                .end();
        // My Hero Academia — Detroit Smash → Texas Smash → Detroit 100%
        def("detroit_smash")
                .st("texas_smash", 5, 0xE8F4FF, shock(0.25F, 0.8F, 2.5F))
                .st("detroit_100", 18, 0xFFD040, dmg(1.35F), shock(0.35F, 1.4F, 4F))
                .end();
    }

    /** Peças do estágio {@code stage} (1..n) do glifo; vazio = nada genérico (o glifo cuida sozinho ou não existe). */
    public static List<Perk> perks(ResourceLocation glyph, int stage) {
        List<List<Perk>> l = PERKS.get(glyph);
        if (l == null || stage <= 0 || stage > l.size()) return List.of();
        return l.get(stage - 1);
    }

    /** Glifos com peças genéricas (para os ouvintes saberem se vale procurar). */
    public static boolean hasPerks(ResourceLocation glyph) {
        return PERKS.containsKey(glyph);
    }

    static Iterable<ResourceLocation> glyphs() {
        return PERKS.keySet();
    }

    private static Def def(String glyph) {
        return new Def(IsekaiMahou.id(glyph));
    }

    private static final class Def {
        final ResourceLocation id;
        final MasteryMutations.EvolutionBuilder core;
        final List<List<Perk>> perks = new ArrayList<>();

        Def(ResourceLocation id) {
            this.id = id;
            this.core = MasteryMutations.evolve(id);
        }

        Def st(String key, int level, int color, Perk... p) {
            core.stage(key, level, color);
            perks.add(List.of(p));
            return this;
        }

        void end() {
            core.done();
            PERKS.put(id, List.copyOf(perks));
        }
    }

    private Evolutions() {}
}
