package com.renanpontes.isekaimahou.feature.class_knight.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_knight.KnightAegis;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Égide Rúnica (efeito benéfico, tier 3, Terra): 2 placas de pedra rúnica (+1 por Amplificar, máx. 5) orbitam o alvo
 * por 12 s (Estender); cada placa ANULA um golpe inteiro. Quando a última quebra, os estilhaços empurram e ferem (4) os
 * inimigos em 3 blocos. Em você ou num aliado (área/Si Mesmo).
 */
public class AegisEffect extends EffectGlyph {
    public AegisEffect() {
        super(KnightGlyphs.AEGIS, 20);
        element(Element.EARTH);
        origin(MagicPath.KNIGHT);
        tier(3);
        minCircle(3);
    }

    @Override
    public boolean isBeneficial() { return true; }

    @Override
    public float baseDamage() { return 4F; }

    @Override
    public int hintDuration() { return 240; }

    @Override
    public float hintRadius() { return 3F; }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        if (!(target instanceof LivingEntity le)) return;
        KnightAegis.apply(le, ctx, stats, 2 + Math.max(0, stats.amplify), stats.duration(240));
    }
}
