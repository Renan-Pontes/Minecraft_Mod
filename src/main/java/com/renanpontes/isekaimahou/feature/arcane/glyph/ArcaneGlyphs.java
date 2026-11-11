package com.renanpontes.isekaimahou.feature.arcane.glyph;

import com.renanpontes.isekaimahou.glyph.GlyphRegistry;

/**
 * Registro dos efeitos neutros (Arcano). Tier = Círculo em que o glifo é liberado (1→C1, 2→C2, 3→C3, 4→C5, 5→C8),
 * e o Círculo mínimo para usar acompanha o tier (pergaminhos achados cedo só funcionam no Círculo certo).
 */
public final class ArcaneGlyphs {

    public static void register() {
        // Círculo 1
        GlyphRegistry.register(new MotionEffects.PullEffect());
        GlyphRegistry.register(new WorldEffects.BreakEffect());
        GlyphRegistry.register(new WorldEffects.HarvestEffect());
        GlyphRegistry.register(new WorldEffects.ConjureLightEffect());
        GlyphRegistry.register(new MotionEffects.SlowFallEffect());
        // Círculo 2
        GlyphRegistry.register(new MotionEffects.BlinkEffect());
        GlyphRegistry.register(new ManaEffects.ShieldEffect());
        GlyphRegistry.register(new MotionEffects.LevitateEffect());
        GlyphRegistry.register(new WorldEffects.PlaceBlockEffect());
        GlyphRegistry.register(new WorldEffects.DetectEffect());
        GlyphRegistry.register(new ControlEffects.StunEffect());
        GlyphRegistry.register(new ControlEffects.MarkTargetEffect());
        // Círculo 3
        GlyphRegistry.register(new MotionEffects.SwapEffect());
        GlyphRegistry.register(new MotionEffects.RecallEffect());
        GlyphRegistry.register(new ManaEffects.ReflectEffect());
        GlyphRegistry.register(new MotionEffects.TelekinesisEffect());
        GlyphRegistry.register(new ControlEffects.SilenceEffect());
        GlyphRegistry.register(new ControlEffects.DisarmEffect());
        GlyphRegistry.register(new ControlEffects.ShrinkEffect());
        GlyphRegistry.register(new ControlEffects.GrowEffect());
        GlyphRegistry.register(new ManaEffects.ManaGiftEffect());
        // Círculo 5
        GlyphRegistry.register(new ManaEffects.DispelEffect());
        GlyphRegistry.register(new ManaEffects.ManaDrainEffect());
        GlyphRegistry.register(new ControlEffects.HasteTimeEffect());
        GlyphRegistry.register(new ControlEffects.SlowTimeEffect());
        // Círculo 8 (lendário)
        GlyphRegistry.register(new ManaEffects.CounterspellEffect());
    }

    private ArcaneGlyphs() {}
}
