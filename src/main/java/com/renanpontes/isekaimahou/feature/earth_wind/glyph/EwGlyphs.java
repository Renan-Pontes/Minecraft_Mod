package com.renanpontes.isekaimahou.feature.earth_wind.glyph;

import com.renanpontes.isekaimahou.feature.earth_wind.glyph.earth.EarthsplitterEffect;
import com.renanpontes.isekaimahou.feature.earth_wind.glyph.earth.ExcavateEffect;
import com.renanpontes.isekaimahou.feature.earth_wind.glyph.earth.FertilizeEffect;
import com.renanpontes.isekaimahou.feature.earth_wind.glyph.earth.FortressEffect;
import com.renanpontes.isekaimahou.feature.earth_wind.glyph.earth.MagnetismEffect;
import com.renanpontes.isekaimahou.feature.earth_wind.glyph.earth.QuicksandEffect;
import com.renanpontes.isekaimahou.feature.earth_wind.glyph.earth.RaisePillarEffect;
import com.renanpontes.isekaimahou.feature.earth_wind.glyph.earth.RockEffect;
import com.renanpontes.isekaimahou.feature.earth_wind.glyph.earth.RootEffect;
import com.renanpontes.isekaimahou.feature.earth_wind.glyph.earth.StalagmiteEffect;
import com.renanpontes.isekaimahou.feature.earth_wind.glyph.earth.StoneWallEffect;
import com.renanpontes.isekaimahou.feature.earth_wind.glyph.earth.StoneskinEffect;
import com.renanpontes.isekaimahou.feature.earth_wind.glyph.earth.SummonGolemEffect;
import com.renanpontes.isekaimahou.feature.earth_wind.glyph.earth.TremorEffect;
import com.renanpontes.isekaimahou.feature.earth_wind.glyph.earth.WeightEffect;
import com.renanpontes.isekaimahou.feature.earth_wind.glyph.wind.GustEffect;
import com.renanpontes.isekaimahou.feature.earth_wind.glyph.wind.ImpulseEffect;
import com.renanpontes.isekaimahou.feature.earth_wind.glyph.wind.LaunchEffect;
import com.renanpontes.isekaimahou.feature.earth_wind.glyph.wind.SonarEffect;
import com.renanpontes.isekaimahou.feature.earth_wind.glyph.wind.SummonSylphEffect;
import com.renanpontes.isekaimahou.feature.earth_wind.glyph.wind.TempestEffect;
import com.renanpontes.isekaimahou.feature.earth_wind.glyph.wind.TornadoEffect;
import com.renanpontes.isekaimahou.feature.earth_wind.glyph.wind.VacuumEffect;
import com.renanpontes.isekaimahou.feature.earth_wind.glyph.wind.WhirlwindEffect;
import com.renanpontes.isekaimahou.feature.earth_wind.glyph.wind.WindBladeEffect;
import com.renanpontes.isekaimahou.feature.earth_wind.glyph.wind.WindBuffs;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;

/** Registro dos glifos de efeito de Terra e Vento. */
public final class EwGlyphs {

    public static void register() {
        // ---------------- Terra ----------------
        GlyphRegistry.register(new RockEffect());            // t1
        GlyphRegistry.register(new RaisePillarEffect());     // t1
        GlyphRegistry.register(new StalagmiteEffect());      // t2
        GlyphRegistry.register(new StoneskinEffect());       // t2
        GlyphRegistry.register(new ExcavateEffect());        // t2
        GlyphRegistry.register(new FertilizeEffect());       // t2
        GlyphRegistry.register(new StoneWallEffect());       // t3
        GlyphRegistry.register(new RootEffect());            // t3
        GlyphRegistry.register(new QuicksandEffect());       // t3
        GlyphRegistry.register(new SummonGolemEffect());     // t3 (invocacao)
        GlyphRegistry.register(new FortressEffect());        // t4
        GlyphRegistry.register(new TremorEffect());          // t4
        GlyphRegistry.register(new WeightEffect());          // t4
        GlyphRegistry.register(new MagnetismEffect());       // t4
        GlyphRegistry.register(new EarthsplitterEffect());   // t5 lendario

        // ---------------- Vento ----------------
        GlyphRegistry.register(new WindBladeEffect());       // t1
        GlyphRegistry.register(new GustEffect());            // t1
        GlyphRegistry.register(new ImpulseEffect());         // t2
        GlyphRegistry.register(new WindBuffs.DoubleJump());  // t2
        GlyphRegistry.register(new WindBuffs.Glide());       // t2
        GlyphRegistry.register(new WhirlwindEffect());       // t3
        GlyphRegistry.register(new LaunchEffect());          // t3
        GlyphRegistry.register(new WindBuffs.Swiftness());   // t3
        GlyphRegistry.register(new SummonSylphEffect());     // t3 (invocacao)
        GlyphRegistry.register(new TornadoEffect());         // t4
        GlyphRegistry.register(new VacuumEffect());          // t4
        GlyphRegistry.register(new WindBuffs.Deflect());     // t4
        GlyphRegistry.register(new WindBuffs.SilentSteps()); // t4
        GlyphRegistry.register(new SonarEffect());           // t4
        GlyphRegistry.register(new TempestEffect());         // t5 lendario
    }

    private EwGlyphs() {}
}
