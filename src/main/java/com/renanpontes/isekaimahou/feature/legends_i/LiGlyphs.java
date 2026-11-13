package com.renanpontes.isekaimahou.feature.legends_i;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.legends_i.glyph.NarutoGlyphs;
import com.renanpontes.isekaimahou.feature.legends_i.glyph.OnePieceGlyphs;
import com.renanpontes.isekaimahou.feature.legends_i.glyph.PainGlyphs;
import com.renanpontes.isekaimahou.feature.legends_i.glyph.UchihaGlyphs;
import com.renanpontes.isekaimahou.glyph.AugmentGlyph;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.resources.ResourceLocation;

/**
 * Glifos de ANIME do legends_i — peças COMUNS do Grimório (não são Magias Lendárias): efeitos e aumentos que o jogador
 * mistura livremente com qualquer forma. A identidade do anime está no efeito, no visual, no som e no nome.
 * <p>
 * Todos são glifos sem origem e sem "lendário": elemento + tier + Círculo os colocam no caminho do elemento e nos
 * sorteios de pergaminhos de sempre (Mago Andarilho, ruínas, guilda, chefes, reputação das cidades) — igual aos glifos
 * dos módulos glyphs_elements/fusions. Maestria e mutações valem sozinhas (o núcleo decide pela família do glifo).
 *
 * <table>
 *   <tr><th>Glifo</th><th>Tipo</th><th>Elemento</th><th>Tier</th><th>Círculo</th><th>Custo</th></tr>
 *   <tr><td>Haki do Armamento</td><td>aumento</td><td>Arcano</td><td>3</td><td>2</td><td>14</td></tr>
 *   <tr><td>Haki da Observação</td><td>efeito</td><td>Arcano</td><td>2</td><td>1</td><td>14</td></tr>
 *   <tr><td>Haki do Rei</td><td>efeito</td><td>Arcano</td><td>4</td><td>4</td><td>30</td></tr>
 *   <tr><td>Gomu Gomu no Pistol</td><td>efeito</td><td>Arcano</td><td>2</td><td>1</td><td>10</td></tr>
 *   <tr><td>Shinra Tensei</td><td>efeito</td><td>Gravidade</td><td>3</td><td>3</td><td>22</td></tr>
 *   <tr><td>Bansho Ten'in</td><td>efeito</td><td>Gravidade</td><td>2</td><td>3</td><td>12</td></tr>
 *   <tr><td>Chibaku Tensei</td><td>efeito</td><td>Gravidade</td><td>4</td><td>5</td><td>40</td></tr>
 *   <tr><td>Shuriken de Papel</td><td>efeito</td><td>Vento</td><td>2</td><td>1</td><td>12</td></tr>
 *   <tr><td>Asas de Papel</td><td>aumento</td><td>Vento</td><td>2</td><td>2</td><td>8</td></tr>
 *   <tr><td>Amaterasu</td><td>efeito</td><td>Fogo Infernal</td><td>3</td><td>4</td><td>24</td></tr>
 *   <tr><td>Susanoo</td><td>efeito</td><td>Sombra</td><td>4</td><td>5</td><td>36</td></tr>
 *   <tr><td>Chidori</td><td>efeito</td><td>Raio</td><td>3</td><td>3</td><td>18</td></tr>
 *   <tr><td>Kage Bunshin</td><td>efeito</td><td>Sombra</td><td>3</td><td>3</td><td>26</td></tr>
 * </table>
 * Rasengan ficou de fora: a "Esfera Espiral" (legends_b) já é um glifo comum de forma.
 */
public final class LiGlyphs {
    // ---------- Aumentos (marcadores em SpellStats.custom; comportamento em LiEvents) ----------
    /** Haki do Armamento: o dano da magia ignora parte da armadura e das proteções e acerta quem está intangível. */
    public static final ResourceLocation HAKI_ARMAMENT = IsekaiMahou.id("haki_armament");
    /** Asas de Papel: ao conjurar, asas de papel nas costas — você plana por um tempo. */
    public static final ResourceLocation PAPER_WINGS = IsekaiMahou.id("paper_wings");

    private static final List<ResourceLocation> ALL = new ArrayList<>();

    static void register() {
        aug(HAKI_ARMAMENT, 14, Element.ARCANE, 3, 2);
        aug(PAPER_WINGS, 8, Element.WIND, 2, 2);

        for (EffectGlyph g : OnePieceGlyphs.all()) add(g);
        for (EffectGlyph g : PainGlyphs.all()) add(g);
        for (EffectGlyph g : NarutoGlyphs.all()) add(g);
        for (EffectGlyph g : UchihaGlyphs.all()) add(g);
    }

    /** Ids de todos os glifos do módulo (comando de teste). */
    public static List<ResourceLocation> all() {
        return Collections.unmodifiableList(ALL);
    }

    private static void add(EffectGlyph g) {
        ALL.add(GlyphRegistry.register(g).id());
    }

    private static void aug(ResourceLocation id, int cost, Element e, int tier, int circle) {
        // O marcador conta quantas vezes o aumento foi colocado (os que acumulam leem o número)
        AugmentGlyph a = new AugmentGlyph(id.getPath(), cost, s -> s.custom.merge(id, 1, Integer::sum));
        a.element(e);
        a.tier(tier);
        a.minCircle(circle);
        ALL.add(GlyphRegistry.register(a).id());
    }

    /** Quantas vezes o aumento {@code id} vale para este dano/efeito (depois do efeito, ou depois da forma). */
    public static int stacks(SpellContext ctx, SpellStats st, ResourceLocation id) {
        int n = st != null ? st.custom(id) : 0;
        return Math.max(n, ctx.formStats().custom(id));
    }

    /** Configuração comum dos efeitos: elemento, tier e Círculo. */
    public static <T extends Glyph> T config(T g, Element e, int tier, int circle) {
        g.element(e);
        g.tier(tier);
        g.minCircle(circle);
        return g;
    }

    private LiGlyphs() {}
}
