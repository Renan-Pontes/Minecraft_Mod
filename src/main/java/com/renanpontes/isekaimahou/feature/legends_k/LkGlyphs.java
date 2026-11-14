package com.renanpontes.isekaimahou.feature.legends_k;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.legends_k.glyph.DimensionEffects;
import com.renanpontes.isekaimahou.feature.legends_k.glyph.ExtraTechniques;
import com.renanpontes.isekaimahou.feature.legends_k.glyph.ProjectionEffect;
import com.renanpontes.isekaimahou.feature.legends_k.glyph.ReturnToZeroEffect;
import com.renanpontes.isekaimahou.feature.legends_k.glyph.RikkaEffects;
import com.renanpontes.isekaimahou.feature.legends_k.glyph.SpiritualPressureEffect;
import com.renanpontes.isekaimahou.feature.legends_k.glyph.TitanModeBody;
import com.renanpontes.isekaimahou.feature.legends_k.glyph.VectorEffects;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;

import net.minecraft.resources.ResourceLocation;

/**
 * Técnicas de anime NAS CLASSES (legends_k, pedido do dono 2026-11-13: "vamos adicionar técnicas dos animes nas
 * classes?"). Todas são glifos de EFEITO comuns do Grimório — não Magias Lendárias — e de ORIGEM de uma classe
 * ({@code origin(MagicPath.X)}): só a própria classe aprende sozinha (por Círculo, ver {@link LkUnlocks}); outras
 * classes só por Lição. Maestria e mutações de glifo funcionam sozinhas (o núcleo cuida).
 * <pre>
 *   Ladino       Kamui                         Steel Ball   Entre Dimensões (D4C), Ponto Zero (Réquiem)
 *   Shinigami    Pressão Espiritual            Ferreiro     Modo Titã (corpo da Transformação)
 *   Encantador   Santen Kesshun, Sōten Kisshun, Koten Zanshun (Orihime)
 *   Pistoleiro   Projeção (Naoya)              Mago         Reflexão / Soco / Explosão de Vetores (Accelerator)
 *   Necromante   Sapo das Sombras              Bruxo        Roda de Mahoraga
 *   Cavaleiro    Calmaria                      Bruxa        Retorno pela Morte
 *   Druida       Campo de Flores               Patrulheiro  Possessão das Sombras
 *   Apostador    Aschente
 * </pre>
 */
public final class LkGlyphs {
    public static final ResourceLocation KAMUI = IsekaiMahou.id("lk_kamui");
    public static final ResourceLocation D4C = IsekaiMahou.id("lk_d4c");
    public static final ResourceLocation SPIRITUAL_PRESSURE = IsekaiMahou.id("lk_spiritual_pressure");
    public static final ResourceLocation TITAN_MODE = IsekaiMahou.id("lk_titan_mode");
    public static final ResourceLocation RETURN_TO_ZERO = IsekaiMahou.id("lk_return_to_zero");
    public static final ResourceLocation SANTEN_KESSHUN = IsekaiMahou.id("lk_santen_kesshun");
    public static final ResourceLocation SOTEN_KISSHUN = IsekaiMahou.id("lk_soten_kisshun");
    public static final ResourceLocation KOTEN_ZANSHUN = IsekaiMahou.id("lk_koten_zanshun");
    public static final ResourceLocation PROJECTION = IsekaiMahou.id("lk_projection");
    public static final ResourceLocation VECTOR_REFLECT = IsekaiMahou.id("lk_vector_reflect");
    public static final ResourceLocation VECTOR_PUNCH = IsekaiMahou.id("lk_vector_punch");
    public static final ResourceLocation VECTOR_BURST = IsekaiMahou.id("lk_vector_burst");
    public static final ResourceLocation SHADOW_TOAD = IsekaiMahou.id("lk_shadow_toad");
    public static final ResourceLocation MAHORAGA = IsekaiMahou.id("lk_mahoraga_wheel");
    public static final ResourceLocation DEAD_CALM = IsekaiMahou.id("lk_dead_calm");
    public static final ResourceLocation RETURN_BY_DEATH = IsekaiMahou.id("lk_return_by_death");
    public static final ResourceLocation FLOWER_FIELD = IsekaiMahou.id("lk_flower_field");
    public static final ResourceLocation SHADOW_POSSESSION = IsekaiMahou.id("lk_shadow_possession");
    public static final ResourceLocation ASCHENTE = IsekaiMahou.id("lk_aschente");

    private static final List<ResourceLocation> ALL = new ArrayList<>();

    static void register() {
        add(new DimensionEffects.Kamui());
        add(new DimensionEffects.D4C());
        add(new SpiritualPressureEffect());
        add(new TitanModeBody());
        add(new ReturnToZeroEffect());
        add(new RikkaEffects.SantenKesshun());
        add(new RikkaEffects.SotenKisshun());
        add(new RikkaEffects.KotenZanshun());
        add(new ProjectionEffect());
        add(new VectorEffects.Reflect());
        add(new VectorEffects.Punch());
        add(new VectorEffects.Burst());
        add(new ExtraTechniques.ShadowToad());
        add(new ExtraTechniques.MahoragaWheel());
        add(new ExtraTechniques.DeadCalm());
        add(new ExtraTechniques.ReturnByDeath());
        add(new ExtraTechniques.FlowerField());
        add(new ExtraTechniques.ShadowPossession());
        add(new ExtraTechniques.Aschente());
    }

    /** Ids de todos os glifos do módulo (comando de teste). */
    public static List<ResourceLocation> all() {
        return Collections.unmodifiableList(ALL);
    }

    private static void add(Glyph g) {
        ALL.add(GlyphRegistry.register(g).id());
    }

    private LkGlyphs() {}
}
