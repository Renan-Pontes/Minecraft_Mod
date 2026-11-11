package com.renanpontes.isekaimahou.feature.class_shinigami.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_shinigami.HollowForms;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellCost;
import com.renanpontes.isekaimahou.glyph.SpellStats;

/**
 * Vasto Lorde (homenagem à forma Hollow completa do Ichigo) — forma G / ultimate. Por 25 s (Estender aumenta) o Vazio
 * toma o corpo: máscara com chifres longos, buraco no peito, Força III, Resistência II, Velocidade II, regeneração (3%
 * da vida por segundo), golpes ×1,5, técnicas Shinigami +50%, um pulso por segundo num raio de 6 (+Expandir) que aplica
 * os efeitos da magia e um CERO GIGANTE a cada 5 s para onde você olha. ARRISCADA: ao acabar o corpo desaba (vida em
 * 25%, Fraqueza II, Lentidão, Cansaço e o Vigor zerado). Funciona sem efeitos. Custo: pulsos × duração (moderado).
 * Rank V + Vazio domado + 30 usos da Máscara.
 */
public class VastoLordeForm extends FormGlyph {
    public static final int DURATION = 500;

    public VastoLordeForm() {
        super("shinigami_vasto_lorde", 110);
        origin(MagicPath.SHINIGAMI);
        element(Element.SHADOW);
        tier(5);
        minCircle(9);
    }

    @Override
    public boolean isUltimate() {
        return true;
    }

    @Override
    public boolean requiresEffect() {
        return false;
    }

    @Override
    public float copies(SpellStats stats, int circle) {
        return SpellCost.pulseCopies(stats.duration(DURATION), 20);
    }

    @Override
    public int baseCastTicks() {
        return 40;
    }

    @Override
    public int baseCooldown() {
        return 1200;
    }

    @Override
    public int hintDuration() {
        return DURATION;
    }

    @Override
    public float hintRadius() {
        return 6F;
    }

    @Override
    public float hintRange() {
        return 40F;
    }

    @Override
    public void cast(SpellContext ctx) {
        SpellStats s = ctx.formStats();
        HollowForms.startVasto(ctx, s.duration(DURATION), s.radius(6F));
    }
}
