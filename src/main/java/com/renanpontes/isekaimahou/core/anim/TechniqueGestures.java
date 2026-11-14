package com.renanpontes.isekaimahou.core.anim;

import static com.renanpontes.isekaimahou.core.anim.MahouAnims.*;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;

/**
 * Gestos próprios das TÉCNICAS de classe e das transformações, pelo id do glifo (sem mexer nos módulos das classes:
 * tudo passa pelo {@code SpellCastEvent.Post} central em {@link MahouAnims}).
 * <p>
 * Ordem: id exato da FORMA → id exato de um EFEITO (ex.: Kaioken é um efeito sobre "si mesmo") → palavra-chave na forma
 * (pega também técnicas novas, como as da classe Hollow: {@code *cero*}, {@code *sonido*}, {@code *resurreccion*}).
 * Sem resultado = a regra geral de {@link SpellGestures} decide.
 */
public final class TechniqueGestures {
    private static final Map<String, String> FORMS = new HashMap<>();
    private static final Map<String, String> EFFECTS = new HashMap<>();
    /** Palavra-chave na forma → gesto (na ordem). */
    private static final String[][] KEYWORDS = {
            {"cero", CERO_FIRE}, {"sonido", SONIDO_STEP}, {"resurreccion", RESURRECCION}, {"hollow_mask", HOLLOW_MASK_TEAR},
            {"mask_tear", HOLLOW_MASK_TEAR}, {"transform", TRANSFORM_BURST}, {"_form", TRANSFORM_ROAR},
            {"ground_slam", GROUND_SLAM}, {"slam", GROUND_SLAM}, {"crater", GROUND_SLAM}, {"spin_slash", SPIN_SLASH},
            {"throw", THROW_OVERHAND}, {"card", THROW_OVERHAND}, {"call", CAST_SUMMON}, {"taunt", TAUNT},
    };

