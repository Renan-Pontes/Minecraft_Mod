package com.renanpontes.isekaimahou.feature.class_knight.glyph;

import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;

import net.minecraft.resources.ResourceLocation;

/**
 * Glifos de ORIGEM do Cavaleiro Rúnico (todos {@code origin(MagicPath.KNIGHT)}: usáveis com qualquer elemento,
 * ensináveis às outras classes só por Lição). Montáveis com o resto do Grimório (aumentos, estágios, multi-elemento).
 * <pre>
 *  tier  tipo     id                    nome                        como se ganha
 *   1    forma    knight_engrave        Gravar na Lâmina            classe escolhida
 *   1    forma    knight_sword_beam     Raio de Espada              classe escolhida
 *   1    efeito   knight_rune_mark      Marca Rúnica                classe escolhida
 *   2    efeito   knight_blade_aura     Aura de Espada              Juramento I
 *   2    forma    knight_runic_thrust   Investida Rúnica            Juramento I
 *   3    efeito   knight_aegis          Égide Rúnica                Juramento II
 *   3    forma    knight_sword_rain     Chuva de Espadas            Juramento II
 *   5    forma G  knight_excalibur      Excalibur (ultimate)        Juramento III
 *   4    efeito   knight_oath_judgment  Sentença do Juramento       Juramento IV
 *  pacote de técnicas de anime:
 *   2    forma    knight_strike_air     Vento Invisível (Golpe do Ar) Juramento I
 *   3    efeito   knight_anti_magic     Lâmina Anti-Magia           Juramento II
 *   3    efeito   knight_caliburn_strike Caliburn (Espada da Seleção) feito: despertar a Caliburn
 *   4    forma    knight_balmung        Balmung (Queda do Crepúsculo) Juramento IV
 *   5    efeito   knight_avalon         Avalon (A Bainha Distante)  feito: despertar a Espada do Lago
 * </pre>
 */
public final class KnightGlyphs {
    public static final String ENGRAVE = "knight_engrave";
    public static final String SWORD_BEAM = "knight_sword_beam";
    public static final String RUNE_MARK = "knight_rune_mark";
    public static final String BLADE_AURA = "knight_blade_aura";
    public static final String RUNIC_THRUST = "knight_runic_thrust";
    public static final String AEGIS = "knight_aegis";
    public static final String SWORD_RAIN = "knight_sword_rain";
    public static final String EXCALIBUR = "knight_excalibur";
    public static final String JUDGMENT = "knight_oath_judgment";
    public static final String STRIKE_AIR = "knight_strike_air";
    public static final String ANTI_MAGIC = "knight_anti_magic";
    public static final String CALIBURN_STRIKE = "knight_caliburn_strike";
    public static final String BALMUNG = "knight_balmung";
    public static final String AVALON = "knight_avalon";

    /** Os 3 que vêm com a classe. */
    public static final List<String> STARTER = List.of(ENGRAVE, SWORD_BEAM, RUNE_MARK);

    public static void register() {
        GlyphRegistry.register(new EngraveForm());
        GlyphRegistry.register(new SwordBeamForm());
        GlyphRegistry.register(new RuneMarkEffect());
        GlyphRegistry.register(new BladeAuraEffect());
        GlyphRegistry.register(new RunicThrustForm());
        GlyphRegistry.register(new AegisEffect());
        GlyphRegistry.register(new SwordRainForm());
        GlyphRegistry.register(new ExcaliburForm());
        GlyphRegistry.register(new JudgmentEffect());
        GlyphRegistry.register(new StrikeAirForm());
        GlyphRegistry.register(new AntiMagicEffect());
        GlyphRegistry.register(new CaliburnStrikeEffect());
        GlyphRegistry.register(new BalmungForm());
        GlyphRegistry.register(new AvalonEffect());
    }

    public static ResourceLocation id(String path) {
        return IsekaiMahou.id(path);
    }

    private KnightGlyphs() {}
}
