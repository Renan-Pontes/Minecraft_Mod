package com.renanpontes.isekaimahou.feature.class_quincy.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_quincy.QuincyFinalForms;
import com.renanpontes.isekaimahou.feature.transform_laws.BodyEffect;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.world.entity.LivingEntity;

/**
 * O Todo-Poderoso (homenagem a "The Almighty", Letra A) — v1.1: CORPO de transformação (era forma G). Vai dentro da
 * Transformação Suprema (tecla G): {@code Transformação Suprema → O Todo-Poderoso → efeitos → leis}.
 * <p>
 * Palavra do dono: <i>"A transformação dura muito tempo, uns 5 minutos. Pra cada ataque que você tomar, ele desvia
 * automaticamente, aparece atrás do alvo e acerta ele com um golpe, dizendo 'eu vi tudo...'; cada ataque reduz 30
 * segundos da transformação. Após um ataque desses, fica 5 segundos sem teleportar, mas ainda desviando."</i>
 * Regras em {@link QuincyFinalForms} (5 min, 10 contra-ataques no máximo). Rank V + 8 Auswählen + 6 chefes.
 */
public class AlmightyForm extends BodyEffect {
    /** 5 minutos. Encurtar diminui; Estender não passa disto. */
    public static final int DURATION = 6000;

    public AlmightyForm() {
        super("quincy_almighty", 40);
        origin(MagicPath.QUINCY);
        element(Element.LIGHT);
        tier(5);
        minCircle(9);
    }

    @Override
    public int bodyTicks(SpellStats stats) {
        return Math.min(DURATION, stats.duration(DURATION));
    }

    /** 10 minutos de recarga (a transformação dura até 5). */
    @Override
    public int minCooldown() {
        return 12000;
    }

    @Override
    public boolean supreme() {
        return true;
    }

    @Override
    public String cutsceneStyle() {
        return "dark";
    }

    @Override
    public int cutsceneRgb() {
        return 0x6FA8FF;
    }

    @Override
    public void onTransformEnd(LivingEntity entity) {
        QuincyFinalForms.stopAlmighty(entity, false);
    }

    @Override
    protected void start(SpellContext ctx, LivingEntity self, SpellStats stats, int ticks) {
        QuincyFinalForms.startAlmighty(ctx, stats, ticks);
    }
}
