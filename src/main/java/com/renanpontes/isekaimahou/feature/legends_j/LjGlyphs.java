package com.renanpontes.isekaimahou.feature.legends_j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.legends_j.glyph.AlchemyEffects;
import com.renanpontes.isekaimahou.feature.legends_j.glyph.ChainEffects;
import com.renanpontes.isekaimahou.feature.legends_j.glyph.GeassEffect;
import com.renanpontes.isekaimahou.feature.legends_j.glyph.TengaiShinseiEffect;
import com.renanpontes.isekaimahou.feature.legends_j.glyph.TricksterEffects;
import com.renanpontes.isekaimahou.feature.legends_j.glyph.ZoltraakEffect;
import com.renanpontes.isekaimahou.glyph.AugmentGlyph;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;

import net.minecraft.resources.ResourceLocation;

/**
 * Glifos "de anime" do legends_j — NÃO são Magias Lendárias: são peças COMUNS do Grimório (efeitos e aumentos) que o
 * jogador combina à vontade com qualquer forma. A identidade do anime vem do efeito, do visual, do som e do nome.
 * <p>
 * <b>Como o jogador aprende</b>: igual a qualquer glifo comum (sem origem de classe, {@code legendary = false}): o
 * ELEMENTO + o TIER colocam o glifo no nó do caminho daquele elemento (os Arcanos chegam por nível no caminho Arcano)
 * e ele entra sozinho nos sorteios de pergaminhos — guilda, cidades, Mago Errante, guardiões elementais, chefes,
 * santuários, diário, reputação. Maestria e mutações de glifo funcionam sozinhas (o núcleo cuida).
 * <p>
 * Aumentos só marcam {@code SpellStats.custom}; o comportamento fica em {@link LjAugments}.
 */
public final class LjGlyphs {
    // ---------- Aumentos ----------
    /** Kokusen / Raio Negro (Jujutsu Kaisen): conjurar no "tempo certo" = dano ×2,5 com faíscas negras e vermelhas. */
    public static final ResourceLocation BLACK_FLASH = IsekaiMahou.id("black_flash");
    /** Starburst Stream (Sword Art Online): cada acerto vira uma rajada de 16 cortes de duas lâminas. */
    public static final ResourceLocation STARBURST = IsekaiMahou.id("starburst_stream");
    /** Excalibur (Fate): o feixe vira uma onda dourada enorme (mais grosso, perfura mais, fere mais mortos-vivos). */
    public static final ResourceLocation EXCALIBUR = IsekaiMahou.id("excalibur_light");

    private static final List<ResourceLocation> ALL = new ArrayList<>();

    static void register() {
        // Efeitos
        add(new TricksterEffects.BoogieWoogie());
        add(new TricksterEffects.Steal());
        add(new AlchemyEffects.FlameAlchemy());
        add(new AlchemyEffects.AlchemyWall());
        add(new ZoltraakEffect());
        add(new GeassEffect());
        add(new ChainEffects.ChainJail());
        add(new ChainEffects.HolyChain());
        add(new ChainEffects.DowsingChain());
        add(new TengaiShinseiEffect());

        // Aumentos: custo, elemento, tier do caminho, Círculo
        aug(BLACK_FLASH, 12, Element.SHADOW, 3, 3, 0, s -> { });
        aug(STARBURST, 18, Element.ARCANE, 4, 4, 0, s -> { });
        // Excalibur: "erguer a lâmina" (+0,5 s de conjuração), feixe bem mais grosso e que atravessa mais alvos
        aug(EXCALIBUR, 22, Element.LIGHT, 4, 5, 10, s -> {
            s.aoe += 2;
            s.pierce += 3;
        });
    }

    /** Ids de todos os glifos do módulo (comando de teste). */
    public static List<ResourceLocation> all() {
        return Collections.unmodifiableList(ALL);
    }

    private static void add(Glyph g) {
        ALL.add(GlyphRegistry.register(g).id());
    }

    private static void aug(ResourceLocation id, int cost, Element e, int tier, int circle, int castTicks,
                            java.util.function.Consumer<com.renanpontes.isekaimahou.glyph.SpellStats> extra) {
        // O marcador conta quantas cópias do aumento foram colocadas
        AugmentGlyph a = new AugmentGlyph(id.getPath(), cost, s -> {
            s.custom.merge(id, 1, Integer::sum);
            extra.accept(s);
        });
        if (castTicks > 0) a.castTicks(castTicks);
        a.element(e);
        a.tier(tier);
        a.minCircle(circle);
        ALL.add(GlyphRegistry.register(a).id());
    }

    private LjGlyphs() {}
}
