package com.renanpontes.isekaimahou.feature.class_quincy.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_quincy.QuincyFinalForms;
import com.renanpontes.isekaimahou.feature.transform_laws.BodyEffect;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.world.entity.LivingEntity;

/**
 * Quincy: Letzt Stil (homenagem à forma final) — v1.1: CORPO de transformação (era forma G). Dentro da Transformação
 * Suprema (tecla G), por 30 s (Estender no corpo aumenta) o Quincy libera TODO o Reishi: técnicas Quincy ×2, Força II,
 * Velocidade II, Resistência I, Regeneração I, plana com asas enormes de luz dourada, engole todo projétil a 8 blocos e a
 * cada 0,75 s um leque de 6 flechas teleguiadas gigantes cai sobre os inimigos a 24 blocos (+Expandir), cada uma com os
 * efeitos. O PREÇO: ao acabar, vida em 50%, Fraqueza II e 10 minutos SEM PODER QUINCY. Rank V + Luva Sanrei.
 */
public class LetztStilForm extends BodyEffect {
    public static final int DURATION = 600;

    public LetztStilForm() {
        super("quincy_letzt_stil", 30);
        origin(MagicPath.QUINCY);
        element(Element.LIGHT);
        tier(5);
        minCircle(8);
    }

    @Override
    public int bodyTicks(SpellStats stats) {
        return stats.duration(DURATION);
    }

    @Override
    public int minCooldown() {
        return 2400;
    }

    @Override
    public boolean supreme() {
        return true;
    }

    @Override
    public String cutsceneStyle() {
        return "holy";
    }

    @Override
    public int cutsceneRgb() {
        return 0xFFE89A;
    }

    @Override
    public float hintRadius() {
        return 24F;
    }

    @Override
    public void onTransformEnd(LivingEntity entity) {
        QuincyFinalForms.stopLetzt(entity);
    }

    @Override
    protected void start(SpellContext ctx, LivingEntity self, SpellStats stats, int ticks) {
        QuincyFinalForms.startLetzt(ctx, ticks, stats.radius(24F));
    }
}
