package com.renanpontes.isekaimahou.feature.glyphs_elements_a;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.glyphs_elements_a.glyph.FlattenEffect;
import com.renanpontes.isekaimahou.glyph.AugmentGlyph;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.TriggerGlyph;

import net.minecraft.resources.ResourceLocation;

/**
 * v1.4 — glifos dos 7 elementos base: 1 AUMENTO + 1 GATILHO por elemento, e o efeito utilitário Aplainar Terreno.
 * <p>
 * Todos são glifos COMUNS (sem origem): o elemento + tier os colocam no caminho do elemento (nó do tier, liberado pela
 * Provação), os Arcanos chegam por nível (caminho Arcano). Por serem comuns também entram sozinhos nos sorteios de
 * pergaminhos (guilda, cidades, guardiões elementais — só aumentos/efeitos —, ruínas), que gastam 1 essência do
 * elemento para aprender.
 * <p>
 * Aumentos guardam um marcador em {@code SpellStats.custom}; o comportamento fica em {@link ElementAugments}.
 * Gatilhos são reativos: o slot que começa com eles sai sozinho; o comportamento fica em {@link ElementTriggers}.
 */
public final class ElementGlyphsA {
    // ---------- Aumentos ----------
    /** Fogo: Combustão Tardia — o alvo atingido explode de novo 2 s depois com metade do dano. */
    public static final ResourceLocation AFTERBURN = IsekaiMahou.id("fire_afterburn");
    /** Água: Ressaca — puxa o alvo para você e o deixa Molhado; se já estava Molhado, também Gelado. */
    public static final ResourceLocation UNDERTOW = IsekaiMahou.id("water_undertow");
    /** Terra: Tremor Residual — 1 s depois o chão em volta do alvo treme (dano e Enrijecendo em quem está no chão). */
    public static final ResourceLocation AFTERSHOCK = IsekaiMahou.id("earth_aftershock");
    /** Vento: Ricochete do Vento — a magia segue o vento até o próximo inimigo na direção em que viajava. */
    public static final ResourceLocation WIND_RICOCHET = IsekaiMahou.id("wind_ricochet");
    /** Luz: Refração — parte do dano vira cura nos aliados mais feridos perto do alvo. */
    public static final ResourceLocation REFRACTION = IsekaiMahou.id("light_refraction");
    /** Sombra: Emboscada — +50% de dano em alvos desprevenidos (de costas, sem te ver); senão Corrompido. */
    public static final ResourceLocation AMBUSH = IsekaiMahou.id("shadow_ambush");
    /** Arcano: Mimetismo Arcano — ao causar dano, também aplica os efeitos da sua magia ANTERIOR (40%). */
    public static final ResourceLocation MIMIC = IsekaiMahou.id("arcane_mimic");

    // ---------- Gatilhos ----------
    public static final ResourceLocation ON_IGNITE = IsekaiMahou.id("trigger_on_ignite");
    public static final ResourceLocation ON_WET = IsekaiMahou.id("trigger_on_wet");
    public static final ResourceLocation ON_LANDING = IsekaiMahou.id("trigger_on_landing");
    public static final ResourceLocation ON_MOMENTUM = IsekaiMahou.id("trigger_on_momentum");
    public static final ResourceLocation ON_ALLY_HEAL = IsekaiMahou.id("trigger_on_ally_heal");
    public static final ResourceLocation IN_SHADOWS = IsekaiMahou.id("trigger_in_shadows");
    public static final ResourceLocation RESONANCE = IsekaiMahou.id("trigger_resonance");

    // ---------- Efeito utilitário ----------
    public static final ResourceLocation FLATTEN = IsekaiMahou.id("earth_flatten");

    /** Todos os gatilhos deste módulo (o slot que começa com qualquer um deles é reativo). */
    public static final List<ResourceLocation> TRIGGERS = List.of(ON_IGNITE, ON_WET, ON_LANDING, ON_MOMENTUM, ON_ALLY_HEAL,
            IN_SHADOWS, RESONANCE);

    private static final List<ResourceLocation> ALL = new ArrayList<>();

    static void register() {
        // Aumentos: custo, elemento, tier do caminho (2 = Aprendiz, 3 = Adepto, 4 = Mestre; Arcano: nível 5/10/18) e Círculo
        aug(AFTERBURN, 12, Element.FIRE, 2, 1);
        aug(UNDERTOW, 6, Element.WATER, 2, 1);
        aug(AFTERSHOCK, 10, Element.EARTH, 3, 2);
        aug(WIND_RICOCHET, 12, Element.WIND, 3, 2);
        aug(REFRACTION, 12, Element.LIGHT, 3, 2);
        aug(AMBUSH, 10, Element.SHADOW, 2, 1);
        aug(MIMIC, 16, Element.ARCANE, 4, 3);

        trig(ON_IGNITE, 6, Element.FIRE, 2, 2);
        trig(ON_WET, 5, Element.WATER, 2, 2);
        trig(ON_LANDING, 5, Element.EARTH, 2, 2);
        trig(ON_MOMENTUM, 6, Element.WIND, 3, 2);
        trig(ON_ALLY_HEAL, 8, Element.LIGHT, 3, 3);
        trig(IN_SHADOWS, 6, Element.SHADOW, 3, 2);
        trig(RESONANCE, 8, Element.ARCANE, 3, 3);

        ALL.add(GlyphRegistry.register(new FlattenEffect()).id());
    }

    /** Ids de todos os glifos do módulo (comando de teste). */
    public static List<ResourceLocation> all() {
        return Collections.unmodifiableList(ALL);
    }

    private static void aug(ResourceLocation id, int cost, Element e, int tier, int circle) {
        // O marcador conta quantas vezes o aumento foi colocado (os que acumulam leem o número)
        AugmentGlyph a = new AugmentGlyph(id.getPath(), cost, s -> s.custom.merge(id, 1, Integer::sum));
        config(a, e, tier, circle);
        ALL.add(GlyphRegistry.register(a).id());
    }

    private static void trig(ResourceLocation id, int cost, Element e, int tier, int circle) {
        TriggerGlyph t = new TriggerGlyph(id.getPath(), cost, true);
        config(t, e, tier, circle);
        ALL.add(GlyphRegistry.register(t).id());
    }

    private static void config(Glyph g, Element e, int tier, int circle) {
        g.element(e);
        g.tier(tier);
        g.minCircle(circle);
    }

    private ElementGlyphsA() {}
}
