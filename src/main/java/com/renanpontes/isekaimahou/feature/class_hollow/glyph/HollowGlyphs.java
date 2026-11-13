package com.renanpontes.isekaimahou.feature.class_hollow.glyph;

import java.util.List;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.anim.MahouAnims;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.feature.class_hollow.Hollow;
import com.renanpontes.isekaimahou.feature.class_hollow.HollowState;
import com.renanpontes.isekaimahou.feature.class_hollow.tech.HollowMoves;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;

/**
 * Glifos da classe Hollow.
 * <p>
 * <b>v1.4:</b> as técnicas do Hollow (Cero, Bala, Sonído, Gran Rey Cero...) deixaram de ser FORMAS para montar magia —
 * agora são técnicas inatas com tecla própria ({@link com.renanpontes.isekaimahou.feature.class_hollow.tech.HollowTech}).
 * Os 10 glifos de forma antigos continuam REGISTRADOS (mundos e magias salvas carregam): não são mais dados pela
 * evolução e, numa magia salva, cada um executa a técnica correspondente (carga cheia; os aumentos da receita valem).
 * <p>
 * Glifos NOVOS da classe (contrato v1.4), aprendidos pela evolução:
 * <ul>
 *   <li>{@link CeroFusionAugment} "Cero Fusionado" (aumento, Adjuchas): a magia também dispara um Cero da boca;</li>
 *   <li>{@link DevourTrigger} "Ao Devorar uma Alma" (gatilho, Gillian): o slot sai sozinho quando você devora uma alma.</li>
 * </ul>
 */
public final class HollowGlyphs {
    /** Os glifos de forma antigos (v1.3), aposentados. */
    public static final List<String> LEGACY = List.of(Hollow.CERO, Hollow.BALA, Hollow.PESQUISA, Hollow.REGEN, Hollow.HIERRO,
            Hollow.SONIDO, Hollow.GARGANTA, Hollow.GRAN_REY_CERO, Hollow.DESCORRER, Hollow.RESURRECCION);

    public static void register() {
        GlyphRegistry.register(new LegacyForm(Hollow.CERO, 14, Element.SHADOW, 1, 1, 16, 60) {
            @Override
            public boolean stageable() {
                return true;
            }

            @Override
            void run(SpellContext ctx) {
                HollowMoves.cero(ctx, 1F);
            }
        });
        GlyphRegistry.register(new LegacyForm(Hollow.BALA, 4, Element.SHADOW, 1, 1, 0, 6) {
            @Override
            void run(SpellContext ctx) {
                HollowMoves.bala(ctx);
            }
        });
        GlyphRegistry.register(new LegacyForm(Hollow.PESQUISA, 6, Element.SHADOW, 1, 1, 0, 200) {
            @Override
            void run(SpellContext ctx) {
                HollowMoves.pesquisa(ctx);
            }
        });
        GlyphRegistry.register(new LegacyForm(Hollow.REGEN, 6, Element.SHADOW, 1, 1, 0, 300) {
            @Override
            void run(SpellContext ctx) {
                HollowMoves.regen(ctx);
            }
        });
        GlyphRegistry.register(new LegacyForm(Hollow.HIERRO, 8, Element.EARTH, 2, 2, 0, 400) {
            @Override
            void run(SpellContext ctx) {
                HollowMoves.hierro(ctx);
            }
        });
        GlyphRegistry.register(new LegacyForm(Hollow.SONIDO, 5, Element.WIND, 2, 4, 0, 20) {
            @Override
            void run(SpellContext ctx) {
                HollowMoves.sonido(ctx);
            }
        });
        GlyphRegistry.register(new LegacyForm(Hollow.GARGANTA, 30, Element.SHADOW, 3, 4, 30, 600) {
            @Override
            void run(SpellContext ctx) {
                HollowMoves.garganta(ctx);
            }
        });
        GlyphRegistry.register(new LegacyForm(Hollow.GRAN_REY_CERO, 60, Element.SHADOW, 5, 8, 40, 900) {
            @Override
            void run(SpellContext ctx) {
                HollowMoves.granRey(ctx, 1F);
            }
        });
        GlyphRegistry.register(new LegacyForm(Hollow.DESCORRER, 20, Element.SHADOW, 4, 8, 10, 160) {
            @Override
            void run(SpellContext ctx) {
                HollowMoves.descorrer(ctx);
            }
        });
        GlyphRegistry.register(new LegacyForm(Hollow.RESURRECCION, 80, Element.SHADOW, 5, 8, 30, 1200) {
            @Override
            public boolean isUltimate() {
                return true;
            }

            @Override
            void run(SpellContext ctx) {
                if (!(ctx.caster() instanceof ServerPlayer sp)) return;
                if (HollowState.released(sp) != null) {
                    sp.displayClientMessage(Hollow.msg("release_already").withStyle(ChatFormatting.GRAY), true);
                    return;
                }
                Hollow.Release r = Hollow.release(com.renanpontes.isekaimahou.core.MagicApi.get(sp));
                if (r != null) Announce.send(sp, Hollow.msg("release_shout", r.command()), r.rgb, Announce.STYLE_ULTIMATE);
                MahouAnims.play(sp, MahouAnims.RESURRECCION);
                HollowMoves.resurreccionBurst(ctx, ctx.formStats().duration(1200));
            }
        });
        GlyphRegistry.register(new CeroFusionAugment());
        GlyphRegistry.register(new DevourTrigger());
    }

    /**
     * Glifo de forma ANTIGO (v1.3) do Hollow: continua montável numa magia salva e executa a técnica nova. Não precisa de
     * efeito (efeitos somados ainda se aplicam onde a técnica acerta).
     */
    abstract static class LegacyForm extends FormGlyph {
        private final int castTicks, cooldown;

        LegacyForm(String id, int cost, Element element, int tier, int minCircle, int castTicks, int cooldown) {
            super(id, cost);
            origin(MagicPath.HOLLOW);
            element(element);
            tier(tier);
            if (minCircle > 1) minCircle(minCircle);
            this.castTicks = castTicks;
            this.cooldown = cooldown;
        }

        @Override
        public boolean requiresEffect() {
            return false;
        }

        @Override
        public int baseCastTicks() {
            return castTicks;
        }

        @Override
        public int baseCooldown() {
            return cooldown;
        }

        abstract void run(SpellContext ctx);

        @Override
        public void cast(SpellContext ctx) {
            if (ctx.caster() == null) return;
            run(ctx);
        }
    }

    private HollowGlyphs() {}
}
