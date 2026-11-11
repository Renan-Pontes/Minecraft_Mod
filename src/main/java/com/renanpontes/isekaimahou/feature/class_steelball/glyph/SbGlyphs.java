package com.renanpontes.isekaimahou.feature.class_steelball.glyph;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_steelball.item.SteelBallItem;
import com.renanpontes.isekaimahou.glyph.AugmentGlyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Glifos de ORIGEM do Steel Ball (todos montáveis no Grimório, combináveis com qualquer elemento/estágio/ligação):
 * <ul>
 *   <li>Formas: {@code spin_ball} Bola de Aço · {@code spin_nail} Unha Giratória (Atos 1–3) · {@code spin_vortex}
 *   Turbilhão Giratório · {@code infinite_rotation} Rotação Infinita (forma G, Ato 4);</li>
 *   <li>Efeitos: {@code rotation_shock} Choque de Rotação · {@code spin_heal} Rotação Medicinal · {@code muscle_lock}
 *   Contração Muscular · {@code spin_drill} Furadeira Giratória;</li>
 *   <li>Aumentos: {@code golden_rectangle} Retângulo Dourado · {@code gallop_spin} Energia do Galope.</li>
 *   <li>Técnicas do Santo (v1.0): formas {@code tusk_act1/2/3} (Atos de Tusk), {@code scan_ball} Bola Escâner,
 *   {@code golden_gallop} Lição do Cavalo; efeitos {@code spin_mend} Rotação Restauradora e {@code ball_breaker}
 *   Esfera Quebrantadora.</li>
 *   <li>LOVE TRAIN: forma G {@code steelball_love_train}, despertada quando o Coração do Cadáver Santo completa o Santo.</li>
 * </ul>
 */
public final class SbGlyphs {
    public static final ResourceLocation GOLDEN = IsekaiMahou.id("golden_rectangle");
    public static final ResourceLocation GALLOP = IsekaiMahou.id("gallop_spin");
    public static final String SPIN_BALL = "spin_ball";
    public static final String SPIN_NAIL = "spin_nail";
    public static final String SPIN_VORTEX = "spin_vortex";
    public static final String INFINITE = "infinite_rotation";

    /** Multiplicador do Retângulo Dourado no dano (Pré-dano). */
    public static final float GOLDEN_MULT = 1.5F;
    /** Energia do Galope: montado / a pé. */
    public static final float GALLOP_MOUNTED = 1.4F;
    public static final float GALLOP_ON_FOOT = 1.1F;

    public static void register() {
        GlyphRegistry.register(new SpinBallForm());
        GlyphRegistry.register(new SpinNailForm());
        GlyphRegistry.register(new SpinVortexForm());
        GlyphRegistry.register(new InfiniteRotationForm());
        GlyphRegistry.register(new RotationShockEffect());
        GlyphRegistry.register(new SpinHealEffect());
        GlyphRegistry.register(new MuscleLockEffect());
        GlyphRegistry.register(new SpinDrillEffect());
        // v1.0 — Técnicas do Santo (Atos de Tusk e companhia; ver SbTechniques)
        GlyphRegistry.register(new TuskActForms.Act1Form());
        GlyphRegistry.register(new TuskActForms.Act2Form());
        GlyphRegistry.register(new TuskActForms.Act3Form());
        GlyphRegistry.register(new SbTechniqueGlyphs.ScanBallForm());
        GlyphRegistry.register(new SbTechniqueGlyphs.GoldenGallopForm());
        GlyphRegistry.register(new SbTechniqueGlyphs.SpinMendEffect());
        GlyphRegistry.register(new SbTechniqueGlyphs.BallBreakerEffect());
        // LOVE TRAIN (forma G): despertada pelo Coração do Cadáver Santo (ver SbLoveTrain)
        GlyphRegistry.register(new LoveTrainForm());

        // Retângulo Dourado: a proporção perfeita do giro — +50% de dano no que vier antes e atravessa +1 alvo
        AugmentGlyph golden = new AugmentGlyph("golden_rectangle", 14, s -> {
            s.custom.merge(GOLDEN, 1, Integer::sum);
            s.pierce += 1;
        });
        golden.origin(MagicPath.STEELBALL);
        golden.element(Element.EARTH);
        golden.tier(4);
        golden.minCircle(5);
        GlyphRegistry.register(golden);

        // Energia do Galope: a força do cavalo entra na rotação — montado +40%, a pé +10%; a montaria dispara
        AugmentGlyph gallop = new AugmentGlyph("gallop_spin", 6, s -> s.custom.merge(GALLOP, 1, Integer::sum));
        gallop.origin(MagicPath.STEELBALL);
        gallop.element(Element.WIND);
        gallop.tier(2);
        gallop.minCircle(2);
        GlyphRegistry.register(gallop);
    }

    /** O Retângulo Dourado está na forma ou neste efeito? */
    public static boolean golden(SpellContext ctx, @Nullable SpellStats stats) {
        return (stats != null && stats.custom(GOLDEN) > 0) || ctx.formStats().custom(GOLDEN) > 0;
    }

    public static boolean gallop(SpellContext ctx, @Nullable SpellStats stats) {
        return (stats != null && stats.custom(GALLOP) > 0) || ctx.formStats().custom(GALLOP) > 0;
    }

    public static boolean mounted(@Nullable LivingEntity e) {
        return e != null && e.getVehicle() instanceof LivingEntity;
    }

    /** Grau da bola de aço na mão (0..4), ou -1 sem bola. */
    public static int heldBallTier(@Nullable LivingEntity e) {
        if (e == null) return -1;
        for (ItemStack st : new ItemStack[] {e.getMainHandItem(), e.getOffhandItem()}) {
            if (st.getItem() instanceof SteelBallItem ball) return ball.tier();
        }
        return -1;
    }

    private SbGlyphs() {}
}
