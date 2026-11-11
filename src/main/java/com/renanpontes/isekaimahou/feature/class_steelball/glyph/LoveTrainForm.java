package com.renanpontes.isekaimahou.feature.class_steelball.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_steelball.SbLoveTrain;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellCost;
import com.renanpontes.isekaimahou.glyph.SpellStats;

/**
 * LOVE TRAIN (forma G de origem do Steel Ball, homenagem a Steel Ball Run): despertada quando o CORAÇÃO DO CADÁVER
 * SANTO completa o Santo no corpo. Por 12 s (×Estender, até 15 s) o portador fica envolto numa cortina de luz
 * dimensional que o acompanha: TODO dano e todo efeito ruim que iriam acertá-lo viram "desgraça" e são desviados para
 * o inimigo mais próximo (a até 8 blocos) ou espalhados ao acaso entre os inimigos a até 24 blocos; projéteis se curvam
 * para longe. Só o que vem "de dentro da luz" atravessa: Acerto Garantido de domínios ({@code Domains.sureHitOn}),
 * dano verdadeiro/vazio e a Rotação Dourada (Rotação Infinita / Retângulo Dourado). Os efeitos da receita pulsam
 * (1×/s por inimigo) em quem recebe a desgraça. Funciona sem efeito nenhum.
 * <p>
 * Custo por cópia: pulsos moderados ({@code 1 + √(duração/40)} ≈ 3,4 cópias com a duração base).
 */
public class LoveTrainForm extends FormGlyph {
    public static final String ID = "steelball_love_train";

    public LoveTrainForm() {
        super(ID, 110);
        origin(MagicPath.STEELBALL);
        element(Element.EARTH);
        legendary();
        minCircle(8);
    }

    @Override public boolean isUltimate() { return true; }
    @Override public boolean requiresEffect() { return false; }

    @Override
    public float copies(SpellStats stats, int circle) {
        return SpellCost.pulseCopies(SbLoveTrain.duration(stats), SbLoveTrain.PULSE_TICKS * 2);
    }

    @Override public int baseCastTicks() { return 20; }
    @Override public int baseCooldown() { return 900; }
    @Override public int hintDuration() { return SbLoveTrain.BASE_TICKS; }
    @Override public float hintRadius() { return (float) SbLoveTrain.RANGE; }

    @Override
    public void cast(SpellContext ctx) {
        if (ctx.caster() == null) return;
        SbLoveTrain.start(ctx);
    }
}
