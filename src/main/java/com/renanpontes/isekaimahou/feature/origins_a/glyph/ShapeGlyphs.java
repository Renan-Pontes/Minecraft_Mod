package com.renanpontes.isekaimahou.feature.origins_a.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.origins_a.OaUtil;
import com.renanpontes.isekaimahou.feature.origins_a.shape.Shape;
import com.renanpontes.isekaimahou.feature.origins_a.shape.ShapeEvents;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * METAMORFOSE — Magia de Origem do Druida. Cada glifo transforma o jogador (você ou um aliado) num animal
 * por algum tempo; conjurar a mesma forma de novo desfaz. Só jogadores mudam de forma.
 */
public final class ShapeGlyphs {

    public static void register() {
        GlyphRegistry.register(new WolfForm());
        GlyphRegistry.register(new BearForm());
        GlyphRegistry.register(new FalconForm());
        GlyphRegistry.register(new DolphinForm());
        GlyphRegistry.register(new SpiderForm());
        GlyphRegistry.register(new ElementalBeastForm());
    }

    /** Base: efeito benéfico que alterna a forma no alvo (jogador). */
    abstract static class ShapeGlyph extends EffectGlyph {
        private final Shape shape;

        ShapeGlyph(String id, int cost, Shape shape) {
            super(id, cost);
            this.shape = shape;
        }

        @Override
        public boolean isBeneficial() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof ServerPlayer sp)) return;
            switch (ShapeEvents.toggle(sp, shape, ctx.element(), stats.duration(shape.baseDuration))) {
                case ENTERED -> OaUtil.actionbar(sp, "shape_enter", displayName());
                case LEFT -> OaUtil.actionbar(sp, "shape_leave");
                case NO_ROOM -> {
                    OaUtil.actionbar(sp, "shape_no_room", displayName());
                    ctx.level().sendParticles(Vfx.smoke(), sp.getX(), sp.getY() + 1.0, sp.getZ(), 10, 0.3, 0.4, 0.3, 0.01);
                }
            }
        }
    }

    /** Forma de Lobo (T1): pequeno e rápido, mordida forte em feridos, fareja monstros por perto. */
    public static class WolfForm extends ShapeGlyph {
        public WolfForm() {
            super("wolf_form", 12, Shape.WOLF);
            element(Element.ARCANE);
            origin(MagicPath.DRUID);
            tier(1);
        }
    }

    /** Forma de Urso (T2): grande, resistente, patadas fortes que empurram e atrasam. */
    public static class BearForm extends ShapeGlyph {
        public BearForm() {
            super("bear_form", 18, Shape.BEAR);
            element(Element.ARCANE);
            origin(MagicPath.DRUID);
            tier(2);
        }
    }

    /** Forma de Falcão (T3): voo planado, batidas de asa no ar e nada de dano de queda. */
    public static class FalconForm extends ShapeGlyph {
        public FalconForm() {
            super("falcon_form", 20, Shape.FALCON);
            element(Element.ARCANE);
            origin(MagicPath.DRUID);
            tier(3);
        }
    }

    /** Forma de Golfinho (T3): nado muito rápido, respira e enxerga debaixo d'água; lento em terra. */
    public static class DolphinForm extends ShapeGlyph {
        public DolphinForm() {
            super("dolphin_form", 18, Shape.DOLPHIN);
            element(Element.ARCANE);
            origin(MagicPath.DRUID);
            tier(3);
        }
    }

    /** Forma de Aranha (T4): escala paredes, enxerga no escuro e envenena com a mordida. */
    public static class SpiderForm extends ShapeGlyph {
        public SpiderForm() {
            super("spider_form", 25, Shape.SPIDER);
            element(Element.ARCANE);
            origin(MagicPath.DRUID);
            tier(4);
        }
    }

    /** Fera Elemental (T5, lendário): fera enorme do elemento da magia, com passivas e golpes do elemento. */
    public static class ElementalBeastForm extends ShapeGlyph {
        public ElementalBeastForm() {
            super("elemental_beast_form", 50, Shape.BEAST);
            element(Element.ARCANE);
            origin(MagicPath.DRUID);
            legendary();
        }
    }

    private ShapeGlyphs() {}
}
