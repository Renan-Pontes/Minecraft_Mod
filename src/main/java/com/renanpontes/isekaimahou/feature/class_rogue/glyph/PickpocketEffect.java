package com.renanpontes.isekaimahou.feature.class_rogue.glyph;

import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_rogue.RogueSteal;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Mão Leve (origem Ladino): furta um item do alvo — da tabela de saque do mob, do bolso de um NPC (crime se alguém
 * vir), de um chefe (uma vez) ou da mochila de um jogador (só com PvP). Pelas costas / sem ser visto: +30% de chance;
 * Amplificar aumenta a chance. Cada mob tem um bolso só. Regras em {@link RogueSteal}.
 */
public class PickpocketEffect extends EffectGlyph {
    public PickpocketEffect() {
        super("rogue_pickpocket", 8);
        origin(MagicPath.ROGUE);
        tier(1);
    }

    @Override
    public float baseDamage() {
        return 0F;
    }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        if (!(ctx.caster() instanceof ServerPlayer thief) || !(target instanceof LivingEntity le) || le == thief) return;
        RogueSteal.steal(thief, le, stats.power() * ctx.powerMult(), false);
    }
}
