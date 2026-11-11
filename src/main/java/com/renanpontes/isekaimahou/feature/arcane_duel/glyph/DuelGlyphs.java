package com.renanpontes.isekaimahou.feature.arcane_duel.glyph;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.glyph.AugmentGlyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellStats;
import com.renanpontes.isekaimahou.glyph.TriggerGlyph;

import net.minecraft.resources.ResourceLocation;

/**
 * Glifos do arcane_duel. Aumentos guardam a marca em {@link SpellStats#custom} (o efeito vem de {@code DuelEvents}
 * pelo {@code SpellDamageEvent}); Instantâneo usa a marca do núcleo ({@link SpellStats#INSTANT}).
 */
public final class DuelGlyphs {
    public static final ResourceLocation DEATHTOUCH = IsekaiMahou.id("deathtouch");
    public static final ResourceLocation TRAMPLE = IsekaiMahou.id("trample");
    public static final ResourceLocation IN_RESPONSE = IsekaiMahou.id("in_response");

    /** Em Resposta: gatilho reativo — o slot fica "preparado" e sai sozinho quando algo hostil entra na Pilha. */
    public static final class InResponseTrigger extends TriggerGlyph {
        public InResponseTrigger() {
            super("in_response", 6, true);
            element(Element.ARCANE);
            tier(2);
            minCircle(2);
        }
    }

    /** Toque Mortal: o dano deste glifo mata mob não-chefe que fique abaixo de 25% da vida; em chefes, +15%. */
    public static final class DeathtouchAugment extends AugmentGlyph {
        public DeathtouchAugment() {
            super("deathtouch", 15, s -> s.custom.merge(DEATHTOUCH, 1, Integer::sum));
            element(Element.ARCANE);
            tier(4);
            minCircle(4);
        }
    }

    /** Atropelar: o dano que sobra ao matar passa para o inimigo mais próximo (até 3 saltos). */
    public static final class TrampleAugment extends AugmentGlyph {
        public TrampleAugment() {
            super("trample", 10, s -> s.custom.merge(TRAMPLE, 1, Integer::sum));
            element(Element.ARCANE);
            tier(3);
            minCircle(3);
        }
    }

    /** Instantâneo: conjuração na hora, fora da Pilha (não pode ser contrariada); o núcleo cobra +50% de mana. */
    public static final class InstantAugment extends AugmentGlyph {
        public InstantAugment() {
            super("instant", 6, s -> s.custom.merge(SpellStats.INSTANT, 1, Integer::sum));
            element(Element.ARCANE);
            tier(3);
            minCircle(3);
        }
    }

    public static void register() {
        GlyphRegistry.register(new CounterEffects.ManaLeakEffect());
        GlyphRegistry.register(new CounterEffects.RedirectEffect());
        GlyphRegistry.register(new CounterEffects.ForkEffect());
        GlyphRegistry.register(new CounterEffects.SpellStealEffect());
        GlyphRegistry.register(new DeathtouchAugment());
        GlyphRegistry.register(new TrampleAugment());
        GlyphRegistry.register(new InstantAugment());
        GlyphRegistry.register(new WardEffects.HexproofEffect());
        GlyphRegistry.register(new WardEffects.IndestructibleEffect());
        GlyphRegistry.register(new JudgmentEffects.ExileEffect());
        GlyphRegistry.register(new JudgmentEffects.WrathEffect());
        GlyphRegistry.register(new WardEffects.TimeWalkEffect());
        GlyphRegistry.register(new InResponseTrigger());
    }

    private DuelGlyphs() {}
}
