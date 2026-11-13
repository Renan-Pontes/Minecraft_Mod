package com.renanpontes.isekaimahou.feature.glyphs_elements_a.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.glyphs_elements_a.TerrainShaper;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Aplainar Terreno (Terra, tier 1 — vem no Despertar da Terra): deixa o chão PLANO na altura dos seus pés, para
 * construir cidades. Corta o que estiver acima (morros, árvores, mato), tapa os buracos abaixo com blocos naturais do
 * lugar (terra com grama por cima; areia no deserto; pedra/netherrack...) e nivela a superfície. Sem materiais: os
 * blocos cortados somem (sem drops, para não duplicar itens) e o aterro sai de graça, mas cada coluna custa mana extra.
 * <ul>
 *   <li>Si Mesmo: em volta dos seus pés. Projétil/Toque/Feixe: onde acertar (bloco ou criatura), na altura dos seus
 *       pés se você estiver a até 16 blocos de altura de lá.</li>
 *   <li>Raio base 4, cresce com Expandir (na forma ou no efeito) e com o poder; teto 6 + 2×Círculo (máx. 24).</li>
 *   <li>A obra anda aos poucos (fila própria, poucas colunas por tick) e respeita proteções (spawn, arenas de chefe em
 *       luta, blocos com inventário, regras de construção).</li>
 * </ul>
 * O trabalho de verdade é do {@link TerrainShaper}.
 */
public class FlattenEffect extends EffectGlyph {
    public FlattenEffect() {
        super("earth_flatten", 6);
        element(Element.EARTH);
        tier(1);
        minCircle(1);
    }

    @Override
    public boolean wantsBlocks() {
        return true;
    }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        TerrainShaper.request(ctx, target.position(), stats);
    }

    @Override
    public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
        TerrainShaper.request(ctx, Vec3.atBottomCenterOf(hit.getBlockPos().above()), stats);
    }

    @Override
    public float baseDamage() { return 0F; }

    @Override
    public float baseHeal() { return 0F; }

    @Override
    public int hintDuration() { return -1; }

    @Override
    public float hintRadius() { return TerrainShaper.BASE_RADIUS; }
}
