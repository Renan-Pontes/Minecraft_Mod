package com.renanpontes.isekaimahou.core.anim;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;

/**
 * Escolhe o GESTO de conjuração pela forma da magia (id do glifo de forma) e pela força (tier / custo):
 * <ul>
 *   <li>projétil/rajada → mão estendida; tier alto → empurrão com as duas mãos</li>
 *   <li>raio/feixe/sopro → empurrão com as duas mãos (mantido enquanto canaliza)</li>
 *   <li>área/nova/chão/chuva → braços sobem e batem no chão</li>
 *   <li>aura/si mesmo/transformação → mãos no peito e abre os braços</li>
 *   <li>invocação/portal/torre → braço erguido</li>
 *   <li>arco/flecha → puxar a corda; corte/espada → golpe; deslocamento → avanço</li>
 *   <li>suprema (tecla G), lendária, domínio → pose de carga com braços abertos e liberação</li>
 * </ul>
 * Formas de ARMA DE FOGO ({@code gun_*}) ficam sem gesto: o Pistoleiro já anima a arma.
 */
public final class SpellGestures {
    private static final String[] BEAM = {"beam", "ray", "cone", "cero", "breath", "laser", "stellar_burst", "black_star",
            "wave", "push", "kamehameha", "flame_thrower", "stream"};
    private static final String[] AREA = {"nova", "pulse", "zone", "wall", "rain", "meteor", "fall", "lances", "slam", "quake",
            "sankt", "seal", "rune", "mark", "shower", "converge", "bakudo", "hado_90", "kurohitsugi", "storm", "field",
            "blade_world", "trap", "barrier", "dome", "pillar", "circle", "eruption", "spiral_sphere", "cataclysm"};
    private static final String[] AURA = {"self", "aura", "orbit", "mend", "heal", "transcend", "visored", "vollstandig",
            "bankai", "vasto", "illusion", "camo", "enchant", "absorb", "letzt", "almighty", "sovereign", "buff", "armor",
            "stance", "guard", "shield", "blessing", "spin_mend"};
    private static final String[] SUMMON = {"conjure", "summon", "turret", "colossus", "monarch", "arsenal", "gate", "portal",
            "starforge", "familiar", "golem", "undead", "army", "clone", "echo", "tusk", "minion"};
    private static final String[] BOW = {"arrow", "bow", "bogen", "feathers", "triple_shot", "light_rain"};
    private static final String[] SLASH = {"slash", "strike", "getsuga", "fang", "zanjutsu", "balmung", "excalibur", "caladbolg",
            "photon_sword", "falchion", "seele", "sword", "blade", "cut", "engrave", "thrust"};
    private static final String[] DASH = {"shunpo", "hirenkyaku", "step", "leap", "dash", "blink", "gallop", "heist", "flash"};
    private static final String[] GRAND = {"domain", "time_stop", "super_tier", "idle_death", "casino", "mugetsu", "love_train",
            "high_noon", "act3"};

    /** Gesto ao LANÇAR a magia (null = sem gesto). */
    @Nullable
    public static String release(ParsedSpell spell) {
        FormGlyph form = spell.form();
        if (form == null) return MahouAnims.CAST_BOLT;
        String p = form.id().getPath();
        if (p.startsWith("gun_")) return null;
        if (spell.isUltimate() || form.isUltimate() || form.isLegendary() || any(p, GRAND)) return MahouAnims.CAST_ULT_RELEASE;
        boolean big = form.tier() >= 4 || spell.baseCost() >= 45;
        if (spell.formStats() != null && spell.formStats().channel) return MahouAnims.CAST_BEAM;
        if (any(p, BEAM)) return MahouAnims.CAST_BEAM;
        if (any(p, BOW)) return MahouAnims.CAST_BOW;
        if (any(p, DASH)) return MahouAnims.CAST_DASH;
        if (any(p, SUMMON)) return MahouAnims.CAST_SUMMON;
        if (any(p, AURA)) return MahouAnims.CAST_AURA;
        if (any(p, AREA)) return big ? MahouAnims.CAST_AREA_BIG : MahouAnims.CAST_AREA;
        if (any(p, SLASH)) return MahouAnims.CAST_SLASH;
        return big ? MahouAnims.CAST_BEAM : MahouAnims.CAST_BOLT;
    }

    /** Pose mantida enquanto a magia carrega ({@code sustain = false}) ou é canalizada sem parar ({@code true}). */
    public static String hold(ParsedSpell spell, boolean sustain) {
        FormGlyph form = spell.form();
        if (form != null && (spell.isUltimate() || form.isUltimate() || form.isLegendary() || any(form.id().getPath(), GRAND))) {
            return MahouAnims.CAST_ULT_CHARGE;
        }
        return sustain ? MahouAnims.CAST_BEAM_HOLD : MahouAnims.CAST_GATHER;
    }

    private static boolean any(String path, String[] keys) {
        for (String k : keys) {
            if (path.contains(k)) return true;
        }
        return false;
    }

    private SpellGestures() {}
}
