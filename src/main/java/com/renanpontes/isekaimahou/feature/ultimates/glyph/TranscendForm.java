package com.renanpontes.isekaimahou.feature.ultimates.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.transform_laws.BodyEffect;
import com.renanpontes.isekaimahou.feature.ultimates.Transcendence;
import com.renanpontes.isekaimahou.feature.ultimates.UltFx;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * Transcendência — v1.1: CORPO de transformação (era forma ultimate). Vai dentro da Transformação Suprema (tecla G):
 * quem conjura desperta por 30 s (Estender no corpo aumenta, até 60 s) — aura do elemento, auréola e asas de luz, VOO,
 * +50% de poder mágico, regeneração de mana ×3, recargas pela metade, e os efeitos da receita pulsam a cada 1 s nos
 * inimigos a até 6 blocos. No fim, Exaustão Mágica por 5 s. Ver {@link Transcendence}.
 */
public class TranscendForm extends BodyEffect {
    public TranscendForm() {
        super("transcend", 25);
        element(Element.ARCANE);
        tier(4);
        minCircle(5);
    }

    @Override
    public int bodyTicks(SpellStats stats) {
        return Mth.clamp(stats.duration(600), 300, 1200);
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
        return 0xE8D8FF;
    }

    @Override
    public float hintRadius() {
        return (float) Transcendence.PULSE_RADIUS;
    }

    @Override
    public void onTransformEnd(LivingEntity entity) {
        // fim junto com a transformação: com a Exaustão Mágica, como no fim natural
        Transcendence.stop(entity, true, false);
    }

    @Override
    protected void start(SpellContext ctx, LivingEntity self, SpellStats stats, int ticks) {
        // Pedestais (jogador falso) não se transformam
        if (!UltFx.casterReady(ctx) || UltFx.isFake(self)) return;
        Transcendence.start(ctx, self, ticks);
    }
}
