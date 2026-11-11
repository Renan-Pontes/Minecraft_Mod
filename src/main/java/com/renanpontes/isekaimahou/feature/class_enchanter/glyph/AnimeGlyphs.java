package com.renanpontes.isekaimahou.feature.class_enchanter.glyph;

import com.renanpontes.isekaimahou.feature.class_enchanter.Transformations;
import com.renanpontes.isekaimahou.feature.class_enchanter.effect.TransformEffect;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.world.entity.LivingEntity;

/**
 * Pacote de TÉCNICAS DE ANIME do Encantador (glifos de origem; outras classes só por Lição, com os limites base):
 * <ul>
 *   <li>{@code eight_gates} — Oito Portões Internos (rank III; até 8 portões no rank V);</li>
 *   <li>{@code kaioken} — Kaioken ×2/×3/×10 (rank III; ×3 no III, ×10 no V);</li>
 *   <li>{@code enchanter_gear_second} — Segunda Marcha (rank II);</li>
 *   <li>{@code enchanter_gear_fourth} — Quarta Marcha: Homem-Mola (rank IV);</li>
 *   <li>{@code ultra_instinct} — Instinto Superior (rank IV; também em aliados).</li>
 * </ul>
 * Todas são encantamentos (combinam com Si Mesmo, Elo, Santuário...), só em você (menos o Instinto) e, com Inverter
 * num inimigo, viram uma versão "maldição". A regra de cada uma está em {@link Transformations}.
 */
public final class AnimeGlyphs {
    public static final String GATES = "eight_gates";
    public static final String KAIOKEN = "kaioken";
    public static final String GEAR_SECOND = "enchanter_gear_second";
    public static final String GEAR_FOURTH = "enchanter_gear_fourth";
    public static final String ULTRA = "ultra_instinct";

    /** Base das transformações: em si mesmo (aviso se mirar um aliado), Inverter = maldição no inimigo. */
    abstract static class TransformGlyph extends EnchanterGlyphs.EnchantGlyph {
        private final TransformEffect.Kind kind;
        private final boolean allies;

        TransformGlyph(String id, int cost, TransformEffect.Kind kind, boolean allies) {
            super(id, cost);
            this.kind = kind;
            this.allies = allies;
            color(kind.color);
        }

        @Override
        void bless(SpellContext ctx, LivingEntity t, SpellStats stats, boolean self) {
            if (!self && !allies) {
                EnchanterGlyphs.selfOnly(ctx, "message.isekai_mahou.enchanter.self_only_transform");
                return;
            }
            transform(ctx, t, stats);
        }

        abstract void transform(SpellContext ctx, LivingEntity t, SpellStats stats);

        @Override
        void curse(SpellContext ctx, LivingEntity t, SpellStats stats) {
            Transformations.curse(ctx, t, stats, kind);
        }
    }

    // =====================================================================================
    //  Oito Portões Internos
    // =====================================================================================
    public static class EightGates extends TransformGlyph {
        public EightGates() {
            super("eight_gates", 24, TransformEffect.Kind.GATES, false);
            tier(3);
            minCircle(3);
        }

        @Override
        void transform(SpellContext ctx, LivingEntity t, SpellStats stats) {
            Transformations.openGates(ctx, t, stats);
        }

        @Override
        public float baseDamage() { return 4F; }

        @Override
        public int hintDuration() { return 400; }
    }

    // =====================================================================================
    //  Kaioken
    // =====================================================================================
    public static class Kaioken extends TransformGlyph {
        public Kaioken() {
            super("kaioken", 22, TransformEffect.Kind.KAIOKEN, false);
            tier(3);
            minCircle(3);
        }

        @Override
        void transform(SpellContext ctx, LivingEntity t, SpellStats stats) {
            Transformations.kaioken(ctx, t, stats);
        }

        @Override
        public float baseDamage() { return 6F; }

        @Override
        public int hintDuration() { return 300; }
    }

    // =====================================================================================
    //  Segunda Marcha
    // =====================================================================================
    public static class GearSecond extends TransformGlyph {
        public GearSecond() {
            super("enchanter_gear_second", 14, TransformEffect.Kind.GEAR_SECOND, false);
            tier(2);
            minCircle(2);
        }

        @Override
        void transform(SpellContext ctx, LivingEntity t, SpellStats stats) {
            Transformations.gearSecond(ctx, t, stats);
        }

        @Override
        public float baseDamage() { return 5F; }

        @Override
        public int hintDuration() { return 400; }
    }

    // =====================================================================================
    //  Quarta Marcha: Homem-Mola
    // =====================================================================================
    public static class GearFourth extends TransformGlyph {
        public GearFourth() {
            super("enchanter_gear_fourth", 34, TransformEffect.Kind.GEAR_FOURTH, false);
            tier(4);
            minCircle(5);
        }

        @Override
        void transform(SpellContext ctx, LivingEntity t, SpellStats stats) {
            Transformations.gearFourth(ctx, t, stats);
        }

        @Override
        public float baseDamage() { return 12F; }

        @Override
        public int hintDuration() { return 400; }
    }

    // =====================================================================================
    //  Instinto Superior
    // =====================================================================================
    public static class UltraInstinct extends TransformGlyph {
        public UltraInstinct() {
            super("ultra_instinct", 26, TransformEffect.Kind.ULTRA_INSTINCT, true);
            tier(4);
            minCircle(5);
        }

        @Override
        void transform(SpellContext ctx, LivingEntity t, SpellStats stats) {
            Transformations.ultraInstinct(ctx, t, stats);
        }

        @Override
        public float baseDamage() { return 3F; }

        @Override
        public int hintDuration() { return 160; }
    }

    private AnimeGlyphs() {}
}
