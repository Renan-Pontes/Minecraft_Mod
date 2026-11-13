package com.renanpontes.isekaimahou.feature.glyphs_classes_b.glyph;

import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.glyphs_classes_b.GcbIds;
import com.renanpontes.isekaimahou.glyph.AugmentGlyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellStats;
import com.renanpontes.isekaimahou.glyph.TriggerGlyph;

import net.minecraft.resources.ResourceLocation;

/**
 * Os 14 glifos de ORIGEM da v1.4 (um aumento e um gatilho por classe). Todos neutros (Arcano): servem em magia de
 * qualquer elemento. Comportamento:
 * <ul>
 *   <li>aumentos — {@code augment/*} (cada um ouve SpellCastEvent / SpellDamageEvent e lê {@link SpellStats#custom});</li>
 *   <li>gatilhos — {@code TriggerEvents} + {@code TriggerCaster} (a tecla do slot não conjura; ele sai sozinho).</li>
 * </ul>
 * Tier/Círculo definem o pedido da Lição para quem é de outra classe; a própria classe aprende pelo rank
 * ({@code ClassUnlocks}).
 */
public final class GcbGlyphs {

    /** Aumento de módulo: só marca o próprio id em {@link SpellStats#custom} (até {@code max} cópias contam). */
    private static class ModAugment extends AugmentGlyph {
        ModAugment(String path, int cost, ResourceLocation id, int max, MagicPath origin, int tier, int circle) {
            super(path, cost, s -> {
                if (s.custom(id) < max) s.custom.merge(id, 1, Integer::sum);
            });
            origin(origin);
            tier(tier);
            minCircle(circle);
        }
    }

    private static class ModTrigger extends TriggerGlyph {
        ModTrigger(String path, int cost, MagicPath origin, int tier, int circle) {
            super(path, cost, true);
            origin(origin);
            tier(tier);
            minCircle(circle);
        }
    }

    // ------------------------------------------------------------------ Encantador
    /** Runa Gravada: inimigos atingidos ganham uma runa; o próximo golpe corpo a corpo detona os efeitos de novo (60%). */
    public static final class EngravedRune extends ModAugment {
        public EngravedRune() { super("enchanter_engraved_rune", 9, GcbIds.ENGRAVED_RUNE, 1, MagicPath.ENCHANTER, 3, 2); }
    }

    /** Ao Golpear com Arma Encantada (gatilho). */
    public static final class OnEnchantedStrike extends ModTrigger {
        public OnEnchantedStrike() { super("enchanter_on_enchanted_strike", 5, MagicPath.ENCHANTER, 3, 3); }
    }

    // ------------------------------------------------------------------ Shinigami
    /** Shunpo: Conjuração Relâmpago — some e reaparece nas costas do alvo; a magia sai de lá. */
    public static final class ShunpoCast extends ModAugment {
        public ShunpoCast() { super("shinigami_shunpo_cast", 8, GcbIds.SHUNPO_CAST, 2, MagicPath.SHINIGAMI, 2, 2); }
    }

    /** Ao Cortar com a Lâmina Liberada (gatilho). */
    public static final class OnReleasedCut extends ModTrigger {
        public OnReleasedCut() { super("shinigami_on_released_cut", 5, MagicPath.SHINIGAMI, 3, 3); }
    }

    // ------------------------------------------------------------------ Quincy
    /** Sklaverei: arranca o Reishi de quem a magia atinge (Reishi guardado + Foco). */
    public static final class Sklaverei extends ModAugment {
        public Sklaverei() { super("quincy_sklaverei", 10, GcbIds.SKLAVEREI, 1, MagicPath.QUINCY, 4, 4); }
    }

    /** Ao Absorver Reishi (gatilho): gasta 3 de Reishi guardado por disparo. */
    public static final class OnReishi extends ModTrigger {
        public OnReishi() { super("quincy_on_reishi", 4, MagicPath.QUINCY, 2, 2); }
    }

    // ------------------------------------------------------------------ Pistoleiro
    /** Ricochete Calculado: a forma quica de inimigo em inimigo, +25% de poder a cada ricochete (1 por cópia, máx. 3). */
    public static final class TrickRicochet extends ModAugment {
        public TrickRicochet() { super("gun_trick_ricochet", 9, GcbIds.TRICK_RICOCHET, 3, MagicPath.GUNSLINGER, 3, 3); }
    }

    /** Ao Acertar na Cabeça (gatilho). */
    public static final class OnHeadshot extends ModTrigger {
        public OnHeadshot() { super("gun_on_headshot", 5, MagicPath.GUNSLINGER, 2, 2); }
    }

    // ------------------------------------------------------------------ Patrulheiro
    /** Ataque Coordenado: seus companheiros repetem a magia (50%) de onde estão, no seu alvo. */
    public static final class Coordinated extends ModAugment {
        public Coordinated() { super("ranger_coordinated_strike", 12, GcbIds.COORDINATED, 1, MagicPath.RANGER, 3, 3); }
    }

    /** Ao Marcar um Alvo (gatilho). */
    public static final class OnMark extends ModTrigger {
        public OnMark() { super("ranger_on_mark", 4, MagicPath.RANGER, 2, 2); }
    }

    // ------------------------------------------------------------------ Apostador
    /** Roleta Russa: tambor de 6; cada clique vazio dá mais poder, a bala acerta VOCÊ. */
    public static final class RussianRoulette extends ModAugment {
        public RussianRoulette() { super("gambler_russian_roulette", 3, GcbIds.RUSSIAN_ROULETTE, 1, MagicPath.GAMBLER, 2, 2); }
    }

    /** Ao Tirar Sorte Grande (gatilho). */
    public static final class OnBigWin extends ModTrigger {
        public OnBigWin() { super("gambler_on_big_win", 6, MagicPath.GAMBLER, 3, 3); }
    }

    // ------------------------------------------------------------------ Steel Ball
    /** Rotação Dourada: o giro entra no alvo e cresce na proporção áurea (pulsos ×1,618). */
    public static final class GoldenSpin extends ModAugment {
        public GoldenSpin() { super("steelball_golden_spin", 12, GcbIds.GOLDEN_SPIN, 1, MagicPath.STEELBALL, 4, 4); }
    }

    /** Ao Acertar a Bola de Aço (gatilho). */
    public static final class OnBallHit extends ModTrigger {
        public OnBallHit() { super("steelball_on_ball_hit", 4, MagicPath.STEELBALL, 2, 2); }
    }

    public static void register() {
        GlyphRegistry.register(new EngravedRune());
        GlyphRegistry.register(new OnEnchantedStrike());
        GlyphRegistry.register(new ShunpoCast());
        GlyphRegistry.register(new OnReleasedCut());
        GlyphRegistry.register(new Sklaverei());
        GlyphRegistry.register(new OnReishi());
        GlyphRegistry.register(new TrickRicochet());
        GlyphRegistry.register(new OnHeadshot());
        GlyphRegistry.register(new Coordinated());
        GlyphRegistry.register(new OnMark());
        GlyphRegistry.register(new RussianRoulette());
        GlyphRegistry.register(new OnBigWin());
        GlyphRegistry.register(new GoldenSpin());
        GlyphRegistry.register(new OnBallHit());
    }

    private GcbGlyphs() {}
}
