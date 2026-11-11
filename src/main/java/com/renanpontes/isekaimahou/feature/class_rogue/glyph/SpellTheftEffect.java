package com.renanpontes.isekaimahou.feature.class_rogue.glyph;

import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_rogue.RogueStolenBook;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Roubar Magia (origem Ladino): copia a RECEITA da última magia que o alvo conjurou (últimos 30 s) para o Grimório
 * Roubado, com 1–3 cargas. Conjure a cópia com a forma Página Roubada ou usando o Grimório Roubado (custa metade do
 * mana original). Funciona em cultistas, chefes e jogadores. Regras em {@link RogueStolenBook}.
 */
public class SpellTheftEffect extends EffectGlyph {
    public SpellTheftEffect() {
        super("rogue_spell_theft", 18);
        origin(MagicPath.ROGUE);
        tier(3);
        minCircle(2);
    }

    @Override
    public float baseDamage() {
        return 0F;
    }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        if (!(ctx.caster() instanceof ServerPlayer thief) || !(target instanceof LivingEntity le) || le == thief) return;
        RogueStolenBook.stealFrom(thief, le, stats.power() * ctx.powerMult());
    }
}
