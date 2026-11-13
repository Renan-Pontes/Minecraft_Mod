package com.renanpontes.isekaimahou.feature.glyphs_elements_b;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.glyph.AugmentGlyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;
import com.renanpontes.isekaimahou.glyph.TriggerGlyph;

import net.minecraft.resources.ResourceLocation;

/**
 * v1.4 — Glifos novos dos 15 elementos de FUSÃO: 1 aumento + 1 gatilho para cada (30 glifos).
 *
 * <h3>Como o jogador aprende</h3>
 * Igual aos outros glifos de fusão (módulos fusions_a..d): cada glifo tem o elemento da fusão e um TIER do caminho
 * daquele elemento. O módulo de Caminhos ensina sozinho os glifos do tier quando o jogador libera o nó (Adepto = tier 3,
 * Mestre = tier 4) — e no login ensina os que faltarem dos nós já liberados. Como a fusão só desperta na Convergência
 * (os DOIS elementos-pai no nó Mestre), todos já pedem os dois pais. Além disso, cada glifo pede Círculo 3+ para ser
 * usado; os mais fortes (Sol Cativo, Ressonância, Colapso, Passo Estelar, Aniquilar) pedem tier 4 e Círculo 4.
 *
 * <h3>Comportamento</h3>
 * Os aumentos só marcam {@link SpellStats#custom}; quem age é {@link GebAugments} (eventos de dano/conjuração).
 * Os gatilhos vão no começo da receita; quem detecta a condição e conjura o slot é {@link GebTriggers}.
 */
public final class GebGlyphs {

    // =====================================================================================
    //  Aumentos
    // =====================================================================================
    public static final ResourceLocation MAGMA_AFTERSHOCK = IsekaiMahou.id("magma_aftershock");
    public static final ResourceLocation STEAM_JET = IsekaiMahou.id("steam_jet");
    public static final ResourceLocation LIGHTNING_FORK = IsekaiMahou.id("lightning_fork");
    public static final ResourceLocation SUN_CAPTIVE_STAR = IsekaiMahou.id("sun_captive_star");
    public static final ResourceLocation HELLFIRE_HUNGER = IsekaiMahou.id("hellfire_hunger");
    public static final ResourceLocation NATURE_SEED = IsekaiMahou.id("nature_seed");
    public static final ResourceLocation ICE_SPLINTER = IsekaiMahou.id("ice_splinter");
    public static final ResourceLocation PURITY_ABLUTION = IsekaiMahou.id("purity_ablution");
    public static final ResourceLocation ABYSS_ANCHOR = IsekaiMahou.id("abyss_anchor");
    public static final ResourceLocation SAND_EROSION = IsekaiMahou.id("sand_erosion");
    public static final ResourceLocation CRYSTAL_RESONANCE = IsekaiMahou.id("crystal_resonance");
    public static final ResourceLocation GRAVITY_COLLAPSE = IsekaiMahou.id("gravity_collapse");
    public static final ResourceLocation ASTRAL_STARSTEP = IsekaiMahou.id("astral_starstep");
    public static final ResourceLocation VOID_ANNIHILATE = IsekaiMahou.id("void_annihilate");
    public static final ResourceLocation TWILIGHT_TIDE = IsekaiMahou.id("twilight_tide");

    // =====================================================================================
    //  Gatilhos
    // =====================================================================================
    public static final ResourceLocation MAGMA_ON_LAND = IsekaiMahou.id("magma_on_land");
    public static final ResourceLocation STEAM_ON_VAPOR = IsekaiMahou.id("steam_on_vapor");
    public static final ResourceLocation LIGHTNING_ON_OVERLOAD = IsekaiMahou.id("lightning_on_overload");
    public static final ResourceLocation SUN_ON_SUNLIGHT = IsekaiMahou.id("sun_on_sunlight");
    public static final ResourceLocation HELLFIRE_ON_BURN = IsekaiMahou.id("hellfire_on_burn");
    public static final ResourceLocation NATURE_ON_ROOT = IsekaiMahou.id("nature_on_root");
    public static final ResourceLocation ICE_ON_FREEZE = IsekaiMahou.id("ice_on_freeze");
    public static final ResourceLocation PURITY_ON_DEBUFF = IsekaiMahou.id("purity_on_debuff");
    public static final ResourceLocation ABYSS_ON_DIVE = IsekaiMahou.id("abyss_on_dive");
    public static final ResourceLocation SAND_ON_BLIND = IsekaiMahou.id("sand_on_blind");
    public static final ResourceLocation CRYSTAL_ON_PETRIFY = IsekaiMahou.id("crystal_on_petrify");
    public static final ResourceLocation GRAVITY_ON_SLAM = IsekaiMahou.id("gravity_on_slam");
    public static final ResourceLocation ASTRAL_ON_TELEPORT = IsekaiMahou.id("astral_on_teleport");
    public static final ResourceLocation VOID_ON_ERASE = IsekaiMahou.id("void_on_erase");
    public static final ResourceLocation TWILIGHT_ON_THRESHOLD = IsekaiMahou.id("twilight_on_threshold");

