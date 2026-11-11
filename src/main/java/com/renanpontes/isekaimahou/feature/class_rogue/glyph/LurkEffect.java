package com.renanpontes.isekaimahou.feature.class_rogue.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_rogue.RogueTechniques;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * "Espreitar" (homenagem à habilidade de ladrão do aventureiro azarado de outro mundo; Sombra, benéfico, origem do
 * Ladino): você (ou um aliado) some por 15 s (Estender +) — invisível e ESQUECIDO pelos monstros: quem não estiver
 * colado (2,5 blocos) larga o alvo e não consegue mirar de novo. Atacar revela, e esse ataque conta como pelas costas;
 * levar dano também revela. Chefes não se deixam enganar.
 */
public class LurkEffect extends EffectGlyph {
    public static final int TICKS = 300;

    public LurkEffect() {
        super("rogue_lurk", 9);
        origin(MagicPath.ROGUE);
        element(Element.SHADOW);
        tier(1);
    }

    @Override
    public boolean isBeneficial() { return true; }

    @Override
    public float baseDamage() { return 0F; }

    @Override
    public int hintDuration() { return TICKS; }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        if (target instanceof LivingEntity le && le.isAlive()) RogueTechniques.lurk(le, stats.duration(TICKS));
    }
}
