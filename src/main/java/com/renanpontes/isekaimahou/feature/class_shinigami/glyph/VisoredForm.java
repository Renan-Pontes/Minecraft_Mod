package com.renanpontes.isekaimahou.feature.class_shinigami.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_shinigami.HollowForms;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.server.level.ServerPlayer;

/**
 * Máscara do Vazio (homenagem aos Visored): a mão passa no rosto e a máscara branca de listras rubras se forma por 20 s
 * (+5 s a cada 10 usos, até 45 s; Estender aumenta): técnicas Shinigami +30% de poder, Força I, Velocidade I, o Cero com
 * força total, Getsuga Negro e a VOZ DUPLA. Ao pôr a máscara, uma explosão de Reiatsu aplica os efeitos da magia em
 * quem estiver a 4 blocos. Funciona sem efeitos. Aprendida ao vencer o Vazio Interior no Mundo Interior.
 */
public class VisoredForm extends FormGlyph {
    public VisoredForm() {
        super("shinigami_visored", 28);
        origin(MagicPath.SHINIGAMI);
        element(Element.SHADOW);
        tier(4);
        minCircle(6);
    }

    @Override
    public boolean requiresEffect() {
        return false;
    }

    @Override
    public int baseCastTicks() {
        return 10;
    }

    @Override
    public int baseCooldown() {
        return 600;
    }

    @Override
    public int hintDuration() {
        return 400;
    }

    @Override
    public float hintRadius() {
        return 4F;
    }

    @Override
    public void cast(SpellContext ctx) {
        if (ctx.caster() == null) return;
        int base = ctx.caster() instanceof ServerPlayer sp ? HollowForms.visoredTicks(MagicApi.get(sp)) : 400;
        HollowForms.startVisored(ctx, ctx.formStats().duration(base));
        ctx.caster().swing(net.minecraft.world.InteractionHand.OFF_HAND, true);
    }
}