    /** Todos os glifos deste módulo (para o comando de teste). */
    private static final List<ResourceLocation> ALL = new ArrayList<>();
    /** Só os gatilhos (o motor de gatilhos bloqueia a tecla dos slots que começam com eles). */
    private static final List<ResourceLocation> TRIGGERS = new ArrayList<>();

    public static List<ResourceLocation> all() { return Collections.unmodifiableList(ALL); }
    public static List<ResourceLocation> triggers() { return Collections.unmodifiableList(TRIGGERS); }

    /** Aumento de fusão: só marca o id em {@link SpellStats#custom} (não acumula efeito, mas conta as cópias). */
    public static class FusionAugment extends AugmentGlyph {
        public FusionAugment(ResourceLocation id, int cost, Element element, int tier, int circle) {
            super(id.getPath(), cost, s -> s.custom.merge(id, 1, Integer::sum));
            element(element);
            tier(tier);
            minCircle(circle);
        }
    }

    /** Gatilho de fusão (vai no começo da receita; o slot sai sozinho quando a condição acontece). */
    public static class FusionTrigger extends TriggerGlyph {
        public FusionTrigger(ResourceLocation id, int cost, Element element, int tier, int circle) {
            super(id.getPath(), cost, true);
            element(element);
            tier(tier);
            minCircle(circle);
        }
    }

    public static void register() {
        // ---------- Aumentos (tier 3 = nó Adepto da fusão; tier 4 = nó Mestre) ----------
        aug(MAGMA_AFTERSHOCK, 12, Element.MAGMA, 3, 3);
        aug(STEAM_JET, 8, Element.STEAM, 3, 3);
        aug(LIGHTNING_FORK, 14, Element.LIGHTNING, 3, 3);
        aug(SUN_CAPTIVE_STAR, 14, Element.SUN, 4, 4);
        aug(HELLFIRE_HUNGER, 12, Element.HELLFIRE, 3, 3);
        aug(NATURE_SEED, 12, Element.NATURE, 3, 3);
        aug(ICE_SPLINTER, 12, Element.ICE, 3, 3);
        aug(PURITY_ABLUTION, 10, Element.PURITY, 3, 3);
        aug(ABYSS_ANCHOR, 10, Element.ABYSS, 3, 3);
        aug(SAND_EROSION, 10, Element.SAND, 3, 3);
        GlyphRegistry.register(new FusionAugment(CRYSTAL_RESONANCE, 18, Element.CRYSTAL, 4, 4).castTicks(6));
        ALL.add(CRYSTAL_RESONANCE);
        aug(GRAVITY_COLLAPSE, 16, Element.GRAVITY, 4, 4);
        aug(ASTRAL_STARSTEP, 16, Element.ASTRAL, 4, 4);
        aug(VOID_ANNIHILATE, 14, Element.VOID, 4, 4);
        aug(TWILIGHT_TIDE, 12, Element.TWILIGHT, 3, 3);

        // ---------- Gatilhos (todos no nó Adepto da fusão, Círculo 3) ----------
        trig(MAGMA_ON_LAND, 6, Element.MAGMA);
        trig(STEAM_ON_VAPOR, 6, Element.STEAM);
        trig(LIGHTNING_ON_OVERLOAD, 6, Element.LIGHTNING);
        trig(SUN_ON_SUNLIGHT, 5, Element.SUN);
        trig(HELLFIRE_ON_BURN, 5, Element.HELLFIRE);
        trig(NATURE_ON_ROOT, 6, Element.NATURE);
        trig(ICE_ON_FREEZE, 6, Element.ICE);
        trig(PURITY_ON_DEBUFF, 5, Element.PURITY);
        trig(ABYSS_ON_DIVE, 5, Element.ABYSS);
        trig(SAND_ON_BLIND, 6, Element.SAND);
        trig(CRYSTAL_ON_PETRIFY, 6, Element.CRYSTAL);
        trig(GRAVITY_ON_SLAM, 6, Element.GRAVITY);
        trig(ASTRAL_ON_TELEPORT, 6, Element.ASTRAL);
        trig(VOID_ON_ERASE, 6, Element.VOID);
        trig(TWILIGHT_ON_THRESHOLD, 6, Element.TWILIGHT);
    }

    private static void aug(ResourceLocation id, int cost, Element e, int tier, int circle) {
        GlyphRegistry.register(new FusionAugment(id, cost, e, tier, circle));
        ALL.add(id);
    }

    private static void trig(ResourceLocation id, int cost, Element e) {
        GlyphRegistry.register(new FusionTrigger(id, cost, e, 3, 3));
        ALL.add(id);
        TRIGGERS.add(id);
    }

    // =====================================================================================
    //  Leitura dos aumentos
    // =====================================================================================

    /** O aumento está no glifo que causou o dano (efeito) ou na forma do estágio? */
    public static boolean has(SpellContext ctx, SpellStats stats, ResourceLocation id) {
        return (stats != null && stats.custom(id) > 0) || (ctx != null && ctx.formStats().custom(id) > 0);
    }

    /** O aumento aparece em QUALQUER glifo da magia inteira (todos os estágios)? */
    public static boolean anywhere(SpellContext ctx, ResourceLocation id) {
        return ctx != null && ctx.rootSpell().anyStats(s -> s.custom(id) > 0);
    }

    private GebGlyphs() {}
}
