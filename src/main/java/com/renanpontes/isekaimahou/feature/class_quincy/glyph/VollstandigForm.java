package com.renanpontes.isekaimahou.feature.class_quincy.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_quincy.Quincy;
import com.renanpontes.isekaimahou.feature.class_quincy.QuincyVollstandig;
import com.renanpontes.isekaimahou.feature.transform_laws.BodyEffect;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.world.entity.LivingEntity;

/**
 * Vollständig — v1.1: CORPO de transformação (era a forma G do Quincy). Dentro da Transformação Suprema (tecla G):
 * asas de luz e auréola por 12 s (Estender no corpo aumenta): plana, resiste, engole projéteis e a cada segundo a
 * auréola dispara uma saraivada nos inimigos (até 4 + Dividir, raio 18 + Expandir), cada flecha com os efeitos da magia.
 * A receita moldada no espaço "Vollständig" (botão Moldar da aba Quincy) continua entrando por cima.
 */
public class VollstandigForm extends BodyEffect {
    public static final int DURATION = 240;

    public VollstandigForm() {
        super("quincy_vollstandig", 20);
        origin(MagicPath.QUINCY);
        element(Element.LIGHT);
        tier(5);
        minCircle(6);
    }

    @Override
    public int bodyTicks(SpellStats stats) {
        return stats.duration(DURATION);
    }

    @Override
    public int minCooldown() {
        return 600;
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
        return Quincy.REISHI_RGB;
    }

    @Override
    public float hintRadius() {
        return 18F;
    }

    @Override
    public void onTransformEnd(LivingEntity entity) {
        QuincyVollstandig.stop(entity);
    }

    @Override
    protected void start(SpellContext ctx, LivingEntity self, SpellStats stats, int ticks) {
        QuincyVollstandig.start(ctx, ticks, stats.radius(18F), 4 + Math.max(0, stats.split));
    }
}