    static {
        // ---- Ferreiro (Arqueiro de Lâminas)
        form("smith_hammer_slam", GROUND_SLAM);
        form("smith_caladbolg", BOW_CHARGE_BIG);
        form("smith_twin_falchions", THROW_OVERHAND);
        form("smith_rho_aias", PALM_WARD);
        form("smith_trace_on", CAST_SUMMON);
        form("smith_spark_shower", CAST_AREA);
        // ---- Ladino
        form("rogue_knife_fan", THROW_OVERHAND);
        form("rogue_shadow_step", SONIDO_STEP);
        form("rogue_phantom_heist", SONIDO_STEP);
        effect("rogue_godspeed", TRANSFORM_BURST);
        effect("rogue_one_cut", SPIN_SLASH);
        effect("rogue_assassin_claw", SPRINT_LUNGE);
        effect("rogue_backstab", SPRINT_LUNGE);
        effect("rogue_smoke_veil", THROW_OVERHAND);
        // ---- Cavaleiro Rúnico
        form("knight_excalibur", HOLY_CLEAVE);
        form("knight_balmung", HOLY_CLEAVE);
        form("knight_strike_air", CAST_BEAM);
        form("knight_runic_thrust", SPRINT_LUNGE);
        form("knight_sword_rain", CAST_SUMMON);
        effect("knight_caliburn_strike", HOLY_CLEAVE);
        effect("knight_oath_judgment", GROUND_SLAM);
        effect("knight_aegis", PALM_WARD);
        effect("knight_avalon", KNEEL_CHANNEL);
        // ---- Encantador (transformações de anime)
        effect("kaioken", TRANSFORM_ROAR);
        effect("eight_gates", TRANSFORM_BURST);
        effect("enchanter_gear_second", TRANSFORM_BURST);
        effect("enchanter_gear_fourth", TRANSFORM_ROAR);
        effect("ultra_instinct", TRANSFORM_RISE);
        effect("heavenly_restriction", TRANSFORM_ROAR);
        // ---- Patrulheiro
        form("ranger_thousand_feathers", BOW_CHARGE_BIG);
        effect("ranger_hecate_shot", BOW_CHARGE_BIG);
        effect("ranger_bullet_line_hecate", BOW_CHARGE_BIG);
        effect("ranger_huntress_lament", BOW_CHARGE_BIG);
        effect("ranger_sacred_purification", BOW_CHARGE_BIG);
        effect("ranger_conjured_chain", THROW_OVERHAND);
        effect("ranger_pack_call", CAST_SUMMON);
        effect("ranger_steed_call", CAST_SUMMON);
        effect("ranger_wolf_god", CAST_SUMMON);
        effect("ranger_wolf_goddess_fury", CAST_SUMMON);
        // ---- Apostador
        form("card_throw", THROW_OVERHAND);
        form("dice_roll", THROW_OVERHAND);
        effect("royal_flush", THROW_OVERHAND);
        effect("gambler_all_in", TAUNT);
        // ---- Steel Ball (bolas giratórias são arremessadas)
        form("spin_ball", THROW_OVERHAND);
        form("spin_vortex", THROW_OVERHAND);
        form("scan_ball", THROW_OVERHAND);
        effect("rotation_shock", THROW_OVERHAND);
        effect("spin_drill", THROW_OVERHAND);
        effect("ball_breaker", THROW_OVERHAND);
        // ---- Shinigami
        form("shinigami_cero", CERO_FIRE);
        effect("shinigami_visored", HOLLOW_MASK_TEAR);
        effect("shinigami_vasto_lorde", TRANSFORM_ROAR);
        effect("zanpakuto_bankai", TRANSFORM_BURST);
        form("zanpakuto_moon_fang", SPIN_SLASH);
        form("shinigami_shunpo", SONIDO_STEP);
        // ---- Quincy
        form("quincy_heilig_bogen", BOW_CHARGE_BIG);
        form("quincy_hirenkyaku", SONIDO_STEP);
        effect("quincy_vollstandig", TRANSFORM_RISE);
        effect("quincy_letzt_stil", TRANSFORM_BURST);
        // ---- Magias de origem e lendas (formas dos caminhos mágicos)
        form("lich_form", TRANSFORM_RISE);
        form("demon_wings", TRANSFORM_RISE);
        form("druid_sage_mode", KNEEL_CHANNEL);
        form("druid_forest_god", TRANSFORM_RISE);
        effect("necro_monarch", TRANSFORM_RISE);
        effect("quincy_almighty", TRANSFORM_RISE); // v1.1: transformações viraram efeitos da forma Transformação
        effect("transcend", TRANSFORM_BURST);
        form("crater", GROUND_SLAM);
        form("slash_storm", SPIN_SLASH);
        form("cleave", HOLY_CLEAVE);
        form("dismantle", SPIN_SLASH);
        form("flame_arrow", BOW_CHARGE_BIG);
        form("witch_envy_hands", TRANSFORM_RISE);
    }

    private static void form(String id, String anim) {
        FORMS.put(id, anim);
    }

    private static void effect(String id, String anim) {
        EFFECTS.put(id, anim);
    }

    /** Gesto próprio ao LANÇAR (null = deixa a regra geral escolher). */
    @Nullable
    public static String release(ParsedSpell spell) {
        FormGlyph form = spell.form();
        if (form == null) return null;
        String p = form.id().getPath();
        String a = FORMS.get(p);
        if (a != null) return a;
        if (spell.effects() != null) {
            for (ParsedSpell.EffectEntry e : spell.effects()) {
                if (e == null || e.effect() == null) continue;
                a = EFFECTS.get(e.effect().id().getPath());
                if (a != null) return a;
            }
        }
        for (String[] k : KEYWORDS) {
            if (p.contains(k[0])) return k[1];
        }
        return null;
    }

    /** Pose mantida própria enquanto CARREGA (null = a regra geral; canalização contínua fica com a pose de raio). */
    @Nullable
    public static String hold(ParsedSpell spell, boolean sustain) {
        if (sustain || spell.form() == null) return null;
        return CERO_FIRE.equals(release(spell)) ? CERO_CHARGE : null;
    }

    private TechniqueGestures() {}
}
