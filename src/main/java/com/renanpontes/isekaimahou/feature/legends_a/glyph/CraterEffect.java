package com.renanpontes.isekaimahou.feature.legends_a.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.legends_a.CraterQueue;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Efeito "Explosão Colossal" (Fogo, tier 4, Círculo 4): uma explosão enorme no ponto atingido — bola de fogo, coluna
 * de fogo, nuvem em cogumelo, onda de choque e tremor de câmera — que abre uma CRATERA de verdade pela API do núcleo
 * ({@code Destruction.crater}: respeita a regra {@code mahouSpellDestruction}, cidades e Provações).
 * Raio da explosão 3 blocos (Expandir/Colossal/Tudo ou Nada aumentam, e o poder da conjuração também; máx. 14);
 * a cratera tem 2,2× esse raio (até 35, a lenda "Explosão!"); a explosão fere 22 no centro, caindo até 40% na borda,
 * e incendeia.
 * Vários alvos atingidos no mesmo instante pela mesma magia viram UMA explosão só.
 */
public class CraterEffect extends EffectGlyph {
    public CraterEffect() {
        super("crater", 40);
        element(Element.FIRE);
        tier(4);
        minCircle(4);
    }

    @Override
    public boolean wantsBlocks() { return true; }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        CraterQueue.request(ctx, target.position(), stats, false);
    }

    @Override
    public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
        CraterQueue.request(ctx, hit.getLocation(), stats, true);
    }
}
