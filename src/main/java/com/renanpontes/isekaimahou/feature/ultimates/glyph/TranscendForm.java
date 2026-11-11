package com.renanpontes.isekaimahou.feature.ultimates.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.ultimates.Transcendence;
import com.renanpontes.isekaimahou.feature.ultimates.UltFx;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * Transcendência (ultimate, slot G): quem conjura desperta por 30 s (Estender aumenta, até 60 s) — aura do elemento,
 * auréola e asas de luz, VOO, +50% de poder mágico, regeneração de mana ×3, recargas pela metade, e os efeitos da
 * receita pulsam a cada 1 s nos inimigos a até 6 blocos. No fim, Exaustão Mágica por 5 s. Ver {@link Transcendence}.
 */
public class TranscendForm extends FormGlyph {
    public TranscendForm() {
        super("transcend", 100);
        element(Element.ARCANE);
        tier(4);
        minCircle(5);
    }

    @Override
    public boolean isUltimate() { return true; }

    @Override
    public int baseCastTicks() { return 20; }

    @Override
    public int baseCooldown() { return 600; }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        // Pedestais (jogador falso) não se transformam
        if (caster == null || !UltFx.casterReady(ctx) || UltFx.isFake(caster)) return;
        int ticks = Mth.clamp(ctx.formStats().duration(600), 300, 1200);
        Transcendence.start(ctx, caster, ticks);
    }
}
